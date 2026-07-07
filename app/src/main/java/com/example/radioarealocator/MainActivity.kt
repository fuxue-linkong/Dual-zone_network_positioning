package com.example.radioarealocator

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.radioarealocator.ui.MainScreen
import com.example.radioarealocator.ui.MainViewModel
import com.example.radioarealocator.ui.theme.RadioAreaLocatorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.any { it.value }
        if (granted) {
            viewModel.refreshLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val backgroundUri by viewModel.backgroundUri

            RadioAreaLocatorTheme(backgroundUri = backgroundUri) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 背景图层（含 scrim 遮罩，保证内容可读）
                        BackgroundLayer(uri = backgroundUri)

                        // 前景内容
                        MainScreen(
                            viewModel = viewModel,
                            onRequestPermission = ::requestLocationPermission
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun BackgroundLayer(uri: Uri?) {
        if (uri == null) {
            // 未设置背景图：用 surface 色填充，保持原视觉
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
            return
        }
        val context = LocalContext.current
        var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(uri) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    // 两阶段解码：先读边界，再按目标尺寸下采样，避免超大图 OOM
                    val bounds = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input, null, bounds)
                    }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

                    // 目标宽度 1080px，按 2 的幂次计算 inSampleSize
                    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1080, 1920)
                    val opts = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input, null, opts)
                    }
                }.getOrNull()
            }
        }
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        // 半透明 scrim：使用 surface 色叠 80% 透明度，既保留背景图轮廓又保证文字对比
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
        )
    }

    private fun requestLocationPermission() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.refreshLocation()
            }

            else -> {
                locationPermissionLauncher.launch(permissions)
            }
        }
    }
}

/**
 * 计算图片下采样比例，避免解码超大图导致 OOM。
 * 返回 2 的幂次，符合 BitmapFactory.inSampleSize 的要求。
 */
private fun calculateInSampleSize(
    srcWidth: Int,
    srcHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Int {
    if (srcWidth <= 0 || srcHeight <= 0) return 1
    var sampleSize = 1
    while (srcWidth / sampleSize > targetWidth * 2 ||
        srcHeight / sampleSize > targetHeight * 2
    ) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

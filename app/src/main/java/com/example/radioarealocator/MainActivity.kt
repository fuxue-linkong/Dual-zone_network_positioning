package com.example.radioarealocator

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.radioarealocator.data.reminder.ReminderNotificationHelper
import com.example.radioarealocator.data.reminder.ReminderStore
import com.example.radioarealocator.ui.MainScreen
import com.example.radioarealocator.ui.MainViewModel
import com.example.radioarealocator.ui.theme.LocalCardAlpha
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

    /**
     * 通知权限请求（Android 13+ 必需）。结果不影响功能本身，
     * 即使拒绝应用也不会崩溃，仅是通知无法显示。
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // 无论是否授予，都不阻塞应用使用
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 创建通知渠道（幂等），并在 Android 13+ 请求运行时通知权限
        ensureNotificationReady()
        // 启动天气定时刷新（每 30 分钟），仅在定位可用时实际请求网络
        viewModel.startWeatherAutoRefresh()
        setContent {
            val backgroundUri by viewModel.backgroundUri
            val cardOpacity by viewModel.cardOpacity
            val backgroundOpacity by viewModel.backgroundOpacity

            RadioAreaLocatorTheme(backgroundUri = backgroundUri) {
                // 设置了背景图时，按用户透明度设置衰减卡片整体不透明度；未设置时保持完全不透明
                val cardAlpha = if (backgroundUri != null) cardOpacity / 100f else 1f
                CompositionLocalProvider(LocalCardAlpha provides cardAlpha) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // 背景图层（含 scrim 遮罩，保证内容可读）
                            BackgroundLayer(
                                uri = backgroundUri,
                                backgroundOpacity = backgroundOpacity
                            )

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
    }

    /**
     * 创建通知渠道（首次启动时），并在 Android 13+ 请求 POST_NOTIFICATIONS 权限。
     * 渠道创建幂等，重复调用安全。
     */
    private fun ensureNotificationReady() {
        val settings = ReminderStore(this).loadSettings()
        val helper = ReminderNotificationHelper(this)
        helper.createChannel(settings)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Composable
    private fun BackgroundLayer(uri: Uri?, backgroundOpacity: Int) {
        if (uri == null) {
            // 未设置背景图：用 surface 色填充，保持原视觉
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
            return
        }
        // 背景图不透明度：0 完全隐藏（图片不可见），100 完全显示（无遮罩）
        val bgAlpha = backgroundOpacity.coerceIn(0, 100) / 100f
        // scrim 反向缩放：不透明度越高，遮罩越淡；0.82 为最大遮罩强度
        val scrimAlpha = (1f - bgAlpha) * 0.82f
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
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(bgAlpha),
                contentScale = ContentScale.Crop
            )
        }
        // 半透明 scrim：随背景不透明度反向衰减，不透明度 100 时无遮罩，0 时最大遮罩保证文字可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = scrimAlpha))
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

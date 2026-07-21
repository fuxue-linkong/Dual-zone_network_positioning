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
import top.yukonga.miuix.kmp.theme.MiuixTheme
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

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationReady()
        viewModel.startWeatherAutoRefresh()
        setContent {
            val backgroundUri by viewModel.backgroundUri
            val cardOpacity by viewModel.cardOpacity
            val backgroundOpacity by viewModel.backgroundOpacity
            val monetEnabled by viewModel.monetEnabled

            RadioAreaLocatorTheme(
                backgroundUri = backgroundUri,
                monetEnabled = monetEnabled
            ) {
                val cardAlpha = if (backgroundUri != null) cardOpacity / 100f else 1f
                CompositionLocalProvider(LocalCardAlpha provides cardAlpha) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        BackgroundLayer(
                            uri = backgroundUri,
                            backgroundOpacity = backgroundOpacity
                        )

                        MainScreen(
                            viewModel = viewModel,
                            onRequestPermission = ::requestLocationPermission
                        )
                    }
                }
            }
        }
    }

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface)
            )
            return
        }
        val bgAlpha = backgroundOpacity.coerceIn(0, 100) / 100f
        val scrimAlpha = (1f - bgAlpha) * 0.82f
        val context = LocalContext.current
        var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(uri) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input, null, bounds)
                    }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface.copy(alpha = scrimAlpha))
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

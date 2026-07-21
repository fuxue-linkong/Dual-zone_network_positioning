package com.example.radioarealocator.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从背景图提取主色调，作为 Miuix [top.yukonga.miuix.kmp.theme.ThemeController]
 * 的 keyColor 传入。Miuix 内部会基于该种子色用 HCT 算法生成完整的 Material 色板，
 * 无需我们手写色板拼装逻辑。
 *
 * 提取策略：优先 vibrant，其次 muted，最后回退到 dominant。
 */
object BackgroundPalette {

    /**
     * 解码 URI 对应图片并提取主色。失败或为空时返回 null。
     */
    suspend fun extractKeyColor(context: Context, uri: Uri): Color? = withContext(Dispatchers.IO) {
        runCatching {
            // 两阶段解码：先读边界，再按目标尺寸下采样，避免超大图 OOM
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 512 || bounds.outHeight / sampleSize > 512) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            } ?: return@runCatching null

            var scaled: android.graphics.Bitmap? = null
            try {
                scaled = if (bitmap.width > 256) {
                    val ratio = 256f / bitmap.width
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        256,
                        (bitmap.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else bitmap
                val palette = Palette.from(scaled).generate()
                val rgb = palette.vibrantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb
                    ?: return@runCatching null
                Color(rgb)
            } finally {
                // 异常路径也确保回收，避免 native 内存泄漏
                if (scaled != null && scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            }
        }.getOrNull()
    }
}

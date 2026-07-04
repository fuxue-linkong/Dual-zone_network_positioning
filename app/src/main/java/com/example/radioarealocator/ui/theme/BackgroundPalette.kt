package com.example.radioarealocator.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 从背景图提取主色调，用于生成 Material3 [androidx.compose.material3.ColorScheme]。
 *
 * 提取策略：
 * - 主色优先用 vibrant/muted 色板
 * - 容器色用对应色板的浅色/深色变体
 * - surface 保持中性，保证 UI 可读性
 */
object BackgroundPalette {

    /**
     * 解码 URI 对应图片并提取颜色。失败或为空时返回 null。
     */
    suspend fun extractColors(context: Context, uri: Uri): PaletteSwatches? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return@use null
                // 大图缩放以加快 Palette 计算
                val scaled = if (bitmap.width > 256) {
                    val ratio = 256f / bitmap.width
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        256,
                        (bitmap.height * ratio).toInt().coerceAtLeast(1),
                        true
                    ).also { if (it != bitmap) bitmap.recycle() }
                } else bitmap
                val palette = Palette.from(scaled).generate()
                scaled.recycle()
                // dominantSwatch 几乎总有值；为空则视为提取失败
                val dominant = palette.dominantSwatch ?: return@use null
                PaletteSwatches(
                    primary = palette.vibrantSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                        ?: dominant.rgb,
                    secondary = palette.mutedSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: dominant.rgb,
                    tertiary = palette.lightVibrantSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: dominant.rgb,
                    surface = palette.lightMutedSwatch?.rgb
                        ?: palette.darkMutedSwatch?.rgb
                        ?: palette.mutedSwatch?.rgb
                        ?: dominant.rgb,
                    bodyTextColor = palette.vibrantSwatch?.bodyTextColor
                        ?: palette.mutedSwatch?.bodyTextColor
                        ?: dominant.bodyTextColor
                )
            }
        }.getOrNull()
    }

    /** 从 Palette 提取出的原始色板信息。rgb 颜色为 android.graphics.Color int。 */
    data class PaletteSwatches(
        val primary: Int,
        val secondary: Int,
        val tertiary: Int,
        val surface: Int,
        val bodyTextColor: Int
    )

    /**
     * 计算一个颜色的合理 "on" 色（黑或白），保证对比度。
     */
    fun onColorFor(rgb: Int): Color {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        // 使用相对亮度判断
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        return if (luminance > 0.5) Color.Black else Color.White
    }
}

package com.example.radioarealocator.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 卡片整体透明度系数（0f~1f）。默认 1f（完全不透明）。
 *
 * 仅在设置了背景图时由上层（[com.example.radioarealocator.MainActivity]）下调，
 * 使卡片整体半透明以透出背景图。卡片通过 `Modifier.alpha(LocalCardAlpha.current)` 应用。
 */
val LocalCardAlpha = compositionLocalOf<Float> { 1f }

private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RadioAreaLocatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    backgroundUri: android.net.Uri? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var backgroundScheme by remember { mutableStateOf<ColorScheme?>(null) }

    // 背景图驱动 ColorScheme：异步提取色板并构造 ColorScheme
    LaunchedEffect(backgroundUri, darkTheme) {
        backgroundScheme = backgroundUri?.let { uri ->
            BackgroundPalette.extractColors(context, uri)?.let { swatches ->
                buildSchemeFromSwatches(swatches, darkTheme)
            }
        }
    }

    val materialColorScheme = when {
        backgroundScheme != null -> backgroundScheme!!
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // 安全转换：仅在 Context 为 Activity 时设置状态栏，避免在非 Activity 场景抛出 ClassCastException
            val ctx = view.context
            if (ctx is Activity) {
                val window = ctx.window
                // 背景图存在时让状态栏透明，否则用 surface 色
                window.statusBarColor = if (backgroundUri != null) {
                    AndroidColor.TRANSPARENT
                } else {
                    materialColorScheme.surface.toArgb()
                }
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = materialColorScheme,
        typography = Typography,
        shapes = Shapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

/**
 * 将 Palette 提取的色板转换为 Material3 ColorScheme。
 * 浅色与深色模式采用不同亮度策略，保证文字与背景对比度。
 */
private fun buildSchemeFromSwatches(
    swatches: BackgroundPalette.PaletteSwatches,
    dark: Boolean
): ColorScheme {
    val primary = Color(swatches.primary)
    val onPrimary = BackgroundPalette.onColorFor(swatches.primary)
    val secondary = Color(swatches.secondary)
    val onSecondary = BackgroundPalette.onColorFor(swatches.secondary)
    val tertiary = Color(swatches.tertiary)
    val onTertiary = BackgroundPalette.onColorFor(swatches.tertiary)

    val surfaceRgb = swatches.surface
    val surface = if (dark) {
        Color(surfaceRgb).copy(alpha = 1f).let {
            // 深色模式：将 surface 调暗
            Color(
                red = it.red * 0.25f,
                green = it.green * 0.25f,
                blue = it.blue * 0.25f,
                alpha = 1f
            )
        }
    } else {
        Color(
            red = (Color(surfaceRgb).red + 1f) / 2f,
            green = (Color(surfaceRgb).green + 1f) / 2f,
            blue = (Color(surfaceRgb).blue + 1f) / 2f,
            alpha = 1f
        )
    }
    val onSurface = BackgroundPalette.onColorFor(surface.toArgb())

    val primaryContainer = if (dark) {
        Color(swatches.primary).copy(alpha = 1f).let {
            Color(it.red * 0.4f, it.green * 0.4f, it.blue * 0.4f)
        }
    } else {
        Color(
            red = (Color(swatches.primary).red + 1f) / 2f,
            green = (Color(swatches.primary).green + 1f) / 2f,
            blue = (Color(swatches.primary).blue + 1f) / 2f
        )
    }
    val onPrimaryContainer = BackgroundPalette.onColorFor(primaryContainer.toArgb())

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            tertiary = tertiary,
            onTertiary = onTertiary,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surface,
            onSurfaceVariant = onSurface,
            outline = onSurface.copy(alpha = 0.5f)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            tertiary = tertiary,
            onTertiary = onTertiary,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surface,
            onSurfaceVariant = onSurface,
            outline = onSurface.copy(alpha = 0.5f)
        )
    }
}

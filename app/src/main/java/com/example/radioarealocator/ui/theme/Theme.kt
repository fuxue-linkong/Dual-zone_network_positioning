package com.example.radioarealocator.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

val LocalCardAlpha = compositionLocalOf<Float> { 1f }

private val LightColorScheme = lightColorScheme(
    primary = light_primary,
    onPrimary = light_onPrimary,
    primaryContainer = light_primaryContainer,
    onPrimaryContainer = light_onPrimaryContainer,
    secondary = light_secondary,
    onSecondary = light_onSecondary,
    secondaryContainer = light_secondaryContainer,
    onSecondaryContainer = light_onSecondaryContainer,
    tertiaryContainer = light_tertiaryContainer,
    onTertiaryContainer = light_onTertiaryContainer,
    error = light_error,
    onError = light_onError,
    errorContainer = light_errorContainer,
    onErrorContainer = light_onErrorContainer,
    surface = light_surface,
    onSurface = light_onSurface,
    surfaceVariant = light_surfaceVariant,
    onSurfaceSecondary = light_onSurfaceVariant,
    background = light_background,
    onBackground = light_onBackground,
    outline = light_outline,
    dividerLine = light_outline.copy(alpha = 0.4f),
    onBackgroundVariant = light_onSurfaceVariant,
    primaryVariant = light_primary,
    onPrimaryVariant = light_onPrimary,
    secondaryVariant = light_secondary,
    onSecondaryVariant = light_onSecondary,
    surfaceContainer = light_surface,
    onSurfaceContainer = light_onSurface,
    surfaceContainerHigh = light_surfaceVariant,
    onSurfaceContainerHigh = light_onSurfaceVariant,
    surfaceContainerHighest = light_surfaceVariant,
    onSurfaceContainerHighest = light_onSurface,
    onSurfaceVariantSummary = light_onSurfaceVariant.copy(alpha = 0.6f),
    onSurfaceVariantActions = light_onSurfaceVariant.copy(alpha = 0.4f),
    windowDimming = Color.Black.copy(alpha = 0.3f),
    sliderKeyPoint = Color(0x4DA3B3CD),
    sliderKeyPointForeground = light_primary,
    sliderBackground = Color(0x0F000000),
    disabledPrimary = light_onSurfaceVariant.copy(alpha = 0.38f),
    disabledOnPrimary = Color.White,
    disabledPrimaryButton = light_onSurfaceVariant.copy(alpha = 0.38f),
    disabledOnPrimaryButton = Color.White,
    disabledPrimarySlider = light_onSurfaceVariant.copy(alpha = 0.38f),
    disabledSecondary = light_surfaceVariant,
    disabledOnSecondary = light_onSurfaceVariant,
    disabledSecondaryVariant = light_surfaceVariant,
    disabledOnSecondaryVariant = light_onSurfaceVariant.copy(alpha = 0.5f),
    disabledOnSurface = light_onSurfaceVariant.copy(alpha = 0.38f),
    secondaryContainerVariant = light_secondaryContainer,
    onSecondaryContainerVariant = light_onSecondaryContainer,
    tertiaryContainerVariant = light_tertiaryContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = dark_primary,
    onPrimary = dark_onPrimary,
    primaryContainer = dark_primaryContainer,
    onPrimaryContainer = dark_onPrimaryContainer,
    secondary = dark_secondary,
    onSecondary = dark_onSecondary,
    secondaryContainer = dark_secondaryContainer,
    onSecondaryContainer = dark_onSecondaryContainer,
    tertiaryContainer = dark_tertiaryContainer,
    onTertiaryContainer = dark_onTertiaryContainer,
    error = dark_error,
    onError = dark_onError,
    errorContainer = dark_errorContainer,
    onErrorContainer = dark_onErrorContainer,
    surface = dark_surface,
    onSurface = dark_onSurface,
    surfaceVariant = dark_surfaceVariant,
    onSurfaceSecondary = dark_onSurfaceVariant,
    background = dark_background,
    onBackground = dark_onBackground,
    outline = dark_outline,
    dividerLine = dark_outline.copy(alpha = 0.4f),
    onBackgroundVariant = dark_onSurfaceVariant,
    primaryVariant = dark_primary,
    onPrimaryVariant = dark_onPrimary,
    secondaryVariant = dark_secondary,
    onSecondaryVariant = dark_onSecondary,
    surfaceContainer = dark_surface,
    onSurfaceContainer = dark_onSurface,
    surfaceContainerHigh = dark_surfaceVariant,
    onSurfaceContainerHigh = dark_onSurfaceVariant,
    surfaceContainerHighest = dark_surfaceVariant,
    onSurfaceContainerHighest = dark_onSurface,
    onSurfaceVariantSummary = dark_onSurfaceVariant.copy(alpha = 0.6f),
    onSurfaceVariantActions = dark_onSurfaceVariant.copy(alpha = 0.4f),
    windowDimming = Color.Black.copy(alpha = 0.6f),
    sliderKeyPoint = Color(0x4D7A8AA6),
    sliderKeyPointForeground = dark_primary,
    sliderBackground = Color(0x26FFFFFF),
    disabledPrimary = dark_onSurfaceVariant.copy(alpha = 0.38f),
    disabledOnPrimary = Color.White,
    disabledPrimaryButton = dark_onSurfaceVariant.copy(alpha = 0.38f),
    disabledOnPrimaryButton = Color.White,
    disabledPrimarySlider = dark_onSurfaceVariant.copy(alpha = 0.38f),
    disabledSecondary = dark_surfaceVariant,
    disabledOnSecondary = dark_onSurfaceVariant,
    disabledSecondaryVariant = dark_surfaceVariant,
    disabledOnSecondaryVariant = dark_onSurfaceVariant.copy(alpha = 0.5f),
    disabledOnSurface = dark_onSurfaceVariant.copy(alpha = 0.38f),
    secondaryContainerVariant = dark_secondaryContainer,
    onSecondaryContainerVariant = dark_onSecondaryContainer,
    tertiaryContainerVariant = dark_tertiaryContainer,
)

@Composable
fun RadioAreaLocatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    backgroundUri: android.net.Uri? = null,
    monetEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var backgroundScheme by remember { mutableStateOf<Colors?>(null) }

    // 仅在启用莫奈取色且设置了背景图时提取主色调；否则使用默认主题色 #3482FF
    LaunchedEffect(backgroundUri, darkTheme, monetEnabled) {
        backgroundScheme = if (monetEnabled) {
            backgroundUri?.let { uri ->
                BackgroundPalette.extractColors(context, uri)?.let { swatches ->
                    buildSchemeFromSwatches(swatches, darkTheme)
                }
            }
        } else {
            null
        }
    }

    val colors = when {
        backgroundScheme != null -> backgroundScheme!!.copy()
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val ctx = view.context
            if (ctx is Activity) {
                val window = ctx.window
                window.statusBarColor = if (backgroundUri != null) {
                    AndroidColor.TRANSPARENT
                } else {
                    colors.surface.toArgb()
                }
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MiuixTheme(
        colors = colors,
        textStyles = AppTextStyles,
        content = content
    )
}

private fun buildSchemeFromSwatches(
    swatches: BackgroundPalette.PaletteSwatches,
    dark: Boolean
): Colors {
    val primary = Color(swatches.primary)
    val onPrimary = BackgroundPalette.onColorFor(swatches.primary)
    val secondary = Color(swatches.secondary)
    val onSecondary = BackgroundPalette.onColorFor(swatches.secondary)
    val tertiary = Color(swatches.tertiary)
    val onTertiary = BackgroundPalette.onColorFor(swatches.tertiary)

    val surfaceRgb = swatches.surface
    val surface = if (dark) {
        Color(surfaceRgb).let {
            Color(it.red * 0.25f, it.green * 0.25f, it.blue * 0.25f)
        }
    } else {
        Color(
            red = (Color(surfaceRgb).red + 1f) / 2f,
            green = (Color(surfaceRgb).green + 1f) / 2f,
            blue = (Color(surfaceRgb).blue + 1f) / 2f,
        )
    }
    val onSurface = BackgroundPalette.onColorFor(surface.toArgb())

    val primaryContainer = if (dark) {
        Color(swatches.primary).let {
            Color(it.red * 0.4f, it.green * 0.4f, it.blue * 0.4f)
        }
    } else {
        Color(
            red = (Color(swatches.primary).red + 1f) / 2f,
            green = (Color(swatches.primary).green + 1f) / 2f,
            blue = (Color(swatches.primary).blue + 1f) / 2f,
        )
    }
    val onPrimaryContainer = BackgroundPalette.onColorFor(primaryContainer.toArgb())

    val base = if (dark) DarkColorScheme.copy() else LightColorScheme.copy()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        tertiaryContainer = tertiary,
        onTertiaryContainer = onTertiary,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surface,
        onSurfaceSecondary = onSurface,
        outline = onSurface.copy(alpha = 0.5f),
        dividerLine = onSurface.copy(alpha = 0.2f),
        primaryVariant = primary,
        onPrimaryVariant = onPrimary,
        secondaryVariant = secondary,
        onSecondaryVariant = onSecondary,
    )
}

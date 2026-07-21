package com.example.radioarealocator.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
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
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
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

/**
 * 应用主题。
 *
 * 莫奈取色使用 Miuix 内置的 [ThemeController] + [ColorSchemeMode.MonetSystem]：
 * - monetEnabled = true 且设置了背景图：从背景图提取主色作为 keyColor，由 Miuix 用 HCT
 *   算法生成完整 Material 色板。
 * - monetEnabled = true 但无背景图：keyColor = null，Miuix 自动取系统壁纸主色
 *   （即 Android Material You 系统取色）。
 * - monetEnabled = false：使用 [ColorSchemeMode.System] + 默认蓝色 #3482FF 色板
 *   （[LightColorScheme] / [DarkColorScheme]）。
 */
@Composable
fun RadioAreaLocatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    backgroundUri: android.net.Uri? = null,
    monetEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // 从背景图提取的主色（作为 Monet keyColor）；monetEnabled 关闭或无背景图时为 null。
    var monetKeyColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(backgroundUri, monetEnabled) {
        monetKeyColor = if (monetEnabled) {
            backgroundUri?.let { uri -> BackgroundPalette.extractKeyColor(context, uri) }
        } else {
            null
        }
    }

    // ThemeController 所有属性为 val，keyColor / monetEnabled 变化时需要重建实例
    val controller = remember(monetEnabled, monetKeyColor, darkTheme) {
        if (monetEnabled) {
            ThemeController(
                colorSchemeMode = ColorSchemeMode.MonetSystem,
                lightColors = LightColorScheme,
                darkColors = DarkColorScheme,
                keyColor = monetKeyColor,
                isDark = darkTheme
            )
        } else {
            ThemeController(
                colorSchemeMode = ColorSchemeMode.System,
                lightColors = LightColorScheme,
                darkColors = DarkColorScheme,
                isDark = darkTheme
            )
        }
    }

    // 同步状态栏色与外观，背景图存在时让状态栏透明以便背景图延伸到顶部
    val view = LocalView.current
    // currentColors() 是 @Composable 方法，需在 Composable 上下文调用，不能放进 SideEffect
    val currentColors = if (!view.isInEditMode) controller.currentColors() else null
    if (!view.isInEditMode) {
        SideEffect {
            val ctx = view.context
            if (ctx is Activity) {
                val window = ctx.window
                window.statusBarColor = if (backgroundUri != null) {
                    AndroidColor.TRANSPARENT
                } else {
                    currentColors?.surface?.toArgb() ?: AndroidColor.TRANSPARENT
                }
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MiuixTheme(
        controller = controller,
        textStyles = AppTextStyles,
        content = content
    )
}

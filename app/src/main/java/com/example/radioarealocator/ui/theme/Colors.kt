package com.example.radioarealocator.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

val keyColorOptions = listOf(
    Color(0xFFEF5350).toArgb(),  // Red 400
    Color(0xFFEC407A).toArgb(),  // Pink 400
    Color(0xFFAB47BC).toArgb(),  // Purple 400
    Color(0xFF7E57C2).toArgb(),  // Deep Purple 400
    Color(0xFF5C6BC0).toArgb(),  // Indigo 400
    Color(0xFF42A5F5).toArgb(),  // Blue 400
    Color(0xFF26C6DA).toArgb(),  // Cyan 400
    Color(0xFF26A69A).toArgb(),  // Teal 400
    Color(0xFF66BB6A).toArgb(),  // Green 400
    Color(0xFFFFEE58).toArgb(),  // Yellow 400
    Color(0xFFFFCA28).toArgb(),  // Amber 400
    Color(0xFFFFA726).toArgb(),  // Orange 400
    Color(0xFF8D6E63).toArgb(),  // Brown 400
    Color(0xFF78909C).toArgb(),  // Blue Grey 400
    Color(0xFFF48FB1).toArgb(),  // Sakura (Pink 200)
)

// 背景图功能已移除，卡片透明度固定为完全不透明。保留 CompositionLocal 以兼容旧业务代码。
val LocalCardAlpha = staticCompositionLocalOf { 1.0f }

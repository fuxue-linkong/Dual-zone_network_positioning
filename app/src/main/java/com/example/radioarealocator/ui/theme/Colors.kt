package com.example.radioarealocator.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 背景图功能已移除，卡片透明度固定为完全不透明。保留 CompositionLocal 以兼容旧业务代码。
val LocalCardAlpha = staticCompositionLocalOf { 1.0f }

/**
 * 关键色种子列表 - 使用 [SafeColors.keyColorSeed] 作为唯一定义来源。
 *
 * 已弃用：请直接使用 [SafeColors.keyColorSeed]。
 */
val keyColorOptions: List<Int> get() = SafeColors.keyColorSeed

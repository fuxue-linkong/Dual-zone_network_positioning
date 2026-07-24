package com.example.radioarealocator.ui.viewmodel

import androidx.compose.runtime.Immutable
import com.example.radioarealocator.ui.UiMode
import com.example.radioarealocator.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableBlur: Boolean,
    val enableFloatingBottomBar: Boolean,
    val enableFloatingBottomBarBlur: Boolean,
    val uiMode: UiMode,
)

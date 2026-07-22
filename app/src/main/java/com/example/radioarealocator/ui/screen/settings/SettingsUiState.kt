package com.example.radioarealocator.ui.screen.settings

import androidx.compose.runtime.Immutable
import com.example.radioarealocator.data.reminder.ReminderItem
import com.example.radioarealocator.data.reminder.ReminderSettings
import com.example.radioarealocator.ui.util.LatestVersionInfo
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.example.radioarealocator.ui.UiMode

@Immutable
data class SettingsUiState(
    val uiMode: String = UiMode.DEFAULT_VALUE,
    val checkUpdate: Boolean = true,
    val themeMode: Int = 0,
    val miuixMonet: Boolean = false,
    val keyColor: Int = 0,
    val colorStyle: String = PaletteStyle.TonalSpot.name,
    val colorSpec: String = ColorSpec.SpecVersion.Default.name,
    val enablePredictiveBack: Boolean = false,
    val enableBlur: Boolean = true,
    val enableFloatingBottomBar: Boolean = true,
    val enableFloatingBottomBarBlur: Boolean = true,
    val pageScale: Float = 1.0f,
    // ── 更新检查与下载 ──
    val updateChecking: Boolean = false,
    val updateAvailable: Boolean = false,
    val updateError: Boolean = false,
    val latestVersionInfo: LatestVersionInfo = LatestVersionInfo(),
    /** 下载进度 0-100，-1 表示未在下载，100 表示已完成 */
    val downloadProgress: Int = -1,
)

/**
 * 业务设置聚合：卫星源、提醒设置、提醒列表。
 * 从 [com.example.radioarealocator.ui.MainViewModel] 收集。
 */
@Immutable
data class SettingsBusinessState(
    val satelliteSource: String = "ALL",
    val reminderSettings: ReminderSettings = ReminderSettings(),
    val reminderItems: List<ReminderItem> = emptyList(),
)

@Immutable
data class SettingsScreenActions(
    val onSetCheckUpdate: (Boolean) -> Unit,
    val onOpenTheme: () -> Unit,
    val onSetUiModeIndex: (Int) -> Unit,
    val onOpenAbout: () -> Unit,
    // 业务相关回调
    val onSetSatelliteSource: (String) -> Unit = {},
    val onUpdateReminderSettings: (ReminderSettings) -> Unit = {},
    val onOpenReminderList: () -> Unit = {},
    // 更新相关回调
    val onCheckUpdateNow: () -> Unit = {},
    val onDownloadAndInstall: () -> Unit = {},
    val onClearUpdateResult: () -> Unit = {},
)

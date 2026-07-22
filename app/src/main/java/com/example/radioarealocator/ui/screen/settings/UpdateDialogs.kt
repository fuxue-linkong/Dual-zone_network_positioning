package com.example.radioarealocator.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.BuildConfig
import com.example.radioarealocator.R
import com.example.radioarealocator.ui.LocalUiMode
import com.example.radioarealocator.ui.UiMode
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 更新检查与下载安装对话框（双主题）。
 *
 * 根据 [SettingsUiState] 的更新状态渲染对应对话框：
 * - [SettingsUiState.updateChecking] → loading 对话框
 * - [SettingsUiState.updateAvailable] → 新版本对话框（含下载按钮）
 * - [SettingsUiState.updateError] → 错误对话框
 * - 检查完成但无更新（latestVersionInfo.versionName 非空且 !updateAvailable）→ "已是最新"对话框
 *
 * 关闭对话框时调用 [SettingsScreenActions.onClearUpdateResult] 重置状态。
 */
@Composable
fun UpdateDialogs(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> UpdateDialogsMiuix(uiState, actions)
        UiMode.Material -> UpdateDialogsMaterial(uiState, actions)
    }
}

@Composable
private fun UpdateDialogsMiuix(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
) {
    val info = uiState.latestVersionInfo

    // 检查中：loading 对话框
    if (uiState.updateChecking) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.settings_check_update),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    InfiniteProgressIndicator(color = MiuixTheme.colorScheme.onBackground)
                    MiuixText(
                        modifier = Modifier.padding(start = 12.dp),
                        text = stringResource(R.string.update_checking),
                    )
                }
            },
        )
        return
    }

    // 下载中：进度对话框
    if (uiState.downloadProgress in 0..99) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.update_download),
            content = {
                MiuixText(text = stringResource(R.string.update_downloading, uiState.downloadProgress))
            },
        )
        return
    }

    // 下载失败：错误对话框
    if (uiState.updateError) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.settings_check_update),
            onDismissRequest = actions.onClearUpdateResult,
            content = {
                Column {
                    MiuixText(text = stringResource(R.string.update_download_failed))
                    MiuixDialogButtons(
                        confirmText = stringResource(R.string.confirm),
                        onConfirm = actions.onClearUpdateResult,
                    )
                }
            },
        )
        return
    }

    // 发现新版本：更新对话框
    if (uiState.updateAvailable) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.update_available_title),
            onDismissRequest = actions.onClearUpdateResult,
            content = {
                Column {
                    MiuixText(text = "${stringResource(R.string.update_current_version)}: ${BuildConfig.VERSION_NAME}")
                    Spacer(Modifier.height(4.dp))
                    MiuixText(text = "${stringResource(R.string.update_latest_version)}: ${info.versionName}")
                    if (info.changelog.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        MiuixText(
                            text = info.changelog,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                    }
                    MiuixDialogButtons(
                        confirmText = stringResource(R.string.update_download),
                        onConfirm = actions.onDownloadAndInstall,
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = actions.onClearUpdateResult,
                    )
                }
            },
        )
        return
    }

    // 检查完成但无更新：已是最新
    if (info.versionName.isNotEmpty()) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.settings_check_update),
            onDismissRequest = actions.onClearUpdateResult,
            content = {
                Column {
                    MiuixText(text = stringResource(R.string.update_latest))
                    MiuixDialogButtons(
                        confirmText = stringResource(R.string.confirm),
                        onConfirm = actions.onClearUpdateResult,
                    )
                }
            },
        )
    }
}

/**
 * Miuix 风格对话框底部按钮区，参考 [com.example.radioarealocator.ui.component.dialog.ConfirmDialogMiuix]。
 * 确认按钮使用 primary 配色，取消按钮默认配色；通过 [LocalDismissState] 关闭对话框。
 */
@Composable
private fun MiuixDialogButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val dismissState = LocalDismissState.current
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
    ) {
        if (dismissText != null) {
            MiuixTextButton(
                text = dismissText,
                onClick = {
                    onDismiss?.invoke()
                    dismissState?.invoke()
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(20.dp))
        }
        MiuixTextButton(
            text = confirmText,
            onClick = {
                onConfirm()
                dismissState?.invoke()
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}

@Composable
private fun UpdateDialogsMaterial(
    uiState: SettingsUiState,
    actions: SettingsScreenActions,
) {
    val info = uiState.latestVersionInfo

    // 检查中：loading 对话框
    if (uiState.updateChecking) {
        AlertDialog(
            onDismissRequest = { /* 检查中不可关闭 */ },
            confirmButton = {},
            title = { Text(stringResource(R.string.settings_check_update)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.update_checking),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        )
        return
    }

    // 下载中：进度对话框
    if (uiState.downloadProgress in 0..99) {
        AlertDialog(
            onDismissRequest = { /* 下载中不可关闭 */ },
            confirmButton = {},
            title = { Text(stringResource(R.string.update_download)) },
            text = {
                Text(
                    text = stringResource(R.string.update_downloading, uiState.downloadProgress),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
        return
    }

    // 下载失败：错误对话框
    if (uiState.updateError) {
        AlertDialog(
            onDismissRequest = actions.onClearUpdateResult,
            confirmButton = {
                TextButton(onClick = actions.onClearUpdateResult) {
                    Text(stringResource(R.string.confirm))
                }
            },
            title = { Text(stringResource(R.string.settings_check_update)) },
            text = {
                Text(
                    text = stringResource(R.string.update_download_failed),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
        return
    }

    // 发现新版本：更新对话框
    if (uiState.updateAvailable) {
        AlertDialog(
            onDismissRequest = actions.onClearUpdateResult,
            confirmButton = {
                TextButton(onClick = actions.onDownloadAndInstall) {
                    Text(stringResource(R.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = actions.onClearUpdateResult) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Column {
                    Text(
                        text = "${stringResource(R.string.update_current_version)}: ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${stringResource(R.string.update_latest_version)}: ${info.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (info.changelog.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = info.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
        return
    }

    // 检查完成但无更新：已是最新
    if (info.versionName.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = actions.onClearUpdateResult,
            confirmButton = {
                TextButton(onClick = actions.onClearUpdateResult) {
                    Text(stringResource(R.string.confirm))
                }
            },
            title = { Text(stringResource(R.string.settings_check_update)) },
            text = {
                Text(
                    text = stringResource(R.string.update_latest),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }
}

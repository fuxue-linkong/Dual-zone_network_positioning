package com.example.radioarealocator.ui.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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

/**
 * 更新检查与下载安装对话框（双主题共用）。
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

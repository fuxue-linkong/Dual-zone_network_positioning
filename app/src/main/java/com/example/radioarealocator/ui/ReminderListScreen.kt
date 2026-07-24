package com.example.radioarealocator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.R
import com.example.radioarealocator.data.reminder.ReminderItem
import com.example.radioarealocator.ui.theme.LocalCardAlpha
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReminderListScreen(
    items: List<ReminderItem>,
    onToggleEnabled: (Int, Boolean) -> Unit,
    onDelete: (Int) -> Unit,
    contentPadding: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        if (items.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.catalogNumber }) { item ->
                    ReminderItemRow(
                        item = item,
                        onToggleEnabled = { onToggleEnabled(item.catalogNumber, it) },
                        onDelete = { onDelete(item.catalogNumber) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.SatelliteAlt,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.reminder_list_empty),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun ReminderItemRow(
    item: ReminderItem,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val localFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    val utcFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.of("UTC"))

    val aosLocal = localFormatter.format(Instant.ofEpochMilli(item.aosTimeMillis))
    val losLocal = localFormatter.format(Instant.ofEpochMilli(item.losTimeMillis))
    val aosUtc = utcFormatter.format(Instant.ofEpochMilli(item.aosTimeMillis))

    val modeText = if (item.modes.isEmpty()) {
        stringResource(R.string.mode_unknown)
    } else {
        item.modes.joinToString("/")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (item.enabled) {
                MiuixTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
            } else {
                MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f * LocalCardAlpha.current)
            },
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.enabled) {
                            MiuixTheme.colorScheme.onSurface
                        } else {
                            MiuixTheme.colorScheme.onSurfaceSecondary
                        }
                    )
                    Text(
                        text = "NORAD: ${item.catalogNumber}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
                Switch(
                    checked = item.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }
            Text(
                text = "AOS: $aosLocal (UTC $aosUtc)",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "LOS: $losLocal | ${stringResource(R.string.elevation)}: ${item.maxElevation.toInt()}° | ${stringResource(R.string.aos_azimuth)}: ${item.aosAzimuth}°",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Text(
                text = "${stringResource(R.string.mode)}: $modeText",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MiuixTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

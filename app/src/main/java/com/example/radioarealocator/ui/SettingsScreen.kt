package com.example.radioarealocator.ui

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.radioarealocator.R
import com.example.radioarealocator.data.reminder.ReminderSettings
import com.example.radioarealocator.data.reminder.RepeatMode
import com.example.radioarealocator.ui.theme.LocalCardAlpha

@Composable
fun SettingsScreen(
    satelliteSource: String,
    onSourceSelected: (String) -> Unit,
    backgroundUri: Uri?,
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    cardOpacity: Int,
    onCardOpacityChange: (Int) -> Unit,
    backgroundOpacity: Int,
    onBackgroundOpacityChange: (Int) -> Unit,
    onAboutClick: () -> Unit,
    reminderSettings: ReminderSettings,
    onUpdateReminderSettings: (ReminderSettings) -> Unit,
    onOpenReminderList: () -> Unit,
    contentPadding: PaddingValues
) {
    val options = listOf("ALL", "SNOGS", "AMSAT")
    val labels = listOf(
        stringResource(R.string.source_all),
        stringResource(R.string.source_satnogs),
        stringResource(R.string.source_amsat)
    )
    val selectedIndex = options.indexOf(satelliteSource).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.satellite_source),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "选择卫星 TLE 数据的来源",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    SourceDropdown(
                        labels = labels,
                        selectedIndex = selectedIndex,
                        onSelected = { onSourceSelected(options[it]) }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.background_image),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.background_image_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.background_pick)) },
                        supportingContent = {
                            Text(
                                if (backgroundUri != null) {
                                    stringResource(R.string.background_set)
                                } else {
                                    stringResource(R.string.background_unset)
                                }
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            if (backgroundUri != null) {
                                TextButton(onClick = onClearBackground) {
                                    Text(stringResource(R.string.clear))
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onPickBackground)
                    )
                    // 仅在已设置背景图时显示卡片透明度调节
                    if (backgroundUri != null) {
                        HorizontalDividerLight()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.card_opacity),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.card_opacity_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = stringResource(R.string.card_opacity_format, cardOpacity),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = cardOpacity.toFloat(),
                            onValueChange = { onCardOpacityChange(it.toInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        HorizontalDividerLight()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.background_opacity),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.background_opacity_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = stringResource(R.string.background_opacity_format, backgroundOpacity),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = backgroundOpacity.toFloat(),
                            onValueChange = { onBackgroundOpacityChange(it.toInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        // 日程提醒设置卡片
        item {
            ReminderSettingsCard(
                settings = reminderSettings,
                onUpdate = onUpdateReminderSettings,
                onOpenList = onOpenReminderList
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_app)) },
                    supportingContent = { Text(stringResource(R.string.about_description)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onAboutClick)
                )
            }
        }
    }
}

@Composable
private fun ReminderSettingsCard(
    settings: ReminderSettings,
    onUpdate: (ReminderSettings) -> Unit,
    onOpenList: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.reminder_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = stringResource(R.string.reminder_section_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            // 总开关
            ListItem(
                headlineContent = { Text(stringResource(R.string.reminder_master_switch)) },
                leadingContent = {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { onUpdate(settings.copy(enabled = it)) }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // 仅在启用时显示详细配置
            if (settings.enabled) {
                HorizontalDividerLight()

                // 提前提醒时间
                LeadTimeRow(
                    currentMinutes = settings.leadMinutes,
                    onSelected = { onUpdate(settings.copy(leadMinutes = it)) }
                )

                HorizontalDividerLight()

                // 重复选项
                RepeatModeRow(
                    current = settings.repeatMode,
                    onSelected = { onUpdate(settings.copy(repeatMode = it)) }
                )

                HorizontalDividerLight()

                // 通知声音
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reminder_sound)) },
                    leadingContent = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = settings.soundEnabled,
                            onCheckedChange = { onUpdate(settings.copy(soundEnabled = it)) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDividerLight()

                // 通知振动
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reminder_vibration)) },
                    leadingContent = { Icon(Icons.Default.Vibration, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = settings.vibrationEnabled,
                            onCheckedChange = { onUpdate(settings.copy(vibrationEnabled = it)) }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDividerLight()

                // 查看提醒列表入口
                ListItem(
                    headlineContent = { Text(stringResource(R.string.reminder_view_list)) },
                    leadingContent = { Icon(Icons.Default.Alarm, contentDescription = null) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = onOpenList)
                )
            }
        }
    }
}

@Composable
private fun LeadTimeRow(
    currentMinutes: Int,
    onSelected: (Int) -> Unit
) {
    val options = listOf(5, 10, 15, 30)
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = stringResource(R.string.reminder_lead_minutes_format, currentMinutes)

    ListItem(
        headlineContent = { Text(stringResource(R.string.reminder_lead_time)) },
        supportingContent = { Text(currentLabel) },
        leadingContent = { Icon(Icons.Default.Alarm, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { expanded = true }
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        options.forEach { minutes ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reminder_lead_minutes_format, minutes)) },
                onClick = {
                    onSelected(minutes)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun RepeatModeRow(
    current: RepeatMode,
    onSelected: (RepeatMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = when (current) {
        RepeatMode.ALWAYS -> stringResource(R.string.reminder_repeat_always)
        RepeatMode.DAYTIME_ONLY -> stringResource(R.string.reminder_repeat_daytime)
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.reminder_repeat_mode)) },
        supportingContent = { Text(currentLabel) },
        leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { expanded = true }
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        RepeatMode.entries.forEach { mode ->
            val label = when (mode) {
                RepeatMode.ALWAYS -> stringResource(R.string.reminder_repeat_always)
                RepeatMode.DAYTIME_ONLY -> stringResource(R.string.reminder_repeat_daytime)
            }
            DropdownMenuItem(
                text = { Text(label) },
                onClick = {
                    onSelected(mode)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun SourceDropdown(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = labels.getOrNull(selectedIndex) ?: ""

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            labels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun HorizontalDividerLight() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f * LocalCardAlpha.current)
    )
}

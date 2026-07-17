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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.menu.OverlayDropdownMenu
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val options = listOf("ALL", "CT", "SNOGS")
    val labels = listOf(
        stringResource(R.string.source_all),
        stringResource(R.string.source_celestrak),
        stringResource(R.string.source_satnogs)
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
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.satellite_source),
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.satellite_source_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.background_image),
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.background_image_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    ArrowPreference(
                        title = stringResource(R.string.background_pick),
                        summary = if (backgroundUri != null) stringResource(R.string.background_set) else stringResource(R.string.background_unset),
                        startAction = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        endActions = {
                            if (backgroundUri != null) {
                                TextButton(
                                    text = stringResource(R.string.clear),
                                    onClick = onClearBackground
                                )
                            }
                        },
                        onClick = onPickBackground
                    )
                    if (backgroundUri != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f * LocalCardAlpha.current)
                        )
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
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.card_opacity_desc),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                            Text(
                                text = stringResource(R.string.card_opacity_format, cardOpacity),
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary
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
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f * LocalCardAlpha.current)
                        )
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
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.background_opacity_desc),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                            Text(
                                text = stringResource(R.string.background_opacity_format, backgroundOpacity),
                                style = MiuixTheme.textStyles.title4,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.primary
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

        item {
            ReminderSettingsCard(
                settings = reminderSettings,
                onUpdate = onUpdateReminderSettings,
                onOpenList = onOpenReminderList
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                ArrowPreference(
                    title = stringResource(R.string.about_app),
                    summary = stringResource(R.string.about_description),
                    startAction = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    },
                    onClick = onAboutClick
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
            contentColor = MiuixTheme.colorScheme.onSurface
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
                    tint = MiuixTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.reminder_section_title),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = stringResource(R.string.reminder_section_desc),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )

            SwitchPreference(
                title = stringResource(R.string.reminder_master_switch),
                checked = settings.enabled,
                onCheckedChange = { onUpdate(settings.copy(enabled = it)) },
                startAction = {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                }
            )

            if (settings.enabled) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f * LocalCardAlpha.current)
                )

                LeadTimeRow(
                    currentMinutes = settings.leadMinutes,
                    onSelected = { onUpdate(settings.copy(leadMinutes = it)) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f * LocalCardAlpha.current)
                )

                RepeatModeRow(
                    current = settings.repeatMode,
                    onSelected = { onUpdate(settings.copy(repeatMode = it)) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f * LocalCardAlpha.current)
                )

                SwitchPreference(
                    title = stringResource(R.string.reminder_sound),
                    checked = settings.soundEnabled,
                    onCheckedChange = { onUpdate(settings.copy(soundEnabled = it)) },
                    startAction = {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f * LocalCardAlpha.current)
                )

                SwitchPreference(
                    title = stringResource(R.string.reminder_vibration),
                    checked = settings.vibrationEnabled,
                    onCheckedChange = { onUpdate(settings.copy(vibrationEnabled = it)) },
                    startAction = {
                        Icon(Icons.Default.Vibration, contentDescription = null)
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.5f * LocalCardAlpha.current)
                )

                ArrowPreference(
                    title = stringResource(R.string.reminder_view_list),
                    summary = null,
                    startAction = {
                        Icon(Icons.Default.Alarm, contentDescription = null)
                    },
                    onClick = onOpenList
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
    val items = remember(options) {
        options.map { minutes ->
            DropdownItem(
                text = stringResource(R.string.reminder_lead_minutes_format, minutes),
                selected = minutes == currentMinutes,
                onClick = { onSelected(minutes) }
            )
        }
    }
    OverlayDropdownMenu(
        entry = DropdownEntry(items = items),
        title = stringResource(R.string.reminder_lead_time),
        summary = stringResource(R.string.reminder_lead_minutes_format, currentMinutes),
        startAction = {
            Icon(Icons.Default.Alarm, contentDescription = null)
        }
    )
}

@Composable
private fun RepeatModeRow(
    current: RepeatMode,
    onSelected: (RepeatMode) -> Unit
) {
    val currentLabel = when (current) {
        RepeatMode.ALWAYS -> stringResource(R.string.reminder_repeat_always)
        RepeatMode.DAYTIME_ONLY -> stringResource(R.string.reminder_repeat_daytime)
    }
    val items = remember {
        RepeatMode.entries.map { mode ->
            val label = when (mode) {
                RepeatMode.ALWAYS -> stringResource(R.string.reminder_repeat_always)
                RepeatMode.DAYTIME_ONLY -> stringResource(R.string.reminder_repeat_daytime)
            }
            DropdownItem(
                text = label,
                selected = mode == current,
                onClick = { onSelected(mode) }
            )
        }
    }
    OverlayDropdownMenu(
        entry = DropdownEntry(items = items),
        title = stringResource(R.string.reminder_repeat_mode),
        summary = currentLabel,
        startAction = {
            Icon(Icons.Default.Notifications, contentDescription = null)
        }
    )
}

@Composable
private fun SourceDropdown(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    val items = remember(labels, selectedIndex) {
        labels.mapIndexed { index, label ->
            DropdownItem(
                text = label,
                selected = index == selectedIndex,
                onClick = { onSelected(index) }
            )
        }
    }
    OverlayDropdownMenu(
        entry = DropdownEntry(items = items),
        title = stringResource(R.string.satellite_source),
        summary = labels.getOrElse(selectedIndex) { "" }
    )
}

package com.example.radioarealocator.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.radioarealocator.R
import com.example.radioarealocator.permission.PermissionState
import com.example.radioarealocator.ui.WeatherCard
import com.example.radioarealocator.ui.component.material.TonalCard
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomePagerMaterial(
    state: HomeUiState,
    businessState: HomeBusinessState,
    permissionState: PermissionState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = { TopBar(scrollBehavior = scrollBehavior) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 业务卡片使用 Miuix 主题色板，外层包裹 MiuixTheme 以确保渲染正确
            MiuixTheme {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DailyQuoteCard(businessState.dailyQuote)
                    // 条件卡：定位未授权 → 权限引导卡；定位已授权 → 时间排布 + 定位详情入口
                    if (permissionState.requiredGranted) {
                        SatelliteListCard(businessState, actions)
                        LocationEntryCard(actions.onLocationDetailClick)
                    } else {
                        PermissionCard(permissionState, actions.onPermissionsClick)
                    }
                    WeatherCard(
                        weather = businessState.weather,
                        isLoading = businessState.weatherLoading,
                        error = businessState.weatherError,
                        nextSatellite = businessState.nextSatellite,
                        onRefresh = actions.onRefreshWeather,
                    )
                    CwEntryCard(actions.onCWPracticeClick)
                }
            }
            InfoCard(systemInfo = state.systemInfo)
            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun PermissionCard(
    state: PermissionState,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor =
                if (state.requiredGranted) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            contentColor =
                if (state.requiredGranted) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.permission_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            if (state.requiredGranted) {
                                stringResource(R.string.permission_ready)
                            } else {
                                stringResource(R.string.permission_missing)
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AssistChip(
                    onClick = { },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor =
                            if (state.requiredGranted) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        leadingIconContentColor =
                            if (state.requiredGranted) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                    ),
                    label = {
                        Text(
                            if (state.requiredGranted) {
                                stringResource(R.string.permission_granted)
                            } else {
                                stringResource(R.string.permission_action_required)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector =
                                if (state.requiredGranted) Icons.Default.CheckCircle
                                else Icons.Default.ErrorOutline,
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DailyQuoteCard(quote: String) {
    TonalCard {
        Text(
            text = quote,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LocationEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.location_detail_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.location_detail_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SatelliteListCard(
    state: HomeBusinessState,
    actions: HomeActions,
) {
    val sats = state.satellites
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.nearby_satellites),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = actions.onSatelliteManagementClick) {
                    Text(stringResource(R.string.satellite_management))
                }
            }
            Spacer(Modifier.height(8.dp))
            when {
                sats.isSatelliteLoading && sats.satellites.isEmpty() -> Text(
                    text = stringResource(R.string.processing),
                    style = MaterialTheme.typography.bodyMedium
                )
                sats.satelliteError != null && sats.satellites.isEmpty() -> Text(
                    text = stringResource(R.string.satellite_load_failed, sats.satelliteError),
                    color = MaterialTheme.colorScheme.error
                )
                sats.satellites.isEmpty() -> Text(
                    text = stringResource(R.string.no_satellites),
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> {
                    val timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
                        .withZone(ZoneId.systemDefault())
                    sats.satellites.take(5).forEach { sat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${sat.modes} · ${if (sat.isCurrentlyVisible) stringResource(R.string.in_pass) else stringResource(R.string.upcoming)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.aos_time) + ": " + timeFormatter.format(sat.aosTime),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.max_elevation) + ": ${sat.maxElevation.toInt()}°",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CwEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.cw_practice),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.cw_practice_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoCard(systemInfo: SystemInfo) {
    TonalCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
            @Composable
            fun InfoCardItem(label: String, content: String) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            InfoCardItem(stringResource(R.string.home_app_version), systemInfo.appVersion)
        }
    }
}

package com.example.radioarealocator.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Cancel
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
import com.example.radioarealocator.ui.AMapCard
import com.example.radioarealocator.ui.WeatherCard
import com.example.radioarealocator.ui.theme.LocalEnableBlur
import com.example.radioarealocator.ui.util.BlurredBar
import com.example.radioarealocator.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomePagerMiuix(
    state: HomeUiState,
    businessState: HomeBusinessState,
    permissionState: PermissionState,
    actions: HomeActions,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    Scaffold(
        topBar = {
            TopBar(
                scrollBehavior = scrollBehavior,
                backdrop = backdrop,
                barColor = barColor,
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = innerPadding,
                overscrollEffect = null,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // 每日一言
                        DailyQuoteCard(businessState.dailyQuote)
                        // 权限卡片：引导用户授权定位
                        PermissionCardMiuix(permissionState, actions.onPermissionsClick)
                        // 定位卡：CQ/ITU/梅登兰德 + 经纬度 + 地址
                        LocationCardMiuix(businessState, actions.onRefreshLocation)
                        // 天气卡
                        WeatherCard(
                            weather = businessState.weather,
                            isLoading = businessState.weatherLoading,
                            error = businessState.weatherError,
                            nextSatellite = businessState.nextSatellite,
                            onRefresh = actions.onRefreshWeather,
                        )
                        // 卫星列表卡：附近过境卫星
                        SatelliteListCardMiuix(businessState, actions)
                        // 地图卡
                        businessState.location.result?.let { loc ->
                            AMapCard(latitude = loc.latitude, longitude = loc.longitude)
                        }
                        // CW 练习入口
                        CwEntryCardMiuix(actions.onCWPracticeClick)
                        // 应用版本信息
                        InfoCard(systemInfo = state.systemInfo)
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

@Composable
private fun PermissionCardMiuix(
    state: PermissionState,
    onClick: () -> Unit,
) {
    val requiredGranted = state.requiredGranted
    val iconColor = if (requiredGranted) Color(0xFF36D167) else Color(0xFFF72727)
    val containerColor = if (requiredGranted) Color(0xFFDFFAE4) else Color(0xFFF8E2E2)
    val textColor = Color(0xFF111111)
    val summary =
        if (requiredGranted) {
            stringResource(R.string.permission_ready)
        } else {
            stringResource(R.string.permission_missing)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = containerColor),
        onClick = onClick,
        showIndication = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 16.dp, bottom = 16.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    modifier = Modifier.size(120.dp),
                    imageVector =
                        if (requiredGranted) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Cancel,
                    tint = iconColor,
                    contentDescription = null,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, top = 28.dp, end = 148.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text =
                            if (requiredGranted) {
                                stringResource(R.string.permission_status_ready_title)
                            } else {
                                stringResource(R.string.permission_status_missing_title)
                            },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                    )
                    Text(
                        text = summary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor.copy(alpha = 0.72f),
                    )
                }
                Text(
                    text =
                        if (requiredGranted) {
                            stringResource(R.string.permission_granted)
                        } else {
                            stringResource(R.string.permission_action_required)
                        },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    scrollBehavior: ScrollBehavior,
    backdrop: LayerBackdrop?,
    barColor: Color,
) {
    BlurredBar(backdrop) {
        TopAppBar(
            color = barColor,
            title = stringResource(R.string.app_name),
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
private fun DailyQuoteCard(quote: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = quote,
                fontSize = 14.sp,
                color = colorScheme.onSurfaceVariantSummary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun LocationCardMiuix(
    state: HomeBusinessState,
    onRefresh: () -> Unit,
) {
    val loc = state.location
    val result = loc.result
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
                    text = stringResource(R.string.location_status),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                top.yukonga.miuix.kmp.basic.TextButton(
                    onClick = onRefresh,
                    text = if (loc.isLoading) stringResource(R.string.locating) else stringResource(R.string.action_refresh)
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                loc.isLoading && result == null -> Text(
                    text = stringResource(R.string.locating),
                    color = colorScheme.onSurfaceVariantSummary
                )
                loc.error != null && result == null -> Text(
                    text = stringResource(R.string.location_failed),
                    color = colorScheme.onError
                )
                result != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LocationRow(stringResource(R.string.latitude), "%.4f°".format(result.latitude))
                    LocationRow(stringResource(R.string.longitude), "%.4f°".format(result.longitude))
                    result.cqZone?.let { LocationRow(stringResource(R.string.cq_zone), it.toString()) }
                    result.ituZone?.let { LocationRow(stringResource(R.string.itu_zone), it.toString()) }
                    LocationRow(stringResource(R.string.maidenhead), result.maidenhead)
                    if (loc.lastLocationCity.isNotBlank()) {
                        LocationRow(stringResource(R.string.address), loc.lastLocationCity)
                    }
                }
                else -> Text(
                    text = stringResource(R.string.tap_to_locate),
                    color = colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun LocationRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, color = colorScheme.onSurfaceVariantSummary, fontSize = 14.sp)
        Text(text = value, color = colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SatelliteListCardMiuix(
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                top.yukonga.miuix.kmp.basic.TextButton(
                    onClick = actions.onSatelliteManagementClick,
                    text = stringResource(R.string.satellite_management)
                )
            }
            Spacer(Modifier.height(8.dp))
            when {
                sats.isSatelliteLoading && sats.satellites.isEmpty() -> Text(
                    text = stringResource(R.string.processing),
                    color = colorScheme.onSurfaceVariantSummary
                )
                sats.satelliteError != null && sats.satellites.isEmpty() -> Text(
                    text = stringResource(R.string.satellite_load_failed, sats.satelliteError),
                    color = colorScheme.onError
                )
                sats.satellites.isEmpty() -> Text(
                    text = stringResource(R.string.no_satellites),
                    color = colorScheme.onSurfaceVariantSummary
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
                                    fontSize = 14.sp,
                                    color = colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${sat.modes} · ${if (sat.isCurrentlyVisible) stringResource(R.string.in_pass) else stringResource(R.string.upcoming)}",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.aos_time) + ": " + timeFormatter.format(sat.aosTime),
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                                Text(
                                    text = stringResource(R.string.max_elevation) + ": ${sat.maxElevation.toInt()}°",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariantSummary
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
private fun CwEntryCardMiuix(onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.cw_practice),
            summary = stringResource(R.string.cw_practice_desc),
            onClick = onClick
        )
    }
}

@Composable
private fun InfoCard(systemInfo: SystemInfo) {
    @Composable
    fun InfoText(
        title: String,
        content: String,
        bottomPadding: Dp = 24.dp
    ) {
        Text(
            text = title,
            fontSize = MiuixTheme.textStyles.headline1.fontSize,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface
        )
        Text(
            text = content,
            fontSize = MiuixTheme.textStyles.body2.fontSize,
            color = colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding)
        )
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            InfoText(
                title = stringResource(R.string.home_app_version),
                content = systemInfo.appVersion,
                bottomPadding = 0.dp
            )
        }
    }
}

package com.example.radioarealocator.ui.screen.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.radioarealocator.R
import com.example.radioarealocator.permission.PermissionState
import com.example.radioarealocator.ui.WeatherCard
import com.example.radioarealocator.ui.component.material.TonalCard
import com.example.radioarealocator.ui.theme.LocalCardAlpha
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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
                    // 条件卡：定位未授权 → 权限引导卡 + 独立每日言/天气；定位已授权 → 时间排布（替换权限卡行）
                    if (permissionState.requiredGranted) {
                        // 时间排布：时间卡（含每日言滚动）+ 紧贴的天气卡
                        HomeHeaderMaterial(businessState, actions.onRefreshWeather)
                        // 卫星列表卡：附近过境卫星
                        SatelliteListCard(businessState, actions)
                        // 定位详情入口
                        LocationEntryCard(actions.onLocationDetailClick)
                    } else {
                        // 权限卡片：引导用户授权定位
                        PermissionCard(permissionState, actions.onPermissionsClick)
                        // 未授权时独立显示每日言 + 天气卡
                        DailyQuoteCard(businessState.dailyQuote)
                        WeatherCard(
                            weather = businessState.weather,
                            isLoading = businessState.weatherLoading,
                            error = businessState.weatherError,
                            nextSatellite = businessState.nextSatellite,
                            onRefresh = actions.onRefreshWeather,
                        )
                    }
                    CwEntryCard(actions.onCWPracticeClick)
                }
            }
            Spacer(Modifier.height(bottomInnerPadding))
        }
    }
}

/**
 * 主页时间排布（来自 main 分支 HomeHeader，适配 Material 主题）。
 *
 * 顶部为时间卡：本地时间（大字号）+ 右侧日期（星期 / 年 月 日，"日"放大）+ UTC 时间 +
 * 每日一言水平滚动；时间卡正下方紧贴天气卡。背景色与定位状态色联动。
 * 颜色使用 MiuixTheme.colorScheme 以与 WeatherCard 保持视觉一致（外层已包裹 MiuixTheme）。
 */
@Composable
private fun HomeHeaderMaterial(
    state: HomeBusinessState,
    onRefreshWeather: () -> Unit,
) {
    // 时钟每秒刷新，仅在此组件内部持有，避免 1s tick 触发整个主页重组
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }

    // 状态色与天气卡保持一致：有天气数据 → primary，无 → outline
    val stateColor = if (state.weather != null) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.outline
    }
    val zonedNow = now.atZone(ZoneId.systemDefault())
    val localTime = zonedNow.format(timeFormatter)
    val utcTime = now.atZone(ZoneOffset.UTC).format(timeFormatter)
    val weekday = zonedNow.format(weekdayFormatter)
    val dateYearMonth = "${zonedNow.year}年 ${zonedNow.monthValue}月 "
    val dateDayText = "${zonedNow.dayOfMonth}日"
    val dateLine = remember(stateColor, dateYearMonth, dateDayText) {
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = DATE_FONT_SIZE.sp, color = stateColor)) {
                append(dateYearMonth)
                withStyle(SpanStyle(fontSize = DATE_DAY_FONT_SIZE.sp)) {
                    append(dateDayText)
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 时间卡
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(stateColor.copy(alpha = 0.12f * LocalCardAlpha.current))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = localTime,
                    fontSize = LOCAL_TIME_FONT_SIZE.sp,
                    color = stateColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = weekday,
                        fontSize = DATE_FONT_SIZE.sp,
                        color = stateColor
                    )
                    Text(text = dateLine)
                }
            }
            Text(
                text = "$utcTime UTC",
                fontSize = (LOCAL_TIME_FONT_SIZE * UTC_FONT_SIZE_SCALE).sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
            DailyQuoteScroller(
                quote = state.dailyQuote,
                contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
        // 天气卡：位于时间卡正下方，占满宽度
        WeatherCard(
            weather = state.weather,
            isLoading = state.weatherLoading,
            error = state.weatherError,
            nextSatellite = state.nextSatellite,
            onRefresh = onRefreshWeather,
            modifier = Modifier.fillMaxWidth()
        )
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


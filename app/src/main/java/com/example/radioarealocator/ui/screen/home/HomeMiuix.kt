package com.example.radioarealocator.ui.screen.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Cancel
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
import com.example.radioarealocator.ui.theme.LocalCardAlpha
import com.example.radioarealocator.ui.theme.LocalEnableBlur
import com.example.radioarealocator.ui.theme.SafeColors
import com.example.radioarealocator.ui.util.BlurredBar
import com.example.radioarealocator.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.delay
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
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
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
                        // 条件卡：定位未授权 → 权限引导卡 + 独立每日言/天气；定位已授权 → 时间排布（替换权限卡行）
                        if (permissionState.requiredGranted) {
                            // 时间排布：时间卡（含每日言滚动）+ 紧贴的天气卡
                            HomeHeaderMiuix(businessState, actions.onRefreshWeather)
                            // 卫星列表卡：附近过境卫星
                            SatelliteListCardMiuix(businessState, actions)
                            // 定位详情入口：跳转到定位状态+地图子页面
                            LocationEntryCardMiuix(actions.onLocationDetailClick)
                        } else {
                            // 权限卡片：引导用户授权定位
                            PermissionCardMiuix(permissionState, actions.onPermissionsClick)
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
                        // CW 练习入口
                        CwEntryCardMiuix(actions.onCWPracticeClick)
                    }
                    Spacer(Modifier.height(bottomInnerPadding))
                }
            }
        }
    }
}

/**
 * 主页时间排布（来自 main 分支 HomeHeader，适配 Miuix 主题）。
 *
 * 顶部为时间卡：本地时间（大字号）+ 右侧日期（星期 / 年 月 日，"日"放大）+ UTC 时间 +
 * 每日一言水平滚动；时间卡正下方紧贴天气卡。背景色与定位状态色联动（有定位结果 → primary，无 → outline）。
 */
@Composable
private fun HomeHeaderMiuix(
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
    val stateColor = if (state.weather != null) colorScheme.primary else colorScheme.outline
    val zonedNow = now.atZone(ZoneId.systemDefault())
    val localTime = zonedNow.format(timeFormatter)
    val utcTime = now.atZone(ZoneOffset.UTC).format(timeFormatter)
    // 日期：第一行星期几，第二行"年 月 日"，其中"日"放大
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
            // 本地时间 + 日期并排：时间在左，月/日（两行）在右
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
                color = colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
            // 每日一言：超宽时水平滚动
            DailyQuoteScroller(
                quote = state.dailyQuote,
                contentColor = colorScheme.onSurfaceVariantSummary,
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
private fun PermissionCardMiuix(
    state: PermissionState,
    onClick: () -> Unit,
) {
    val requiredGranted = state.requiredGranted
    val iconColor = if (requiredGranted) SafeColors.successIcon else SafeColors.errorIcon
    val containerColor = if (requiredGranted) SafeColors.successContainer else SafeColors.errorContainer
    val textColor = SafeColors.textPrimaryLight
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
private fun LocationEntryCardMiuix(onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        BasicComponent(
            title = stringResource(R.string.location_detail_title),
            summary = stringResource(R.string.location_detail_desc),
            onClick = onClick
        )
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

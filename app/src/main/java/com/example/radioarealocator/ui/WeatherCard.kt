package com.example.radioarealocator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.radioarealocator.R
import com.example.radioarealocator.data.satellite.SatelliteInfo
import com.example.radioarealocator.data.weather.WeatherResult
import com.example.radioarealocator.data.weather.mapWeatherIcon
import com.example.radioarealocator.ui.theme.LocalCardAlpha
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val weatherDateFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val weatherDateOnlyFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * 天气信息卡片。
 *
 * 显示内容：
 * 1. 实时温度（精确到小数点后一位）+ 天气图标 + 天气状况文字
 * 2. 城市名 + 数据更新时间
 * 3. 卫星过境时刻的天气预测（如存在即将过境的卫星）
 *
 * 状态：
 * - 加载中：显示 CircularProgressIndicator
 * - 错误：显示错误提示 + 重试按钮
 * - 正常：显示天气数据
 *
 * UI 风格与主页时间卡片保持一致：圆角、半透明背景色、primary 文字色。
 *
 * @param weather 天气数据，null 表示尚未加载
 * @param isLoading 是否正在加载
 * @param error 错误消息，null 表示无错误
 * @param nextSatellite 下颗即将过境的卫星，用于显示过境天气预测
 * @param onRefresh 手动刷新回调
 * @param modifier 修饰符
 */
@Composable
fun WeatherCard(
    weather: WeatherResult?,
    isLoading: Boolean,
    error: String?,
    nextSatellite: SatelliteInfo?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateColor = if (weather != null) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(stateColor.copy(alpha = 0.12f * LocalCardAlpha.current))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        when {
            // 加载中且无缓存数据：显示加载动画
            isLoading && weather == null -> LoadingState(stateColor = stateColor)

            // 错误且无缓存数据：显示错误提示 + 重试
            error != null && weather == null -> ErrorState(
                error = error,
                stateColor = stateColor,
                onRetry = onRefresh
            )

            // 有数据（可能在刷新中）：显示天气内容
            weather != null -> WeatherContent(
                weather = weather,
                nextSatellite = nextSatellite,
                stateColor = stateColor,
                isLoading = isLoading,
                onRefresh = onRefresh
            )

            // 初始状态（无数据、无加载、无错误）：提示点击刷新
            else -> InitialState(
                stateColor = stateColor,
                onRefresh = onRefresh
            )
        }
    }
}

/**
 * 天气内容主体。
 */
@Composable
private fun WeatherContent(
    weather: WeatherResult,
    nextSatellite: SatelliteInfo?,
    stateColor: Color,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    // 主行：图标 + 温度 + 状况 + 刷新按钮
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = mapWeatherIcon(weather.now.text),
            contentDescription = weather.now.text,
            tint = stateColor,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = formatTemperature(weather.now.temp),
            style = TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium
            ),
            color = stateColor
        )
        Text(
            text = weather.now.text,
            style = TextStyle(fontSize = 14.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onRefresh,
            enabled = !isLoading,
            modifier = Modifier.size(28.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = stateColor
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.weather_refresh),
                    tint = stateColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    // 城市名 + 更新时间
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text(
            text = weather.cityName.ifEmpty { stringResource(R.string.weather_unknown_city) },
            style = TextStyle(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatUpdateTime(weather.fetchTimeMillis),
            style = TextStyle(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }

    // 卫星过境天气预测
    if (nextSatellite != null && weather.daily.isNotEmpty()) {
        SatelliteForecastRow(
            satellite = nextSatellite,
            weather = weather,
            stateColor = stateColor
        )
    }
}

/**
 * 卫星过境天气预测行。
 *
 * 根据卫星 AOS 时间匹配预报日，并按小时判断使用日间/夜间天气图标。
 */
@Composable
private fun SatelliteForecastRow(
    satellite: SatelliteInfo,
    weather: WeatherResult,
    stateColor: Color
) {
    val aosInstant = satellite.aosTime
    val aosZone = aosInstant.atZone(ZoneId.systemDefault())
    val aosDateStr = aosZone.toLocalDate().format(weatherDateOnlyFormat)
    val aosHour = aosZone.hour

    // 匹配预报日（按 date 精确匹配）
    val matchedDay = weather.daily.firstOrNull { it.date == aosDateStr }
    if (matchedDay == null) return

    // 按小时判断日间/夜间：6:00-18:00 用日间图标，否则夜间
    val isDaytime = aosHour in 6..17
    val weatherText = if (isDaytime) matchedDay.dayWeather else matchedDay.nightWeather

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.weather_next_pass_label),
            style = TextStyle(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Text(
            text = satellite.name.take(8),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = stateColor
        )
        Text(
            text = aosZone.format(weatherDateFormat),
            style = TextStyle(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = mapWeatherIcon(weatherText, isNight = !isDaytime),
            contentDescription = weatherText,
            tint = stateColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "${matchedDay.nightTemp}~${matchedDay.dayTemp}°",
            style = TextStyle(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 加载状态：骨架屏 + 加载动画。
 */
@Composable
private fun LoadingState(stateColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = stateColor
        )
        Text(
            text = stringResource(R.string.weather_loading),
            style = TextStyle(fontSize = 14.sp),
            color = stateColor
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    // 骨架占位
    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(stateColor.copy(alpha = 0.1f))
    )
}

/**
 * 错误状态：错误提示 + 重试按钮。
 */
@Composable
private fun ErrorState(
    error: String,
    stateColor: Color,
    onRetry: () -> Unit
) {
    Text(
        text = stringResource(R.string.weather_load_failed),
        style = TextStyle(fontSize = 13.sp),
        color = stateColor
    )
    Text(
        text = error,
        style = TextStyle(fontSize = 11.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )
    TextButton(
        onClick = onRetry,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.weather_retry),
            style = TextStyle(fontSize = 13.sp)
        )
    }
}

/**
 * 初始状态：提示点击刷新。
 */
@Composable
private fun InitialState(
    stateColor: Color,
    onRefresh: () -> Unit
) {
    TextButton(onClick = onRefresh) {
        Text(
            text = stringResource(R.string.weather_tap_to_load),
            style = TextStyle(fontSize = 13.sp),
            color = stateColor
        )
    }
}

/**
 * 格式化温度：保留一位小数 + 单位。
 *
 * 和风 API 返回的 temp 可能是 "25" 或 "25.3"，统一格式化为 "25.0°C"。
 */
private fun formatTemperature(temp: String): String {
    val value = temp.toDoubleOrNull() ?: return "--"
    return String.format("%.1f°C", value)
}

/**
 * 格式化数据更新时间："HH:mm 更新"。
 */
private fun formatUpdateTime(timestampMillis: Long): String {
    val instant = Instant.ofEpochMilli(timestampMillis)
    val time = instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    return "$time${UiConstants.WEATHER_UPDATED_SUFFIX}"
}

/**
 * UI 常量，避免魔法字符串。
 */
private object UiConstants {
    const val WEATHER_UPDATED_SUFFIX = " 更新"
}

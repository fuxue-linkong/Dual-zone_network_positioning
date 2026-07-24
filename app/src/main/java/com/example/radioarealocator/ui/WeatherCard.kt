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
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val weatherDateFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val weatherDateOnlyFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.outline
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(stateColor.copy(alpha = 0.12f * LocalCardAlpha.current))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        when {
            isLoading && weather == null -> LoadingState(stateColor = stateColor)
            error != null && weather == null -> ErrorState(
                error = error,
                stateColor = stateColor,
                onRetry = onRefresh
            )
            weather != null -> WeatherContent(
                weather = weather,
                nextSatellite = nextSatellite,
                stateColor = stateColor,
                isLoading = isLoading,
                onRefresh = onRefresh
            )
            else -> InitialState(
                stateColor = stateColor,
                onRefresh = onRefresh
            )
        }
    }
}

@Composable
private fun WeatherContent(
    weather: WeatherResult,
    nextSatellite: SatelliteInfo?,
    stateColor: Color,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
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
            style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium),
            color = stateColor
        )
        Text(
            text = weather.now.text,
            style = TextStyle(fontSize = 14.sp),
            color = MiuixTheme.colorScheme.onSurfaceSecondary
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
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = stateColor
                    )
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text(
            text = weather.cityName.ifEmpty { stringResource(R.string.weather_unknown_city) },
            style = TextStyle(fontSize = 12.sp),
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
        Text(
            text = formatUpdateTime(weather.fetchTimeMillis),
            style = TextStyle(fontSize = 11.sp),
            color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f)
        )
    }

    if (nextSatellite != null && weather.daily.isNotEmpty()) {
        SatelliteForecastRow(
            satellite = nextSatellite,
            weather = weather,
            stateColor = stateColor
        )
    }
}

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

    val matchedDay = weather.daily.firstOrNull { it.date == aosDateStr }
    if (matchedDay == null) return

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
            color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.8f)
        )
        Text(
            text = satellite.name.take(8),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = stateColor
        )
        Text(
            text = aosZone.format(weatherDateFormat),
            style = TextStyle(fontSize = 11.sp),
            color = MiuixTheme.colorScheme.onSurfaceSecondary
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
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

@Composable
private fun LoadingState(stateColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = stateColor
            )
        )
        Text(
            text = stringResource(R.string.weather_loading),
            style = TextStyle(fontSize = 14.sp),
            color = stateColor
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(stateColor.copy(alpha = 0.1f))
    )
}

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
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier.padding(top = 2.dp)
    )
    TextButton(
        text = stringResource(R.string.weather_retry),
        onClick = onRetry,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun InitialState(
    stateColor: Color,
    onRefresh: () -> Unit
) {
    TextButton(
        text = stringResource(R.string.weather_tap_to_load),
        onClick = onRefresh,
    )
}

private fun formatTemperature(temp: String): String {
    val value = temp.toDoubleOrNull() ?: return "--"
    return String.format(java.util.Locale.US, "%.1f°C", value)
}

private fun formatUpdateTime(timestampMillis: Long): String {
    val instant = Instant.ofEpochMilli(timestampMillis)
    val time = instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    return "$time${UiConstants.WEATHER_UPDATED_SUFFIX}"
}

private object UiConstants {
    const val WEATHER_UPDATED_SUFFIX = " 更新"
}

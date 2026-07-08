package com.example.radioarealocator.data.weather

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 天气数据模型。包含实时天气与 4 天预报。
 *
 * 数据来源：高德天气 API
 * - 实时天气：/v3/weather/weatherInfo?extensions=base
 * - 天气预报：/v3/weather/weatherInfo?extensions=all
 *
 * @param cityName 城市名（如"北京"），由高德逆地理编码 API 返回
 * @param now 实时天气
 * @param daily 4 天预报列表
 * @param fetchTimeMillis 数据获取时间戳，用于缓存过期判断
 */
data class WeatherResult(
    val cityName: String,
    val now: WeatherNow,
    val daily: List<WeatherDay>,
    val fetchTimeMillis: Long = System.currentTimeMillis()
)

/**
 * 实时天气（高德 lives 字段）。
 *
 * @param temp 当前温度（摄氏度，保留一位小数由 UI 格式化）
 * @param text 天气状况文字（如"晴"、"多云"、"阴"）
 * @param windDir 风向（如"东北"）
 * @param windPower 风力（如"≤3"、"1-3"）
 * @param humidity 湿度百分比
 * @param reportTime 数据报告时间（如"2026-07-07 22:03:09"）
 */
data class WeatherNow(
    val temp: String,
    val text: String,
    val windDir: String = "",
    val windPower: String = "",
    val humidity: String = "",
    val reportTime: String = ""
)

/**
 * 单日天气预报（高德 casts 字段）。
 *
 * @param date 日期（yyyy-MM-dd）
 * @param week 星期几（1-7，1=周一）
 * @param dayWeather 白天天气文字
 * @param nightWeather 夜间天气文字
 * @param dayTemp 白天最高温度
 * @param nightTemp 夜间最低温度
 * @param dayWind 白天风向
 * @param nightWind 夜间风向
 * @param dayPower 白天风力
 * @param nightPower 夜间风力
 */
data class WeatherDay(
    val date: String,
    val week: String = "",
    val dayWeather: String = "",
    val nightWeather: String = "",
    val dayTemp: String = "",
    val nightTemp: String = "",
    val dayWind: String = "",
    val nightWind: String = "",
    val dayPower: String = "",
    val nightPower: String = ""
)

/**
 * 高德天气文字描述到 Material Icon 的映射。
 *
 * 高德天气 API 不提供 icon code，仅返回中文天气描述。
 * 此函数根据描述中的关键字匹配到最合适的 Material Icon：
 * - 晴 → WbSunny / NightsStay
 * - 多云 → CloudQueue
 * - 阴 → Cloud
 * - 雷 → Thunderstorm
 * - 雨 → Grain
 * - 雪 → AcUnit
 * - 雾/霾/沙/尘/风 → Air
 *
 * @param weatherText 天气状况文字（如"晴"、"雷阵雨"）
 * @param isNight 是否夜间（影响晴的图标选择）
 */
fun mapWeatherIcon(weatherText: String, isNight: Boolean = false): ImageVector {
    return when {
        weatherText.contains("晴") -> if (isNight) Icons.Default.NightsStay else Icons.Default.WbSunny
        weatherText.contains("多云") -> Icons.Default.CloudQueue
        weatherText.contains("阴") -> Icons.Default.Cloud
        weatherText.contains("雷") -> Icons.Default.Thunderstorm
        weatherText.contains("雨") -> Icons.Default.Grain
        weatherText.contains("雪") -> Icons.Default.AcUnit
        weatherText.contains("雾") || weatherText.contains("霾") -> Icons.Default.Air
        weatherText.contains("沙") || weatherText.contains("尘") -> Icons.Default.Air
        weatherText.contains("风") -> Icons.Default.Air
        else -> Icons.Default.BrokenImage
    }
}

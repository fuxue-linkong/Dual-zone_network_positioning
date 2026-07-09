package com.example.radioarealocator.data.weather

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.weather.LocalWeatherForecastResult
import com.amap.api.services.weather.LocalWeatherLiveResult
import com.amap.api.services.weather.WeatherSearch
import com.amap.api.services.weather.WeatherSearchQuery
import com.example.radioarealocator.data.crypto.SecretManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 高德天气 SDK 封装。
 *
 * 使用高德搜索 SDK（com.amap.api:search）替代 Web API：
 * - [GeocodeSearch]：经纬度 → adcode + 城市名（逆地理编码）
 * - [WeatherSearch]：adcode → 实时天气（Live）+ 4 天预报（Forecast）
 *
 * SDK 内部处理 HTTP 请求、JSON 解析、线程切换，复用 manifest 注入的 [AMAP_SDK_KEY]，
 * 不再需要独立的 Web 服务 Key（amap.api.key）。
 *
 * SDK 数据类层次（v9.7.1，通过 javap 反编译确认）：
 * - 逆地理：`onRegeocodeSearched(RegeocodeResult, rCode)` → `result.regeocodeAddress` → `RegeocodeAddress`
 *   （注意回调参数是包装类 RegeocodeResult，非 RegeocodeAddress）
 * - 实时天气：`onWeatherLiveSearched(LocalWeatherLiveResult, rCode)` → `result.liveResult` → `LocalWeatherLive`
 *   （注意方法名为 onWeatherLiveSearched，非 onLiveWeatherSearched）
 * - 天气预报：`onWeatherForecastSearched(LocalWeatherForecastResult, rCode)` → `result.forecastResult` → `LocalWeatherForecast`
 *   → `forecast.weatherForecast` → `List<LocalDayWeatherForecast>`
 *   （注意属性名为 weatherForecast，非 casts；风向/风力为 dayWindDirection/nightWindDirection/dayWindPower/nightWindPower）
 *
 * Key 安全策略：
 * - 明文 Key 仅存在于 local.properties（不进 git）
 * - 开发阶段通过 `gradle encryptSecrets` 加密为 assets/secrets.dat（提交到 git）
 * - 运行时由 [SecretManager] 从 secrets.dat 解密
 * - CI 构建无需 GitHub Secrets，secrets.dat 已在仓库中
 *
 * 错误处理：
 * - SDK Key 缺失时抛出 [ApiKeyMissingException]
 * - 网络层错误（rCode 1800/1900）抛出 [WeatherNetworkException]
 * - SDK 业务错误（rCode != 1000）抛出 [WeatherApiException]，携带 rCode
 *
 * @param context 应用上下文，用于创建 GeocodeSearch / WeatherSearch
 */
class WeatherApiService(private val context: Context) {

    /**
     * 完整天气数据获取流程：
     * 1. 用经纬度逆地理编码获取 adcode（行政区划代码）和城市名
     * 2. 用 adcode 查实时天气（WeatherSearchQuery.WEATHER_TYPE_LIVE）
     * 3. 用 adcode 查 4 天预报（WeatherSearchQuery.WEATHER_TYPE_FORECAST）
     *
     * SDK 的回调式接口通过 [suspendCancellableCoroutine] 包装为 suspend 函数，
     * 实时天气与预报通过 [coroutineScope] + [async] 并行查询。
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @return [WeatherResult]
     * @throws ApiKeyMissingException SDK Key 未配置（SecretManager 中 amap.sdk.key 为空）
     * @throws WeatherNetworkException 网络请求失败（rCode 1800/1900）
     * @throws WeatherApiException SDK 业务错误（rCode != 1000 或数据为空）
     */
    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherResult {
        val key = SecretManager.getSecret("amap.sdk.key")
        if (key.isBlank()) {
            // SDK Key 为空：构建时未注入 secret，SDK 调用必然失败，直接抛出明确异常
            throw ApiKeyMissingException()
        }

        // Step 1: 经纬度逆地理编码获取 adcode + 城市名
        val locationInfo = searchAdcode(latitude, longitude)

        // Step 2 & 3: 并行获取实时天气和预报
        return coroutineScope {
            val nowDeferred = async { searchNowWeather(locationInfo.first) }
            val dailyDeferred = async { searchDailyForecast(locationInfo.first) }
            val now = nowDeferred.await()
            val daily = dailyDeferred.await()

            WeatherResult(
                cityName = locationInfo.second,
                now = now,
                daily = daily
            )
        }
    }

    /**
     * 逆地理编码：经纬度 → adcode + 城市名。
     *
     * 使用搜索 SDK 的 [GeocodeSearch.getFromLocationAsyn] 发起异步查询，
     * 通过 [suspendCancellableCoroutine] 将回调转为挂起函数。
     *
     * @return Pair(adcode, cityName)
     * @throws WeatherNetworkException 网络请求失败（rCode 1800/1900）
     * @throws WeatherApiException SDK 业务错误（rCode != 1000 或 adcode 为空）
     */
    private suspend fun searchAdcode(lat: Double, lng: Double): Pair<String, String> =
        suspendCancellableCoroutine { cont ->
            val search = GeocodeSearch(context)
            search.setOnGeocodeSearchListener(object : GeocodeSearch.OnGeocodeSearchListener {
                override fun onRegeocodeSearched(result: RegeocodeResult?, rCode: Int) {
                    if (rCode != AMAP_SUCCESS) {
                        cont.resumeWithException(mapRCode(rCode, "逆地理编码"))
                        return
                    }
                    if (result == null) {
                        cont.resumeWithException(WeatherApiException("逆地理编码结果为空"))
                        return
                    }
                    // RegeocodeResult 是包装类，需通过 regeocodeAddress 获取实际地址数据
                    val address = result.regeocodeAddress
                    if (address == null) {
                        cont.resumeWithException(WeatherApiException("逆地理编码地址为空"))
                        return
                    }
                    val adcode = address.adCode ?: ""
                    // city 可能为空（直辖市），回退到 province
                    val city = address.city ?: ""
                    val province = address.province ?: ""
                    val cityName = if (city.isNotEmpty()) city else province

                    if (adcode.isEmpty()) {
                        cont.resumeWithException(WeatherApiException("adcode 为空"))
                    } else if (cityName.isEmpty()) {
                        cont.resumeWithException(WeatherApiException("城市名为空"))
                    } else {
                        cont.resume(Pair(adcode, cityName))
                    }
                }

                override fun onGeocodeSearched(result: GeocodeResult?, rCode: Int) {
                    // 正向地理编码（地址→坐标），此处未使用
                }
            })
            // 搜索 SDK 用 LatLonPoint，非地图 SDK 的 LatLng；200m 查询半径，AMAP 坐标系
            val query = RegeocodeQuery(LatLonPoint(lat, lng), 200f, GeocodeSearch.AMAP)
            search.getFromLocationAsyn(query)
        }

    /**
     * 获取实时天气（WeatherSearchQuery.WEATHER_TYPE_LIVE）。
     *
     * @throws WeatherNetworkException 网络请求失败（rCode 1800/1900）
     * @throws WeatherApiException SDK 业务错误（rCode != 1000 或数据为空）
     */
    private suspend fun searchNowWeather(adcode: String): WeatherNow =
        suspendCancellableCoroutine { cont ->
            val search = WeatherSearch(context)
            search.setOnWeatherSearchListener(object : WeatherSearch.OnWeatherSearchListener {
                override fun onWeatherLiveSearched(result: LocalWeatherLiveResult?, rCode: Int) {
                    if (rCode != AMAP_SUCCESS) {
                        cont.resumeWithException(mapRCode(rCode, "实时天气"))
                        return
                    }
                    if (result == null) {
                        cont.resumeWithException(WeatherApiException("实时天气结果为空"))
                        return
                    }
                    // LocalWeatherLiveResult 是包装类，需通过 liveResult 获取实际数据
                    val live = result.liveResult
                    if (live == null) {
                        cont.resumeWithException(WeatherApiException("实时天气数据为空"))
                        return
                    }
                    cont.resume(
                        WeatherNow(
                            temp = live.temperature ?: "",
                            text = live.weather ?: "",
                            windDir = live.windDirection ?: "",
                            windPower = live.windPower ?: "",
                            humidity = live.humidity ?: "",
                            reportTime = live.reportTime ?: ""
                        )
                    )
                }

                override fun onWeatherForecastSearched(result: LocalWeatherForecastResult?, rCode: Int) {
                    // 预报回调，此处未使用
                }
            })
            search.query = WeatherSearchQuery(adcode, WeatherSearchQuery.WEATHER_TYPE_LIVE)
            search.searchWeatherAsyn()
        }

    /**
     * 获取 4 天预报（WeatherSearchQuery.WEATHER_TYPE_FORECAST）。
     *
     * @throws WeatherNetworkException 网络请求失败（rCode 1800/1900）
     * @throws WeatherApiException SDK 业务错误（rCode != 1000 或 weatherForecast 为空）
     */
    private suspend fun searchDailyForecast(adcode: String): List<WeatherDay> =
        suspendCancellableCoroutine { cont ->
            val search = WeatherSearch(context)
            search.setOnWeatherSearchListener(object : WeatherSearch.OnWeatherSearchListener {
                override fun onWeatherLiveSearched(result: LocalWeatherLiveResult?, rCode: Int) {
                    // 实时天气回调，此处未使用
                }

                override fun onWeatherForecastSearched(result: LocalWeatherForecastResult?, rCode: Int) {
                    if (rCode != AMAP_SUCCESS) {
                        cont.resumeWithException(mapRCode(rCode, "天气预报"))
                        return
                    }
                    if (result == null) {
                        cont.resumeWithException(WeatherApiException("天气预报结果为空"))
                        return
                    }
                    // LocalWeatherForecastResult 是包装类，需通过 forecastResult 获取实际数据
                    val forecast = result.forecastResult
                    if (forecast == null) {
                        cont.resumeWithException(WeatherApiException("天气预报数据为空"))
                        return
                    }
                    val casts = forecast.weatherForecast
                    if (casts.isNullOrEmpty()) {
                        cont.resumeWithException(WeatherApiException("weatherForecast 为空"))
                        return
                    }
                    val days = casts.map { cast ->
                        WeatherDay(
                            date = cast.date ?: "",
                            week = cast.week ?: "",
                            dayWeather = cast.dayWeather ?: "",
                            nightWeather = cast.nightWeather ?: "",
                            dayTemp = cast.dayTemp ?: "",
                            nightTemp = cast.nightTemp ?: "",
                            dayWind = cast.dayWindDirection ?: "",
                            nightWind = cast.nightWindDirection ?: "",
                            dayPower = cast.dayWindPower ?: "",
                            nightPower = cast.nightWindPower ?: ""
                        )
                    }
                    cont.resume(days)
                }
            })
            search.query = WeatherSearchQuery(adcode, WeatherSearchQuery.WEATHER_TYPE_FORECAST)
            search.searchWeatherAsyn()
        }

    /**
     * 将高德 SDK rCode 映射到对应异常。
     *
     * rCode 体系：
     * - 1000：成功
     * - 1800/1900：网络错误 → [WeatherNetworkException]
     * - 其他（1001 Key无效、1100 频率限制、2000 结果为空等）→ [WeatherApiException]
     */
    private fun mapRCode(rCode: Int, operation: String): Exception {
        return if (rCode == 1800 || rCode == 1900) {
            WeatherNetworkException("${operation}失败：网络错误(rCode=$rCode)")
        } else {
            WeatherApiException("${operation}失败：rCode=$rCode")
        }
    }

    companion object {
        /** 高德 SDK 成功码 */
        private const val AMAP_SUCCESS = 1000
    }
}

/**
 * API Key 未配置：构建时未注入 AMAP_SDK_KEY secret（local.properties 缺失或为空）。
 * 此时 SDK 调用必然失败，无需发起请求。
 */
class ApiKeyMissingException : Exception("API Key 未配置，请运行 gradle encryptSecrets 生成 secrets.dat")

/**
 * 网络层异常：SDK 报告网络错误（rCode 1800/1900）。
 */
class WeatherNetworkException(message: String) : Exception(message)

/**
 * SDK 业务错误：rCode != 1000，如 Key 无效、配额超限、参数错误、结果为空等。
 * message 包含操作名与 rCode，便于定位。
 */
class WeatherApiException(message: String) : Exception(message)

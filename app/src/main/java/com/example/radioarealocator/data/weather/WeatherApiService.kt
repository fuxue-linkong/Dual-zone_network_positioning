package com.example.radioarealocator.data.weather

import com.example.radioarealocator.BuildConfig
import com.example.radioarealocator.data.crypto.ApiKeyCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 高德天气 API 封装。
 *
 * API 文档：https://lbs.amap.com/api/webservice/guide/api/weatherinfo
 *
 * 三个核心接口：
 * 1. 逆地理编码（经纬度→adcode）：GET /v3/geocode/regeo?location=lng,lat
 * 2. 实时天气：GET /v3/weather/weatherInfo?city=adcode&extensions=base
 * 3. 天气预报：GET /v3/weather/weatherInfo?city=adcode&extensions=all
 *
 * Key 安全策略：
 * - Key 存储在 local.properties（不进 git）
 * - 编译时 AES-GCM 加密后注入 BuildConfig.AMAP_API_KEY_ENCRYPTED
 * - 运行时通过 ApiKeyCrypto.decrypt() 解密
 * - 与静态地图服务共用同一个高德 Web 服务 Key
 */
class WeatherApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 完整天气数据获取流程：
     * 1. 用经纬度逆地理编码获取 adcode（行政区划代码）和城市名
     * 2. 用 adcode 查实时天气（extensions=base）
     * 3. 用 adcode 查 4 天预报（extensions=all）
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @return [WeatherResult] 或 null（任何步骤失败均返回 null）
     */
    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherResult? {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: 经纬度逆地理编码获取 adcode
                val locationInfo = fetchAdcode(latitude, longitude)
                    ?: return@withContext null

                // Step 2 & 3: 并行获取实时天气和预报
                coroutineScope {
                    val nowDeferred = async { fetchNowWeather(locationInfo.first) }
                    val dailyDeferred = async { fetchDailyForecast(locationInfo.first) }
                    val now = nowDeferred.await() ?: return@coroutineScope null
                    val daily = dailyDeferred.await() ?: return@coroutineScope null

                    WeatherResult(
                        cityName = locationInfo.second,
                        now = now,
                        daily = daily
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 逆地理编码：经纬度 → adcode + 城市名。
     *
     * 高德 regeo API 返回格式：
     * ```json
     * {"status":"1","regeocode":{"addressComponent":{"adcode":"110000","city":"北京市"}}}
     * ```
     *
     * @return Pair(adcode, cityName) 或 null
     */
    private suspend fun fetchAdcode(lat: Double, lng: Double): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                // 高德 API location 参数格式：经度,纬度（lng,lat）
                val location = String.format("%.6f,%.6f", lng, lat)
                val url = "$REGEO_BASE_URL?location=$location&key=${apiKey()}"
                val response = executeRequest(url) ?: return@withContext null
                val json = JSONObject(response)
                if (json.optString("status") != "1") return@withContext null

                val regeocode = json.optJSONObject("regeocode") ?: return@withContext null
                val addrComponent = regeocode.optJSONObject("addressComponent") ?: return@withContext null

                val adcode = addrComponent.optString("adcode")
                if (adcode.isEmpty()) return@withContext null

                // city 可能为空（直辖市），回退到 province
                val city = addrComponent.optString("city")
                val province = addrComponent.optString("province")
                val cityName = if (city.isNotEmpty()) city else province

                if (cityName.isEmpty()) null else Pair(adcode, cityName)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 获取实时天气（extensions=base）。
     *
     * 返回格式：
     * ```json
     * {"status":"1","lives":[{"temperature":"22","weather":"阴","winddirection":"东北","windpower":"≤3","humidity":"93","reporttime":"2026-07-07 22:03:09"}]}
     * ```
     */
    private suspend fun fetchNowWeather(adcode: String): WeatherNow? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$WEATHER_BASE_URL?city=$adcode&key=${apiKey()}&extensions=base"
                val response = executeRequest(url) ?: return@withContext null
                val json = JSONObject(response)
                if (json.optString("status") != "1") return@withContext null

                val livesArr = json.optJSONArray("lives") ?: return@withContext null
                if (livesArr.length() == 0) return@withContext null

                val live = livesArr.optJSONObject(0) ?: return@withContext null
                WeatherNow(
                    temp = live.optString("temperature"),
                    text = live.optString("weather"),
                    windDir = live.optString("winddirection"),
                    windPower = live.optString("windpower"),
                    humidity = live.optString("humidity"),
                    reportTime = live.optString("reporttime")
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 获取 4 天预报（extensions=all）。
     *
     * 返回格式：
     * ```json
     * {"status":"1","forecasts":[{"city":"北京市","adcode":"110000","casts":[{"date":"2026-07-07","week":"2","dayweather":"雷阵雨","nightweather":"多云","daytemp":"31","nighttemp":"22",...}]}]}
     * ```
     */
    private suspend fun fetchDailyForecast(adcode: String): List<WeatherDay>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$WEATHER_BASE_URL?city=$adcode&key=${apiKey()}&extensions=all"
                val response = executeRequest(url) ?: return@withContext null
                val json = JSONObject(response)
                if (json.optString("status") != "1") return@withContext null

                val forecastsArr = json.optJSONArray("forecasts") ?: return@withContext null
                if (forecastsArr.length() == 0) return@withContext null

                val forecast = forecastsArr.optJSONObject(0) ?: return@withContext null
                val castsArr = forecast.optJSONArray("casts") ?: return@withContext null

                (0 until castsArr.length()).mapNotNull { idx ->
                    val cast = castsArr.optJSONObject(idx) ?: return@mapNotNull null
                    WeatherDay(
                        date = cast.optString("date"),
                        week = cast.optString("week"),
                        dayWeather = cast.optString("dayweather"),
                        nightWeather = cast.optString("nightweather"),
                        dayTemp = cast.optString("daytemp"),
                        nightTemp = cast.optString("nighttemp"),
                        dayWind = cast.optString("daywind"),
                        nightWind = cast.optString("nightwind"),
                        dayPower = cast.optString("daypower"),
                        nightPower = cast.optString("nightpower")
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 执行 HTTP 请求，返回响应体字符串。失败返回 null。
     */
    private fun executeRequest(url: String): String? {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取已解密的 API Key。
     * 与静态地图服务共用同一个高德 Web 服务 Key。
     */
    private fun apiKey(): String = ApiKeyCrypto.decrypt(BuildConfig.AMAP_API_KEY_ENCRYPTED)

    companion object {
        // 高德 Web 服务 API 基础 URL
        private const val WEATHER_BASE_URL = "https://restapi.amap.com/v3/weather/weatherInfo"
        private const val REGEO_BASE_URL = "https://restapi.amap.com/v3/geocode/regeo"
    }
}

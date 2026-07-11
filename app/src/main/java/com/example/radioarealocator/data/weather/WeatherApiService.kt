package com.example.radioarealocator.data.weather

import com.example.radioarealocator.data.crypto.SecretManager
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
 * Key 安全策略：
 * - 明文 Key 仅存在于 local.properties（不进 git）
 * - 开发阶段通过 `gradle encryptSecrets` 加密为 assets/secrets.dat（提交到 git）
 * - 运行时由 [SecretManager] 从 secrets.dat 解密，三碎片组装主密钥 + AES-GCM
 * - CI 构建无需 GitHub Secrets，secrets.dat 已在仓库中
 *
 * 错误处理：
 * - API Key 缺失时抛出 [ApiKeyMissingException]，避免发出注定失败的网络请求
 * - 网络异常抛出 [WeatherNetworkException]，与 API 业务错误区分
 * - 高德 API 返回 status!=1 时抛出 [WeatherApiException]，携带 info 描述
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
     * @return [WeatherResult]
     * @throws ApiKeyMissingException API Key 未配置（BuildConfig 中为空）
     * @throws WeatherNetworkException 网络请求失败（连接超时、DNS、断网等）
     * @throws WeatherApiException 高德 API 返回业务错误（Key 无效、配额超限等）
     */
    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherResult {
        val key = apiKey()
        if (key.isBlank()) {
            // API Key 为空：通常是构建时未注入 secret（如 GHA 缺少 AMAP_API_KEY），
            // 不必发出注定失败的网络请求，直接抛出明确异常供上层定位
            throw ApiKeyMissingException()
        }

        return withContext(Dispatchers.IO) {
            // Step 1: 经纬度逆地理编码获取 adcode
            val locationInfo = fetchAdcode(latitude, longitude, key)

            // Step 2 & 3: 并行获取实时天气和预报
            coroutineScope {
                val nowDeferred = async { fetchNowWeather(locationInfo.first, key) }
                val dailyDeferred = async { fetchDailyForecast(locationInfo.first, key) }
                val now = nowDeferred.await()
                val daily = dailyDeferred.await()

                WeatherResult(
                    cityName = locationInfo.second,
                    now = now,
                    daily = daily
                )
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
     * @return Pair(adcode, cityName)
     * @throws WeatherNetworkException 网络请求失败
     * @throws WeatherApiException 高德 API 返回 status!=1
     */
    private suspend fun fetchAdcode(lat: Double, lng: Double, key: String): Pair<String, String> {
        // 高德 API location 参数格式：经度,纬度（lng,lat）
        val location = String.format(java.util.Locale.US, "%.6f,%.6f", lng, lat)
        val url = "$REGEO_BASE_URL?location=$location&key=$key"
        val response = executeRequest(url)
        val json = JSONObject(response)
        checkApiStatus(json)
        val regeocode = json.optJSONObject("regeocode")
            ?: throw WeatherApiException("regeo 响应缺少 regeocode 字段")
        val addrComponent = regeocode.optJSONObject("addressComponent")
            ?: throw WeatherApiException("regeo 响应缺少 addressComponent 字段")

        val adcode = addrComponent.optString("adcode")
        if (adcode.isEmpty()) throw WeatherApiException("adcode 为空")

        // city 可能为空（直辖市），回退到 province
        val city = addrComponent.optString("city")
        val province = addrComponent.optString("province")
        val cityName = if (city.isNotEmpty()) city else province

        if (cityName.isEmpty()) {
            throw WeatherApiException("城市名为空")
        }
        return Pair(adcode, cityName)
    }

    /**
     * 获取实时天气（extensions=base）。
     *
     * 返回格式：
     * ```json
     * {"status":"1","lives":[{"temperature":"22","weather":"阴","winddirection":"东北","windpower":"≤3","humidity":"93","reporttime":"2026-07-07 22:03:09"}]}
     * ```
     *
     * @throws WeatherNetworkException 网络请求失败
     * @throws WeatherApiException 高德 API 返回 status!=1 或数据为空
     */
    private suspend fun fetchNowWeather(adcode: String, key: String): WeatherNow {
        val url = "$WEATHER_BASE_URL?city=$adcode&key=$key&extensions=base"
        val response = executeRequest(url)
        val json = JSONObject(response)
        checkApiStatus(json)

        val livesArr = json.optJSONArray("lives")
            ?: throw WeatherApiException("lives 为空")
        if (livesArr.length() == 0) throw WeatherApiException("lives 数组为空")

        val live = livesArr.optJSONObject(0)
            ?: throw WeatherApiException("lives[0] 为空")
        return WeatherNow(
            temp = live.optString("temperature"),
            text = live.optString("weather"),
            windDir = live.optString("winddirection"),
            windPower = live.optString("windpower"),
            humidity = live.optString("humidity"),
            reportTime = live.optString("reporttime")
        )
    }

    /**
     * 获取 4 天预报（extensions=all）。
     *
     * 返回格式：
     * ```json
     * {"status":"1","forecasts":[{"city":"北京市","adcode":"110000","casts":[{"date":"2026-07-07","week":"2","dayweather":"雷阵雨","nightweather":"多云","daytemp":"31","nighttemp":"22",...}]}]}
     * ```
     *
     * @throws WeatherNetworkException 网络请求失败
     * @throws WeatherApiException 高德 API 返回 status!=1 或数据为空
     */
    private suspend fun fetchDailyForecast(adcode: String, key: String): List<WeatherDay> {
        val url = "$WEATHER_BASE_URL?city=$adcode&key=$key&extensions=all"
        val response = executeRequest(url)
        val json = JSONObject(response)
        checkApiStatus(json)

        val forecastsArr = json.optJSONArray("forecasts")
            ?: throw WeatherApiException("forecasts 为空")
        if (forecastsArr.length() == 0) throw WeatherApiException("forecasts 数组为空")

        val forecast = forecastsArr.optJSONObject(0)
            ?: throw WeatherApiException("forecasts[0] 为空")
        val castsArr = forecast.optJSONArray("casts")
            ?: throw WeatherApiException("casts 为空")

        return (0 until castsArr.length()).mapNotNull { idx ->
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
    }

    /**
     * 校验高德 API 响应状态。status != "1" 时抛出 [WeatherApiException] 携带 info。
     */
    private fun checkApiStatus(json: JSONObject) {
        if (json.optString("status") != "1") {
            val info = json.optString("info", "未知错误")
            val infocode = json.optString("infocode", "")
            throw WeatherApiException("$info(${infocode})")
        }
    }

    /**
     * 执行 HTTP 请求，返回响应体字符串。
     *
     * @throws WeatherNetworkException 网络层异常（连接超时、DNS 解析失败、断网等）
     */
    private fun executeRequest(url: String): String {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                        ?: throw WeatherNetworkException("响应体为空")
                } else {
                    throw WeatherNetworkException("HTTP ${response.code}")
                }
            }
        } catch (e: WeatherNetworkException) {
            throw e
        } catch (e: Exception) {
            throw WeatherNetworkException(e.message ?: "网络请求异常")
        }
    }

    /**
     * 获取已解密的 API Key。
     * 与静态地图服务共用同一个高德 Web 服务 Key。
     */
    private fun apiKey(): String = SecretManager.getSecret("amap.api.key")

    companion object {
        // 高德 Web 服务 API 基础 URL
        private const val WEATHER_BASE_URL = "https://restapi.amap.com/v3/weather/weatherInfo"
        private const val REGEO_BASE_URL = "https://restapi.amap.com/v3/geocode/regeo"
    }
}

/**
 * API Key 未配置：构建时未注入 AMAP_API_KEY secret（local.properties 缺失或为空）。
 * 此时所有高德 API 请求都会返回 INVALID_USER_KEY，无需发出网络请求。
 */
class ApiKeyMissingException : Exception("API Key 未配置，请运行 gradle encryptSecrets 生成 secrets.dat")

/**
 * 网络层异常：连接超时、DNS 解析失败、断网、HTTP 非 2xx 等。
 */
class WeatherNetworkException(message: String) : Exception(message)

/**
 * 高德 API 业务错误：status != "1"，如 Key 无效、配额超限、参数错误等。
 * message 包含高德返回的 info 与 infocode，便于定位。
 */
class WeatherApiException(message: String) : Exception(message)

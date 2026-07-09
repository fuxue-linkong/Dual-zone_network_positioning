package com.example.radioarealocator.data.weather

import android.content.Context
import android.util.Log
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
 * 数据流（用户明确要求的 4 步流程）：
 * 1. 软件通过定位获得经纬度
 * 2. 经纬度发送给高德 → 高德返回逆地理编码（adcode + 城市名）
 *    → **缓存到进程内存（[LocationCache]）**
 * 3. 软件从内存读出逆地理编码（adcode）→ 再次发送给高德天气接口
 * 4. 高德返回实时天气 + 4 天预报 → 返回给 UI
 *
 * 内存缓存的作用：
 * - 定位抖动（同地点小幅移动）不触发重复逆地编请求，节省配额
 * - 短时网络抖动导致天气查询失败时，下次重试可跳过逆地编直接用缓存的 adcode
 * - 1 小时内同区域查询命中率最高，符合日常使用模式
 *
 * SDK 类层次（v9.7.1，通过 javap 反编译确认）：
 * - 逆地理：`onRegeocodeSearched(RegeocodeResult, rCode)` → `result.regeocodeAddress` → `RegeocodeAddress`
 * - 实时天气：`onWeatherLiveSearched(LocalWeatherLiveResult, rCode)` → `result.liveResult` → `LocalWeatherLive`
 * - 天气预报：`onWeatherForecastSearched(LocalWeatherForecastResult, rCode)` → `result.forecastResult` → `LocalWeatherForecast`
 *   → `forecast.weatherForecast` → `List<LocalDayWeatherForecast>`
 *
 * Key 安全策略：
 * - 明文 Key 仅存在于 local.properties（不进 git）
 * - 开发阶段通过 `gradle encryptSecrets` 加密为 assets/secrets.dat（提交到 git）
 * - 运行时由 [SecretManager] 从 secrets.dat 解密
 * - CI 构建无需 GitHub Secrets
 *
 * @param context 应用上下文，用于创建 GeocodeSearch / WeatherSearch
 */
class WeatherApiService(private val context: Context) {

    /** 进程级内存缓存：最近一次成功逆地编结果。命中即跳过步骤 2。 */
    private val locationCache = LocationCache()

    /**
     * 完整天气数据获取（4 步流程）。
     *
     * 步骤 1：调用方传入经纬度（来自定位）
     * 步骤 2：经纬度 → 高德逆地编 → adcode + 城市名（命中缓存时跳过）
     * 步骤 3：用 adcode 并行查询实时天气 + 4 天预报
     * 步骤 4：组装为 [WeatherResult] 返回
     *
     * @param latitude 纬度
     * @param longitude 经度
     * @throws ApiKeyMissingException SDK Key 未配置
     * @throws WeatherNetworkException 网络请求失败（rCode 1800/1900）
     * @throws WeatherApiException SDK 业务错误（rCode != 1000 或数据为空）
     */
    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherResult {
        val key = SecretManager.getSecret("amap.sdk.key")
        if (key.isBlank()) {
            // SDK Key 为空：构建时未注入 secret，SDK 调用必然失败，直接抛出明确异常
            throw ApiKeyMissingException()
        }

        // 步骤 2：经纬度 → adcode（命中缓存则跳过网络请求）
        val locationInfo = resolveAdcode(latitude, longitude)
        val adcode = locationInfo.first
        val cityName = locationInfo.second
        Log.i(TAG, "天气查询：adcode=$adcode, cityName=$cityName, "
            + "缓存${if (locationCache.isHit(latitude, longitude)) "命中" else "未命中"}")

        // 步骤 3 & 4：用 adcode 并行查询实时天气和预报
        return coroutineScope {
            val nowDeferred = async { searchNowWeather(adcode) }
            val dailyDeferred = async { searchDailyForecast(adcode) }
            val now = nowDeferred.await()
            val daily = dailyDeferred.await()

            WeatherResult(
                cityName = cityName,
                now = now,
                daily = daily
            )
        }
    }

    /**
     * 解析 adcode（含内存缓存）。
     *
     * - 缓存命中（距离 < [LocationCache.CACHE_RADIUS_M] 且未过期）→ 直接返回缓存
     * - 未命中 → 调用 [searchAdcode] 走网络 → 写入缓存
     *
     * 缓存失效场景：首次启动、位置移动 > 5km、缓存超过 1 小时。
     */
    private suspend fun resolveAdcode(lat: Double, lng: Double): Pair<String, String> {
        if (locationCache.isHit(lat, lng)) {
            return locationCache.get()
        }
        val (adcode, cityName) = searchAdcode(lat, lng)
        locationCache.put(lat, lng, adcode, cityName)
        return Pair(adcode, cityName)
    }

    /**
     * 逆地理编码：经纬度 → adcode + 城市名（步骤 2 的网络层）。
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
     * 获取实时天气（步骤 3 的实时分支）。
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
     * 获取 4 天预报（步骤 3 的预报分支）。
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
     * rCode 体系（高德搜索 SDK）：
     * - 1000：成功
     * - 1001：用户签名未通过（INVALID_USER_SCODE）
     * - 1002：用户 key 不正确或过期（INVALID_USER_KEY） ← 真机实测常见
     * - 1003：请求的服务不存在
     * - 1004/1100：用户日调用量超限
     * - 1101/1102：请求服务被禁用
     * - 1800/1900：网络错误
     *
     * rCode=1002 排查清单（已写入异常消息，便于真机定位）：
     * 1. AMap 控制台 → 应用管理 → 当前 Key → 是否绑定包名 com.example.radioarealocator
     * 2. AMap 控制台 → 应用管理 → 当前 Key → 是否绑定正确的 SHA1 签名指纹
     *    - debug APK：~/.android/debug.keystore 的 SHA1
     *    - release APK：release.keystore 的 SHA1（与 debug 不同！两套都要配）
     *      本项目 release SHA1：A4:73:9A:C1:9B:69:D8:F7:BB:F3:75:AC:0E:63:7E:D3:29:0D:B2:7C
     *      本项目包名：com.example.radioarealocator
     * 3. Key 类型应为"Android SDK"，不是"Web 服务"（Web Key 用于 REST API）
     * 4. secrets.dat 是否正确的解密出 amap.sdk.key（看 logcat "SecretManager" 标签）
     */
    private fun mapRCode(rCode: Int, operation: String): Exception {
        return if (rCode == 1800 || rCode == 1900) {
            WeatherNetworkException("${operation}失败：网络错误(rCode=$rCode)")
        } else if (rCode == 1002) {
            // 1002 = INVALID_USER_KEY：Key 无效或与签名/包名不匹配
            // 真机 release 测试时最常见：未在 AMap 控制台配置 release keystore 的 SHA1
            WeatherApiException(
                "${operation}失败：rCode=$rCode（Key 无效或签名不匹配）。" +
                "排查：1)AMap 控制台是否给此 Key 绑定包名 com.example.radioarealocator；" +
                "2)是否绑定 release keystore 的 SHA1（与 debug 不同）；" +
                "3)Key 类型应为 Android SDK 而非 Web 服务"
            )
        } else {
            WeatherApiException("${operation}失败：rCode=$rCode")
        }
    }

    companion object {
        private const val TAG = "WeatherApiService"
        /** 高德 SDK 成功码 */
        private const val AMAP_SUCCESS = 1000
    }
}

/**
 * 进程级逆地理编码内存缓存。
 *
 * 实现"定位 → 经纬度 → 高德 → 缓存到内存"中的第 2 步缓存层。
 *
 * 命中条件（同时满足）：
 * - Haversine 距离 < [CACHE_RADIUS_M]（容忍定位抖动，5km 内视为同地点）
 * - 缓存时间 < [CACHE_TTL_MS]（1 小时，超时强制刷新）
 *
 * 失效场景：首次启动、位置移动 > 5km、超过 1 小时未刷新。
 * 进程结束后缓存自动消失（纯内存，不持久化）。
 */
private class LocationCache {
    private var lat: Double = 0.0
    private var lng: Double = 0.0
    private var adcode: String = ""
    private var cityName: String = ""
    private var timestampMs: Long = 0L
    private var valid: Boolean = false

    /** 缓存命中半径（米）：5km，足以覆盖同城移动且避免跨区误判 */
    private val cacheRadiusM = CACHE_RADIUS_M
    /** 缓存有效期（毫秒）：1 小时 */
    private val cacheTtlMs = CACHE_TTL_MS

    /** 是否命中缓存（位置在阈值内且未过期）。 */
    fun isHit(latitude: Double, longitude: Double): Boolean {
        if (!valid) return false
        if (System.currentTimeMillis() - timestampMs > cacheTtlMs) return false
        val distance = haversineMeters(lat, lng, latitude, longitude)
        return distance < cacheRadiusM
    }

    /** 读取缓存。调用前应先调用 [isHit] 判断是否命中。 */
    fun get(): Pair<String, String> = Pair(adcode, cityName)

    /** 写入缓存。 */
    fun put(latitude: Double, longitude: Double, adcode: String, cityName: String) {
        this.lat = latitude
        this.lng = longitude
        this.adcode = adcode
        this.cityName = cityName
        this.timestampMs = System.currentTimeMillis()
        this.valid = true
    }

    companion object {
        const val CACHE_RADIUS_M = 5000.0
        const val CACHE_TTL_MS = 60L * 60 * 1000

        /**
         * Haversine 公式计算两点间球面距离（米）。
         * 用于判断是否在缓存半径内，避免每次定位都触发逆地编。
         */
        private fun haversineMeters(
            lat1: Double, lng1: Double,
            lat2: Double, lng2: Double
        ): Double {
            val r = 6_371_000.0 // 地球平均半径（米）
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2).let { it * it }
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return r * c
        }
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

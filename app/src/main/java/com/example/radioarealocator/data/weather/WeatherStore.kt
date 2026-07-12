package com.example.radioarealocator.data.weather

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 天气数据缓存层。
 *
 * 缓存策略：
 * - 数据存储在 SharedPreferences（JSON 序列化）
 * - 缓存有效期 30 分钟（[CACHE_DURATION_MS]）
 * - [isCacheValid] 判断缓存是否可用
 * - [clearCache] 清除缓存（用于手动刷新或错误恢复）
 *
 * 进程重启后缓存仍可读取，避免冷启动时立即请求网络。
 */
class WeatherStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 进程级内存缓存：避免 isCacheValid() 每次重新解析 JSON
    @Volatile
    private var cachedResult: WeatherResult? = null
    @Volatile
    private var cacheLoaded: Boolean = false

    /**
     * 读取缓存的天气数据。
     * @return 缓存数据或 null（无缓存或解析失败）
     */
    fun load(): WeatherResult? {
        if (cacheLoaded) return cachedResult
        val json = prefs.getString(KEY_DATA, null) ?: return null
        return try {
            parseJson(json).also {
                cachedResult = it
                cacheLoaded = true
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 保存天气数据到缓存。
     */
    fun save(result: WeatherResult) {
        prefs.edit()
            .putString(KEY_DATA, toJson(result))
            .putLong(KEY_FETCH_TIME_ONLY, result.fetchTimeMillis)
            .apply()
        cachedResult = result
        cacheLoaded = true
    }

    /**
     * 判断缓存是否有效（存在且未过期）。
     * 优先读取独立存储的时间戳，避免解析完整 JSON。
     * @param maxAgeMillis 最大缓存时间（毫秒），默认 30 分钟
     */
    fun isCacheValid(maxAgeMillis: Long = CACHE_DURATION_MS): Boolean {
        val ts = prefs.getLong(KEY_FETCH_TIME_ONLY, -1L)
        if (ts < 0) return false
        val hasData = prefs.contains(KEY_DATA)
        if (!hasData) return false
        val age = System.currentTimeMillis() - ts
        return age < maxAgeMillis
    }

    /**
     * 清除缓存。
     */
    fun clearCache() {
        prefs.edit().remove(KEY_DATA).remove(KEY_FETCH_TIME_ONLY).apply()
        cachedResult = null
        cacheLoaded = false
    }

    // ---- JSON 序列化/反序列化 ----

    private fun toJson(result: WeatherResult): String {
        val json = JSONObject()
        json.put(KEY_CITY, result.cityName)
        json.put(KEY_FETCH_TIME, result.fetchTimeMillis)

        val nowObj = JSONObject()
        nowObj.put("temp", result.now.temp)
        nowObj.put("text", result.now.text)
        nowObj.put("windDir", result.now.windDir)
        nowObj.put("windPower", result.now.windPower)
        nowObj.put("humidity", result.now.humidity)
        nowObj.put("reportTime", result.now.reportTime)
        json.put("now", nowObj)

        val dailyArr = JSONArray()
        result.daily.forEach { day ->
            val dayObj = JSONObject()
            dayObj.put("date", day.date)
            dayObj.put("week", day.week)
            dayObj.put("dayWeather", day.dayWeather)
            dayObj.put("nightWeather", day.nightWeather)
            dayObj.put("dayTemp", day.dayTemp)
            dayObj.put("nightTemp", day.nightTemp)
            dayObj.put("dayWind", day.dayWind)
            dayObj.put("nightWind", day.nightWind)
            dayObj.put("dayPower", day.dayPower)
            dayObj.put("nightPower", day.nightPower)
            dailyArr.put(dayObj)
        }
        json.put("daily", dailyArr)
        return json.toString()
    }

    private fun parseJson(json: String): WeatherResult? {
        val obj = JSONObject(json)
        val nowObj = obj.optJSONObject("now") ?: return null
        val dailyArr = obj.optJSONArray("daily") ?: return null

        val now = WeatherNow(
            temp = nowObj.optString("temp"),
            text = nowObj.optString("text"),
            windDir = nowObj.optString("windDir"),
            windPower = nowObj.optString("windPower"),
            humidity = nowObj.optString("humidity"),
            reportTime = nowObj.optString("reportTime")
        )

        val daily = (0 until dailyArr.length()).mapNotNull { idx ->
            val day = dailyArr.optJSONObject(idx) ?: return@mapNotNull null
            WeatherDay(
                date = day.optString("date"),
                week = day.optString("week"),
                dayWeather = day.optString("dayWeather"),
                nightWeather = day.optString("nightWeather"),
                dayTemp = day.optString("dayTemp"),
                nightTemp = day.optString("nightTemp"),
                dayWind = day.optString("dayWind"),
                nightWind = day.optString("nightWind"),
                dayPower = day.optString("dayPower"),
                nightPower = day.optString("nightPower")
            )
        }

        return WeatherResult(
            cityName = obj.optString(KEY_CITY),
            now = now,
            daily = daily,
            fetchTimeMillis = obj.optLong(KEY_FETCH_TIME, System.currentTimeMillis())
        )
    }

    companion object {
        private const val PREFS_NAME = "radio_area_weather"
        private const val KEY_DATA = "weather_data"
        private const val KEY_CITY = "city"
        private const val KEY_FETCH_TIME = "fetch_time"
        private const val KEY_FETCH_TIME_ONLY = "fetch_time_only"
        private const val CACHE_DURATION_MS = 30L * 60 * 1000
    }
}

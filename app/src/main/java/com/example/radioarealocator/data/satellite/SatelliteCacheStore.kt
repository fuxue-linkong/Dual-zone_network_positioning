package com.example.radioarealocator.data.satellite

import android.content.Context
import android.content.SharedPreferences
import com.github.amsacode.predict4java.TLE
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * 卫星 TLE 数据本地缓存。进程重启后可复用，避免每次重新拉取网络。
 *
 * 缓存格式：JSON 数组，每项包含 tle0/tle1/tle2/source/status 五个字段。
 * 同时记录上次更新时间戳（epoch millis）。
 */
class SatelliteCacheStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 读取缓存的 TLE 列表。无缓存时返回 null。
     */
    fun load(): CachedTLEs? {
        val ts = prefs.getLong(KEY_UPDATED_AT, -1L)
        if (ts < 0) return null
        val json = prefs.getString(KEY_TLE_JSON, null) ?: return null
        return try {
            val array = JSONArray(json)
            val list = ArrayList<SourcedTLE>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val tle0 = obj.optString("tle0", "")
                val tle1 = obj.optString("tle1", "")
                val tle2 = obj.optString("tle2", "")
                val source = obj.optString("source", "CT")
                val status = obj.optString("status", "")
                list.add(
                    SourcedTLE(
                        tle = TLE(arrayOf(tle0, tle1, tle2)),
                        source = source,
                        status = status,
                        rawLines = arrayOf(tle0, tle1, tle2)
                    )
                )
            }
            CachedTLEs(list, Instant.ofEpochMilli(ts))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存 TLE 列表与更新时间。
     */
    fun save(tles: List<SourcedTLE>, updatedAt: Instant = Instant.now()) {
        val array = JSONArray()
        for (t in tles) {
            val obj = JSONObject()
            obj.put("tle0", t.rawLines.getOrNull(0) ?: "")
            obj.put("tle1", t.rawLines.getOrNull(1) ?: "")
            obj.put("tle2", t.rawLines.getOrNull(2) ?: "")
            obj.put("source", t.source)
            obj.put("status", t.status)
            array.put(obj)
        }
        prefs.edit()
            .putString(KEY_TLE_JSON, array.toString())
            .putLong(KEY_UPDATED_AT, updatedAt.toEpochMilli())
            .apply()
    }

    /**
     * 清空缓存。
     */
    fun clear() {
        prefs.edit()
            .remove(KEY_TLE_JSON)
            .remove(KEY_UPDATED_AT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "radio_area_settings"
        private const val KEY_TLE_JSON = "cached_tle_json"
        private const val KEY_UPDATED_AT = "cached_tle_updated_at"
    }
}

/**
 * 缓存的 TLE 数据及其更新时间。
 */
data class CachedTLEs(
    val tles: List<SourcedTLE>,
    val updatedAt: Instant
)

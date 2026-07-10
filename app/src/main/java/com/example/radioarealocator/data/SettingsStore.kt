package com.example.radioarealocator.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置持久化存储。保存用户选择的背景图 URI、卫星源等设置。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 背景图 URI 字符串。null 表示未设置。
     */
    var backgroundUri: String?
        get() = prefs.getString(KEY_BACKGROUND_URI, null)
        set(value) {
            prefs.edit().putString(KEY_BACKGROUND_URI, value).apply()
        }

    /**
     * 卡片透明度（0~100）。0 完全透明，100 完全不透明。
     * 仅在设置了背景图时生效。默认 100。
     */
    var cardOpacity: Int
        get() = prefs.getInt(KEY_CARD_OPACITY, 100).coerceIn(0, 100)
        set(value) {
            prefs.edit().putInt(KEY_CARD_OPACITY, value.coerceIn(0, 100)).apply()
        }

    /**
     * 卫星 TLE 数据来源："ALL" / "CT" / "SNOGS"。默认 ALL。
     */
    var satelliteSource: String
        get() = prefs.getString(KEY_SATELLITE_SOURCE, "ALL") ?: "ALL"
        set(value) {
            prefs.edit().putString(KEY_SATELLITE_SOURCE, value).apply()
        }

    /**
     * 最后已知纬度。供后台 Worker 做过境预测时使用，避免依赖应用进程存活。
     * 默认 0.0 表示无有效位置。
     */
    var lastLatitude: Float
        get() = prefs.getFloat(KEY_LAST_LAT, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_LAST_LAT, value).apply()
        }

    /**
     * 最后已知经度。供后台 Worker 做过境预测时使用。
     */
    var lastLongitude: Float
        get() = prefs.getFloat(KEY_LAST_LON, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_LAST_LON, value).apply()
        }

    /**
     * 最后位置是否有效（latitude 和 longitude 均非 0）。
     */
    fun hasLastLocation(): Boolean =
        prefs.contains(KEY_LAST_LAT) && prefs.contains(KEY_LAST_LON)

    companion object {
        private const val PREFS_NAME = "radio_area_settings"
        private const val KEY_BACKGROUND_URI = "background_uri"
        private const val KEY_CARD_OPACITY = "card_opacity"
        private const val KEY_SATELLITE_SOURCE = "satellite_source"
        private const val KEY_LAST_LAT = "last_lat"
        private const val KEY_LAST_LON = "last_lon"
    }
}

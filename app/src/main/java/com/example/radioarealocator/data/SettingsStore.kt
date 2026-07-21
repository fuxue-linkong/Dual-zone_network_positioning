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
     * 背景图不透明度（0~100）。0 完全透明（不可见），100 完全显示。
     * 仅在设置了背景图时生效。默认 100。
     */
    var backgroundOpacity: Int
        get() = prefs.getInt(KEY_BACKGROUND_OPACITY, 100).coerceIn(0, 100)
        set(value) {
            prefs.edit().putInt(KEY_BACKGROUND_OPACITY, value.coerceIn(0, 100)).apply()
        }

    /**
     * 是否启用莫奈取色（从背景图提取主色调）。默认 true。
     * 关闭后即使设置了背景图，也使用默认主题色 #3482FF。
     */
    var monetEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONET_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MONET_ENABLED, value).apply()
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
     * 使用 String 存储 Double，避免 Float 精度丢失（Float 仅 ~7 位有效数字）。
     */
    var lastLatitude: Double
        get() = prefs.getString(KEY_LAST_LAT, null)?.toDoubleOrNull() ?: 0.0
        set(value) {
            prefs.edit().putString(KEY_LAST_LAT, value.toString()).apply()
        }

    /**
     * 最后已知经度。供后台 Worker 做过境预测时使用。
     * 使用 String 存储 Double，避免 Float 精度丢失。
     */
    var lastLongitude: Double
        get() = prefs.getString(KEY_LAST_LON, null)?.toDoubleOrNull() ?: 0.0
        set(value) {
            prefs.edit().putString(KEY_LAST_LON, value.toString()).apply()
        }

    /**
     * 最后位置是否有效（latitude 和 longitude 均已存储）。
     */
    fun hasLastLocation(): Boolean =
        prefs.contains(KEY_LAST_LAT) && prefs.contains(KEY_LAST_LON)

    /**
     * 每日一言最近一次成功获取的日期（epoch day）。
     * 避免进程重启后同一天内重复请求 hitokoto API。
     * -1 表示从未获取。
     */
    var dailyQuoteEpochDay: Long
        get() = prefs.getLong(KEY_DAILY_QUOTE_EPOCH_DAY, -1L)
        set(value) {
            prefs.edit().putLong(KEY_DAILY_QUOTE_EPOCH_DAY, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "radio_area_settings"
        private const val KEY_BACKGROUND_URI = "background_uri"
        private const val KEY_CARD_OPACITY = "card_opacity"
        private const val KEY_BACKGROUND_OPACITY = "background_opacity"
        private const val KEY_MONET_ENABLED = "monet_enabled"
        private const val KEY_SATELLITE_SOURCE = "satellite_source"
        private const val KEY_LAST_LAT = "last_lat"
        private const val KEY_LAST_LON = "last_lon"
        private const val KEY_DAILY_QUOTE_EPOCH_DAY = "daily_quote_epoch_day"
    }
}

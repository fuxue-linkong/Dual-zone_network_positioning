package com.example.radioarealocator.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置持久化存储。保存卫星源、最后位置、每日一言获取日期等业务设置。
 *
 * 注：主题相关设置（colorMode、keyColor、paletteStyle、colorSpec、uiMode 等）由
 * [com.example.radioarealocator.data.repository.SettingsRepositoryImpl] 统一管理，
 * 存放在名为 "settings" 的 SharedPreferences 中。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
        private const val KEY_SATELLITE_SOURCE = "satellite_source"
        private const val KEY_LAST_LAT = "last_lat"
        private const val KEY_LAST_LON = "last_lon"
        private const val KEY_DAILY_QUOTE_EPOCH_DAY = "daily_quote_epoch_day"
    }
}

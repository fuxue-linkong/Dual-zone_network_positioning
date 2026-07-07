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
     * 卫星 TLE 数据来源："ALL" / "CT" / "SNOGS"。默认 ALL。
     */
    var satelliteSource: String
        get() = prefs.getString(KEY_SATELLITE_SOURCE, "ALL") ?: "ALL"
        set(value) {
            prefs.edit().putString(KEY_SATELLITE_SOURCE, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "radio_area_settings"
        private const val KEY_BACKGROUND_URI = "background_uri"
        private const val KEY_SATELLITE_SOURCE = "satellite_source"
    }
}

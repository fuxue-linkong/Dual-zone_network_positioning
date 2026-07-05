package com.example.radioarealocator.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置持久化存储。当前用于保存用户选择的背景图 URI。
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
     * 卫星过境日历提醒开关。开启后可在卫星卡片添加过境事件到系统日历。
     */
    var calendarReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_REMINDER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CALENDAR_REMINDER, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "radio_area_settings"
        private const val KEY_BACKGROUND_URI = "background_uri"
        private const val KEY_CALENDAR_REMINDER = "calendar_reminder_enabled"
    }
}

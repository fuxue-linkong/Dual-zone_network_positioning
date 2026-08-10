package com.example.radioarealocator.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置持久化存储。保存最后位置、每日一言获取日期等业务设置。
 *
 * 注：主题相关设置（colorMode、keyColor、paletteStyle、colorSpec、uiMode 等）由
 * [com.example.radioarealocator.data.repository.SettingsRepositoryImpl] 统一管理，
 * 存放在名为 "settings" 的 SharedPreferences 中。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 是否启用 AMSAT 状态数据源（www.amsat.org/status）。默认 true。
     */
    var amsatStatusEnabled: Boolean
        get() = prefs.getBoolean(KEY_AMSAT_STATUS_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AMSAT_STATUS_ENABLED, value).apply()
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

    // ── 卫星数据设置（Phase 3/4） ──
    // 注：以下字段当前由设置页提供配置入口；预测器/数据源的接线（读取这些值
    // 并传入 SatellitePredictor / SatelliteDataSource）由未来版本在
    // MainViewModel 与 ReminderRefreshWorker 中完成，UI 侧仅持久化。

    /**
     * 卫星过境预测窗口（小时）。默认 48。
     * 未来由 MainViewModel / ReminderRefreshWorker 传给
     * [com.example.radioarealocator.data.satellite.SatellitePredictor.predictUpcomingPasses]。
     */
    var satelliteHoursAhead: Int
        get() = prefs.getInt(KEY_SAT_HOURS_AHEAD, 48)
        set(value) {
            prefs.edit().putInt(KEY_SAT_HOURS_AHEAD, value.coerceIn(12, 168)).apply()
        }

    /**
     * 卫星过境最小仰角（度），低于该仰角的过境不显示。默认 0。
     * 使用 String 存储 Double，避免 Float 精度丢失。
     */
    var minSatelliteElevation: Double
        get() = prefs.getString(KEY_SAT_MIN_ELEVATION, null)?.toDoubleOrNull() ?: 0.0
        set(value) {
            prefs.edit().putString(KEY_SAT_MIN_ELEVATION, value.toString()).apply()
        }

    /**
     * 是否启用 CelesTrak amateur 分组 TLE 源。默认 true。
     */
    var tleSourceAmateur: Boolean
        get() = prefs.getBoolean(KEY_TLE_SOURCE_AMATEUR, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TLE_SOURCE_AMATEUR, value).apply()
        }

    /**
     * 是否启用 CelesTrak satnogs 分组 TLE 源。默认 true。
     */
    var tleSourceSatnogs: Boolean
        get() = prefs.getBoolean(KEY_TLE_SOURCE_SATNOGS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TLE_SOURCE_SATNOGS, value).apply()
        }

    /**
     * 是否启用 CelesTrak active 分组 TLE 源（全部活跃卫星，含非业余）。默认 true。
     * 启用后卫星列表覆盖全部在轨活跃卫星（"卫星太少"问题：不再只显示业余分组）。
     */
    var tleSourceActive: Boolean
        get() = prefs.getBoolean(KEY_TLE_SOURCE_ACTIVE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TLE_SOURCE_ACTIVE, value).apply()
        }

    /**
     * 是否使用 SatNOGS DB 转发器频率数据库（db.satnogs.org）。默认 true。
     */
    var useSatnogsTransmitters: Boolean
        get() = prefs.getBoolean(KEY_USE_SATNOGS_TRANSMITTERS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_USE_SATNOGS_TRANSMITTERS, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "radio_area_settings"
        private const val KEY_LAST_LAT = "last_lat"
        private const val KEY_LAST_LON = "last_lon"
        private const val KEY_DAILY_QUOTE_EPOCH_DAY = "daily_quote_epoch_day"
        private const val KEY_AMSAT_STATUS_ENABLED = "amsat_status_enabled"
        private const val KEY_SAT_HOURS_AHEAD = "sat_hours_ahead"
        private const val KEY_SAT_MIN_ELEVATION = "sat_min_elevation"
        private const val KEY_TLE_SOURCE_AMATEUR = "tle_source_amateur"
        private const val KEY_TLE_SOURCE_SATNOGS = "tle_source_satnogs"
        private const val KEY_TLE_SOURCE_ACTIVE = "tle_source_active"
        private const val KEY_USE_SATNOGS_TRANSMITTERS = "use_satnogs_transmitters"
    }
}

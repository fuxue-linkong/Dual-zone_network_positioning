package com.example.radioarealocator.ui.screen.satellite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.example.radioarealocator.data.satellite.LiveSatelliteTracker
import com.example.radioarealocator.data.satellite.predict.TleElements
import com.example.radioarealocator.ui.LocalMainViewModel
import java.time.Instant

/**
 * 卫星详情/雷达/地图页共享的会话数据：由 catalogNumber 从 [LocalMainViewModel]
 * 解析出的 TLE、过境信息与地面站位置。
 *
 * TLE 来自 MainViewModel 的 SatelliteUiState.cachedTles，过境信息来自
 * SatelliteUiState.satellites，地面站位置来自 LocationUiState.result。
 * 任一数据缺失时对应字段为 null / 空，UI 显示占位。
 */
@Immutable
data class SatelliteLiveSession(
    val tle: TleElements? = null,
    val satelliteName: String = "",
    val aosTime: Instant? = null,
    val losTime: Instant? = null,
    val maxElevation: Double = 0.0,
    val isGeo: Boolean = false,
)

/**
 * 读取当前 [SatelliteLiveSession]（TLE / 过境信息 / 名称）。
 */
@Composable
fun rememberSatelliteLiveSession(catalogNumber: Int): SatelliteLiveSession {
    val mainViewModel = LocalMainViewModel.current
    val satelliteState by mainViewModel.satelliteState
    return remember(satelliteState.cachedTles, satelliteState.satellites, catalogNumber) {
        val sourcedTle = satelliteState.cachedTles.firstOrNull { it.tle.catnum == catalogNumber }
        val info = satelliteState.satellites.firstOrNull { it.catalogNumber == catalogNumber }
        SatelliteLiveSession(
            tle = sourcedTle?.tle,
            satelliteName = info?.name
                ?: sourcedTle?.tle?.name?.trim()
                ?: catalogNumber.toString(),
            aosTime = info?.aosTime,
            losTime = info?.losTime,
            maxElevation = info?.maxElevation ?: 0.0,
            isGeo = info?.isGeo ?: false
        )
    }
}

/**
 * 读取当前地面站位置（纬度、经度、海拔米）。
 * 定位不可用（未授权 / 尚未定位）时返回 null。
 */
@Composable
fun rememberStationPosition(): Triple<Double, Double, Double>? {
    val mainViewModel = LocalMainViewModel.current
    val locationState by mainViewModel.locationState
    val result = locationState.result
    return remember(result) {
        if (result != null) Triple(result.latitude, result.longitude, 0.0) else null
    }
}

/**
 * 创建并持有 [LiveSatelliteTracker] 实例：
 * - TLE 与位置就绪时创建追踪器并自动 start()，离开组合时自动 stop()
 * - TLE / 位置缺失时返回 null（UI 显示占位）
 *
 * @param baseDownlinkHz 多普勒下行基准频率（Hz），默认 145.800 MHz
 * @param baseUplinkHz 多普勒上行基准频率（Hz），默认 435.000 MHz
 */
@Composable
fun rememberSatelliteLiveTracker(
    catalogNumber: Int,
    baseDownlinkHz: Double = LiveSatelliteTracker.DEFAULT_DOWNLINK_HZ,
    baseUplinkHz: Double = LiveSatelliteTracker.DEFAULT_UPLINK_HZ,
): LiveSatelliteTracker? {
    val session = rememberSatelliteLiveSession(catalogNumber)
    val position = rememberStationPosition()

    val tracker = remember(session.tle, position, catalogNumber) {
        if (session.tle != null && position != null) {
            LiveSatelliteTracker(
                tle = session.tle,
                latitudeDeg = position.first,
                longitudeDeg = position.second,
                altitudeM = position.third,
                baseDownlinkHz = baseDownlinkHz,
                baseUplinkHz = baseUplinkHz
            )
        } else {
            null
        }
    }

    DisposableEffect(tracker) {
        tracker?.start()
        onDispose { tracker?.stop() }
    }
    return tracker
}

/**
 * 计算 AOS→LOS 倒计时显示文本。
 *
 * - 尚未 AOS：距 AOS mm:ss
 * - 在境：距 LOS mm:ss（含"在境"标记）
 * - 已结束：已结束
 */
fun formatPassCountdown(aosTime: Instant?, losTime: Instant?, nowMillis: Long): String {
    val aos = aosTime ?: return ""
    val los = losTime ?: return ""
    val now = nowMillis
    return when {
        now < aos.toEpochMilli() -> "距 AOS ${formatDuration(aos.toEpochMilli() - now)}"
        now < los.toEpochMilli() -> "在境 · 距 LOS ${formatDuration(los.toEpochMilli() - now)}"
        else -> "过境已结束"
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * 格式化频率（Hz）为可读文本：≥1 MHz 显示 MHz（3 位小数），否则显示 kHz。
 */
fun formatFrequencyHz(frequencyHz: Double): String {
    if (frequencyHz >= 1_000_000.0) {
        return "%.3f MHz".format(frequencyHz / 1_000_000.0)
    }
    return "%.2f kHz".format(frequencyHz / 1_000.0)
}

/**
 * 页面存活期间保持屏幕常亮（雷达图使用）。
 * [active] 为 false 时恢复系统默认。
 */
@Composable
fun rememberKeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, active) {
        if (active) {
            view.keepScreenOn = true
        }
        onDispose {
            view.keepScreenOn = false
        }
    }
}

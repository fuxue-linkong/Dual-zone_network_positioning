package com.example.radioarealocator.data.satellite

import com.github.amsacode.predict4java.GroundStationPosition
import com.github.amsacode.predict4java.PassPredictor
import com.github.amsacode.predict4java.SatNotFoundException
import com.github.amsacode.predict4java.TLE
import java.time.Instant
import java.util.Date

/**
 * 基于 predict4java 计算卫星过境信息。
 */
class SatellitePredictor {

    /**
     * 计算指定地面站位置未来一段时间内的卫星过境信息。
     *
     * @param sourcedTles 带来源标记的卫星 TLE 列表
     * @param latitude 地面站纬度（度）
     * @param longitude 地面站经度（度）
     * @param altitude 地面站海拔（米）
     * @param limit 返回结果数量上限
     * @param hoursAhead 预测未来小时数
     */
    fun predictUpcomingPasses(
        sourcedTles: List<SourcedTLE>,
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        limit: Int = 25,
        hoursAhead: Int = 48
    ): List<SatelliteInfo> {
        val groundStation = GroundStationPosition(latitude, longitude, altitude)
        val now = Date()
        val searchEnd = Date(now.time + hoursAhead * 60L * 60L * 1000L)

        val passes = mutableListOf<SatelliteInfo>()

        for (sourcedTle in sourcedTles) {
            try {
                val tle = sourcedTle.tle
                val modes = SatelliteCatalog.MODES_BY_CATALOG_NUMBER[tle.catnum].orEmpty()
                if (modes.isEmpty()) continue

                val predictor = PassPredictor(tle, groundStation)

                // 判断当前是否在境内（仰角 > 0）
                val currentPos = predictor.getSatPos(now)
                val isCurrentlyVisible = currentPos != null && currentPos.elevation > 0

                // 在境时获取当前过境（含出境时间），即将入境时获取下次过境
                val nextPass = if (isCurrentlyVisible) {
                    predictor.nextSatPass(now, true)
                } else {
                    predictor.nextSatPass(now, false)
                }
                if (nextPass == null || nextPass.startTime == null || nextPass.endTime == null) continue

                // 只取预测窗口内的过境
                if (nextPass.startTime.after(searchEnd)) continue

                passes.add(
                    SatelliteInfo(
                        name = tle.name.trim().ifEmpty { tle.catnum.toString() },
                        catalogNumber = tle.catnum,
                        modes = modes,
                        aosTime = nextPass.startTime.toInstant(),
                        losTime = nextPass.endTime.toInstant(),
                        maxElevation = nextPass.maxEl,
                        aosAzimuth = nextPass.aosAzimuth,
                        losAzimuth = nextPass.losAzimuth,
                        isCurrentlyVisible = isCurrentlyVisible,
                        source = sourcedTle.source,
                        status = sourcedTle.status
                    )
                )
            } catch (_: SatNotFoundException) {
                // 卫星在当前位置不可见或计算失败，跳过
            } catch (_: IllegalArgumentException) {
                // TLE 或地面站参数异常，跳过
            }
        }

        // 当前在境内的优先显示，其次按 AOS 时间排序
        return passes
            .sortedWith(
                compareByDescending<SatelliteInfo> { it.isCurrentlyVisible }
                    .thenBy { it.aosTime }
            )
            .take(limit)
    }
}

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
     * @param tles 卫星 TLE 列表
     * @param latitude 地面站纬度（度）
     * @param longitude 地面站经度（度）
     * @param altitude 地面站海拔（米）
     * @param limit 返回结果数量上限
     * @param hoursAhead 预测未来小时数
     */
    fun predictUpcomingPasses(
        tles: List<TLE>,
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        limit: Int = 10,
        hoursAhead: Int = 48
    ): List<SatelliteInfo> {
        val groundStation = GroundStationPosition(latitude, longitude, altitude)
        val now = Date()
        val searchEnd = Date(now.time + hoursAhead * 60L * 60L * 1000L)

        val passes = mutableListOf<SatelliteInfo>()

        for (tle in tles) {
            try {
                val modes = MODES_BY_CATALOG_NUMBER[tle.catnum].orEmpty()
                if (modes.isEmpty()) continue

                val predictor = PassPredictor(tle, groundStation)

                // 判断当前是否在境内（仰角 > 0）
                val currentPos = predictor.getSatPos(now)
                val isCurrentlyVisible = currentPos != null && currentPos.elevation > 0

                val nextPass = predictor.nextSatPass(now, false)
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
                        isCurrentlyVisible = isCurrentlyVisible
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

    companion object {
        /**
         * 业余卫星常见工作模式，按 NORAD 编号维护。
         */
        private val MODES_BY_CATALOG_NUMBER = mapOf(
            // FM
            25544 to listOf("FM", "SSTV"),      // ISS
            43017 to listOf("FM"),              // AO-91 (Fox-1B)
            43137 to listOf("FM"),              // AO-92 (Fox-1D)
            27607 to listOf("FM"),              // SO-50
            22825 to listOf("FM"),              // AO-27
            43678 to listOf("FM"),              // PO-101 (Diwata-2)
            40908 to listOf("FM", "SSTV"),      // LilacSat-2
            41909 to listOf("FM"),              // BY70-1
            42684 to listOf("FM"),              // CAS-3H
            // 线性转发器（CW / USB / LSB）
            7530 to listOf("CW", "USB", "LSB"), // AO-7
            24278 to listOf("CW", "USB", "LSB"),// FO-29
            39417 to listOf("CW", "USB", "LSB"),// AO-73 (FUNcube-1)
            42017 to listOf("CW", "USB", "LSB"),// EO-88
            43854 to listOf("CW", "USB", "LSB"),// JO-97
            42761 to listOf("CW", "USB", "LSB"),// CAS-4A
            42759 to listOf("CW", "USB", "LSB"),// CAS-4B
            40903 to listOf("CW", "USB", "LSB"),// XW-2A
            40911 to listOf("CW", "USB", "LSB"),// XW-2B
            40906 to listOf("CW", "USB", "LSB"),// XW-2C
            40907 to listOf("CW", "USB", "LSB"),// XW-2D
            40910 to listOf("CW", "USB", "LSB"),// XW-2F
            // D-Star
            43879 to listOf("DSTAR")            // D-Star ONE
        )
    }
}

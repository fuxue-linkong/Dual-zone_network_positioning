package com.example.radioarealocator.data.satellite

import com.github.amsacode.predict4java.GroundStationPosition
import com.github.amsacode.predict4java.PassPredictor
import com.github.amsacode.predict4java.SatNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone

/**
 * 基于 predict4java 计算卫星过境信息。
 */
class SatellitePredictor {

    /**
     * 计算指定地面站位置未来一段时间内的卫星过境信息。
     * 过境预测为 CPU 密集型计算，默认在 Default 调度器上并行执行。
     *
     * @param sourcedTles 带来源标记的卫星 TLE 列表
     * @param latitude 地面站纬度（度）
     * @param longitude 地面站经度（度）
     * @param altitude 地面站海拔（米）
     * @param hoursAhead 预测未来小时数
     */
    suspend fun predictUpcomingPasses(
        sourcedTles: List<SourcedTLE>,
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        hoursAhead: Int = 48
    ): List<SatelliteInfo> = withContext(Dispatchers.Default) {
        if (sourcedTles.isEmpty()) return@withContext emptyList()

        val groundStation = GroundStationPosition(latitude, longitude, altitude)
        // 使用 UTC 时区确保与卫星轨道计算的一致性
        val utcNow = Instant.now()
        val now = Date.from(utcNow)
        val searchEnd = Date.from(utcNow.plusSeconds(hoursAhead * 60L * 60L))

        // 并行预测每颗卫星的过境：每颗卫星的 SGP4/SDP4 计算是独立的，
        // 使用 async + awaitAll 充分利用多核 CPU，相比串行 mapNotNull 显著降低总耗时。
        // 对超大列表分批处理，避免同时派发过多协程导致调度压力。
        val passes = coroutineScope {
            sourcedTles
                .chunked(CHUNK_SIZE)
                .flatMap { batch ->
                    batch.map { sourcedTle ->
                        async(Dispatchers.Default) {
                            predictSinglePass(sourcedTle, groundStation, now, searchEnd)
                        }
                    }.awaitAll()
                }
                .filterNotNull()
        }

        // 当前在境内的优先显示，其次按 AOS 时间排序
        passes
            .sortedWith(
                compareByDescending<SatelliteInfo> { it.isCurrentlyVisible }
                    .thenBy { it.aosTime }
            )
    }

    private companion object {
        /**
         * 单批并行预测的卫星数量上限。
         * 过大会一次性派发过多协程、增加调度开销；
         * 过小则并行度不足。业余卫星总数约 20~30 颗，分批意义不大，
         * 但为应对未来数据源扩展（如全量 CelesTrak）保留分批能力。
         */
        private const val CHUNK_SIZE = 32
    }

    private fun predictSinglePass(
        sourcedTle: SourcedTLE,
        groundStation: GroundStationPosition,
        now: Date,
        searchEnd: Date
    ): SatelliteInfo? {
        return try {
            val tle = sourcedTle.tle
            // 不在 catalog 中的卫星返回空 modes 列表（UI 显示"未知"）
            val modes = SatelliteCatalog.MODES_BY_CATALOG_NUMBER[tle.catnum].orEmpty()

            val predictor = PassPredictor(tle, groundStation)

            // 判断当前是否在境内（仰角 > 0），仅作为元数据，不影响下次过境计算
            val currentPos = predictor.getSatPos(now)
            val isCurrentlyVisible = currentPos != null && currentPos.elevation > 0

            // 始终取"下次过境"（AOS > now），确保 AOS 在未来，
            // 这样 UI 列表与提醒项都不会因为在境卫星被过滤而丢失下次过境。
            // nextSatPass(now, false) 在当前在境时会跳过本次过境、返回下一次。
            val nextPass = predictor.nextSatPass(now, false)
            if (nextPass == null || nextPass.startTime == null || nextPass.endTime == null) return null
            // 防御：若实现差异导致返回的 startTime 仍 < now，则跳过避免提醒无法调度
            if (nextPass.startTime.before(now)) return null

            // 只取预测窗口内的过境
            if (nextPass.startTime.after(searchEnd)) return null

            val aosInstant = nextPass.startTime.toInstant()

            SatelliteInfo(
                name = tle.name.trim().ifEmpty { tle.catnum.toString() },
                catalogNumber = tle.catnum,
                modes = modes,
                aosTime = aosInstant,
                losTime = nextPass.endTime.toInstant(),
                maxElevation = nextPass.maxEl,
                aosAzimuth = nextPass.aosAzimuth,
                losAzimuth = nextPass.losAzimuth,
                isCurrentlyVisible = isCurrentlyVisible,
                source = sourcedTle.source,
                status = sourcedTle.status
            )
        } catch (_: SatNotFoundException) {
            // 卫星在当前位置不可见或计算失败，跳过
            null
        } catch (_: IllegalArgumentException) {
            // TLE 或地面站参数异常，跳过
            null
        } catch (e: CancellationException) {
            // 协程取消：必须向上传播，不能静默吞掉
            throw e
        } catch (_: Exception) {
            // predict4java 内部的其他异常（如 NPE、数组越界、轨道计算溢出），
            // 跳过单颗卫星，避免整体崩溃。
            null
        }
    }
}

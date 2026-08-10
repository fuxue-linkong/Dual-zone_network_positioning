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
import java.util.Date
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.ln

/**
 * 地球半径（km），与 Look4Sat / PREDICT 对齐。
 */
private const val EARTH_RADIUS_KM = 6378.137

/**
 * 判断卫星是否可能从地面站纬度可见（几何可达性预检）。
 *
 * 移植自 Look4Sat 的 [OrbitalObject.willBeSeen] 算法：
 * 由平均运动求出半长轴与远地点高度，再与轨道倾角共同判定其覆盖纬度范围能否
 * 覆盖观测者纬度。无法覆盖 → 本站永不可见过境，直接跳过，避免无效的 SGP4 扫描。
 *
 * meanmo ≈ 0 视为已衰变 / 无效 TLE，返回 false。
 */
internal fun willBeSeenAt(
    meanMotion: Double,
    eccentricity: Double,
    inclinationDeg: Double,
    observerLatDeg: Double
): Boolean {
    if (meanMotion < 1e-8) return false
    val sma = 331.25 * exp(ln(1440.0 / meanMotion) * (2.0 / 3.0))
    val apogee = sma * (1.0 + eccentricity) - EARTH_RADIUS_KM
    var lin = inclinationDeg
    if (lin >= 90.0) lin = 180.0 - lin
    return acos(EARTH_RADIUS_KM / (apogee + EARTH_RADIUS_KM)) + Math.toRadians(lin) > abs(Math.toRadians(observerLatDeg))
}

/**
 * 基于 predict4java 计算卫星过境信息。
 *
 * 性能优化要点：
 * 1. 不再调用 [PassPredictor.nextSatPass]（库内部默认搜到 7 天上限），
 *    改用 [PassPredictor.getSatPos] 按步长采样仰角，搜索范围严格限制在 [hoursAhead] 内。
 * 2. 剔除 GEO / 高轨卫星（周期过长），避免对永不可见过境的卫星做无效扫描。
 * 3. 全量并发派发（不再分批串行 await），让 Default 线程池自行调度。
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
        val utcNow = Instant.now()
        val now = Date.from(utcNow)
        val searchEndMs = utcNow.toEpochMilli() + hoursAhead * 60L * 60L * 1000L

        // 全量并发：每颗卫星的 SGP4/SDP4 计算独立，一次性派发让 Default 线程池自行调度。
        // 相比分批 awaitAll，避免批间串行等待，在多核设备上并行度更高。
        val passes = coroutineScope {
            sourcedTles
                .map { sourcedTle ->
                    async(Dispatchers.Default) {
                        predictSinglePass(sourcedTle, groundStation, now, searchEndMs)
                    }
                }
                .awaitAll()
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
         * 仰角采样步长（分钟）。用于粗扫描过境窗口。
         * LEO 卫星过境持续 10-20 分钟，5 分钟步长足以捕捉所有过境的 AOS/LOS 大致位置，
         * 随后用二分法精化到秒级。步长过大可能漏掉极短过境，过小则增加采样次数。
         */
        private const val SAMPLE_STEP_MINUTES = 5L

        /**
         * GEO / 高轨卫星周期阈值（分钟）。
         * 周期 > 此值的卫星（近 GEO，~1436 分钟）要么对地面站永远可见、要么永不过境，
         * nextSatPass 对它们会扫满内置 7 天上限才返回 null，跳过可显著降低无效计算。
         */
        private const val GEO_PERIOD_THRESHOLD_MINUTES = 1200.0

        /**
         * 二分法精化 AOS/LOS 的时间精度（毫秒）。10 秒精度足以满足 UI 显示与提醒调度。
         */
        private const val BISECTION_PRECISION_MS = 10_000L

        /**
         * 单次过境最大持续时间（毫秒）：取该卫星轨道周期的 1.5 倍。
         *
         * SGP4 传播器在 TLE 过期 / 近再入时可能因数值不稳定产生假性恒定仰角，
         * 使 [findNextPass] 找不到 LOS，AOS-LOS 直接跨到 searchEndMs（最长 48h），
         * UI 上表现为"在境 48 小时"等荒谬时长。以轨道周期动态设上限，而非固定小时数，
         * 对近地 / 中高轨卫星都能精确滤除这类异常，同时不影响真实长过境（HEO/Molniya）。
         */
        private fun maxPassDurationMs(meanMotion: Double): Long {
            if (meanMotion <= 0.0) return 3L * 60L * 60L * 1000L
            val periodMinutes = 1440.0 / meanMotion
            return ((periodMinutes * 1.5) * 60L * 1000L).toLong()
        }
    }

    private fun predictSinglePass(
        sourcedTle: SourcedTLE,
        groundStation: GroundStationPosition,
        now: Date,
        searchEndMs: Long
    ): SatelliteInfo? {
        return try {
            val tle = sourcedTle.tle
            // 不在 catalog 中的卫星返回空 modes 列表（UI 显示"未知"）
            val modes = SatelliteCatalog.MODES_BY_CATALOG_NUMBER[tle.catnum].orEmpty()

            // 预过滤：剔除 GEO / 高轨卫星，避免对永不可见过境的卫星做无效 SGP4 扫描
            val meanMotion = tle.meanmo // 转/天
            if (meanMotion > 0.0) {
                val periodMinutes = 1440.0 / meanMotion
                if (periodMinutes > GEO_PERIOD_THRESHOLD_MINUTES) return null
            }

            // 几何可达性预检（参照 Look4Sat willBeSeen）：
            // 远地点 + 轨道倾角无法覆盖观测者纬度 → 本站永不可见，直接跳过。
            // 同时过滤 meanmo ≈ 0 的已衰变 / 无效 TLE。
            if (!willBeSeenAt(meanMotion, tle.eccn, tle.incl, groundStation.latitude)) return null

            val predictor = PassPredictor(tle, groundStation)
            val nowMs = now.time

            // 判断当前是否在境内（仰角 > 0），仅作为元数据，不影响下次过境计算
            val currentPos = predictor.getSatPos(now)
            val isCurrentlyVisible = currentPos != null && currentPos.elevation > 0

            // 自实现过境搜索：按步长采样仰角，严格限制在 searchEnd 内，
            // 避免库内 nextSatPass 扫到 7 天上限。
            val pass = findNextPass(predictor, nowMs, searchEndMs) ?: return null
            val aosMs = pass.aosMs
            val losMs = pass.losMs
            if (aosMs >= searchEndMs) return null

            // 动态过境时长校验：基于轨道周期而非固定小时数。
            // SGP4 在 TLE 过期 / 近再入时可能假性恒定可见，AOS-LOS 跨整个搜索窗口，
            // 以 1.5 × 轨道周期为上限滤除，避免 UI 显示荒谬的过境时长。
            if (losMs - aosMs > maxPassDurationMs(meanMotion)) return null

            SatelliteInfo(
                name = tle.name.trim().ifEmpty { tle.catnum.toString() },
                catalogNumber = tle.catnum,
                modes = modes,
                aosTime = Instant.ofEpochMilli(aosMs),
                losTime = Instant.ofEpochMilli(losMs),
                maxElevation = pass.maxElevation,
                aosAzimuth = pass.aosAzimuth,
                losAzimuth = pass.losAzimuth,
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

    /**
     * 按固定步长采样仰角，寻找 [fromMs, toMs] 内的下次过境（仰角从 ≤0 穿越到 >0）。
     * 命中后用二分法精化 AOS/LOS 到秒级，并取过境窗口内的最大仰角。
     *
     * 当前已在境内时，取本次过境的剩余 LOS 作为结果（AOS 回退到 fromMs），
     * 确保 UI 列表与提醒项不会丢失在境卫星。
     */
    private fun findNextPass(
        predictor: PassPredictor,
        fromMs: Long,
        toMs: Long
    ): PassResult? {
        val stepMs = SAMPLE_STEP_MINUTES * 60L * 1000L
        var prevEl = elevationAt(predictor, fromMs)

        // 当前已在境内：记录本次过境，继续扫描 LOS
        if (prevEl > 0) {
            var losMs = -1L
            var t = fromMs + stepMs
            var maxEl = prevEl
            var maxElMs = fromMs
            while (t <= toMs) {
                val el = elevationAt(predictor, t)
                if (el > maxEl) {
                    maxEl = el
                    maxElMs = t
                }
                if (el <= 0) {
                    losMs = refineZeroCrossing(predictor, t - stepMs, t, crossingUp = false)
                    break
                }
                t += stepMs
            }
            if (losMs < 0) return null // 整个窗口内都在境内（异常情况），跳过
            val maxElInfo = maxElevationAzimuthAt(predictor, maxElMs, fromMs, losMs)
            return PassResult(
                aosMs = fromMs,
                losMs = losMs,
                maxElevation = maxElInfo.first,
                aosAzimuth = azimuthAt(predictor, fromMs),
                losAzimuth = azimuthAt(predictor, losMs)
            )
        }

        // 正常搜索：仰角从 ≤0 穿越到 >0 = AOS
        var t = fromMs + stepMs
        while (t <= toMs) {
            val el = elevationAt(predictor, t)
            if (prevEl <= 0 && el > 0) {
                // 命中 AOS：精化 AOS，然后向前找 LOS
                val aosMs = refineZeroCrossing(predictor, t - stepMs, t, crossingUp = true)
                val losResult = findLosAfter(predictor, aosMs, toMs, stepMs)
                if (losResult == null) return null // 窗口结束前未出境
                val (losMs, maxEl, maxElMs) = losResult
                val maxElValue = if (maxElMs == aosMs) {
                    maxEl
                } else {
                    maxElevationAzimuthAt(predictor, maxElMs, aosMs, losMs).first
                }
                return PassResult(
                    aosMs = aosMs,
                    losMs = losMs,
                    maxElevation = maxElValue,
                    aosAzimuth = azimuthAt(predictor, aosMs),
                    losAzimuth = azimuthAt(predictor, losMs)
                )
            }
            prevEl = el
            t += stepMs
        }
        return null
    }

    /**
     * 在 [aosMs] 之后寻找 LOS（仰角从 >0 穿越到 ≤0），同时记录窗口内最大仰角。
     */
    private fun findLosAfter(
        predictor: PassPredictor,
        aosMs: Long,
        toMs: Long,
        stepMs: Long
    ): Triple<Long, Double, Long>? {
        var t = aosMs + stepMs
        var prevEl = elevationAt(predictor, t - stepMs)
        var maxEl = prevEl
        var maxElMs = aosMs
        while (t <= toMs) {
            val el = elevationAt(predictor, t)
            if (el > maxEl) {
                maxEl = el
                maxElMs = t
            }
            if (prevEl > 0 && el <= 0) {
                val losMs = refineZeroCrossing(predictor, t - stepMs, t, crossingUp = false)
                return Triple(losMs, maxEl, maxElMs)
            }
            prevEl = el
            t += stepMs
        }
        return null
    }

    /**
     * 二分法精化仰角过零点（AOS 为上升过零，LOS 为下降过零）到 [BISECTION_PRECISION_MS] 精度。
     */
    private fun refineZeroCrossing(
        predictor: PassPredictor,
        loMs: Long,
        hiMs: Long,
        crossingUp: Boolean
    ): Long {
        var lo = loMs
        var hi = hiMs
        while (hi - lo > BISECTION_PRECISION_MS) {
            val mid = (lo + hi) / 2
            val el = elevationAt(predictor, mid)
            if (crossingUp) {
                // AOS: lo 处 ≤0，hi 处 >0，mid >0 时收缩 hi
                if (el > 0) hi = mid else lo = mid
            } else {
                // LOS: lo 处 >0，hi 处 ≤0，mid ≤0 时收缩 hi
                if (el <= 0) hi = mid else lo = mid
            }
        }
        return if (crossingUp) hi else lo
    }

    /**
     * 在 [aosMs, losMs] 窗口内以更细步长搜索最大仰角及其方位角。
     * 粗扫描已给出大致 maxElMs，这里在其邻域精化。
     */
    private fun maxElevationAzimuthAt(
        predictor: PassPredictor,
        maxElMs: Long,
        aosMs: Long,
        losMs: Long
    ): Pair<Double, Int> {
        // 在 maxElMs ± stepMs 邻域内以 1 分钟步长精化最大仰角
        val fineStepMs = 60L * 1000L
        val searchStart = maxOf(maxElMs - 5L * 60L * 1000L, aosMs)
        val searchEnd = minOf(maxElMs + 5L * 60L * 1000L, losMs)
        var bestEl = elevationAt(predictor, maxElMs)
        var bestMs = maxElMs
        var t = searchStart
        while (t <= searchEnd) {
            val el = elevationAt(predictor, t)
            if (el > bestEl) {
                bestEl = el
                bestMs = t
            }
            t += fineStepMs
        }
        val pos = predictor.getSatPos(Date(bestMs))
        return bestEl to (pos?.let { Math.toDegrees(it.azimuth).toInt() } ?: 0)
    }

    private fun elevationAt(predictor: PassPredictor, timeMs: Long): Double {
        val pos = predictor.getSatPos(Date(timeMs)) ?: return -1.0
        return Math.toDegrees(pos.elevation)
    }

    private fun azimuthAt(predictor: PassPredictor, timeMs: Long): Int {
        val pos = predictor.getSatPos(Date(timeMs)) ?: return 0
        return Math.toDegrees(pos.azimuth).toInt()
    }

    private data class PassResult(
        val aosMs: Long,
        val losMs: Long,
        val maxElevation: Double,
        val aosAzimuth: Int,
        val losAzimuth: Int
    )
}

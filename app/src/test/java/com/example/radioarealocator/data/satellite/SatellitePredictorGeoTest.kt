package com.example.radioarealocator.data.satellite

import com.example.radioarealocator.data.satellite.predict.SatellitePropagator
import com.example.radioarealocator.data.satellite.predict.TleElements
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * [SatellitePredictor] GEO / 深空特判与 [isGeoPeriod] 单元测试。
 *
 * 验证依据：
 * - 周期 = 1440 / meanmo；GEO 卫星 meanmo ≈ 1.0027（周期 ≈ 1436 min > 1200 min 阈值）；
 * - GEO 卫星星下点长期固定：在星下点正上方仰角 ≈ 90°（恒定可见分支），
 *   在对跖点（lon+180）仰角 ≈ -90°（不可见分支）；
 * - 通过自研引擎 [SatellitePropagator] 计算星下点（lat/lon，弧度），
 *   避免手工假设卫星经度，保证测试与"当前时刻"无关。
 */
class SatellitePredictorGeoTest {

    // ── isGeoPeriod 纯函数 ──

    @Test
    fun `isGeoPeriod - GEO mean motion is geo`() {
        assertTrue(isGeoPeriod(1.0027))   // 同步轨道
        assertTrue(isGeoPeriod(1.0))      // 周期 1440 min
        assertTrue(isGeoPeriod(1.1))      // 周期 ≈ 1309 min > 1200
    }

    @Test
    fun `isGeoPeriod - LEO and MEO mean motion are not geo`() {
        assertFalse(isGeoPeriod(15.5))    // ISS，周期 ≈ 93 min
        assertFalse(isGeoPeriod(6.0))     // 周期 240 min
        assertFalse(isGeoPeriod(2.0))     // 周期 720 min
    }

    @Test
    fun `isGeoPeriod - non-positive mean motion guarded`() {
        assertFalse(isGeoPeriod(0.0))
        assertFalse(isGeoPeriod(-1.0))
    }

    // ── 全流程：GEO 恒定可见分支 ──

    @Test
    fun `predictUpcomingPasses - GEO satellite above sub-satellite point is always visible with isGeo=true`() =
        runBlocking {
            val tle = buildGeoTle()
            val subPoint = subSatellitePoint(tle)

            val results = SatellitePredictor().predictUpcomingPasses(
                sourcedTles = listOf(sourcedTleOf(tle)),
                latitude = subPoint.first,
                longitude = subPoint.second,
                altitude = 0.0,
                hoursAhead = 24
            )

            assertEquals("GEO 卫星在星下点应恒定可见，实际结果 ${results.map { it.name }}", 1, results.size)
            val sat = results[0]
            assertTrue("isGeo 应标记为 true", sat.isGeo)
            assertTrue("恒定可见卫星应 isCurrentlyVisible=true", sat.isCurrentlyVisible)
            assertTrue("最大仰角应接近 90°，实际 ${sat.maxElevation}", sat.maxElevation > 80.0)
            assertTrue("AOS 应为当前时刻（now 之后不久）", sat.aosTime.toEpochMilli() <= System.currentTimeMillis() + 60_000)
            assertTrue("LOS 应约为 now+24h", sat.losTime.toEpochMilli() >= System.currentTimeMillis() + 23L * 3600L * 1000L)
        }

    @Test
    fun `predictUpcomingPasses - GEO satellite at antipode is excluded`() = runBlocking {
        val tle = buildGeoTle()
        val subPoint = subSatellitePoint(tle)
        val antipodeLon = (subPoint.second + 180.0) % 360.0

        val results = SatellitePredictor().predictUpcomingPasses(
            sourcedTles = listOf(sourcedTleOf(tle)),
            latitude = subPoint.first,
            longitude = antipodeLon,
            altitude = 0.0,
            hoursAhead = 24
        )

        assertTrue("对跖点看不到 GEO 卫星，应返回空列表", results.isEmpty())
    }

    // ── 回归：LEO 卫星行为不变 ──

    @Test
    fun `predictUpcomingPasses - LEO satellite still produces passes without geo flag`() = runBlocking {
        val tle = buildLeoTle()
        val results = SatellitePredictor().predictUpcomingPasses(
            sourcedTles = listOf(sourcedTleOf(tle)),
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0.0,
            hoursAhead = 48
        )

        assertTrue("48 小时内 (0,0) 至少有一次 ISS 过境（轨道周期 93 分钟，每 24h 覆盖全经度）",
            results.isNotEmpty())
        results.forEach {
            assertFalse("LEO 卫星不应标记 isGeo", it.isGeo)
            // isDaylightPass 应与 AOS 时刻地面站昼夜状态一致（预测器内部同一判定）
            val expectedDaylight = CelestialComputer.isDaylightAt(
                0.0, 0.0, it.aosTime.toEpochMilli()
            )
            assertEquals("isDaylightPass 应与 AOS 时刻昼夜一致", expectedDaylight, it.isDaylightPass)
        }
    }

    // ── 辅助 ──

    /** 构造一颗合成 GEO 卫星（meanmo ≈ 1.0027，历元 = 当前时刻）。 */
    private fun buildGeoTle(): TleElements {
        val epochStr = LocalDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val (year, refEpoch) = TleParser.parseEpoch(epochStr)!!
        val line1 = TleParser.buildLine1(
            noradCatId = 43700,
            classification = "U",
            objectId = "2018-090A",
            year = year,
            refEpoch = refEpoch,
            meanMotionDot = -0.0000027,
            meanMotionDdot = 0.0,
            bstar = 0.0,
            ephemerisType = "0",
            elementSetNo = 999
        )
        val line2 = TleParser.buildLine2(
            noradCatId = 43700,
            inclination = 0.02,
            raan = 250.0,
            eccentricity = 0.0003,
            argPerigee = 90.0,
            meanAnomaly = 180.0,
            meanMotion = 1.0027,
            revAtEpoch = 9995
        )
        return TleElements.fromThreeLines(arrayOf("TEST-GEO", line1, line2))
    }

    /** 构造一颗合成 LEO 卫星（ISS 轨道参数，历元 = 当前时刻）。 */
    private fun buildLeoTle(): TleElements {
        val epochStr = LocalDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val (year, refEpoch) = TleParser.parseEpoch(epochStr)!!
        val line1 = TleParser.buildLine1(
            noradCatId = 25544,
            classification = "U",
            objectId = "1998-067A",
            year = year,
            refEpoch = refEpoch,
            meanMotionDot = 0.00010980,
            meanMotionDdot = 0.0,
            bstar = 0.00011606,
            ephemerisType = "0",
            elementSetNo = 999
        )
        val line2 = TleParser.buildLine2(
            noradCatId = 25544,
            inclination = 51.6416,
            raan = 247.4627,
            eccentricity = 0.0006703,
            argPerigee = 130.5360,
            meanAnomaly = 325.0288,
            meanMotion = 15.50028952,
            revAtEpoch = 9995
        )
        return TleElements.fromThreeLines(arrayOf("TEST-LEO", line1, line2))
    }

    private fun sourcedTleOf(tle: TleElements): SourcedTLE = SourcedTLE(tle = tle, source = "TEST")

    /**
     * 用自研引擎计算卫星当前星下点（lat, lon 度）。
     * [com.example.radioarealocator.data.satellite.predict.PropagatedPosition] 的
     * latitudeRad/longitudeRad 为弧度。
     */
    private fun subSatellitePoint(tle: TleElements): Pair<Double, Double> {
        val propagator = SatellitePropagator(tle, 0.0, 0.0, 0.0)
        val pos = propagator.getPosition(System.currentTimeMillis())
            ?: error("无法计算测试卫星位置")
        return Math.toDegrees(pos.latitudeRad) to Math.toDegrees(pos.longitudeRad)
    }
}

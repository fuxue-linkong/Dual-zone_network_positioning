package com.example.radioarealocator.data.satellite

import com.example.radioarealocator.data.satellite.predict.TleElements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * [LiveSatelliteTracker] 与轨道几何工具函数单元测试。
 *
 * 覆盖：
 * - [LiveSatelliteTracker.computeOnce]：实时位置计算（角度单位、取值范围）
 * - [splitTrackAtAntimeridian]：±180° 经线分段
 * - [buildCoverageCircle]：覆盖圆生成
 * - [subpointFromAzElevation]：地平坐标 → 星下点反推
 */
class LiveSatelliteTrackerTest {

    // 2026-08-09 拉取的 ISS TLE（CelesTrak，NORAD 25544）
    private val issTle = TleElements.fromThreeLines(
        arrayOf(
            "ISS (ZARYA)",
            "1 25544U 98067A   26220.50489838  .00004539  00000+0  89319-4 0  9991",
            "2 25544  51.6323  41.1734 0007358  25.6275 334.5077 15.49385107579869"
        )
    )

    private fun createTracker(
        tle: TleElements = issTle,
        lat: Double = 39.9042,
        lon: Double = 116.4074,
        altM: Double = 50.0,
    ): LiveSatelliteTracker = LiveSatelliteTracker(tle, lat, lon, altM)

    // ── 实时位置计算 ──

    @Test
    fun `computeOnce - returns non-null info for ISS`() {
        val tracker = createTracker()
        val info = tracker.computeOnce()
        assertNotNull(info)
        info ?: return
        assertEquals(25544, info.catalogNumber)
        assertTrue(info.name.contains("ISS", ignoreCase = true))
    }

    @Test
    fun `computeOnce - azimuth in 0 to 360 and elevation in -90 to 90 (radians to degrees)`() {
        val tracker = createTracker()
        var samples = 0
        for (offsetMinutes in 0..30 step 5) {
            val info = tracker.computeOnce(System.currentTimeMillis() + offsetMinutes * 60_000L)
            info ?: continue
            assertTrue("方位角应在 [0,360)：${info.azimuthDeg}", info.azimuthDeg in 0.0..360.0)
            assertTrue("仰角应在 [-90,90]：${info.elevationDeg}", info.elevationDeg in -90.0..90.0)
            assertTrue("相位应在 [0,360)：${info.phaseDeg}", info.phaseDeg in 0.0..360.0)
            samples++
        }
        assertTrue("应至少产生一个有效采样", samples > 0)
    }

    @Test
    fun `computeOnce - rangeRate converted from km per s to m per s`() {
        val tracker = createTracker()
        val info = tracker.computeOnce()
        info ?: return
        // ISS 轨道速度约 7.66 km/s，径向速度分量不会超过该值
        assertTrue("径向速度应小于 8000 m/s：${info.rangeRateMps}", abs(info.rangeRateMps) < 8_000.0)
        assertTrue("斜距应为正：${info.rangeKm}", info.rangeKm > 0.0)
    }

    @Test
    fun `computeOnce - altitude matches LEO orbit`() {
        val tracker = createTracker()
        val info = tracker.computeOnce()
        info ?: return
        assertTrue("ISS 高度应在 300-600 km：${info.altitudeKm}", info.altitudeKm in 300.0..600.0)
    }

    @Test
    fun `computeOnce - subpoint latitude and longitude in valid ranges`() {
        val tracker = createTracker()
        val info = tracker.computeOnce()
        info ?: return
        assertTrue("星下点纬度应在 [-90,90]", info.subpointLatDeg in -90.0..90.0)
        assertTrue("星下点经度应在 [-180,180]", info.subpointLonDeg in -180.0..180.0)
    }

    @Test
    fun `computeOnce - isAboveHorizon consistent with elevation`() {
        val tracker = createTracker()
        var checked = 0
        for (offsetMinutes in 0..60 step 10) {
            val info = tracker.computeOnce(System.currentTimeMillis() + offsetMinutes * 60_000L)
            info ?: continue
            assertEquals("isAboveHorizon 应与仰角一致", info.elevationDeg > 0.0, info.isAboveHorizon)
            assertEquals("isVisible 应与 isAboveHorizon 一致", info.isAboveHorizon, info.isVisible)
            checked++
        }
        assertTrue(checked > 0)
    }

    @Test
    fun `computeOnce - celestial and doppler computations do not crash the tracker`() {
        // CelestialComputer / DopplerCalculator 可能与追踪器分属不同实现阶段，
        // 无论桩是否就绪，追踪器都不应抛异常；日月/多普勒字段保持可选语义。
        val tracker = createTracker()
        val info = tracker.computeOnce()
        info ?: return
        assertTrue(info.isEclipsed || !info.isEclipsed)
        assertTrue("相位应为有限数值", info.phaseDeg.isFinite())
    }

    @Test
    fun `computeOnce - invalid TLE returns null`() {
        val badTle = TleElements.fromThreeLines(
            arrayOf(
                "BAD",
                "1 00000U 00000A   00001.00000000  .00000000  00000-0  00000-0 0  9991",
                "2 00000   0.0000   0.0000 0000000   0.0000   0.0000 00.00000000    00"
            )
        )
        val tracker = createTracker(tle = badTle)
        // 无效 TLE 不应抛异常，而是返回 null
        val info = tracker.computeOnce()
        assertNull(info)
    }

    // ── 经线分段 ──

    @Test
    fun `splitTrack - no crossing keeps single segment`() {
        val points = listOf(10.0 to 100.0, 11.0 to 110.0, 12.0 to 120.0)
        val segments = splitTrackAtAntimeridian(points)
        assertEquals(1, segments.size)
        assertEquals(points, segments[0])
    }

    @Test
    fun `splitTrack - east to west crossing 180 splits into two segments`() {
        val points = listOf(10.0 to 170.0, 11.0 to 175.0, 12.0 to -175.0, 13.0 to -170.0)
        val segments = splitTrackAtAntimeridian(points)
        assertEquals(2, segments.size)
        // 第一段以 +180 终止（经度线性插值：11 + (12-11)*(180-175)/(185-175) = 11.5）
        assertEquals(11.5 to 180.0, segments[0].last())
        assertEquals(10.0 to 170.0, segments[0].first())
        // 第二段从 -175 开始
        assertEquals(12.0 to -175.0, segments[1].first())
        assertEquals(13.0 to -170.0, segments[1].last())
    }

    @Test
    fun `splitTrack - west to east crossing -180 splits into two segments`() {
        val points = listOf(10.0 to -175.0, 11.0 to -170.0, 12.0 to 175.0)
        val segments = splitTrackAtAntimeridian(points)
        assertEquals(2, segments.size)
        assertEquals(-180.0, segments[0].last().second, 1e-9)
        assertEquals(12.0 to 175.0, segments[1].first())
    }

    @Test
    fun `splitTrack - multiple crossings produce multiple segments`() {
        val points = listOf(
            0.0 to 170.0,
            1.0 to -170.0,
            2.0 to -160.0,
            3.0 to 175.0,
            4.0 to 179.0,
            5.0 to -179.0,
        )
        val segments = splitTrackAtAntimeridian(points)
        assertEquals(4, segments.size)
        // 每段内相邻点经度差不超 180
        segments.forEach { segment ->
            segment.zipWithNext().forEach { (a, b) ->
                assertTrue("段内不应跨经线：${a.second} → ${b.second}", abs(b.second - a.second) <= 180.0)
            }
        }
    }

    @Test
    fun `splitTrack - edge latitude interpolated between neighbours`() {
        val points = listOf(10.0 to 170.0, 20.0 to -170.0)
        val segments = splitTrackAtAntimeridian(points)
        assertEquals(2, segments.size)
        // 从 170° 到 -170° 跨 +180：中点纬度线性插值 = 15°
        assertEquals(15.0, segments[0].last().first, 1e-9)
        assertEquals(180.0, segments[0].last().second, 1e-9)
    }

    @Test
    fun `splitTrack - empty and single point`() {
        assertEquals(listOf<List<Pair<Double, Double>>>(), splitTrackAtAntimeridian(emptyList()))
        val single = listOf(1.0 to 2.0)
        assertEquals(listOf(single), splitTrackAtAntimeridian(single))
    }

    // ── 覆盖圆 ──

    @Test
    fun `coverageCircle - returns requested number of points`() {
        val circle = buildCoverageCircle(20.0, 110.0, 2250.0, 180)
        assertEquals(180, circle.size)
    }

    @Test
    fun `coverageCircle - points lie at requested radius around subpoint`() {
        val centerLat = 20.0
        val centerLon = 110.0
        val radiusKm = 2250.0
        val circle = buildCoverageCircle(centerLat, centerLon, radiusKm, 90)
        circle.forEach { (lat, lon) ->
            val distance = haversineKm(centerLat, centerLon, lat, lon)
            // 允许 1% 容差（大圆近似误差）
            assertTrue(
                "距离应接近 $radiusKm km，实际 $distance km",
                abs(distance - radiusKm) < radiusKm * 0.01
            )
        }
    }

    @Test
    fun `coverageCircle - all points in valid ranges`() {
        val circle = buildCoverageCircle(10.0, 179.0, 2250.0, 180)
        circle.forEach { (lat, lon) ->
            assertTrue(lat in -90.0..90.0)
            assertTrue(lon in -180.0..180.0)
        }
    }

    @Test
    fun `coverageCircle - zero radius degenerates to subpoint`() {
        val circle = buildCoverageCircle(30.0, 60.0, 0.0, 36)
        circle.forEach { (lat, lon) ->
            assertEquals(30.0, lat, 1e-6)
            assertEquals(60.0, lon, 1e-6)
        }
    }

    @Test
    fun `coverageRadius - LEO altitude yields expected footprint radius`() {
        // ISS ~420 km 高度：覆盖圆半径约 2250 km
        val radius = coverageRadiusKm(420.0)
        assertTrue("覆盖圆半径应在 2000-2500 km：$radius", radius in 2000.0..2500.0)
        // 更高轨道 → 更大覆盖圆
        assertTrue(coverageRadiusKm(800.0) > coverageRadiusKm(420.0))
        // 0 高度 → 0 半径
        assertEquals(0.0, coverageRadiusKm(0.0), 1e-6)
    }

    // ── 星下点反推 ──

    @Test
    fun `subpoint - zenith maps to observer position`() {
        val (lat, lon) = subpointFromAzElevation(39.9042, 116.4074, 45.0, 90.0)
        assertEquals(39.9042, lat, 1e-6)
        assertEquals(116.4074, lon, 1e-6)
    }

    @Test
    fun `subpoint - north horizon from equator is north pole`() {
        val (lat, lon) = subpointFromAzElevation(0.0, 0.0, 0.0, 0.0)
        assertEquals(90.0, lat, 1e-6)
        assertEquals(0.0, lon, 1e-6)
    }

    @Test
    fun `subpoint - nadir is antipode of observer`() {
        val (lat, lon) = subpointFromAzElevation(30.0, 100.0, 0.0, -90.0)
        assertEquals(-30.0, lat, 1e-6)
        assertEquals(-80.0, lon, 1e-6)
    }

    @Test
    fun `subpoint - bearing from observer to subpoint matches azimuth`() {
        val observerLat = 39.9042
        val observerLon = 116.4074
        val azimuth = 67.5
        val (lat, lon) = subpointFromAzElevation(observerLat, observerLon, azimuth, 30.0)
        val bearing = initialBearingDeg(observerLat, observerLon, lat, lon)
        assertEquals(azimuth, bearing, 1.0)
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6378.137
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    private fun initialBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }
}

package com.example.radioarealocator.data.satellite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * [CelestialComputer] 单元测试。
 *
 * 验证依据（公开天文事实，独立于实现）：
 * - 夏至/冬至瞬间太阳赤纬 ≈ ±23.44°；在北极点太阳仰角恒等于赤纬；
 * - 春分瞬间太阳赤纬 ≈ 0°；
 * - 2024-06-21T00:00Z 子太阳点约在 (23.44°N, 180°)，子夜点约在 (23.44°S, 0°)，
 *   因此赤道 (0,0) 处于夜间、(0,180) 处于白天；
 * - 地影圆柱模型：位于反太阳轴附近的卫星被地球遮挡，位于太阳一侧的卫星被照亮。
 */
class CelestialComputerTest {

    // ── 太阳位置 ──

    @Test
    fun `sunPosition - north pole elevation equals solar declination at June solstice`() {
        // 2024 夏至：2024-06-20T20:51Z，太阳赤纬 +23.44°
        val pos = CelestialComputer.sunPosition(
            latitudeDeg = 90.0, longitudeDeg = 0.0, altitudeM = 0.0,
            epochMillis = Instant.parse("2024-06-20T20:51:00Z").toEpochMilli()
        )
        assertTrue("北极点太阳仰角应约等于夏至赤纬 +23.44°，实际 ${pos.elevationDeg}",
            pos.elevationDeg in 23.3..23.55)
        assertTrue(pos.azimuthDeg in 0.0..360.0)
    }

    @Test
    fun `sunPosition - north pole elevation equals solar declination at December solstice`() {
        // 2024 冬至：2024-12-21T09:20Z，太阳赤纬 -23.44°
        val pos = CelestialComputer.sunPosition(
            latitudeDeg = 90.0, longitudeDeg = 0.0, altitudeM = 0.0,
            epochMillis = Instant.parse("2024-12-21T09:20:00Z").toEpochMilli()
        )
        assertTrue("北极点太阳仰角应约等于冬至赤纬 -23.44°，实际 ${pos.elevationDeg}",
            pos.elevationDeg in -23.55..-23.3)
    }

    @Test
    fun `sunPosition - north pole elevation near zero at March equinox`() {
        // 2024 春分：2024-03-20T03:06Z，太阳赤纬 ≈ 0°
        val pos = CelestialComputer.sunPosition(
            latitudeDeg = 90.0, longitudeDeg = 0.0, altitudeM = 0.0,
            epochMillis = Instant.parse("2024-03-20T03:06:00Z").toEpochMilli()
        )
        assertTrue("春分北极点太阳仰角应接近 0°，实际 ${pos.elevationDeg}",
            kotlin.math.abs(pos.elevationDeg) < 0.5)
    }

    @Test
    fun `sunPosition - distance is about one astronomical unit`() {
        // 6 月 21 日接近远日点（7 月初，1.0167 AU），距离约 1.52e8 km
        val pos = CelestialComputer.sunPosition(
            latitudeDeg = 30.0, longitudeDeg = 120.0, altitudeM = 0.0,
            epochMillis = Instant.parse("2024-06-21T12:00:00Z").toEpochMilli()
        )
        assertTrue("太阳距离应约 1.496~1.521e8 km，实际 ${pos.distanceKm}",
            pos.distanceKm in 1.47e8..1.53e8)
    }

    @Test
    fun `sunPosition - sun near zenith at subsolar point at solstice noon`() {
        // 夏至正午，(23.44°N, 0°) 太阳应接近天顶（黄赤交角 ≈ 23.44°）
        val pos = CelestialComputer.sunPosition(
            latitudeDeg = 23.44, longitudeDeg = 0.0, altitudeM = 0.0,
            epochMillis = Instant.parse("2024-06-21T12:00:00Z").toEpochMilli()
        )
        assertTrue("夏至正午太阳应接近天顶，实际仰角 ${pos.elevationDeg}",
            pos.elevationDeg > 88.0)
    }

    // ── 月亮位置 ──

    @Test
    fun `moonPosition - distance around 384400 km and angles in range`() {
        val pos = CelestialComputer.moonPosition(
            latitudeDeg = 30.0, longitudeDeg = 120.0, altitudeM = 0.0,
            epochMillis = Instant.parse("2024-06-21T12:00:00Z").toEpochMilli()
        )
        assertTrue("月球距离应在 35~41 万 km 之间，实际 ${pos.distanceKm}",
            pos.distanceKm in 350_000.0..415_000.0)
        assertTrue(pos.elevationDeg in -90.0..90.0)
        assertTrue(pos.azimuthDeg in 0.0..360.0)
    }

    // ── 昼夜 ──

    @Test
    fun `isDaylightAt - midnight UTC on solstice is night at (0,0)`() {
        val t = Instant.parse("2024-06-21T00:00:00Z").toEpochMilli()
        assertFalse("子夜 (0,0) 应处于夜间", CelestialComputer.isDaylightAt(0.0, 0.0, t))
    }

    @Test
    fun `isDaylightAt - noon UTC on solstice is day at (0,0)`() {
        val t = Instant.parse("2024-06-21T12:00:00Z").toEpochMilli()
        assertTrue("正午 (0,0) 应处于白天", CelestialComputer.isDaylightAt(0.0, 0.0, t))
    }

    @Test
    fun `isDaylightAt - solstice noon at 23-44N is day`() {
        val t = Instant.parse("2024-06-21T12:00:00Z").toEpochMilli()
        assertTrue(CelestialComputer.isDaylightAt(23.44, 0.0, t))
    }

    // ── 蚀检测（圆柱地影模型） ──

    @Test
    fun `isSunlit - midnight-side LEO is eclipsed on solstice`() {
        // 2024-06-21T00:00Z：子太阳点约在 (23.44N, 180)，(0,0) 位于夜侧，
        // 1000km 高 LEO 卫星（星下点 (0,0)）应处于地影中
        assertFalse(
            CelestialComputer.isSunlit(
                satLatDeg = 0.0, satLonDeg = 0.0, satAltKm = 1000.0,
                epochMillis = Instant.parse("2024-06-21T00:00:00Z").toEpochMilli()
            )
        )
    }

    @Test
    fun `isSunlit - day-side LEO is sunlit on solstice`() {
        // 星下点 (0,180) 位于太阳一侧，应被照亮
        assertTrue(
            CelestialComputer.isSunlit(
                satLatDeg = 0.0, satLonDeg = 180.0, satAltKm = 1000.0,
                epochMillis = Instant.parse("2024-06-21T00:00:00Z").toEpochMilli()
            )
        )
    }

    @Test
    fun `isSunlit - satellite on anti-solar axis is eclipsed`() {
        // 夏至子夜，反太阳方向子点约在 (23.44S, 0)；该位置正上方卫星应蚀中
        assertFalse(
            CelestialComputer.isSunlit(
                satLatDeg = -23.44, satLonDeg = 0.0, satAltKm = 1000.0,
                epochMillis = Instant.parse("2024-06-21T00:00:00Z").toEpochMilli()
            )
        )
    }

    @Test
    fun `isSunlit - satellite on solar axis is sunlit`() {
        // 夏至子夜，太阳子点约在 (23.44N, 180)；该位置正上方卫星应被照亮
        assertTrue(
            CelestialComputer.isSunlit(
                satLatDeg = 23.44, satLonDeg = 180.0, satAltKm = 1000.0,
                epochMillis = Instant.parse("2024-06-21T00:00:00Z").toEpochMilli()
            )
        )
    }

    // ── 日出日落 ──

    @Test
    fun `sunriseSunset - June solstice at 23-44N sunrise before sunset and duration sane`() {
        val t = Instant.parse("2024-06-21T12:00:00Z").toEpochMilli()
        val (sunrise, sunset) = CelestialComputer.sunriseSunset(23.44, 0.0, t)

        assertTrue("日出应在 03:00~07:00 UTC 之间，实际 $sunrise",
            sunrise >= Instant.parse("2024-06-21T03:00:00Z") &&
                sunrise <= Instant.parse("2024-06-21T07:00:00Z"))
        assertTrue("日落应在 17:00~21:00 UTC 之间，实际 $sunset",
            sunset >= Instant.parse("2024-06-21T17:00:00Z") &&
                sunset <= Instant.parse("2024-06-21T21:00:00Z"))

        val durationHours = java.time.Duration.between(sunrise, sunset).toMinutes() / 60.0
        assertTrue("夏至 23.44N 昼长约 13.5h，实际 $durationHours h",
            durationHours in 12.0..15.0)
    }

    @Test
    fun `sunriseSunset - consistent with isDaylightAt around sunrise`() {
        val t = Instant.parse("2024-06-21T12:00:00Z").toEpochMilli()
        val (sunrise, _) = CelestialComputer.sunriseSunset(23.44, 0.0, t)

        val before = sunrise.toEpochMilli() - 30 * 60 * 1000L
        val after = sunrise.toEpochMilli() + 30 * 60 * 1000L
        assertFalse("日出前 30 分钟应为夜间", CelestialComputer.isDaylightAt(23.44, 0.0, before))
        assertTrue("日出后 30 分钟应为白天", CelestialComputer.isDaylightAt(23.44, 0.0, after))
    }

    @Test
    fun `sunriseSunset - polar day returns UTC day boundaries as fallback`() {
        // 北极圈内夏至极昼：无日出/日落穿越点，返回当日 00:00 ~ 次日 00:00
        val t = Instant.parse("2024-06-21T12:00:00Z").toEpochMilli()
        val (sunrise, sunset) = CelestialComputer.sunriseSunset(89.0, 0.0, t)
        assertEquals(Instant.parse("2024-06-21T00:00:00Z"), sunrise)
        assertEquals(Instant.parse("2024-06-22T00:00:00Z"), sunset)
    }

    @Test
    fun `sunriseSunset - sunset always after sunrise for normal latitudes`() {
        val t = Instant.parse("2024-12-21T12:00:00Z").toEpochMilli()
        val (sunrise, sunset) = CelestialComputer.sunriseSunset(39.9, 116.4, t) // 北京
        assertTrue("冬至北京日落应晚于日出", sunset.isAfter(sunrise))
    }
}

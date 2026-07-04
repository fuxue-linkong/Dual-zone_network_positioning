package com.example.radioarealocator.data.zone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ZoneResolver] 单元测试。
 *
 * 覆盖：
 * - 主要人口密集区的 CQ/ITU 分区正确性。
 * - 海洋/偏远地区的兜底公式。
 * - 返回的 maidenhead 与 [MaidenheadCalculator] 一致。
 */
class ZoneResolverTest {

    @Test
    fun `beijing resolves to east asia zones`() {
        val info = ZoneResolver.resolve(39.9042, 116.4074)
        assertEquals(24, info.cqZone)
        assertEquals(44, info.ituZone)
        assertEquals("OM89ev", info.maidenhead)
    }

    @Test
    fun `tokyo resolves to japan zones`() {
        val info = ZoneResolver.resolve(35.6762, 139.6503)
        assertEquals(25, info.cqZone)
        assertEquals(45, info.ituZone)
        assertEquals("PM95tq", info.maidenhead)
    }

    @Test
    fun `new york resolves to north america zones`() {
        val info = ZoneResolver.resolve(40.7128, -74.0060)
        assertEquals(4, info.cqZone)
        assertEquals(8, info.ituZone)
        assertEquals("FN30xr", info.maidenhead)
    }

    @Test
    fun `london resolves to western europe zones`() {
        val info = ZoneResolver.resolve(51.5074, -0.1278)
        assertEquals(14, info.cqZone)
        assertEquals(27, info.ituZone)
        assertEquals("IO91wm", info.maidenhead)
    }

    @Test
    fun `sydney resolves to australia zones`() {
        val info = ZoneResolver.resolve(-33.8688, 151.2093)
        assertEquals(30, info.cqZone)
        assertEquals(55, info.ituZone)
        assertEquals("QF56od", info.maidenhead)
    }

    @Test
    fun `antarctic ocean uses fallback formula`() {
        // 南极海域无精确覆盖，应使用基于经度的兜底公式
        val info = ZoneResolver.resolve(-60.0, -30.0)
        // fallbackCqZone(-30) = floor(150/9) + 1 = 17
        assertEquals(17, info.cqZone)
        // fallbackItuZone(-30) = floor(150/4) + 1 = 38
        assertEquals(38, info.ituZone)
        assertNotNull(info.maidenhead)
        assertTrue(info.maidenhead.length == 6)
    }

    @Test
    fun `fallback cq zone clamps to valid range`() {
        // 经度 -180 应得到 cqZone=1，经度 180 应得到 cqZone=40 但被 clamp
        val west = ZoneResolver.resolve(-85.0, -180.0)
        assertEquals(1, west.cqZone)
        val east = ZoneResolver.resolve(-85.0, 180.0)
        // fallbackCqZone(180) = floor(360/9) + 1 = 41, clamp 至 40
        assertEquals(40, east.cqZone)
    }

    @Test
    fun `fallback itu zone clamps to valid range`() {
        val east = ZoneResolver.resolve(-85.0, 180.0)
        // fallbackItuZone(180) = floor(360/4) + 1 = 91, clamp 至 90
        assertEquals(90, east.ituZone)
    }

    @Test
    fun `resolver always returns maidenhead consistent with calculator`() {
        // 抽样若干坐标点，确认 maidenhead 字段来自 MaidenheadCalculator
        val samples = listOf(
            39.9042 to 116.4074,
            -33.8688 to 151.2093,
            0.0 to 0.0,
            -60.0 to -30.0,
            55.0 to 60.0
        )
        for ((lat, lon) in samples) {
            val expected = MaidenheadCalculator.calculate(lat, lon)
            val actual = ZoneResolver.resolve(lat, lon).maidenhead
            assertEquals("mismatch at ($lat, $lon)", expected, actual)
        }
    }
}

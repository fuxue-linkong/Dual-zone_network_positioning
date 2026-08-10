package com.example.radioarealocator.data.satellite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [TransmitterDataSource] 转发器解析（含状态字段）与
 * [RadioInfoRepository] 模式提取、[SatelliteListItem] 合并逻辑单元测试。
 *
 * org.json 在 JVM 单测中需 Robolectric 提供实现。
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TransmitterDataSourceTest {

    // ── 解析：单条转发器 ──

    @Test
    fun `parseTransmitters - parses full transmitter with status and description`() {
        val json = """
            [
              {"uuid":"u1","norad_cat_id":25544,"name":"FM Voice",
               "uplink_low":435000000,"uplink_high":435010000,"uplink_mode":"FM",
               "downlink_low":145800000,"downlink_high":145810000,"downlink_mode":"FM",
               "inverted":false,"status":"active","description":"FM Voice Repeater"}
            ]
        """.trimIndent()
        val radios = TransmitterDataSource().parseTransmitters(json)
        assertEquals(1, radios.size)
        val radio = radios[0]
        assertEquals(25544, radio.noradCatId)
        assertEquals("FM Voice", radio.name)
        assertEquals(435000000L, radio.uplinkHz)
        assertEquals(145800000L, radio.downlinkHz)
        assertEquals("FM", radio.mode)
        assertFalse(radio.inverted)
        assertEquals("active", radio.status)
        assertEquals("FM Voice Repeater", radio.description)
        assertTrue(radio.isActive)
    }

    @Test
    fun `parseTransmitters - inactive status parsed and flagged`() {
        val json = """
            [
              {"uuid":"u2","norad_cat_id":25544,"name":"APRS",
               "uplink_low":145825000,"downlink_low":145825000,"uplink_mode":"AFSK",
               "downlink_mode":"AFSK","status":"inactive"}
            ]
        """.trimIndent()
        val radio = TransmitterDataSource().parseTransmitters(json)[0]
        assertEquals("inactive", radio.status)
        assertFalse(radio.isActive)
    }

    @Test
    fun `parseTransmitters - missing status defaults to unknown`() {
        val json = """
            [
              {"uuid":"u3","norad_cat_id":25544,"name":"SSTV",
               "uplink_low":145800000,"downlink_low":145800000}
            ]
        """.trimIndent()
        val radio = TransmitterDataSource().parseTransmitters(json)[0]
        assertEquals(RadioInfo.STATUS_UNKNOWN, radio.status)
        assertFalse(radio.isActive)
    }

    @Test
    fun `parseTransmitters - missing both frequencies skipped`() {
        val json = """
            [
              {"uuid":"u4","norad_cat_id":25544,"name":"NoFreq"},
              {"uuid":"u5","norad_cat_id":25544,"name":"OK",
               "uplink_low":145800000,"downlink_low":145800000}
            ]
        """.trimIndent()
        val radios = TransmitterDataSource().parseTransmitters(json)
        assertEquals(1, radios.size)
        assertEquals("OK", radios[0].name)
    }

    // ── 解析：数组 + 多转发器/多状态 ──

    @Test
    fun `parseTransmitters - same satellite multiple transmitters with different statuses`() {
        val json = """
            [
              {"uuid":"a1","norad_cat_id":25544,"name":"FM Voice",
               "uplink_low":435000000,"downlink_low":145800000,"status":"active"},
              {"uuid":"a2","norad_cat_id":25544,"name":"APRS",
               "uplink_low":145825000,"downlink_low":145825000,"status":"inactive"},
              {"uuid":"b1","norad_cat_id":43017,"name":"FM",
               "uplink_low":435000000,"downlink_low":145880000,"status":"active"}
            ]
        """.trimIndent()
        val radios = TransmitterDataSource().parseTransmitters(json)
        assertEquals(3, radios.size)
        // 同一卫星（25544）两个转发器、状态不同
        val issRadios = radios.filter { it.noradCatId == 25544 }
        assertEquals(2, issRadios.size)
        assertEquals(setOf("active", "inactive"), issRadios.map { it.status }.toSet())
        // 卫星列表项合并：有效模式 = 活跃转发器模式 ∪ catalog 模式（ISS: FM + SSTV）
        val item = SatelliteListItem(
            tle = SourcedTLE(tle = TleElementsForTest(), source = "T"),
            pass = null,
            radios = issRadios
        )
        assertTrue("FM" in item.effectiveModes)
        assertTrue("SSTV" in item.effectiveModes)
        assertEquals(2, item.effectiveModes.size)
        assertTrue(item.hasActiveTransmitter)
    }

    // ── 模式提取 ──

    @Test
    fun `radioModes - deduplicates and skips blank modes`() {
        val radios = listOf(
            RadioInfo(25544, "a", mode = "FM", status = RadioInfo.STATUS_ACTIVE),
            RadioInfo(25544, "b", mode = "USB", status = RadioInfo.STATUS_ACTIVE),
            RadioInfo(25544, "c", mode = "FM", status = RadioInfo.STATUS_ACTIVE),
            RadioInfo(25544, "d", mode = "", status = RadioInfo.STATUS_ACTIVE),
        )
        assertEquals(listOf("FM", "USB"), RadioInfoRepository.radioModes(radios))
    }

    @Test
    fun `radioModes - includes inactive modes but activeModes filters them`() {
        val radios = listOf(
            RadioInfo(25544, "a", mode = "FM", status = RadioInfo.STATUS_ACTIVE),
            RadioInfo(25544, "b", mode = "SSTV", status = RadioInfo.STATUS_INACTIVE),
        )
        assertEquals(listOf("FM", "SSTV"), RadioInfoRepository.radioModes(radios))
        assertEquals(listOf("FM"), RadioInfoRepository.radioModes(radios).filter {
            radios.first { r -> r.mode == it }.isActive
        })
    }

    // ── SatelliteListItem ──

    @Test
    fun `listItem - modes merge radios with catalog`() {
        val item = SatelliteListItem(
            tle = SourcedTLE(tle = TleElementsForTest(), source = "T"),
            pass = null,
            radios = listOf(RadioInfo(25544, "a", mode = "FM", status = RadioInfo.STATUS_ACTIVE))
        )
        // ISS catalog modes 含 FM / SSTV
        assertTrue("FM" in item.effectiveModes)
        assertTrue("SSTV" in item.effectiveModes)
        assertEquals(2, item.effectiveModes.size)
    }

    @Test
    fun `listItem - no pass marks not visible and empty status`() {
        val item = SatelliteListItem(
            tle = SourcedTLE(tle = TleElementsForTest(), source = "T"),
            pass = null,
            radios = emptyList()
        )
        assertFalse(item.isCurrentlyVisible)
        assertEquals("", item.status)
        assertEquals(25544, item.catalogNumber)
    }

    @Test
    fun `listItem - pass fields projected`() {
        val pass = SatelliteInfo(
            name = "ISS (ZARYA)",
            catalogNumber = 25544,
            modes = listOf("FM"),
            aosTime = java.time.Instant.ofEpochMilli(1_000_000L),
            losTime = java.time.Instant.ofEpochMilli(2_000_000L),
            maxElevation = 42.0,
            aosAzimuth = 90,
            losAzimuth = 270,
            isCurrentlyVisible = true,
            status = "Heard"
        )
        val item = SatelliteListItem(
            tle = SourcedTLE(tle = TleElementsForTest(), source = "T"),
            pass = pass,
            radios = emptyList()
        )
        assertTrue(item.isCurrentlyVisible)
        assertEquals("Heard", item.status)
        assertEquals("ISS (ZARYA)", item.name)
    }
}

/** 测试用最小 TLE（仅需 catnum/name 字段） */
private fun TleElementsForTest(): com.example.radioarealocator.data.satellite.predict.TleElements =
    com.example.radioarealocator.data.satellite.predict.TleElements.fromThreeLines(
        arrayOf(
            "ISS (ZARYA)",
            "1 25544U 98067A   26220.50489838  .00004539  00000+0  89319-4 0  9991",
            "2 25544  51.6323  41.1734 0007358  25.6275 334.5077 15.49385107579869"
        )
    )

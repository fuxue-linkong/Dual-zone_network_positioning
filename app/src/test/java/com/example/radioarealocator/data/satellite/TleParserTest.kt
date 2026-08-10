package com.example.radioarealocator.data.satellite

import com.example.radioarealocator.data.satellite.predict.TleElements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TleParser] 单元测试：CelesTrak active CSV → 标准三行 TLE → 自研 [TleElements]。
 *
 * 验证依据：[TleElements] 按固定列偏移解析 line1/line2，
 * 本测试通过"CSV 字段 → 三行 TLE → TleElements 字段"的 round-trip 验证格式正确性。
 */
class TleParserTest {

    /** 模拟 CelesTrak gp.php active CSV（含表头与一行 ISS 数据，字段顺序打乱以验证按列名定位） */
    private val sampleCsv = """
        OBJECT_NAME,EPOCH,NORAD_CAT_ID,OBJECT_ID,MEAN_MOTION,ECCENTRICITY,INCLINATION,RA_OF_ASC_NODE,ARG_OF_PERICENTER,MEAN_ANOMALY,EPHEMERIS_TYPE,CLASSIFICATION_TYPE,ELEMENT_SET_NO,REV_AT_EPOCH,BSTAR,MEAN_MOTION_DOT,MEAN_MOTION_DDOT
        "ISS (ZARYA)",2026-02-01 12:29:52.488,25544,1998-067A,15.50028952,0.0006703,51.6416,247.4627,130.5360,325.0288,0,U,999,9995,0.00011606,0.00010980,0
    """.trimIndent()

    @Test
    fun `parseActiveCsv - converts CSV row into three-line TLE`() {
        val triples = TleParser.parseActiveCsv(sampleCsv)
        assertEquals(1, triples.size)

        val (name, line1, line2) = triples[0]
        assertEquals("ISS (ZARYA)", name)
        assertTrue(line1.length == 69)
        assertTrue(line2.length == 69)
        assertTrue(line1.startsWith("1 "))
        assertTrue(line2.startsWith("2 "))
    }

    @Test
    fun `parseActiveCsv - checksums are valid on generated lines`() {
        val (_, line1, line2) = TleParser.parseActiveCsv(sampleCsv)[0]
        assertEquals("line1 校验和错误", line1.last().digitToInt(), TleParser.checksum(line1.dropLast(1)))
        assertEquals("line2 校验和错误", line2.last().digitToInt(), TleParser.checksum(line2.dropLast(1)))
    }

    @Test
    fun `parseActiveCsv - TleElements fields round-trip from CSV values`() {
        val (name, line1, line2) = TleParser.parseActiveCsv(sampleCsv)[0]
        val tle = TleElements.fromThreeLines(arrayOf(name, line1, line2))

        assertEquals(25544, tle.catnum)
        assertEquals("ISS (ZARYA)", tle.name)
        assertEquals(51.6416, tle.inclinationDeg, 1e-4)
        assertEquals(247.4627, tle.raanDeg, 1e-4)
        assertEquals(0.0006703, tle.eccentricity, 1e-7)
        assertEquals(130.5360, tle.argPerigeeDeg, 1e-4)
        assertEquals(325.0288, tle.meanAnomalyDeg, 1e-4)
        assertEquals(15.50028952, tle.meanmo, 1e-8)
        // 历元：2026-02-01 = 第 32 天，12:29:52.488 → 32.520746…
        assertEquals(2026, tle.epochYear)
        assertTrue("epochDays 应约 32.52，实际 ${tle.epochDays}",
            kotlin.math.abs(tle.epochDays - 32.520746) < 1e-4)
        // B*：CSV 0.00011606 → TLE 指数格式 → 解析回 1.1606e-4
        assertEquals(0.00011606, tle.bstar, 1e-9)
        assertTrue("ISS 不应标记为深空轨道", !tle.isDeepSpace)
    }

    @Test
    fun `parseActiveCsv - header row skipped and blank lines tolerated`() {
        val csv = sampleCsv + "\n\n   \n"
        assertEquals(1, TleParser.parseActiveCsv(csv).size)
    }

    @Test
    fun `parseActiveCsv - row with missing required fields is skipped`() {
        val csv = """
            OBJECT_NAME,EPOCH,NORAD_CAT_ID,MEAN_MOTION,INCLINATION,RA_OF_ASC_NODE,ARG_OF_PERICENTER,MEAN_ANOMALY,BSTAR,MEAN_MOTION_DOT
            "BAD-EPOCH",,25544,15.5,51.6,247.5,130.5,325.0,0.0001,0.0001
            "OK",2026-02-01 12:00:00,25544,15.5,51.6,247.5,130.5,325.0,NULL,NULL
        """.trimIndent()
        val triples = TleParser.parseActiveCsv(csv)
        assertEquals("缺 EPOCH 的行应跳过，NULL 字段应容忍", 1, triples.size)
        assertEquals("OK", triples[0].first)
    }

    @Test
    fun `parseActiveCsv - quoted name containing comma is parsed`() {
        val csv = """
            OBJECT_NAME,EPOCH,NORAD_CAT_ID,MEAN_MOTION,INCLINATION,RA_OF_ASC_NODE,ARG_OF_PERICENTER,MEAN_ANOMALY
            "FOO, BAR",2026-02-01 12:00:00,99999,15.5,51.6,247.5,130.5,325.0
        """.trimIndent()
        val triples = TleParser.parseActiveCsv(csv)
        assertEquals(1, triples.size)
        assertEquals("FOO, BAR", triples[0].first)
    }

    @Test
    fun `parseActiveCsv - empty or header-only input returns empty list`() {
        assertTrue(TleParser.parseActiveCsv("").isEmpty())
        assertTrue(TleParser.parseActiveCsv("OBJECT_NAME,EPOCH,NORAD_CAT_ID\n").isEmpty())
    }

    @Test
    fun `parseEpoch - computes day of year and fraction correctly`() {
        // 2025-08-09 是 2025 年第 221 天；12:34:56.789 → 0.524268…
        val (year, refEpoch) = TleParser.parseEpoch("2025-08-09 12:34:56.789")!!
        assertEquals(25, year)
        val expected = 221.0 + (12 * 3600 + 34 * 60 + 56.789) / 86_400.0
        assertEquals(expected, refEpoch, 1e-9)
    }

    @Test
    fun `parseEpoch - rejects invalid formats`() {
        assertNull(TleParser.parseEpoch(""))
        assertNull(TleParser.parseEpoch("NULL"))
        assertNull(TleParser.parseEpoch("2025-13-01 00:00:00"))
        assertNull(TleParser.parseEpoch("not a date"))
    }

    @Test
    fun `checksum - matches real TLE checksums fetched from CelesTrak`() {
        // 真实 ISS TLE（2026 年 220 日历元，校验和已对照 CelesTrak 在线数据核实）
        val line1 = "1 25544U 98067A   26220.50489838  .00004539  00000+0  89319-4 0  9991"
        val line2 = "2 25544  51.6323  41.1734 0007358  25.6275 334.5077 15.49385107579869"
        assertEquals(1, TleParser.checksum(line1.dropLast(1)))
        assertEquals(9, TleParser.checksum(line2.dropLast(1)))
    }

    @Test
    fun `buildLine1 - drag field is plain parseable double`() {
        // 验证 drag 字段（0-based [33,43)）无需 strip 即可被 parseDouble 解析
        val (_, line1, _) = TleParser.parseActiveCsv(sampleCsv)[0]
        val dragField = line1.substring(33, 43)
        assertEquals(0.00010980, dragField.trim().toDouble(), 1e-12)
    }
}

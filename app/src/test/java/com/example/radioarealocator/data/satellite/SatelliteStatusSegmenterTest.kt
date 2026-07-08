package com.example.radioarealocator.data.satellite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * [SatelliteStatusSegmenter] 单元测试：BJT 时段划分 + 状态延续（carry-over）逻辑。
 */
class SatelliteStatusSegmenterTest {

    // ===== BJT 时段/日期划分 =====

    @Test
    fun `segmentOf maps UTC instants to correct BJT 6h segment`() {
        // 2026-07-07T16:00:00Z == 2026-07-08 00:00 BJT -> SEG_1
        assertEquals(BjtSegment.SEG_1, SatelliteStatusSegmenter.segmentOf(Instant.parse("2026-07-07T16:00:00Z")))
        // 01:00 BJT -> SEG_1
        assertEquals(BjtSegment.SEG_1, SatelliteStatusSegmenter.segmentOf(Instant.parse("2026-07-07T17:00:00Z")))
        // 07:00 BJT -> SEG_2
        assertEquals(BjtSegment.SEG_2, SatelliteStatusSegmenter.segmentOf(Instant.parse("2026-07-07T23:00:00Z")))
        // 12:00 BJT（边界）-> SEG_3
        assertEquals(BjtSegment.SEG_3, SatelliteStatusSegmenter.segmentOf(Instant.parse("2026-07-08T04:00:00Z")))
        // 18:00 BJT（边界）-> SEG_4
        assertEquals(BjtSegment.SEG_4, SatelliteStatusSegmenter.segmentOf(Instant.parse("2026-07-08T10:00:00Z")))
    }

    @Test
    fun `dateOf returns BJT date not UTC date`() {
        // 2026-07-07T17:00:00Z == 2026-07-08 01:00 BJT -> 日期为 2026-07-08
        assertEquals(
            LocalDate.of(2026, 7, 8),
            SatelliteStatusSegmenter.dateOf(Instant.parse("2026-07-07T17:00:00Z"))
        )
    }

    // ===== 延续逻辑核心用例（需求示例）=====

    @Test
    fun `empty segment carries over status from most recent previous segment`() {
        // 需求示例：
        //   00:00-06:00 BJT 时段报告 "Telemetry Only"
        //   06:00-12:00 BJT 时段无报告
        //   -> 06:00-12:00 应显示 "Telemetry Only"（延续自 00:00-06:00），而非未知
        val reference = Instant.parse("2026-07-08T03:00:00Z") // 11:00 BJT，处于 SEG_2
        val reportInSeg1 = AmsatStatusReport(
            reportedTime = Instant.parse("2026-07-07T17:30:00Z"), // 01:30 BJT -> SEG_1 of 2026-07-08
            callsign = "BG7SJU",
            report = "Telemetry Only",
            gridSquare = "OL41hx"
        )

        val timeline = SatelliteStatusSegmenter.buildSegmentTimeline(listOf(reportInSeg1), reference)
        val today = LocalDate.of(2026, 7, 8)
        val daySegments = SatelliteStatusSegmenter.segmentsForDate(timeline, today)

        assertEquals(4, daySegments.size)

        val seg1 = daySegments.first { it.segment == BjtSegment.SEG_1 }
        assertEquals("Telemetry Only", seg1.status)
        assertFalse("有报告的时段不应标记为延续", seg1.carriedOver)
        assertEquals(1, seg1.reportCount)

        val seg2 = daySegments.first { it.segment == BjtSegment.SEG_2 }
        assertEquals(
            "无报告时段应延续前一有效状态",
            "Telemetry Only",
            seg2.status
        )
        assertTrue("无报告时段应标记为延续", seg2.carriedOver)
        assertEquals(0, seg2.reportCount)

        // 后续未发生的时段同样延续
        val seg3 = daySegments.first { it.segment == BjtSegment.SEG_3 }
        assertEquals("Telemetry Only", seg3.status)
        assertTrue(seg3.carriedOver)
        val seg4 = daySegments.first { it.segment == BjtSegment.SEG_4 }
        assertEquals("Telemetry Only", seg4.status)
        assertTrue(seg4.carriedOver)
    }

    // ===== AO-123 (ASRTU-1) 场景 =====

    @Test
    fun `ao123 telemetry only carries over to subsequent empty segments`() {
        // AO-123（ASRTU-1，NORAD 61781）在 AMSAT 状态系统有 "Telemetry Only" 报告。
        // 验证：当 AO-123 某时段报告 "Telemetry Only"，后续无报告时段延续显示 "Telemetry Only"。
        val reference = Instant.parse("2026-07-08T03:00:00Z") // 11:00 BJT，SEG_2
        val reports = listOf(
            // 13:30 BJT (07-07) 的前一天报告，落在 2026-07-07 的 SEG_3
            AmsatStatusReport(Instant.parse("2026-07-07T05:30:00Z"), "XE1YDK", "Telemetry Only", "EK09lj"),
            // 01:30 BJT 当天的报告，落在 2026-07-08 的 SEG_1
            AmsatStatusReport(Instant.parse("2026-07-07T17:30:00Z"), "BG7SJU", "Telemetry Only", "OL41hx")
        )

        val timeline = SatelliteStatusSegmenter.buildSegmentTimeline(reports, reference)
        val today = LocalDate.of(2026, 7, 8)
        val todaySegs = SatelliteStatusSegmenter.segmentsForDate(timeline, today)

        val seg1 = todaySegs.first { it.segment == BjtSegment.SEG_1 }
        assertEquals("Telemetry Only", seg1.status)
        assertFalse(seg1.carriedOver)

        val seg2 = todaySegs.first { it.segment == BjtSegment.SEG_2 }
        assertEquals("AO-123 无报告时段应延续 Telemetry Only", "Telemetry Only", seg2.status)
        assertTrue(seg2.carriedOver)
    }

    // ===== 跨日延续 =====

    @Test
    fun `carry over crosses day boundary when next day starts with empty segment`() {
        // 2026-07-07 (BJT) SEG_4 报告 "Not Heard"；2026-07-08 各时段均无报告。
        val reference = Instant.parse("2026-07-07T17:00:00Z") // 2026-07-08 01:00 BJT，SEG_1
        val reports = listOf(
            // 19:00 BJT (07-07) -> 07-07 SEG_4
            AmsatStatusReport(Instant.parse("2026-07-07T11:00:00Z"), "K5OLV", "Not Heard", "EM54bw")
        )

        val timeline = SatelliteStatusSegmenter.buildSegmentTimeline(reports, reference)

        val day1 = LocalDate.of(2026, 7, 7)
        val day2 = LocalDate.of(2026, 7, 8)
        val day1Segs = SatelliteStatusSegmenter.segmentsForDate(timeline, day1)
        val day2Segs = SatelliteStatusSegmenter.segmentsForDate(timeline, day2)

        // 07-07 SEG_1..3 无前序数据 -> 保持 null
        assertNull(day1Segs.first { it.segment == BjtSegment.SEG_1 }.status)
        assertNull(day1Segs.first { it.segment == BjtSegment.SEG_3 }.status)
        // 07-07 SEG_4 有报告
        val day1Seg4 = day1Segs.first { it.segment == BjtSegment.SEG_4 }
        assertEquals("Not Heard", day1Seg4.status)
        assertFalse(day1Seg4.carriedOver)
        // 07-08 SEG_1 无报告 -> 跨日延续自 07-07 SEG_4
        val day2Seg1 = day2Segs.first { it.segment == BjtSegment.SEG_1 }
        assertEquals("Not Heard", day2Seg1.status)
        assertTrue("跨日延续应标记 carriedOver", day2Seg1.carriedOver)
    }

    // ===== 无数据保持未知 =====

    @Test
    fun `no reports yields all null statuses without carry over`() {
        val reference = Instant.parse("2026-07-08T03:00:00Z")
        val timeline = SatelliteStatusSegmenter.buildSegmentTimeline(emptyList(), reference)
        val today = LocalDate.of(2026, 7, 8)
        val segs = SatelliteStatusSegmenter.segmentsForDate(timeline, today)

        assertEquals(4, segs.size)
        segs.forEach { seg ->
            assertNull(seg.status)
            assertFalse(seg.carriedOver)
            assertEquals(0, seg.reportCount)
        }
    }

    // ===== 同时段多报告归约 =====

    @Test
    fun `reduce picks majority status within a segment`() {
        val reference = Instant.parse("2026-07-08T03:00:00Z")
        val reports = listOf(
            AmsatStatusReport(Instant.parse("2026-07-07T17:00:00Z"), "A", "Telemetry Only", ""), // SEG_1
            AmsatStatusReport(Instant.parse("2026-07-07T18:00:00Z"), "B", "Telemetry Only", ""), // SEG_1
            AmsatStatusReport(Instant.parse("2026-07-07T19:00:00Z"), "C", "Not Heard", "")       // SEG_1
        )
        val timeline = SatelliteStatusSegmenter.buildSegmentTimeline(reports, reference)
        val seg1 = timeline.first {
            it.date == LocalDate.of(2026, 7, 8) && it.segment == BjtSegment.SEG_1
        }
        assertEquals("Telemetry Only", seg1.status)
        assertEquals(3, seg1.reportCount)
    }

    @Test
    fun `reduce breaks ties by status priority`() {
        // Heard(1) 与 Telemetry Only(1) 平票 -> 优先级 Heard > Telemetry Only
        val reference = Instant.parse("2026-07-08T03:00:00Z")
        val reports = listOf(
            AmsatStatusReport(Instant.parse("2026-07-07T17:00:00Z"), "A", "Heard", ""),
            AmsatStatusReport(Instant.parse("2026-07-07T18:00:00Z"), "B", "Telemetry Only", "")
        )
        val timeline = SatelliteStatusSegmenter.buildSegmentTimeline(reports, reference)
        val seg1 = timeline.first {
            it.date == LocalDate.of(2026, 7, 8) && it.segment == BjtSegment.SEG_1
        }
        assertEquals("Heard", seg1.status)
    }

    // ===== applyCarryOver 直接测试 =====

    @Test
    fun `applyCarryOver sorts segments chronologically before filling`() {
        // 故意乱序输入
        val date = LocalDate.of(2026, 7, 8)
        val unsorted = listOf(
            SegmentStatus(date, BjtSegment.SEG_3, null, false, 0),
            SegmentStatus(date, BjtSegment.SEG_1, "Heard", false, 1),
            SegmentStatus(date, BjtSegment.SEG_2, null, false, 0),
            SegmentStatus(date, BjtSegment.SEG_4, null, false, 0)
        )
        val result = SatelliteStatusSegmenter.applyCarryOver(unsorted)
        assertEquals("Heard", result[0].status) // SEG_1
        assertFalse(result[0].carriedOver)
        assertEquals("Heard", result[1].status) // SEG_2 延续
        assertTrue(result[1].carriedOver)
        assertEquals("Heard", result[2].status) // SEG_3 延续
        assertTrue(result[2].carriedOver)
        assertEquals("Heard", result[3].status) // SEG_4 延续
        assertTrue(result[3].carriedOver)
    }

    @Test
    fun `applyCarryOver leaves leading empty segments null`() {
        val date = LocalDate.of(2026, 7, 8)
        val segments = listOf(
            SegmentStatus(date, BjtSegment.SEG_1, null, false, 0),
            SegmentStatus(date, BjtSegment.SEG_2, null, false, 0),
            SegmentStatus(date, BjtSegment.SEG_3, "Heard", false, 1),
            SegmentStatus(date, BjtSegment.SEG_4, null, false, 0)
        )
        val result = SatelliteStatusSegmenter.applyCarryOver(segments)
        assertNull(result[0].status) // 无前序 -> 保持未知
        assertFalse(result[0].carriedOver)
        assertNull(result[1].status)
        assertEquals("Heard", result[2].status)
        assertFalse(result[2].carriedOver)
        assertEquals("Heard", result[3].status)
        assertTrue(result[3].carriedOver)
    }
}

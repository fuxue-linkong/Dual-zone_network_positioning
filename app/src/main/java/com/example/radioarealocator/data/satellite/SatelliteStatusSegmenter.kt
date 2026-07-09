package com.example.radioarealocator.data.satellite

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 北京时间（BJT, UTC+8）一天内的四个 6 小时时段。
 *
 * 声明顺序即时间顺序，[values] 按该顺序遍历，便于按下标索引。
 *
 * - SEG_1：00:00 BJT - 06:00 BJT
 * - SEG_2：06:00 BJT - 12:00 BJT
 * - SEG_3：12:00 BJT - 18:00 BJT
 * - SEG_4：18:00 BJT - 24:00 BJT
 */
enum class BjtSegment(val startHour: Int, val endHour: Int, val label: String) {
    SEG_1(0, 6, "00:00-06:00"),
    SEG_2(6, 12, "06:00-12:00"),
    SEG_3(12, 18, "12:00-18:00"),
    SEG_4(18, 24, "18:00-24:00");

    companion object {
        /** 按 BJT 小时（0-23）返回所属时段。 */
        fun ofHour(bjtHour: Int): BjtSegment {
            val idx = (bjtHour / 6).coerceIn(0, 3)
            return values()[idx]
        }
    }
}

/**
 * 单条 AMSAT 状态报告（来自 www.amsat.org/status 的 sat_info.php）。
 *
 * [report] 为 API 原始状态文本，如 "Heard" / "Telemetry Only" / "Not Heard" / "Crew Active"。
 */
data class AmsatStatusReport(
    val reportedTime: Instant,
    val callsign: String,
    val report: String,
    val gridSquare: String
)

/**
 * 某卫星在某 BJT 时段的状态（可能已应用延续逻辑）。
 *
 * [status] 为 null 表示该时段无任何报告且无可延续的前序状态（真正未知）。
 * [carriedOver] 为 true 表示该状态由前序时段延续而来，而非本时段直接报告。
 */
data class SegmentStatus(
    val date: LocalDate,
    val segment: BjtSegment,
    val status: String?,
    val carriedOver: Boolean,
    val reportCount: Int
)

/**
 * 卫星运行状态的时段化与延续（carry-over）逻辑。
 *
 * 将 AMSAT 状态报告按北京时间划分为每日 4 个 6 小时时段；
 * 对无报告的时段，自动延续最近一个有有效报告的时段的状态。
 *
 * 示例：若 00:00-06:00 BJT 时段报告 "Telemetry Only"，而 06:00-12:00 BJT 时段无报告，
 * 则 06:00-12:00 时段显示 "Telemetry Only"（延续自前一时段），而非"未知"。
 */
object SatelliteStatusSegmenter {

    /** 北京时间时区。 */
    val ZONE_BJT: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 同一时段内多条报告归约时的状态优先级（高 -> 低），仅在票数相同时用于定序。 */
    private val STATUS_PRIORITY = listOf("Heard", "Crew Active", "Telemetry Only", "Not Heard")

    /** 时刻所在 BJT 时段。 */
    fun segmentOf(instant: Instant): BjtSegment =
        BjtSegment.ofHour(instant.atZone(ZONE_BJT).hour)

    /** 时刻所在 BJT 日期。 */
    fun dateOf(instant: Instant): LocalDate =
        instant.atZone(ZONE_BJT).toLocalDate()

    /**
     * 将原始报告聚合成完整的时段网格（含空时段），并应用延续逻辑。
     *
     * 时段范围从最早报告日期（不晚于 [reference] 前一天）到最晚报告日期（不早于 [reference]），
     * 保证 [reference] 当天的 4 个时段始终存在，从而能从前一天延续状态。
     */
    fun buildSegmentTimeline(
        reports: List<AmsatStatusReport>,
        reference: Instant = Instant.now()
    ): List<SegmentStatus> {
        val today = dateOf(reference)
        val reportDates = reports.map { dateOf(it.reportedTime) }
        val earliest = reportDates.minOrNull() ?: today
        val latest = reportDates.maxOrNull() ?: today
        val start = if (earliest.isAfter(today)) today.minusDays(1) else earliest
        val end = if (latest.isBefore(today)) today else latest

        val byKey: Map<Pair<LocalDate, BjtSegment>, List<AmsatStatusReport>> =
            reports.groupBy { Pair(dateOf(it.reportedTime), segmentOf(it.reportedTime)) }

        val grid = mutableListOf<SegmentStatus>()
        var d = start
        while (!d.isAfter(end)) {
            for (seg in BjtSegment.values()) {
                val reps = byKey[Pair(d, seg)].orEmpty()
                grid.add(SegmentStatus(d, seg, reduce(reps), carriedOver = false, reps.size))
            }
            d = d.plusDays(1)
        }
        return applyCarryOver(grid)
    }

    /**
     * 将同一时段内的多条报告归约成单一状态。
     *
     * - 无报告 -> null
     * - 取报告数量最多的状态；票数相同时按 [STATUS_PRIORITY] 优先级定序。
     */
    private fun reduce(reports: List<AmsatStatusReport>): String? {
        if (reports.isEmpty()) return null
        val counts = reports.groupingBy { it.report }.eachCount()
        val maxCount = counts.values.max()
        val tied = counts.filter { it.value == maxCount }.keys
        return tied.minByOrNull { status ->
            val idx = STATUS_PRIORITY.indexOf(status)
            if (idx < 0) Int.MAX_VALUE else idx
        }
    }

    /**
     * 延续逻辑：按时间顺序遍历，无报告的时段沿用最近一个有效状态。
     *
     * 输入无需预先排序，内部按（日期, 时段序号）排序后处理。
     */
    fun applyCarryOver(segments: List<SegmentStatus>): List<SegmentStatus> {
        val sorted = segments.sortedWith(compareBy({ it.date }, { it.segment.ordinal }))
        var lastValid: String? = null
        return sorted.map { seg ->
            if (seg.status != null) {
                lastValid = seg.status
                seg
            } else {
                val carry = lastValid
                if (carry != null) seg.copy(status = carry, carriedOver = true) else seg
            }
        }
    }

    /** 取某一天的 4 个时段（已延续）。若该天不存在则返回空列表。 */
    fun segmentsForDate(timeline: List<SegmentStatus>, date: LocalDate): List<SegmentStatus> =
        timeline.filter { it.date == date }.sortedBy { it.segment.ordinal }
}

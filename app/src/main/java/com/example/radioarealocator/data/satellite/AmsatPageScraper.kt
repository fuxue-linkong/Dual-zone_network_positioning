package com.example.radioarealocator.data.satellite

import com.example.radioarealocator.data.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * AMSAT 卫星状态网页抓取器。
 *
 * 从 `https://www.amsat.org/status/` 网页解析卫星状态数据，
 * 提取最近 15 分钟时间槽内的报告，确保数据时效性。
 *
 * 网页表格结构：
 * - 第一列：卫星名称（含链接）
 * - 后续列：每 15 分钟一个时间槽的状态码
 *   - 1 = Satellite Active
 *   - 2 = Telemetry/Beacon only
 *   - 3 = No signal
 *   - 4 = Conflicting reports
 *   - _ = ISS Crew (Voice) Active
 *
 * 数据仅保留最近 15 分钟内的时间槽报告，过期数据自动丢弃。
 */
class AmsatPageScraper {

    private val client = HttpClientProvider.client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 抓取 AMSAT 状态页面并解析最近 15 分钟内的卫星状态报告。
     *
     * @return 最近 15 分钟时间槽内的卫星状态报告列表
     */
    suspend fun fetchRecentReports(): List<SatelliteStatusReport> = withContext(Dispatchers.IO) {
        val html = fetchPage()
        val allRows = parseTableRows(html)
        val timeSlots = parseTimeSlots(html)

        if (allRows.isEmpty() || timeSlots.isEmpty()) {
            return@withContext emptyList()
        }

        // 找到最近 15 分钟时间槽对应的列索引
        val recentSlotIndex = findRecentSlotIndex(timeSlots) ?: return@withContext emptyList()

        // 提取该时间槽内的卫星状态
        val reports = mutableListOf<SatelliteStatusReport>()
        val now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)

        for (row in allRows) {
            if (recentSlotIndex < row.statusCodes.size) {
                val statusCode = row.statusCodes[recentSlotIndex]
                if (statusCode.isNotEmpty()) {
                    val status = mapStatusCode(statusCode)
                    if (status != null) {
                        reports.add(
                            SatelliteStatusReport(
                                name = row.satelliteName,
                                status = status,
                                reportTime = now
                            )
                        )
                    }
                }
            }
        }

        reports
    }

    /**
     * 抓取 AMSAT 状态页面 HTML 内容。
     */
    private fun fetchPage(): String {
        val request = Request.Builder()
            .url(PAGE_URL)
            .header("User-Agent", "RadioAreaLocator/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("AMSAT 页面请求失败：${response.code}")
            }
            return response.body?.string() ?: throw java.io.IOException("AMSAT 页面响应为空")
        }
    }

    /**
     * 解析 HTML 表格中的卫星行数据。
     *
     * 每行包含卫星名称和对应时间槽的状态码列表。
     */
    private fun parseTableRows(html: String): List<SatelliteRow> {
        val rows = mutableListOf<SatelliteRow>()

        // 匹配表格行：提取卫星名称和状态码
        val rowPattern = Regex(
            """<tr[^>]*>\s*<td[^>]*>\s*<a[^>]*>([^<]+)</a>\s*</td>(.*?)</tr>""",
            RegexOption.DOT_MATCHES_ALL
        )

        val cellPattern = Regex("""<td[^>]*>(.*?)</td>""", RegexOption.DOT_MATCHES_ALL)

        for (match in rowPattern.findAll(html)) {
            val satelliteName = match.groupValues[1].trim()
            val cellsHtml = match.groupValues[2]

            val statusCodes = mutableListOf<String>()
            for (cellMatch in cellPattern.findAll(cellsHtml)) {
                val cellContent = cellMatch.groupValues[1].trim()
                statusCodes.add(cellContent)
            }

            if (satelliteName.isNotEmpty()) {
                rows.add(SatelliteRow(satelliteName, statusCodes))
            }
        }

        return rows
    }

    /**
     * 解析表头中的时间槽信息。
     *
     * 表头结构：日期行 + 时间槽行
     * 日期行：<th colspan="N">May 20</th>
     * 时间槽行：<th>00:00</th><th>00:15</th>...
     */
    private fun parseTimeSlots(html: String): List<TimeSlotInfo> {
        val slots = mutableListOf<TimeSlotInfo>()

        // 提取表头行中的日期
        val datePattern = Regex("""<th[^>]*colspan="(\d+)"[^>]*>(\w+ \d+)</th>""")
        val dates = mutableListOf<Pair<String, Int>>() // dateStr, colspan

        for (match in datePattern.findAll(html)) {
            val colspan = match.groupValues[1].toIntOrNull() ?: 0
            val dateStr = match.groupValues[2].trim()
            if (colspan > 0 && dateStr.isNotEmpty()) {
                dates.add(dateStr to colspan)
            }
        }

        // 提取时间槽行
        val timePattern = Regex("""<th[^>]*>(\d{2}:\d{2})</th>""")
        val times = timePattern.findAll(html).map { it.groupValues[1] }.toList()

        // 构建时间槽信息
        var timeIndex = 0
        for ((dateStr, colspan) in dates) {
            val date = parseDate(dateStr) ?: continue
            val slotsPerDate = colspan

            for (i in 0 until slotsPerDate) {
                if (timeIndex < times.size) {
                    val time = times[timeIndex]
                    slots.add(TimeSlotInfo(date, time, timeIndex))
                    timeIndex++
                }
            }
        }

        return slots
    }

    /**
     * 找到最近 15 分钟时间槽对应的列索引。
     *
     * 计算当前时间所属的 15 分钟时间槽，然后在表头中找到对应的列。
     */
    private fun findRecentSlotIndex(slots: List<TimeSlotInfo>): Int? {
        val now = Instant.now()
        val utcNow = now.atZone(ZoneOffset.UTC)
        val currentDate = utcNow.toLocalDate()
        val currentHour = utcNow.hour
        val currentMinute = utcNow.minute

        // 计算当前时间所属的 15 分钟时间槽
        val currentSlotMinute = (currentMinute / 15) * 15
        val currentSlotTime = String.format("%02d:%02d", currentHour, currentSlotMinute)

        // 在表头中查找匹配的时间槽
        for (slot in slots.reversed()) { // 从最新时间槽开始查找
            if (slot.date == currentDate && slot.time == currentSlotTime) {
                return slot.columnIndex
            }
        }

        // 如果找不到精确匹配，找最近的过去时间槽
        for (slot in slots.reversed()) {
            if (slot.date == currentDate) {
                val slotHour = slot.time.substring(0, 2).toIntOrNull() ?: continue
                val slotMinute = slot.time.substring(3, 5).toIntOrNull() ?: continue
                val slotTotalMinutes = slotHour * 60 + slotMinute
                val currentTotalMinutes = currentHour * 60 + currentMinute

                // 只选择过去的时间槽（不超过 15 分钟）
                if (currentTotalMinutes - slotTotalMinutes in 0..14) {
                    return slot.columnIndex
                }
            }
        }

        return null
    }

    /**
     * 解析日期字符串（如 "May 20"）为 LocalDate。
     */
    private fun parseDate(dateStr: String): LocalDate? {
        return try {
            val formatter = DateTimeFormatter.ofPattern("MMM d")
            val parsed = java.time.MonthDay.parse(dateStr, formatter)
            val year = LocalDate.now().year
            parsed.atYear(year)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将状态码映射为状态字符串。
     *
     * - 1 = Satellite Active → "Heard"
     * - 2 = Telemetry/Beacon only → "Telemetry Only"
     * - 3 = No signal → "Not Heard"
     * - 4 = Conflicting reports → "Conflicting"
     * - _ = ISS Crew (Voice) Active → "Crew Active"
     */
    private fun mapStatusCode(code: String): String? {
        return when (code) {
            "1" -> "Heard"
            "2" -> "Telemetry Only"
            "3" -> "Not Heard"
            "4" -> "Conflicting"
            "_" -> "Crew Active"
            else -> null
        }
    }

    /**
     * 卫星行数据：卫星名称和对应时间槽的状态码列表。
     */
    private data class SatelliteRow(
        val satelliteName: String,
        val statusCodes: List<String>
    )

    /**
     * 时间槽信息：日期、时间和列索引。
     */
    private data class TimeSlotInfo(
        val date: LocalDate,
        val time: String,
        val columnIndex: Int
    )

    companion object {
        private const val PAGE_URL = "https://www.amsat.org/status/"
    }
}
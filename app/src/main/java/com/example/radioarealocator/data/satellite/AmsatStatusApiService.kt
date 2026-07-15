package com.example.radioarealocator.data.satellite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * 单条卫星状态报告（summary 聚合），含报告时间戳（UTC，精确到分钟）。
 *
 * 用于客户端 15 分钟时间槽延续算法：[reportTime] 标记该状态被确认的时间，
 * 供 [SatelliteStatusTracker] 计算时间槽与状态延续。
 */
data class SatelliteStatusReport(
    val name: String,
    val status: String,
    val reportTime: Instant
)

/**
 * AMSAT Satellite Status API 服务。
 *
 * API 文档：https://www.amsat.org/status/api/
 *
 * 提供两类接口：
 * 1. [fetchStatusSummaries] / [fetchStatusSummaryReports]：summary.php 聚合接口，
 *    返回每颗卫星一条代表状态，供 [SatelliteStatusTracker] 做 15 分钟时间槽延续
 * 2. [fetchStatusReports]：sat_info.php 逐条报告接口，返回带时间戳的原始报告列表，
 *    供 [SatelliteStatusSegmenter] 按 BJT 时段聚合与延续使用
 */
class AmsatStatusApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 获取过去 [hours] 小时内所有卫星的状态报告列表（summary 聚合）。
     *
     * 返回每颗卫星一条 [SatelliteStatusReport]，包含卫星名称、状态值与报告时间戳。
     *
     * 说明：AMSAT summary API 为实时聚合接口，按 report_count 取最多的一条作为代表，
     * 不暴露单条提交时间。此处以抓取时刻（UTC，截断到分钟）作为该状态的确认时间，
     * 用于客户端 15 分钟时间槽延续算法。
     */
    suspend fun fetchStatusSummaryReports(hours: Int = 24): List<SatelliteStatusReport> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/summary.php?hours=$hours")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("AMSAT status 请求失败：${response.code}")
            }

            val body = response.body?.string() ?: throw IOException("AMSAT status 响应为空")
            parseSummaryReports(body)
        }
    }

    /**
     * 获取过去 [hours] 小时内所有卫星的状态摘要。
     *
     * 返回按卫星名称（API 原始 name，例如 "AO-91_[FM]"）到状态的映射。
     * 同一卫星可能出现多条记录，取报告数量最多的一条作为代表。
     *
     * 保留以兼容 [SatelliteDataSource]：TLE 拉取时附加初始状态。
     * 内部委托给 [fetchStatusSummaryReports]。
     */
    suspend fun fetchStatusSummaries(hours: Int = 24): Map<String, String> {
        return fetchStatusSummaryReports(hours).associate { it.name to it.status }
    }

    /**
     * 获取指定卫星的逐条状态报告（来自 sat_info.php）。
     *
     * 与 [fetchStatusSummaries] 不同，本方法返回带时间戳的原始报告列表，
     * 供 [SatelliteStatusSegmenter] 按 BJT 时段聚合与延续使用。
     *
     * @param satName AMSAT 卫星名称（如 "AO-123_[FM]"），内部会做 URL 编码。
     */
    suspend fun fetchStatusReports(satName: String): List<AmsatStatusReport> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(satName, "UTF-8")
        val request = Request.Builder()
            .url("$BASE_URL/sat_info.php?name=$encoded")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("AMSAT sat_info 请求失败：${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("AMSAT sat_info 响应为空")
            parseReports(body)
        }
    }

    /**
     * 解析 sat_info.php 返回的 JSON 数组，提取逐条报告。
     */
    private fun parseReports(json: String): List<AmsatStatusReport> {
        // sat_info.php 直接返回 JSON 数组（非 { "data": [...] } 包装）
        val arr = JSONArray(json)
        val result = mutableListOf<AmsatStatusReport>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val timeStr = item.optString("reported_time", "").trim()
            if (timeStr.isEmpty()) continue
            val time = try {
                Instant.parse(timeStr)
            } catch (_: Exception) {
                continue
            }
            val report = item.optString("report", "").trim()
            if (report.isEmpty()) continue
            result.add(
                AmsatStatusReport(
                    reportedTime = time,
                    callsign = item.optString("callsign", "").trim(),
                    report = report,
                    gridSquare = item.optString("grid_square", "").trim()
                )
            )
        }
        return result
    }

    /**
     * 解析 summary.php 返回的 JSON，按卫星聚合为代表状态。
     *
     * 同一 name 可能出现多条记录（不同状态），按 report_count 取最多的一条作为代表。
     * 报告时间戳以抓取时刻（UTC，截断到分钟）近似。
     */
    private fun parseSummaryReports(json: String): List<SatelliteStatusReport> {
        // 抓取时刻（UTC，截断到分钟）作为本轮所有状态记录的确认时间
        val reportTime = Instant.now().truncatedTo(ChronoUnit.MINUTES)
        val root = JSONObject(json)
        val data = root.optJSONArray("data") ?: return emptyList()

        // 同一 name 可能出现多条记录（不同状态），按 report_count 取最多的
        val bestStatus = mutableMapOf<String, Pair<String, Int>>()

        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val name = item.optString("name", "").trim()
            val report = item.optString("report", "").trim()
            val count = item.optInt("report_count", 0)
            if (name.isEmpty() || report.isEmpty()) continue

            val current = bestStatus[name]
            if (current == null || count > current.second) {
                bestStatus[name] = report to count
            }
        }

        return bestStatus.map { (name, pair) ->
            SatelliteStatusReport(name, pair.first, reportTime)
        }
    }

    companion object {
        private const val BASE_URL = "https://www.amsat.org/status/api/v1"
    }
}

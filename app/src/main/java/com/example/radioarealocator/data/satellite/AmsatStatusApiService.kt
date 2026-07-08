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
import java.util.concurrent.TimeUnit

/**
 * AMSAT Satellite Status API 服务。
 *
 * API 文档：https://www.amsat.org/status/api/
 *
 * 使用该 API 获取业余卫星的当前状态报告（如 Heard / Telemetry Only / Not Heard）。
 */
class AmsatStatusApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 获取过去 [hours] 小时内所有卫星的状态摘要。
     *
     * 返回按卫星名称（API 原始 name，例如 "AO-91_[FM]"）到状态的映射。
     * 同一卫星可能出现多条记录，取报告数量最多的一条作为代表。
     */
    suspend fun fetchStatusSummaries(hours: Int = 24): Map<String, String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/summary.php?hours=$hours")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("AMSAT status 请求失败：${response.code}")
            }

            val body = response.body?.string() ?: throw IOException("AMSAT status 响应为空")
            parseSummary(body)
        }
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

    private fun parseSummary(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val root = JSONObject(json)
        val data = root.optJSONArray("data") ?: return result

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

        bestStatus.forEach { (name, pair) ->
            result[name] = pair.first
        }
        return result
    }

    companion object {
        private const val BASE_URL = "https://www.amsat.org/status/api/v1"
    }
}

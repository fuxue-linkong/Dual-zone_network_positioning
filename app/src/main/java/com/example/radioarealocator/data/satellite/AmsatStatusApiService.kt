package com.example.radioarealocator.data.satellite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * 单条卫星状态报告，含报告时间戳（UTC，精确到分钟）。
 *
 * 用于客户端时间槽延续算法：[reportTime] 标记该状态被确认的时间，
 * [slotIndex] 为该时间所属的 15 分钟时间槽索引（0-95）。
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
 * 使用该 API 获取业余卫星的当前状态报告（如 Heard / Telemetry Only / Not Heard）。
 */
class AmsatStatusApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 获取过去 [hours] 小时内所有卫星的状态报告列表。
     *
     * 返回每颗卫星一条 [SatelliteStatusReport]，包含卫星名称、状态值与报告时间戳。
     *
     * 说明：AMSAT summary API 为实时聚合接口，按 report_count 取最多的一条作为代表，
     * 不暴露单条提交时间。此处以抓取时刻（UTC，截断到分钟）作为该状态的确认时间，
     * 用于客户端 15 分钟时间槽延续算法。
     */
    suspend fun fetchStatusReports(hours: Int = 24): List<SatelliteStatusReport> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/summary.php?hours=$hours")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("AMSAT status 请求失败：${response.code}")
            }

            val body = response.body?.string() ?: throw IOException("AMSAT status 响应为空")
            parseReports(body)
        }
    }

    private fun parseReports(json: String): List<SatelliteStatusReport> {
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

    /**
     * 获取过去 [hours] 小时内所有卫星的状态摘要。
     *
     * 返回按卫星名称（API 原始 name，例如 "AO-91_[FM]"）到状态的映射。
     * 同一卫星可能出现多条记录，取报告数量最多的一条作为代表。
     *
     * 保留以兼容 [SatelliteDataSource]：TLE 拉取时附加初始状态。
     * 内部委托给 [fetchStatusReports]。
     */
    suspend fun fetchStatusSummaries(hours: Int = 24): Map<String, String> {
        return fetchStatusReports(hours).associate { it.name to it.status }
    }

    companion object {
        private const val BASE_URL = "https://www.amsat.org/status/api/v1"
    }
}

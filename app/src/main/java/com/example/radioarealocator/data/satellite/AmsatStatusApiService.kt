package com.example.radioarealocator.data.satellite

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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

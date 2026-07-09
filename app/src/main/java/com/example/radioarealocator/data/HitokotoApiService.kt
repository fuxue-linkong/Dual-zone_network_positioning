package com.example.radioarealocator.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 一言数据。
 *
 * @param content 一言正文
 * @param from 来源（作品名），可能为空
 */
data class HitokotoQuote(
    val content: String,
    val from: String
) {
    /**
     * 展示文本：正文 + 来源（若来源非空则追加 " —— 来源"）。
     */
    fun toDisplayText(): String =
        if (from.isBlank()) content else "$content —— $from"
}

/**
 * Hitokoto（一言）API 服务。
 *
 * API 文档：https://developer.hitokoto.cn/
 *
 * 数据源：https://v1.hitokoto.cn/
 *
 * 返回随机一条一言，包含正文与来源（作品名）。网络失败时返回 null，
 * 由调用方回退到本地兜底文案池。
 */
class HitokotoApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 获取一条一言。失败（网络错误、响应非法、正文为空）时返回 null。
     */
    suspend fun fetchQuote(): HitokotoQuote? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(BASE_URL)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseQuote(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQuote(json: String): HitokotoQuote? {
        val root = JSONObject(json)
        val content = root.optString("hitokoto", "").trim()
        if (content.isEmpty()) return null
        val from = root.optString("from", "").trim()
        return HitokotoQuote(content, from)
    }

    companion object {
        private const val BASE_URL = "https://v1.hitokoto.cn/"
    }
}

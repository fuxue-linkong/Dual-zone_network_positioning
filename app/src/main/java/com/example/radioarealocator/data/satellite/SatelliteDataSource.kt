package com.example.radioarealocator.data.satellite

import com.github.amsacode.predict4java.TLE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import com.example.radioarealocator.data.network.HttpClientProvider
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 协程安全的 [runCatching]：捕获异常时重新抛出 [CancellationException]，
 * 避免破坏 Kotlin 协程的结构化并发语义。
 */
private suspend inline fun <R> runCatchingCancellable(block: suspend () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/**
 * 带 TLE 数据来源标记的包装类。
 */
data class SourcedTLE(
    val tle: TLE,
    val source: String, // "SNOGS" / "AMSAT" / "ALL"
    val status: String = "", // AMSAT 状态：Heard / Telemetry Only / Not Heard / Crew Active
    val rawLines: Array<String> = arrayOf("", "", "") // 原始三行 TLE，用于本地缓存序列化
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SourcedTLE) return false
        return tle == other.tle && source == other.source && status == other.status &&
            rawLines.contentEquals(other.rawLines)
    }

    override fun hashCode(): Int {
        var result = tle.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + rawLines.contentHashCode()
        return result
    }
}

/**
 * 卫星 TLE 数据源，同时从 SatNOGS 和 AMSAT 获取并合并去重。
 */
class SatelliteDataSource {

    // 基于共享单例派生：共享连接池/线程池，仅覆盖本服务的超时配置
    private val client = HttpClientProvider.client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val amsatStatusApi = AmsatStatusApiService()

    /**
     * 获取业余卫星 TLE 列表。
     * 同时从 SatNOGS 和 AMSAT 拉取，合并去重（按 NORAD 编号）。
     * 同时出现在两个源的卫星标记为 ALL。
     * 任一源失败时使用另一个源的结果。
     *
     * @param source 数据来源过滤："ALL" 全部, "SNOGS" 仅 SatNOGS, "AMSAT" 仅 AMSAT
     */
    suspend fun fetchAmateurTLEs(source: String = "ALL"): List<SourcedTLE> = withContext(Dispatchers.IO) {
        coroutineScope {
            // 根据用户设置跳过不需要的数据源，减少等待时间
            val needSatnogs = source != "AMSAT"
            val needAmsat = source != "SNOGS"

            val satnogsDeferred = if (needSatnogs) async { runCatchingCancellable { fetchSatnogsTLEs() } } else null
            val amsatStatusDeferred = if (needAmsat) async { runCatchingCancellable { amsatStatusApi.fetchStatusSummaries() } } else null

            val satnogsResult = satnogsDeferred?.await()
            val amsatStatusResult = amsatStatusDeferred?.await()

            // 只有请求了的源才参与失败判断：单一数据源失败时也应抛异常，
            // 避免返回空列表覆盖本地缓存
            val requestedResults = listOfNotNull(
                if (needSatnogs) satnogsResult else null,
                if (needAmsat) amsatStatusResult else null
            )
            if (requestedResults.isNotEmpty() && requestedResults.all { it.isFailure }) {
                throw IOException(
                    "TLE 下载失败：SatNOGS=${satnogsResult?.exceptionOrNull()?.message}, " +
                        "AMSAT=${amsatStatusResult?.exceptionOrNull()?.message}"
                )
            }

            // 按 NORAD 编号合并，记录来源
            val merged = LinkedHashMap<Int, SourcedTLE>()
            satnogsResult?.getOrNull()?.forEach { stle ->
                merged[stle.tle.catnum] = stle
            }

            // 按用户选择的来源过滤
            val filtered = when (source) {
                "SNOGS" -> merged.values.filter { it.source == "SNOGS" || it.source == "ALL" }
                    .map { it.copy(source = "SNOGS") }
                "AMSAT" -> merged.values.filter { it.source == "AMSAT" || it.source == "ALL" }
                    .map { it.copy(source = "AMSAT") }
                else -> merged.values.toList()
            }

            // 附加 AMSAT 状态（失败时不影响 TLE 结果）
            val statusMap = amsatStatusResult?.getOrNull() ?: emptyMap()
            filtered.map { sourcedTle ->
                val amsatName = SatelliteCatalog.AMSAT_STATUS_NAME_BY_CATALOG_NUMBER[sourcedTle.tle.catnum]
                val status = if (amsatName != null) statusMap[amsatName] ?: "" else ""
                sourcedTle.copy(status = status)
            }
        }
    }

    /**
     * 从 SatNOGS 批量获取 TLE，然后过滤出 SatelliteCatalog 中关心的卫星。
     * 单次请求可拿到全部 TLE，避免逐颗查询的大量网络往返。
     *
     * 优化：SatNOGS 返回全量 TLE（数千颗），但只关心 catalog 中的几十颗。
     * 使用 HashSet 进行 O(1) 过滤，并优先判断 norad_cat_id 是否命中目标集合，
     * 命中后才解析 tle0/tle1/tle2，避免对无关记录做字符串读取与对象构造。
     */
    private fun fetchSatnogsTLEs(): List<SourcedTLE> {
        val request = Request.Builder()
            .url(SATNOGS_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("SatNOGS 请求失败：${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("SatNOGS 响应为空")
            val array = JSONArray(body)
            // 用 HashSet 加速 contains 查询；目标卫星数量少，构建成本可忽略
            val catalogNumbers = SatelliteCatalog.catalogNumbers.toHashSet()
            val tles = mutableListOf<SourcedTLE>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val noradCatId = obj.optInt("norad_cat_id", -1)
                if (noradCatId < 0 || !catalogNumbers.contains(noradCatId)) continue

                val tle1 = obj.optString("tle1", "")
                val tle2 = obj.optString("tle2", "")
                if (tle1.isBlank() || tle2.isBlank()) continue
                val tle0 = obj.optString("tle0", "")

                try {
                    tles.add(
                        SourcedTLE(
                            tle = TLE(arrayOf(tle0, tle1, tle2)),
                            source = "SNOGS",
                            rawLines = arrayOf(tle0, tle1, tle2)
                        )
                    )
                } catch (_: IllegalArgumentException) {
                    // 跳过解析失败的 TLE
                }
            }
            if (tles.isEmpty()) {
                return emptyList()
            }
            return tles
        }
    }

    /**
     * 解析标准三行文本格式 TLE，返回 (name, line1, line2) 三元组列表。
     */
    private fun parseTextTLEs(text: String): List<Triple<String, String, String>> {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<Triple<String, String, String>>()
        var i = 0
        while (i < lines.size) {
            val isNameLine = !lines[i].startsWith("1 ") && !lines[i].startsWith("2 ")
            val name = if (isNameLine) lines[i] else ""
            val line1Index = if (isNameLine) i + 1 else i
            val line2Index = line1Index + 1

            if (line2Index >= lines.size) break

            val line1 = lines[line1Index]
            val line2 = lines[line2Index]

            if (line1.startsWith("1 ") && line2.startsWith("2 ")) {
                result.add(Triple(name, line1, line2))
            }

            i = line2Index + 1
        }
        return result
    }

    companion object {
        private const val SATNOGS_URL = "https://db.satnogs.org/api/tle/"
    }
}

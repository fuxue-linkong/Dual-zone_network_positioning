package com.example.radioarealocator.data.satellite

import com.github.amsacode.predict4java.TLE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
    val source: String, // "CT" / "SNOGS" / "ALL"
    val status: String = "" // AMSAT 状态：Heard / Telemetry Only / Not Heard / Crew Active
)

/**
 * 卫星 TLE 数据源，同时从 CelesTrak 和 SatNOGS 获取并合并去重。
 */
class SatelliteDataSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val amsatStatusApi = AmsatStatusApiService()

    // 限制 SatNOGS 并发请求数，避免同时打开过多连接导致网络阻塞
    private val satnogsSemaphore = Semaphore(8)

    /**
     * 获取业余卫星 TLE 列表。
     * 同时从 CelesTrak 和 SatNOGS 拉取，合并去重（按 NORAD 编号）。
     * 同时出现在两个源的卫星标记为 ALL。
     * 任一源失败时使用另一个源的结果。
     *
     * 还会从 AMSAT Satellite Status API 获取每颗卫星的当前状态并附加到结果中。
     *
     * @param source 数据来源过滤："ALL" 全部, "CT" 仅 CelesTrak, "SNOGS" 仅 SatNOGS
     */
    suspend fun fetchAmateurTLEs(source: String = "ALL"): List<SourcedTLE> = withContext(Dispatchers.IO) {
        coroutineScope {
            val celestrakDeferred = async { runCatching { fetchCelestrakTLEs() } }
            val satnogsDeferred = async { runCatchingCancellable { fetchSatnogsTLEs() } }
            val amsatStatusDeferred = async { runCatchingCancellable { amsatStatusApi.fetchStatusSummaries() } }

            val celestrakResult = celestrakDeferred.await()
            val satnogsResult = satnogsDeferred.await()
            val amsatStatusResult = amsatStatusDeferred.await()

            if (celestrakResult.isFailure && satnogsResult.isFailure) {
                throw IOException(
                    "TLE 下载失败：CelesTrak=${celestrakResult.exceptionOrNull()?.message}, " +
                        "SatNOGS=${satnogsResult.exceptionOrNull()?.message}"
                )
            }

            // 按 NORAD 编号合并，记录来源
            val merged = LinkedHashMap<Int, SourcedTLE>()
            celestrakResult.getOrNull()?.forEach { (tle) ->
                merged[tle.catnum] = SourcedTLE(tle, "CT")
            }
            satnogsResult.getOrNull()?.forEach { (tle) ->
                val existing = merged[tle.catnum]
                if (existing == null) {
                    merged[tle.catnum] = SourcedTLE(tle, "SNOGS")
                } else {
                    // 两个源都有，标记为 ALL
                    merged[tle.catnum] = SourcedTLE(existing.tle, "ALL")
                }
            }

            // 按用户选择的来源过滤
            val filtered = when (source) {
                "CT" -> merged.values.filter { it.source == "CT" || it.source == "ALL" }
                    .map { SourcedTLE(it.tle, "CT") }
                "SNOGS" -> merged.values.filter { it.source == "SNOGS" || it.source == "ALL" }
                    .map { SourcedTLE(it.tle, "SNOGS") }
                else -> merged.values.toList()
            }

            // 附加 AMSAT 状态（失败时不影响 TLE 结果）
            val statusMap = amsatStatusResult.getOrNull() ?: emptyMap()
            filtered.map { sourcedTle ->
                val amsatName = SatelliteCatalog.AMSAT_STATUS_NAME_BY_CATALOG_NUMBER[sourcedTle.tle.catnum]
                val status = if (amsatName != null) statusMap[amsatName] ?: "" else ""
                sourcedTle.copy(status = status)
            }
        }
    }

    /**
     * 从 CelesTrak 获取业余卫星 TLE（标准三行文本格式）。
     */
    private fun fetchCelestrakTLEs(): List<SourcedTLE> {
        val request = Request.Builder()
            .url(CELESTRAK_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("CelesTrak 请求失败：${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("CelesTrak 响应为空")
            return parseTextTLEs(body).map { SourcedTLE(it, "CT") }
        }
    }

    /**
     * 从 SatNOGS 获取我们关心的卫星 TLE（JSON 格式）。
     * 只查询 SatelliteCatalog 中的卫星，避免下载全部数据。
     * 使用信号量限制并发数，防止同时发起过多请求导致卡顿或失败。
     */
    private suspend fun fetchSatnogsTLEs(): List<SourcedTLE> = withContext(Dispatchers.IO) {
        coroutineScope {
            // 并行查询每颗卫星，但限制并发数
            val deferreds = SatelliteCatalog.catalogNumbers.map { noradId ->
                async {
                    runCatchingCancellable {
                        satnogsSemaphore.withPermit {
                            fetchSingleSatnogsTLE(noradId)
                        }
                    }
                }
            }
            val results = deferreds.map { it.await() }

            val tles = mutableListOf<SourcedTLE>()
            for (result in results) {
                result.getOrNull()?.let { tles.add(SourcedTLE(it, "SNOGS")) }
            }

            if (tles.isEmpty()) {
                val firstError = results.firstOrNull { it.isFailure }?.exceptionOrNull()
                throw IOException("SatNOGS 查询失败：${firstError?.message ?: "无数据"}")
            }
            tles
        }
    }

    /**
     * 从 SatNOGS 查询单颗卫星的 TLE。
     */
    private fun fetchSingleSatnogsTLE(noradCatId: Int): TLE {
        val request = Request.Builder()
            .url("$SATNOGS_URL?norad_cat_id=$noradCatId&format=json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("SatNOGS 请求失败：${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("SatNOGS 响应为空")
            val array = JSONArray(body)
            if (array.length() == 0) {
                throw IOException("SatNOGS 无 NORAD $noradCatId 的数据")
            }
            val obj = array.optJSONObject(0)
                ?: throw IOException("SatNOGS 响应格式异常")

            val tle0 = obj.optString("tle0", "")
            val tle1 = obj.optString("tle1", "")
            val tle2 = obj.optString("tle2", "")
            if (tle1.isBlank() || tle2.isBlank()) {
                throw IOException("SatNOGS TLE 数据不完整")
            }
            return TLE(arrayOf(tle0, tle1, tle2))
        }
    }

    /**
     * 解析标准三行文本格式 TLE。
     */
    private fun parseTextTLEs(text: String): List<TLE> {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val result = mutableListOf<TLE>()
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
                try {
                    result.add(TLE(arrayOf(name, line1, line2)))
                } catch (_: IllegalArgumentException) {
                    // 跳过解析失败的 TLE
                }
            }

            i = line2Index + 1
        }
        return result
    }

    companion object {
        private const val CELESTRAK_URL = "https://celestrak.org/NORAD/elements/amateur.txt"
        private const val SATNOGS_URL = "https://db.satnogs.org/api/tle/"
    }
}

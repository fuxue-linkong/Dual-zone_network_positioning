package com.example.radioarealocator.data.satellite

import com.github.amsacode.predict4java.TLE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * 卫星 TLE 数据源，同时从 CelesTrak 和 SatNOGS 获取并合并去重。
 */
class SatelliteDataSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val amsatStatusApi = AmsatStatusApiService()

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
            // 根据用户设置跳过不需要的数据源，减少等待时间
            val needCelestrak = source != "SNOGS"
            val needSatnogs = source != "CT"

            val celestrakDeferred = if (needCelestrak) async { runCatchingCancellable { fetchCelestrakTLEs() } } else null
            val satnogsDeferred = if (needSatnogs) async { runCatchingCancellable { fetchSatnogsTLEs() } } else null
            val amsatStatusDeferred = async { runCatchingCancellable { amsatStatusApi.fetchStatusSummaries() } }

            val celestrakResult = celestrakDeferred?.await()
            val satnogsResult = satnogsDeferred?.await()
            val amsatStatusResult = amsatStatusDeferred.await()

            if (celestrakResult?.isFailure == true && satnogsResult?.isFailure == true) {
                throw IOException(
                    "TLE 下载失败：CelesTrak=${celestrakResult.exceptionOrNull()?.message}, " +
                        "SatNOGS=${satnogsResult.exceptionOrNull()?.message}"
                )
            }

            // 按 NORAD 编号合并，记录来源
            val merged = LinkedHashMap<Int, SourcedTLE>()
            celestrakResult?.getOrNull()?.forEach { stle ->
                merged[stle.tle.catnum] = stle
            }
            satnogsResult?.getOrNull()?.forEach { stle ->
                val existing = merged[stle.tle.catnum]
                if (existing == null) {
                    merged[stle.tle.catnum] = stle
                } else {
                    // 两个源都有，标记为 ALL
                    merged[stle.tle.catnum] = SourcedTLE(
                        tle = existing.tle,
                        source = "ALL",
                        status = existing.status,
                        rawLines = existing.rawLines
                    )
                }
            }

            // 按用户选择的来源过滤
            val filtered = when (source) {
                "CT" -> merged.values.filter { it.source == "CT" || it.source == "ALL" }
                    .map { it.copy(source = "CT") }
                "SNOGS" -> merged.values.filter { it.source == "SNOGS" || it.source == "ALL" }
                    .map { it.copy(source = "SNOGS") }
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
            return parseTextTLEs(body).map { (name, line1, line2) ->
                SourcedTLE(
                    tle = TLE(arrayOf(name, line1, line2)),
                    source = "CT",
                    rawLines = arrayOf(name, line1, line2)
                )
            }
        }
    }

    /**
     * 从 SatNOGS 批量获取 TLE，然后过滤出 SatelliteCatalog 中关心的卫星。
     * 单次请求可拿到全部 TLE，避免逐颗查询的大量网络往返。
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
            val catalogNumbers = SatelliteCatalog.catalogNumbers
            val tles = mutableListOf<SourcedTLE>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val noradCatId = obj.optInt("norad_cat_id", -1)
                if (!catalogNumbers.contains(noradCatId)) continue

                val tle0 = obj.optString("tle0", "")
                val tle1 = obj.optString("tle1", "")
                val tle2 = obj.optString("tle2", "")
                if (tle1.isBlank() || tle2.isBlank()) continue

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
                throw IOException("SatNOGS 无我们关心的卫星数据")
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
        private const val CELESTRAK_URL = "https://celestrak.org/NORAD/elements/amateur.txt"
        private const val SATNOGS_URL = "https://db.satnogs.org/api/tle/"
    }
}

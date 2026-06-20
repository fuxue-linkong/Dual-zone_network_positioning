package com.example.radioarealocator.data.satellite

import com.github.amsacode.predict4java.TLE
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
 * 带 TLE 数据来源标记的包装类。
 */
data class SourcedTLE(
    val tle: TLE,
    val source: String // "CT" 或 "SNOGS"
)

/**
 * 卫星 TLE 数据源，同时从 CelesTrak 和 SatNOGS 获取并合并去重。
 */
class SatelliteDataSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 获取业余卫星 TLE 列表。
     * 同时从 CelesTrak 和 SatNOGS 拉取，合并去重（按 NORAD 编号）。
     * 同时出现在两个源的卫星标记为 ALL。
     * 任一源失败时使用另一个源的结果。
     */
    suspend fun fetchAmateurTLEs(): List<SourcedTLE> = withContext(Dispatchers.IO) {
        coroutineScope {
            val celestrakDeferred = async { runCatching { fetchCelestrakTLEs() } }
            val satnogsDeferred = async { runCatching { fetchSatnogsTLEs() } }

            val celestrakResult = celestrakDeferred.await()
            val satnogsResult = satnogsDeferred.await()

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

            merged.values.toList()
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
     */
    private suspend fun fetchSatnogsTLEs(): List<SourcedTLE> = withContext(Dispatchers.IO) {
        coroutineScope {
            // 并行查询每颗卫星
            val deferreds = SatelliteCatalog.catalogNumbers.map { noradId ->
                async { runCatching { fetchSingleSatnogsTLE(noradId) } }
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

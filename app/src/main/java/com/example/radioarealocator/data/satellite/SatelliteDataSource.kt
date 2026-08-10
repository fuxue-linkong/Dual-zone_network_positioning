package com.example.radioarealocator.data.satellite

import android.util.Log
import com.example.radioarealocator.data.satellite.predict.TleElements
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import com.example.radioarealocator.data.network.HttpClientProvider
import okhttp3.Request
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
    val tle: TleElements,
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
 * 卫星 TLE 数据源，同时从 SatNOGS 和 CelesTrak 获取并合并去重，
 * 再附加 AMSAT 状态报告。返回全部业余卫星（不做 catalog 过滤），
 * 不在 SatelliteCatalog 中的卫星 modes 为空（UI 显示"未知"）。
 */
class SatelliteDataSource {

    // 基于共享单例派生：共享连接池/线程池，仅覆盖本服务的超时配置
    private val client = HttpClientProvider.client.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val amsatStatusApi = AmsatStatusApiService()

    /**
     * 获取业余卫星 TLE 列表（返回数据源提供的全部业余卫星，不做 catalog 过滤）。
     *
     * 数据源（Phase 3 扩展，均可独立开关，任一失败仅记录日志不影响其余源）：
     * 1. CelesTrak amateur 分组（[enableAmateur]，3le 文本）
     * 2. CelesTrak satnogs 分组（[enableSatnogs]，3le 文本）
     * 3. CelesTrak active 分组（[enableActive]，CSV，含全部活跃卫星）
     * 4. 自定义 URL（[customUrl]，自动识别 3le 或 CSV 格式）
     * 5. ISS/ARISS 单星源（恒定开启，补全 ISS 25544）
     *
     * 按 NORAD 编号合并去重：现有源（amateur/satnogs）优先，active/自定义源
     * 只补全不在列表中的卫星；同时出现在 amateur 与 satnogs 的卫星标记为 ALL。
     * 任一 TLE 源失败时容忍；仅当所有启用的 TLE 源都失败时才抛 IOException，
     * 避免返回空列表覆盖本地缓存。AMSAT 状态源失败不影响 TLE 结果。
     *
     * @param enableAmateur 是否启用 CelesTrak amateur 分组（默认 true）
     * @param enableSatnogs 是否启用 CelesTrak satnogs 分组（默认 true）
     * @param enableActive 是否启用 CelesTrak active 分组（默认 false，全量拉取较大）
     * @param customUrl 可选自定义 TLE URL（3le 或 CSV，默认 null）
     */
    suspend fun fetchAmateurTLEs(
        enableAmateur: Boolean = true,
        enableSatnogs: Boolean = true,
        enableActive: Boolean = false,
        customUrl: String? = null,
    ): List<SourcedTLE> = withContext(Dispatchers.IO) {
        coroutineScope {
            val tleDeferreds = mutableListOf<kotlinx.coroutines.Deferred<Result<List<SourcedTLE>>>>()
            if (enableSatnogs) {
                tleDeferreds += async { runCatchingCancellable { fetchSatnogsTLEs() } }
            }
            if (enableAmateur) {
                tleDeferreds += async { runCatchingCancellable { fetchCelesTrakTLEs() } }
            }
            if (enableActive) {
                tleDeferreds += async { runCatchingCancellable { fetchActiveTLEs() } }
            }
            if (!customUrl.isNullOrBlank()) {
                tleDeferreds += async { runCatchingCancellable { fetchCustomTLEs(customUrl) } }
            }
            // ISS/ARISS 单星源：恒定补全，失败不影响结果
            val issDeferred = async { runCatchingCancellable { fetchIssTLE() } }
            // AMSAT 状态与 TLE 源正交，始终附加状态标签
            val amsatStatusDeferred = async { runCatchingCancellable { amsatStatusApi.fetchStatusSummaries() } }

            val tleResults = tleDeferreds.awaitAll()
            val issResult = issDeferred.await()
            val amsatStatusResult = amsatStatusDeferred.await()

            // 所有启用的 TLE 源都失败时抛异常，避免返回空列表覆盖本地缓存。
            // AMSAT 状态失败不在此判定内。
            if (tleResults.isEmpty()) {
                throw IOException("未启用任何 TLE 数据源，无法拉取卫星数据")
            }
            if (tleResults.all { it.isFailure }) {
                throw IOException(
                    "TLE 下载失败：" + tleResults.mapIndexed { i, r ->
                        "源$i=${r.exceptionOrNull()?.message}"
                    }.joinToString(", ")
                )
            }
            // 单个源失败：记录日志后继续（保持"全失败才抛异常"的容错语义）
            tleResults.forEachIndexed { i, r ->
                if (r.isFailure) {
                    Log.w(TAG, "TLE 源 $i 拉取失败，已跳过", r.exceptionOrNull())
                }
            }

            // 合并顺序即优先级：amateur/satnogs 在前（含 ALL 标记），
            // active/custom 仅补全缺失的 NORAD 号；ISS 单星源最后兜底。
            val merged = LinkedHashMap<Int, SourcedTLE>()
            // 先合并 satnogs（若启用），再合并 amateur（若启用），标记 ALL
            if (enableSatnogs) {
                tleResults[0].getOrNull()?.forEach { stle ->
                    merged[stle.tle.catnum] = stle
                }
            }
            var amateurIdx = if (enableSatnogs) 1 else 0
            if (enableAmateur) {
                tleResults[amateurIdx].getOrNull()?.forEach { stle ->
                    val existing = merged[stle.tle.catnum]
                    merged[stle.tle.catnum] = if (existing != null && existing.source != stle.source) {
                        existing.copy(source = "ALL")
                    } else {
                        stle
                    }
                }
                amateurIdx++
            }
            // 其余源（active / custom）：只补全不在列表中的卫星
            for (i in amateurIdx until tleResults.size) {
                tleResults[i].getOrNull()?.forEach { stle ->
                    if (!merged.containsKey(stle.tle.catnum)) {
                        merged[stle.tle.catnum] = stle
                    }
                }
            }
            // ISS 单星源兜底
            issResult.getOrNull()?.forEach { stle ->
                if (!merged.containsKey(stle.tle.catnum)) {
                    merged[stle.tle.catnum] = stle
                }
            }

            // 附加 AMSAT 状态（失败时不影响 TLE 结果）
            val statusMap = amsatStatusResult.getOrNull() ?: emptyMap()
            merged.values.toList().map { sourcedTle ->
                val amsatName = SatelliteCatalog.AMSAT_STATUS_NAME_BY_CATALOG_NUMBER[sourcedTle.tle.catnum]
                val status = if (amsatName != null) statusMap[amsatName] ?: "" else ""
                sourcedTle.copy(status = status)
            }
        }
    }

    /**
     * 获取 SatNOGS 维护的卫星 TLE（参考 Look4Sat 实现）。
     *
     * 不直接请求 db.satnogs.org DB API（国内访问不稳定），而是请求 CelesTrak 整理好的
     * `satnogs` 分组（3le 文本格式）。CelesTrak 已过滤失效/再入卫星，无需再做 alive 过滤。
     *
     * 实测约 649 颗，含 SatNOGS 跟踪的全部可观测卫星；不在 SatelliteCatalog 中的
     * 卫星 modes 为空列表（UI 显示"未知"），AMSAT 状态为空字符串。
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
            val triples = parseThreeLineTLEs(body)
            val tles = mutableListOf<SourcedTLE>()
            for ((tle0, tle1, tle2) in triples) {
                // NORAD 编号位于 line1 的第 3-7 列（0-based 索引 2..6）
                if (tle1.length < 7) continue
                val noradCatId = tle1.substring(2, 7).trim().toIntOrNull() ?: continue
                if (noradCatId <= 0) continue

                try {
                    tles.add(
                        SourcedTLE(
                            tle = TleElements.fromThreeLines(arrayOf(tle0, tle1, tle2)),
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
     * 从 CelesTrak 批量获取业余卫星 TLE，返回全部（不做 catalog 过滤）。
     *
     * 使用 gp.php 3le 文本接口（GROUP=amateur&FORMAT=3le），返回标准三行 TLE：
     * 第一行卫星名称，后两行为 TLE line1/line2，可直接喂给自研
     * [com.example.radioarealocator.data.satellite.predict.TleElements] 解析器。
     * 不在 SatelliteCatalog 中的卫星 modes 为空列表（UI 显示"未知"）。
     *
     * NORAD 编号从 line1 第 3-7 列解析（标准 TLE 格式），避免依赖名称行匹配。
     */
    private fun fetchCelesTrakTLEs(): List<SourcedTLE> {
        val request = Request.Builder()
            .url(CELESTRAK_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("CelesTrak 请求失败：${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("CelesTrak 响应为空")
            val tles = mutableListOf<SourcedTLE>()

            val triples = parseThreeLineTLEs(body)
            for ((tle0, tle1, tle2) in triples) {
                // NORAD 编号位于 line1 的第 3-7 列（0-based 索引 2..6）
                if (tle1.length < 7) continue
                val noradCatId = tle1.substring(2, 7).trim().toIntOrNull() ?: continue

                try {
                    tles.add(
                        SourcedTLE(
                            tle = TleElements.fromThreeLines(arrayOf(tle0, tle1, tle2)),
                            source = "CT",
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
     * 适用于 CelesTrak 3le 格式：每三行为一组，依次是名称行、line1、line2。
     */
    private fun parseThreeLineTLEs(text: String): List<Triple<String, String, String>> {
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

    /**
     * 从 CelesTrak 拉取全部活跃卫星（GROUP=active，CSV 格式）。
     *
     * 包含所有在轨活跃卫星（含 ISS、GEO 通信星等非业余卫星），
     * 由 [TleParser] 将 CSV 行转换为标准三行 TLE。
     * 来源标记为 "ACTIVE"；合并时仅在 amateur/satnogs 缺失的 NORAD 号上补全。
     */
    private fun fetchActiveTLEs(): List<SourcedTLE> {
        val request = Request.Builder()
            .url(ACTIVE_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("CelesTrak active 请求失败：${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("CelesTrak active 响应为空")
            val tles = mutableListOf<SourcedTLE>()
            for ((name, line1, line2) in TleParser.parseActiveCsv(body)) {
                val noradCatId = line1.substring(2, 7).trim().toIntOrNull() ?: continue
                if (noradCatId <= 0) continue
                try {
                    tles.add(
                        SourcedTLE(
                            tle = TleElements.fromThreeLines(arrayOf(name, line1, line2)),
                            source = "ACTIVE",
                            rawLines = arrayOf(name, line1, line2)
                        )
                    )
                } catch (_: IllegalArgumentException) {
                    // 跳过解析失败的 TLE
                }
            }
            return tles
        }
    }

    /**
     * 拉取 ISS / ARISS 单星 TLE（CATNR=25544，3le 格式）。
     * 用于兜底补全 ISS，保证业余中继（ARISS）始终在列表中。
     * 来源标记为 "ARISS"。
     */
    private fun fetchIssTLE(): List<SourcedTLE> {
        val request = Request.Builder()
            .url(ISS_URL)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("ISS TLE 请求失败：${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("ISS TLE 响应为空")
            val tles = mutableListOf<SourcedTLE>()
            for ((tle0, tle1, tle2) in parseThreeLineTLEs(body)) {
                val noradCatId = tle1.substring(2, 7).trim().toIntOrNull() ?: continue
                if (noradCatId <= 0) continue
                try {
                    tles.add(
                        SourcedTLE(
                            tle = TleElements.fromThreeLines(arrayOf(tle0, tle1, tle2)),
                            source = "ARISS",
                            rawLines = arrayOf(tle0, tle1, tle2)
                        )
                    )
                } catch (_: IllegalArgumentException) {
                    // 跳过解析失败的 TLE
                }
            }
            return tles
        }
    }

    /**
     * 从自定义 URL 拉取 TLE。自动识别格式：
     * - 内容含 CelesTrak CSV 表头（OBJECT_NAME）→ [TleParser.parseActiveCsv]
     * - 否则按标准三行文本（3le）解析
     * 来源标记为 "CUSTOM"。
     */
    private fun fetchCustomTLEs(url: String): List<SourcedTLE> {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("自定义 TLE 请求失败：${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("自定义 TLE 响应为空")
            val tles = mutableListOf<SourcedTLE>()

            if (body.contains("OBJECT_NAME")) {
                for ((name, line1, line2) in TleParser.parseActiveCsv(body)) {
                    val noradCatId = line1.substring(2, 7).trim().toIntOrNull() ?: continue
                    if (noradCatId <= 0) continue
                    try {
                        tles.add(
                            SourcedTLE(
                                tle = TleElements.fromThreeLines(arrayOf(name, line1, line2)),
                                source = "CUSTOM",
                                rawLines = arrayOf(name, line1, line2)
                            )
                        )
                    } catch (_: IllegalArgumentException) {
                        // 跳过解析失败的 TLE
                    }
                }
            } else {
                for ((tle0, tle1, tle2) in parseThreeLineTLEs(body)) {
                    val noradCatId = tle1.substring(2, 7).trim().toIntOrNull() ?: continue
                    if (noradCatId <= 0) continue
                    try {
                        tles.add(
                            SourcedTLE(
                                tle = TleElements.fromThreeLines(arrayOf(tle0, tle1, tle2)),
                                source = "CUSTOM",
                                rawLines = arrayOf(tle0, tle1, tle2)
                            )
                        )
                    } catch (_: IllegalArgumentException) {
                        // 跳过解析失败的 TLE
                    }
                }
            }
            return tles
        }
    }

    companion object {
        private const val TAG = "SatelliteDataSource"

        // SatNOGS 源改用 CelesTrak 整理的 satnogs 分组（3le 文本格式），
        // 避免直接请求 db.satnogs.org（国内访问不稳定），CelesTrak 有 CDN 且已过滤失效卫星
        private const val SATNOGS_URL =
            "https://celestrak.org/NORAD/elements/gp.php?GROUP=satnogs&FORMAT=3le"
        private const val CELESTRAK_URL =
            "https://celestrak.org/NORAD/elements/gp.php?GROUP=amateur&FORMAT=3le"
        // CelesTrak 全部活跃卫星（CSV 格式，Phase 3 新增）
        private const val ACTIVE_URL =
            "https://celestrak.org/NORAD/elements/gp.php?GROUP=active&FORMAT=csv"
        // ISS / ARISS 单星源（Phase 3 新增）
        private const val ISS_URL =
            "https://celestrak.org/NORAD/elements/gp.php?CATNR=25544&FORMAT=3le"
    }
}

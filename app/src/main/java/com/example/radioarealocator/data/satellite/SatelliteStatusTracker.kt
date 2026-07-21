package com.example.radioarealocator.data.satellite

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * 卫星状态条目：存储某卫星最近一次有报告时的状态及其所属时间槽。
 *
 * [absoluteSlot] 为从 epoch 起的绝对 15 分钟时间槽序号（epochSecond / 900），
 * 用于跨天正确计算延续差值；语义上等价于"一天 96 个 15 分钟时间槽"。
 */
@Immutable
data class SatelliteStatusEntry(
    val status: String,
    val reportTime: Instant,
    val absoluteSlot: Long
)

/**
 * 卫星状态查询结果：包含状态值与是否为"延续自上一时段"的标记。
 */
@Immutable
data class SatelliteStatusQuery(
    val status: String,
    val reportTime: Instant,
    val isInherited: Boolean
)

/**
 * 卫星状态持续显示跟踪器。
 *
 * 核心机制：
 * 1. 每 5 分钟从 AMSAT API 和网页同时抓取卫星状态报告；
 * 2. 网页数据源提供精确的 15 分钟时间槽数据，确保时效性；
 * 3. 以卫星名称为键存储最近一次有报告时间槽的状态（卫星状态字典）；
 * 4. 收到新报告时更新对应卫星从当前时间槽开始的状态记录；
 * 5. 查询时严格筛选仅返回最近 15 分钟内的报告（[queryRecentStatus]）；
 * 6. 超过 15 分钟无报告视为无数据，不再延续显示过期状态。
 *
 * 网页数据源（[AmsatPageScraper]）解析 `https://www.amsat.org/status/` 页面，
 * 提取当前 15 分钟时间槽内有报告的卫星状态，数据更精确、时效性更强。
 */
class SatelliteStatusTracker(
    private val apiService: AmsatStatusApiService,
    private val pageScraper: AmsatPageScraper,
    private val scope: CoroutineScope
) {

    // 卫星状态字典：卫星名称 -> 最近有效状态条目
    private val _statusMap = mutableStateOf<Map<String, SatelliteStatusEntry>>(emptyMap())
    val statusMap: State<Map<String, SatelliteStatusEntry>> = _statusMap

    private var refreshJob: Job? = null

    // 串行化 refresh：防止定时循环与 refreshOnce 并发执行时丢失更新（lost update）
    private val refreshMutex = Mutex()

    /**
     * 启动 5 分钟定时抓取。首次立即执行一次，之后按 [REFRESH_INTERVAL_MS] 循环。
     * 重复调用安全（已在运行时直接返回）。
     */
    @Synchronized
    fun start() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            refresh()
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                refresh()
            }
        }
    }

    /**
     * 停止定时抓取。
     */
    @Synchronized
    fun stop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /**
     * 立即触发一次抓取（不影响定时循环）。可用于用户手动刷新。
     */
    suspend fun refreshOnce() {
        refresh()
    }

    private suspend fun refresh() {
        try {
            // 优先从网页抓取最近 15 分钟时间槽的数据（更精确）
            val pageReports = try {
                pageScraper.fetchRecentReports()
            } catch (_: Exception) {
                emptyList()
            }

            // 同时从 API 获取数据作为补充
            val apiReports = apiService.fetchStatusSummaryReports()

            val currentSlot = absoluteSlot()
            // 合并策略：网页数据优先，API 数据补充
            refreshMutex.withLock {
                val merged = _statusMap.value.toMutableMap()

                // 先用 API 数据更新
                apiReports.forEach { report ->
                    merged[report.name] = SatelliteStatusEntry(
                        status = report.status,
                        reportTime = report.reportTime,
                        absoluteSlot = currentSlot
                    )
                }

                // 网页数据覆盖 API 数据（更精确的15分钟时间槽）
                pageReports.forEach { report ->
                    merged[report.name] = SatelliteStatusEntry(
                        status = report.status,
                        reportTime = report.reportTime,
                        absoluteSlot = currentSlot
                    )
                }

                _statusMap.value = merged
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 网络等异常静默失败，保留旧状态字典供延续显示
        }
    }

    /**
     * 查询某卫星当前状态（含延续逻辑）。
     *
     * - 返回 null：超过 24 小时无任何报告，视为无数据
     * - [SatelliteStatusQuery.isInherited] = false：当前时间槽有实时报告
     * - [SatelliteStatusQuery.isInherited] = true：沿用最近一个有报告时间槽的状态
     */
    fun queryStatus(name: String): SatelliteStatusQuery? {
        val entry = _statusMap.value[name] ?: return null
        val currentSlot = absoluteSlot()
        val diff = currentSlot - entry.absoluteSlot
        // 时钟回退（异常）：视为当前槽，避免误判延续
        if (diff < 0) {
            return SatelliteStatusQuery(entry.status, entry.reportTime, isInherited = false)
        }
        // 超过 24 小时（96 槽）无报告：视为无数据
        if (diff >= SLOTS_PER_DAY) return null
        // diff == 0：当前槽有报告（实时）；diff > 0：延续自最近有报告的槽
        return SatelliteStatusQuery(
            status = entry.status,
            reportTime = entry.reportTime,
            isInherited = diff > 0
        )
    }

    /**
     * 严格查询最近 15 分钟内的卫星状态。
     *
     * 仅返回当前 15 分钟时间槽内有报告的卫星状态，过期数据不返回。
     * 用于确保展示的卫星报告均为最近 15 分钟内生成的内容。
     *
     * - 返回 null：当前时间槽内无该卫星的报告
     * - 返回非 null：当前时间槽内有实时报告
     */
    fun queryRecentStatus(name: String): SatelliteStatusQuery? {
        val entry = _statusMap.value[name] ?: return null
        val currentSlot = absoluteSlot()
        val diff = currentSlot - entry.absoluteSlot
        // 仅返回当前时间槽（diff == 0）的报告
        if (diff != 0L) return null
        return SatelliteStatusQuery(
            status = entry.status,
            reportTime = entry.reportTime,
            isInherited = false
        )
    }

    companion object {
        /** 抓取间隔：5 分钟，确保状态时效性 */
        private const val REFRESH_INTERVAL_MS = 5L * 60 * 1000
        /** 一天 96 个 15 分钟时间槽 */
        private const val SLOTS_PER_DAY = 96
        /** 单个时间槽时长（秒）：15 分钟 */
        private const val SLOT_SECONDS = 900L

        /**
         * 计算 [now] 所属的绝对 15 分钟时间槽序号（从 epoch 起）。
         * 语义等价于当天 0-95 的槽索引，但使用绝对值可正确处理跨天延续。
         */
        fun absoluteSlot(now: Instant = Instant.now()): Long {
            return now.epochSecond / SLOT_SECONDS
        }
    }
}

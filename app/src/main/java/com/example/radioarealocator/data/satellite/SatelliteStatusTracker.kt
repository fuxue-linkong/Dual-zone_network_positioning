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
import java.time.Instant

/**
 * 卫星状态条目：存储某卫星最近一次有报告时的状态及其所属时间槽。
 *
 * [absoluteSlot] 为从 epoch 起的绝对 15 分钟时间槽序号（epochSecond / 900），
 * 用于跨天正确计算延续差值；语义上等价于"一天 96 个 15 分钟时间槽"。
 */
data class SatelliteStatusEntry(
    val status: String,
    val reportTime: Instant,
    val absoluteSlot: Long
)

/**
 * 卫星状态查询结果：包含状态值与是否为"延续自上一时段"的标记。
 */
data class SatelliteStatusQuery(
    val status: String,
    val reportTime: Instant,
    val isInherited: Boolean
)

/**
 * 卫星状态持续显示跟踪器。
 *
 * 核心机制：
 * 1. 每 5 分钟从 AMSAT 抓取一次卫星状态报告（含 UTC 分钟级时间戳）；
 * 2. 以卫星名称为键存储最近一次有报告时间槽的状态（卫星状态字典）；
 * 3. 收到新报告时更新对应卫星从当前时间槽开始的状态记录；
 * 4. 查询时若当前时间槽无该卫星新报告，自动向前追溯最近有效状态槽，
 *    沿用其状态值并标记 [SatelliteStatusQuery.isInherited] = true；
 * 5. 超过 24 小时（96 槽）无报告视为无数据。
 *
 * 这样界面永远不会因"当前槽无新报告"而显示"无数据"，而是延续显示最近状态，
 * 并通过 [SatelliteStatusQuery.isInherited] 提供视觉区分依据。
 */
class SatelliteStatusTracker(
    private val apiService: AmsatStatusApiService,
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
            val reports = apiService.fetchStatusSummaryReports()
            val currentSlot = absoluteSlot()
            // 合并策略：保留未在新报告中出现的旧卫星（沿用其状态），
            // 新报告中出现的卫星覆盖为当前槽的实时状态
            refreshMutex.withLock {
                val merged = _statusMap.value.toMutableMap()
                reports.forEach { report ->
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

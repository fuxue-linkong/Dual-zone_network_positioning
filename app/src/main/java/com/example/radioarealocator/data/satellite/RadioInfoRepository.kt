package com.example.radioarealocator.data.satellite

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException

/**
 * 转发器频率仓库：封装"内存缓存 → Room 数据库 → 网络"三级读取链。
 *
 * - [getRadiosForSatellite]：先查内存缓存，未命中查 Room；本地无数据时
 *   触发一次网络刷新（静默失败，失败后仍返回空列表），供 UI 使用。
 * - [getAllRadios]：返回全量转发器映射（NORAD → 转发器列表），
 *   供卫星列表一次取用（避免逐颗查询）。
 * - [refresh]：拉取 SatNOGS 全部转发器（含 active/inactive）并写入 Room + 内存缓存。
 *   失败仅记录日志（android.util.Log），不向上抛异常，保证离线可用。
 * - 缓存 TTL 7 天；未过期时 [refresh] 直接跳过（幂等）。
 */
class RadioInfoRepository(context: Context) {

    private val dao = TransmitterDatabase.getDatabase(context).radioInfoDao()
    private val dataSource = TransmitterDataSource()

    @Volatile
    private var memoryCache: Map<Int, List<RadioInfo>>? = null

    /**
     * 查询指定卫星（按 NORAD 号）的转发器列表。
     *
     * 读取链：内存缓存 → Room；两者都未命中时触发一次 [refresh] 再查 Room。
     * 网络失败不影响返回值（返回空列表）。
     */
    suspend fun getRadiosForSatellite(noradCatId: Int): List<RadioInfo> {
        memoryCache?.get(noradCatId)?.let { return it }

        var radios = dao.getByNorad(noradCatId).map { it.toModel() }
        if (radios.isEmpty()) {
            refresh()
            radios = dao.getByNorad(noradCatId).map { it.toModel() }
        }
        return radios
    }

    /**
     * 返回全量转发器映射（NORAD 编号 → 转发器列表，含全部状态）。
     *
     * 内存缓存未就绪时先触发一次 [refresh]（静默失败）。用于卫星列表页
     * 一次性取用全部转发器信息，避免对每颗卫星逐次查询。
     */
    suspend fun getAllRadios(): Map<Int, List<RadioInfo>> {
        memoryCache?.let { return it }

        var all = dao.getAll().map { it.toModel() }.groupBy { it.noradCatId }
        if (all.isEmpty()) {
            refresh()
            all = dao.getAll().map { it.toModel() }.groupBy { it.noradCatId }
        }
        memoryCache = all
        return all
    }

    /**
     * 刷新转发器缓存（网络 → Room + 内存）。幂等：7 天内且已有内存缓存则跳过。
     * 失败静默回退（仅 Log.w），不抛异常。
     */
    suspend fun refresh() {
        try {
            val lastFetched = dao.lastFetchedAt()
            if (lastFetched != null &&
                System.currentTimeMillis() - lastFetched < CACHE_TTL_MILLIS &&
                memoryCache != null
            ) {
                return
            }
            val radios = dataSource.fetchTransmitters()
            val fetchedAt = System.currentTimeMillis()
            dao.clear()
            dao.insertAll(radios.map { it.toEntity(fetchedAt) })
            memoryCache = radios.groupBy { it.noradCatId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "刷新转发器数据失败，使用本地缓存", e)
        }
    }

    /**
     * 由 [RadioInfo] 列表提取去重后的**活跃**模式标签（如 ["FM", "USB"]）。
     * 仅统计 [RadioInfo.isActive] 的转发器；无活跃转发器时返回空列表。
     */
    fun activeModes(radios: List<RadioInfo>): List<String> =
        radios.filter { it.isActive }
            .map { it.mode }
            .filter { it.isNotBlank() }
            .distinct()

    companion object {
        private const val TAG = "RadioInfoRepository"

        /** 缓存新鲜度：7 天内不重新拉取 */
        private const val CACHE_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L

        /**
         * 纯函数：由 [RadioInfo] 列表提取去重后的模式标签（如 ["FM", "USB"]，
         * 含非活跃转发器）。无数据时返回空列表。
         */
        fun radioModes(radios: List<RadioInfo>): List<String> =
            radios.map { it.mode }
                .filter { it.isNotBlank() }
                .distinct()
    }

    private fun RadioInfoEntity.toModel() = RadioInfo(
        noradCatId = noradCatId,
        name = name,
        uplinkHz = uplinkHz,
        downlinkHz = downlinkHz,
        mode = mode,
        inverted = inverted,
        status = status,
        description = description,
    )

    private fun RadioInfo.toEntity(fetchedAt: Long) = RadioInfoEntity(
        uuid = "$noradCatId:$name:$uplinkHz:$downlinkHz:$mode:$status",
        noradCatId = noradCatId,
        name = name,
        uplinkHz = uplinkHz,
        downlinkHz = downlinkHz,
        mode = mode,
        inverted = inverted,
        status = status,
        description = description,
        fetchedAt = fetchedAt,
    )
}

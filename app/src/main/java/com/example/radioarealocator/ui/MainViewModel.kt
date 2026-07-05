package com.example.radioarealocator.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.radioarealocator.data.LocationResult
import com.example.radioarealocator.data.SettingsStore
import com.example.radioarealocator.data.location.LocationHelper
import com.example.radioarealocator.data.satellite.FavoriteSatellitesStore
import com.example.radioarealocator.data.satellite.SatelliteCacheStore
import com.example.radioarealocator.data.satellite.SatelliteDataSource
import com.example.radioarealocator.data.satellite.SatelliteInfo
import com.example.radioarealocator.data.satellite.SatellitePredictor
import com.example.radioarealocator.data.satellite.SourcedTLE
import com.example.radioarealocator.data.zone.ZoneResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val locationHelper = LocationHelper(application)
    private val satelliteDataSource = SatelliteDataSource()
    private val satellitePredictor = SatellitePredictor()
    private val settingsStore = SettingsStore(application)
    private val satelliteCache = SatelliteCacheStore(application)
    private val favoriteStore = FavoriteSatellitesStore(application)

    // 跟踪上一次刷新的 Job，避免用户快速多次点击导致并发竞态
    private var refreshJob: Job? = null
    private var locationOnlyJob: Job? = null
    private var satelliteOnlyJob: Job? = null
    private var predictJob: Job? = null
    private var initialized = false

    private val _uiState = mutableStateOf(MainUiState())
    val uiState: State<MainUiState> = _uiState

    // 卫星数据来源设置："ALL" / "CT" / "SNOGS"
    private val _satelliteSource = mutableStateOf("ALL")
    val satelliteSource: State<String> = _satelliteSource

    fun setSatelliteSource(source: String) {
        _satelliteSource.value = source
    }

    /**
     * 卫星筛选状态。类型筛选为空集合时表示不按模式筛选。
     */
    private val _satelliteFilter = mutableStateOf(SatelliteFilter())
    val satelliteFilter: State<SatelliteFilter> = _satelliteFilter

    fun updateSatelliteFilter(filter: SatelliteFilter) {
        _satelliteFilter.value = filter
    }

    /**
     * 用户关注的卫星 NORAD 编号集合，进程重启后可从本地恢复。
     */
    private val _favoriteSatellites = mutableStateOf(favoriteStore.load())
    val favoriteSatellites: State<Set<Int>> = _favoriteSatellites

    /**
     * 切换某颗卫星的关注状态并持久化。
     */
    fun toggleFavorite(catalogNumber: Int) {
        val updated = favoriteStore.toggle(catalogNumber)
        _favoriteSatellites.value = updated
    }

    // 背景图 URI：null 表示未设置
    private val _backgroundUri = mutableStateOf<Uri?>(settingsStore.backgroundUri?.let(Uri::parse))
    val backgroundUri: State<Uri?> = _backgroundUri

    /**
     * 设置背景图 URI 并持久化。null 表示清除。
     * 调用方需先通过 [android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION] 获取持久化读取权限。
     */
    fun setBackgroundUri(uri: Uri?) {
        _backgroundUri.value = uri
        settingsStore.backgroundUri = uri?.toString()
    }

    val hasLocationPermission: Boolean
        get() = locationHelper.hasPermission()

    /**
     * ViewModel 初始化：从本地缓存加载 TLE，并按需触发后台更新。
     * 应在 UI 准备好后调用一次。多次调用安全（仅首次执行）。
     *
     * 缓存读取（JSON 解析）放在 IO 调度器执行，避免阻塞主线程导致 UI 卡顿。
     */
    suspend fun initializeIfNeeded() {
        if (initialized) return
        initialized = true

        // 先从本地缓存恢复 TLE 与时间戳（IO 密集型，避免在主线程解析 JSON）
        val cached = withContext(Dispatchers.IO) { satelliteCache.load() }
        if (cached != null) {
            _uiState.value = _uiState.value.copy(
                cachedTles = cached.tles,
                lastSatelliteUpdateTime = cached.updatedAt
            )
            // 缓存存在但有定位时立即预测一次
            val current = _uiState.value.result
            if (current != null) {
                triggerPrediction(current.latitude, current.longitude)
            }
        }

        // 缓存为空或已过期：后台拉取新数据
        if (cached == null || isSatelliteSourceExpired(cached.updatedAt)) {
            refreshSatelliteSourceOnly()
        }
    }

    fun refreshLocation() {
        if (!locationHelper.hasPermission()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "需要定位权限"
            )
            return
        }

        // 取消上一次刷新任务，避免并发竞态导致 UI 闪烁和数据不一致
        refreshJob?.cancel()
        locationOnlyJob?.cancel()

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            result = null
        )
        refreshJob = viewModelScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                val zoneInfo = ZoneResolver.resolve(location.latitude, location.longitude)

                // 立即显示定位结果（不等待地址），减少用户感知等待时间
                val baseResult = LocationResult(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    cqZone = zoneInfo.cqZone,
                    ituZone = zoneInfo.ituZone,
                    maidenhead = zoneInfo.maidenhead,
                    address = ""
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result = baseResult,
                    lastLocationUpdateTime = Instant.now(),
                    lastLocationCity = ""
                )

                // 后台加载地址
                val addressDeferred = async {
                    locationHelper.getAddress(location.latitude, location.longitude)
                }
                val cityDeferred = async {
                    locationHelper.getCityAddress(location.latitude, location.longitude)
                }

                val address = addressDeferred.await()
                val city = cityDeferred.await()

                _uiState.value = _uiState.value.copy(
                    result = baseResult.copy(address = address),
                    lastLocationCity = city
                )

                // 触发卫星过境预测（基于已有 TLE 缓存或刚拉取的 TLE）
                triggerPrediction(location.latitude, location.longitude)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "定位失败"
                )
            }
        }
    }

    /**
     * 仅刷新定位（不重新获取卫星数据）。用于卫星页"获取定位"按钮。
     * 成功后保留已有卫星列表，但会用新定位重新预测过境。
     */
    fun refreshLocationOnly() {
        if (!locationHelper.hasPermission()) {
            _uiState.value = _uiState.value.copy(
                error = "需要定位权限"
            )
            return
        }

        locationOnlyJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null
        )
        locationOnlyJob = viewModelScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                val zoneInfo = ZoneResolver.resolve(location.latitude, location.longitude)
                val city = locationHelper.getCityAddress(location.latitude, location.longitude)
                val address = locationHelper.getAddress(location.latitude, location.longitude)

                val newResult = LocationResult(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    cqZone = zoneInfo.cqZone,
                    ituZone = zoneInfo.ituZone,
                    maidenhead = zoneInfo.maidenhead,
                    address = address
                )
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result = newResult,
                    lastLocationUpdateTime = Instant.now(),
                    lastLocationCity = city
                )
                // 用新定位重新预测
                triggerPrediction(location.latitude, location.longitude)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "定位失败"
                )
            }
        }
    }

    /**
     * 仅刷新卫星源（不重新定位）。用于卫星页"更新卫星源"按钮。
     * 无定位时也允许更新：只更新 TLE 缓存，等有定位后再做预测。
     */
    fun refreshSatelliteSourceOnly() {
        satelliteOnlyJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isSatelliteLoading = true,
            satelliteError = null
        )
        satelliteOnlyJob = viewModelScope.launch {
            try {
                val tles = fetchAndCacheTLEs()
                val current = _uiState.value.result
                if (current != null) {
                    // 有定位：重新预测
                    triggerPrediction(current.latitude, current.longitude, tles)
                } else {
                    // 无定位：仅更新缓存，等待定位后再预测
                    _uiState.value = _uiState.value.copy(
                        isSatelliteLoading = false,
                        cachedTles = tles,
                        lastSatelliteUpdateTime = Instant.now()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSatelliteLoading = false,
                    satelliteError = e.message ?: "卫星源更新失败"
                )
            }
        }
    }

    /**
     * 拉取 TLE 并写入本地缓存，返回最新 TLE 列表。
     * 缓存写入（JSON 序列化）放在 IO 调度器执行，避免阻塞主线程。
     */
    private suspend fun fetchAndCacheTLEs(): List<SourcedTLE> {
        val tles = satelliteDataSource.fetchAmateurTLEs(source = _satelliteSource.value)
        val now = Instant.now()
        withContext(Dispatchers.IO) { satelliteCache.save(tles, now) }
        return tles
    }

    /**
     * 触发卫星过境预测。使用 [tlesOverride] 或当前缓存的 TLE。
     */
    private fun triggerPrediction(
        latitude: Double,
        longitude: Double,
        tlesOverride: List<SourcedTLE>? = null
    ) {
        val tles = tlesOverride ?: _uiState.value.cachedTles
        predictJob?.cancel()
        if (tles.isEmpty()) {
            // 无 TLE 数据，先拉取再预测
            predictJob = viewModelScope.launch {
                try {
                    val fresh = fetchAndCacheTLEs()
                    predictAndApply(fresh, latitude, longitude)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isSatelliteLoading = false,
                        satelliteError = e.message ?: "卫星源更新失败"
                    )
                }
            }
            return
        }
        predictJob = viewModelScope.launch {
            predictAndApply(tles, latitude, longitude)
        }
    }

    private suspend fun predictAndApply(
        tles: List<SourcedTLE>,
        latitude: Double,
        longitude: Double
    ) {
        try {
            _uiState.value = _uiState.value.copy(isSatelliteLoading = true)
            val satellites = satellitePredictor.predictUpcomingPasses(
                sourcedTles = tles,
                latitude = latitude,
                longitude = longitude
            )
            _uiState.value = _uiState.value.copy(
                isSatelliteLoading = false,
                satellites = satellites,
                cachedTles = tles,
                satelliteError = null,
                lastSatelliteUpdateTime = Instant.now()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSatelliteLoading = false,
                satelliteError = e.message ?: "卫星过境预测失败"
            )
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * 卫星源过期阈值：TLE 数据通常 24 小时后视为过期。
 */
private val SATELLITE_SOURCE_EXPIRY = java.time.Duration.ofHours(24)

/**
 * 判断 [lastUpdate] 相对 [now] 是否已过期。
 */
fun isSatelliteSourceExpired(lastUpdate: Instant?, now: Instant = Instant.now()): Boolean {
    if (lastUpdate == null) return true
    return java.time.Duration.between(lastUpdate, now) >= SATELLITE_SOURCE_EXPIRY
}

/**
 * 卫星筛选条件。
 *
 * @param modes 工作模式多选筛选，空集合表示不按模式筛选。可选值：FM/SSTV/DSTAR/CW/USB/LSB
 * @param onlyUpcoming 仅显示即将入境（不含当前在境）
 * @param onlyInPass 仅显示当前在境
 * @param onlyAmsat 仅显示 AMSAT 状态 API 中的卫星（即有 status 报告的）
 * @param onlyFavorites 仅显示已关注卫星
 */
data class SatelliteFilter(
    val modes: Set<String> = emptySet(),
    val onlyUpcoming: Boolean = false,
    val onlyInPass: Boolean = false,
    val onlyAmsat: Boolean = false,
    val onlyFavorites: Boolean = false
) {
    /**
     * 当前筛选是否处于激活状态（任一条件被设置）。
     */
    val isActive: Boolean
        get() = modes.isNotEmpty() || onlyUpcoming || onlyInPass || onlyAmsat || onlyFavorites
}

/**
 * 应用筛选条件到卫星列表。
 */
fun List<SatelliteInfo>.applyFilter(
    filter: SatelliteFilter,
    favorites: Set<Int> = emptySet()
): List<SatelliteInfo> {
    if (!filter.isActive) return this
    return this.filter { sat ->
        val modeOk = filter.modes.isEmpty() || filter.modes.any { it in sat.modes }
        val upcomingOk = !filter.onlyUpcoming || !sat.isCurrentlyVisible
        val inPassOk = !filter.onlyInPass || sat.isCurrentlyVisible
        val amsatOk = !filter.onlyAmsat || sat.status.isNotBlank()
        val favoriteOk = !filter.onlyFavorites || sat.catalogNumber in favorites
        modeOk && upcomingOk && inPassOk && amsatOk && favoriteOk
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val isSatelliteLoading: Boolean = false,
    val result: LocationResult? = null,
    val satellites: List<SatelliteInfo> = emptyList(),
    val error: String? = null,
    val satelliteError: String? = null,
    /** 最近一次定位成功的时间，null 表示从未获取 */
    val lastLocationUpdateTime: Instant? = null,
    /** 最近一次定位的市级地址，空字符串表示尚未获取 */
    val lastLocationCity: String = "",
    /** 最近一次卫星源更新时间，null 表示从未更新 */
    val lastSatelliteUpdateTime: Instant? = null,
    /** 本地缓存的 TLE 列表，进程重启后可从 [SatelliteCacheStore] 恢复 */
    val cachedTles: List<SourcedTLE> = emptyList()
)

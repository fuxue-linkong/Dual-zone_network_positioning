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
import com.example.radioarealocator.data.satellite.SatelliteDataSource
import com.example.radioarealocator.data.satellite.SatelliteInfo
import com.example.radioarealocator.data.satellite.SatellitePredictor
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

    // 跟踪上一次刷新的 Job，避免用户快速多次点击导致并发竞态
    private var refreshJob: Job? = null
    private var locationOnlyJob: Job? = null
    private var satelliteOnlyJob: Job? = null

    private val _uiState = mutableStateOf(MainUiState())
    val uiState: State<MainUiState> = _uiState

    // 卫星数据来源设置："ALL" / "CT" / "SNOGS"
    private val _satelliteSource = mutableStateOf("ALL")
    val satelliteSource: State<String> = _satelliteSource

    fun setSatelliteSource(source: String) {
        _satelliteSource.value = source
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
        satelliteOnlyJob?.cancel()

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isSatelliteLoading = true,
            error = null,
            satelliteError = null,
            result = null,
            satellites = emptyList()
        )
        refreshJob = viewModelScope.launch {
            try {
                val location = locationHelper.getCurrentLocation()
                val zoneInfo = ZoneResolver.resolve(location.latitude, location.longitude)

                // 立即显示定位结果（不等待地址和卫星），减少用户感知等待时间
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

                // 后台并行加载地址与卫星信息
                val addressDeferred = async {
                    locationHelper.getAddress(location.latitude, location.longitude)
                }
                val cityDeferred = async {
                    locationHelper.getCityAddress(location.latitude, location.longitude)
                }
                val satellitesDeferred = async {
                    // 显式 try-catch 替代 runCatching，避免吞掉 CancellationException
                    try {
                        Result.success(refreshSatellites(location.latitude, location.longitude))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }

                val address = addressDeferred.await()
                val city = cityDeferred.await()
                val satellitesResult = satellitesDeferred.await()

                _uiState.value = _uiState.value.copy(
                    result = baseResult.copy(address = address),
                    lastLocationCity = city,
                    satellites = satellitesResult.getOrNull() ?: emptyList(),
                    satelliteError = satellitesResult.exceptionOrNull()?.message,
                    isSatelliteLoading = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSatelliteLoading = false,
                    error = e.message ?: "定位失败"
                )
            }
        }
    }

    /**
     * 仅刷新定位（不重新获取卫星数据）。用于卫星页"获取定位"按钮。
     * 成功后保留已有卫星列表。
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
     * 使用已有定位结果；若无定位则提示。
     */
    fun refreshSatelliteSourceOnly() {
        val current = _uiState.value.result
        if (current == null) {
            _uiState.value = _uiState.value.copy(
                satelliteError = "请先获取定位"
            )
            return
        }

        satelliteOnlyJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isSatelliteLoading = true,
            satelliteError = null
        )
        satelliteOnlyJob = viewModelScope.launch {
            try {
                val satellites = refreshSatellites(current.latitude, current.longitude)
                _uiState.value = _uiState.value.copy(
                    isSatelliteLoading = false,
                    satellites = satellites,
                    lastSatelliteUpdateTime = Instant.now()
                )
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

    private suspend fun refreshSatellites(latitude: Double, longitude: Double): List<SatelliteInfo> {
        val tles = satelliteDataSource.fetchAmateurTLEs(source = _satelliteSource.value)
        return satellitePredictor.predictUpcomingPasses(
            sourcedTles = tles,
            latitude = latitude,
            longitude = longitude
        )
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
    val lastSatelliteUpdateTime: Instant? = null
)

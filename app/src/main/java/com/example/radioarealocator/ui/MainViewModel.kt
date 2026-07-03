package com.example.radioarealocator.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.radioarealocator.data.LocationResult
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

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val locationHelper = LocationHelper(application)
    private val satelliteDataSource = SatelliteDataSource()
    private val satellitePredictor = SatellitePredictor()

    // 跟踪上一次刷新的 Job，避免用户快速多次点击导致并发竞态
    private var refreshJob: Job? = null

    private val _uiState = mutableStateOf(MainUiState())
    val uiState: State<MainUiState> = _uiState

    // 卫星数据来源设置："ALL" / "CT" / "SNOGS"
    private val _satelliteSource = mutableStateOf("ALL")
    val satelliteSource: State<String> = _satelliteSource

    fun setSatelliteSource(source: String) {
        _satelliteSource.value = source
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
                    result = baseResult
                )

                // 后台并行加载地址与卫星信息
                val addressDeferred = async {
                    locationHelper.getAddress(location.latitude, location.longitude)
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
                val satellitesResult = satellitesDeferred.await()

                _uiState.value = _uiState.value.copy(
                    result = baseResult.copy(address = address),
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

    private suspend fun refreshSatellites(latitude: Double, longitude: Double): List<SatelliteInfo> {
        val tles = satelliteDataSource.fetchAmateurTLEs(source = _satelliteSource.value)
        // 卫星过境预测为 CPU 密集计算，切到 Default 调度器避免阻塞主线程
        return withContext(Dispatchers.Default) {
            satellitePredictor.predictUpcomingPasses(
                sourcedTles = tles,
                latitude = latitude,
                longitude = longitude
            )
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val isSatelliteLoading: Boolean = false,
    val result: LocationResult? = null,
    val satellites: List<SatelliteInfo> = emptyList(),
    val error: String? = null,
    val satelliteError: String? = null
)

package com.example.radioarealocator.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationHelper(private val context: Context) {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getCurrentLocation(): Location {
        if (!hasPermission()) {
            throw SecurityException("缺少定位权限")
        }

        val errors = Collections.synchronizedList(mutableListOf<String>())

        return try {
            withTimeout(10_000) {
                coroutineScope {
                    // 并行启动所有定位策略，取最快成功的一个
                    val strategies = listOf(
                        async { fetchFusedLastLocation(errors) },
                        async {
                            fetchFusedCurrentLocation(
                                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                                5_000,
                                "Fused低精度",
                                errors
                            )
                        },
                        async {
                            fetchFusedCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                4_000,
                                "Fused高精度",
                                errors
                            )
                        },
                        async { fetchLocationManagerLocation(6_000, errors) },
                        async { fetchSingleFusedUpdate(4_000, errors) }
                    )

                    val pending = strategies.toMutableList()
                    while (pending.isNotEmpty()) {
                        val done = select<Deferred<Location?>> {
                            pending.forEach { deferred ->
                                deferred.onAwait { deferred }
                            }
                        }
                        val location = done.await()
                        if (location != null) {
                            pending.forEach { it.cancel() }
                            return@coroutineScope location
                        }
                        pending.remove(done)
                    }

                    throw Exception("所有定位方式均失败")
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw Exception("定位超时，请检查手机是否开启 GPS/网络定位")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val detail = errors.joinToString("; ")
            throw Exception(
                "无法获取定位，请检查手机是否开启 GPS/网络定位，或检查是否禁止了本应用定位权限。详情: $detail"
            )
        }
    }

    /**
     * 持续位置监听（Flow 形式）。
     *
     * 基于 FusedLocationProviderClient.requestLocationUpdates 注册持续回调，
     * 当设备位置变化超过 [minDistanceM] 或经过 [intervalMs] 时上报新位置。
     * Flow 被取消时自动移除回调，避免泄漏。
     *
     * 默认参数优先保证实时性：5 秒间隔 + 0 米最小位移 + 高精度优先级，
     * 确保设备位置发生变化时经纬度能被及时捕获并更新到界面。
     *
     * @param intervalMs 期望的位置上报间隔（毫秒）
     * @param minDistanceM 最小位移阈值（米），小于此距离的变化不上报
     */
    fun locationUpdates(
        intervalMs: Long = 5_000L,
        minDistanceM: Float = 0f
    ): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            close(SecurityException("缺少定位权限"))
            return@callbackFlow
        }

        val locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMs
        ).apply {
            setMinUpdateDistanceMeters(minDistanceM)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            ).addOnFailureListener { e ->
                close(e)
            }
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            try {
                fusedClient.removeLocationUpdates(locationCallback)
            } catch (_: Exception) {
                // 忽略移除回调时的异常
            }
        }
    }

    private suspend fun fetchFusedLastLocation(errors: MutableList<String>): Location? {
        return try {
            withTimeout(1_500) { requestFusedLastLocation() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add("Fused最近位置: ${e.message ?: "失败"}")
            null
        }
    }

    private suspend fun fetchFusedCurrentLocation(
        priority: Int,
        timeoutMs: Long,
        name: String,
        errors: MutableList<String>
    ): Location? {
        return try {
            withTimeout(timeoutMs) { requestFusedCurrentLocation(priority) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add("$name: ${e.message ?: "失败"}")
            null
        }
    }

    private suspend fun fetchSingleFusedUpdate(
        timeoutMs: Long,
        errors: MutableList<String>
    ): Location? {
        return try {
            withTimeout(timeoutMs) { requestSingleFusedUpdate() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add("Fused单次更新: ${e.message ?: "失败"}")
            null
        }
    }

    private suspend fun fetchLocationManagerLocation(
        timeoutMs: Long,
        errors: MutableList<String>
    ): Location? {
        return try {
            withTimeout(timeoutMs) { requestLocationManagerLocation() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add("系统定位: ${e.message ?: "失败"}")
            null
        }
    }

    private suspend fun requestFusedCurrentLocation(priority: Int): Location =
        suspendCancellableCoroutine { continuation ->
            val token = CancellationTokenSource()
            try {
                fusedClient.getCurrentLocation(
                    priority,
                    token.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        continuation.resume(location)
                    } else {
                        continuation.resumeWithException(NullPointerException("定位返回空值"))
                    }
                }.addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }.addOnCanceledListener {
                    continuation.cancel()
                }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }

            continuation.invokeOnCancellation {
                token.cancel()
            }
        }

    private suspend fun requestFusedLastLocation(): Location =
        suspendCancellableCoroutine { continuation ->
            try {
                fusedClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            continuation.resume(location)
                        } else {
                            continuation.resumeWithException(NullPointerException("没有最近一次定位记录"))
                        }
                    }
                    .addOnFailureListener { exception ->
                        continuation.resumeWithException(exception)
                    }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }

            // lastLocation 的 Task 没有显式取消 API，但注册取消回调明确意图：
            // 取消后迟到的 resume 会被 CancellableContinuation 静默忽略
            continuation.invokeOnCancellation { /* Task 无法取消，迟到回调由框架忽略 */ }
        }

    private suspend fun requestSingleFusedUpdate(): Location =
        suspendCancellableCoroutine { continuation ->
            try {
                val request = LocationRequest.Builder(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    1000L
                ).apply {
                    setWaitForAccurateLocation(false)
                    setMinUpdateIntervalMillis(500L)
                    setMaxUpdateDelayMillis(2000L)
                }.build()

                val callback = object : com.google.android.gms.location.LocationCallback() {
                    override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                        fusedClient.removeLocationUpdates(this)
                        result.lastLocation?.let {
                            continuation.resume(it)
                        } ?: continuation.resumeWithException(NullPointerException("定位返回空值"))
                    }
                }

                fusedClient.requestLocationUpdates(
                    request,
                    callback,
                    Looper.getMainLooper()
                ).addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }

                continuation.invokeOnCancellation {
                    fusedClient.removeLocationUpdates(callback)
                }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }

    private suspend fun requestLocationManagerLocation(): Location =
        suspendCancellableCoroutine { continuation ->
            var resumed = false
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (resumed) return
                    resumed = true
                    removeListener(this)
                    continuation.resume(location)
                }

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }

            continuation.invokeOnCancellation {
                removeListener(listener)
            }

            try {
                val providers = listOfNotNull(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                ).filter { provider ->
                    try {
                        locationManager.isProviderEnabled(provider)
                    } catch (e: Exception) {
                        false
                    }
                }

                if (providers.isEmpty()) {
                    resumed = true
                    continuation.resumeWithException(Exception("系统未开启任何定位源"))
                    return@suspendCancellableCoroutine
                }

                // 先尝试 getLastKnownLocation
                for (provider in providers) {
                    val last = try {
                        locationManager.getLastKnownLocation(provider)
                    } catch (e: SecurityException) {
                        null
                    } catch (e: Exception) {
                        null
                    }
                    if (last != null) {
                        resumed = true
                        continuation.resume(last)
                        return@suspendCancellableCoroutine
                    }
                }

                // 没有缓存则请求一次更新
                for (provider in providers) {
                    try {
                        locationManager.requestLocationUpdates(
                            provider,
                            500L,
                            0f,
                            listener,
                            Looper.getMainLooper()
                        )
                    } catch (e: SecurityException) {
                        // 忽略单个 provider 的权限异常，继续下一个
                    } catch (e: Exception) {
                        // 忽略单个 provider 异常
                    }
                }
            } catch (e: SecurityException) {
                if (!resumed) {
                    resumed = true
                    removeListener(listener)
                    continuation.resumeWithException(e)
                }
            }
        }

    private fun removeListener(listener: LocationListener) {
        try {
            locationManager.removeUpdates(listener)
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * 根据经纬度反向地理编码获取地址信息。
     * 在后台线程执行，返回格式化地址字符串；失败或不可用时返回空字符串。
     */
    suspend fun getAddress(latitude: Double, longitude: Double): String {
        return try {
            withTimeout(3_000) {
                if (!Geocoder.isPresent()) {
                    return@withTimeout ""
                }

                val geocoder = Geocoder(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(
                            latitude,
                            longitude,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    continuation.resume(formatAddress(addresses.firstOrNull()))
                                }

                                override fun onError(errorMessage: String?) {
                                    continuation.resume("")
                                }
                            }
                        )
                        // Geocoder 异步回调无显式取消 API，注册取消回调明确意图
                        continuation.invokeOnCancellation { /* 迟到回调由 CancellableContinuation 静默忽略 */ }
                    }
                } else {
                    // pre-TIRAMISU 的同步阻塞调用，切到 IO 调度器避免阻塞主线程
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                        formatAddress(addresses?.firstOrNull())
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 仅返回市级地址（locality 优先，缺失时回退到 adminArea）。
     * 失败或不可用时返回空字符串。
     */
    suspend fun getCityAddress(latitude: Double, longitude: Double): String {
        return try {
            withTimeout(3_000) {
                if (!Geocoder.isPresent()) {
                    return@withTimeout ""
                }

                val geocoder = Geocoder(context)
                val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(
                            latitude,
                            longitude,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    continuation.resume(addresses.firstOrNull())
                                }

                                override fun onError(errorMessage: String?) {
                                    continuation.resume(null)
                                }
                            }
                        )
                        continuation.invokeOnCancellation { /* 迟到回调由 CancellableContinuation 静默忽略 */ }
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    }
                }

                address?.locality?.takeIf { it.isNotBlank() }
                    ?: address?.adminArea?.takeIf { it.isNotBlank() }
                    ?: ""
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    private fun formatAddress(address: Address?): String {
        if (address == null) return ""
        val parts = listOfNotNull(
            address.adminArea,
            address.locality,
            address.subLocality,
            address.thoroughfare
        ).filter { it.isNotBlank() }
        return if (parts.isNotEmpty()) {
            parts.joinToString(" ")
        } else {
            address.getAddressLine(0) ?: ""
        }
    }
}

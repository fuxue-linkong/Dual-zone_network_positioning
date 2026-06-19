package com.example.radioarealocator.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
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

        val errors = mutableListOf<String>()

        // 1. 优先尝试 Fused getCurrentLocation，低精度优先（兼容部分厂商魔改系统）
        runCatching {
            withTimeout(8_000) { requestFusedCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY) }
        }.onSuccess { return it }
            .onFailure { errors.add("Fused低精度: ${it.message ?: "失败"}") }

        runCatching {
            withTimeout(8_000) { requestFusedCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY) }
        }.onSuccess { return it }
            .onFailure { errors.add("Fused高精度: ${it.message ?: "失败"}") }

        // 2. 尝试 Fused lastLocation
        runCatching {
            withTimeout(5_000) { requestFusedLastLocation() }
        }.onSuccess { return it }
            .onFailure { errors.add("Fused最近位置: ${it.message ?: "失败"}") }

        // 3. 尝试原生 LocationManager（很多国产厂商魔改后此接口更稳定）
        runCatching {
            withTimeout(12_000) { requestLocationManagerLocation() }
        }.onSuccess { return it }
            .onFailure { errors.add("系统定位: ${it.message ?: "失败"}") }

        // 4. 最后再试一次 Fused requestLocationUpdates（持续监听一次）
        runCatching {
            withTimeout(10_000) { requestSingleFusedUpdate() }
        }.onSuccess { return it }
            .onFailure { errors.add("Fused单次更新: ${it.message ?: "失败"}") }

        val detail = errors.joinToString("; ")
        throw Exception(
            "无法获取定位，请检查手机是否开启 GPS/网络定位，或检查是否禁止了本应用定位权限。详情: $detail"
        )
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
}

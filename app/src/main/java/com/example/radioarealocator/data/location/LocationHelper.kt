package com.example.radioarealocator.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationHelper(private val context: Context) {

    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
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

    suspend fun getCurrentLocation(): Location {
        if (!hasPermission()) {
            throw SecurityException("缺少定位权限")
        }

        return suspendCancellableCoroutine { continuation ->
            val token = CancellationTokenSource()
            try {
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
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
    }
}

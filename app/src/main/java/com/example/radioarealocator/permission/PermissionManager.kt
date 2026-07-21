package com.example.radioarealocator.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionManager(context: Context) {

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(readState())

    val state: StateFlow<PermissionState> = _state.asStateFlow()

    fun refresh() {
        _state.value = readState()
    }

    /**
     * 定位运行时权限：ACCESS_FINE_LOCATION（核心业务依赖，必需授权）。
     * 同时声明了 ACCESS_COARSE_LOCATION 作为降级兜底，但 [PermissionState.location] 仅以 FINE 为准。
     */
    fun locationRuntimePermission(): String = Manifest.permission.ACCESS_FINE_LOCATION

    fun locationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", appContext.packageName, null)
        }

    private fun readState() = PermissionState(
        location = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
    )

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PermissionChecker.PERMISSION_GRANTED
}

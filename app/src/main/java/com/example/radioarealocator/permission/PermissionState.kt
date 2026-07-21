package com.example.radioarealocator.permission

data class PermissionState(
    val notification: Boolean = false,
    val batteryWhitelist: Boolean = false,
) {
    val requiredGranted: Boolean
        get() = notification && batteryWhitelist
}

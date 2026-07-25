package com.example.radioarealocator.permission

data class PermissionState(
    val location: Boolean = false,
    val notification: Boolean = false,
    val exactAlarm: Boolean = true,
) {
    val requiredGranted: Boolean
        get() = location
}

package com.example.radioarealocator.permission

data class PermissionState(
    val location: Boolean = false,
) {
    val requiredGranted: Boolean
        get() = location
}

package com.example.radioarealocator.data

import java.time.Instant

data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val cqZone: Int?,
    val ituZone: Int?,
    val maidenhead: String,
    val timestamp: Instant = Instant.now()
)

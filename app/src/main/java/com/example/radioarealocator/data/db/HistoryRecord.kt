package com.example.radioarealocator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "location_history")
data class HistoryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val cqZone: Int?,
    val ituZone: Int?,
    val maidenhead: String,
    val createdAt: Instant
)

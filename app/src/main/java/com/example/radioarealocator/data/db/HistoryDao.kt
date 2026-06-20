package com.example.radioarealocator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM location_history ORDER BY createdAt DESC")
    fun getAll(): Flow<List<HistoryRecord>>

    @Insert
    suspend fun insert(record: HistoryRecord)

    @Query("DELETE FROM location_history")
    suspend fun clearAll()

    @Query("DELETE FROM location_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}

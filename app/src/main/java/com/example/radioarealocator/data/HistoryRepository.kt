package com.example.radioarealocator.data

import com.example.radioarealocator.data.db.HistoryDao
import com.example.radioarealocator.data.db.HistoryRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class HistoryRepository(private val dao: HistoryDao) {

    val history: Flow<List<HistoryRecord>> = dao.getAll()

    suspend fun save(result: LocationResult) {
        dao.insert(
            HistoryRecord(
                latitude = result.latitude,
                longitude = result.longitude,
                cqZone = result.cqZone,
                ituZone = result.ituZone,
                maidenhead = result.maidenhead,
                createdAt = result.timestamp
            )
        )
    }

    suspend fun clearAll() {
        dao.clearAll()
    }

    suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }
}

package com.example.radioarealocator

import android.app.Application
import androidx.room.Room
import com.example.radioarealocator.data.HistoryRepository
import com.example.radioarealocator.data.db.AppDatabase

class RadioAreaLocatorApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "radio_area_locator.db"
        ).build()
        historyRepository = HistoryRepository(database.historyDao())
    }
}

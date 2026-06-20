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
        // 强制 commons-logging 使用 SimpleLog，避免在 Android 上发现日志实现失败
        // （predict4java 依赖 commons-logging，其默认发现机制在 Android 上会抛 NPE）
        System.setProperty(
            "org.apache.commons.logging.Log",
            "org.apache.commons.logging.impl.SimpleLog"
        )
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "radio_area_locator.db"
        ).build()
        historyRepository = HistoryRepository(database.historyDao())
    }
}

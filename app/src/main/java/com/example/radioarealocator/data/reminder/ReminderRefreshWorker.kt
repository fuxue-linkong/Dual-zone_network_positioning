package com.example.radioarealocator.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 每日刷新提醒调度的 Worker。
 *
 * 职责：
 * - 重新注册所有 [ReminderItem] 的 AlarmManager 闹钟。
 *   Android 设备重启或应用被强杀后，已注册的精确闹钟会丢失，需定期重新调度。
 * - 不做卫星过境重预测（那是 ViewModel 的职责，需要 TLE + 用户位置）。
 *   仅基于 ReminderStore 中已存的 aosTimeMillis 重新调度。
 *
 * 周期：每 24 小时执行一次（WorkManager 最小周期 15 分钟，但每日足够）。
 */
class ReminderRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val store = ReminderStore(applicationContext)
            val settings = store.loadSettings()
            val items = store.loadItems()
            val scheduler = ReminderScheduler(applicationContext)

            // 重新调度所有提醒
            scheduler.scheduleAll(items, settings)
            Result.success()
        } catch (e: Exception) {
            // 失败不阻塞下次执行
            Result.retry()
        }
    }
}

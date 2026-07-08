package com.example.radioarealocator.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 设备重启后恢复卫星过境提醒闹钟。
 *
 * Android 系统在设备重启后会清除所有通过 AlarmManager 注册的精确闹钟，
 * 因此必须在收到 [Intent.ACTION_BOOT_COMPLETED] 时重新注册。
 *
 * 同时会在应用更新后（[Intent.ACTION_MY_PACKAGE_REPLACED]）恢复闹钟，
 * 因为应用更新也可能导致 PendingIntent 失效。
 *
 * 恢复策略（两步）：
 * 1. 立即从 [ReminderStore] 读取已存储的 [ReminderItem] 列表，
 *    用 [ReminderScheduler] 重新注册闹钟——可恢复未来尚未过期的提醒。
 * 2. 入队一次性 [ReminderRefreshWorker]（延迟 30 秒），
 *    让 Worker 在后台重新下载 TLE + 预测过境 + 刷新闹钟，
 *    确保未来也有新增的过境提醒。
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }

        // 第一步：立即恢复已存储的闹钟（从 ReminderStore 读取未过期的提醒项）
        try {
            val store = ReminderStore(context)
            val settings = store.loadSettings()
            val items = store.loadItems()
            if (settings.enabled && items.isNotEmpty()) {
                val scheduler = ReminderScheduler(context)
                scheduler.scheduleAll(items, settings)
            }
        } catch (_: Exception) {
            // 读取或调度失败不影响第二步，Worker 会重新建立完整状态
        }

        // 第二步：入队一次性刷新任务，延迟 30 秒避免开机瞬间网络不可用
        // Worker 会独立完成"TLE 下载 + 过境预测 + 闹钟注册"全链路
        val refreshRequest = OneTimeWorkRequestBuilder<ReminderRefreshWorker>()
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_BOOT_REFRESH,
            ExistingWorkPolicy.REPLACE,
            refreshRequest
        )
    }

    companion object {
        private const val WORK_BOOT_REFRESH = "reminder_boot_refresh"
    }
}

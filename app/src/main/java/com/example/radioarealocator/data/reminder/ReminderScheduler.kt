package com.example.radioarealocator.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 提醒调度器：负责将每条 [ReminderItem] 注册到 [AlarmManager]。
 *
 * 调度策略：
 * - AlarmManager.setExactAndAllowWhileIdle 在 AOS 前 [ReminderSettings.leadMinutes] 分钟触发
 *   该 API 在 Doze 模式下仍会唤醒，保证精确提醒。
 * - WorkManager 周期任务（每日一次）负责重新预测过境并刷新闹钟，
 *   因为 TLE 数据每天漂移会导致过境时间变化。
 *
 * 注意：调度的时间是 AOS - leadMinutes。若该时间已过去则不调度。
 */
class ReminderScheduler(private val context: Context) {

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 调度单条提醒。若 settings.enabled=false 或 item.enabled=false 或触发时间已过去则跳过。
     *
     * @return true 表示已成功调度，false 表示跳过
     */
    fun schedule(item: ReminderItem, settings: ReminderSettings): Boolean {
        if (!settings.enabled || !item.enabled) {
            cancel(item.catalogNumber)
            return false
        }

        // 计算触发时间（AOS - leadMinutes），转换为毫秒
        val triggerAtMillis = item.aosTimeMillis - settings.leadMinutes * 60L * 1000L
        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) {
            // 触发时间已过去，不调度
            cancel(item.catalogNumber)
            return false
        }

        // 仅白天提醒：检查触发时间的小时是否在 6-22 之间
        if (settings.repeatMode == RepeatMode.DAYTIME_ONLY) {
            val hour = Instant.ofEpochMilli(triggerAtMillis)
                .atZone(ZoneId.systemDefault())
                .hour
            if (hour < 6 || hour >= 22) {
                cancel(item.catalogNumber)
                return false
            }
        }

        val intent = createReceiverIntent(item).apply {
            action = ReminderReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.catalogNumber,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            // Android 12+ 要求 SCHEDULE_EXACT_ALARM 或 USE_EXACT_ALARM 权限
            // 先主动检查权限状态，避免不必要的 SecurityException
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                // 用户未授予精确闹钟权限，直接使用非精确模式
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            // 用户未授予精确闹钟权限，回退到非精确闹钟
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
        return true
    }

    /**
     * 批量调度多个提醒。会先清除所有旧闹钟再重新注册。
     */
    fun scheduleAll(items: List<ReminderItem>, settings: ReminderSettings) {
        // 注意：无法枚举已注册的 PendingIntent，所以仅取消 items 中未启用的项
        items.forEach { item ->
            if (!settings.enabled || !item.enabled) {
                cancel(item.catalogNumber)
            }
        }
        // 调度启用的提醒
        items.forEach { item ->
            if (item.enabled && settings.enabled) {
                schedule(item, settings)
            }
        }
        // 启动每日刷新 Worker
        scheduleDailyRefresh()
        // 取消已不在 items 中的提醒（如取消收藏）由调用方在 toggleFavorite 中处理
    }

    /**
     * 取消指定卫星的提醒。
     */
    fun cancel(catalogNumber: Int) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            catalogNumber,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * 取消所有提醒。
     */
    fun cancelAll() {
        // 由于无法枚举已注册的 PendingIntent，仅能取消已知的
        // 实际取消依赖调用方传入 catalogNumber 列表
        // WorkManager 取消
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_DAILY_REFRESH)
    }

    /**
     * 启动或更新每日刷新的周期 Work。
     * 重复调用安全（ExistingPeriodicWorkPolicy.KEEP 保持已有任务，UPDATE 更新配置）。
     */
    fun scheduleDailyRefresh() {
        // 网络约束：Worker 需要下载 TLE 数据，仅在有网络时执行
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<ReminderRefreshWorker>(
            REPEAT_INTERVAL_HOURS, TimeUnit.HOURS,
            // flex period 让 Work 在每日的最后 1 小时内执行，减少电量消耗
            FLEX_PERIOD_HOURS, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_DAILY_REFRESH,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun createReceiverIntent(item: ReminderItem): Intent {
        return Intent(context, ReminderReceiver::class.java).apply {
            // 通过 extra 传递提醒详情，避免 Receiver 再次查 DB
            putExtra(ReminderReceiver.EXTRA_CATALOG, item.catalogNumber)
            putExtra(ReminderReceiver.EXTRA_NAME, item.name)
            putExtra(ReminderReceiver.EXTRA_AOS, item.aosTimeMillis)
            putExtra(ReminderReceiver.EXTRA_LOS, item.losTimeMillis)
            putExtra(ReminderReceiver.EXTRA_MAX_EL, item.maxElevation)
            putExtra(ReminderReceiver.EXTRA_AOS_AZ, item.aosAzimuth)
            putExtra(ReminderReceiver.EXTRA_LOS_AZ, item.losAzimuth)
            // modes 用 String 数组传递
            putExtra(ReminderReceiver.EXTRA_MODES, item.modes.toTypedArray())
        }
    }

    companion object {
        private const val WORK_DAILY_REFRESH = "reminder_daily_refresh"
        // WorkManager 最小周期为 15 分钟，但每日刷新足够（TLE 漂移每日约 1 分钟）
        private const val REPEAT_INTERVAL_HOURS = 24L
        private const val FLEX_PERIOD_HOURS = 1L
    }
}

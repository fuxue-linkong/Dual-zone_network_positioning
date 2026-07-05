package com.example.radioarealocator.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.radioarealocator.data.satellite.SatelliteInfo
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 系统日历操作工具。负责将卫星过境事件写入用户日历，并设置提前提醒。
 *
 * 使用 Android 标准 [CalendarContract] API，无需第三方依赖。
 * 要求调用方已获取 [Manifest.permission.WRITE_CALENDAR] 权限。
 */
object CalendarHelper {

    /** 提前提醒时间（分钟） */
    private const val REMINDER_MINUTES = 10

    /**
     * 检查是否已授予日历写入权限。
     */
    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 将单颗卫星的过境事件添加到系统日历。
     *
     * @param context 应用上下文
     * @param satellite 卫星过境信息
     * @return 事件 ID；失败（无权限、无可用日历、写入失败）返回 null
     */
    fun addPassEvent(context: Context, satellite: SatelliteInfo): Long? {
        if (!hasPermission(context)) return null

        val calendarId = getFirstWritableCalendar(context) ?: return null

        val zone = ZoneId.systemDefault()
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val aosLocal = satellite.aosTime.atZone(zone).format(timeFormatter)
        val losLocal = satellite.losTime.atZone(zone).format(timeFormatter)

        val title = "${satellite.name} 过境提醒"
        val description = buildString {
            append("卫星 ${satellite.name} 即将过境\n")
            append("入境(AOS): ${satellite.aosTime.atZone(zone).format(fullFormatter)}\n")
            append("出境(LOS): ${satellite.losTime.atZone(zone).format(fullFormatter)}\n")
            append("最大仰角: ${satellite.maxElevation.toInt()}°\n")
            append("入境方位角: ${satellite.aosAzimuth}°\n")
            append("出境方位角: ${satellite.losAzimuth}°\n")
            if (satellite.modes.isNotEmpty()) {
                append("工作模式: ${satellite.modes.joinToString(", ")}\n")
            }
            if (satellite.source.isNotEmpty()) {
                append("数据源: ${satellite.source}\n")
            }
            append("\n由双区网络定位应用自动添加")
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, satellite.aosTime.toEpochMilli())
            put(CalendarContract.Events.DTEND, satellite.losTime.toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }

        val uri = context.contentResolver.insert(
            CalendarContract.Events.CONTENT_URI, values
        ) ?: return null

        val eventId = ContentUris.parseId(uri)

        // 添加提前 10 分钟的提醒
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, REMINDER_MINUTES)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(
            CalendarContract.Reminders.CONTENT_URI, reminderValues
        )

        return eventId
    }

    /**
     * 查询第一个可写的日历账户。优先 owner 级别，其次 contributor。
     */
    private fun getFirstWritableCalendar(context: Context): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.OWNER_ACCOUNT
        )

        // 优先查找 owner 级别的日历
        val ownerSelection =
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_OWNER}"

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            ownerSelection,
            null,
            "${CalendarContract.Calendars._ID} ASC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }

        // 退而求其次：查找 contributor 级别（可写入）的日历
        val contributorSelection =
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            contributorSelection,
            null,
            "${CalendarContract.Calendars._ID} ASC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }

        return null
    }
}

package com.example.radioarealocator.data.satellite

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.GlobalScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe
import java.time.Instant

/**
 * [SatelliteStatusTracker] 单元测试：
 * - [SatelliteStatusTracker.absoluteSlot] 时间槽计算
 * - [SatelliteStatusTracker.queryStatus] 含延续逻辑的状态查询
 * - [SatelliteStatusTracker.queryRecentStatus] 严格 15 分钟窗口查询
 */
class SatelliteStatusTrackerTest {

    // ── absoluteSlot 计算 ──

    @Test
    fun `absoluteSlot - epoch zero is slot 0`() {
        assertEquals(0L, SatelliteStatusTracker.absoluteSlot(Instant.EPOCH))
    }

    @Test
    fun `absoluteSlot - exactly one slot after epoch is slot 1`() {
        assertEquals(1L, SatelliteStatusTracker.absoluteSlot(Instant.ofEpochSecond(900)))
    }

    @Test
    fun `absoluteSlot - 899 seconds is still slot 0`() {
        assertEquals(0L, SatelliteStatusTracker.absoluteSlot(Instant.ofEpochSecond(899)))
    }

    @Test
    fun `absoluteSlot - known UTC instant maps to expected slot`() {
        // 使用 Instant 自身的 epochSecond 计算期望值，避免硬编码错误
        val instant = Instant.parse("2026-07-25T12:00:00Z")
        val expectedSlot = instant.epochSecond / 900L
        assertEquals(expectedSlot, SatelliteStatusTracker.absoluteSlot(instant))
    }

    @Test
    fun `absoluteSlot - 15 minutes apart differ by exactly 1`() {
        val t1 = Instant.parse("2026-07-25T12:00:00Z")
        val t2 = Instant.parse("2026-07-25T12:15:00Z")
        assertEquals(1L, SatelliteStatusTracker.absoluteSlot(t2) - SatelliteStatusTracker.absoluteSlot(t1))
    }

    @Test
    fun `absoluteSlot - same instant always returns same slot`() {
        val instant = Instant.parse("2026-01-01T00:00:00Z")
        val slot1 = SatelliteStatusTracker.absoluteSlot(instant)
        val slot2 = SatelliteStatusTracker.absoluteSlot(instant)
        assertEquals(slot1, slot2)
    }

    // ── queryStatus 延续逻辑 ──

    /**
     * 使用 Unsafe.allocateInstance 创建 SatelliteStatusTracker 实例，
     * 绕过构造函数（避免 OkHttpClient/Android 依赖初始化）。
     * 仅设置 [_statusMap] 字段，其他字段不影响 queryStatus/queryRecentStatus。
     */
    @Suppress("UNCHECKED_CAST")
    private fun createTrackerWithStatusMap(
        map: Map<String, SatelliteStatusEntry>
    ): SatelliteStatusTracker {
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").let {
            it.isAccessible = true
            it.get(null) as Unsafe
        }
        val tracker = unsafe.allocateInstance(SatelliteStatusTracker::class.java)
            as SatelliteStatusTracker

        // 设置 _statusMap（Compose MutableState）
        val statusMapField = SatelliteStatusTracker::class.java.getDeclaredField("_statusMap")
        statusMapField.isAccessible = true
        val state = mutableStateOf(map)
        statusMapField.set(tracker, state)

        // 设置 scope（start() 才用，query 不用，但字段需非 null）
        val scopeField = SatelliteStatusTracker::class.java.getDeclaredField("scope")
        scopeField.isAccessible = true
        scopeField.set(tracker, GlobalScope)

        return tracker
    }

    @Test
    fun `queryStatus - returns null for unknown satellite`() {
        val tracker = createTrackerWithStatusMap(emptyMap())
        assertNull(tracker.queryStatus("ISS"))
    }

    @Test
    fun `queryStatus - returns real-time status when in current slot`() {
        val now = Instant.now()
        val currentSlot = SatelliteStatusTracker.absoluteSlot(now)
        val tracker = createTrackerWithStatusMap(mapOf(
            "ISS" to SatelliteStatusEntry("Heard", now, currentSlot)
        ))
        val result = tracker.queryStatus("ISS")
        assertNotNull(result)
        assertEquals("Heard", result!!.status)
        assertFalse("当前槽报告不应标记为延续", result.isInherited)
    }

    @Test
    fun `queryStatus - returns inherited status when report is from previous slot`() {
        val now = Instant.now()
        val currentSlot = SatelliteStatusTracker.absoluteSlot(now)
        val tracker = createTrackerWithStatusMap(mapOf(
            "ISS" to SatelliteStatusEntry("Telemetry Only", now.minusSeconds(900), currentSlot - 1)
        ))
        val result = tracker.queryStatus("ISS")
        assertNotNull(result)
        assertEquals("Telemetry Only", result!!.status)
        assertTrue("过期槽报告应标记为延续", result.isInherited)
    }

    @Test
    fun `queryStatus - returns null when report is older than 24h (96 slots)`() {
        val now = Instant.now()
        val currentSlot = SatelliteStatusTracker.absoluteSlot(now)
        val tracker = createTrackerWithStatusMap(mapOf(
            "ISS" to SatelliteStatusEntry("Heard", now.minusSeconds(96 * 900), currentSlot - 96)
        ))
        assertNull(tracker.queryStatus("ISS"))
    }

    @Test
    fun `queryStatus - returns status when report is from 95 slots ago (just under 24h)`() {
        val now = Instant.now()
        val currentSlot = SatelliteStatusTracker.absoluteSlot(now)
        val tracker = createTrackerWithStatusMap(mapOf(
            "ISS" to SatelliteStatusEntry("Not Heard", now.minusSeconds(95 * 900), currentSlot - 95)
        ))
        val result = tracker.queryStatus("ISS")
        assertNotNull(result)
        assertEquals("Not Heard", result!!.status)
        assertTrue(result.isInherited)
    }

    @Test
    fun `queryStatus - handles clock skew (diff negative) as current slot`() {
        val now = Instant.now()
        val currentSlot = SatelliteStatusTracker.absoluteSlot(now)
        val tracker = createTrackerWithStatusMap(mapOf(
            "ISS" to SatelliteStatusEntry("Heard", now.plusSeconds(900), currentSlot + 1)
        ))
        val result = tracker.queryStatus("ISS")
        assertNotNull(result)
        assertEquals("Heard", result!!.status)
        assertFalse("时钟回退应视为当前槽，不标记延续", result.isInherited)
    }

    @Test
    fun `queryStatus - multiple satellites queried independently`() {
        val now = Instant.now()
        val currentSlot = SatelliteStatusTracker.absoluteSlot(now)
        val tracker = createTrackerWithStatusMap(mapOf(
            "ISS" to SatelliteStatusEntry("Heard", now, currentSlot),
            "AO-73" to SatelliteStatusEntry("Telemetry Only", now.minusSeconds(1800), currentSlot - 2)
        ))
        val iss = tracker.queryStatus("ISS")
        val ao73 = tracker.queryStatus("AO-73")
        assertNotNull(iss)
        assertNotNull(ao73)
        assertFalse(iss!!.isInherited)
        assertTrue(ao73!!.isInherited)
    }

    // ── queryRecentStatus 严格窗口 ──

    @Test
    fun `queryRecentStatus - returns null for unknown satellite`() {
        val tracker = createTrackerWithStatusMap(emptyMap())
        assertNull(tracker.queryRecentStatus("ISS"))
    }

    @Test
    fun `queryRecentStatus - returns status only in current slot`() {
        val now = Instant.now()
        val currentSlot = SatelliteStatusTracker.absoluteSlot(now)
        val tracker = createTrackerWithStatusMap(mapOf(
            "ISS" to SatelliteStatusEntry("Heard", now, currentSlot)
        ))
        val result = tracker.queryRecentStatus("ISS")
        assertNotNull(result)
        assertEquals("Heard", result!!.status)
        assertFalse(result.isInherited)
    }

    @Test
    fun `queryRecentStatus - returns null when report is from previous slot`() {
        val now = Instant.now()
        val currentSlot = SatelliteStatusTracker.absoluteSlot(now)
        val tracker = createTrackerWithStatusMap(mapOf(
            "ISS" to SatelliteStatusEntry("Heard", now.minusSeconds(900), currentSlot - 1)
        ))
        assertNull(tracker.queryRecentStatus("ISS"))
    }

    @Test
    fun `queryRecentStatus - returns null when report is from next slot (clock skew)`() {
        val now = Instant.now()
        val currentSlot = SatelliteStatusTracker.absoluteSlot(now)
        val tracker = createTrackerWithStatusMap(mapOf(
            "ISS" to SatelliteStatusEntry("Heard", now.plusSeconds(900), currentSlot + 1)
        ))
        assertNull(tracker.queryRecentStatus("ISS"))
    }
}

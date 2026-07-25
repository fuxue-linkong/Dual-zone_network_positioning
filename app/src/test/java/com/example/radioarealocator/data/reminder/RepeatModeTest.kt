package com.example.radioarealocator.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RepeatMode.fromName] 反序列化测试。
 *
 * 该方法从 SharedPreferences 存储的字符串恢复枚举值，
 * 需覆盖正常值、null、空字符串、未知字符串等边界情况。
 */
class RepeatModeTest {

    @Test
    fun `fromName - ALWAYS returns ALWAYS`() {
        assertEquals(RepeatMode.ALWAYS, RepeatMode.fromName("ALWAYS"))
    }

    @Test
    fun `fromName - DAYTIME_ONLY returns DAYTIME_ONLY`() {
        assertEquals(RepeatMode.DAYTIME_ONLY, RepeatMode.fromName("DAYTIME_ONLY"))
    }

    @Test
    fun `fromName - null defaults to ALWAYS`() {
        assertEquals(RepeatMode.ALWAYS, RepeatMode.fromName(null))
    }

    @Test
    fun `fromName - empty string defaults to ALWAYS`() {
        assertEquals(RepeatMode.ALWAYS, RepeatMode.fromName(""))
    }

    @Test
    fun `fromName - unknown string defaults to ALWAYS`() {
        assertEquals(RepeatMode.ALWAYS, RepeatMode.fromName("UNKNOWN_MODE"))
    }

    @Test
    fun `fromName - case sensitive - lowercase returns default`() {
        assertEquals(RepeatMode.ALWAYS, RepeatMode.fromName("always"))
    }

    @Test
    fun `fromName - whitespace string returns default`() {
        assertEquals(RepeatMode.ALWAYS, RepeatMode.fromName("  "))
    }
}

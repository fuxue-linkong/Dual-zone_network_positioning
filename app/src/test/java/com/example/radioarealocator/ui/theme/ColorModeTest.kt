package com.example.radioarealocator.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ColorMode] 单元测试。
 *
 * 覆盖：
 * - 枚举值与预期整数映射一致
 * - isSystem / isDark / isAmoled / isMonet 属性判断正确
 * - toNonMonetMode / toMonetMode 转换逻辑正确
 * - fromValue 对未知值返回 SYSTEM 默认值
 */
class ColorModeTest {

    @Test
    fun `enum values have correct integer mappings`() {
        assertEquals(0, ColorMode.SYSTEM.value)
        assertEquals(1, ColorMode.LIGHT.value)
        assertEquals(2, ColorMode.DARK.value)
        assertEquals(3, ColorMode.MONET_SYSTEM.value)
        assertEquals(4, ColorMode.MONET_LIGHT.value)
        assertEquals(5, ColorMode.MONET_DARK.value)
        assertEquals(6, ColorMode.DARK_AMOLED.value)
    }

    @Test
    fun `isSystem returns true for system-following modes`() {
        assertTrue(ColorMode.SYSTEM.isSystem)
        assertTrue(ColorMode.MONET_SYSTEM.isSystem)
        assertFalse(ColorMode.LIGHT.isSystem)
        assertFalse(ColorMode.DARK.isSystem)
        assertFalse(ColorMode.MONET_LIGHT.isSystem)
        assertFalse(ColorMode.MONET_DARK.isSystem)
        assertFalse(ColorMode.DARK_AMOLED.isSystem)
    }

    @Test
    fun `isDark returns true for dark modes`() {
        assertTrue(ColorMode.DARK.isDark)
        assertTrue(ColorMode.MONET_DARK.isDark)
        assertTrue(ColorMode.DARK_AMOLED.isDark)
        assertFalse(ColorMode.SYSTEM.isDark)
        assertFalse(ColorMode.LIGHT.isDark)
        assertFalse(ColorMode.MONET_SYSTEM.isDark)
        assertFalse(ColorMode.MONET_LIGHT.isDark)
    }

    @Test
    fun `isAmoled returns true only for DARK_AMOLED`() {
        assertTrue(ColorMode.DARK_AMOLED.isAmoled)
        assertFalse(ColorMode.SYSTEM.isAmoled)
        assertFalse(ColorMode.DARK.isAmoled)
        assertFalse(ColorMode.MONET_DARK.isAmoled)
    }

    @Test
    fun `isMonet returns true for monet modes`() {
        assertTrue(ColorMode.MONET_SYSTEM.isMonet)
        assertTrue(ColorMode.MONET_LIGHT.isMonet)
        assertTrue(ColorMode.MONET_DARK.isMonet)
        assertTrue(ColorMode.DARK_AMOLED.isMonet)
        assertFalse(ColorMode.SYSTEM.isMonet)
        assertFalse(ColorMode.LIGHT.isMonet)
        assertFalse(ColorMode.DARK.isMonet)
    }

    @Test
    fun `toNonMonetMode converts monet to non-monet`() {
        assertEquals(0, ColorMode.MONET_SYSTEM.toNonMonetMode())
        assertEquals(1, ColorMode.MONET_LIGHT.toNonMonetMode())
        assertEquals(2, ColorMode.MONET_DARK.toNonMonetMode())
        assertEquals(2, ColorMode.DARK_AMOLED.toNonMonetMode())
    }

    @Test
    fun `toNonMonetMode leaves non-monet unchanged`() {
        assertEquals(0, ColorMode.SYSTEM.toNonMonetMode())
        assertEquals(1, ColorMode.LIGHT.toNonMonetMode())
        assertEquals(2, ColorMode.DARK.toNonMonetMode())
    }

    @Test
    fun `toMonetMode converts non-monet to monet`() {
        assertEquals(3, ColorMode.SYSTEM.toMonetMode())
        assertEquals(4, ColorMode.LIGHT.toMonetMode())
        assertEquals(5, ColorMode.DARK.toMonetMode())
    }

    @Test
    fun `toMonetMode leaves monet unchanged`() {
        assertEquals(3, ColorMode.MONET_SYSTEM.toMonetMode())
        assertEquals(4, ColorMode.MONET_LIGHT.toMonetMode())
        assertEquals(5, ColorMode.MONET_DARK.toMonetMode())
        assertEquals(6, ColorMode.DARK_AMOLED.toMonetMode())
    }

    @Test
    fun `fromValue returns correct enum for valid values`() {
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(0))
        assertEquals(ColorMode.LIGHT, ColorMode.fromValue(1))
        assertEquals(ColorMode.DARK, ColorMode.fromValue(2))
        assertEquals(ColorMode.MONET_SYSTEM, ColorMode.fromValue(3))
        assertEquals(ColorMode.MONET_LIGHT, ColorMode.fromValue(4))
        assertEquals(ColorMode.MONET_DARK, ColorMode.fromValue(5))
        assertEquals(ColorMode.DARK_AMOLED, ColorMode.fromValue(6))
    }

    @Test
    fun `fromValue returns SYSTEM for unknown values`() {
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(-1))
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(99))
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(Int.MAX_VALUE))
    }

    @Test
    fun `round trip non-monet to monet and back`() {
        ColorMode.entries.filter { !it.isMonet }.forEach { mode ->
            assertEquals(
                "Round trip failed for $mode",
                mode.value,
                ColorMode.fromValue(mode.toMonetMode()).toNonMonetMode()
            )
        }
    }
}

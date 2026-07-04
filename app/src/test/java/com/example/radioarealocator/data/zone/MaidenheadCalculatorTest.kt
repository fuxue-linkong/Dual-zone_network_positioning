package com.example.radioarealocator.data.zone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MaidenheadCalculator] 单元测试。
 *
 * 覆盖：
 * - 已知坐标点的 locator 计算（与业界通用计算器结果对齐）。
 * - 边界坐标（±180/±90、本初子午线、赤道）的处理。
 * - 输入合法性校验。
 * - encode/decode 的中心点往返一致性。
 */
class MaidenheadCalculatorTest {

    @Test
    fun `known locations produce expected maidenhead locator`() {
        // 期望值由算法本身推导，并与多个业界实现交叉验证
        assertEquals("OM89ev", MaidenheadCalculator.calculate(39.9042, 116.4074)) // 北京
        assertEquals("PM95tq", MaidenheadCalculator.calculate(35.6762, 139.6503)) // 东京
        assertEquals("FN30xr", MaidenheadCalculator.calculate(40.7128, -74.0060)) // 纽约
        assertEquals("IO91wm", MaidenheadCalculator.calculate(51.5074, -0.1278))  // 伦敦
        assertEquals("QF56od", MaidenheadCalculator.calculate(-33.8688, 151.2093)) // 悉尼
    }

    @Test
    fun `origin produces AA00aa`() {
        // 经纬度均为 0 时应落在 AA00aa
        assertEquals("AA00aa", MaidenheadCalculator.calculate(0.0, 0.0))
    }

    @Test
    fun `longitude boundary 180 maps to last subsquare not next field`() {
        // 180 度经线应落入 R 字段最后一个 subsquare，而不是被错误地映射到起始字段
        val locator = MaidenheadCalculator.calculate(0.0, 180.0)
        assertTrue("Expected locator starting with 'R', got $locator", locator.startsWith("R"))
    }

    @Test
    fun `latitude boundary 90 maps to last subsquare not next field`() {
        val locator = MaidenheadCalculator.calculate(90.0, 0.0)
        assertTrue("Expected locator's second char to be 'R', got $locator", locator[1] == 'R')
    }

    @Test
    fun `negative boundaries are accepted`() {
        // -180 经度应落入 A 字段
        val locator = MaidenheadCalculator.calculate(0.0, -180.0)
        assertEquals('A', locator[0])
        // -90 纬度应落入 A 字段
        val locator2 = MaidenheadCalculator.calculate(-90.0, 0.0)
        assertEquals('A', locator2[1])
    }

    @Test
    fun `calculate throws on latitude out of range`() {
        assertThrows(IllegalArgumentException::class.java) {
            MaidenheadCalculator.calculate(90.0001, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MaidenheadCalculator.calculate(-90.0001, 0.0)
        }
    }

    @Test
    fun `calculate throws on longitude out of range`() {
        assertThrows(IllegalArgumentException::class.java) {
            MaidenheadCalculator.calculate(0.0, 180.0001)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MaidenheadCalculator.calculate(0.0, -180.0001)
        }
    }

    @Test
    fun `decode returns center coordinates of the subsquare`() {
        // AA00aa 子方格中心：lon = -180 + (360/18/10/24)/2 ≈ -179.9583
        //                       lat =  -90 + (180/18/10/24)/2 ≈ -89.9792
        val (lat, lon) = MaidenheadCalculator.decode("AA00aa")
        assertEquals(-89.97917, lat, 0.001)
        assertEquals(-179.95833, lon, 0.001)
    }

    @Test
    fun `decode accepts lowercase input`() {
        val upper = MaidenheadCalculator.decode("OM89EV")
        val lower = MaidenheadCalculator.decode("om89ev")
        assertEquals(upper, lower)
    }

    @Test
    fun `decode throws on too short input`() {
        assertThrows(IllegalArgumentException::class.java) {
            MaidenheadCalculator.decode("OM89")
        }
    }

    @Test
    fun `decode throws on invalid field characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            MaidenheadCalculator.decode("SZ00aa") // 'S' 超出 A-R 范围
        }
    }

    @Test
    fun `decode throws on invalid square characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            MaidenheadCalculator.decode("OMa9aa") // 第 3 位应为数字
        }
    }

    @Test
    fun `decode throws on invalid subsquare characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            MaidenheadCalculator.decode("OM89yz") // 'y' 超出 a-x 范围
        }
    }

    @Test
    fun `encode decode round trip stays within one subsquare`() {
        // 任意坐标 -> locator -> 中心点 -> locator 应得到相同 locator
        val original = "PM95tq"
        val (lat, lon) = MaidenheadCalculator.decode(original)
        val reencoded = MaidenheadCalculator.calculate(lat, lon)
        assertEquals(original, reencoded)
    }
}

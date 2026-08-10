package com.example.radioarealocator.data.satellite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DopplerCalculator] 单元测试。
 *
 * 验证依据：Δf = -f0·v/c。已知量：145.8 MHz、接近速度 7500 m/s
 * （v = -7500）→ Δf ≈ +3647 Hz（145800000 × 7500 / 299792458 ≈ 3646.52）。
 */
class DopplerCalculatorTest {

    private val c = DopplerCalculator.SPEED_OF_LIGHT_MPS

    @Test
    fun `dopplerShiftHz - approaching satellite yields positive shift about 3647 Hz`() {
        val shift = DopplerCalculator.dopplerShiftHz(145_800_000.0, -7500.0)
        assertEquals("145.8MHz 接近 7500m/s 应约 +3647Hz，实际 $shift", 3647.523, shift, 1.0)
    }

    @Test
    fun `dopplerShiftHz - receding satellite yields negative shift`() {
        val shift = DopplerCalculator.dopplerShiftHz(145_800_000.0, 7500.0)
        assertTrue("远离时频移应为负，实际 $shift", shift < 0)
        assertEquals(3647.523, -shift, 1.0)
    }

    @Test
    fun `dopplerShiftHz - zero range rate yields zero shift`() {
        assertEquals(0.0, DopplerCalculator.dopplerShiftHz(145_800_000.0, 0.0), 1e-9)
    }

    @Test
    fun `dopplerShiftHz - exact formula delta-f equals -f0 times v over c`() {
        val f0 = 435_000_000.0
        val v = 1234.5
        val shift = DopplerCalculator.dopplerShiftHz(f0, v)
        assertEquals(-f0 * v / c, shift, 1e-6)
    }

    @Test
    fun `dopplerFrequencies - both bands shifted by same amount`() {
        val downlink = 145_800_000.0
        val uplink = 435_000_000.0
        val v = -7500.0
        val (downLive, upLive) = DopplerCalculator.dopplerFrequencies(downlink, uplink, v)

        val expectedDown = downlink - downlink * v / c
        val expectedUp = uplink - uplink * v / c
        assertEquals(expectedDown, downLive, 1e-3)
        assertEquals(expectedUp, upLive, 1e-3)
        assertTrue("接近时下行实时频率应高于基频", downLive > downlink)
        assertTrue("接近时上行实时频率应高于基频", upLive > uplink)
    }

    @Test
    fun `dopplerFrequencies - receding lowers both frequencies`() {
        val (downLive, upLive) = DopplerCalculator.dopplerFrequencies(
            145_800_000.0, 435_000_000.0, 7500.0
        )
        assertTrue(downLive < 145_800_000.0)
        assertTrue(upLive < 435_000_000.0)
    }
}

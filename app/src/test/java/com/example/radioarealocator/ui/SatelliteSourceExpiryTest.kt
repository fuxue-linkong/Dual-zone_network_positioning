package com.example.radioarealocator.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * [isSatelliteSourceExpired] 单元测试。
 */
class SatelliteSourceExpiryTest {

    @Test
    fun `null lastUpdate is always expired`() {
        assertTrue(isSatelliteSourceExpired(null))
    }

    @Test
    fun `recent update is not expired`() {
        val now = Instant.now()
        val recent = now.minus(1, ChronoUnit.HOURS)
        assertFalse(isSatelliteSourceExpired(recent, now))
    }

    @Test
    fun `update exactly 24 hours ago is expired`() {
        val now = Instant.now()
        val exactly24h = now.minus(24, ChronoUnit.HOURS)
        assertTrue(isSatelliteSourceExpired(exactly24h, now))
    }

    @Test
    fun `update older than 24 hours is expired`() {
        val now = Instant.now()
        val old = now.minus(48, ChronoUnit.HOURS)
        assertTrue(isSatelliteSourceExpired(old, now))
    }

    @Test
    fun `update 23 hours ago is not expired`() {
        val now = Instant.now()
        val recent = now.minus(23, ChronoUnit.HOURS)
        assertFalse(isSatelliteSourceExpired(recent, now))
    }

    @Test
    fun `update 23 hours 59 minutes ago is not expired`() {
        val now = Instant.now()
        val recent = now.minus(23, ChronoUnit.HOURS).minus(59, ChronoUnit.MINUTES)
        assertFalse(isSatelliteSourceExpired(recent, now))
    }

    @Test
    fun `future update is not expired`() {
        val now = Instant.now()
        val future = now.plus(1, ChronoUnit.HOURS)
        assertFalse(isSatelliteSourceExpired(future, now))
    }
}

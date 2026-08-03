package com.example.radioarealocator

import com.example.radioarealocator.data.aprs.AprsPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AprsPacketTest {

    @Test
    fun `passcode calculation is correct for known callsigns`() {
        // APRS-IS passcode algorithm: XOR-based hash
        val code1 = AprsPacket.passcode("N0CALL")
        assertTrue("Passcode should be positive", code1 > 0)
        assertTrue("Passcode should be <= 32767", code1 <= 32767)

        // Same callsign should produce same passcode
        assertEquals(code1, AprsPacket.passcode("N0CALL"))
    }

    @Test
    fun `passcode handles ssid correctly`() {
        val withoutSsid = AprsPacket.passcode("N0CALL")
        val withSsid = AprsPacket.passcode("N0CALL-1")
        assertEquals(withoutSsid, withSsid)
    }

    @Test
    fun `passcode handles lowercase`() {
        assertEquals(AprsPacket.passcode("N0CALL"), AprsPacket.passcode("n0call"))
    }

    @Test
    fun `formatCallSsid returns correct format`() {
        assertEquals("N0CALL-7", AprsPacket.formatCallSsid("N0CALL", "7"))
        assertEquals("N0CALL", AprsPacket.formatCallSsid("N0CALL", ""))
        assertEquals("N0CALL", AprsPacket.formatCallSsid("N0CALL", null))
    }

    @Test
    fun `formatLogin produces correct login string`() {
        val login = AprsPacket.formatLogin("N0CALL", "7", "12345", "TestApp-1.0")
        assertTrue(login.contains("user N0CALL-7"))
        assertTrue(login.contains("pass 12345"))
        assertTrue(login.contains("vers TestApp-1.0"))
    }

    @Test
    fun `formatPosition produces correct uncompressed format`() {
        val position = AprsPacket.formatPosition(
            latitude = 40.7128,
            longitude = -74.0060,
            symbolTable = '/',
            symbolCode = '>',
            comment = "Test",
            compressed = false
        )
        assertTrue(position.startsWith("!"))
        assertTrue(position.contains("40"))
        assertTrue(position.contains("074"))
        assertTrue(position.contains(">"))
        assertTrue(position.contains("Test"))
    }

    @Test
    fun `formatMessage produces correct format`() {
        val message = AprsPacket.formatMessage("N0CALL", "W1AW", "Hello", "123")
        assertTrue(message.startsWith(":W1AW     :Hello"))
        assertTrue(message.contains("{123}"))
    }

    @Test
    fun `formatMessage without msgNumber produces correct format`() {
        val message = AprsPacket.formatMessage("N0CALL", "W1AW", "Hello")
        assertEquals(":W1AW     :Hello", message)
    }

    @Test
    fun `parseHostPort extracts host and port`() {
        val (host1, port1) = AprsPacket.parseHostPort("euro.aprs2.net:14580", 14580)
        assertEquals("euro.aprs2.net", host1)
        assertEquals(14580, port1)

        val (host2, port2) = AprsPacket.parseHostPort("rotate.aprs.net", 14580)
        assertEquals("rotate.aprs.net", host2)
        assertEquals(14580, port2)
    }

    @Test
    fun `formatCompressedPosition produces valid compressed string`() {
        val compressed = AprsPacket.formatPosition(
            latitude = 51.5074,
            longitude = -0.1278,
            symbolTable = '/',
            symbolCode = '>',
            comment = "London",
            compressed = true
        )
        assertTrue("Should start with !", compressed.startsWith("!"))
        assertTrue("Compressed should be at least 13 chars", compressed.length >= 13)
    }

    @Test
    fun `parseQrg extracts frequency from comment`() {
        assertEquals("146.520", AprsPacket.parseQrg("Freq 146.520 MHz"))
        assertEquals("446.000", AprsPacket.parseQrg("Output: 446.000"))
        assertEquals(null, AprsPacket.parseQrg("No frequency here"))
    }
}

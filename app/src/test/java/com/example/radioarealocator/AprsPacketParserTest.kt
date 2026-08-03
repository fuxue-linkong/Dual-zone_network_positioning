package com.example.radioarealocator

import com.example.radioarealocator.data.aprs.AprsPacketParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AprsPacketParserTest {

    @Test
    fun `parse uncompressed position packet`() {
        val packet = "N0CALL>APRLAR:!4903.50N/07201.75W-Test message"
        val parsed = AprsPacketParser.parse(packet)

        assertTrue(parsed is AprsPacketParser.ParsedPosition)
        val pos = parsed as AprsPacketParser.ParsedPosition
        assertEquals("N0CALL", pos.source)
        assertEquals(49 + 3.50 / 60.0, pos.latitude, 0.001)
        assertEquals(-(72 + 1.75 / 60.0), pos.longitude, 0.001)
        assertEquals('/', pos.symbolTable)
        assertEquals('-', pos.symbolCode)
    }

    @Test
    fun `parse message packet`() {
        val packet = "W1AW>APRLAR::N0CALL   :Hello World{123"
        val parsed = AprsPacketParser.parse(packet)

        assertTrue(parsed is AprsPacketParser.ParsedMessage)
        val msg = parsed as AprsPacketParser.ParsedMessage
        assertEquals("W1AW", msg.source)
        assertEquals("N0CALL", msg.destination)
        assertEquals("Hello World", msg.body)
        assertEquals("123", msg.msgNumber)
    }

    @Test
    fun `parse message with ack`() {
        val packet = "W1AW>APRLAR::N0CALL   :ack456"
        val parsed = AprsPacketParser.parse(packet) as AprsPacketParser.ParsedMessage

        assertTrue(parsed.isAck)
    }

    @Test
    fun `parse message without msgNumber`() {
        val packet = "W1AW>APRLAR::N0CALL   :Hello"
        val parsed = AprsPacketParser.parse(packet) as AprsPacketParser.ParsedMessage

        assertEquals("Hello", parsed.body)
        assertEquals(null, parsed.msgNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse empty payload throws exception`() {
        AprsPacketParser.parse("N0CALL>APRLAR:")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse unknown data type throws exception`() {
        AprsPacketParser.parse("N0CALL>APRLAR:Xinvalid")
    }
}

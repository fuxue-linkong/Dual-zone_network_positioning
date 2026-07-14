package com.example.radioarealocator.data.cw

import org.junit.Assert.assertEquals
import org.junit.Test

class MorseCodeGeneratorTest {
    @Test
    fun `test generate morse code for letter A`() {
        val generator = MorseCodeGenerator()
        val result = generator.toMorseCode("A")
        assertEquals(".-", result)
    }

    @Test
    fun `test generate morse code for word HI`() {
        val generator = MorseCodeGenerator()
        val result = generator.toMorseCode("HI")
        assertEquals(".... ..", result)
    }

    @Test
    fun `test generate random characters`() {
        val generator = MorseCodeGenerator()
        val result = generator.generateRandomCharacters(CharacterSet.LETTERS, 10)
        assertEquals(10, result.length)
    }
}
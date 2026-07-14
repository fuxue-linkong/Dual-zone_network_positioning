package com.example.radioarealocator.data.cw

import org.junit.Assert.assertEquals
import org.junit.Test

class CWSettingsStoreTest {
    @Test
    fun `test default CWSettings values`() {
        val settings = CWSettings()
        assertEquals(15, settings.wpm)
        assertEquals(600, settings.frequency)
        assertEquals(CharacterSet.LETTERS, settings.characterSet)
        assertEquals(100, settings.practiceLength)
        assertEquals(5, settings.practiceDuration)
        assertEquals(PlayMode.CONTINUOUS, settings.playMode)
    }

    @Test
    fun `test CWSettings with custom values`() {
        val settings = CWSettings(
            wpm = 20,
            frequency = 700,
            characterSet = CharacterSet.NUMBERS,
            practiceLength = 50,
            practiceDuration = 10,
            playMode = PlayMode.INTERVAL
        )
        assertEquals(20, settings.wpm)
        assertEquals(700, settings.frequency)
        assertEquals(CharacterSet.NUMBERS, settings.characterSet)
        assertEquals(50, settings.practiceLength)
        assertEquals(10, settings.practiceDuration)
        assertEquals(PlayMode.INTERVAL, settings.playMode)
    }

    @Test
    fun `test CharacterSet enum values`() {
        val values = CharacterSet.values()
        assertEquals(4, values.size)
        assertEquals(CharacterSet.LETTERS, values[0])
        assertEquals(CharacterSet.NUMBERS, values[1])
        assertEquals(CharacterSet.SYMBOLS, values[2])
        assertEquals(CharacterSet.CUSTOM, values[3])
    }

    @Test
    fun `test PlayMode enum values`() {
        val values = PlayMode.values()
        assertEquals(2, values.size)
        assertEquals(PlayMode.CONTINUOUS, values[0])
        assertEquals(PlayMode.INTERVAL, values[1])
    }

    @Test
    fun `test CWSettings copy with modified wpm`() {
        val original = CWSettings()
        val modified = original.copy(wpm = 25)
        assertEquals(25, modified.wpm)
        assertEquals(original.frequency, modified.frequency)
        assertEquals(original.characterSet, modified.characterSet)
    }
}

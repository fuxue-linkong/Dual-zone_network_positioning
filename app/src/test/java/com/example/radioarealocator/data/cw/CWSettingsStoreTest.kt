package com.example.radioarealocator.data.cw

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class CWSettingsStoreTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: CWSettingsStore
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        tempFile = File.createTempFile("test_cw_settings", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create {
            tempFile
        }
        store = CWSettingsStore.createForTest(dataStore)
    }

    @After
    fun tearDown() {
        tempFile.delete()
        tempFile.parentFile?.listFiles { _, name ->
            name.startsWith("test_cw_settings")
        }?.forEach { it.delete() }
    }

    @Test
    fun `settingsFlow emits default values when no data saved`() = runBlocking {
        val settings = store.settingsFlow.first()

        assertEquals(15, settings.wpm)
        assertEquals(600, settings.frequency)
        assertEquals(CharacterSet.LETTERS, settings.characterSet)
        assertEquals(100, settings.practiceLength)
        assertEquals(5, settings.practiceDuration)
        assertEquals(PlayMode.CONTINUOUS, settings.playMode)
    }

    @Test
    fun `updateSettings persists and settingsFlow reads back`() = runBlocking {
        val customSettings = CWSettings(
            wpm = 25,
            frequency = 750,
            characterSet = CharacterSet.NUMBERS,
            practiceLength = 200,
            practiceDuration = 10,
            playMode = PlayMode.INTERVAL
        )

        store.updateSettings(customSettings)
        val result = store.settingsFlow.first()

        assertEquals(25, result.wpm)
        assertEquals(750, result.frequency)
        assertEquals(CharacterSet.NUMBERS, result.characterSet)
        assertEquals(200, result.practiceLength)
        assertEquals(10, result.practiceDuration)
        assertEquals(PlayMode.INTERVAL, result.playMode)
    }

    @Test
    fun `multiple updates retain the latest values`() = runBlocking {
        store.updateSettings(CWSettings(wpm = 10))
        store.updateSettings(CWSettings(wpm = 30))
        store.updateSettings(CWSettings(wpm = 45))

        val result = store.settingsFlow.first()
        assertEquals(45, result.wpm)
    }

    @Test
    fun `updateSettings only changes specified fields`() = runBlocking {
        store.updateSettings(CWSettings(wpm = 20, frequency = 800))

        val result = store.settingsFlow.first()
        assertEquals(20, result.wpm)
        assertEquals(800, result.frequency)
        assertEquals(CharacterSet.LETTERS, result.characterSet)
        assertEquals(100, result.practiceLength)
        assertEquals(5, result.practiceDuration)
        assertEquals(PlayMode.CONTINUOUS, result.playMode)
    }

    @Test
    fun `settingsFlow persists across new store instances`() = runBlocking {
        store.updateSettings(CWSettings(wpm = 35, frequency = 500))

        val newStore = CWSettingsStore.createForTest(dataStore)
        val result = newStore.settingsFlow.first()

        assertEquals(35, result.wpm)
        assertEquals(500, result.frequency)
    }
}

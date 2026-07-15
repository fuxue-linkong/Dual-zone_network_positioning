package com.example.radioarealocator.data.cw

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.cwSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "cw_settings")

class CWSettingsStore private constructor(
    private val dataStore: DataStore<Preferences>
) {

    constructor(context: Context) : this(context.cwSettingsDataStore)

    companion object {
        /** 仅供单元测试使用，允许注入内存 DataStore 实例 */
        fun createForTest(dataStore: DataStore<Preferences>): CWSettingsStore {
            return CWSettingsStore(dataStore)
        }
    }

    private val wpmKey = intPreferencesKey("wpm")
    private val frequencyKey = intPreferencesKey("frequency")
    private val characterSetKey = stringPreferencesKey("character_set")
    private val practiceLengthKey = intPreferencesKey("practice_length")
    private val practiceDurationKey = intPreferencesKey("practice_duration")
    private val playModeKey = stringPreferencesKey("play_mode")

    val settingsFlow: Flow<CWSettings> = dataStore.data.map { preferences ->
        CWSettings(
            wpm = preferences[wpmKey] ?: 15,
            frequency = preferences[frequencyKey] ?: 600,
            characterSet = enumValueOrDefault(preferences[characterSetKey], CharacterSet.LETTERS),
            practiceLength = preferences[practiceLengthKey] ?: 100,
            practiceDuration = preferences[practiceDurationKey] ?: 5,
            playMode = enumValueOrDefault(preferences[playModeKey], PlayMode.CONTINUOUS)
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T {
        if (name == null) return default
        return try {
            enumValueOf<T>(name)
        } catch (_: IllegalArgumentException) {
            default
        }
    }

    suspend fun updateSettings(settings: CWSettings) {
        dataStore.edit { preferences ->
            preferences[wpmKey] = settings.wpm
            preferences[frequencyKey] = settings.frequency
            preferences[characterSetKey] = settings.characterSet.name
            preferences[practiceLengthKey] = settings.practiceLength
            preferences[practiceDurationKey] = settings.practiceDuration
            preferences[playModeKey] = settings.playMode.name
        }
    }
}

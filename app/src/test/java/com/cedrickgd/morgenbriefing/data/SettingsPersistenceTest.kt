package com.cedrickgd.morgenbriefing.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cedrickgd.morgenbriefing.data.model.EffectsMode
import com.cedrickgd.morgenbriefing.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How [SettingsRepository] stores the settings in its preferences DataStore: defaults for a
 * fresh install and for installs from before a setting existed, round trips. The store is kept
 * in memory (the file format is DataStore's business, and its file storage cannot replace a file
 * on a Windows test machine).
 */
class SettingsPersistenceTest {

    /** A preferences DataStore in memory, with DataStore's semantics (atomic, serialized updates). */
    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        private val lock = Mutex()
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

    private val store = MemoryStore()
    private val repository = SettingsRepository(store)

    @Test
    fun `a fresh install uses automatic effects`() = runBlocking {
        assertEquals(EffectsMode.AUTO, repository.current().effects)
    }

    @Test
    fun `an install from before the setting reads automatic effects and keeps the rest`() = runBlocking {
        // Version 3.1 stored its settings without the "effects" key.
        store.edit { prefs ->
            prefs[stringPreferencesKey("theme_mode")] = ThemeMode.DARK.name
            prefs[booleanPreferencesKey("onboarding_done")] = true
        }
        val read = repository.current()
        assertEquals(EffectsMode.AUTO, read.effects)
        assertEquals(ThemeMode.DARK, read.themeMode)
        assertEquals(true, read.onboardingDone)
        // The next write of any setting stores the default explicitly.
        repository.update { it.copy(sectionSize = 5) }
        assertEquals(EffectsMode.AUTO.name, store.data.first()[stringPreferencesKey("effects")])
    }

    @Test
    fun `every effects mode is stored by name and read back`() = runBlocking {
        EffectsMode.entries.forEach { mode ->
            repository.update { it.copy(effects = mode) }
            assertEquals(mode, repository.current().effects)
            assertEquals(mode.name, store.data.first()[stringPreferencesKey("effects")])
        }
    }

    @Test
    fun `changing the effects leaves every other setting alone`() = runBlocking {
        repository.update { it.copy(themeMode = ThemeMode.LIGHT, sectionSize = 6) }
        val before = repository.current()
        repository.update { it.copy(effects = EffectsMode.LIGHT) }
        assertEquals(before.copy(effects = EffectsMode.LIGHT), repository.current())
    }

    @Test
    fun `an unknown stored value falls back to automatic`() = runBlocking {
        store.edit { it[stringPreferencesKey("effects")] = "ULTRA" }
        assertEquals(EffectsMode.AUTO, repository.current().effects)
    }
}

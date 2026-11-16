package com.cedrickgd.morgenbriefing.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cedrickgd.morgenbriefing.data.model.ReaderFont
import com.cedrickgd.morgenbriefing.data.model.ReaderSettings
import com.cedrickgd.morgenbriefing.data.model.ReaderSpacing
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Test

/** How the reader's "Darstellung" (text size, font, line spacing) is stored with the settings. */
class ReaderSettingsPersistenceTest {

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
    fun `a fresh install and an install from before the setting read the reader as it was`() = runBlocking {
        assertEquals(ReaderSettings(), repository.current().reader)
        store.edit { it[stringPreferencesKey("theme_mode")] = "DARK" }
        assertEquals(ReaderSettings(), repository.current().reader)
    }

    @Test
    fun `every reader setting is stored and read back, and the rest stays as it was`() = runBlocking {
        val before = repository.current()
        val reader = ReaderSettings(textScale = 1.25f, font = ReaderFont.SANS, spacing = ReaderSpacing.WIDE)
        repository.update { it.copy(reader = reader) }
        assertEquals(before.copy(reader = reader), repository.current())
        val prefs = store.data.first()
        assertEquals(1.25f, prefs[floatPreferencesKey("reader_text_scale")])
        assertEquals("SANS", prefs[stringPreferencesKey("reader_font")])
        assertEquals("WIDE", prefs[stringPreferencesKey("reader_spacing")])
    }

    @Test
    fun `broken reader values fall back to safe ones`() = runBlocking {
        store.edit { prefs ->
            prefs[floatPreferencesKey("reader_text_scale")] = 3.7f
            prefs[stringPreferencesKey("reader_font")] = "COMIC"
            prefs[stringPreferencesKey("reader_spacing")] = ""
        }
        assertEquals(ReaderSettings(textScale = ReaderSettings.MAX_SCALE), repository.current().reader)
        store.edit { it[floatPreferencesKey("reader_text_scale")] = Float.NaN }
        assertEquals(ReaderSettings.DEFAULT_SCALE, repository.current().reader.textScale)
        // A size between two steps is stored on the nearest step.
        repository.update { it.copy(reader = ReaderSettings(textScale = 1.12f)) }
        assertEquals(1.1f, store.data.first()[floatPreferencesKey("reader_text_scale")])
    }
}

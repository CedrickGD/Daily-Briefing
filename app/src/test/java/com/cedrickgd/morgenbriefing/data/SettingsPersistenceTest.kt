package com.cedrickgd.morgenbriefing.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cedrickgd.morgenbriefing.data.model.EffectsMode
import com.cedrickgd.morgenbriefing.data.model.ListenSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.ThemeMode
import com.cedrickgd.morgenbriefing.data.model.VoicePreference
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
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

    // ---- "Vorlesen" ----

    @Test
    fun `a fresh install and an install from before 3_3 read aloud as before`() = runBlocking {
        assertEquals(ListenSettings(), repository.current().listen)
        // Version 3.2 stored no listen keys at all.
        store.edit { prefs -> prefs[stringPreferencesKey("theme_mode")] = ThemeMode.DARK.name }
        val read = repository.current().listen
        assertEquals(null, read.engine)
        assertEquals(emptyMap<String, String>(), read.voices)
        assertEquals(VoicePreference.ANY, read.voicePreference)
        assertEquals(1f, read.rate)
        assertEquals(1f, read.pitch)
        assertEquals(null, read.language)
        // The next write stores the defaults, leaving "default engine" and "automatic language" unset.
        repository.update { it.copy(sectionSize = 5) }
        val prefs = store.data.first()
        assertEquals(1f, prefs[floatPreferencesKey("listen_rate")])
        assertEquals("ANY", prefs[stringPreferencesKey("listen_voice_preference")])
        assertEquals(null, prefs[stringPreferencesKey("listen_engine")])
        assertEquals(null, prefs[stringPreferencesKey("listen_language")])
        assertEquals(ListenSettings(), repository.current().listen)
    }

    @Test
    fun `every listen setting is stored and read back`() = runBlocking {
        val listen = ListenSettings(
            engine = "com.google.android.tts",
            voices = mapOf("de" to "de-de-x-deb-local", "en" to "en-US-SMTf00"),
            voicePreference = VoicePreference.FEMALE,
            rate = 1.5f,
            pitch = 0.9f,
            language = "en",
        )
        repository.update { it.copy(listen = listen) }
        assertEquals(listen, repository.current().listen)
        // Back to the defaults: engine and language keys go away again.
        repository.update { it.copy(listen = ListenSettings()) }
        assertEquals(ListenSettings(), repository.current().listen)
        assertEquals(null, store.data.first()[stringPreferencesKey("listen_engine")])
        assertEquals(null, store.data.first()[stringPreferencesKey("listen_language")])
    }

    @Test
    fun `changing a listen setting leaves every other setting alone`() = runBlocking {
        repository.update { it.copy(themeMode = ThemeMode.LIGHT, sectionSize = 6, effects = EffectsMode.LIGHT) }
        val before = repository.current()
        repository.update { it.copy(listen = it.listen.copy(rate = 1.25f)) }
        assertEquals(before.copy(listen = before.listen.copy(rate = 1.25f)), repository.current())
    }

    @Test
    fun `broken listen values fall back to safe ones`() = runBlocking {
        store.edit { prefs ->
            prefs[floatPreferencesKey("listen_rate")] = 7.3f
            prefs[floatPreferencesKey("listen_pitch")] = Float.NaN
            prefs[stringPreferencesKey("listen_voices")] = "not json"
            prefs[stringPreferencesKey("listen_voice_preference")] = "ROBOT"
            prefs[stringPreferencesKey("listen_language")] = "English"
            prefs[stringPreferencesKey("listen_engine")] = "   "
        }
        val read = repository.current().listen
        assertEquals(2f, read.rate)
        assertEquals(1f, read.pitch)
        assertEquals(emptyMap<String, String>(), read.voices)
        assertEquals(VoicePreference.ANY, read.voicePreference)
        assertEquals(null, read.language)
        assertEquals(null, read.engine)
        // Malformed entries inside a readable map are dropped one by one.
        store.edit { it[stringPreferencesKey("listen_voices")] = """{"DE":"de-DE-SMTm00","x1":"a","en":" "}""" }
        assertEquals(mapOf("de" to "de-DE-SMTm00"), repository.current().listen.voices)
    }

    // ---- News region and news language ----

    @Test
    fun `every edition an install from before 3_4 could have stored reads as before and is written back unchanged`() = runBlocking {
        // The 23 pairs the old picker offered, as it stored them.
        val stored = listOf(
            "DE:de", "AT:de", "CH:de", "US:en", "GB:en", "CA:en", "AU:en", "IE:en", "IN:en", "FR:fr", "BE:fr", "CH:fr",
            "ES:es", "MX:es", "AR:es", "IT:it", "NL:nl", "PL:pl", "PT:pt", "BR:pt", "SE:sv", "TR:tr", "JP:ja",
        )
        for (key in stored) {
            store.edit { prefs ->
                prefs[stringPreferencesKey("news_edition")] = key
                prefs[booleanPreferencesKey("onboarding_done")] = true
            }
            val read = repository.current().edition
            assertEquals(key, read.key)
            assertEquals(key, "${read.country}:${read.language}")
            // Google News has each of them: they read the very feeds they read before.
            assertEquals(key, true, read.isNative)
            assertEquals(key, read, read.google)
            repository.update { it.copy(sectionSize = 5) }
            assertEquals(key, store.data.first()[stringPreferencesKey("news_edition")])
        }
    }

    @Test
    fun `any region with any news language is stored and read back, the app language apart`() = runBlocking {
        repository.update { it.copy(uiLanguage = AppLanguage.DE) }
        for (country in NewsEdition.COUNTRIES) {
            for (language in NewsEdition.LANGUAGES) {
                val edition = NewsEdition(country, language)
                repository.update { it.copy(edition = edition) }
                val read = repository.current()
                assertEquals(edition, read.edition)
                assertEquals("$country:$language", store.data.first()[stringPreferencesKey("news_edition")])
                // Neither choice moves the interface language.
                assertEquals(AppLanguage.DE, read.uiLanguage)
                assertEquals("de", store.data.first()[stringPreferencesKey("ui_language")])
            }
        }
        // And the interface language moves neither of them.
        repository.update { it.copy(edition = NewsEdition("US", "de")) }
        repository.update { it.copy(uiLanguage = AppLanguage.EN) }
        assertEquals(NewsEdition("US", "de"), repository.current().edition)
    }

    @Test
    fun `changing the news region or language leaves every other setting alone`() = runBlocking {
        repository.update { it.copy(themeMode = ThemeMode.LIGHT, sectionSize = 6, onboardingDone = true, followed = listOf("sport", "ki")) }
        val before = repository.current()
        repository.update { it.copy(edition = NewsEdition("US", "de")) }
        assertEquals(before.copy(edition = NewsEdition("US", "de")), repository.current())
        repository.update { it.copy(edition = NewsEdition("DE", "en")) }
        assertEquals(before.copy(edition = NewsEdition("DE", "en")), repository.current())
    }

    @Test
    fun `sources from abroad and in English are on for a fresh install and for one from before 3_6`() = runBlocking {
        assertEquals(true to true, repository.current().let { it.internationalSources to it.englishSources })
        // Version 3.5 stored its settings without either key.
        store.edit { prefs ->
            prefs[booleanPreferencesKey("readable_only")] = false
            prefs[booleanPreferencesKey("onboarding_done")] = true
        }
        val read = repository.current()
        assertEquals(true to true, read.internationalSources to read.englishSources)
        assertEquals(false, read.readableOnly)
    }

    @Test
    fun `switching the sources from abroad and in English off is stored and leaves the rest alone`() = runBlocking {
        repository.update { it.copy(themeMode = ThemeMode.LIGHT, onboardingDone = true, followed = listOf("ki")) }
        val before = repository.current()
        repository.update { it.copy(internationalSources = false) }
        assertEquals(before.copy(internationalSources = false), repository.current())
        repository.update { it.copy(englishSources = false) }
        assertEquals(before.copy(internationalSources = false, englishSources = false), repository.current())
        repository.update { it.copy(internationalSources = true) }
        assertEquals(before.copy(englishSources = false), repository.current())
    }

    @Test
    fun `a stored edition the app does not offer falls back to the German news an old install had`() = runBlocking {
        for (broken in listOf("XX:yy", "US", "us:en", "US:en:x", "NO:nb", "")) {
            store.edit { prefs ->
                prefs[stringPreferencesKey("news_edition")] = broken
                prefs[booleanPreferencesKey("onboarding_done")] = true
            }
            assertEquals(broken, NewsEdition.GERMANY, repository.current().edition)
        }
    }
}

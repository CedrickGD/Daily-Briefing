package com.cedrickgd.morgenbriefing.listen

import com.cedrickgd.morgenbriefing.data.model.ListenSettings
import com.cedrickgd.morgenbriefing.data.model.VoicePreference
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ListenStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {

    private fun voice(
        name: String,
        language: String = name.substringBefore('-').lowercase(),
        country: String = name.split('-').getOrNull(1)?.uppercase().orEmpty(),
        quality: Int = VoiceInfo.QUALITY_NORMAL,
        network: Boolean = false,
        notInstalled: Boolean = false,
        features: Set<String> = emptySet(),
    ) = VoiceInfo(name, language, country, quality, VoiceInfo.LATENCY_NORMAL, network, notInstalled, features)

    private fun gender(name: String, features: Set<String> = emptySet()) = VoiceGenders.of(voice(name, features = features))

    @Test
    fun `Samsung voices reveal their gender by the letter after SMT, only f and m`() {
        val table = mapOf(
            "de-DE-SMTf00" to VoiceGender.FEMALE,
            "en-US-SMTf00" to VoiceGender.FEMALE,
            "fr-FR-SMTf01" to VoiceGender.FEMALE,
            "en-GB-SMTm00" to VoiceGender.MALE,
            "de-DE-SMTm00" to VoiceGender.MALE,
            // Newer voices (named "Voice 1/2/3" by Samsung) say nothing about their gender.
            "en-US-SMTl03" to null,
            "en-US-SMTg02" to null,
            "ko-KR-SMTl01" to null,
        )
        table.forEach { (name, expected) -> assertEquals(name, expected, gender(name)) }
    }

    @Test
    fun `Google voices are looked up in the table, local and network alike`() {
        val table = mapOf(
            "de-de-x-dea-local" to VoiceGender.FEMALE,
            "de-de-x-nfh-network" to VoiceGender.FEMALE,
            "de-de-x-deb-local" to VoiceGender.MALE,
            "de-de-x-deb-network" to VoiceGender.MALE,
            "de-de-x-deg-local" to VoiceGender.MALE,
            "en-us-x-sfg-local" to VoiceGender.FEMALE,
            "en-us-x-tpc-network" to VoiceGender.FEMALE,
            "en-us-x-iol-local" to VoiceGender.MALE,
            "en-us-x-iom-local" to VoiceGender.MALE,
            "en-gb-x-gba-local" to VoiceGender.FEMALE,
            "en-gb-x-rjs-network" to VoiceGender.MALE,
            "fr-fr-x-frc-local" to VoiceGender.FEMALE,
            "fr-fr-x-frd-local" to VoiceGender.MALE,
            "es-es-x-eea-local" to VoiceGender.FEMALE,
            "es-es-x-eed-local" to VoiceGender.MALE,
            "it-it-x-itb-local" to VoiceGender.FEMALE,
            "it-it-x-itd-network" to VoiceGender.MALE,
        )
        table.forEach { (name, expected) -> assertEquals(name, expected, gender(name)) }
    }

    @Test
    fun `unknown voices stay unknown – never guessed`() {
        listOf(
            "en-au-x-aua-local", // a code outside the table
            "nl-nl-x-bmh-local", // a language outside the table
            "de-at-x-deb-local", // the code is known for de-DE only
            "de-DE-language", // Google's alias of whichever voice is the default
            "de-de-x-deb", // neither local nor network
            "Anna", "Yannick", "eSpeak German",
        ).forEach { assertNull(it, gender(it)) }
    }

    @Test
    fun `any engine that says female or male as a word is believed`() {
        assertEquals(VoiceGender.MALE, gender("en-us-x-sfg#male_1-local"))
        assertEquals(VoiceGender.FEMALE, gender("en-us-x-sfg#female_1-local"))
        assertEquals(VoiceGender.FEMALE, gender("Vocalizer Anna", features = setOf("gender=female")))
        assertEquals(VoiceGender.MALE, gender("Acapela Male Voice"))
        // Inside another word, or both words: no claim.
        assertNull(gender("femaleandmale"))
        assertNull(gender("voice-male-female"))
        assertNull(gender("de-DE-x-normal", features = setOf("networkTimeoutMs", "embeddedTts")))
    }

    @Test
    fun `voice languages from engines using ISO 639-2 are mapped back`() {
        assertEquals("de", VoiceLanguages.language("deu"))
        assertEquals("en", VoiceLanguages.language("ENG"))
        assertEquals("fr", VoiceLanguages.language("fr"))
        assertEquals("DE", VoiceLanguages.country("DEU"))
        assertEquals("US", VoiceLanguages.country("us"))
    }

    private val german = listOf(
        voice("de-de-x-nfh-network", network = true, quality = VoiceInfo.QUALITY_VERY_HIGH),
        voice("de-de-x-nfh-local"),
        voice("de-de-x-deb-local"),
        voice("de-de-x-dea-local", notInstalled = true),
        voice("de-DE-language"),
        voice("de-at-x-aaa-local"),
        voice("en-us-x-sfg-local"),
    )

    @Test
    fun `the list shows each installed voice once, offline first, the edition's country first`() {
        val entries = VoiceCatalog.entries(german, "de", preferredCountry = "DE")
        assertEquals(listOf("de-de-x-deb-local", "de-de-x-nfh-local", "de-at-x-aaa-local"), entries.map { it.voice.name })
        assertEquals(listOf(1, 2, 3), entries.map { it.number })
        assertEquals(listOf(VoiceGender.MALE, VoiceGender.FEMALE, null), entries.map { it.gender })
        // Only the alias: it is the one voice there is, so it stays.
        assertEquals(listOf("de-DE-language"), VoiceCatalog.entries(listOf(voice("de-DE-language")), "de", "DE").map { it.voice.name })
        assertTrue(VoiceCatalog.entries(german, "fr", "FR").isEmpty())
    }

    @Test
    fun `labels read like Stimme 1 · weiblich · offline in every language`() {
        val de = ListenStrings.of(AppLanguage.DE)
        assertEquals("Stimme 2 · weiblich · offline", de.voiceLabel(2, VoiceGender.FEMALE, offline = true))
        assertEquals("Stimme 1 · männlich · offline", de.voiceLabel(1, VoiceGender.MALE, offline = true))
        assertEquals("Stimme 3 · online", de.voiceLabel(3, null, offline = false))
        assertEquals("Voice 1 · female · offline", ListenStrings.of(AppLanguage.EN).voiceLabel(1, VoiceGender.FEMALE, true))
        assertEquals("Voix 2 · masculine · hors ligne", ListenStrings.of(AppLanguage.FR).voiceLabel(2, VoiceGender.MALE, true))
        assertEquals("Voz 1 · en línea", ListenStrings.of(AppLanguage.ES).voiceLabel(1, null, false))
        assertEquals("Voce 4 · femminile · offline", ListenStrings.of(AppLanguage.IT).voiceLabel(4, VoiceGender.FEMALE, true))
        assertEquals("Österreich · hohe Qualität", de.voiceDetails("AT", VoiceInfo.QUALITY_HIGH, slow = false))
        assertEquals("sehr hohe Qualität · startet etwas später", de.voiceDetails(null, VoiceInfo.QUALITY_VERY_HIGH, slow = true))
        assertEquals("United Kingdom · normal quality", ListenStrings.of(AppLanguage.EN).voiceDetails("GB", VoiceInfo.QUALITY_NORMAL, false))
    }

    @Test
    fun `the panel finds the chosen voice, the automatic one, the genders and what is missing`() {
        val chosen = ListenSettings().withVoice("de", "de-de-x-nfh-network") // an older choice of the online variant
        val panel = VoiceCatalog.panel(german, "de", chosen, preferredCountry = "DE")
        assertEquals("de-de-x-nfh-local", panel.chosen?.voice?.name)
        assertFalse(panel.chosenMissing)
        assertEquals(setOf(VoiceGender.FEMALE, VoiceGender.MALE), panel.genders)
        assertEquals(1, panel.notInstalled)
        assertEquals(listOf(1, 2), panel.nearby.map { it.number })
        assertEquals(listOf(3), panel.elsewhere.map { it.number })
        assertFalse(panel.onlineOnly)
        assertFalse(panel.noVoice)
        // Automatic without a preference: the first offline voice of the best quality.
        assertEquals(VoiceSource.BEST_OFFLINE, panel.auto.source)

        val gone = VoiceCatalog.panel(german, "de", ListenSettings().withVoice("de", "de-DE-SMTf00"), "DE")
        assertNull(gone.chosen)
        assertTrue(gone.chosenMissing)

        val female = VoiceCatalog.panel(german, "de", ListenSettings(voicePreference = VoicePreference.FEMALE), "DE")
        assertEquals(VoiceSource.PREFERENCE, female.auto.source)
        assertEquals(2, female.autoEntry?.number)
    }

    @Test
    fun `engines get short names where they are known`() {
        assertEquals("Samsung", TtsEngine("com.samsung.SMT", "Samsung text-to-speech engine").shortLabel)
        assertEquals("Google", TtsEngine("com.google.android.tts", "Speech Services by Google").shortLabel)
        assertEquals("eSpeak NG", TtsEngine("com.reecedunn.espeak", " eSpeak NG ").shortLabel)
        assertEquals("rhvoice", TtsEngine("com.github.olga_yakovleva.rhvoice", "").shortLabel)
    }

    @Test
    fun `within a country the best quality comes first`() {
        val voices = listOf(
            voice("de-de-x-aaa-local"),
            voice("de-de-x-zzz-local", quality = VoiceInfo.QUALITY_VERY_HIGH),
            voice("de-at-x-bbb-local", quality = VoiceInfo.QUALITY_VERY_HIGH),
        )
        assertEquals(
            listOf("de-de-x-zzz-local", "de-de-x-aaa-local", "de-at-x-bbb-local"),
            VoiceCatalog.entries(voices, "de", preferredCountry = "DE").map { it.voice.name },
        )
    }

    @Test
    fun `a chosen natural voice is neither an engine voice nor missing`() {
        val settings = ListenSettings().withVoice("de", NeuralVoiceIds.choice("de_DE-thorsten-medium"))
        val panel = VoiceCatalog.panel(german, "de", settings, preferredCountry = "DE")
        assertEquals("de_DE-thorsten-medium", panel.neuralChosen)
        assertNull(panel.chosen)
        assertFalse(panel.chosenMissing)
        assertNull(VoiceCatalog.panel(german, "de", ListenSettings(), "DE").neuralChosen)
        // The engine itself never gets the stored natural choice.
        assertEquals(VoiceSource.BEST_OFFLINE, VoiceChoice.resolve(german, "de", null, settings.voicePreference, "DE").source)
    }

    @Test
    fun `the hint to Google's engine shows only where it helps`() {
        val samsung = TtsEngine("com.samsung.SMT", "Samsung")
        val google = TtsEngine(GoogleVoices.PACKAGE, "Speech Services by Google")
        val plain = listOf(voice("de-DE-SMTf00", country = "DE"))
        fun state(engines: List<TtsEngine>, engine: String, voices: List<VoiceInfo> = plain) =
            ListenVoices(engines = engines, engine = engine, voices = voices, loaded = true)
        assertEquals(GoogleVoiceHint.USE, GoogleVoices.hint(state(listOf(samsung, google), samsung.name), "de"))
        assertEquals(GoogleVoiceHint.INSTALL, GoogleVoices.hint(state(listOf(samsung), samsung.name), "de"))
        // Google already reads: nothing to say.
        assertNull(GoogleVoices.hint(state(listOf(samsung, google), google.name), "de"))
        // Not loaded yet: wait.
        assertNull(GoogleVoices.hint(ListenVoices(), "de"))
        // The engine in use already has a very high quality offline voice for the language.
        val excellent = listOf(voice("de-DE-SMTl03", country = "DE", quality = VoiceInfo.QUALITY_VERY_HIGH))
        assertNull(GoogleVoices.hint(state(listOf(samsung), samsung.name, excellent), "de"))
        // ... but not for English: then the hint is back.
        assertEquals(GoogleVoiceHint.INSTALL, GoogleVoices.hint(state(listOf(samsung), samsung.name, excellent), "en"))
    }
}

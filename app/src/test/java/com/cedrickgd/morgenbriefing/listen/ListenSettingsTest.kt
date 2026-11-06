package com.cedrickgd.morgenbriefing.listen

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ListenSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ListenStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListenSettingsTest {

    @Test
    fun `speeds come in sensible steps from half to double`() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f), ListenSettings.RATES)
        assertEquals(1.25f, ListenSettings.snapRate(1.3f))
        assertEquals(2f, ListenSettings.snapRate(7.3f))
        assertEquals(0.5f, ListenSettings.snapRate(0.1f))
        assertEquals(1f, ListenSettings.snapRate(Float.NaN))
        assertEquals(listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f), ListenSettings.PITCHES)
        assertEquals(1.1f, ListenSettings.snapPitch(1.13f))
        assertEquals(1f, ListenSettings.snapPitch(Float.NaN))
    }

    @Test
    fun `the speed chip cycles 1 to 1,25 to 1,5 to 2 to 0,75 and back`() {
        var rate = 1f
        val seen = mutableListOf(rate)
        repeat(5) {
            rate = ListenSettings.nextChipRate(rate)
            seen += rate
        }
        assertEquals(listOf(1f, 1.25f, 1.5f, 2f, 0.75f, 1f), seen)
        // Speeds only the settings offer go on to the next faster chip speed.
        assertEquals(0.75f, ListenSettings.nextChipRate(0.5f))
        assertEquals(2f, ListenSettings.nextChipRate(1.75f))
    }

    @Test
    fun `speeds and pitches are labelled in each language`() {
        val de = ListenStrings.of(AppLanguage.DE)
        val en = ListenStrings.of(AppLanguage.EN)
        assertEquals(listOf("0,5×", "0,75×", "1×", "1,25×", "1,5×", "1,75×", "2×"), ListenSettings.RATES.map(de::rateLabel))
        assertEquals(listOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"), ListenSettings.RATES.map(en::rateLabel))
        assertEquals("1,25×", ListenStrings.of(AppLanguage.FR).rateLabel(1.25f))
        assertEquals(listOf("Tiefer", "Etwas tiefer", "Normal", "Etwas höher", "Höher"), ListenSettings.PITCHES.map(de::pitchLabel))
        for (lang in AppLanguage.entries) {
            val s = ListenStrings.of(lang)
            assertEquals("$lang pitch labels are distinct", 5, ListenSettings.PITCHES.map(s::pitchLabel).distinct().size)
        }
    }

    @Test
    fun `stored languages and voices are cleaned`() {
        assertEquals("de", ListenSettings.cleanLanguage(" DE "))
        assertNull(ListenSettings.cleanLanguage(""))
        assertNull(ListenSettings.cleanLanguage("d3"))
        assertNull(ListenSettings.cleanLanguage("English"))
        val chosen = ListenSettings().withVoice("EN", "en-US-SMTf00")
        assertEquals(mapOf("en" to "en-US-SMTf00"), chosen.voices)
        assertEquals("en-US-SMTf00", chosen.voiceFor("en"))
        assertEquals(emptyMap<String, String>(), chosen.withVoice("en", null).voices)
        assertEquals(emptyMap<String, String>(), chosen.withVoice("en", "  ").voices)
    }

    @Test
    fun `a fixed language reads every article in it, in a fitting country`() {
        val austria = NewsEdition("AT", "de")
        val uk = NewsEdition("GB", "en")
        // Automatic: the article's own edition.
        assertEquals(NewsEdition.USA, ListenLanguages.voiceEdition(NewsEdition.USA, fixed = null, main = NewsEdition.GERMANY))
        // Fixed German: the main edition when it speaks German.
        assertEquals(austria, ListenLanguages.voiceEdition(NewsEdition.USA, fixed = "de", main = austria))
        // The article speaks the fixed language itself: its country stays (en-GB).
        assertEquals(uk, ListenLanguages.voiceEdition(uk, fixed = "en", main = NewsEdition.GERMANY))
        assertEquals(NewsEdition("FR", "fr"), ListenLanguages.voiceEdition(NewsEdition.GERMANY, fixed = "fr", main = NewsEdition.GERMANY))
        assertEquals("DE", ListenLanguages.preferredCountry("de", NewsEdition.GERMANY))
        assertEquals("AT", ListenLanguages.preferredCountry("de", austria))
        assertEquals("US", ListenLanguages.preferredCountry("en", NewsEdition.GERMANY))
    }

    @Test
    fun `the settings offer the languages that matter`() {
        assertEquals(listOf("de", "en", "fr", "es", "it"), ListenLanguages.fixedChoices(NewsEdition.GERMANY, stored = null))
        assertEquals(listOf("nl", "de", "en", "fr", "es", "it"), ListenLanguages.fixedChoices(NewsEdition("NL", "nl"), stored = null))
        assertEquals(listOf("de", "en", "fr", "es", "it", "ja"), ListenLanguages.fixedChoices(NewsEdition.GERMANY, stored = "ja"))

        val plain = AppSettings(edition = NewsEdition.GERMANY)
        assertEquals(listOf("de", "en"), ListenLanguages.voiceLanguages(plain))
        val paris = Place("Paris", 48.85, 2.35, "Île-de-France, France", "FR")
        val traveller = plain.copy(localPlaces = listOf(paris), listen = ListenSettings().withVoice("it", "it-it-x-itb-local"))
        assertEquals(listOf("de", "en", "fr", "it"), ListenLanguages.voiceLanguages(traveller))
        assertEquals(listOf("fr"), ListenLanguages.voiceLanguages(plain.copy(listen = ListenSettings(language = "fr"))))
    }

    @Test
    fun `a change mid-way continues at the start of the current sentence`() {
        val text = "Erster Satz. Zweiter Satz mit mehr Wörtern. Dritter Satz."
        val second = text.indexOf("Zweiter")
        val third = text.indexOf("Dritter")
        assertEquals(0, sentenceStartBefore(text, 0))
        assertEquals(0, sentenceStartBefore(text, 5))
        assertEquals(second, sentenceStartBefore(text, second))
        assertEquals(second, sentenceStartBefore(text, text.indexOf("Wörtern")))
        assertEquals(third, sentenceStartBefore(text, text.length - 2))
        assertEquals(0, sentenceStartBefore(text, -1))
        assertEquals(0, sentenceStartBefore("", 3))
    }

    @Test
    fun `sample utterances are never taken for a part of a reading`() {
        assertEquals(3, UtteranceIds.previewCount(UtteranceIds.preview(3)))
        assertNull(UtteranceIds.parse(UtteranceIds.preview(3)))
        assertNull(UtteranceIds.previewCount(UtteranceIds.speech(1, 2)))
        assertNull(UtteranceIds.previewCount(null))
    }

    @Test
    fun `the status line counts the remaining time at the chosen speed`() {
        val de = ListenStrings.of(AppLanguage.DE)
        val reading = ListenState(speaking = true, estimatedSeconds = 360, progress = 0f)
        assertEquals("LIEST VOR · NOCH ETWA 6 MIN.", statusLine(reading, de))
        assertEquals("LIEST VOR · NOCH ETWA 3 MIN.", statusLine(reading.copy(rate = 2f), de))
    }
}

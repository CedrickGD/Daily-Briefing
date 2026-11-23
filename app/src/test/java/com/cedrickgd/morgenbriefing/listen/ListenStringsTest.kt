package com.cedrickgd.morgenbriefing.listen

import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ListenStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenStringsTest {

    /** Every fixed text (`val`) of [ListenStrings], per language. */
    private val fixed: Map<AppLanguage, Map<String, String>> = AppLanguage.entries.associateWith { lang ->
        val strings = ListenStrings.of(lang)
        ListenStrings::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == String::class.java && it.name.startsWith("get") }
            .associate { it.name to it.invoke(strings) as String }
    }

    @Test
    fun `every fixed text exists in every language and is translated`() {
        val german = fixed.getValue(AppLanguage.DE)
        assertTrue(german.size > 40)
        fixed.forEach { (lang, texts) ->
            texts.forEach { (name, text) ->
                assertTrue("$name in $lang is blank", text.isNotBlank())
                assertEquals("$name in $lang has stray spaces", text.trim(), text)
            }
            if (lang != AppLanguage.DE) {
                val same = texts.filter { (name, text) -> german[name] == text }
                assertTrue("$lang copies German: ${same.keys}", same.size <= texts.size / 10)
            }
        }
    }

    @Test
    fun `French says tu, never vous`() {
        val french = ListenStrings.of(AppLanguage.FR)
        val texts = fixed.getValue(AppLanguage.FR).values + listOf(
            french.noVoiceFor("en"), french.onlineOnlyFor("en"), french.notInstalledVoices(3), french.languageFixedHint("de"),
        )
        val formal = Regex("""\b(vous|votre|vos)\b""", RegexOption.IGNORE_CASE)
        texts.forEach { assertFalse("formal French: $it", formal.containsMatchIn(it)) }
    }

    @Test
    fun `the honest notes name the language`() {
        assertEquals(
            "Auf diesem Handy ist keine englische Stimme installiert. Solche Artikel liest dann die Stimme in der Sprache deines Handys – installiere eine, damit sie richtig klingen.",
            ListenStrings.of(AppLanguage.DE).noVoiceFor("en"),
        )
        assertTrue(ListenStrings.of(AppLanguage.EN).noVoiceFor("de").startsWith("This phone has no German voice installed."))
        assertEquals("Stimme für Englisch", ListenStrings.of(AppLanguage.DE).voicesOverline("en"))
        assertEquals("Eine weitere Stimme kannst du noch herunterladen.", ListenStrings.of(AppLanguage.DE).notInstalledVoices(1))
        assertEquals(
            "Eine weibliche Stimme · gerade Stimme 2",
            ListenStrings.of(AppLanguage.DE).voiceAutoText(VoiceSource.PREFERENCE, VoiceGender.FEMALE, 2),
        )
        assertEquals("The best offline voice", ListenStrings.of(AppLanguage.EN).voiceAutoText(VoiceSource.BEST_OFFLINE, null, null))
    }

    @Test
    fun `the sample sentence is spoken in the voice's language`() {
        // A Dutch voice says the English sample, like "Source:" after a Dutch article.
        assertEquals(AppLanguage.EN, ListenLanguages.scriptLanguage(AppLanguage.DE, com.cedrickgd.morgenbriefing.data.model.NewsEdition("NL", "nl")))
        AppLanguage.entries.forEach { lang -> assertTrue(ListenStrings.of(lang).sampleSentence.length > 20) }
        assertEquals(5, AppLanguage.entries.map { ListenStrings.of(it).sampleSentence }.distinct().size)
    }

    @Test
    fun `natural voice texts exist in every language, name the voice and say tu in French`() {
        AppLanguage.entries.forEach { lang ->
            val strings = ListenStrings.of(lang)
            val texts = listOf(
                strings.neuralVoiceTitle("Kerstin", VoiceGender.FEMALE),
                strings.neuralVoiceDetails("Thorsten-Voice", "CC0"),
                strings.neuralDownload(21),
                strings.neuralProgress(8, 21),
                strings.neuralCancel("Thorsten"),
                strings.neuralDelete("Thorsten"),
            )
            assertTrue(texts[0].startsWith("Kerstin · "))
            assertTrue(texts[1].endsWith("Thorsten-Voice · CC0"))
            assertTrue(texts[2].endsWith("21 MB") || texts[2].endsWith("21 Mo"))
            assertTrue(texts[3].contains("8") && texts[3].contains("21"))
            assertTrue(texts[4].contains("Thorsten") && texts[5].contains("Thorsten"))
            texts.forEach { assertFalse("em dash in $lang: $it", it.contains('\u2014')) }
        }
        val de = ListenStrings.of(AppLanguage.DE)
        assertEquals("Thorsten · männlich", de.neuralVoiceTitle("Thorsten", VoiceGender.MALE))
        assertEquals("Aufnahmen: LJ Speech · gemeinfrei", de.neuralVoiceDetails("LJ Speech", NeuralVoice.PUBLIC_DOMAIN))
        assertEquals("Laden · 21 MB", de.neuralDownload(21))
        assertEquals("Recordings: LibriVox · public domain", ListenStrings.of(AppLanguage.EN).neuralVoiceDetails("LibriVox", NeuralVoice.PUBLIC_DOMAIN))
        val formal = Regex("""\b(vous|votre|vos)\b""", RegexOption.IGNORE_CASE)
        val french = ListenStrings.of(AppLanguage.FR)
        listOf(french.neuralHint, french.neuralMetered, french.neuralFailed, french.neuralSource, french.googleInstallHint, french.googleUseHint)
            .forEach { assertFalse("formal French: $it", formal.containsMatchIn(it)) }
    }

    @Test
    fun `no new listen text uses an em dash`() {
        fixed.forEach { (lang, texts) ->
            texts.filterKeys { it.contains("Neural") || it.contains("Google") }.forEach { (name, text) ->
                assertFalse("$name in $lang", text.contains('\u2014'))
            }
        }
    }
}

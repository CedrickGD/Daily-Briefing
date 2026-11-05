package com.cedrickgd.morgenbriefing.listen

import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ListenStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ListenLanguagesTest {

    private val item = NewsItem(
        id = "a1", headline = "Senate passes the budget", summary = "Sen. Smith voted yes. The bill now goes to the House.",
        url = "https://npr.org/a", source = "NPR", sourceId = "npr", topicId = "politik",
    )

    @Test
    fun `the script speaks the news language, the interface language when they match`() {
        val us = NewsEdition.USA
        // German interface, US news: the voice reads English, so it says "Source", not "Quelle".
        assertEquals(AppLanguage.EN, ListenLanguages.scriptLanguage(AppLanguage.DE, us))
        assertEquals(AppLanguage.DE, ListenLanguages.scriptLanguage(AppLanguage.DE, NewsEdition.GERMANY))
        assertEquals(AppLanguage.DE, ListenLanguages.scriptLanguage(AppLanguage.EN, NewsEdition("AT", "de")))
        assertEquals(AppLanguage.FR, ListenLanguages.scriptLanguage(AppLanguage.IT, NewsEdition("BE", "fr")))
        // An edition in a language the app does not speak: English connecting words.
        assertEquals(AppLanguage.EN, ListenLanguages.scriptLanguage(AppLanguage.DE, NewsEdition("NL", "nl")))
        assertEquals(AppLanguage.EN, ListenLanguages.scriptLanguage(AppLanguage.ES, NewsEdition("JP", "ja")))
    }

    @Test
    fun `the voice is the edition's language in its country, then the plain language`() {
        assertEquals(listOf(Locale.forLanguageTag("en-US"), Locale.forLanguageTag("en")), ListenLanguages.voiceLocales(NewsEdition.USA))
        assertEquals(listOf(Locale.forLanguageTag("de-DE"), Locale.forLanguageTag("de")), ListenLanguages.voiceLocales(NewsEdition.GERMANY))
    }

    @Test
    fun `the source sentence follows the script language`() {
        val texts = ListenScript.from(item, AppLanguage.EN).parts.map { it.text }
        assertEquals("Source: NPR.", texts.last())
        assertEquals("Source : NPR.", ListenScript.sourcesSentence(item, AppLanguage.FR))
        assertEquals("Fuente: NPR.", ListenScript.sourcesSentence(item, AppLanguage.ES))
        assertEquals("Fonte: NPR.", ListenScript.sourcesSentence(item, AppLanguage.IT))
        assertEquals("Quelle: NPR.", ListenScript.sourcesSentence(item, AppLanguage.DE))
    }

    @Test
    fun `English titles do not end a sentence`() {
        assertEquals(listOf("Sen. Smith voted yes.", "The bill now goes to the House."), splitSentences(item.summary))
    }

    @Test
    fun `voice hints name the news language`() {
        assertEquals("Keine deutsche Stimme", ListenStrings.of(AppLanguage.DE).missingVoiceTitle("de"))
        assertEquals("Keine englische Stimme", ListenStrings.of(AppLanguage.DE).missingVoiceTitle("en"))
        assertEquals("Deutsche Stimme offline installieren", ListenStrings.of(AppLanguage.DE).installOfflineVoice("de"))
        assertEquals(
            "Installiere eine deutsche Stimme unter Einstellungen › Sprachausgabe.",
            ListenStrings.of(AppLanguage.DE).installVoiceHint("de"),
        )
        assertEquals("No English voice", ListenStrings.of(AppLanguage.EN).missingVoiceTitle("en"))
        assertEquals("No German voice", ListenStrings.of(AppLanguage.EN).missingVoiceTitle("de"))
        assertEquals("Aucune voix en anglais", ListenStrings.of(AppLanguage.FR).missingVoiceTitle("en"))
        assertEquals("No hay voz en inglés", ListenStrings.of(AppLanguage.ES).missingVoiceTitle("en"))
        assertEquals("Nessuna voce in inglese", ListenStrings.of(AppLanguage.IT).missingVoiceTitle("en"))
        for (lang in AppLanguage.entries) {
            for (code in NewsEdition.ALL.map { it.language }.distinct()) {
                assertTrue("$lang $code", ListenStrings.of(lang).languageName(code).isNotBlank())
            }
        }
    }

    @Test
    fun `mini player status line`() {
        val de = ListenStrings.of(AppLanguage.DE)
        val en = ListenStrings.of(AppLanguage.EN)
        val reading = ListenState(speaking = true, estimatedSeconds = 180, progress = 0f)
        assertEquals("LIEST VOR · NOCH ETWA 3 MIN.", statusLine(reading, de))
        assertEquals("READING ALOUD · ABOUT 3 MIN LEFT", statusLine(reading, en))
        assertEquals("LIEST VOR · ONLINE-STIMME", statusLine(ListenState(speaking = true, onlineVoiceOnly = true), de))
        assertEquals("PAUSIERT", statusLine(ListenState(paused = true), de))
        assertEquals("PREPARING THE VOICE", statusLine(ListenState(speaking = true, preparing = true, estimatedSeconds = 90), en))
    }
}

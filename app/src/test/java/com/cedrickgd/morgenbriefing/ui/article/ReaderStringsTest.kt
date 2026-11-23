package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.article.ArticleException
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import com.cedrickgd.morgenbriefing.data.model.ReaderFont
import com.cedrickgd.morgenbriefing.data.model.ReaderSpacing
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ReaderStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderStringsTest {

    private val outcomes: List<ReadOutcome?> = ReadOutcome.entries + null

    @Test
    fun `every language explains every outcome, with the site and the status code`() {
        for (lang in AppLanguage.entries) {
            val s = ReaderStrings.of(lang)
            for (outcome in outcomes) {
                assertTrue("$lang $outcome", s.failureTitle(outcome).isNotBlank())
                assertTrue("$lang $outcome", s.failureReason(outcome).isNotBlank())
            }
            assertTrue(s.failureReason(ReadOutcome.PAYWALL, "The Verge").contains("The Verge"))
            assertTrue(s.failureReason(ReadOutcome.BLOCKED, "kicker", 403).contains("403"))
            assertTrue(s.failureReason(ReadOutcome.NOT_ARTICLE, "", 404).contains("404"))
            assertTrue(s.failureReason(ReadOutcome.NETWORK, "", 429).contains("429"))
            assertTrue(s.percent(115).contains("115"))
            ReaderFont.entries.forEach { assertTrue("$lang $it", s.fontName(it).isNotBlank()) }
            ReaderSpacing.entries.forEach { assertTrue("$lang $it", s.spacingName(it).isNotBlank()) }
            listOf(s.appearance, s.textSize, s.font, s.spacing, s.showImages, s.showImagesHint, s.resetAppearance).forEach { assertTrue("$lang", it.isNotBlank()) }
            assertEquals("", s.status(ArticleStatus.UNKNOWN))
            ArticleStatus.entries.filter { it != ArticleStatus.UNKNOWN }.forEach { assertTrue("$lang $it", s.status(it).isNotBlank()) }
            listOf(s.listen, s.pauseListening, s.readInApp, s.truncatedNote).forEach { assertTrue(it.isNotBlank()) }
        }
    }

    @Test
    fun `the languages really differ – no silent German fallback`() {
        val texts = AppLanguage.entries.map { ReaderStrings.of(it).failureReason(ReadOutcome.CONSENT, "Golem.de") }
        assertEquals(texts.size, texts.toSet().size)
        assertNotEquals(ReaderStrings.of(AppLanguage.DE).truncatedNote, ReaderStrings.of(AppLanguage.EN).truncatedNote)
        assertTrue(ReaderStrings.of(AppLanguage.DE).failureTitle(ReadOutcome.PAYWALL).contains("Redaktion"))
        assertTrue(ReaderStrings.of(AppLanguage.EN).failureTitle(ReadOutcome.NETWORK).contains("Article"))
    }

    @Test
    fun `a failure carries the typed outcome to the screen`() {
        val failure = ArticleFailure.of(ArticleException(ReadOutcome.BLOCKED, "", 403), fallbackSite = "kicker")
        assertEquals(ReadOutcome.BLOCKED, failure.outcome)
        assertEquals("kicker", failure.site)
        assertEquals(403, failure.httpCode)
        assertTrue(failure.isWall)
        val unknown = ArticleFailure.of(IllegalStateException("boom"), fallbackSite = "BBC")
        assertEquals(null, unknown.outcome)
        assertTrue(ReaderStrings.of(AppLanguage.EN).failureReason(unknown.outcome, unknown.site).isNotBlank())
    }

    @Test
    fun `a Google News link that could not be resolved says so, can be retried, in every language`() {
        val failure = ArticleFailure.of(ArticleException(ReadOutcome.NETWORK, "", 503, via = "Google News"), fallbackSite = "CNN")
        assertEquals("Google News", failure.via)
        assertEquals(listOf(FailureAction.RETRY, FailureAction.OPEN_IN_BROWSER), failureActions(failure))
        assertEquals(FailureIcon.OFFLINE, failureIcon(failure))
        for (lang in AppLanguage.entries) {
            val s = ReaderStrings.of(lang)
            val down = s.failureReason(failure.outcome, failure.site, failure.httpCode, failure.via)
            assertTrue("$lang $down", down.contains("Google News") && down.contains("503"))
            val offline = s.failureReason(ReadOutcome.NETWORK, "CNN", null, "Google News")
            assertTrue("$lang $offline", offline.contains("Google News"))
            assertNotEquals(s.failureReason(ReadOutcome.NETWORK, "CNN"), offline)
        }
        assertEquals(
            "Der Link führt über Google News, und Google News ist gerade nicht erreichbar. Bist du online?",
            ReaderStrings.of(AppLanguage.DE).failureReason(ReadOutcome.NETWORK, "CNN", null, "Google News"),
        )
    }

    @Test
    fun `read full names the minutes that are left, in every language`() {
        for (lang in AppLanguage.entries) {
            val text = ReaderStrings.of(lang).readFull(4)
            assertTrue("$lang $text", text.contains("4"))
            assertFalse("$lang", 0x2014.toChar() in text)
        }
        assertEquals("Ganzen Artikel lesen · noch 4 Min.", ReaderStrings.of(AppLanguage.DE).readFull(4))
        val texts = AppLanguage.entries.map { ReaderStrings.of(it).readFull(2) }
        assertEquals(texts.size, texts.toSet().size)
    }
}

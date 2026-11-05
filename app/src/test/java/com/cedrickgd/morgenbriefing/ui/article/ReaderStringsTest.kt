package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.article.ArticleException
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ReaderStrings
import org.junit.Assert.assertEquals
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
            assertTrue(s.source("BBC").contains("BBC"))
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
}

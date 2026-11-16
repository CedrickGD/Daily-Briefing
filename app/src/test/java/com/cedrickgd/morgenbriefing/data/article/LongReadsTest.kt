package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongReadsTest {

    private fun item(headline: String, url: String = "https://www.tagesschau.de/inland/artikel-100.html") =
        NewsItem(id = "x", headline = headline, url = url, source = "S", sourceId = "s", topicId = "politik")

    private fun content(words: Int, declared: Int = 0) = ArticleContent(
        url = "https://example.org/a", title = "T", fetchedAt = "2026-11-16T07:00:00+01:00", declaredWords = declared,
        blocks = listOf(ArticleBlock.paragraph(List(words) { "Wort" }.joinToString(" "))),
    )

    @Test
    fun `more than 900 words is a long read`() {
        assertFalse(LongReads.isLong(0))
        assertFalse(LongReads.isLong(900))
        assertTrue(LongReads.isLong(901))
        assertEquals(setOf("b"), LongReads.longIds(mapOf("a" to 350, "b" to 2_400, "c" to 0)))
    }

    @Test
    fun `the length is what the reader extracted, or what the publisher declares when that is more`() {
        assertEquals(420, LongReads.wordsOf(content(420)))
        assertEquals(1_800, LongReads.wordsOf(content(120, declared = 1_800)))
        assertEquals(600, LongReads.wordsOf(content(600, declared = 300)))
    }

    @Test
    fun `live blogs and tickers are known by headline or link`() {
        assertTrue(LongReads.isLiveBlog(item("Nahost-Liveblog: ++ Waffenruhe hält ++")))
        assertTrue(LongReads.isLiveBlog(item("Krieg in der Ukraine im Live-Ticker")))
        assertTrue(LongReads.isLiveBlog(item("LIVE: Bundestag debattiert den Haushalt")))
        assertTrue(LongReads.isLiveBlog(item("Election results: live updates")))
        assertTrue(LongReads.isLiveBlog(item("Grève : suivez la journée en direct")))
        assertTrue(LongReads.isLiveBlog(item("Wahl in den USA", url = "https://www.spiegel.de/ausland/liveblog-zur-us-wahl-a-123")))
        assertTrue(LongReads.isLiveBlog(item("US election", url = "https://www.bbc.co.uk/news/live/world-123")))
        assertFalse(LongReads.isLiveBlog(item("Bundestag beschließt Haushalt")))
        assertFalse(LongReads.isLiveBlog(item("Konzert: Die Band spielt live in Berlin")))
        assertFalse(LongReads.isLiveBlog(item("Leben mit Demenz", url = "https://www.zeit.de/wissen/live-mit-demenz-leben")))
    }
}

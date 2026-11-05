package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoogleNewsTest {

    @Test
    fun `feed urls carry the edition`() {
        assertEquals("https://news.google.com/rss?hl=de&gl=DE&ceid=DE:de", GoogleNews.topStoriesUrl(NewsEdition.GERMANY))
        assertEquals(
            "https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=en-US&gl=US&ceid=US:en",
            GoogleNews.sectionUrl(NewsSection.BUSINESS, NewsEdition.USA),
        )
        val geo = GoogleNews.geoUrl("Washington, District of Columbia", NewsEdition.USA).toHttpUrl()
        assertEquals(listOf("rss", "headlines", "section", "geo", "Washington, District of Columbia"), geo.pathSegments)
        val search = GoogleNews.searchUrl("\"Wall Street\" OR DAX -site:zeit.de", NewsEdition.GERMANY).toHttpUrl()
        assertEquals("/rss/search", search.encodedPath)
        assertEquals("\"Wall Street\" OR DAX -site:zeit.de", search.queryParameter("q"))
        assertEquals("DE:de", search.queryParameter("ceid"))
        assertEquals("fr-BE", GoogleNews.searchUrl("x", NewsEdition("BE", "fr")).toHttpUrl().queryParameter("hl"))
    }

    @Test
    fun `queries get exclusions and date windows`() {
        val window = GoogleNews.DateWindow(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 27))
        assertEquals(
            "after:2026-10-20 before:2026-10-27 Midterms -site:nytimes.com -site:wsj.com",
            GoogleNews.buildQuery("  Midterms ", listOf("www.nytimes.com", "wsj.com", "nytimes.com"), window),
        )
        val many = (1..30).map { "site$it.com" }
        assertEquals(GoogleNews.MAX_EXCLUSIONS, GoogleNews.buildQuery("x", many).split(' ').count { it.startsWith("-site:") })
        assertEquals("a -site:zeit.de -site:bild.de", GoogleNews.buildQuery("a -site:zeit.de", listOf("zeit.de", "bild.de")))
        assertEquals("x", GoogleNews.buildQuery("x"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a window must not end before it starts`() {
        GoogleNews.DateWindow(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 20))
    }

    @Test
    fun `exclusions are ranked for the edition`() {
        val german = GoogleNews.rankExclusions(KnownWalls.DOMAINS, NewsEdition.GERMANY)
        assertTrue(german.take(7).all { it.endsWith(".de") || it == "faz.net" })
        assertEquals("youtube.com", german[7])
        assertTrue(german.takeLast(5).all { it in setOf("tiktok.com", "instagram.com", "facebook.com", "x.com", "twitter.com") })
        val american = GoogleNews.rankExclusions(KnownWalls.DOMAINS, NewsEdition.USA)
        assertEquals("nytimes.com", american.first())
        assertTrue("reuters.com" in american.take(GoogleNews.MAX_EXCLUSIONS))
        assertTrue(american.indexOf("zeit.de") > american.indexOf("seekingalpha.com"))
        assertTrue(american.indexOf("faz.net") > american.indexOf("youtube.com"))
        val british = GoogleNews.rankExclusions(KnownWalls.DOMAINS, NewsEdition("GB", "en"))
        assertEquals(listOf("thetimes.co.uk", "telegraph.co.uk"), british.take(2))
        // Learned domains keep their order within a rank.
        assertEquals(listOf("b.de", "a.de", "x.com"), GoogleNews.rankExclusions(listOf("x.com", "b.de", "a.de"), NewsEdition.GERMANY))
    }

    @Test
    fun `article links are recognized`() {
        val link = "https://news.google.com/rss/articles/CBMic0FVX3lxTE45eE5aUXhYMVFkZ0lRNzk1UTdrUkFY?oc=5"
        assertEquals("CBMic0FVX3lxTE45eE5aUXhYMVFkZ0lRNzk1UTdrUkFY", GoogleNews.articleId(link))
        assertTrue(GoogleNews.isArticleLink("https://news.google.com/articles/CBMic0FVX3lxTE45eE5aUXhYMVFk?hl=de"))
        assertFalse(GoogleNews.isArticleLink("https://www.tagesschau.de/rss/articles/CBMic0FVX3lxTE45eE5aUXhYMVFk"))
        assertNull(GoogleNews.articleId("https://news.google.com/stories/CAAqNggKIjBDQklTSGpvSmMzUnZjbmt0"))
        assertNull(GoogleNews.articleId("nonsense"))
    }

    @Test
    fun `the publisher suffix leaves the headline`() {
        assertEquals("Dax springt auf Rekordhoch", GoogleNews.stripPublisher("Dax springt auf Rekordhoch - Spiegel", "Spiegel"))
        assertEquals(
            "Leipzigs Ehrendirigent Herbert Blomstedt ist gestorben",
            GoogleNews.stripPublisher("Leipzigs Ehrendirigent Herbert Blomstedt ist gestorben - LVZ - Leipziger Volkszeitung", "LVZ - Leipziger Volkszeitung"),
        )
        // A dash inside the headline stays when the publisher is known.
        assertEquals("Berlin - Bundestag debattiert", GoogleNews.stripPublisher("Berlin - Bundestag debattiert - Deutschlandfunk", "Deutschlandfunk"))
        assertEquals("Ohne Quelle", GoogleNews.stripPublisher("Ohne Quelle - BBC", ""))
        assertEquals("Nur ein Titel", GoogleNews.stripPublisher("Nur ein Titel", "BBC"))
        assertEquals("BBC", GoogleNews.stripPublisher("BBC", "BBC"))
    }

    @Test
    fun `domains and teasers`() {
        assertEquals("bbc.co.uk", GoogleNews.domainOf("https://www.bbc.co.uk/news/x"))
        assertEquals("", GoogleNews.domainOf("kein link"))
        assertEquals("", GoogleNews.teaser("Dax springt auf Rekordhoch  Spiegel", "Dax springt auf Rekordhoch", "Spiegel"))
        assertEquals("", GoogleNews.teaser("Eins RND.de\nZwei Spiegel\nDrei ZEIT", "Eins", "RND.de"))
        val real = "Der Leitindex legte am Donnerstag um mehr als zwei Prozent zu, getragen von Banken, Autowerten und Versorgern."
        assertEquals(real, GoogleNews.teaser("Dax steigt $real", "Dax steigt", ""))
    }

    @Test
    fun `quote pages and subscriber-only headlines are recognized`() {
        assertTrue(GoogleNews.isQuotePage("DAX Turbo S 27083.00813 open end (SG) | Kurs, Chart, Analysen | DE000FG2TZV5"))
        assertFalse(GoogleNews.isQuotePage("Dax steigt auf Rekordhoch – drei Treiber für den Leitindex"))
        assertFalse(GoogleNews.isQuotePage("NASA SPACEX launch delayed to 2027"))
        assertTrue(GoogleNews.isPaywallHeadline("(S+) Elon Musk wollte die US-Midterms für Trump retten"))
        assertTrue(GoogleNews.isPaywallHeadline("Z+ Wie die Demokraten gewannen"))
        assertTrue(GoogleNews.isPaywallHeadline("Analyse: WELTplus – Was jetzt kommt"))
        assertFalse(GoogleNews.isPaywallHeadline("Zverev verliert in Paris"))
        assertFalse(GoogleNews.isPaywallHeadline("C++ 26 bringt Reflection"))
    }
}

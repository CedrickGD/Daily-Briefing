package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** The search suggestions: Google Trends' RSS, the headlines as fallback, two examples per start. */
class TrendingTermsTest {

    private val parser = RssParser { KXmlParser() }

    /** Google Trends' daily RSS as it came on 23.11.2026 (trimmed): `ht:` fields around every term. */
    private val trendsFeed = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <rss xmlns:atom="http://www.w3.org/2005/Atom" xmlns:ht="https://trends.google.com/trending/rss" version="2.0">
          <channel>
            <title>Daily Search Trends</title>
            <description>Recent searches</description>
            <link>https://trends.google.com/trending/rss?geo=DE</link>
            <atom:link href="https://trends.google.com/trending/rss?geo=DE" rel="self" type="application/rss+xml"/>
            <item>
              <title>champions league</title>
              <ht:approx_traffic>2000+</ht:approx_traffic>
              <description/>
              <link>https://trends.google.com/trending/rss?geo=DE</link>
              <pubDate>Mon, 23 Nov 2026 10:50:00 -0800</pubDate>
              <ht:picture>https://encrypted-tbn3.gstatic.com/images?q=tbn:x</ht:picture>
              <ht:picture_source>Eurosport</ht:picture_source>
              <ht:news_item>
                <ht:news_item_title>Bayern gewinnt in Paris: &quot;Ein großer Abend&quot;</ht:news_item_title>
                <ht:news_item_snippet/>
                <ht:news_item_url>https://www.example.de/cl</ht:news_item_url>
                <ht:news_item_picture>https://encrypted-tbn3.gstatic.com/images?q=tbn:y</ht:news_item_picture>
                <ht:news_item_source>Eurosport</ht:news_item_source>
              </ht:news_item>
            </item>
            <item>
              <title>christian sewing</title>
              <ht:approx_traffic>200+</ht:approx_traffic>
              <description/>
              <link>https://trends.google.com/trending/rss?geo=DE</link>
              <pubDate>Mon, 23 Nov 2026 10:40:00 -0800</pubDate>
            </item>
            <item>
              <title>الأهلي ضد الأهرام</title>
              <ht:approx_traffic>200+</ht:approx_traffic>
            </item>
            <item>
              <title>Champions League</title>
              <ht:approx_traffic>100+</ht:approx_traffic>
            </item>
            <item>
              <title>mt melsungen</title>
              <ht:approx_traffic>200+</ht:approx_traffic>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private fun feed(): ParsedFeed = parser.parse(trendsFeed.byteInputStream(Charsets.UTF_8))

    @Test
    fun `the terms of the trends feed, in Google's order, each once`() {
        val terms = TrendingTerms.fromFeed(feed(), "de")
        // The nested news items do not count as terms; the Arabic search is no example for a German edition.
        assertEquals(listOf("champions league", "christian sewing", "mt melsungen"), terms)
        // An edition in another script keeps every term.
        assertEquals(4, TrendingTerms.fromFeed(feed(), "ja").size)
    }

    @Test
    fun `sentences and blanks are no terms`() {
        val raw = listOf("  bali ", "", "x".repeat(41), "bali", "sylt\n1")
        assertEquals(listOf("bali", "sylt 1"), TrendingTerms.clean(raw, "de"))
        assertEquals(TrendingTerms.MAX_TERMS, TrendingTerms.clean((1..30).map { "term $it" }, "de").size)
    }

    @Test
    fun `without trends the subjects the headlines name before their colon`() {
        val headlines = listOf(
            "Champions League: Bayern siegt in Paris",
            "Liveblog: Die Lage in der Ukraine",
            "+++ Ticker +++: Was heute wichtig ist",
            "Bundestag beschließt Haushalt",
            "Ein sehr langer Satz mit vielen Wörtern: und dann",
            "Ukraine-Krieg: Neue Gespräche in Genf",
            "champions league: Das Rückspiel",
            "2026: Ein Jahr",
            "„Tatort“: Kritik am Sonntagskrimi",
        )
        assertEquals(listOf("Champions League", "Ukraine-Krieg", "Tatort"), TrendingTerms.fromHeadlines(headlines))
        assertTrue(TrendingTerms.fromHeadlines(listOf("Kein Doppelpunkt", "")).isEmpty())
    }

    @Test
    fun `two short examples, the same ones for the same start`() {
        val terms = listOf("champions league", "christian sewing", "johann andré forfang und mehr", "bali", "mt melsungen")
        val first = TrendingTerms.examples(terms, Random(7))
        assertEquals(TrendingTerms.EXAMPLES, first.size)
        assertTrue(first.all { it in terms && it.length <= TrendingTerms.MAX_EXAMPLE_LENGTH })
        assertEquals(first, TrendingTerms.examples(terms, Random(7)))
        // Other starts pick others (over a few seeds, more than one pair comes up).
        assertTrue((1..10).map { TrendingTerms.examples(terms, Random(it)).toSet() }.toSet().size > 1)
        // Fewer terms than examples: what there is; none: the bar's fixed examples (empty here).
        assertEquals(listOf("bali"), TrendingTerms.examples(listOf("bali"), Random(1)))
        assertTrue(TrendingTerms.examples(emptyList(), Random(1)).isEmpty())
    }

    @Test
    fun `the suggestions fall back to the headlines`() {
        val trends = TrendingRepository.suggestionsOf(listOf("bali", "sylt"), listOf("Haushalt: Einigung"), Random(1))
        assertTrue(trends.fromTrends)
        assertEquals(listOf("bali", "sylt"), trends.terms)
        val headlines = TrendingRepository.suggestionsOf(emptyList(), listOf("Haushalt: Einigung", "Wetter: Schnee"), Random(1))
        assertFalse(headlines.fromTrends)
        assertEquals(listOf("Haushalt", "Wetter"), headlines.terms)
        assertEquals(2, headlines.examples.size)
        val nothing = TrendingRepository.suggestionsOf(emptyList(), listOf("Kein Thema"), Random(1))
        assertTrue(nothing.terms.isEmpty() && nothing.examples.isEmpty())
        assertEquals("https://trends.google.com/trending/rss?geo=AT", TrendingRepository.urlFor("at"))
    }

    @Test
    fun `the last trends of the country stay for a start without network`() = runBlocking {
        val dir = Files.createTempDirectory("trending").toFile()
        val file = File(dir, "trending.json")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var now = Instant.parse("2026-11-23T08:00:00Z")
            val headlines = MutableStateFlow(listOf("Haushalt: Einigung"))
            val online = TrendingRepository({ feed() }, file, AppJson, headlines, scope, clock = { now })
            online.load("DE", "de")
            assertEquals("champions league", online.suggestions.first { it.fromTrends }.terms.first())
            assertTrue(file.isFile)

            val offline = { TrendingRepository({ throw IOException("offline") }, file, AppJson, headlines, scope, clock = { now }) }
            // The next start, offline: the cached terms.
            val next = offline()
            next.load("DE", "de")
            assertTrue(next.suggestions.value.fromTrends)
            // Another country: not its trends – the headlines instead.
            val austria = offline()
            austria.load("AT", "de")
            assertEquals(listOf("Haushalt"), austria.suggestions.value.terms)
            // Two days later the cache is too old.
            now = now.plus(Duration.ofDays(3))
            val later = offline()
            later.load("DE", "de")
            assertFalse(later.suggestions.value.fromTrends)
        } finally {
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}

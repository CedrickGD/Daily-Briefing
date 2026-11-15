package com.cedrickgd.morgenbriefing.data.remote

import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.SourceKind
import com.cedrickgd.morgenbriefing.data.news.GoogleNews
import com.cedrickgd.morgenbriefing.data.news.RssParser
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.kxml2.io.KXmlParser

class GoogleNewsClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: RssClient
    private val seen = mutableListOf<String>()

    private val source = FeedSource(
        id = "gn:wirtschaft/boerse@DE:de",
        name = GoogleNews.SOURCE_NAME,
        url = GoogleNews.searchUrl("DAX OR Börse", com.cedrickgd.morgenbriefing.data.model.NewsEdition.GERMANY),
        topicId = "wirtschaft/boerse",
        kind = SourceKind.GOOGLE_NEWS,
        edition = "DE:de",
    )

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = RssClient(GoogleNewsFixtures.redirectingClient(server, seen), RssParser { KXmlParser() })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `google news items name their publisher and carry no fake teaser`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                GoogleNewsFixtures.feed(
                    GoogleNewsFixtures.item("Dax springt auf Rekordhoch", "Spiegel", "https://www.spiegel.de", "CBMiAAAAAAAAAAAAAAAAAAAAAAAA1"),
                    GoogleNewsFixtures.item("Leipzigs Ehrendirigent ist gestorben", "LVZ - Leipziger Volkszeitung", "https://www.lvz.de", "CBMiAAAAAAAAAAAAAAAAAAAAAAAA2"),
                    GoogleNewsFixtures.TOP_STORY,
                ),
            ),
        )
        val items = client.fetch(source)
        assertEquals(3, items.size)
        val first = items[0]
        assertEquals("Dax springt auf Rekordhoch", first.headline)
        assertEquals("Spiegel", first.source)
        assertEquals("spiegel.de", first.publisherDomain)
        assertEquals(GoogleNews.AGGREGATOR, first.aggregator)
        assertEquals("", first.summary)
        assertNull(first.image)
        assertEquals("https://news.google.com/rss/articles/CBMiAAAAAAAAAAAAAAAAAAAAAAAA1?oc=5", first.url)
        assertEquals(source.id, first.sourceId)
        assertEquals("wirtschaft/boerse", first.topicId)
        assertEquals("2026-11-05T12:00:15+01:00", first.publishedAt)
        assertEquals("Leipzigs Ehrendirigent ist gestorben", items[1].headline)
        assertEquals("LVZ - Leipziger Volkszeitung", items[1].source)
        assertEquals("Zwischenwahlen in den USA: Alle Ergebnisse im Überblick", items[2].headline)
        assertEquals("", items[2].summary)
        assertEquals("DAX OR Börse", seen.single().toHttpUrl().queryParameter("q"))
        assertTrue(server.takeRequest().getHeader("User-Agent")!!.contains("DailyBriefing/3.0"))
    }

    @Test
    fun `an empty search is no error`() = runTest {
        server.enqueue(MockResponse().setBody(GoogleNewsFixtures.feed()))
        assertTrue(client.fetch(source).isEmpty())
    }

    @Test(expected = FeedException::class)
    fun `an empty publisher feed still is`() = runTest {
        server.enqueue(MockResponse().setBody(GoogleNewsFixtures.feed()))
        client.fetch(source.copy(kind = SourceKind.RSS))
    }

    @Test
    fun `publisher items get their domain from the link`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """<rss version="2.0"><channel><title>BBC</title><item><title>A story</title>
                   <link>https://www.bbc.co.uk/news/articles/c1?at_medium=RSS</link><description>Teaser text.</description></item></channel></rss>""",
            ),
        )
        val item = client.fetch(FeedSource("bbc-top", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "top")).single()
        assertEquals("bbc.co.uk", item.publisherDomain)
        assertNull(item.aggregator)
        assertEquals("BBC News", item.source)
        assertEquals("Teaser text.", item.summary)
    }

    // ---- A place searched in the news language, with its place page in its own language behind it ----

    private val english = com.cedrickgd.morgenbriefing.data.model.NewsEdition.USA
    private val place = FeedSource(
        id = "gn:local:boise@US:de",
        name = GoogleNews.SOURCE_NAME,
        url = GoogleNews.searchUrl("when:14d \"Boise\"", com.cedrickgd.morgenbriefing.data.model.NewsEdition("US", "de")),
        topicId = "local:boise",
        kind = SourceKind.GOOGLE_NEWS,
        edition = "US:de",
        fallback = FeedSource(
            id = "gn:local:boise@US:en",
            name = GoogleNews.SOURCE_NAME,
            url = GoogleNews.geoUrl("Boise, Idaho", english),
            topicId = "local:boise",
            kind = SourceKind.GOOGLE_NEWS,
            edition = "US:en",
        ),
    )

    private fun stories(count: Int, prefix: String) = GoogleNewsFixtures.feed(
        *(1..count).map { GoogleNewsFixtures.item("$prefix Nummer $it aus der Stadt", "Zeitung", "https://www.zeitung.example", "CBMiAAAAAAAAAAAAAAAAAAAA$prefix$it") }.toTypedArray(),
    )

    @Test
    fun `too little about a place in the news language adds the place's own language`() = runTest {
        server.enqueue(MockResponse().setBody(stories(3, "Deutsch")))
        server.enqueue(MockResponse().setBody(stories(20, "English")))
        val items = client.fetch(place)
        // The few German stories stay in front; the English ones carry the id of their own source.
        assertEquals(23, items.size)
        assertTrue(items.take(3).all { it.sourceId == "gn:local:boise@US:de" && it.headline.startsWith("Deutsch") })
        assertTrue(items.drop(3).all { it.sourceId == "gn:local:boise@US:en" && it.headline.startsWith("English") })
        assertTrue(items.all { it.topicId == "local:boise" })
        assertEquals(2, seen.size)
        assertEquals("/rss/search", seen[0].toHttpUrl().encodedPath)
        assertEquals("de", seen[0].toHttpUrl().queryParameter("hl"))
        assertTrue(seen[1], "/geo/" in seen[1] && seen[1].toHttpUrl().queryParameter("ceid") == "US:en")
    }

    @Test
    fun `nothing at all in the news language still shows the place`() = runTest {
        server.enqueue(MockResponse().setBody(GoogleNewsFixtures.feed()))
        server.enqueue(MockResponse().setBody(stories(5, "English")))
        assertEquals(5, client.fetch(place).size)
    }

    @Test
    fun `enough in the news language is read alone`() = runTest {
        server.enqueue(MockResponse().setBody(stories(FeedSource.ENOUGH, "Deutsch")))
        val items = client.fetch(place)
        assertEquals(FeedSource.ENOUGH, items.size)
        assertTrue(items.all { it.sourceId == "gn:local:boise@US:de" })
        assertEquals(1, seen.size)
    }

    @Test
    fun `a failing fallback keeps what the news language brought`() = runTest {
        server.enqueue(MockResponse().setBody(stories(2, "Deutsch")))
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(2, client.fetch(place).size)
    }

    @Test(expected = FeedException::class)
    fun `a failing search is a failure, whatever its fallback`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        client.fetch(place)
    }
}

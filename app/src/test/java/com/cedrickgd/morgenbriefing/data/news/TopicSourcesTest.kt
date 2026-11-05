package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.SourceKind
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicSourcesTest {

    private val germany = AppSettings(edition = NewsEdition.GERMANY)
    private val usa = AppSettings(edition = NewsEdition.USA, home = Place("Austin", 30.27, -97.74, "Texas, Vereinigte Staaten", "US"))
    private val dc = Place("Washington", 38.895, -77.036, "District of Columbia, Vereinigte Staaten", "US")

    private fun google(sources: List<FeedSource>) = sources.single { it.kind == SourceKind.GOOGLE_NEWS }

    @Test
    fun `a category reads its publisher feeds, those of its sub-topics and its Google News section`() {
        val sources = TopicSources.resolve("wirtschaft", germany)
        val ids = sources.map { it.id }
        assertTrue("tagesschau-wirtschaft" in ids)
        assertTrue("tagesschau-finanzen" in ids) // from wirtschaft/boerse
        assertTrue("btc-echo" in ids) // from wirtschaft/krypto
        assertTrue(sources.all { it.topicId == "wirtschaft" })
        val gn = google(sources)
        assertEquals("gn:wirtschaft@DE:de", gn.id)
        assertEquals("DE:de", gn.edition)
        assertEquals("https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=de&gl=DE&ceid=DE:de", gn.url)
        assertTrue(TopicSources.resolve("top", germany).any { it.url == GoogleNews.topStoriesUrl(NewsEdition.GERMANY) })
    }

    @Test
    fun `a sub-topic reads its feeds and a Google News search without the walled sites`() {
        val sources = TopicSources.resolve("wirtschaft/boerse", germany)
        assertEquals(listOf("tagesschau-finanzen", "ntv-boerse"), sources.filterNot { it.isGoogleNews }.map { it.id })
        val q = google(sources).url.toHttpUrl().queryParameter("q")!!
        assertTrue(q, q.startsWith("DAX OR Börse OR Aktien"))
        assertTrue(q, "-site:zeit.de" in q && "-site:faz.net" in q)
        assertEquals(GoogleNews.MAX_EXCLUSIONS, q.split(' ').count { it.startsWith("-site:") })
        val open = google(TopicSources.resolve("wirtschaft/boerse", germany.copy(readableOnly = false))).url.toHttpUrl().queryParameter("q")!!
        assertFalse(open, "site:" in open)
        // Learned walls join the seed list.
        val learned = google(TopicSources.resolve("wirtschaft/boerse", germany, blocked = listOf("boerse-online.de"))).url
        assertTrue(learned, "boerse-online.de" in learned.toHttpUrl().queryParameter("q")!!)
    }

    @Test
    fun `an American reader gets English feeds and English searches`() {
        val boerse = TopicSources.resolve("wirtschaft/boerse", usa)
        assertTrue(boerse.filterNot { it.isGoogleNews }.isEmpty())
        val url = google(boerse).url.toHttpUrl()
        assertEquals("US:en", url.queryParameter("ceid"))
        assertTrue(url.queryParameter("q")!!.contains("\"Wall Street\""))
        assertTrue(url.queryParameter("q")!!.contains("-site:nytimes.com"))
        val politics = TopicSources.resolve("politik", usa).map { it.id }
        assertTrue("npr-politics" in politics && "tagesschau-inland" !in politics)
    }

    @Test
    fun `the region is the Bundesland in Germany plus the home place, elsewhere only the home place`() {
        val home = Place("Bad Düben", 51.59, 12.58, "Sachsen, Deutschland", "DE")
        val german = TopicSources.resolve(Topics.REGION_ID, germany.copy(region = "sachsen", home = home))
        assertEquals(listOf("mdr-sachsen", "lvz", "l-iz"), german.filterNot { it.isGoogleNews }.map { it.id })
        assertEquals(GoogleNews.geoUrl("Bad Düben, Sachsen", NewsEdition.GERMANY), google(german).url)
        val american = TopicSources.resolve(Topics.REGION_ID, usa)
        assertEquals(GoogleNews.geoUrl("Austin, Texas", NewsEdition.USA), american.single().url)
        // Living in Vienna while reading the German edition: Austrian local news.
        val vienna = TopicSources.resolve(Topics.REGION_ID, germany.copy(home = Place("Wien", 48.2, 16.37, "Wien, Österreich", "AT")))
        assertEquals(GoogleNews.geoUrl("Wien", NewsEdition("AT", "de")), google(vienna).url)
    }

    @Test
    fun `a place abroad reads the edition of its country`() {
        val id = Topics.localId(dc)
        val settings = germany.copy(localPlaces = listOf(dc), followed = listOf(id))
        val source = TopicSources.resolve(id, settings).single()
        assertEquals(GoogleNews.geoUrl("Washington, District of Columbia", NewsEdition.USA), source.url)
        assertEquals("US:en", source.edition)
        assertEquals(NewsEdition.USA, TopicSources.editionFor(id, settings))
        assertEquals(PagingQuery("\"Washington\"", NewsEdition.USA, "gn:$id@US:en"), TopicSources.pagingQuery(id, settings))
        assertTrue(TopicSources.resolve("local:gibts-nicht", settings).isEmpty())
    }

    @Test
    fun `own topics read their feeds and their search`() {
        val search = Topic("custom:m", "Midterms", "🔎", 0xFF2F6FDE, custom = true, query = "Midterms")
        val feeds = Topic("custom:f", "Blog", "🦉", 0xFF2F6FDE, custom = true)
        val blog = FeedSource("custom:b", "Blog", "https://blog.example/feed", "custom:f", builtIn = false)
        val settings = usa.copy(customTopics = listOf(search, feeds), customFeeds = listOf(blog))
        val gn = TopicSources.resolve("custom:m", settings).single()
        assertTrue(gn.url.toHttpUrl().queryParameter("q")!!.startsWith("Midterms -site:"))
        assertEquals(listOf(blog), TopicSources.resolve("custom:f", settings))
        assertEquals(PagingQuery("Midterms", NewsEdition.USA, "gn:custom:m@US:en"), TopicSources.pagingQuery("custom:m", settings))
        assertNull(TopicSources.pagingQuery("custom:f", settings))
        // A user feed of a blocked site stays: the user chose it.
        val walled = FeedSource("custom:z", "ZEIT", "https://newsfeed.zeit.de/index", "custom:f", builtIn = false)
        assertTrue(TopicSources.resolve("custom:f", settings.copy(customFeeds = listOf(walled))).isNotEmpty())
    }

    @Test
    fun `switched-off sources and paging`() {
        val off = germany.copy(disabledFeeds = setOf("gn:wirtschaft/boerse@DE:de", "ntv-boerse"))
        assertEquals(listOf("tagesschau-finanzen"), TopicSources.resolve("wirtschaft/boerse", off).map { it.id })
        assertEquals(3, TopicSources.resolve("wirtschaft/boerse", off, includeDisabled = true).size)
        assertNull(TopicSources.pagingQuery(Topics.TOP_ID, germany))
        val paging = TopicSources.pagingQuery("wirtschaft/boerse", germany)!!
        assertTrue(paging.query.startsWith("DAX OR"))
        assertEquals("gn:wirtschaft/boerse@DE:de", paging.sourceId)
        assertEquals("\"Berlin\"", TopicSources.pagingQuery(Topics.REGION_ID, germany)?.query)
    }

    @Test
    fun `geo names disambiguate with the first region part`() {
        assertEquals("Washington, District of Columbia", TopicSources.geoName(dc))
        assertEquals("Wien", TopicSources.geoName(Place("Wien", 48.2, 16.37, "Wien, Österreich", "AT")))
        assertEquals("Paris", TopicSources.geoName(Place("Paris", 48.85, 2.35, "Frankreich", "FR")))
        assertEquals("Paris", TopicSources.geoName(Place("Paris", 48.85, 2.35, "", "FR")))
    }
}

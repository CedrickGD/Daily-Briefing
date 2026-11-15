package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.article.KnownWalls
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
        // LVZ sits behind a bot wall (KnownWalls): with "readable only" (the default) its feed is left out.
        assertEquals(listOf("mdr-sachsen", "l-iz"), german.filterNot { it.isGoogleNews }.map { it.id })
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
        // Older pages search the same place as page one, not every Washington.
        assertEquals(PagingQuery("\"Washington\" \"District of Columbia\"", NewsEdition.USA, "gn:$id@US:en"), TopicSources.pagingQuery(id, settings))
        assertTrue(TopicSources.resolve("local:gibts-nicht", settings).isEmpty())
    }

    @Test
    fun `older pages of a place name its state in the edition's language`() {
        val la = Place("Los Angeles", 34.05, -118.24, "Kalifornien, Vereinigte Staaten", "US")
        assertEquals("\"Los Angeles\" \"California\"", TopicSources.placeQuery(la, NewsEdition.USA))
        val mumbai = Place("Mumbai", 19.07, 72.88, "Bundesstaat Maharashtra, Indien", "IN")
        assertEquals("\"Mumbai\" \"Maharashtra\"", TopicSources.placeQuery(mumbai, NewsEdition("IN", "en")))
        val halle = Place("Halle (Saale)", 51.48, 11.97, "Sachsen-Anhalt, Deutschland", "DE")
        assertEquals("\"Halle (Saale)\" \"Sachsen-Anhalt\"", TopicSources.placeQuery(halle, NewsEdition.GERMANY))
        // The region of a city state is the city; Britain's "England" and other languages' states narrow nothing.
        assertEquals("\"Wien\"", TopicSources.placeQuery(Place("Wien", 48.2, 16.37, "Wien, Österreich", "AT"), NewsEdition("AT", "de")))
        assertEquals("\"Manchester\"", TopicSources.placeQuery(Place("Manchester", 53.48, -2.24, "England, Vereinigtes Königreich", "GB"), NewsEdition("GB", "en")))
        assertEquals("\"Barcelona\"", TopicSources.placeQuery(Place("Barcelona", 41.39, 2.17, "Katalonien, Spanien", "ES"), NewsEdition("ES", "es")))
        // The region topic pages the home place the same way.
        assertEquals("\"Austin\" \"Texas\"", TopicSources.pagingQuery(Topics.REGION_ID, usa)?.query)
    }

    @Test
    fun `own topics read their feeds and their search`() {
        val search = Topic("custom:m", "Midterms", "🔎", 0xFF2F6FDE, custom = true, query = "Midterms")
        val feeds = Topic("custom:f", "Blog", "🦉", 0xFF2F6FDE, custom = true)
        val blog = FeedSource("custom:b", "Blog", "https://blog.example/feed", "custom:f", builtIn = false)
        val settings = usa.copy(customTopics = listOf(search, feeds), customFeeds = listOf(blog))
        val gn = TopicSources.resolve("custom:m", settings).single()
        // No -site: exclusions: they make Google News answer loosely; walled sites go by domain.
        assertEquals("Midterms", gn.url.toHttpUrl().queryParameter("q"))
        assertTrue(TopicSources.searchExclusions("custom:m", settings, NewsEdition.USA, KnownWalls.DOMAINS).isEmpty())
        assertTrue(TopicSources.searchExclusions("wirtschaft/boerse", settings, NewsEdition.USA, KnownWalls.DOMAINS).isNotEmpty())
        assertEquals(listOf(blog), TopicSources.resolve("custom:f", settings))
        assertEquals(PagingQuery("Midterms", NewsEdition.USA, "gn:custom:m@US:en"), TopicSources.pagingQuery("custom:m", settings))
        assertNull(TopicSources.pagingQuery("custom:f", settings))
        // A user feed of a blocked site stays: the user chose it.
        val walled = FeedSource("custom:z", "ZEIT", "https://newsfeed.zeit.de/index", "custom:f", builtIn = false)
        assertTrue(TopicSources.resolve("custom:f", settings.copy(customFeeds = listOf(walled))).isNotEmpty())
    }

    @Test
    fun `a followed search reads like the search screen - exact ticker, English editions too`() {
        val slv = Topic("custom:slv", "SLV ETF", "🔎", 0xFF2F6FDE, custom = true, query = "SLV ETF")
        val settings = germany.copy(customTopics = listOf(slv))
        // The settings list the search once; the English editions come along with it.
        val main = TopicSources.resolve("custom:slv", settings).single()
        val extra = TopicSources.extraEditions("custom:slv", settings)
        val sources = listOf(main) + extra
        assertEquals(listOf("gn:custom:slv@DE:de", "gn:custom:slv@US:en", "gn:custom:slv@GB:en"), sources.map { it.id })
        assertEquals(listOf("DE:de", "US:en", "GB:en"), sources.map { it.url.toHttpUrl().queryParameter("ceid") })
        assertTrue(sources.all { it.url.toHttpUrl().queryParameter("q") == "\"SLV\" ETF" })
        // Only the main edition pages into the past, with the same query.
        assertEquals(PagingQuery("\"SLV\" ETF", NewsEdition.GERMANY, "gn:custom:slv@DE:de"), TopicSources.pagingQuery("custom:slv", settings))
        // Switching the search off switches its English editions off; English main editions and other topics have none.
        assertTrue(TopicSources.extraEditions("custom:slv", settings.copy(disabledFeeds = setOf(main.id))).isEmpty())
        assertTrue(TopicSources.extraEditions("custom:slv", settings.copy(edition = NewsEdition.USA)).isEmpty())
        assertTrue(TopicSources.extraEditions("wirtschaft/boerse", settings).isEmpty())
    }

    @Test
    fun `switched-off sources and paging`() {
        val off = germany.copy(disabledFeeds = setOf("gn:wirtschaft/boerse@DE:de", "ntv-boerse"))
        assertEquals(listOf("tagesschau-finanzen"), TopicSources.resolve("wirtschaft/boerse", off).map { it.id })
        assertEquals(3, TopicSources.resolve("wirtschaft/boerse", off, includeDisabled = true).size)
        // The top stories page with the headline desks' query of the edition's language …
        val top = TopicSources.pagingQuery(Topics.TOP_ID, germany)!!
        assertTrue(top.query.startsWith("Politik OR"))
        assertEquals("gn:top@DE:de", top.sourceId)
        assertTrue(TopicSources.pagingQuery(Topics.TOP_ID, germany.copy(edition = NewsEdition.USA))!!.query.startsWith("politics OR"))
        // … not where only the English fallback would answer, and not with their Google News source off.
        assertNull(TopicSources.pagingQuery(Topics.TOP_ID, germany.copy(edition = NewsEdition("NL", "nl"))))
        assertNull(TopicSources.pagingQuery(Topics.TOP_ID, germany.copy(disabledFeeds = setOf("gn:top@DE:de"))))
        val paging = TopicSources.pagingQuery("wirtschaft/boerse", germany)!!
        assertTrue(paging.query.startsWith("DAX OR"))
        assertEquals("gn:wirtschaft/boerse@DE:de", paging.sourceId)
        assertEquals("\"Berlin\"", TopicSources.pagingQuery(Topics.REGION_ID, germany)?.query)
        // With its Google News source off a topic has nothing to page with.
        assertNull(TopicSources.pagingQuery("wirtschaft/boerse", off))
        assertNull(TopicSources.pagingQuery(Topics.REGION_ID, germany.copy(disabledFeeds = setOf("gn:region@DE:de"))))
    }

    @Test
    fun `geo names disambiguate with the first region part`() {
        assertEquals("Washington, District of Columbia", TopicSources.geoName(dc))
        assertEquals("Wien", TopicSources.geoName(Place("Wien", 48.2, 16.37, "Wien, Österreich", "AT")))
        assertEquals("Paris", TopicSources.geoName(Place("Paris", 48.85, 2.35, "Frankreich", "FR")))
        assertEquals("Paris", TopicSources.geoName(Place("Paris", 48.85, 2.35, "", "FR")))
    }
}

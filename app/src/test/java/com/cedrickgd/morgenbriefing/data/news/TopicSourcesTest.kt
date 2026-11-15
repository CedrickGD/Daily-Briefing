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
    fun `a place in a country Google News has in the news language reads its place page`() {
        val id = Topics.localId(dc)
        val settings = usa.copy(localPlaces = listOf(dc), followed = listOf(id))
        val source = TopicSources.resolve(id, settings).single()
        assertEquals(GoogleNews.geoUrl("Washington, District of Columbia", NewsEdition.USA), source.url)
        assertEquals("US:en", source.edition)
        assertNull(source.fallback)
        assertEquals(NewsEdition.USA, TopicSources.editionFor(id, settings))
        // Older pages search the same place as page one, not every Washington.
        assertEquals(PagingQuery("\"Washington\" \"District of Columbia\"", NewsEdition.USA, "gn:$id@US:en"), TopicSources.pagingQuery(id, settings))
        assertTrue(TopicSources.resolve("local:gibts-nicht", settings).isEmpty())
        // A country that speaks the news language abroad: its own edition (London for an American, Vienna for a German).
        val london = Place("London", 51.51, -0.13, "England, Vereinigtes Königreich", "GB")
        val british = TopicSources.resolve(Topics.localId(london), usa.copy(localPlaces = listOf(london))).single()
        assertEquals(GoogleNews.geoUrl("London, England", NewsEdition("GB", "en")), british.url)
        assertNull(british.fallback)
        // Zurich for a reader of Swiss French stays French; too little in French adds the German page of Switzerland.
        val zurich = Place("Zürich", 47.37, 8.54, "Zürich, Schweiz", "CH")
        val swissFrench = AppSettings(edition = NewsEdition("CH", "fr"), localPlaces = listOf(zurich))
        val french = TopicSources.resolve(Topics.localId(zurich), swissFrench).single()
        assertEquals(GoogleNews.geoUrl("Zürich", NewsEdition("CH", "fr")), french.url)
        assertEquals(GoogleNews.geoUrl("Zürich", NewsEdition("CH", "de")), french.fallback!!.url)
        assertEquals("gn:${Topics.localId(zurich)}@CH:de", french.fallback!!.id)
    }

    @Test
    fun `a place abroad is read in the news language - Washington in German`() {
        val id = Topics.localId(dc)
        val settings = germany.copy(localPlaces = listOf(dc), followed = listOf(id))
        val source = TopicSources.resolve(id, settings).single()
        // The USA in German: no edition of Google News, its place page would be empty – a German search for the name.
        assertEquals(NewsEdition("US", "de"), TopicSources.editionFor(id, settings))
        assertEquals("gn:$id@US:de", source.id)
        assertEquals("US:de", source.edition)
        val url = source.url.toHttpUrl()
        assertEquals("/rss/search", url.encodedPath)
        assertEquals("when:14d \"Washington\"", url.queryParameter("q"))
        assertEquals(listOf("de", "DE", "DE:de"), listOf("hl", "gl", "ceid").map { url.queryParameter(it) })
        // Too little in German: the place page in the place's own language is read as well.
        val fallback = source.fallback!!
        assertEquals(GoogleNews.geoUrl("Washington, District of Columbia", NewsEdition.USA), fallback.url)
        assertEquals("gn:$id@US:en", fallback.id)
        assertEquals("US:en", fallback.edition)
        assertEquals(id, fallback.topicId)
        assertNull(fallback.fallback)
        // Older pages go on in German, by name (German news hardly ever name the state).
        assertEquals(PagingQuery("\"Washington\"", NewsEdition("US", "de"), "gn:$id@US:de"), TopicSources.pagingQuery(id, settings))
        // Switching the place's Google News source off switches both off.
        assertTrue(TopicSources.resolve(id, settings.copy(disabledFeeds = setOf(source.id))).isEmpty())
    }

    @Test
    fun `a place at home is read in the news language too - Leipzig in English`() {
        val leipzig = Place.LEIPZIG
        val id = Topics.localId(leipzig)
        val settings = AppSettings(edition = NewsEdition("DE", "en"), localPlaces = listOf(leipzig), home = leipzig)
        val source = TopicSources.resolve(id, settings).single()
        val url = source.url.toHttpUrl()
        assertEquals("when:14d \"Leipzig\"", url.queryParameter("q"))
        assertEquals(listOf("en-US", "US", "US:en"), listOf("hl", "gl", "ceid").map { url.queryParameter(it) })
        assertEquals("gn:$id@DE:en", source.id)
        assertEquals(GoogleNews.geoUrl("Leipzig, Sachsen", NewsEdition.GERMANY), source.fallback!!.url)
        // The "Region" topic reads the home place the same way – and no German broadcasters in an English feed.
        val region = TopicSources.resolve(Topics.REGION_ID, settings.copy(region = "sachsen"))
        assertEquals(listOf("gn:region@DE:en"), region.map { it.id })
        assertEquals(source.url, region.single().url)
        assertEquals("\"Leipzig\"", TopicSources.pagingQuery(Topics.REGION_ID, settings)?.query)
    }

    @Test
    fun `a place in a country the app does not offer is searched in the main edition`() {
        val oslo = Place("Oslo", 59.91, 10.75, "Oslo, Norwegen", "NO")
        val id = Topics.localId(oslo)
        val settings = germany.copy(localPlaces = listOf(oslo))
        val source = TopicSources.resolve(id, settings).single()
        assertEquals(NewsEdition.GERMANY, TopicSources.editionFor(id, settings))
        assertEquals("when:14d \"Oslo\"", source.url.toHttpUrl().queryParameter("q"))
        assertEquals("gn:$id@DE:de", source.id)
        // No edition of its country to fall back to.
        assertNull(source.fallback)
    }

    @Test
    fun `the USA in German - home news are searched by the country's name, the rest reads German`() {
        val settings = AppSettings(edition = NewsEdition("US", "de"))
        // Top stories: German top stories are Germany's, so a search for the USA; no German newsroom's front page.
        val top = TopicSources.resolve("top", settings).single()
        assertEquals("gn:top@US:de", top.id)
        assertEquals("US:de", top.edition)
        val topUrl = top.url.toHttpUrl()
        assertEquals("/rss/search", topUrl.encodedPath)
        assertTrue(topUrl.queryParameter("q")!!, topUrl.queryParameter("q")!!.startsWith("when:7d USA OR \"Vereinigte Staaten\" -site:"))
        assertEquals(listOf("de", "DE", "DE:de"), listOf("hl", "gl", "ceid").map { topUrl.queryParameter(it) })
        assertNull(TopicSources.pagingQuery("top", settings))
        // Politics and the economy: the country, then words every country has – not "Bundestag", no tagesschau.
        val politics = TopicSources.resolve("politik", settings).single()
        val politicsQuery = politics.url.toHttpUrl().queryParameter("q")!!
        assertTrue(politicsQuery, politicsQuery.startsWith("USA OR \"Vereinigte Staaten\" Politik OR Regierung OR Parlament"))
        assertFalse(politicsQuery, "Bundestag" in politicsQuery)
        val economy = TopicSources.resolve("wirtschaft", settings).single()
        assertTrue(economy.url.toHttpUrl().queryParameter("q")!!.startsWith("USA OR \"Vereinigte Staaten\" Wirtschaft OR Konjunktur"))
        // The endless list pages with the same narrowed words.
        assertEquals(
            PagingQuery("USA OR \"Vereinigte Staaten\" Wirtschaft OR Konjunktur OR Unternehmen OR Börse OR Inflation OR Arbeitsmarkt", NewsEdition("US", "de"), "gn:wirtschaft@US:de"),
            TopicSources.pagingQuery("wirtschaft", settings),
        )
        // The world, tech and the stock market read the same in German everywhere: section, searches, world desks.
        val world = TopicSources.resolve("welt", settings)
        assertEquals("https://news.google.com/rss/headlines/section/topic/WORLD?hl=de&gl=DE&ceid=DE:de", google(world).url)
        assertTrue("tagesschau-ausland" in world.map { it.id })
        val boerse = TopicSources.resolve("wirtschaft/boerse", settings)
        assertEquals(listOf("tagesschau-finanzen", "ntv-boerse"), boerse.filterNot { it.isGoogleNews }.map { it.id })
        assertTrue(google(boerse).url.toHttpUrl().queryParameter("q")!!.startsWith("DAX OR Börse OR Aktien"))
        // Nothing bound to Germany, nothing in English.
        val all = Taxonomy.ALL.flatMap { TopicSources.resolve(it.id, settings) }.filterNot { it.isGoogleNews }.map { it.id }.toSet()
        assertTrue(all.none { it in setOf("tagesschau-top", "spiegel-top", "tagesschau-inland", "spiegel-politik", "dw", "tagesschau-wirtschaft", "npr-politics", "bbc-top") })
    }

    @Test
    fun `Germany in English - English searches for Germany, no German newsroom`() {
        val settings = AppSettings(edition = NewsEdition("DE", "en"), region = "sachsen")
        val top = TopicSources.resolve("top", settings).single().url.toHttpUrl()
        assertEquals("when:7d Germany", top.queryParameter("q")!!.substringBefore(" -site:"))
        assertEquals(listOf("en-US", "US", "US:en"), listOf("hl", "gl", "ceid").map { top.queryParameter(it) })
        val politics = TopicSources.resolve("politik/inland", settings).single().url.toHttpUrl().queryParameter("q")!!
        assertTrue(politics, politics.startsWith("Germany politics OR government OR parliament"))
        assertFalse(politics, "Congress" in politics)
        val catalogue = FeedCatalog.entries.associateBy { it.source.id }
        val feeds = (Taxonomy.ALL.map { it.id }).flatMap { TopicSources.resolve(it, settings, includeDisabled = true) }.filterNot { it.isGoogleNews }
        assertTrue(feeds.isNotEmpty())
        feeds.forEach { feed ->
            val entry = catalogue.getValue(feed.id)
            assertEquals(feed.id, "en", entry.language)
            // Desks for American or British readers only stay with them.
            assertTrue(feed.id, entry.countries.isEmpty())
        }
    }

    @Test
    fun `every region reads every language - in that language, from an edition Google News has, about that region`() {
        val topics = listOf("top", "politik", "politik/inland", "politik/wahlen", "wirtschaft", "wirtschaft/boerse", "welt", "ki", "sport", "wissenschaft/raumfahrt")
        val catalogue = FeedCatalog.entries.associateBy { it.source.id }
        val place = Place("Springfield", 39.8, -89.64, "Illinois, Vereinigte Staaten", "US")
        for (country in NewsEdition.COUNTRIES) {
            for (language in NewsEdition.LANGUAGES) {
                val edition = NewsEdition(country, language)
                val settings = AppSettings(edition = edition, localPlaces = listOf(place))
                for (topicId in topics) {
                    val def = Taxonomy.byId(topicId)!!
                    val label = "${edition.key} $topicId"
                    val sources = TopicSources.resolve(topicId, settings)
                    val gn = google(sources)
                    assertEquals(label, "gn:$topicId@${edition.key}", gn.id)
                    assertEquals(label, edition.key, gn.edition)
                    val url = gn.url.toHttpUrl()
                    // Google News is asked for an edition it has, in the chosen language.
                    val asked = NewsEdition(url.queryParameter("gl")!!, url.queryParameter("ceid")!!.substringAfter(':'))
                    assertTrue(label, asked.isNative)
                    assertEquals(label, language, asked.language)
                    assertEquals(label, edition.isNative, asked == edition)
                    // Publisher feeds are in the chosen language and not bound to another country.
                    sources.filterNot { it.isGoogleNews }.forEach { feed ->
                        val entry = catalogue.getValue(feed.id)
                        assertEquals("$label ${feed.id}", language, entry.language)
                        assertTrue("$label ${feed.id}", entry.countries.isEmpty() || country in entry.countries)
                    }
                    if (!edition.isNative && def.domestic) {
                        // Home news of a pair without an edition: a search that names the region, no publisher feeds.
                        assertEquals(label, "/rss/search", url.encodedPath)
                        assertTrue(label, CountryQuery.terms(country, language) in url.queryParameter("q")!!)
                        assertEquals(label, 1, sources.size)
                    }
                }
                // A place: the place's country in the chosen language.
                val id = Topics.localId(place)
                assertEquals(NewsEdition("US", language), TopicSources.editionFor(id, settings))
                val local = TopicSources.resolve(id, settings).single()
                assertEquals("gn:$id@US:$language", local.id)
                // Its place page where Google News has the USA in that language, else a search for its name;
                // behind anything but English, the English place page.
                assertEquals(language in setOf("en", "es"), "/geo/" in local.url)
                assertEquals(language == "en", local.fallback == null)
                local.fallback?.let { assertEquals("gn:$id@US:en", it.id) }
            }
        }
    }

    @Test
    fun `older pages of a place name its state in the edition's language`() {
        val la = Place("Los Angeles", 34.05, -118.24, "Kalifornien, Vereinigte Staaten", "US")
        assertEquals("\"Los Angeles\" \"California\"", TopicSources.placeQuery(la, NewsEdition.USA))
        val mumbai = Place("Mumbai", 19.07, 72.88, "Bundesstaat Maharashtra, Indien", "IN")
        assertEquals("\"Mumbai\" \"Maharashtra\"", TopicSources.placeQuery(mumbai, NewsEdition("IN", "en")))
        val halle = Place("Halle (Saale)", 51.48, 11.97, "Sachsen-Anhalt, Deutschland", "DE")
        assertEquals("\"Halle (Saale)\" \"Sachsen-Anhalt\"", TopicSources.placeQuery(halle, NewsEdition.GERMANY))
        // Read in a language Google News has no edition of the country in: the name alone (German news seldom name the state).
        assertEquals("\"Los Angeles\"", TopicSources.placeQuery(la, NewsEdition("US", "de")))
        assertEquals("\"Halle (Saale)\"", TopicSources.placeQuery(halle, NewsEdition("DE", "en")))
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
        assertNull(TopicSources.pagingQuery(Topics.TOP_ID, germany))
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

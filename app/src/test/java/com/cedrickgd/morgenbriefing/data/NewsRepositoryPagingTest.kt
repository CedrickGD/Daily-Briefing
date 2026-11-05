package com.cedrickgd.morgenbriefing.data

import com.cedrickgd.morgenbriefing.data.local.NewsCache
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.RssParser
import com.cedrickgd.morgenbriefing.data.remote.GoogleNewsFixtures
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.kxml2.io.KXmlParser
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Refresh, endless paging and search against a fake Google News. */
class NewsRepositoryPagingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var repository: NewsRepository
    private val seen: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Volatile
    private var now = ZonedDateTime.of(2026, 11, 5, 13, 0, 0, 0, ZoneId.of("Europe/Berlin"))

    /** Only the Google News source: the publisher feeds of the topic are switched off. */
    private val settings = AppSettings(edition = NewsEdition.USA, followed = listOf(KRYPTO), disabledFeeds = setOf("coindesk", "cointelegraph", "decrypt"))

    /** The newest day of the first page (it moves when the test lets days pass). */
    @Volatile
    private var firstPageDay = "2026-11-05"

    /** The first page also carries a stray hit from April, as Google News' first pages do. */
    @Volatile
    private var stray = false

    /** Requests for which [holdIf] is true wait for [gate]; [held] counts them. */
    @Volatile
    private var holdIf: (String) -> Boolean = { false }
    private val gate = CountDownLatch(1)
    private val held = AtomicInteger()

    /** Answers by the query's date window: one story per day, a few days per window. */
    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val q = request.requestUrl!!.queryParameter("q").orEmpty()
            if (holdIf(q)) {
                held.incrementAndGet()
                gate.await(20, TimeUnit.SECONDS)
            }
            val before = Regex("before:(\\d{4}-\\d{2}-\\d{2})").find(q)?.groupValues?.get(1)
            val after = Regex("after:(\\d{4}-\\d{2}-\\d{2})").find(q)?.groupValues?.get(1)
            val newest = LocalDate.parse(firstPageDay)
            val ceid = request.requestUrl!!.queryParameter("ceid")
            val items = when {
                q.startsWith("Nothing") -> emptyList()
                // A ticker: German news in the German edition, English news (and noise) in the English ones.
                q.startsWith("\"BTC\"") && ceid == "DE:de" -> listOf(story("BTC springt nach der Wahl über 80.000 Dollar", "$newest", site = "https://www.btc-echo.de", publisher = "BTC-ECHO"))
                q.startsWith("\"BTC\"") -> listOf(
                    story("BTC jumps as ETF flows return after the vote", "$newest"),
                    story("Weather turns cold across the country this weekend", "$newest", site = "https://www.weather.com", publisher = "Weather"),
                    story("BTC hits a record in a year long gone", "2024-03-01"),
                )
                // A story that was quiet in the last week.
                "Quiet" in q && before != null && before > "2026-10-30" -> emptyList()
                before == null -> listOfNotNull(
                    story("Bitcoin jumps after the election results came in", "$newest"),
                    story("Ethereum upgrade goes live on the main network", "${newest.minusDays(1)}"),
                    story("A walled story about crypto markets this morning", "$newest", site = "https://www.wsj.com", publisher = "WSJ"),
                    if (stray) story("An old story about crypto from the spring", "2026-04-10") else null,
                )
                // Nothing older than October 20th.
                before < "2026-10-20" -> emptyList()
                else -> listOf(story("Crypto story from the week before $before", after!!))
            }
            return MockResponse().setBody(GoogleNewsFixtures.feed(*items.toTypedArray()))
        }
    }

    private fun story(title: String, day: String, site: String = "https://www.coindesk.com", publisher: String = "CoinDesk"): String {
        val date = LocalDate.parse(day).atTime(10, 0).atZone(ZoneId.of("UTC"))
        val rfc = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(date)
        val id = "CBMi" + Integer.toHexString((title + day).hashCode()).padStart(8, '0') + "AAAAAAAAAAAA"
        return GoogleNewsFixtures.item(title, publisher, site, id, rfc)
    }

    private fun items() = repository.state.value.topics.getValue(KRYPTO).items

    private fun lastQuery() = seen.last().toHttpUrl().queryParameter("q")!!

    /** Waits (in real time, off the test scheduler) until [condition] holds. */
    private suspend fun waitFor(condition: () -> Boolean) = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        server.dispatcher = dispatcher
        val client = RssClient(GoogleNewsFixtures.redirectingClient(server, seen), RssParser { KXmlParser() })
        repository = NewsRepository(client, NewsCache(temp.root, AppJson), clock = { now })
    }

    @After
    fun tearDown() {
        gate.countDown()
        server.shutdown()
    }

    @Test
    fun `a refresh reads Google News, leaves walled sites out and caches`() = runTest {
        val outcome = repository.refresh(listOf(KRYPTO), settings)
        assertEquals(setOf(KRYPTO), outcome.refreshed)
        assertEquals(listOf("Bitcoin jumps after the election results came in", "Ethereum upgrade goes live on the main network"), items().map { it.headline })
        val search = seen.first { it.contains("/rss/search") }.toHttpUrl()
        assertTrue(search.queryParameter("q")!!.contains("-site:wsj.com"))
        assertEquals("US:en", search.queryParameter("ceid"))
        assertTrue(NewsCache(temp.root, AppJson).load(KRYPTO) != null)
    }

    @Test
    fun `load more walks back week by week until nothing older comes`() = runTest {
        repository.refresh(listOf(KRYPTO), settings)
        assertTrue(repository.canLoadMore(KRYPTO, settings))
        assertTrue(repository.loadMore(KRYPTO, settings))
        assertTrue(lastQuery(), lastQuery().startsWith("after:2026-11-02 before:2026-11-05 "))
        assertEquals("Crypto story from the week before 2026-11-05", items().last().headline)
        assertTrue(items().last().sourceId.endsWith(NewsRepository.PAGE_SUFFIX))

        assertTrue(repository.loadMore(KRYPTO, settings))
        assertTrue(lastQuery(), lastQuery().startsWith("after:2026-10-27 before:2026-11-02 "))

        var rounds = 0
        while (repository.loadMore(KRYPTO, settings)) rounds++
        assertTrue(rounds in 1..10)
        assertFalse(repository.loadMore(KRYPTO, settings))
        assertTrue(items().last().publishedAt!! >= "2026-10-1")

        // Paged articles stay through the next refresh.
        val count = items().size
        repository.refresh(listOf(KRYPTO), settings)
        assertEquals(count, items().size)
    }

    @Test
    fun `a stray old hit on the first page does not make paging skip the months after it`() = runTest {
        stray = true
        repository.refresh(listOf(KRYPTO), settings)
        assertTrue(items().any { it.headline.startsWith("An old story") })
        assertTrue(repository.loadMore(KRYPTO, settings))
        assertTrue(lastQuery(), lastQuery().startsWith("after:2026-11-02 before:2026-11-05 "))
    }

    @Test
    fun `older articles that came while a refresh ran stay`() = runTest {
        repository.refresh(listOf(KRYPTO), settings)
        // The next refresh hangs on its first page while the user scrolls to the end.
        holdIf = { q -> "before:" !in q }
        val refreshing = async { repository.refresh(listOf(KRYPTO), settings) }
        waitFor { held.get() >= 1 }
        holdIf = { false }
        assertTrue(repository.loadMore(KRYPTO, settings))
        val paged = items().filter { it.sourceId.endsWith(NewsRepository.PAGE_SUFFIX) }.map { it.id }
        assertTrue(paged.isNotEmpty())
        gate.countDown()
        refreshing.await()
        assertTrue(items().map { it.id }.containsAll(paged))
        // Paging goes on from there, not over the same days again.
        assertTrue(repository.loadMore(KRYPTO, settings))
        assertTrue(lastQuery(), lastQuery().startsWith("after:2026-10-27 before:2026-11-02 "))
    }

    @Test
    fun `after days away the endless list starts again from the fresh first page`() = runTest {
        repository.refresh(listOf(KRYPTO), settings)
        assertTrue(repository.loadMore(KRYPTO, settings))
        assertTrue(repository.loadMore(KRYPTO, settings))
        now = now.plusDays(9)
        firstPageDay = "2026-11-14"
        repository.refresh(listOf(KRYPTO), settings)
        // Nov 6–12 were never loaded: the old pages would leave a hole nothing fills.
        assertTrue(items().none { it.sourceId.endsWith(NewsRepository.PAGE_SUFFIX) })
        assertTrue(repository.loadMore(KRYPTO, settings))
        assertTrue(lastQuery(), lastQuery().startsWith("after:2026-11-11 before:2026-11-14 "))
    }

    @Test
    fun `a search does not wait for the feeds a refresh queued`() = runTest {
        val topics = (1..8).map { Topic("custom:h$it", "Held $it", "🔎", 0xFF2F6FDE, custom = true, query = "Held$it") }
        val busy = settings.copy(customTopics = topics, followed = topics.map { it.id })
        holdIf = { q -> q.startsWith("Held") }
        val refreshing = async { repository.refresh(topics.map { it.id }, busy) }
        // Every refresh connection is busy with Google News, more than OkHttp's default 5 per host.
        waitFor { held.get() >= 6 }
        val found = repository.search("Ethereum upgrade", busy)
        assertTrue(found.items.isNotEmpty())
        gate.countDown()
        assertEquals(topics.size, refreshing.await().refreshed.size)
    }

    @Test
    fun `topics without a search cannot page`() = runTest {
        assertFalse(repository.canLoadMore(Topics.TOP_ID, settings))
        assertFalse(repository.loadMore(Topics.TOP_ID, settings))
        assertEquals(0, server.requestCount)
        // Google News switched off: nothing to page with.
        val off = settings.copy(disabledFeeds = settings.disabledFeeds + "gn:$KRYPTO@US:en")
        assertFalse(repository.canLoadMore(KRYPTO, off))
        assertFalse(repository.loadMore(KRYPTO, off))
    }

    @Test
    fun `a topic read from other sources than the settings give is stale`() = runTest {
        repository.refresh(listOf(KRYPTO), settings)
        val fresh = Duration.ofMinutes(15)
        assertFalse(repository.isStale(KRYPTO, fresh, settings))
        assertTrue(repository.isStale(KRYPTO, fresh, settings.copy(edition = NewsEdition.GERMANY)))
        assertTrue(repository.isStale(KRYPTO, fresh, settings.copy(disabledFeeds = setOf("coindesk"))))
        now = now.plusMinutes(20)
        assertTrue(repository.isStale(KRYPTO, fresh, settings))
    }

    @Test
    fun `search pages reach back week by week and include cached matches`() = runTest {
        repository.refresh(listOf(KRYPTO), settings)
        val first = repository.search("Ethereum upgrade", settings)
        assertTrue(first.items.isNotEmpty())
        // Most relevant first: the story with both words, as the topic's own copy; Google's
        // first hit shares no word with the query and comes last (it stays: Google saw more).
        assertEquals("Ethereum upgrade goes live on the main network", first.items.first().headline)
        assertEquals(KRYPTO, first.items.first().topicId)
        assertEquals("Bitcoin jumps after the election results came in", first.items.last().headline)
        assertTrue(first.items.none { it.publisherDomain == "wsj.com" })
        assertTrue(first.hasMore)
        val second = repository.search("Ethereum upgrade", settings, page = 1)
        assertTrue(lastQuery().startsWith("after:2026-10-29 before:2026-11-06 "))
        assertEquals(Topics.SEARCH_ID, second.items.single().topicId)
        assertFalse(repository.search("Nothing at all", settings).hasMore)
        assertTrue(repository.search("   ", settings).items.isEmpty())
    }

    @Test
    fun `a search in a German edition reads the English editions too, ranked and filtered`() = runTest {
        val germany = AppSettings(edition = NewsEdition.GERMANY, followed = listOf(KRYPTO))
        val found = repository.search("BTC", germany)
        val asked = seen.filter { "/rss/search" in it }.map { it.toHttpUrl() }
        assertEquals(setOf("DE:de", "US:en", "GB:en"), asked.map { it.queryParameter("ceid") }.toSet())
        // The ticker goes to Google News quoted, in every edition.
        assertTrue(asked.all { it.queryParameter("q")!!.startsWith("\"BTC\"") })
        // English noise that shares no word, and a page from years ago, stay out; the same English story from two editions shows once.
        assertEquals(listOf("BTC springt nach der Wahl über 80.000 Dollar", "BTC jumps as ETF flows return after the vote"), found.items.map { it.headline })
        assertEquals(listOf("gn:search@DE:de", "gn:search@US:en"), found.items.map { it.sourceId })

        seen.clear()
        val german = repository.search("BTC", germany, allLanguages = false)
        assertEquals(listOf("DE:de"), seen.filter { "/rss/search" in it }.map { it.toHttpUrl().queryParameter("ceid") })
        assertEquals(1, german.items.size)
    }

    @Test
    fun `a followed search reads the English editions too, without their noise`() = runTest {
        val followed = Topic("custom:btc", "BTC", "🔎", 0xFF2F6FDE, custom = true, query = "BTC")
        val germany = AppSettings(edition = NewsEdition.GERMANY, customTopics = listOf(followed), followed = listOf(followed.id))
        repository.refresh(listOf(followed.id), germany)
        val news = repository.state.value.topics.getValue(followed.id)
        assertEquals(listOf("gn:custom:btc@DE:de", "gn:custom:btc@US:en", "gn:custom:btc@GB:en"), news.sources.map { it.sourceId })
        assertEquals(
            setOf("BTC springt nach der Wahl über 80.000 Dollar", "BTC jumps as ETF flows return after the vote"),
            news.items.map { it.headline }.toSet(),
        )
        assertTrue(seen.filter { "/rss/search" in it }.all { it.toHttpUrl().queryParameter("q")!!.startsWith("\"BTC\"") })
    }

    @Test
    fun `a quiet week does not end a search`() = runTest {
        val quiet = repository.search("Quiet town", settings, page = 1)
        assertTrue(quiet.items.isEmpty())
        assertTrue(quiet.hasMore)
        assertEquals(1, repository.search("Quiet town", settings, page = 2).items.size)
        assertFalse(repository.search("Quiet town", settings, page = NewsRepository.MAX_SEARCH_PAGES - 1).hasMore)
    }

    private companion object {
        const val KRYPTO = "wirtschaft/krypto"
    }
}

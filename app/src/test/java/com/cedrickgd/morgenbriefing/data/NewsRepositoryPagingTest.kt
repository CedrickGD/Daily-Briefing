package com.cedrickgd.morgenbriefing.data

import com.cedrickgd.morgenbriefing.data.local.NewsCache
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.RssParser
import com.cedrickgd.morgenbriefing.data.remote.GoogleNewsFixtures
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import kotlinx.coroutines.test.runTest
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
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Collections

/** Refresh, endless paging and search against a fake Google News. */
class NewsRepositoryPagingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var repository: NewsRepository
    private val seen: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val now = ZonedDateTime.of(2026, 11, 5, 13, 0, 0, 0, ZoneId.of("Europe/Berlin"))
    /** Only the Google News source: the publisher feeds of the topic are switched off. */
    private val settings = AppSettings(edition = NewsEdition.USA, followed = listOf("wirtschaft/krypto"), disabledFeeds = setOf("coindesk", "cointelegraph", "decrypt"))

    /** Answers by the query's date window: one story per day, a few days per window. */
    private val dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val q = request.requestUrl!!.queryParameter("q").orEmpty()
            val before = Regex("before:(\\d{4}-\\d{2}-\\d{2})").find(q)?.groupValues?.get(1)
            val after = Regex("after:(\\d{4}-\\d{2}-\\d{2})").find(q)?.groupValues?.get(1)
            val items = when {
                q.startsWith("Nothing") -> emptyList()
                before == null -> listOf(
                    story("Bitcoin jumps after the election results came in", "2026-11-05"),
                    story("Ethereum upgrade goes live on the main network", "2026-11-04"),
                    story("A walled story about crypto markets this morning", "2026-11-05", site = "https://www.wsj.com", publisher = "WSJ"),
                )
                // Nothing older than October 20th.
                before < "2026-10-20" -> emptyList()
                else -> listOf(story("Crypto story from the week before $before", after!!))
            }
            return MockResponse().setBody(GoogleNewsFixtures.feed(*items.toTypedArray()))
        }
    }

    private fun story(title: String, day: String, site: String = "https://www.coindesk.com", publisher: String = "CoinDesk"): String {
        val date = java.time.LocalDate.parse(day).atTime(10, 0).atZone(ZoneId.of("UTC"))
        val rfc = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(date)
        val id = "CBMi" + Integer.toHexString((title + day).hashCode()).padStart(8, '0') + "AAAAAAAAAAAA"
        return GoogleNewsFixtures.item(title, publisher, site, id, rfc)
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
        server.shutdown()
    }

    @Test
    fun `a refresh reads Google News, leaves walled sites out and caches`() = runTest {
        val outcome = repository.refresh(listOf("wirtschaft/krypto"), settings)
        assertEquals(setOf("wirtschaft/krypto"), outcome.refreshed)
        val news = repository.state.value.topics.getValue("wirtschaft/krypto")
        assertEquals(listOf("Bitcoin jumps after the election results came in", "Ethereum upgrade goes live on the main network"), news.items.map { it.headline })
        val search = seen.first { it.contains("/rss/search") }.toHttpUrl()
        assertTrue(search.queryParameter("q")!!.contains("-site:wsj.com"))
        assertEquals("US:en", search.queryParameter("ceid"))
        assertTrue(NewsCache(temp.root, AppJson).load("wirtschaft/krypto") != null)
    }

    @Test
    fun `load more walks back week by week until nothing older comes`() = runTest {
        repository.refresh(listOf("wirtschaft/krypto"), settings)
        assertTrue(repository.canLoadMore("wirtschaft/krypto", settings))
        assertTrue(repository.loadMore("wirtschaft/krypto", settings))
        val first = seen.last().toHttpUrl().queryParameter("q")!!
        assertTrue(first, first.startsWith("after:2026-11-02 before:2026-11-05 "))
        val items = repository.state.value.topics.getValue("wirtschaft/krypto").items
        assertEquals("Crypto story from the week before 2026-11-05", items.last().headline)
        assertTrue(items.last().sourceId.endsWith(NewsRepository.PAGE_SUFFIX))

        assertTrue(repository.loadMore("wirtschaft/krypto", settings))
        assertTrue(seen.last(), seen.last().toHttpUrl().queryParameter("q")!!.startsWith("after:2026-10-27 before:2026-11-02 "))

        var rounds = 0
        while (repository.loadMore("wirtschaft/krypto", settings)) rounds++
        assertTrue(rounds in 1..10)
        assertFalse(repository.loadMore("wirtschaft/krypto", settings))
        val oldest = repository.state.value.topics.getValue("wirtschaft/krypto").items.last()
        assertTrue(oldest.publishedAt!! >= "2026-10-1")

        // Paged articles stay through the next refresh.
        val count = repository.state.value.topics.getValue("wirtschaft/krypto").items.size
        repository.refresh(listOf("wirtschaft/krypto"), settings)
        assertEquals(count, repository.state.value.topics.getValue("wirtschaft/krypto").items.size)
    }

    @Test
    fun `topics without a search cannot page`() = runTest {
        assertFalse(repository.canLoadMore(Topics.TOP_ID, settings))
        assertFalse(repository.loadMore(Topics.TOP_ID, settings))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `search pages reach back week by week and include cached matches`() = runTest {
        repository.refresh(listOf("wirtschaft/krypto"), settings)
        val first = repository.search("Ethereum upgrade", settings)
        assertTrue(first.items.isNotEmpty())
        assertEquals("Bitcoin jumps after the election results came in", first.items.first().headline)
        assertTrue(first.items.any { it.topicId == "wirtschaft/krypto" })
        assertTrue(first.items.none { it.publisherDomain == "wsj.com" })
        assertTrue(first.hasMore)
        val second = repository.search("Ethereum upgrade", settings, page = 1)
        assertTrue(seen.last().toHttpUrl().queryParameter("q")!!.startsWith("after:2026-10-29 before:2026-11-06 "))
        assertEquals(Topics.SEARCH_ID, second.items.single().topicId)
        assertFalse(repository.search("Nothing at all", settings).hasMore)
        assertTrue(repository.search("   ", settings).items.isEmpty())
    }
}

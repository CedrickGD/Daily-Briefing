package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.AppJson
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.ZonedDateTime

/** The reader's loading against a local server: redirects with cookies, outcomes, links, cache, bookkeeping. */
class ArticleRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var dir: File
    private lateinit var store: ReadabilityStore
    private val now = ZonedDateTime.parse("2026-11-05T12:00:00+01:00")

    private val article = "<html lang=\"de\"><head><meta property=\"og:title\" content=\"Titel\"></head><body><article><h1>Titel</h1>" +
        (1..6).joinToString("") { "<p>Absatz $it erzählt in aller Ruhe, was an diesem Tag in der Stadt passiert ist, wer dabei war und warum das für die Menschen vor Ort eine Rolle spielt.</p>" } +
        "</article></body></html>"

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        dir = Files.createTempDirectory("articles").toFile()
        store = ReadabilityStore(File(dir, "readability.json"), AppJson)
    }

    @After
    fun tearDown() {
        server.shutdown()
        dir.deleteRecursively()
    }

    private fun repository(resolver: LinkResolver = LinkResolver.Identity) =
        ArticleRepository(OkHttpClient(), dir, AppJson, linkResolver = resolver, readability = store, clock = { now }, requireHttps = false)

    private fun item(url: String, id: String = "a1", aggregator: String? = null) =
        NewsItem(id = id, headline = "Titel", url = url, source = "Testzeitung", sourceId = "t", topicId = "welt", aggregator = aggregator)

    private fun html(body: String) = MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(body)

    private suspend fun failure(block: suspend () -> Unit): ArticleException {
        try {
            block()
        } catch (e: ArticleException) {
            return e
        }
        fail("expected an ArticleException")
        throw IllegalStateException()
    }

    @Test
    fun `a site that sets a cookie and redirects to itself gets the cookie back`() = runBlocking {
        val url = server.url("/artikel").toString()
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/artikel").addHeader("Set-Cookie", "session=abc; Path=/"))
        server.enqueue(html(article))
        val content = repository().load(item(url))
        assertTrue(content.wordCount > 100)
        server.takeRequest()
        assertEquals("session=abc", server.takeRequest().getHeader("Cookie"))
        assertEquals(1, store.statsFor(server.hostName)?.ok)
    }

    @Test
    fun `a redirect loop is a bot block, a consent redirect is consent`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/loop")) }
        assertEquals(ReadOutcome.BLOCKED, failure { repository().load(item(server.url("/loop").toString())) }.outcome)

        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/sonstiges/zustimmung/auswahl.html?from=x"))
        assertEquals(ReadOutcome.CONSENT, failure { repository().load(item(server.url("/news/a.html").toString(), id = "a2")) }.outcome)
    }

    @Test
    fun `server answers become typed outcomes`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("<html><title>Access Denied</title></html>"))
        val blocked = failure { repository().load(item(server.url("/a").toString(), id = "x1")) }
        assertEquals(ReadOutcome.BLOCKED, blocked.outcome)
        assertEquals(403, blocked.httpCode)
        assertEquals("Testzeitung", blocked.site)

        server.enqueue(MockResponse().setResponseCode(404))
        val gone = failure { repository().load(item(server.url("/b").toString(), id = "x2")) }
        assertEquals(ReadOutcome.NOT_ARTICLE, gone.outcome)
        assertEquals(404, gone.httpCode)

        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(ReadOutcome.NETWORK, failure { repository().load(item(server.url("/c").toString(), id = "x3")) }.outcome)

        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF"))
        assertEquals(ReadOutcome.NOT_ARTICLE, failure { repository().load(item(server.url("/d").toString(), id = "x4")) }.outcome)

        // Every unreadable page counts for the host – except the 503: a network hiccup says nothing about the site.
        val stats = store.statsFor(server.hostName)!!
        assertEquals(3, stats.failed)
        assertEquals(ReadOutcome.NOT_ARTICLE, stats.lastFailure)
    }

    @Test
    fun `aggregator links are resolved first, the cache stays keyed by the item, the publisher's host learns`() = runBlocking {
        val publisher = server.url("/publisher/story").toString()
        server.enqueue(html(article))
        var asked: String? = null
        val repo = repository { url -> asked = url; publisher }
        val google = item("https://news.google.com/rss/articles/CBMiXYZ?oc=5", id = "g1", aggregator = "google")
        val content = repo.load(google)
        assertEquals("https://news.google.com/rss/articles/CBMiXYZ?oc=5", asked)
        assertEquals(publisher, content.url)
        assertEquals(1, store.statsFor(server.hostName)?.ok)
        // Second load: from the cache, no request, no resolving.
        asked = null
        assertNotNull(repo.load(google))
        assertNull(asked)
        assertEquals(1, server.requestCount)
        // A resolver that cannot help: no article, and Google is never learned as a wall.
        val stuck = failure { repository { it }.load(google.copy(id = "g2")) }
        assertEquals(ReadOutcome.NOT_ARTICLE, stuck.outcome)
        assertNull(store.statsFor("news.google.com"))
    }

    @Test
    fun `Accept-Language follows the site's country, else the item's language`() = runBlocking {
        server.enqueue(html(article))
        repository().load(item(server.url("/x").toString()), language = "fr")
        assertEquals("fr,en;q=0.7", server.takeRequest().getHeader("Accept-Language"))
        assertEquals("de,en;q=0.7", ArticleRepository.acceptLanguage("en", "https://www.tagesschau.de/a".toHttpUrl()))
        assertEquals("en-US,en;q=0.9", ArticleRepository.acceptLanguage(null, "https://www.bbc.com/news".toHttpUrl()))
        assertEquals("en-US,en;q=0.9", ArticleRepository.acceptLanguage("de", "https://www.bbc.co.uk/news".toHttpUrl()))
    }

    @Test
    fun `an article cached by an older extractor is read again`() = runBlocking {
        val url = server.url("/old").toString()
        val folder = File(dir, "articles").apply { mkdirs() }
        val old = ArticleContent(url = url, title = "Alt", blocks = emptyList(), fetchedAt = now.toString(), format = ArticleContent.FORMAT - 1)
        File(folder, "a1.json").writeText(AppJson.encodeToString(ArticleContent.serializer(), old))
        server.enqueue(html(article))
        val content = repository().load(item(url))
        assertEquals(ArticleContent.FORMAT, content.format)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the AMP version the page links to is the second try when the page has no text`() = runBlocking {
        val amp = server.url("/amp/story").toString()
        server.enqueue(html("<html><head><link rel=\"amphtml\" href=\"$amp\"></head><body><div id=\"app\">Wird geladen …</div></body></html>"))
        server.enqueue(html(article))
        val content = repository().load(item(server.url("/story").toString()))
        assertTrue(content.wordCount > 100)
        assertEquals(server.url("/story").toString(), content.url)
    }
}

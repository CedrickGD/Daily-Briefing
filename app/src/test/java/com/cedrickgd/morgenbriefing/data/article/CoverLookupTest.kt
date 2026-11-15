package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** The cover a page names in its head ([CoverMeta]) and the light lookup that reads only that head ([CoverLookup]). */
class CoverLookupTest {

    private val page = "https://www.example.org/politik/artikel-1.html".toHttpUrl()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun html(head: String, body: String = "<p>Text</p>") = "<!doctype html><html><head><title>T</title>$head</head><body>$body</body></html>"

    private fun item(url: String, aggregator: String? = null) = NewsItem(
        id = "i1", headline = "H", url = url, source = "S", sourceId = "s", topicId = "top", aggregator = aggregator,
    )

    private fun lookup(resolver: LinkResolver = LinkResolver.Identity) = CoverLookup(OkHttpClient(), resolver, requireHttps = false)

    @Test
    fun `og image is read, in any attribute order and quoting`() {
        assertEquals(
            "https://cdn.example.org/a.jpg",
            CoverMeta.find(html("""<meta property="og:image" content="https://cdn.example.org/a.jpg">"""), page),
        )
        assertEquals(
            "https://cdn.example.org/b.jpg",
            CoverMeta.find(html("""<META content='https://cdn.example.org/b.jpg' property='og:image' />"""), page),
        )
        assertEquals(
            "https://cdn.example.org/c.jpg",
            CoverMeta.find(html("<meta name=twitter:image content=https://cdn.example.org/c.jpg>"), page),
        )
    }

    @Test
    fun `the secure address wins, then og, then twitter, then the image link`() {
        val all = html(
            """
            <link rel="image_src" href="https://cdn.example.org/link.jpg">
            <meta name="twitter:image" content="https://cdn.example.org/twitter.jpg">
            <meta property="og:image" content="https://cdn.example.org/og.jpg">
            <meta property="og:image:secure_url" content="https://cdn.example.org/secure.jpg">
            """,
        )
        assertEquals("https://cdn.example.org/secure.jpg", CoverMeta.find(all, page))
        assertEquals(
            "https://cdn.example.org/twitter.jpg",
            CoverMeta.find(html("""<link rel="image_src" href="https://cdn.example.org/link.jpg"><meta name="twitter:image" content="https://cdn.example.org/twitter.jpg">"""), page),
        )
        assertEquals("https://cdn.example.org/link.jpg", CoverMeta.find(html("""<link rel="image_src" href="https://cdn.example.org/link.jpg">"""), page))
        // The first og:image of a page counts (galleries name several).
        assertEquals(
            "https://cdn.example.org/1.jpg",
            CoverMeta.find(html("""<meta property="og:image" content="https://cdn.example.org/1.jpg"><meta property="og:image" content="https://cdn.example.org/2.jpg">"""), page),
        )
    }

    @Test
    fun `addresses are made absolute, unescaped and https`() {
        assertEquals("https://www.example.org/img/a.jpg", CoverMeta.find(html("""<meta property="og:image" content="/img/a.jpg">"""), page))
        assertEquals("https://cdn.example.org/a.jpg", CoverMeta.find(html("""<meta property="og:image" content="//cdn.example.org/a.jpg">"""), page))
        assertEquals(
            "https://cdn.example.org/a.jpg?w=1200&h=630",
            CoverMeta.find(html("""<meta property="og:image" content="https://cdn.example.org/a.jpg?w=1200&amp;h=630">"""), page),
        )
        assertEquals(
            "https://cdn.example.org/a.jpg?w=1200&h=630",
            CoverMeta.find(html("""<meta property="og:image" content="https://cdn.example.org/a.jpg?w=1200&#38;h=630">"""), page),
        )
        // Plain http becomes https: newsroom CDNs serve both, and the app loads nothing in the clear.
        assertEquals("https://cdn.example.org/a.jpg", CoverMeta.find(html("""<meta property="og:image" content="http://cdn.example.org/a.jpg">"""), page))
    }

    @Test
    fun `what the app cannot show is skipped for the next candidate`() {
        assertNull(CoverMeta.find(html("""<meta property="og:image" content="">"""), page))
        assertNull(CoverMeta.find(html("""<meta property="og:image" content="data:image/png;base64,AAAA">"""), page))
        assertNull(CoverMeta.find(html("""<meta property="og:image" content="https://cdn.example.org/logo.svg">"""), page))
        assertNull(CoverMeta.find(html("""<meta property="og:image" content="https://cdn.example.org:8443/a.jpg">"""), page))
        assertNull(CoverMeta.find(html("""<meta property="og:image" content="https://user@cdn.example.org/a.jpg">"""), page))
        assertNull(CoverMeta.find(html("""<meta property="og:title" content="https://cdn.example.org/a.jpg">"""), page))
        assertNull(CoverMeta.find(html(""), page))
        assertEquals(
            "https://cdn.example.org/photo.jpg",
            CoverMeta.find(html("""<meta property="og:image" content="https://cdn.example.org/logo.svg"><meta name="twitter:image" content="https://cdn.example.org/photo.jpg">"""), page),
        )
    }

    @Test
    fun `the lookup asks like the reader and finds the page's picture`() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").setBody(html("""<meta property="og:image" content="https://cdn.example.org/a.jpg">""")))
        val answer = lookup().find(item(server.url("/artikel").toString()), language = "de")
        assertEquals(CoverAnswer.Found("https://cdn.example.org/a.jpg"), answer)
        val request = server.takeRequest()
        assertEquals("/artikel", request.path)
        assertEquals(ArticleRepository.USER_AGENT, request.getHeader("User-Agent"))
        assertTrue(request.getHeader("Accept-Language")!!.startsWith("de"))
    }

    @Test
    fun `only the head of a page is read`() = runTest {
        // Ten megabytes of body behind the head, sent slowly: reading it all would take the test minutes.
        val body = Buffer().writeUtf8(html("""<meta property="og:image" content="https://cdn.example.org/a.jpg">""", body = ""))
        body.write(ByteArray(10 * 1024 * 1024) { 'x'.code.toByte() })
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(body).throttleBody(64 * 1024, 100, java.util.concurrent.TimeUnit.MILLISECONDS))
        val started = System.nanoTime()
        val answer = lookup().find(item(server.url("/long").toString()), language = null)
        assertEquals(CoverAnswer.Found("https://cdn.example.org/a.jpg"), answer)
        assertTrue("took ${(System.nanoTime() - started) / 1_000_000} ms", System.nanoTime() - started < 5_000_000_000L)
    }

    @Test
    fun `a page without a head end is read up to the limit and no further`() = runTest {
        val filler = "<style>" + "a{color:red}".repeat(60_000) + "</style>" // ≈ 720 kB, no </head>
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html><head>$filler<meta property=\"og:image\" content=\"https://cdn.example.org/late.jpg\">"))
        assertEquals(CoverAnswer.None, lookup().find(item(server.url("/huge").toString()), language = null))
    }

    @Test
    fun `refusals, other content and dead ends are told apart`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        val refused = lookup().find(item(server.url("/blocked").toString()), language = null)
        assertEquals(CoverAnswer.Refused(server.hostName, 403), refused)

        server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF"))
        assertEquals(CoverAnswer.None, lookup().find(item(server.url("/file.pdf").toString()), language = null))

        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(html("<meta name=\"description\" content=\"x\">")))
        assertEquals(CoverAnswer.None, lookup().find(item(server.url("/plain").toString()), language = null))

        // Nobody listens there any more: a network failure says nothing about the article.
        val gone = MockWebServer().apply { start() }
        val down = gone.url("/down").toString()
        gone.shutdown()
        assertEquals(CoverAnswer.Unreachable, lookup().find(item(down), language = null))

        assertEquals(CoverAnswer.None, lookup().find(item("not a link"), language = null))
    }

    @Test
    fun `an aggregator link is resolved first, and a link Google does not resolve says so`() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(html("""<meta property="og:image" content="https://cdn.example.org/a.jpg">""")))
        val target = server.url("/publisher/story").toString()
        val google = "https://news.google.com/rss/articles/CBMiAAAAAAAAAAAAAAAAAAAA?oc=5"
        val asked = ArrayList<String>()
        val resolving = LinkResolver { url ->
            asked += url
            target
        }
        assertEquals(CoverAnswer.Found("https://cdn.example.org/a.jpg"), lookup(resolving).find(item(google, aggregator = "google"), language = null))
        assertEquals(listOf(google), asked)
        assertEquals("/publisher/story", server.takeRequest().path)

        val limited = object : LinkResolver {
            override suspend fun resolve(url: String): String = url
            override suspend fun resolveOrThrow(url: String): String = throw LinkResolveException("batchexecute HTTP 429", 429)
        }
        assertEquals(CoverAnswer.Unresolved(429), lookup(limited).find(item(google, aggregator = "google"), language = null))
        val offline = object : LinkResolver {
            override suspend fun resolve(url: String): String = url
            override suspend fun resolveOrThrow(url: String): String = throw IOException("no route")
        }
        assertEquals(CoverAnswer.Unresolved(null), lookup(offline).find(item(google, aggregator = "google"), language = null))
        // A link that stays an aggregator link holds no page to read.
        assertEquals(CoverAnswer.None, lookup().find(item(google, aggregator = "google"), language = null))
        assertEquals(1, server.requestCount)
    }
}

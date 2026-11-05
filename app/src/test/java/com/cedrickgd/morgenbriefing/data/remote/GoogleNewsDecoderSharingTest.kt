package com.cedrickgd.morgenbriefing.data.remote

import com.cedrickgd.morgenbriefing.data.AppJson
import com.cedrickgd.morgenbriefing.data.article.LinkResolveException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

/** Two callers asking for the same link at once, and what the reader learns when Google cannot help. */
class GoogleNewsDecoderSharingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer

    private val id = "CBMic0FVX3lxTE45eE5aUXhYMVFkZ0lRNzk1UTdrUkFYYkxGdHVVMlE3dm9OaXAyQ3ZSWGl5ZmZueUd3SXgxU1BLNUE4Zm1sUy1WaGdpb05aZDZHbHhmT2EteGRpWmFZdFJCUGxWSTQxWW90VEdVZVBHZDZUYWs"
    private val link = "https://news.google.com/rss/articles/$id?oc=5"
    private val target = "https://www.cnn.com/2026/11/05/economy/data-center-texas-election"
    private val page = "<html><body><div data-n-a-sg=\"AdwtSYNTcd40LpPfWFQ-xyz\" data-n-a-ts=\"1793883020\"></div></body></html>"
    private val answer = ")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",\"[\\\"garturlres\\\",\\\"$target\\\",1]\",null,null,null,\"generic\"]]"

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun decoder() = GoogleNewsDecoder(OkHttpClient(), File(temp.root, "links.json"), AppJson, baseUrl = server.url("/"))

    @Test
    fun `a caller waiting on a decode that is cancelled decodes the link itself`() = runBlocking {
        // The first caller's page never comes (it is cancelled first); the waiter then asks again.
        server.enqueue(MockResponse().setBody(page).setHeadersDelay(3, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setBody(page))
        server.enqueue(MockResponse().setBody(answer))
        val decoder = decoder()
        val prefetch = launch(Dispatchers.IO) { decoder.resolve(link) }
        server.takeRequest(5, TimeUnit.SECONDS) ?: fail("the first caller never asked")
        val reader = async(Dispatchers.IO) { decoder.resolveOrThrow(link) }
        delay(300)
        assertEquals("the reader waits on the running decode", 1, server.requestCount)
        prefetch.cancelAndJoin()
        assertEquals(target, withTimeout(5_000) { reader.await() })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `Google's refusal is reported by resolveOrThrow and shared with a waiter, resolve falls back to the link`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeadersDelay(500, TimeUnit.MILLISECONDS))
        val decoder = decoder()
        val first = async(Dispatchers.IO) { runCatching { decoder.resolveOrThrow(link) } }
        server.takeRequest(5, TimeUnit.SECONDS) ?: fail("never asked")
        val second = async(Dispatchers.IO) { runCatching { decoder.resolveOrThrow(link) } }
        val errors = listOf(first.await(), second.await()).map { it.exceptionOrNull() }
        errors.forEach { e ->
            assertTrue("$e", e is LinkResolveException)
            assertEquals(429, (e as LinkResolveException).httpCode)
        }
        assertEquals("one request for both", 1, server.requestCount)

        // Offline-like failure (no signature on the page): resolveOrThrow throws, resolve hands the link back.
        server.enqueue(MockResponse().setBody("<html>kein Artikel</html>"))
        try {
            decoder.resolveOrThrow(link)
            fail("expected a LinkResolveException")
        } catch (e: LinkResolveException) {
            assertEquals(null, e.httpCode)
        }
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(link, decoder.resolve(link))
        // Not a Google News article link: nothing to resolve, no failure.
        assertEquals("https://www.bbc.co.uk/news/x", decoder.resolveOrThrow("https://www.bbc.co.uk/news/x"))
    }
}

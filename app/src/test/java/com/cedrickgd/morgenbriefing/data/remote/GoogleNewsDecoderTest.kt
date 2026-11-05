package com.cedrickgd.morgenbriefing.data.remote

import com.cedrickgd.morgenbriefing.data.AppJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URLDecoder
import java.util.Base64

class GoogleNewsDecoderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var cacheFile: File

    private val id = "CBMic0FVX3lxTE45eE5aUXhYMVFkZ0lRNzk1UTdrUkFYYkxGdHVVMlE3dm9OaXAyQ3ZSWGl5ZmZueUd3SXgxU1BLNUE4Zm1sUy1WaGdpb05aZDZHbHhmT2EteGRpWmFZdFJCUGxWSTQxWW90VEdVZVBHZDZUYWs"
    private val link = "https://news.google.com/rss/articles/$id?oc=5"
    private val target = "https://www.cnn.com/2026/11/05/economy/data-center-texas-election"

    /** What the article page carries (05.11.2026, trimmed). */
    private val page = """
        <!doctype html><html><head><title>Google News</title></head><body>
        <c-wiz><div jscontroller="aLI87" data-n-au="$link" data-n-a-sg="AdwtSYNTcd40LpPfWFQ-xyz" data-n-a-ts="1793883020" data-n-a-id="$id"></div></c-wiz>
        </body></html>
    """.trimIndent()

    /** The batchexecute answer for it. */
    private val answer = ")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",\"[\\\"garturlres\\\",\\\"$target\\\",1]\",null,null,null,\"generic\"],[\"di\",9],[\"af.httprm\",8,\"-4908143037374517745\",35]]"

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        cacheFile = File(temp.root, "links.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun decoder(maxEntries: Int = GoogleNewsDecoder.MAX_ENTRIES) =
        GoogleNewsDecoder(OkHttpClient(), cacheFile, AppJson, baseUrl = server.url("/"), maxEntries = maxEntries)

    @Test
    fun `old style ids decode offline`() {
        val url = "https://www.tagesschau.de/inland/innenpolitik/pflegereform-100.html"
        val bytes = byteArrayOf(0x08, 0x13, 0x22, url.length.toByte()) + url.toByteArray() + byteArrayOf(0xD2.toByte(), 0x01, 0x00)
        val oldId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        assertEquals(url, GoogleNewsDecoder.decodeOffline(oldId))
        assertNull(GoogleNewsDecoder.decodeOffline(id)) // AU_yqL… needs Google
        assertNull(GoogleNewsDecoder.decodeOffline("!!!"))
    }

    @Test
    fun `signature, request and answer`() {
        val signature = GoogleNewsDecoder.signatureOf(page)!!
        assertEquals("AdwtSYNTcd40LpPfWFQ-xyz", signature.signature)
        assertEquals(1793883020L, signature.timestamp)
        assertNull(GoogleNewsDecoder.signatureOf("<html></html>"))
        val request = Json.parseToJsonElement(GoogleNewsDecoder.batchRequest(id, signature.timestamp, signature.signature)).jsonArray
        val call = request[0].jsonArray[0].jsonArray
        assertEquals("Fbv4je", call[0].jsonPrimitive.content)
        val inner = Json.parseToJsonElement(call[1].jsonPrimitive.content).jsonArray
        assertEquals("garturlreq", inner[0].jsonPrimitive.content)
        assertEquals(id, inner[2].jsonPrimitive.content)
        assertEquals("1793883020", inner[3].jsonPrimitive.content)
        assertEquals("AdwtSYNTcd40LpPfWFQ-xyz", inner[4].jsonPrimitive.content)
        assertEquals(target, GoogleNewsDecoder.parseBatchResponse(answer))
        assertNull(GoogleNewsDecoder.parseBatchResponse(")]}'\n[[\"wrb.fr\",\"Fbv4je\",null]]"))
        assertNull(GoogleNewsDecoder.parseBatchResponse("<html>error</html>"))
    }

    @Test
    fun `current links take two steps and are cached on disk`() = runTest {
        server.enqueue(MockResponse().setBody(page))
        server.enqueue(MockResponse().setBody(answer))
        val decoder = decoder()
        assertEquals(target, decoder.resolve(link))

        val pageRequest = server.takeRequest()
        assertEquals("/rss/articles/$id", pageRequest.requestUrl!!.encodedPath)
        assertTrue(pageRequest.getHeader("Cookie")!!.startsWith("SOCS="))
        assertTrue(pageRequest.getHeader("User-Agent")!!.contains("Chrome"))
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/_/DotsSplashUi/data/batchexecute", post.requestUrl!!.encodedPath)
        assertTrue(post.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
        val form = URLDecoder.decode(post.body.readUtf8().removePrefix("f.req="), "UTF-8")
        assertTrue(form, form.contains("Fbv4je") && form.contains(id) && form.contains("1793883020"))

        // Second time from memory, then from the file in a new process.
        assertEquals(target, decoder.resolve(link))
        assertEquals(target, decoder.peek(link))
        val fresh = decoder()
        assertNull(fresh.peek(link))
        fresh.warm()
        assertEquals(target, fresh.peek(link))
        assertEquals(target, fresh.resolve(link))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `failures give the original link back and are retried later`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        val decoder = decoder()
        assertEquals(link, decoder.resolve(link))
        server.enqueue(MockResponse().setBody("<html>kein Artikel</html>"))
        assertEquals(link, decoder.resolve(link))
        server.enqueue(MockResponse().setBody(page))
        server.enqueue(MockResponse().setBody("[]"))
        assertEquals(link, decoder.resolve(link))
        server.enqueue(MockResponse().setBody(page))
        server.enqueue(MockResponse().setBody(answer))
        assertEquals(target, decoder.resolve(link))
        // Other links are not touched.
        assertEquals("https://www.bbc.co.uk/news/x", decoder.resolve("https://www.bbc.co.uk/news/x"))
        assertEquals(6, server.requestCount)
    }

    @Test
    fun `the cache keeps the newest entries`() = runTest {
        val decoder = decoder(maxEntries = 2)
        val urls = (1..3).map { n ->
            val url = "https://example.org/story-$n"
            val bytes = byteArrayOf(0x08, 0x13, 0x22, url.length.toByte()) + url.toByteArray()
            val oldId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val gn = "https://news.google.com/rss/articles/$oldId?oc=5"
            assertEquals(url, decoder.resolve(gn))
            gn
        }
        val fresh = decoder(maxEntries = 2)
        fresh.warm()
        assertNull(fresh.peek(urls[0]))
        assertEquals("https://example.org/story-3", fresh.peek(urls[2]))
        assertEquals(0, server.requestCount)
    }
}

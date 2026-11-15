package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.AppJson
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration

/** Covers looked up ahead of the lists ([CoverResolver]) and kept across starts ([CoverStore]). */
@OptIn(ExperimentalCoroutinesApi::class)
class CoverResolverTest {

    private lateinit var dir: File
    private var clock = 1_800_000_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("covers").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val file get() = File(dir, "covers.json")

    private fun item(id: String, image: String? = null, google: Boolean = true, domain: String = "example.org") = NewsItem(
        id = id,
        headline = "H$id",
        url = if (google) "https://news.google.com/rss/articles/CBMi${id.padEnd(20, 'A')}?oc=5" else "https://www.$domain/$id",
        source = "S",
        sourceId = "gn:top@DE:de",
        topicId = "top",
        image = image?.let { ArticleImage(it) },
        publisherDomain = domain,
        aggregator = if (google) "google" else null,
    )

    /** Answers per item id; counts the lookups and how many run at once; [gate] holds them when set. */
    private inner class FakeSource : CoverResolver.Source {
        val answers = HashMap<String, CoverAnswer>()
        val asked = ArrayList<String>()
        var running = 0
        var maxRunning = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun find(item: NewsItem, language: String?): CoverAnswer {
            asked += item.id
            running++
            maxRunning = maxOf(maxRunning, running)
            try {
                gate?.await()
            } finally {
                running--
            }
            return answers[item.id] ?: CoverAnswer.Found("https://cdn.example.org/${item.id}.jpg")
        }
    }

    private class Meter(var online: Boolean = true) : NetworkMeter {
        override fun isUnmetered(): Boolean = false
        override fun isOnline(): Boolean = online
    }

    private fun TestScope.store() = CoverStore(file, AppJson, backgroundScope, clock = { clock })

    private fun TestScope.resolver(
        source: FakeSource,
        network: NetworkMeter = Meter(),
        store: CoverStore? = null,
        light: () -> Boolean = { false },
        resolved: (String) -> String? = { null },
        cached: suspend (NewsItem) -> String? = { null },
    ) = CoverResolver(source, network, store, scope = backgroundScope, light = light, clock = { clock }, resolved = resolved, cached = cached)

    @Test
    fun `covers are looked up on mobile data, three at a time, each story once`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource().apply { gate = CompletableDeferred() }
        // A metered connection: "Artikel vorladen" would wait for Wi-Fi, covers do not.
        val resolver = resolver(source, Meter(online = true))
        val items = (1..8).map { item("a$it") }
        resolver.request(items + items.take(2), language = "de")
        runCurrent()
        assertEquals(3, source.running)
        source.gate!!.complete(Unit)
        runCurrent()
        assertEquals(3, source.maxRunning)
        assertEquals(items.map { it.id }, source.asked)
        assertEquals(items.associate { it.id to "https://cdn.example.org/${it.id}.jpg" }, resolver.covers.value)
        // Known covers are not asked for again.
        resolver.request(items)
        runCurrent()
        assertEquals(8, source.asked.size)
    }

    @Test
    fun `with light effects two lookups run at a time`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource().apply { gate = CompletableDeferred() }
        val resolver = resolver(source, light = { true })
        resolver.request((1..6).map { item("a$it") })
        runCurrent()
        assertEquals(2, source.running)
        source.gate!!.complete(Unit)
        runCurrent()
        assertEquals(2, source.maxRunning)
        assertEquals(6, resolver.covers.value.size)
    }

    @Test
    fun `stories with a picture of their own are never looked up`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val resolver = resolver(source)
        resolver.request(listOf(item("own", image = "https://cdn.example.org/own.jpg"), item("none")))
        runCurrent()
        assertEquals(listOf("none"), source.asked)
        assertEquals(setOf("none"), resolver.covers.value.keys)
    }

    @Test
    fun `offline nothing is tried and nothing is noted`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val network = Meter(online = false)
        val resolver = resolver(source, network, store())
        resolver.request(listOf(item("a")))
        runCurrent()
        assertTrue(source.asked.isEmpty())
        network.online = true
        resolver.request(listOf(item("a")))
        runCurrent()
        assertEquals(listOf("a"), source.asked)
    }

    @Test
    fun `a new window drops the waiting lookups of the old one`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource().apply { gate = CompletableDeferred() }
        val resolver = resolver(source)
        resolver.request((1..6).map { item("old$it") })
        runCurrent()
        // Three run, three wait; the list moved on.
        resolver.request((1..3).map { item("new$it") })
        source.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("old1", "old2", "old3", "new1", "new2", "new3"), source.asked)
        // Leaving the screen drops what waits as well.
        source.gate = CompletableDeferred()
        resolver.request((1..5).map { item("late$it") })
        runCurrent()
        resolver.stop()
        source.gate!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("late1", "late2", "late3"), source.asked.drop(6))
    }

    @Test
    fun `found covers and stories without one survive a restart`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource().apply { answers["none"] = CoverAnswer.None }
        val first = store()
        val resolver = resolver(source, store = first)
        resolver.request(listOf(item("a"), item("none")))
        runCurrent()
        first.save()

        val again = FakeSource()
        val restarted = resolver(again, store = store())
        runCurrent()
        // At once, before any request: the cards show the picture when the app opens.
        assertEquals(mapOf("a" to "https://cdn.example.org/a.jpg"), restarted.covers.value)
        restarted.request(listOf(item("a"), item("none")))
        runCurrent()
        assertTrue("nothing is asked twice", again.asked.isEmpty())
        // Newsrooms add pictures later: after half a day the story is asked again.
        clock += Duration.ofHours(13).toMillis()
        restarted.request(listOf(item("a"), item("none")))
        runCurrent()
        assertEquals(listOf("none"), again.asked)
        assertEquals("https://cdn.example.org/none.jpg", restarted.covers.value["none"])
    }

    @Test
    fun `the store keeps pictures a month, stays bounded and ignores what is not https`() = runTest(StandardTestDispatcher()) {
        val small = CoverStore(file, AppJson, backgroundScope, clock = { clock }, maxEntries = 3)
        small.record("a", "https://cdn.example.org/a.jpg")
        clock += 1000
        small.record("b", "https://cdn.example.org/b.jpg")
        small.record("c", null)
        small.record("d", "https://cdn.example.org/d.jpg")
        // "None" never replaces a picture that is still kept.
        small.record("b", null)
        small.save()
        assertEquals(mapOf("b" to "https://cdn.example.org/b.jpg", "d" to "https://cdn.example.org/d.jpg"), store().restore())
        assertTrue(store().isKnownMissing("c"))
        assertFalse(store().isKnownMissing("b"))
        assertFalse(store().isKnownMissing("unknown"))
        clock += Duration.ofDays(31).toMillis()
        assertTrue(store().restore().isEmpty())
        assertFalse(store().isKnownMissing("c"))

        // Files of their own: the store above may still write its file once more.
        val edited = File(dir, "edited.json")
        edited.writeText("""{"version":1,"entries":[{"id":"x","url":"http://cdn.example.org/x.jpg","at":$clock},{"id":"y","url":"https://cdn.example.org/y.jpg","at":$clock}]}""")
        assertEquals(mapOf("y" to "https://cdn.example.org/y.jpg"), CoverStore(edited, AppJson, backgroundScope, clock = { clock }).restore())
        val broken = File(dir, "broken.json")
        broken.writeText("not json")
        assertTrue(CoverStore(broken, AppJson, backgroundScope, clock = { clock }).restore().isEmpty())
    }

    @Test
    fun `what the reader and the prefetcher found needs no lookup`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val kept = store()
        val resolver = resolver(source, store = kept, cached = { item -> "https://cdn.example.org/cached-${item.id}.jpg".takeIf { item.id == "read" } })
        val prefetched = MutableStateFlow(emptyMap<String, String>())
        resolver.follow(prefetched)
        prefetched.value = mapOf("warm" to "https://cdn.example.org/lead.jpg", "plain" to "http://cdn.example.org/plain.jpg")
        runCurrent()
        assertEquals(mapOf("warm" to "https://cdn.example.org/lead.jpg"), resolver.covers.value)
        resolver.request(listOf(item("warm"), item("read"), item("new")))
        runCurrent()
        // "read" lies in the article cache; only "new" goes to the network.
        assertEquals(listOf("new"), source.asked)
        assertEquals("https://cdn.example.org/cached-read.jpg", resolver.covers.value["read"])
        kept.save()
        assertEquals(setOf("warm", "read", "new"), store().restore().keys)
    }

    @Test
    fun `when Google rate-limits, unresolved links wait - resolved ones still get their cover`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource().apply { answers["a1"] = CoverAnswer.Unresolved(429) }
        val known = HashMap<String, String>()
        val resolver = resolver(source, resolved = { known[it] })
        resolver.request(listOf(item("a1")))
        runCurrent()
        assertEquals(listOf("a1"), source.asked)
        // Paused: no further link goes to Google …
        val resolvedBefore = item("a3")
        known[resolvedBefore.url] = "https://www.example.org/a3"
        resolver.request(listOf(item("a2"), resolvedBefore, item("direct", google = false)))
        runCurrent()
        // … but a link resolved before and a publisher's own link need no Google.
        assertEquals(listOf("a1", "a3", "direct"), source.asked)
        // After the pause the waiting ones are tried (the failed one after its own pause, too).
        source.answers.remove("a1")
        clock += CoverResolver.GOOGLE_PAUSE_MS + 1
        resolver.request(listOf(item("a1"), item("a2")))
        runCurrent()
        assertEquals(listOf("a1", "a3", "direct", "a1", "a2"), source.asked)
        assertEquals(setOf("a1", "a2", "a3", "direct"), resolver.covers.value.keys)
    }

    @Test
    fun `three link failures in a row pause Google as well, and the pause doubles`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        (1..9).forEach { source.answers["f$it"] = CoverAnswer.Unresolved(null) }
        val resolver = resolver(source)
        resolver.request((1..3).map { item("f$it") })
        runCurrent()
        assertEquals(3, source.asked.size)
        resolver.request(listOf(item("f4")))
        runCurrent()
        assertEquals("paused after three failures", 3, source.asked.size)
        clock += CoverResolver.GOOGLE_PAUSE_MS + 1
        resolver.request((4..6).map { item("f$it") })
        runCurrent()
        assertEquals(6, source.asked.size)
        // The second pause is twice as long.
        clock += CoverResolver.GOOGLE_PAUSE_MS + 1
        resolver.request(listOf(item("f7")))
        runCurrent()
        assertEquals(6, source.asked.size)
        clock += CoverResolver.GOOGLE_PAUSE_MS
        resolver.request(listOf(item("f7")))
        runCurrent()
        assertEquals(7, source.asked.size)
    }

    @Test
    fun `a site that keeps refusing is left alone, its stories keep the generated cover`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        (1..5).forEach { source.answers["w$it"] = CoverAnswer.Refused("www.walled.example", 403) }
        val resolver = resolver(source)
        resolver.request((1..3).map { item("w$it", google = false, domain = "walled.example") })
        runCurrent()
        assertEquals(3, source.asked.size)
        resolver.request(listOf(item("w4", google = false, domain = "walled.example"), item("ok", google = false, domain = "open.example")))
        runCurrent()
        assertEquals(listOf("w1", "w2", "w3", "ok"), source.asked)
        assertNull(resolver.covers.value["w1"])
        // The refused stories count as "no picture" for now: not asked again right away.
        resolver.request(listOf(item("w1", google = false, domain = "walled.example")))
        runCurrent()
        assertEquals(4, source.asked.size)
        clock += CoverResolver.HOST_PAUSE_MS + 1
        resolver.request(listOf(item("w4", google = false, domain = "walled.example")))
        runCurrent()
        assertEquals(listOf("w1", "w2", "w3", "ok", "w4"), source.asked)
    }

    @Test
    fun `a network failure is tried again after a pause that grows`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource().apply { answers["n"] = CoverAnswer.Unreachable }
        val resolver = resolver(source, store = store())
        val story = item("n", google = false)
        resolver.request(listOf(story))
        runCurrent()
        resolver.request(listOf(story))
        runCurrent()
        assertEquals(1, source.asked.size)
        clock += CoverResolver.RETRY_AFTER_MS
        resolver.request(listOf(story))
        runCurrent()
        assertEquals(2, source.asked.size)
        clock += CoverResolver.RETRY_AFTER_MS
        resolver.request(listOf(story))
        runCurrent()
        assertEquals("the second pause is twice as long", 2, source.asked.size)
        source.answers.remove("n")
        clock += CoverResolver.RETRY_AFTER_MS
        resolver.request(listOf(story))
        runCurrent()
        assertEquals(3, source.asked.size)
        assertEquals("https://cdn.example.org/n.jpg", resolver.covers.value["n"])
        // Nothing about the failures was kept on disk.
        assertFalse(store().isKnownMissing("n"))
    }
}

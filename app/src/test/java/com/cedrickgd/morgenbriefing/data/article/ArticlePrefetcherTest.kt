package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.AppJson
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.PrefetchMode
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ArticlePrefetcherTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("prefetcher").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun item(n: Int, publisher: String = "news$n.example.com") = NewsItem(
        id = "id$n", headline = "Stadtrat beschließt Haushalt $n", url = "https://$publisher/a$n", source = "S", sourceId = "s", topicId = "welt",
        publisherDomain = publisher,
    )

    /** A complete article: three paragraphs of real text (what the strict judgement accepts). */
    private fun complete(url: String, image: String) = ArticleContent(
        url = url, title = "Stadtrat beschließt Haushalt", fetchedAt = "2026-11-05T12:00:00+01:00",
        blocks = listOf(ArticleBlock.image(image)) + (1..3).map {
            ArticleBlock.paragraph("Der Stadtrat hat am Abend den Haushalt beschlossen, nach einer langen Debatte über Schulen, Straßen und den Nahverkehr der Stadt in Absatz $it.")
        },
    )

    /** Only the lead of an article: one short paragraph. */
    private fun teaser(url: String) = ArticleContent(
        url = url, title = "Stadtrat beschließt Haushalt", fetchedAt = "2026-11-05T12:00:00+01:00",
        blocks = listOf(ArticleBlock.paragraph("Der Stadtrat hat am Abend den Haushalt beschlossen.")),
    )

    private fun content(n: Int) = complete("https://news$n.example.com/a$n", "https://img.example.com/$n.jpg")

    /** Loads that wait until the test lets them finish; counts how many run at once. */
    private inner class FakeSource : ArticlePrefetcher.Source {
        val cached = HashMap<String, ArticleContent>()
        val blocked = HashMap<String, ReadOutcome>()
        val failures = HashMap<String, ReadOutcome>()
        val teasers = HashSet<String>()
        val gates = HashMap<String, CompletableDeferred<Unit>>()
        val loads = ArrayList<String>()
        val running = AtomicInteger()
        var maxRunning = 0

        override suspend fun cached(item: NewsItem) = cached[item.id]

        override suspend fun load(item: NewsItem, language: String?): ArticleContent {
            loads += item.id
            maxRunning = maxOf(maxRunning, running.incrementAndGet())
            try {
                gates.getOrPut(item.id) { CompletableDeferred() }.await()
                failures[item.id]?.let { throw ArticleException(it) }
                return if (item.id in teasers) teaser(item.url) else complete(item.url, "https://img.example.com/${item.id}.jpg")
            } finally {
                running.decrementAndGet()
            }
        }

        override fun blockReason(item: NewsItem) = blocked[item.publisherDomain]

        fun release(id: String) = gates.getOrPut(id) { CompletableDeferred() }.complete(Unit)
    }

    private fun TestScope.prefetcher(source: FakeSource, unmetered: Boolean = true, maxQueued: Int = 40) =
        prefetcher(source, NetworkMeter { unmetered }, maxQueued)

    private fun TestScope.prefetcher(source: FakeSource, network: NetworkMeter, maxQueued: Int = 40, verdicts: VerdictStore? = null) =
        ArticlePrefetcher(source, network, scope = this.backgroundScope, maxQueued = maxQueued, clock = { testScheduler.currentTime }, verdicts = verdicts)

    /** A network meter for tests: metered or not, online or not. */
    private class Meter(var unmetered: Boolean = true, var online: Boolean = true) : NetworkMeter {
        override fun isUnmetered() = unmetered
        override fun isOnline() = online
    }

    @Test
    fun `at most three load at a time, every item once, statuses and pictures are published`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val p = prefetcher(source)
        val items = (1..5).map(::item)
        p.prefetch(items, PrefetchMode.ALWAYS)
        p.prefetch(items, PrefetchMode.ALWAYS) // the same items again: no second load
        runCurrent()
        assertEquals(3, source.running.get())
        assertEquals(ArticleStatus.LOADING, p.statusOf("id1"))
        items.forEach { source.release(it.id) }
        runCurrent()
        assertEquals(3, source.maxRunning)
        assertEquals(listOf("id1", "id2", "id3", "id4", "id5"), source.loads)
        assertTrue(items.all { p.statusOf(it.id) == ArticleStatus.READABLE })
        assertEquals("https://img.example.com/id3.jpg", p.images.value["id3"])
        // Readable articles are not loaded again.
        p.prefetch(items, PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(5, source.loads.size)
    }

    @Test
    fun `only a complete article is verified, a teaser counts as paid`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        source.teasers += "id2"
        source.cached["id3"] = teaser("https://news3.example.com/a3")
        listOf("id1", "id2").forEach(source::release)
        val p = prefetcher(source)
        p.prefetch(listOf(item(1), item(2), item(3)), PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(ArticleStatus.READABLE, p.statusOf("id1"))
        assertEquals(ArticleStatus.PAYWALL, p.statusOf("id2"))
        assertEquals("a cached teaser is no verified article either", ArticleStatus.PAYWALL, p.statusOf("id3"))
        // The reader's own results are judged the same way.
        p.report(item(4), teaser("https://news4.example.com/a4"))
        p.report(item(5), content(5))
        assertEquals(ArticleStatus.PAYWALL, p.statusOf("id4"))
        assertEquals(ArticleStatus.READABLE, p.statusOf("id5"))
    }

    @Test
    fun `cached and known-unreadable items are answered without loading, in every mode`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        source.cached["id1"] = content(1)
        source.blocked["news2.example.com"] = ReadOutcome.PAYWALL
        source.blocked["news3.example.com"] = ReadOutcome.CONSENT
        val p = prefetcher(source)
        p.prefetch(listOf(item(1), item(2), item(3)), PrefetchMode.OFF)
        runCurrent()
        assertEquals(ArticleStatus.READABLE, p.statusOf("id1"))
        assertEquals("https://img.example.com/1.jpg", p.images.value["id1"])
        assertEquals(ArticleStatus.PAYWALL, p.statusOf("id2"))
        assertEquals(ArticleStatus.BLOCKED, p.statusOf("id3"))
        assertTrue(source.loads.isEmpty())
    }

    @Test
    fun `off never loads, wifi-only waits for an unmetered network`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        prefetcher(source).prefetch(listOf(item(1)), PrefetchMode.OFF)
        prefetcher(source, unmetered = false).prefetch(listOf(item(2)), PrefetchMode.WIFI_ONLY)
        runCurrent()
        assertTrue(source.loads.isEmpty())
        source.release("id3")
        val p = prefetcher(source, unmetered = true)
        p.prefetch(listOf(item(3)), PrefetchMode.WIFI_ONLY)
        runCurrent()
        assertEquals(listOf("id3"), source.loads)
        assertEquals(ArticleStatus.READABLE, p.statusOf("id3"))
    }

    @Test
    fun `nur vollstaendige Artikel verifies on mobile data too, whatever the prefetch mode says`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        source.release("id1")
        source.release("id2")
        val p = prefetcher(source, Meter(unmetered = false))
        p.prefetch(listOf(item(1)), PrefetchMode.OFF, verify = true)
        runCurrent()
        assertEquals(listOf("id1"), source.loads)
        assertEquals(ArticleStatus.READABLE, p.statusOf("id1"))
        // Through the settings: readableOnly turns verification on.
        p.prefetch(listOf(item(2)), AppSettings(readableOnly = true, prefetch = PrefetchMode.WIFI_ONLY))
        runCurrent()
        assertEquals(listOf("id1", "id2"), source.loads)
        // And off again: "Nur im WLAN" rules, no load on mobile data.
        p.prefetch(listOf(item(3)), AppSettings(readableOnly = false, prefetch = PrefetchMode.WIFI_ONLY))
        runCurrent()
        assertEquals(listOf("id1", "id2"), source.loads)
    }

    @Test
    fun `offline nothing is tried, the request is taken up again once the phone is online`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val meter = Meter(online = false)
        (1..2).forEach { source.release("id$it") }
        val p = prefetcher(source, meter)
        p.prefetch(listOf(item(1), item(2)), PrefetchMode.ALWAYS, verify = true)
        runCurrent()
        assertTrue(source.loads.isEmpty())
        assertTrue(p.waitingForNetwork.value)
        assertEquals("no failure: the items stay unknown, not rejected", ArticleStatus.UNKNOWN, p.statusOf("id1"))
        advanceTimeBy(ArticlePrefetcher.NETWORK_POLL_MS * 3)
        assertTrue(source.loads.isEmpty())
        meter.online = true
        advanceTimeBy(ArticlePrefetcher.NETWORK_POLL_MS + 1)
        runCurrent()
        assertFalse(p.waitingForNetwork.value)
        assertEquals(listOf("id1", "id2"), source.loads)
        assertEquals(ArticleStatus.READABLE, p.statusOf("id2"))
    }

    @Test
    fun `verdicts survive a restart, network failures are not kept`() = runTest(StandardTestDispatcher()) {
        val file = File(dir, "verdicts.json")
        val source = FakeSource()
        source.teasers += "id2"
        source.failures["id3"] = ReadOutcome.NOT_ARTICLE
        source.failures["id4"] = ReadOutcome.NETWORK
        (1..4).forEach { source.release("id$it") }
        val store = VerdictStore(file, AppJson, backgroundScope, clock = { 1_800_000_000_000L })
        val first = prefetcher(source, Meter(), verdicts = store)
        first.prefetch((1..4).map(::item), PrefetchMode.ALWAYS, verify = true)
        runCurrent()
        store.save() // what the store does by itself SAVE_DELAY_MS later
        assertTrue(file.isFile)

        // A new process: the verdicts are back before anything is loaded again.
        val again = FakeSource()
        val restored = VerdictStore(file, AppJson, backgroundScope, clock = { 1_800_000_000_000L })
        val second = prefetcher(again, Meter(), verdicts = restored)
        runCurrent()
        assertEquals(ArticleStatus.READABLE, second.statusOf("id1"))
        assertEquals(ArticleStatus.PAYWALL, second.statusOf("id2"))
        assertEquals(ArticleStatus.FAILED, second.statusOf("id3"))
        assertEquals(ArticleStatus.UNKNOWN, second.statusOf("id4"))
        again.release("id4")
        second.prefetch((1..4).map(::item), PrefetchMode.ALWAYS, verify = true)
        runCurrent()
        assertEquals("only the network failure is tried again", listOf("id4"), again.loads)
    }

    @Test
    fun `verify waits for the verdict of one article`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        source.teasers += "id2"
        val p = prefetcher(source, Meter(unmetered = false))
        // A setting that forbids prefetching does not stop the morning check.
        p.prefetch(emptyList(), PrefetchMode.OFF)
        val one = async { p.verify(item(1), "de") }
        val two = async { p.verify(item(2), "de") }
        runCurrent()
        source.release("id1")
        source.release("id2")
        runCurrent()
        assertEquals(ArticleStatus.READABLE, one.await())
        assertEquals(ArticleStatus.PAYWALL, two.await())
        // Known now: answered without a second load.
        assertEquals(ArticleStatus.READABLE, p.verify(item(1), "de"))
        assertEquals(listOf("id1", "id2"), source.loads)
    }

    @Test
    fun `failures map to statuses, network failures are tried again after a growing pause`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        source.failures["id1"] = ReadOutcome.PAYWALL
        source.failures["id2"] = ReadOutcome.BLOCKED
        source.failures["id3"] = ReadOutcome.NETWORK
        listOf("id1", "id2", "id3").forEach(source::release)
        val p = prefetcher(source)
        val items = listOf(item(1), item(2), item(3))
        p.prefetch(items, PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(ArticleStatus.PAYWALL, p.statusOf("id1"))
        assertEquals(ArticleStatus.BLOCKED, p.statusOf("id2"))
        assertEquals(ArticleStatus.FAILED, p.statusOf("id3"))
        // Scrolling on right away: nothing is loaded again.
        p.prefetch(items, PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(listOf("id1", "id2", "id3"), source.loads)
        // After the first pause the network failure is tried again – and fails again: the next pause is twice as long.
        advanceTimeBy(ArticlePrefetcher.RETRY_AFTER_MS)
        p.prefetch(items, PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(listOf("id1", "id2", "id3", "id3"), source.loads)
        advanceTimeBy(ArticlePrefetcher.RETRY_AFTER_MS)
        p.prefetch(items, PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(4, source.loads.size)
        advanceTimeBy(ArticlePrefetcher.RETRY_AFTER_MS)
        source.failures.remove("id3")
        p.prefetch(items, PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(listOf("id1", "id2", "id3", "id3", "id3"), source.loads)
        assertEquals(ArticleStatus.READABLE, p.statusOf("id3"))
    }

    @Test
    fun `a page without an article is not loaded again this session`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        source.failures["id1"] = ReadOutcome.NOT_ARTICLE
        source.release("id1")
        val p = prefetcher(source)
        repeat(4) {
            p.prefetch(listOf(item(1)), PrefetchMode.ALWAYS)
            runCurrent()
            advanceTimeBy(ArticlePrefetcher.RETRY_MAX_MS)
        }
        assertEquals(listOf("id1"), source.loads)
        assertEquals(ArticleStatus.FAILED, p.statusOf("id1"))
        // The reader found nothing either: no prefetch of it afterwards.
        p.report(item(2), ArticleException(ReadOutcome.NOT_ARTICLE))
        p.prefetch(listOf(item(2)), PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(listOf("id1"), source.loads)
    }

    @Test
    fun `wifi-only loads that waited are skipped when the phone left the wifi meanwhile`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        var unmetered = true
        val p = prefetcher(source, NetworkMeter { unmetered })
        p.prefetch((1..5).map(::item), PrefetchMode.WIFI_ONLY)
        runCurrent()
        assertEquals(listOf("id1", "id2", "id3"), source.loads)
        unmetered = false
        (1..5).forEach { source.release("id$it") }
        runCurrent()
        assertEquals("id4 and id5 waited and would now go over mobile data", listOf("id1", "id2", "id3"), source.loads)
        assertEquals(ArticleStatus.UNKNOWN, p.statusOf("id4"))
    }

    @Test
    fun `a newer window goes first, waiting loads of cards that scrolled away are dropped`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val p = prefetcher(source)
        p.prefetch((1..6).map(::item), PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(listOf("id1", "id2", "id3"), source.loads)
        // The user flung on: cards 7 and 8 are on screen now, 4..6 never started.
        p.prefetch(listOf(item(7), item(8)), PrefetchMode.ALWAYS)
        (1..8).forEach { source.release("id$it") }
        runCurrent()
        assertEquals(listOf("id1", "id2", "id3", "id7", "id8"), source.loads)
        assertEquals(ArticleStatus.READABLE, p.statusOf("id1"))
        assertEquals(ArticleStatus.UNKNOWN, p.statusOf("id4"))
    }

    @Test
    fun `cancelling stops waiting and running loads, the oldest waiting ones go when too many wait`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val p = prefetcher(source, maxQueued = 2)
        p.prefetch((1..7).map(::item), PrefetchMode.ALWAYS)
        runCurrent()
        // id1..id3 run; of id4..id7 only the newest two may wait.
        assertEquals(listOf("id1", "id2", "id3"), source.loads)
        p.cancel(listOf("id1"))
        runCurrent()
        assertEquals(ArticleStatus.UNKNOWN, p.statusOf("id1"))
        (1..7).forEach { source.release("id$it") }
        runCurrent()
        assertEquals(listOf("id1", "id2", "id3", "id6", "id7"), source.loads)
        assertEquals(ArticleStatus.UNKNOWN, p.statusOf("id4"))
        p.cancelAll()
    }

    @Test
    fun `the reader's own results are reported`() = runTest(StandardTestDispatcher()) {
        val p = prefetcher(FakeSource())
        p.report(item(1), content(1))
        p.report(item(2), ArticleException(ReadOutcome.PAYWALL))
        p.report(item(3), IllegalStateException())
        assertEquals(ArticleStatus.READABLE, p.statusOf("id1"))
        assertEquals(ArticleStatus.PAYWALL, p.statusOf("id2"))
        assertEquals(ArticleStatus.FAILED, p.statusOf("id3"))
    }
}

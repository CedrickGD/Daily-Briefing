package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.PrefetchMode
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ArticlePrefetcherTest {

    private fun item(n: Int, publisher: String = "news$n.example.com") = NewsItem(
        id = "id$n", headline = "H$n", url = "https://$publisher/a$n", source = "S", sourceId = "s", topicId = "welt", publisherDomain = publisher,
    )

    private fun content(n: Int) = ArticleContent(
        url = "https://news$n.example.com/a$n", title = "T", fetchedAt = "2026-11-05T12:00:00+01:00",
        blocks = listOf(ArticleBlock.image("https://img.example.com/$n.jpg"), ArticleBlock.paragraph("Text")),
    )

    /** Loads that wait until the test lets them finish; counts how many run at once. */
    private class FakeSource : ArticlePrefetcher.Source {
        val cached = HashMap<String, ArticleContent>()
        val blocked = HashMap<String, ReadOutcome>()
        val failures = HashMap<String, ReadOutcome>()
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
                return ArticleContent(url = item.url, title = "T", fetchedAt = "x", blocks = listOf(ArticleBlock.image("https://img.example.com/${item.id}.jpg")))
            } finally {
                running.decrementAndGet()
            }
        }

        override fun blockReason(item: NewsItem) = blocked[item.publisherDomain]

        fun release(id: String) = gates.getOrPut(id) { CompletableDeferred() }.complete(Unit)
    }

    private fun TestScope.prefetcher(source: FakeSource, unmetered: Boolean = true, maxQueued: Int = 40) =
        prefetcher(source, NetworkMeter { unmetered }, maxQueued)

    private fun TestScope.prefetcher(source: FakeSource, network: NetworkMeter, maxQueued: Int = 40) =
        ArticlePrefetcher(source, network, scope = this.backgroundScope, maxQueued = maxQueued, clock = { testScheduler.currentTime })

    @Test
    fun `at most two load at a time, every item once, statuses and pictures are published`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val p = prefetcher(source)
        val items = (1..5).map(::item)
        p.prefetch(items, PrefetchMode.ALWAYS)
        p.prefetch(items, PrefetchMode.ALWAYS) // the same items again: no second load
        runCurrent()
        assertEquals(2, source.running.get())
        assertEquals(ArticleStatus.LOADING, p.statusOf("id1"))
        items.forEach { source.release(it.id) }
        runCurrent()
        assertEquals(2, source.maxRunning)
        assertEquals(listOf("id1", "id2", "id3", "id4", "id5"), source.loads)
        assertTrue(items.all { p.statusOf(it.id) == ArticleStatus.READABLE })
        assertEquals("https://img.example.com/id3.jpg", p.images.value["id3"])
        // Readable articles are not loaded again.
        p.prefetch(items, PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(5, source.loads.size)
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
        p.prefetch((1..4).map(::item), PrefetchMode.WIFI_ONLY)
        runCurrent()
        assertEquals(listOf("id1", "id2"), source.loads)
        unmetered = false
        (1..4).forEach { source.release("id$it") }
        runCurrent()
        assertEquals("id3 and id4 waited and would now go over mobile data", listOf("id1", "id2"), source.loads)
        assertEquals(ArticleStatus.UNKNOWN, p.statusOf("id3"))
    }

    @Test
    fun `a newer window goes first, waiting loads of cards that scrolled away are dropped`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val p = prefetcher(source)
        p.prefetch((1..6).map(::item), PrefetchMode.ALWAYS)
        runCurrent()
        assertEquals(listOf("id1", "id2"), source.loads)
        // The user flung on: cards 7 and 8 are on screen now, 3..6 never started.
        p.prefetch(listOf(item(7), item(8)), PrefetchMode.ALWAYS)
        (1..8).forEach { source.release("id$it") }
        runCurrent()
        assertEquals(listOf("id1", "id2", "id7", "id8"), source.loads)
        assertEquals(ArticleStatus.READABLE, p.statusOf("id1"))
        assertEquals(ArticleStatus.UNKNOWN, p.statusOf("id4"))
    }

    @Test
    fun `cancelling stops waiting and running loads, the oldest waiting ones go when too many wait`() = runTest(StandardTestDispatcher()) {
        val source = FakeSource()
        val p = prefetcher(source, maxQueued = 2)
        p.prefetch((1..6).map(::item), PrefetchMode.ALWAYS)
        runCurrent()
        // id1 and id2 run; of id3..id6 only the newest two may wait.
        assertEquals(listOf("id1", "id2"), source.loads)
        p.cancel(listOf("id1"))
        runCurrent()
        assertEquals(ArticleStatus.UNKNOWN, p.statusOf("id1"))
        (1..6).forEach { source.release("id$it") }
        runCurrent()
        assertEquals(listOf("id1", "id2", "id5", "id6"), source.loads)
        assertEquals(ArticleStatus.UNKNOWN, p.statusOf("id3"))
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

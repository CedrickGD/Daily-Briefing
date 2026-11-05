package com.cedrickgd.morgenbriefing.data

import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceKind
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NewsRepositoryMergeTest {

    private val now: Instant = Instant.parse("2026-10-29T12:00:00Z")
    private val a = FeedSource("a", "A", "https://a.de/feed", "ki")
    private val b = FeedSource("b", "B", "https://b.de/feed", "ki")
    private val gn = FeedSource("gn:ki@DE:de", "Google News", "https://news.google.com/rss/search?q=KI", "ki", kind = SourceKind.GOOGLE_NEWS, edition = "DE:de")

    private fun item(id: String, source: FeedSource, at: String?, headline: String = "Schlagzeile Nummer $id", url: String = "https://x/$id") =
        NewsItem(id = id, headline = headline, url = url, source = source.name, sourceId = source.id, topicId = "ki", publishedAt = at)

    private fun googleItem(id: String, headline: String, domain: String, at: String? = "2026-10-29T10:00:00+01:00") =
        item(id, gn, at, headline, url = "https://news.google.com/rss/articles/CBMi$id?oc=5").copy(source = domain, publisherDomain = domain, aggregator = "google")

    private fun ok(source: FeedSource, vararg items: NewsItem) = NewsRepository.FetchResult(source, items.toList(), null)
    private fun failed(source: FeedSource) = NewsRepository.FetchResult(source, emptyList(), "Nicht erreichbar")

    @Test
    fun `newest first, undated last, statuses per source`() {
        val merged = NewsRepository.merge(
            "ki", null,
            listOf(
                ok(a, item("1", a, "2026-10-29T10:00:00+01:00"), item("2", a, null)),
                ok(b, item("3", b, "2026-10-29T12:30:00+01:00")),
            ),
            now,
        )
        assertEquals(listOf("3", "1", "2"), merged.items.map { it.id })
        assertEquals(listOf(true, true), merged.sources.map { it.ok })
        assertEquals("2026-10-29T13:00:00+01:00", merged.fetchedAt)
    }

    @Test
    fun `a failed source keeps its recent articles from last time`() {
        val previous = TopicNews("ki", "2026-10-29T08:00:00+01:00", listOf(item("old", b, "2026-10-29T07:00:00+01:00"), item("ancient", b, "2026-10-20T07:00:00+01:00"), item("fromA", a, "2026-10-29T07:30:00+01:00")))
        val merged = NewsRepository.merge("ki", previous, listOf(ok(a, item("new", a, "2026-10-29T11:00:00+01:00")), failed(b)), now)
        assertEquals(listOf("new", "old"), merged.items.map { it.id })
        val status = merged.sources.single { it.sourceId == "b" }
        assertFalse(status.ok)
        assertEquals("Nicht erreichbar", status.error)
        assertEquals(1, status.count)
        assertTrue(merged.failedSources.isNotEmpty())
    }

    @Test
    fun `the same story shows once across sources`() {
        val items = listOf(
            item("1", a, null, "Gleiche Schlagzeile für alle"),
            item("1", a, null, "Andere Schlagzeile, gleiche Id"),
            item("2", a, null, "gleiche Schlagzeile für alle "),
            item("3", b, null, "„Gleiche Schlagzeile – für alle!“"),
            item("4", b, null, "Kurz"),
            item("5", a, null, "Kurz"),
        )
        // Short headlines are too generic to merge by.
        assertEquals(listOf("1", "4", "5"), NewsRepository.dedupe(items).map { it.id })
    }

    @Test
    fun `the publisher's own item wins over Google News`() {
        val google = googleItem("g1", "Dax springt auf Rekordhoch", "tagesschau.de")
        val own = item("t1", a, "2026-10-29T10:05:00+01:00", "DAX springt auf Rekordhoch").copy(image = ArticleImage("https://img/x.jpg"))
        assertEquals(listOf("t1"), NewsRepository.dedupe(listOf(google, own)).map { it.id })
        // A resolved Google News link is the publisher's link.
        val resolvedGoogle = googleItem("g2", "Ganz anderer Wortlaut der Meldung", "a.de")
        val publisher = item("p", a, null, "Die Schlagzeile der Redaktion selbst", url = "https://www.a.de/story?utm_source=rss")
        val resolved = { url: String -> if (url == resolvedGoogle.url) "https://a.de/story" else null }
        assertEquals(listOf("p"), NewsRepository.dedupe(listOf(resolvedGoogle, publisher), resolved).map { it.id })
        assertEquals(2, NewsRepository.dedupe(listOf(resolvedGoogle, publisher)).size)
    }

    @Test
    fun `articles of walled sites go when only readable ones are wanted`() {
        val merged = NewsRepository.merge(
            "ki", null,
            listOf(ok(gn, googleItem("1", "Bei der Zeit hinter der Bezahlschranke", "zeit.de"), googleItem("2", "Bei der Tagesschau frei lesbar heute", "tagesschau.de"))),
            now,
            blocked = setOf("zeit.de"),
        )
        assertEquals(listOf("2"), merged.items.map { it.id })
        assertEquals(1, NewsRepository.readable(listOf(item("x", a, null, url = "https://www.zeit.de/x"), item("y", a, null)), setOf("zeit.de")).size)
    }

    @Test
    fun `paged articles survive a refresh while their source belongs to the topic`() {
        val paged = googleItem("old", "Eine Meldung von letzter Woche", "a.de", at = "2026-10-20T10:00:00+01:00").copy(sourceId = gn.id + NewsRepository.PAGE_SUFFIX)
        val previous = TopicNews("ki", "2026-10-29T08:00:00+01:00", listOf(paged))
        val kept = NewsRepository.merge("ki", previous, listOf(ok(gn, googleItem("new", "Eine neue Meldung von heute früh", "a.de"))), now)
        assertEquals(listOf("new", "old"), kept.items.map { it.id })
        val dropped = NewsRepository.merge("ki", previous, listOf(ok(a, item("x", a, "2026-10-29T10:00:00+01:00"))), now)
        assertEquals(listOf("x"), dropped.items.map { it.id })
    }

    @Test
    fun `a page appends older articles and counts what is new`() {
        val previous = TopicNews("ki", "2026-10-29T08:00:00+01:00", listOf(item("1", a, "2026-10-29T10:00:00+01:00")))
        val (news, added) = NewsRepository.appendPage(
            "ki", previous,
            listOf(item("1", a, "2026-10-29T10:00:00+01:00"), item("2", a, "2026-10-25T10:00:00+01:00")),
            now,
        )
        assertEquals(1, added)
        assertEquals(listOf("1", "2"), news.items.map { it.id })
        assertEquals(previous.fetchedAt, news.fetchedAt)
    }

    @Test
    fun `at most max per topic survive`() {
        val many = (1..NewsRepository.MAX_PER_TOPIC + 100).map { item("$it", a, "2026-10-29T10:00:00+01:00") }
        val merged = NewsRepository.merge("ki", null, listOf(ok(a, *many.toTypedArray())), now)
        assertEquals(NewsRepository.MAX_PER_TOPIC, merged.items.size)
    }

    @Test
    fun `a round in which every source failed keeps the previous fetch time`() {
        val previous = TopicNews("ki", "2026-10-29T10:00:00+01:00", listOf(item("1", a, "2026-10-29T09:00:00+01:00")))
        val merged = NewsRepository.merge("ki", previous, listOf(failed(a), failed(b)), now)
        assertEquals("2026-10-29T10:00:00+01:00", merged.fetchedAt)
        assertTrue(merged.allFailed)
        assertEquals(listOf("1"), merged.items.map { it.id })
        // Without anything to keep, the attempt is still stamped so the UI can say when it was.
        assertEquals("2026-10-29T13:00:00+01:00", NewsRepository.merge("ki", null, listOf(failed(a)), now).fetchedAt)
    }

    @Test
    fun `texts fold for comparison and search`() {
        assertEquals("borse dax rekord", NewsRepository.normalizeText("„Börse: DAX-Rekord!“"))
        assertEquals("strasse", NewsRepository.normalizeText("Straße"))
        assertEquals(listOf("midterms", "senat"), NewsRepository.searchWords("Midterms OR Senat -site:x.com when:1d"))
    }
}

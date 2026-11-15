package com.cedrickgd.morgenbriefing.data

import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceKind
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.news.GoogleNews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

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

    private fun paged(id: String, at: String?) =
        googleItem(id, "Eine ältere Meldung Nummer $id", "a.de", at = at).copy(sourceId = gn.id + NewsRepository.PAGE_SUFFIX)

    @Test
    fun `what a publisher's feed only drags along is left out`() {
        // ZDFheute's feed on 15.11.2026: today's news, and rubric pages and house notices up to seven years old.
        val zdf = FeedSource("zdfheute", "ZDFheute", "https://www.zdf.de/rss/zdf/nachrichten", "top")
        fun zdf(id: String, at: String?, headline: String, path: String) = item(id, zdf, at, headline, url = "https://www.zdfheute.de/$path")
        val delivered = listOf(
            zdf("fresh", "2026-10-29T11:00:00+01:00", "Estland schließt Grenzübergänge zu Russland", "politik/estland-grenze-100.html"),
            // 54 days: a slow desk's article is still an article.
            zdf("slow", "2026-09-05T09:00:00+02:00", "Korrekturen und Richtigstellungen", "in-eigener-sache/korrekturen-104.html"),
            zdf("notice", "2026-08-18T09:00:00+02:00", "Bettina Schausten bleibt ZDF-Chefredakteurin", "in-eigener-sache/schausten-100.html"),
            zdf("rubric", "2024-08-20T09:00:00+02:00", "Nachrichten aus Sachsen", "thema/sachsen-news-100.html"),
            zdf("faq", "2019-10-08T10:00:00+02:00", "Fragen und Antworten", "in-eigener-sache/fragen-und-antworten-114.html"),
            // A rubric page is none whatever its date says.
            zdf("index", "2026-10-28T09:00:00+01:00", "Nachrichten aus Sachsen-Anhalt", "thema/sachsen-anhalt-news-100.html"),
            zdf("undated", null, "Ein Beitrag ohne Datum im Feed", "panorama/ohne-datum-100.html"),
        )
        val merged = NewsRepository.merge("top", null, listOf(ok(zdf, *delivered.toTypedArray())), now)
        assertEquals(listOf("fresh", "slow", "undated"), merged.items.map { it.id })
        assertEquals(3, merged.sources.single().count)
        assertTrue(NewsRepository.isIndexPage("https://www.zdfheute.de/thema/aktuelle-lottozahlen-100.html"))
        assertFalse(NewsRepository.isIndexPage("https://www.tagesschau.de/inland/innenpolitik/thema-des-tages-100.html"))
        assertEquals(60, NewsRepository.STALE_AFTER.toDays())

        // A quiet desk with nothing newer keeps what it has.
        val quiet = listOf(item("q1", a, "2026-06-01T09:00:00+02:00"), item("q2", a, "2026-03-01T09:00:00+01:00"))
        assertEquals(quiet, NewsRepository.current(a, quiet, now))
        // Google News keeps old articles (a small town's place page, the stray hits paging counts on), and so does a feed the user added.
        val mine = FeedSource("custom:1", "Mein Blog", "https://blog.example/feed", "ki", builtIn = false)
        val mixed = listOf(item("new", mine, "2026-10-28T09:00:00+01:00"), item("old", mine, "2025-01-01T09:00:00+01:00", url = "https://blog.example/tag/old"))
        assertEquals(mixed, NewsRepository.current(mine, mixed, now))
        val found = listOf(googleItem("g1", "Eine neue Meldung von heute früh", "x.de"), googleItem("g2", "Eine alte Meldung vom Januar", "x.de", at = "2026-01-10T10:00:00+01:00"))
        assertEquals(found, NewsRepository.current(gn, found, now))
    }

    @Test
    fun `one publisher's article from its own feed and from Google News shows once`() {
        val ntv = FeedSource("ntv-top", "n-tv", "https://www.n-tv.de/rss", "ki")
        val own = item("own", ntv, "2026-10-29T10:00:00+01:00", "Rekruten aus aller Welt: Russland lockt Ausländer mit Fake-Jobs in den Krieg", url = "https://www.n-tv.de/politik/rekruten-article1.html")
            .copy(publisherDomain = "n-tv.de")
        // Google News words it a little differently (no kicker) and dates it six minutes later.
        val google = googleItem("g", "Russland lockt Ausländer mit Fake-Jobs in den Krieg", "n-tv.de", at = "2026-10-29T10:06:00+01:00")
        assertEquals(listOf("own"), NewsRepository.dedupe(listOf(google, own)).map { it.id })
        assertEquals(listOf("own"), NewsRepository.dedupe(listOf(own, google)).map { it.id })
        // The survivor has no picture, its twin has one: it takes the twin's.
        val picture = ArticleImage("https://img.n-tv.de/rekruten.jpg")
        val survivor = NewsRepository.dedupe(listOf(own, google.copy(image = picture))).single()
        assertEquals("own", survivor.id)
        assertEquals(picture, survivor.image)
        // Another publisher's article with nearly those words is another article.
        val welt = googleItem("w", "Russland lockt Ausländer mit Fake-Jobs in den Krieg – so läuft es ab", "welt.de", at = "2026-10-29T10:06:00+01:00")
        assertEquals(listOf("own", "w"), NewsRepository.dedupe(listOf(own, welt)).map { it.id })
        // The same publisher two days later: a new article.
        val later = google.copy(id = "later", url = "https://news.google.com/rss/articles/CBMilater?oc=5", publishedAt = "2026-10-31T10:06:00+01:00")
        assertEquals(listOf("own", "later"), NewsRepository.dedupe(listOf(own, later)).map { it.id })
        // In a merged topic: one card, at the publisher's place.
        val merged = NewsRepository.merge("ki", null, listOf(ok(ntv, own), ok(gn, google)), now)
        assertEquals(listOf("own"), merged.items.map { it.id })
    }

    @Test
    fun `a page of older articles keeps to its days`() {
        val window = GoogleNews.DateWindow(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 23))
        val page = listOf(
            paged("in", "2026-10-21T08:00:00+02:00"),
            // Time zones and updated stories: two days of slack.
            paged("edge", "2026-10-18T12:00:00+02:00"),
            paged("stray", "2026-04-02T08:00:00+02:00"),
            paged("undated", null),
        )
        assertEquals(listOf("in", "edge", "undated"), NewsRepository.inWindow(page, window).map { it.id })
    }

    @Test
    fun `paged articles survive a refresh while their source belongs to the topic`() {
        // Yesterday's first page reached back to the 27th, paging went on from there.
        val previous = TopicNews(
            "ki", "2026-10-28T20:00:00+01:00",
            listOf(
                googleItem("first", "Die Meldung von gestern Abend", "a.de", at = "2026-10-28T19:00:00+01:00"),
                googleItem("bridge", "Die Meldung von vorgestern früh", "a.de", at = "2026-10-27T08:00:00+01:00"),
                paged("old", "2026-10-25T10:00:00+01:00"),
            ),
        )
        val fresh = ok(gn, googleItem("new", "Eine neue Meldung von heute früh", "a.de"), googleItem("first", "Die Meldung von gestern Abend", "a.de", at = "2026-10-28T19:00:00+01:00"))
        val kept = NewsRepository.merge("ki", previous, listOf(fresh), now)
        assertEquals(listOf("new", "first", "bridge", "old"), kept.items.map { it.id })
        // The day the fresh page no longer covers joins the paged ones.
        assertEquals(gn.id + NewsRepository.PAGE_SUFFIX, kept.items.single { it.id == "bridge" }.sourceId)
        val dropped = NewsRepository.merge("ki", previous, listOf(ok(a, item("x", a, "2026-10-29T10:00:00+01:00"))), now)
        assertEquals(listOf("x"), dropped.items.map { it.id })
        // Google News down: everything stays as it was.
        assertEquals(listOf("first", "bridge", "old"), NewsRepository.merge("ki", previous, listOf(failed(gn)), now).items.map { it.id })
    }

    @Test
    fun `paged articles that no longer reach the fresh first page go, so paging can fill the days in between`() {
        // Paged back to Nov 1 on Nov 5; the app is opened again on Nov 14.
        val previous = TopicNews(
            "ki", "2026-11-05T12:00:00+01:00",
            listOf(
                googleItem("nov5", "Meldung vom fünften November", "a.de", at = "2026-11-05T10:00:00+01:00"),
                googleItem("nov3", "Meldung vom dritten November", "a.de", at = "2026-11-03T10:00:00+01:00"),
                paged("nov2", "2026-11-02T10:00:00+01:00"),
                paged("nov1", "2026-11-01T10:00:00+01:00"),
            ),
        )
        val fresh = ok(
            gn,
            googleItem("nov14", "Meldung vom vierzehnten November", "a.de", at = "2026-11-14T10:00:00+01:00"),
            googleItem("nov12", "Meldung vom zwölften November", "a.de", at = "2026-11-12T10:00:00+01:00"),
        )
        val merged = NewsRepository.merge("ki", previous, listOf(fresh), Instant.parse("2026-11-14T12:00:00Z"))
        assertEquals(listOf("nov14", "nov12"), merged.items.map { it.id })
    }

    @Test
    fun `the bulk of a first page leaves stray old hits out`() {
        val november = (1..40).map { googleItem("n$it", "Meldung aus dem November Nummer $it", "a.de", at = "2026-11-0${1 + it % 5}T${10 + it % 10}:00:00+01:00") }
        val april = (1..5).map { googleItem("a$it", "Meldung aus dem April Nummer $it", "a.de", at = "2026-04-1${it}T10:00:00+02:00") }
        assertEquals(Instant.parse("2026-11-01T09:00:00Z"), NewsRepository.bulkOldest(november + april))
        // A quiet place: a story every week or so is its bulk, not a stray.
        val quiet = listOf("2026-11-05", "2026-10-29", "2026-10-20", "2026-10-14", "2026-10-03").map { googleItem(it, "Meldung aus dem Ort vom $it", "a.de", at = "${it}T10:00:00Z") }
        assertEquals(Instant.parse("2026-10-03T10:00:00Z"), NewsRepository.bulkOldest(quiet))
        assertEquals(null, NewsRepository.bulkOldest(listOf(googleItem("u", "Eine Meldung ohne Datum", "a.de", at = null))))
    }

    @Test
    fun `feeds the user added keep the articles of walled sites, and statuses count what shows`() {
        val kicker = FeedSource("custom:k1", "kicker", "https://newsfeed.kicker.de/news/aktuell", "custom:f", builtIn = false)
        val own = item("k", kicker, "2026-10-29T10:00:00+01:00", "Bundesliga: Das Spiel am Abend", url = "https://www.kicker.de/spiel")
        val merged = NewsRepository.merge(
            "custom:f", null,
            listOf(ok(kicker, own), ok(gn, googleItem("z", "Bei der Zeit hinter der Bezahlschranke", "zeit.de"), googleItem("t", "Bei der Tagesschau frei lesbar heute", "tagesschau.de"))),
            now,
            blocked = setOf("kicker.de", "zeit.de"),
        )
        assertEquals(setOf("k", "t"), merged.items.map { it.id }.toSet())
        assertEquals(listOf(1, 1), merged.sources.map { it.count })
        assertTrue(own.fromUserFeed)
    }

    @Test
    fun `cached matches are the newest over all topics`() {
        val older = TopicNews("a", "2026-10-29T08:00:00+01:00", (1..250).map { item("o$it", a, "2026-10-01T10:00:00+01:00", "Midterms: ältere Meldung Nummer $it") })
        val newer = TopicNews("b", "2026-10-29T08:00:00+01:00", listOf(item("n", b, "2026-10-29T10:00:00+01:00", "Midterms: die neueste Meldung")))
        val found = NewsRepository.matching(listOf(older, newer), "midterms")
        assertEquals(NewsRepository.MAX_CACHED_MATCHES, found.size)
        assertEquals("n", found.first().id)
    }

    @Test
    fun `a paged article belongs to its Google News source`() {
        assertEquals(gn.id, paged("p", "2026-10-25T10:00:00+01:00").feedSourceId)
        assertEquals("a", item("1", a, null).feedSourceId)
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
    }

    @Test
    fun `cached matches need every word whole, and one of an OR`() {
        val topic = TopicNews(
            "a", "2026-10-29T08:00:00+01:00",
            listOf(
                item("slv", a, "2026-10-29T10:00:00+01:00", "Silber: Zufluss in SLV-ETF"),
                item("etf", a, "2026-10-29T10:00:00+01:00", "Ein ETF für alle Fälle"),
                item("slvp", a, "2026-10-29T10:00:00+01:00", "SLVP ETF zieht an"),
                item("senat", a, "2026-10-29T09:00:00+01:00", "Der Senat nach der Wahl"),
            ),
        )
        // Both words, whole: "SLV" is not in "SLVP", "ETF" alone is not enough.
        assertEquals(listOf("slv"), NewsRepository.matching(listOf(topic), "SLV ETF").map { it.id })
        // "OR" wants one of them.
        assertEquals(setOf("slv", "etf", "slvp", "senat"), NewsRepository.matching(listOf(topic), "Senat OR ETF").map { it.id }.toSet())
        // Operators and excluded sites are for Google; the words still have to be there.
        assertEquals(listOf("senat"), NewsRepository.matching(listOf(topic), "Midterms OR Senat -site:x.com when:1d").map { it.id })
        assertEquals(listOf("etf"), NewsRepository.matching(listOf(topic), "ETF -Silber -SLVP").map { it.id })
    }
}

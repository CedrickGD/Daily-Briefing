package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsAge
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** The endless timeline behind "Neueste" and below the "Für dich" cards. */
class FeedTimelineTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))
    private val instant: Instant = now.toInstant()

    private fun hoursAgo(hours: Long): Instant = instant.minus(Duration.ofHours(hours))

    /** [kind]: "rss" a publisher's feed, "gn" the topic's Google News first page, "page" an older page of it. */
    private fun item(id: String, topic: String, hoursAgo: Long?, kind: String = "rss") = NewsItem(
        id = id,
        headline = "H$id",
        url = "https://example.org/$id",
        source = "S",
        sourceId = when (kind) {
            "gn" -> "gn:$topic@DE:de"
            "page" -> "gn:$topic@DE:de${NewsItem.PAGE_SUFFIX}"
            else -> "s"
        },
        topicId = topic,
        publishedAt = hoursAgo?.let { now.minusHours(it).toOffsetDateTime().toString() },
        aggregator = if (kind == "rss") null else "google",
    )

    private fun news(topic: String, vararg items: NewsItem) =
        topic to TopicNews(topic, now.toOffsetDateTime().toString(), items.toList(), listOf(SourceStatus("s", "S", ok = true)))

    private fun prefs(vararg followed: String, age: NewsAge = NewsAge.DAY, size: Int = 2, readableOnly: Boolean = false) = FeedPrefs(
        AppSettings(
            uiLanguage = AppLanguage.DE, edition = NewsEdition.GERMANY, followed = followed.toList(),
            sectionSize = size, newsAge = age, readableOnly = readableOnly,
        ),
    )

    /** "welt" reaches back two days, "sport" (paged before) more than a week. */
    private val repo = mapOf(
        news(
            "welt",
            item("w1", "welt", 1), item("w2", "welt", 5), item("w3", "welt", 30, "gn"), item("w4", "welt", 50, "gn"),
        ),
        news(
            "sport",
            item("s1", "sport", 2), item("s2", "sport", 40, "gn"), item("s3", "sport", 100, "gn"), item("s4", "sport", 200, "page"),
        ),
    )

    @Test
    fun `a topic's list is complete back to its oldest page, else to the bulk of its first page`() {
        val welt = repo.getValue("welt").items
        assertEquals(hoursAgo(50), pagedDepth(welt, instant))
        assertEquals(hoursAgo(200), pagedDepth(repo.getValue("sport").items, instant))
        // A stray old hit on the first page is not how far the list reaches.
        val stray = welt + item("old", "welt", 24L * 200, "gn")
        assertEquals(hoursAgo(50), pagedDepth(stray, instant))
        // Only publisher feeds so far: nothing older is known to be complete.
        assertEquals(instant, pagedDepth(listOf(item("r1", "welt", 3), item("r2", "welt", 90)), instant))
        assertNull(pagedDepth(emptyList(), instant))
        assertNull(pagedDepth(listOf(item("u", "welt", null)), instant))
    }

    @Test
    fun `the age setting is no dead end - the timeline goes on with everything older`() {
        val feed = buildNewsFeed(repo, prefs("welt", "sport"), now)
        // "Neueste" as the age setting shapes it (a quiet topic keeps its three newest) …
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "s3"), feed.latest.map { it.item.id })
        // … and the endless list behind the tab: everything, newest first.
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w4", "s3", "s4"), feed.timeline.map { it.item.id })
        assertEquals(8, feed.timelineCandidates)
        // Nothing can page: nothing to ask for.
        assertTrue(feed.olderNext.isEmpty())
    }

    @Test
    fun `stories older than the shallowest topic's reach wait, so older pages only append`() {
        val prefs = prefs("welt", "sport")
        val feed = buildNewsFeed(repo, prefs, now, open = setOf("welt", "sport"))
        // "welt" is read back 50 hours: what "sport" holds beyond that would end up above its next page.
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w4"), feed.timeline.map { it.item.id })
        assertEquals(listOf("welt"), feed.olderNext)

        // "welt" paged three more days: its stories land below everything shown, and the list moves on.
        val paged = repo + news(
            "welt",
            *repo.getValue("welt").items.toTypedArray(), item("w5", "welt", 70, "page"), item("w6", "welt", 120, "page"),
        )
        val next = buildNewsFeed(paged, prefs, now, open = setOf("welt", "sport"))
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w4", "w5", "s3", "w6"), next.timeline.map { it.item.id })
        assertEquals(feed.timeline.map { it.item.id }, next.timeline.map { it.item.id }.take(feed.timeline.size))
        // Now "welt" reaches back 120 hours, "sport" 200: "welt" is still the one to page.
        assertEquals(listOf("welt"), next.olderNext)

        // "welt" at its end: only "sport" holds the list back, and everything it has shows.
        val ended = buildNewsFeed(paged, prefs, now, open = setOf("sport"))
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w4", "w5", "s3", "w6", "s4"), ended.timeline.map { it.item.id })
        assertEquals(listOf("sport"), ended.olderNext)
    }

    @Test
    fun `the last day always shows, also when a topic has not been read back at all`() {
        // "welt" has only its publisher feeds: complete up to now, it holds everything older back.
        val shallow = mapOf(
            news("welt", item("w1", "welt", 1), item("w2", "welt", 30)),
            news("sport", item("s1", "sport", 2), item("s2", "sport", 40, "gn"), item("u", "sport", null)),
        )
        val feed = buildNewsFeed(shallow, prefs("welt", "sport"), now, open = setOf("welt", "sport"))
        // Undated stories count as a day old: they show, after the dated ones of the day.
        assertEquals(listOf("w1", "s1", "u"), feed.timeline.map { it.item.id })
        assertEquals("welt", feed.olderNext.first())
    }

    @Test
    fun `topics that end within a day of the shallowest are paged with it, a few at a time`() {
        val depths = linkedMapOf(
            "a" to hoursAgo(50), "b" to hoursAgo(40), "c" to hoursAgo(60), "d" to hoursAgo(300),
            "e" to hoursAgo(41), "f" to hoursAgo(45), "g" to hoursAgo(64),
        )
        assertEquals(listOf("b", "e", "f", "a"), nextToPage(depths))
        assertEquals(listOf("b", "e"), nextToPage(depths, max = 2))
        assertEquals(listOf("d"), nextToPage(mapOf("d" to hoursAgo(300))))
        assertTrue(nextToPage(emptyMap()).isEmpty())
    }

    @Test
    fun `Fuer dich goes on below its cards with everything they do not show`() {
        val feed = buildNewsFeed(repo, prefs("welt", "sport", size = 2), now)
        assertEquals(listOf("w1", "w2"), feed.sections[0].top.map { it.id })
        assertEquals(listOf("s1", "s2"), feed.sections[1].top.map { it.id })
        assertEquals(listOf("w3", "w4", "s3", "s4"), feed.more.map { it.item.id })
        assertEquals(listOf("welt", "welt", "sport", "sport"), feed.more.map { it.topic.id })
    }

    @Test
    fun `a story of several topics shows once, under its most specific one`() {
        val shared = item("x", "politik/inland", 30)
        val both = mapOf(news("politik", shared, item("p1", "politik", 1)), news("politik/inland", shared, item("i1", "politik/inland", 2)))
        val feed = buildNewsFeed(both, prefs("politik", "politik/inland"), now)
        assertEquals(listOf("p1", "i1", "x"), feed.timeline.map { it.item.id })
        assertEquals("politik/inland", feed.timeline.last().topic.id)
    }

    @Test
    fun `with nur vollstaendige Artikel the timeline shows verified stories and names the next to check`() {
        val verdicts = mapOf("w1" to ArticleStatus.READABLE, "s1" to ArticleStatus.READABLE, "w2" to ArticleStatus.PAYWALL, "w3" to ArticleStatus.READABLE)
        val feed = buildNewsFeed(repo, prefs("welt", "sport", readableOnly = true), now, verification = Verification(true, verdicts))
        // w2 is a teaser (never shows), s2 is not judged yet: the list stops there.
        assertEquals(listOf("w1", "s1", "w3"), feed.timeline.map { it.item.id })
        assertEquals(listOf("s2", "w4", "s3", "s4"), feed.timelineWaiting.map { it.id })
        // What a list showed stays in it until the next build.
        val kept = keptAfter(emptyMap(), feed)
        assertEquals(setOf("w1", "s1", "w3"), kept[TIMELINE_LIST])
    }
}

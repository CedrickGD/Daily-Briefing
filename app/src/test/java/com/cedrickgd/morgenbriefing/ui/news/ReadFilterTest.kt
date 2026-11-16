package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.local.HistoryEntry
import com.cedrickgd.morgenbriefing.data.local.ReadHistory
import com.cedrickgd.morgenbriefing.data.local.StoryIndex
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsAge
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReadFilterTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))

    private fun item(id: String, topic: String, hoursAgo: Long, headline: String = "H$id", domain: String = "") = NewsItem(
        id = id, headline = headline, url = "https://example.org/$id", source = "S", sourceId = "s", topicId = topic,
        publishedAt = now.minusHours(hoursAgo).toOffsetDateTime().toString(), publisherDomain = domain,
    )

    private fun news(topic: String, vararg items: NewsItem) =
        topic to TopicNews(topic, now.toOffsetDateTime().toString(), items.toList(), listOf(SourceStatus("s", "S", ok = true)))

    private fun prefs(hideRead: Boolean) = FeedPrefs(
        AppSettings(
            uiLanguage = AppLanguage.DE,
            edition = NewsEdition.GERMANY,
            followed = listOf("politik", "wirtschaft"),
            sectionSize = 3,
            newsAge = NewsAge.DAY,
            readableOnly = false,
            hideRead = hideRead,
        ),
    )

    private val p1 = item("p1", "politik", 1)
    private val p2 = item("p2", "politik", 2)
    private val p3 = item("p3", "politik", 3)
    private val p4 = item("p4", "politik", 4)
    private val w1 = item("w1", "wirtschaft", 1)
    private val w2 = item("w2", "wirtschaft", 5)
    private val repo = mapOf(news("politik", p1, p2, p3, p4), news("wirtschaft", w1, w2))

    private fun read(vararg items: NewsItem, at: Long = 1L) = items.map { HistoryEntry.of(it).copy(readAt = at) }
    private fun hidden(vararg items: NewsItem) = items.map { HistoryEntry.of(it).copy(hiddenAt = 1L) }

    /** Every story id a list of the feed shows. */
    private fun NewsFeed.everywhere(): Set<String> =
        (sections.flatMap { s -> s.all.map { it.id } + s.top.map { it.id } } + latest.map { it.item.id } + timeline.map { it.item.id } + more.map { it.item.id }).toSet()

    @Test
    fun `a hidden story leaves every list at once, whatever the setting says`() {
        val history = ReadHistory(hidden(p2))
        for (hideRead in listOf(false, true)) {
            val feed = buildNewsFeed(repo, prefs(hideRead), now, reads = ReadFilter(history.hidden, StoryIndex.EMPTY))
            assertFalse("p2 hidden (hideRead=$hideRead)", "p2" in feed.everywhere())
            assertEquals(listOf("p1", "p3", "p4"), feed.sections[0].all.map { it.id })
            assertEquals(1, feed.sections[0].heldBack)
            assertEquals(1, feed.timelineHeldBack)
        }
    }

    @Test
    fun `read stories stay - calmer - while the setting is off`() {
        val history = ReadHistory(read(p1, w1))
        val feed = buildNewsFeed(repo, prefs(hideRead = false), now, reads = ReadFilter(history.hidden, history.read))
        assertTrue(setOf("p1", "w1").all { it in feed.everywhere() })
        assertEquals(0, feed.sections[0].heldBack)
    }

    @Test
    fun `with the setting on, the stories read at the last build leave every list`() {
        val history = ReadHistory(read(p1, w1))
        val feed = buildNewsFeed(repo, prefs(hideRead = true), now, reads = ReadFilter(history.hidden, history.read))
        assertFalse("p1" in feed.everywhere())
        assertFalse("w1" in feed.everywhere())
        assertEquals(listOf("p2", "p3", "p4"), feed.sections[0].top.map { it.id })
        assertEquals(listOf("w2"), feed.sections[1].all.map { it.id })
        // They still count for paging: a list of read stories pages on.
        assertEquals(1, feed.sections[0].heldBack)
        assertEquals(1, feed.sections[1].heldBack)
        assertEquals(2, feed.timelineHeldBack)
    }

    @Test
    fun `a story read after the build shows until the lists are built anew`() {
        // The lists were built when p1 was read; then the user reads p2 (closing the reader).
        val atBuild = ReadHistory(read(p1)).read
        val now1 = ReadHistory(read(p1) + read(p2, at = 2L))
        val sameBuild = buildNewsFeed(repo, prefs(hideRead = true), now, reads = ReadFilter(now1.hidden, atBuild))
        assertTrue("p2 stays under the finger", "p2" in sameBuild.everywhere())
        assertFalse("p1" in sameBuild.everywhere())
        // A refresh / another tab takes a new snapshot: now p2 leaves too.
        val rebuilt = buildNewsFeed(repo, prefs(hideRead = true), now, reads = ReadFilter(now1.hidden, now1.read))
        assertFalse("p2" in rebuilt.everywhere())
    }

    @Test
    fun `the same story from another feed is left out too`() {
        val original = item("orig", "politik", 1, headline = "Koalition einigt sich auf den Haushalt für 2027", domain = "tagesschau.de")
        val again = item("other", "wirtschaft", 1, headline = "KOALITION einigt sich auf den Haushalt für 2027!", domain = "zeit.de")
        val news = mapOf(news("politik", p2, p3), news("wirtschaft", again, w2))
        val history = ReadHistory(read(original))
        val feed = buildNewsFeed(news, prefs(hideRead = true), now, reads = ReadFilter(history.hidden, history.read))
        assertFalse("other" in feed.everywhere())
    }

    @Test
    fun `a story shown again comes back where it was, not at the end`() {
        val hide = ReadFilter(ReadHistory(hidden(p2)).hidden)
        val first = buildFeed(repo, prefs(hideRead = false), now, reads = hide)
        assertEquals(listOf("p1", "p3", "p4"), first.feed.sections[0].all.map { it.id })
        // "Wieder einblenden": the next build (same fetch) goes on from the order of the first.
        val back = buildFeed(repo, prefs(hideRead = false), now, order = first.order, reads = ReadFilter.NONE)
        assertEquals(listOf("p1", "p2", "p3", "p4"), back.feed.sections[0].all.map { it.id })
        assertEquals(listOf("p1", "w1", "p2", "p3", "p4", "w2"), back.feed.timeline.map { it.item.id })
    }

    @Test
    fun `the filter keeps everything when nothing is held`() {
        assertTrue(ReadFilter.NONE.isEmpty)
        assertTrue(ReadFilter.NONE.keeps(p1, hideRead = true))
        val history = ReadHistory(read(p1) + hidden(p2))
        val filter = ReadFilter(history.hidden, history.read)
        assertTrue(filter.keeps(p1, hideRead = false))
        assertFalse(filter.keeps(p1, hideRead = true))
        assertFalse(filter.keeps(p2, hideRead = false))
        assertTrue(filter.keeps(p3, hideRead = true))
        // The same stories make an equal filter: no rebuild for nothing.
        assertEquals(filter, ReadFilter(ReadHistory(hidden(p2)).hidden, ReadHistory(read(p1, at = 9L)).read))
    }
}

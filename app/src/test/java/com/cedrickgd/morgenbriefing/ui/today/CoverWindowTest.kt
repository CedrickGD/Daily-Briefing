package com.cedrickgd.morgenbriefing.ui.today

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.ui.news.FeedFilter
import com.cedrickgd.morgenbriefing.ui.news.FeedPrefs
import com.cedrickgd.morgenbriefing.ui.news.Verification
import com.cedrickgd.morgenbriefing.ui.news.buildNewsFeed
import com.cedrickgd.morgenbriefing.ui.news.entryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Whose covers Today looks up and loads ahead of the list, and what the endless views warm. */
class CoverWindowTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))

    private fun item(id: String, topic: String, minutesAgo: Long) = NewsItem(
        id = id, headline = "H$id", url = "https://example.org/$id", source = "S", sourceId = "s", topicId = topic,
        publishedAt = now.minusMinutes(minutesAgo).toOffsetDateTime().toString(),
    )

    private fun news(topic: String, prefix: String, count: Int, offset: Long) =
        topic to TopicNews(topic, now.toOffsetDateTime().toString(), (1..count).map { item("$prefix$it", topic, offset + it * 2) }, listOf(SourceStatus("s", "S", ok = true)))

    private fun prefs(readableOnly: Boolean = false) = FeedPrefs(
        AppSettings(uiLanguage = AppLanguage.DE, edition = NewsEdition.GERMANY, followed = listOf("welt", "sport"), sectionSize = 2, readableOnly = readableOnly),
    )

    /** "welt": w1 … w60 (the newer ones), "sport": s1 … s5. */
    private val repo = mapOf(news("welt", "w", 60, offset = 0), news("sport", "s", 5, offset = 1))
    private val feed = buildNewsFeed(repo, prefs(), now)

    @Test
    fun `the stories of a view come with the next page, the first of a card or topic large`() {
        val topic = viewStories(FeedFilter.OneTopic("welt"), feed, revealed = 20)
        assertEquals((1..40).map { "w$it" }, topic.map { it.item.id })
        assertEquals(listOf(true) + List(39) { false }, topic.map { it.lead })

        val latest = viewStories(FeedFilter.Latest, feed, revealed = 20)
        assertEquals(40, latest.size)
        assertTrue(latest.none { it.lead })
        assertEquals(listOf("w1", "s1", "w2", "s2"), latest.take(4).map { it.item.id })

        // "Für dich": the cards (the first story of each large), then the list below them.
        val forYou = viewStories(FeedFilter.ForYou, feed, revealed = 0, upcoming = 3)
        assertEquals(listOf("w1", "w2", "s1", "s2", "w3", "s3", "w4"), forYou.map { it.item.id })
        assertEquals(listOf(true, false, true, false, false, false, false), forYou.map { it.lead })
    }

    @Test
    fun `covers are wanted for what is on screen and a page below it`() {
        val stories = viewStories(FeedFilter.OneTopic("welt"), feed, revealed = 20)
        val keys = listOf("today-bar", entryKey("topic-welt", 0, "w3"), entryKey("topic-welt", 0, "w4"))
        val window = coverWindow(stories, keys, ahead = 5)
        assertEquals(listOf("w3", "w4", "w5", "w6", "w7", "w8", "w9"), window.map { it.item.id })
        // The list never asks beyond what the next page reveals.
        val end = coverWindow(stories, listOf(entryKey("topic-welt", 0, "w39")), ahead = COVER_LOOKUP_AHEAD)
        assertEquals(listOf("w39", "w40"), end.map { it.item.id })
    }

    @Test
    fun `before the feed scrolls in the first stories are wanted, so their covers are there in time`() {
        val stories = viewStories(FeedFilter.ForYou, feed, revealed = 20)
        val window = coverWindow(stories, listOf("header", "weather", "today-bar"), ahead = 3)
        assertEquals(listOf("w1", "w2", "s1"), window.map { it.item.id })
        assertEquals(listOf(true, false, true), window.map { it.lead })
        assertTrue(coverWindow(emptyList(), listOf("header"), ahead = 3).isEmpty())
    }

    @Test
    fun `a story shown twice is wanted once, large if it is large anywhere`() {
        val a = item("a", "welt", 1)
        val b = item("b", "welt", 2)
        val stories = listOf(CoverTarget(a, lead = false), CoverTarget(a, lead = true), CoverTarget(b, lead = false))
        val window = coverWindow(stories, listOf(entryKey("x", 0, "a")), ahead = 4)
        assertEquals(listOf("a", "b"), window.map { it.item.id })
        assertEquals(listOf(true, false), window.map { it.lead })
    }

    @Test
    fun `the list below the cards is warmed and, with nur vollstaendige Artikel, verified once it is near`() {
        // Warming (setting off): what "Für dich" shows includes the revealed part of the list below the cards.
        val shown = viewItems(FeedFilter.ForYou, feed, revealed = 2)
        assertEquals(listOf("w1", "w2", "s1", "s2", "w3", "s3"), shown.map { it.id })
        assertEquals(listOf("w1", "s1", "w2"), viewItems(FeedFilter.Latest, feed, revealed = 3).map { it.id })

        val verdicts = (listOf("w1", "w2", "s1", "s2", "w3")).associateWith { ArticleStatus.READABLE }
        val verifying = buildNewsFeed(repo, prefs(readableOnly = true), now, verification = Verification(true, verdicts))
        assertEquals(listOf("w3"), verifying.more.map { it.item.id })
        // The first card on screen, both cards full: nothing to verify, the list below is far away.
        val top = warmWindow(FeedFilter.ForYou, verifying, revealed = 20, visibleKeys = listOf("foryou-s0-welt-header"), sectionSize = 2)
        assertTrue(top.isEmpty())
        // The last card on screen: the list below it gets its next candidates, in its own order.
        val near = warmWindow(FeedFilter.ForYou, verifying, revealed = 20, visibleKeys = listOf("foryou-s1-sport-header"), sectionSize = 2)
        assertEquals(listOf("s3", "w4", "s4"), near.take(3).map { it.id })
        // Scrolled into the list: the same, counted from the story on screen.
        val inside = warmWindow(FeedFilter.ForYou, verifying, revealed = 20, visibleKeys = listOf(entryKey("foryou-more", 0, "w3")), sectionSize = 2)
        assertEquals("s3", inside.first().id)
    }
}

package com.cedrickgd.morgenbriefing.ui.today

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import com.cedrickgd.morgenbriefing.ui.news.FeedFilter
import com.cedrickgd.morgenbriefing.ui.news.FeedPrefs
import com.cedrickgd.morgenbriefing.ui.news.VERIFY_AHEAD
import com.cedrickgd.morgenbriefing.ui.news.Verification
import com.cedrickgd.morgenbriefing.ui.news.buildFeed
import com.cedrickgd.morgenbriefing.ui.news.entryKey
import com.cedrickgd.morgenbriefing.ui.news.keptAfter
import com.cedrickgd.morgenbriefing.ui.news.keptForBuild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** A refresh like Google News: new stories at the top, checked first, counted. */
class FreshStoriesTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 16, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))

    private fun item(id: String, minutesAgo: Long, paged: Boolean = false) = NewsItem(
        id = id, headline = "Meldung $id", url = "https://example.org/$id", source = "S",
        sourceId = if (paged) "s${NewsItem.PAGE_SUFFIX}" else "s", topicId = "welt",
        publishedAt = now.minusMinutes(minutesAgo).toOffsetDateTime().toString(),
    )

    private fun news(fetchedAt: ZonedDateTime, items: List<NewsItem>) =
        mapOf("welt" to TopicNews("welt", fetchedAt.toOffsetDateTime().toString(), items, listOf(SourceStatus("s", "S", ok = true))))

    private fun prefs(readableOnly: Boolean, size: Int = 4) = FeedPrefs(
        AppSettings(uiLanguage = AppLanguage.DE, edition = NewsEdition.GERMANY, followed = listOf("welt"), sectionSize = size, readableOnly = readableOnly),
    )

    /** 40 stories of the morning, w1 the newest. */
    private val morning = (1..40).map { item("w$it", minutesAgo = 60L + it) }
    private val fetch1 = now.minusMinutes(30)
    private val fetch2 = now

    @Test
    fun `a refresh brings the stories that were not there before - not the older pages of the endless lists`() {
        val before = storyIds(news(fetch1, morning))
        val after = news(fetch2, listOf(item("new1", 5), item("new2", 10)) + morning + item("old", 4_000, paged = true))
        assertEquals(setOf("new1", "new2"), freshIds(before, after))
        assertTrue("first start: everything would be new, nothing is announced", freshIds(emptySet(), after).isEmpty())
    }

    @Test
    fun `older pages only append, a refresh sorts afresh with the new stories on top`() {
        val first = buildFeed(news(fetch1, morning), prefs(readableOnly = false), now)
        // An older page arrives (same fetch): appended, nothing above moves.
        val paged = buildFeed(news(fetch1, morning + item("older", 3_000, paged = true)), prefs(readableOnly = false), now, order = first.order)
        assertEquals("older", paged.feed.sections.single().all.last().id)
        // A refresh (new fetch; the repository keeps every topic newest first): the new stories stand at the top.
        val refreshed = buildFeed(
            news(fetch2, listOf(item("new1", 5), item("new2", 10)) + morning),
            prefs(readableOnly = false), now, order = paged.order,
        )
        assertEquals(listOf("new1", "new2", "w1"), refreshed.feed.sections.single().all.take(3).map { it.id })
        assertEquals(listOf("new1", "new2", "w1"), refreshed.feed.timeline.take(3).map { it.item.id })
        assertEquals(2, freshShown(FeedFilter.Latest, refreshed.feed, setOf("new1", "new2")))
    }

    @Test
    fun `with only complete articles the new stories are checked first and then show at the top`() {
        // Before the refresh: every story of the morning verified and on screen once.
        val verdicts = morning.associate { it.id to ArticleStatus.READABLE }
        val before = buildFeed(news(fetch1, morning), prefs(readableOnly = true), now, verification = Verification(true, verdicts))
        var kept = keptAfter(emptyMap(), before.feed)
        // The refresh: two new stories, not checked yet.
        val refreshedNews = news(fetch2, listOf(item("new1", 5), item("new2", 10)) + morning)
        kept = keptForBuild(kept, verdicts)
        val pending = buildFeed(refreshedNews, prefs(readableOnly = true), now, verification = Verification(true, verdicts, kept), order = before.order).feed
        assertFalse("not shown before their check", "new1" in pending.sections.single().all.map { it.id })
        // The user sits at the top: 40 verified stories lie below, so the usual window checks nothing –
        // the new stories at the top were never checked and never showed. The fresh ones go first.
        val keys = listOf(entryKey("topic-welt", 0, "w1"), entryKey("topic-welt", 1, "w2"))
        val usual = warmWindow(FeedFilter.OneTopic("welt"), pending, revealed = 20, visibleKeys = keys, sectionSize = 4)
        assertTrue(usual.isEmpty())
        val fresh = setOf("new1", "new2")
        val window = warmWindow(FeedFilter.OneTopic("welt"), pending, revealed = 20, visibleKeys = keys, sectionSize = 4, fresh = fresh)
        assertEquals(listOf("new1", "new2"), window.map { it.id })
        // "Für dich" too, although its card is full.
        val forYou = warmWindow(FeedFilter.ForYou, pending, revealed = 20, visibleKeys = listOf("foryou-s0-welt-header"), sectionSize = 4, fresh = fresh)
        assertEquals(listOf("new1", "new2"), forYou.take(2).map { it.id })
        assertTrue(window.size <= VERIFY_AHEAD)
        // Checked: they take their place at the top – of the topic, of "Neueste" and of the "Für dich" card.
        val checked = verdicts + mapOf("new1" to ArticleStatus.READABLE, "new2" to ArticleStatus.READABLE)
        val shown = buildFeed(refreshedNews, prefs(readableOnly = true), now, verification = Verification(true, checked, kept), order = before.order).feed
        assertEquals(listOf("new1", "new2", "w1"), shown.sections.single().all.take(3).map { it.id })
        assertEquals(listOf("new1", "new2"), shown.sections.single().top.take(2).map { it.id })
        assertEquals(listOf("new1", "new2", "w1"), shown.timeline.take(3).map { it.item.id })
        assertTrue(freshWaiting(shown, fresh).isEmpty())
        assertEquals(2, freshShown(FeedFilter.ForYou, shown, fresh))
        assertEquals(2, freshShown(FeedFilter.OneTopic("welt"), shown, fresh))
    }

    @Test
    fun `the note counts in every language`() {
        for (lang in AppLanguage.entries) {
            val s = FeedStrings.of(lang)
            assertTrue(s.newStories(12).contains("12"))
            assertTrue(s.newStories(1).contains("1"))
            assertTrue(s.noNewStories.isNotBlank())
        }
        assertEquals("12 neue Meldungen", FeedStrings.of(AppLanguage.DE).newStories(12))
        assertEquals("1 neue Meldung", FeedStrings.of(AppLanguage.DE).newStories(1))
    }

    @Test
    fun `a fresh round is announced once`() {
        val round = FreshRound(3, setOf("a"), byUser = true)
        // Not handled yet: the page announces it.
        assertEquals(round, freshToHandle(round, NO_ROUND))
        // Back from the reader the page is rebuilt with the round it handled: no second jump to the top.
        assertEquals(null, freshToHandle(round, handled = 3))
        assertEquals(null, freshToHandle(null, NO_ROUND))
        // The next refresh is a new round.
        assertEquals(4, freshToHandle(round.copy(round = 4), handled = 3)?.round)

        // The view model forgets a round the page is done with, never a newer one.
        val state = TodayUiState(now = now, fresh = round)
        assertEquals(null, state.consumed(3).fresh)
        assertEquals(round, state.consumed(2).fresh)
        assertEquals(null, TodayUiState(now = now).consumed(3).fresh)
    }
}

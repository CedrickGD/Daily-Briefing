package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus.BLOCKED
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus.FAILED
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus.LOADING
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus.PAYWALL
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus.READABLE
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** "Nur vollständige Artikel" in the lists: only verified articles show, appended in order. */
class VerifiedListsTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))

    private fun item(id: String, topic: String = "welt", hoursAgo: Long = 1) = NewsItem(
        id = id, headline = "H$id", url = "https://example.org/$id", source = "S", sourceId = "s", topicId = topic,
        publishedAt = now.minusHours(hoursAgo).toOffsetDateTime().toString(),
    )

    private fun items(vararg ids: String) = ids.map { item(it) }

    private fun verification(vararg statuses: Pair<String, ArticleStatus>, kept: Map<String, Set<String>> = emptyMap()) =
        Verification(enabled = true, statuses = statuses.toMap(), kept = kept)

    private fun ids(list: List<NewsItem>) = list.map { it.id }

    @Test
    fun `only verified articles show, rejected ones never, off shows everything`() {
        val list = items("a", "b", "c", "d")
        val v = verification("a" to READABLE, "b" to PAYWALL, "c" to READABLE, "d" to BLOCKED)
        val checked = verifiedList(list, "welt", v, NewsItem::id)
        assertEquals(listOf("a", "c"), ids(checked.shown))
        assertTrue(checked.waiting.isEmpty())
        assertEquals(list, verifiedList(list, "welt", Verification.OFF, NewsItem::id).shown)
        // A network failure is no verified article either (it is tried again later).
        assertEquals(listOf("a"), ids(verifiedList(items("a", "x"), "welt", verification("a" to READABLE, "x" to FAILED), NewsItem::id).shown))
    }

    @Test
    fun `the first pending candidate stops the list - articles are only ever appended at the end`() {
        val list = items("a", "b", "c", "d", "e")
        // c verified before b: it waits until b is judged, so nothing is inserted above it later.
        val step1 = verifiedList(list, "welt", verification("a" to READABLE, "b" to LOADING, "c" to READABLE), NewsItem::id)
        assertEquals(listOf("a"), ids(step1.shown))
        assertEquals(listOf("b", "d", "e"), ids(step1.waiting))
        val step2 = verifiedList(list, "welt", verification("a" to READABLE, "b" to PAYWALL, "c" to READABLE), NewsItem::id)
        assertEquals(listOf("a", "c"), ids(step2.shown))
        assertEquals(listOf("d", "e"), ids(step2.waiting))
        // Every step's list starts with the one before: appended, never reordered.
        assertEquals(ids(step1.shown), ids(step2.shown).take(step1.shown.size))
    }

    @Test
    fun `what a list showed stays until the next build, new stories on top do not stop it`() {
        val kept = mapOf("welt" to setOf("a", "b"))
        // a was shown, then the reader failed on it: it stays (removals wait for the next build).
        val after = verifiedList(items("a", "b", "c"), "welt", verification("a" to FAILED, "b" to READABLE, "c" to READABLE, kept = kept), NewsItem::id)
        assertEquals(listOf("a", "b", "c"), ids(after.shown))
        // A refresh brought n1 and n2 on top, not judged yet: the kept articles still show (no empty list).
        val fresh = verifiedList(items("n1", "n2", "a", "b", "c"), "welt", verification("b" to READABLE, "c" to READABLE, kept = kept), NewsItem::id)
        assertEquals(listOf("a", "b", "c"), ids(fresh.shown))
        assertEquals(listOf("n1", "n2"), ids(fresh.waiting))
        // Other lists keep their own: "Neueste" did not show a.
        assertEquals(listOf("b"), ids(verifiedList(items("b", "a"), LATEST_LIST, verification("a" to FAILED, "b" to READABLE, kept = kept), NewsItem::id).shown))

        // A new build: only what is still verified stays kept.
        val next = keptForBuild(kept, mapOf("a" to FAILED, "b" to READABLE))
        assertEquals(mapOf("welt" to setOf("b")), next)
    }

    @Test
    fun `an endless list verifies the next candidates only while few verified ones lie ahead`() {
        val waiting = (1..40).map { item("w$it") }
        val shown = (1..10).map { "s$it" }
        // On screen: s2..s4 – six verified below, fourteen missing: those plus a few spare.
        assertEquals((1..17).map { "w$it" }, ids(verifyAhead(shown, listOf("s2", "s3", "s4"), waiting)))
        // Twenty and more ready below the screen: nothing more is verified (no data spent on what nobody scrolls to).
        assertTrue(verifyAhead((1..30).map { "s$it" }, listOf("s2", "s3"), waiting).isEmpty())
        // An empty list (only its intro on screen): the first twenty.
        assertEquals((1..VERIFY_AHEAD).map { "w$it" }, ids(verifyAhead(emptyList(), emptyList(), waiting)))
        assertTrue(verifyAhead(shown, listOf("s10"), emptyList()).isEmpty())
    }

    private fun settings(followed: List<String>, size: Int = 2) = AppSettings(
        uiLanguage = AppLanguage.DE, edition = NewsEdition.GERMANY, followed = followed, sectionSize = size, readableOnly = true,
    )

    private fun news(topic: String, vararg items: NewsItem) =
        topic to TopicNews(topic, now.toOffsetDateTime().toString(), items.toList(), listOf(SourceStatus("s", "S", ok = true)))

    @Test
    fun `section sizes count verified articles, the rest is checked in order`() {
        val prefs = FeedPrefs(settings(listOf("welt", "sport"), size = 2))
        val repo = mapOf(
            news("welt", item("w1"), item("w2"), item("w3"), item("w4")),
            news("sport", item("s1", "sport"), item("s2", "sport"), item("s3", "sport")),
        )
        val v = verification("w1" to PAYWALL, "w2" to READABLE, "w3" to READABLE, "s1" to READABLE)
        val feed = buildNewsFeed(repo, prefs, now, verification = v)
        val welt = feed.sectionFor("welt")!!
        assertEquals(listOf("w2", "w3"), ids(welt.top))
        assertEquals(2, welt.count)
        assertFalse("full", welt.checking)
        assertEquals(listOf("w4"), ids(welt.waiting))
        val sport = feed.sectionFor("sport")!!
        assertEquals(listOf("s1"), ids(sport.top))
        assertTrue(sport.checking)
        assertEquals(listOf("s2", "s3"), ids(sport.topWaiting))
        assertEquals(3, sport.candidates)
        // "Neueste" holds only verified articles too, in its own order: s1 waits until w4 before it is judged.
        assertEquals(listOf("w2", "w3"), feed.latest.map { it.item.id })
        assertEquals(listOf("w4", "s2", "s3"), ids(feed.latestWaiting))
        assertEquals(7, feed.latestCandidates)
        assertTrue(feed.verifying)
        assertTrue(feed.checking)
        // Without the setting the feed is what it always was.
        val off = buildNewsFeed(repo, FeedPrefs(settings(listOf("welt", "sport")).copy(readableOnly = false)), now, verification = v)
        assertEquals(listOf("w1", "w2"), ids(off.sectionFor("welt")!!.top))
        assertFalse(off.verifying)
        assertTrue(off.sectionFor("welt")!!.waiting.isEmpty())
    }

    @Test
    fun `a topic whose candidates are all incomplete says so`() {
        val prefs = FeedPrefs(settings(listOf("welt")))
        val repo = mapOf(news("welt", item("w1"), item("w2")))
        val feed = buildNewsFeed(repo, prefs, now, verification = verification("w1" to PAYWALL, "w2" to BLOCKED))
        val welt = feed.sectionFor("welt")!!
        assertTrue(welt.isEmpty)
        assertTrue(welt.noneComplete)
        assertTrue(feed.noneComplete)
        assertFalse(feed.checking)
    }

    @Test
    fun `fur dich verifies the sections from the one on screen on, then the ones above`() {
        val prefs = FeedPrefs(settings(listOf("welt", "sport", "politik"), size = 2))
        val repo = mapOf(
            news("welt", item("w1"), item("w2"), item("w3")),
            news("sport", item("s1", "sport"), item("s2", "sport"), item("s3", "sport")),
            news("politik", item("p1", "politik"), item("p2", "politik"), item("p3", "politik")),
        )
        val feed = buildNewsFeed(repo, prefs, now, verification = verification("s1" to READABLE))
        // The screen shows "sport" (section 1): its one missing article (+1 spare) first, then politik, then welt.
        assertEquals(listOf("s2", "s3", "p1", "p2", "p3", "w1", "w2", "w3"), ids(forYouVerifyWindow(feed.sections, 2, firstVisible = 1)))
        assertEquals(listOf("s2", "s3", "p1"), ids(forYouVerifyWindow(feed.sections, 2, firstVisible = 1, cap = 3)))
        // The section index comes from the list keys.
        assertEquals(1, forYouSectionOf("foryou-s1-sport-header"))
        assertEquals(2, forYouSectionOf(entryKey("foryou-s2-politik", 0, "p1")))
        assertNull(forYouSectionOf("latest-intro"))
    }

    @Test
    fun `every list keeps what it shows`() {
        val prefs = FeedPrefs(settings(listOf("welt")))
        val repo = mapOf(news("welt", item("w1"), item("w2")))
        val feed = buildNewsFeed(repo, prefs, now, verification = verification("w1" to READABLE))
        val kept = keptAfter(emptyMap(), feed)
        assertEquals(setOf("w1"), kept["welt"])
        assertEquals(setOf("w1"), kept[LATEST_LIST])
        assertEquals(kept, keptAfter(kept, feed))
    }

    @Test
    fun `endless lists ask for older articles when the verified count runs low`() {
        // All 12 verified articles on screen, 30 candidates still to check: verify first, no paging yet.
        assertEquals(EndAction.NONE, endAction(12, 12, canLoadMore = true, loadingMore = false, stalled = false, waiting = 30, candidates = 50))
        // Few candidates left: older ones are fetched while those are checked.
        assertEquals(EndAction.LOAD_MORE, endAction(12, 12, canLoadMore = true, loadingMore = false, stalled = false, waiting = 4, candidates = 50))
        // Not one candidate was complete: still worth paging back.
        assertEquals(EndAction.LOAD_MORE, endAction(0, 0, canLoadMore = true, loadingMore = false, stalled = false, waiting = 0, candidates = 20))
        // Without the setting, as before: an empty list waits for its refresh.
        assertEquals(EndAction.NONE, endAction(0, 0, canLoadMore = true, loadingMore = false, stalled = false))
        // The tail says what happens.
        assertEquals(ListTail.VERIFYING, listTail(12, 12, canLoadMore = true, loadingMore = true, stalled = false, checking = true))
        assertEquals(ListTail.VERIFY_OFFLINE, listTail(12, 12, canLoadMore = true, loadingMore = false, stalled = false, checking = true, offline = true))
        assertEquals(ListTail.REVEALING, listTail(10, 12, canLoadMore = true, loadingMore = false, stalled = false, checking = true))
        assertEquals(ListTail.END, listTail(12, 12, canLoadMore = false, loadingMore = false, stalled = false))
    }
}

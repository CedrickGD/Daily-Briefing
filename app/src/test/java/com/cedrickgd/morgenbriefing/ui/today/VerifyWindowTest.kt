package com.cedrickgd.morgenbriefing.ui.today

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.CommonStrings
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import com.cedrickgd.morgenbriefing.ui.news.FeedFilter
import com.cedrickgd.morgenbriefing.ui.news.FeedPrefs
import com.cedrickgd.morgenbriefing.ui.news.LATEST_LIST
import com.cedrickgd.morgenbriefing.ui.news.Verification
import com.cedrickgd.morgenbriefing.ui.news.buildNewsFeed
import com.cedrickgd.morgenbriefing.ui.news.entryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** What Today verifies (or warms) for the list on screen, and the hint above the feed while it does. */
class VerifyWindowTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))

    private fun item(id: String, topic: String = "welt") = NewsItem(
        id = id, headline = "H$id", url = "https://example.org/$id", source = "S", sourceId = "s", topicId = topic,
        publishedAt = now.minusHours(1).toOffsetDateTime().toString(),
    )

    private fun news(topic: String, ids: List<String>) =
        topic to TopicNews(topic, now.toOffsetDateTime().toString(), ids.map { item(it, topic) }, listOf(SourceStatus("s", "S", ok = true)))

    private fun prefs(readableOnly: Boolean, size: Int = 2) = FeedPrefs(
        AppSettings(uiLanguage = AppLanguage.DE, edition = NewsEdition.GERMANY, followed = listOf("welt", "sport"), sectionSize = size, readableOnly = readableOnly),
    )

    private val repo = mapOf(news("welt", (1..30).map { "w$it" }), news("sport", (1..5).map { "s$it" }))

    @Test
    fun `with the setting the window holds the candidates ahead, in list order`() {
        val verdicts = (1..5).associate { "w$it" to ArticleStatus.READABLE }
        val feed = buildNewsFeed(repo, prefs(readableOnly = true), now, verification = Verification(true, verdicts))
        // The topic's own list: w1..w5 shown, the user sees w4 and w5 – the next candidates, starting with w6.
        val keys = listOf(entryKey("topic-welt", 3, "w4"), entryKey("topic-welt", 4, "w5"))
        val window = warmWindow(FeedFilter.OneTopic("welt"), feed, revealed = 20, visibleKeys = keys, sectionSize = 2)
        assertEquals((6..25).map { "w$it" }, window.map { it.id })
        // "Für dich": "welt" is full, "sport" needs both of its articles (+1 spare) – nothing for welt.
        val forYou = warmWindow(FeedFilter.ForYou, feed, revealed = 20, visibleKeys = listOf("foryou-s0-welt-header"), sectionSize = 2)
        assertEquals(listOf("s1", "s2", "s3"), forYou.map { it.id })
        // "Neueste": verified prefix w1..w5, then the candidates in its own order.
        val latest = warmWindow(FeedFilter.Latest, feed, revealed = 20, visibleKeys = listOf("latest-intro"), sectionSize = 2)
        assertEquals("w6", latest.first().id)
    }

    @Test
    fun `without the setting the articles on screen and the next few are warmed`() {
        val feed = buildNewsFeed(repo, prefs(readableOnly = false), now)
        val keys = listOf(entryKey("topic-welt", 0, "w1"), entryKey("topic-welt", 1, "w2"))
        val window = warmWindow(FeedFilter.OneTopic("welt"), feed, revealed = 20, visibleKeys = keys, sectionSize = 2)
        assertEquals(listOf("w1", "w2", "w3", "w4", "w5", "w6"), window.map { it.id })
    }

    @Test
    fun `the hint above the feed waits while articles are checked and is honest when none is complete`() {
        val strings = FeedStrings.of(AppLanguage.DE)
        val common = CommonStrings.of(AppLanguage.DE)
        val checking = buildNewsFeed(repo, prefs(readableOnly = true), now, verification = Verification(true))
        assertTrue(checking.isEmpty)
        assertNull("the lists say 'Weitere Artikel werden geprüft' themselves", newsNotice(checking, false, false, true, true, strings, common))
        // Offline: one clear note above the feed – only verified articles show until the phone is back online.
        val offline = newsNotice(checking, false, false, true, true, strings, common, verifyOffline = true)!!
        assertEquals(strings.verifyOfflineTitle, offline.title)
        assertEquals(strings.verifyOfflineText, offline.text)

        val all = repo.values.flatMap { it.items }.associate { it.id to ArticleStatus.PAYWALL }
        val none = buildNewsFeed(repo, prefs(readableOnly = true), now, verification = Verification(true, all))
        val notice = newsNotice(none, false, false, true, true, strings, common)!!
        assertEquals(strings.noCompleteTitle, notice.title)
        assertEquals(NoticeAction.RETRY, notice.action)
    }

    @Test
    fun `kept articles of a list key stay in place`() {
        val verdicts = mapOf("w1" to ArticleStatus.FAILED, "w2" to ArticleStatus.READABLE)
        val feed = buildNewsFeed(
            repo, prefs(readableOnly = true), now,
            verification = Verification(true, verdicts, kept = mapOf("welt" to setOf("w1"), LATEST_LIST to setOf("w1"))),
        )
        assertEquals(listOf("w1", "w2"), feed.sectionFor("welt")!!.all.map { it.id })
        assertEquals(listOf("w1", "w2"), feed.latest.map { it.item.id })
    }

    private fun com.cedrickgd.morgenbriefing.ui.news.NewsFeed.sectionFor(id: String) = sections.firstOrNull { it.id == id }
}

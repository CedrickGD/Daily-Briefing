package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
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

/** "Lange Artikel ausblenden" in the lists: live blogs at once, measured long reads from the last refresh on. */
class LongReadsFeedTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 16, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))

    private fun item(id: String, hoursAgo: Long, headline: String = "Meldung $id") = NewsItem(
        id = id, headline = headline, url = "https://example.org/$id", source = "S", sourceId = "s", topicId = "politik",
        publishedAt = now.minusHours(hoursAgo).toOffsetDateTime().toString(),
    )

    private val a = item("a", 1)
    private val live = item("live", 2, headline = "Liveblog: Der Wahlabend")
    private val novel = item("novel", 3)
    private val b = item("b", 4)
    private val repo = mapOf(
        "politik" to TopicNews("politik", now.toOffsetDateTime().toString(), listOf(a, live, novel, b), listOf(SourceStatus("s", "S", ok = true))),
    )

    private fun prefs(hideLong: Boolean, readableOnly: Boolean = false) = FeedPrefs(
        AppSettings(
            uiLanguage = AppLanguage.DE, edition = NewsEdition.GERMANY, followed = listOf("politik"),
            readableOnly = readableOnly, hideLong = hideLong,
        ),
    )

    private val measured = ReadabilityHides.of(emptyList(), emptyMap(), mapOf("novel" to 2_100, "a" to 380))

    @Test
    fun `with the setting on, live blogs and measured long reads stay out`() {
        val feed = buildNewsFeed(repo, prefs(hideLong = true), now, hides = measured)
        assertEquals(listOf("a", "b"), feed.sections.single().all.map { it.id })
        assertEquals(listOf("a", "b"), feed.timeline.map { it.item.id })
    }

    @Test
    fun `with the setting off, everything shows`() {
        val feed = buildNewsFeed(repo, prefs(hideLong = false), now, hides = measured)
        assertEquals(listOf("a", "live", "novel", "b"), feed.sections.single().all.map { it.id })
    }

    @Test
    fun `an unmeasured article shows - nothing waits for its length`() {
        val feed = buildNewsFeed(repo, prefs(hideLong = true), now, hides = ReadabilityHides.NONE)
        assertTrue("novel" in feed.sections.single().all.map { it.id })
        assertFalse("live" in feed.sections.single().all.map { it.id })
    }

    @Test
    fun `with only complete articles, a long one is never verified`() {
        val statuses = mapOf("a" to ArticleStatus.READABLE, "novel" to ArticleStatus.READABLE, "b" to ArticleStatus.READABLE)
        val verification = Verification(enabled = true, statuses = statuses, long = setOf("novel"))
        val feed = buildNewsFeed(repo, prefs(hideLong = true, readableOnly = true), now, verification = verification)
        assertEquals(listOf("a", "b"), feed.sections.single().all.map { it.id })
        // Kept since the last build: it stays until the next one, then it goes.
        val kept = mapOf("politik" to setOf("a", "novel"))
        assertEquals(Check.VERIFIED, verification.copy(kept = kept).check("politik", "novel"))
        assertEquals(mapOf("politik" to setOf("a")), keptForBuild(kept, statuses, long = setOf("novel")))
    }
}

package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsAge
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicIcon
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import com.cedrickgd.morgenbriefing.i18n.TopicStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class FeedBuildTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))

    private fun item(id: String, topic: String, hoursAgo: Long, domain: String = "") = NewsItem(
        id = id, headline = "H$id", url = "https://example.org/$id", source = "S", sourceId = "s", topicId = topic,
        publishedAt = now.minusHours(hoursAgo).toOffsetDateTime().toString(), publisherDomain = domain,
    )

    private fun news(topic: String, vararg items: NewsItem) =
        topic to TopicNews(topic, now.toOffsetDateTime().toString(), items.toList(), listOf(SourceStatus("s", "S", ok = true)))

    private val washington = Place("Washington", 38.9072, -77.0369, "District of Columbia, USA", "US")
    private val midterms = Topic(
        "custom:mid1", "Midterms", "🔎", 0xFF2F6FDE, custom = true, icon = TopicIcon.SEARCH.name, query = "Midterms",
    )

    private fun settings(followed: List<String>, sectionSize: Int = 2, readableOnly: Boolean = true) = AppSettings(
        uiLanguage = AppLanguage.DE,
        edition = NewsEdition.GERMANY,
        followed = followed,
        localPlaces = listOf(washington),
        customTopics = listOf(midterms),
        sectionSize = sectionSize,
        readableOnly = readableOnly,
    )

    @Test
    fun `sections follow the followed topics - categories, sub-topics, places and searches`() {
        val local = Topics.localId(washington)
        val prefs = FeedPrefs(settings(listOf("wirtschaft/boerse", "politik", local, midterms.id)))
        val feed = buildNewsFeed(emptyMap(), prefs, now)
        assertEquals(listOf("wirtschaft/boerse", "politik", local, midterms.id), feed.sections.map { it.id })
        val strings = TopicStrings.of(AppLanguage.DE)
        assertEquals(strings.title("wirtschaft/boerse"), feed.sections[0].topic.title)
        assertEquals(strings.subtitle("wirtschaft/boerse"), feed.sections[0].subtitle)
        assertEquals("Washington", feed.sections[2].topic.title)
        assertEquals(strings.localSubtitle("Washington"), feed.sections[2].subtitle)
        assertEquals(strings.searchSubtitle("Midterms"), feed.sections[3].subtitle)
        // Categories with sub-topics lead to the discover screen; sub-topics and places do not.
        assertTrue(feed.sections[1].browsable)
        assertFalse(feed.sections[0].browsable)
        assertFalse(feed.sections[2].browsable)
        // Chips: "Für dich", "Neueste", then the followed topics with their icons.
        val chips = feedChips(feed, FeedStrings.of(AppLanguage.DE))
        assertEquals(listOf("Für dich", "Neueste", strings.title("wirtschaft/boerse"), strings.title("politik"), "Washington", "Midterms"), chips.map { it.label })
        assertEquals(TopicIcon.LOCATION.name, chips[4].topic?.icon)
    }

    @Test
    fun `titles follow the interface language`() {
        val prefs = FeedPrefs(settings(listOf("wirtschaft")).copy(uiLanguage = AppLanguage.EN))
        assertEquals(AppLanguage.EN, prefs.language)
        assertEquals(TopicStrings.of(AppLanguage.EN).title("wirtschaft"), prefs.topics.single().title)
    }

    @Test
    fun `a story in a category and its sub-topic shows once on top and once in latest, under the sub-topic`() {
        val prefs = FeedPrefs(settings(listOf("wirtschaft", "wirtschaft/boerse"), sectionSize = 2))
        val shared = item("dax", "wirtschaft/boerse", 1)
        val repo = mapOf(
            news("wirtschaft", shared, item("w1", "wirtschaft", 2), item("w2", "wirtschaft", 3)),
            news("wirtschaft/boerse", shared, item("b1", "wirtschaft/boerse", 4)),
        )
        val feed = buildNewsFeed(repo, prefs, now)
        val wirtschaft = feed.sectionFor("wirtschaft")!!
        val boerse = feed.sectionFor("wirtschaft/boerse")!!
        assertEquals(listOf("dax", "w1"), wirtschaft.top.map { it.id })
        // "Für dich" does not repeat the DAX story in the sub-topic's section …
        assertEquals(listOf("b1"), boerse.top.map { it.id })
        // … but the sub-topic's own list still has everything.
        assertEquals(listOf("dax", "b1"), boerse.all.map { it.id })
        assertTrue(boerse.hasMore)
        // "Neueste": every story once, the shared one under its own topic.
        assertEquals(listOf("dax", "w1", "w2", "b1"), feed.latest.map { it.item.id })
        assertEquals("wirtschaft/boerse", feed.latest.first().topic.id)
        assertEquals(4, feed.articleCount)
    }

    @Test
    fun `a sub-topic wins over its category even when the story came from neither`() {
        val prefs = FeedPrefs(settings(listOf("wirtschaft", "wirtschaft/boerse")))
        val story = item("x", "gn-other", 1)
        val feed = buildNewsFeed(mapOf(news("wirtschaft", story), news("wirtschaft/boerse", story)), prefs, now)
        assertEquals("wirtschaft/boerse", feed.latest.single().topic.id)
    }

    @Test
    fun `a section whose stories all opened earlier sections still shows them`() {
        val prefs = FeedPrefs(settings(listOf("wirtschaft", "wirtschaft/boerse"), sectionSize = 2))
        val a = item("a", "wirtschaft/boerse", 1)
        val feed = buildNewsFeed(mapOf(news("wirtschaft", a), news("wirtschaft/boerse", a)), prefs, now)
        assertEquals(listOf("a"), feed.sectionFor("wirtschaft/boerse")!!.top.map { it.id })
    }

    @Test
    fun `a topic's own list keeps older pages that the age filter leaves out of for-you`() {
        val prefs = FeedPrefs(settings(listOf("politik"), sectionSize = 2).copy(newsAge = NewsAge.DAY))
        val items = listOf(item("n1", "politik", 1), item("n2", "politik", 2), item("n3", "politik", 3), item("old1", "politik", 72), item("old2", "politik", 96))
        val feed = buildNewsFeed(mapOf(news("politik", *items.toTypedArray())), prefs, now)
        val politik = feed.sectionFor("politik")!!
        assertEquals(5, politik.count)
        assertEquals(listOf("n1", "n2"), politik.top.map { it.id })
        assertEquals(listOf("n1", "n2", "n3"), feed.latest.map { it.item.id })
    }

    @Test
    fun `readable only applies the snapshot of the last refresh`() {
        val hides = ReadabilityHides.of(listOf("nytimes.com"), mapOf("paid" to ArticleStatus.PAYWALL))
        val repo = mapOf(news("welt", item("ok", "welt", 1), item("nyt", "welt", 2, domain = "nytimes.com"), item("paid", "welt", 3)))
        val on = buildNewsFeed(repo, FeedPrefs(settings(listOf("welt"))), now, hides)
        assertEquals(listOf("ok"), on.sectionFor("welt")!!.all.map { it.id })
        assertEquals(listOf("ok"), on.latest.map { it.item.id })
        val off = buildNewsFeed(repo, FeedPrefs(settings(listOf("welt"), readableOnly = false)), now, hides)
        assertEquals(listOf("ok", "nyt", "paid"), off.sectionFor("welt")!!.all.map { it.id })
    }

    @Test
    fun `prefs know which followed topics can page and what to suggest`() {
        val prefs = FeedPrefs(settings(listOf("top", "wirtschaft/boerse", Topics.localId(washington), midterms.id)))
        // Top stories cannot page; searches, sub-topics and places can.
        assertFalse("top" in prefs.pageable)
        assertTrue("wirtschaft/boerse" in prefs.pageable)
        assertTrue(Topics.localId(washington) in prefs.pageable)
        assertTrue(midterms.id in prefs.pageable)
        // Suggestions: categories not followed, the category of a followed sub-topic left out.
        val suggested = prefs.suggestions.map { it.id }
        assertFalse("top" in suggested)
        assertFalse("wirtschaft" in suggested)
        assertTrue("politik" in suggested)
        assertTrue(suggested.all { prefs.subtitles[it]?.isNotBlank() == true || it == "region" })
        assertEquals("de", prefs.editionLanguage)
        assertEquals(listOf("top", "wirtschaft/boerse", Topics.localId(washington), midterms.id), prefs.followedIds)
    }
}

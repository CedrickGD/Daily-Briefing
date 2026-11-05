package com.cedrickgd.morgenbriefing.ui.search

import com.cedrickgd.morgenbriefing.data.SettingsOps
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUiTest {

    /** Everything shows: the readability filter has its own tests ([SearchViewTest]). */
    private val settings = AppSettings(followed = listOf("top", "politik"), recentSearches = listOf("Midterms", "EZB"), readableOnly = false)

    private fun item(id: String, topic: String = Topics.SEARCH_ID, image: ArticleImage? = null, source: String = "gn") = NewsItem(
        id = id, headline = "Story $id", url = "https://news.example/$id", source = "Example", sourceId = source, topicId = topic, image = image,
    )

    private fun results(query: String, vararg items: NewsItem) =
        SearchResults(query = query, items = items.toList(), nextPage = 1, hasMore = true, phase = SearchPhase.READY)

    @Test
    fun `results get their topic and the picture the prefetcher found`() {
        val own = ArticleImage("https://img.example/own.jpg")
        val ui = buildSearchUi(
            settings = settings,
            lang = AppLanguage.EN,
            results = results("Midterms", item("a"), item("b", image = own), item("c", topic = "politik")),
            images = mapOf("a" to "https://img.example/a.jpg", "b" to "https://img.example/other.jpg"),
            top = null,
            topLoading = false,
            statuses = emptyMap(),
        )
        val (a, b, c) = ui.articles
        assertEquals("https://img.example/a.jpg", a.item.image?.url)
        // An item's own picture wins.
        assertEquals(own, b.item.image)
        assertNull(c.item.image)
        // Google News results show the query as their topic, cached stories their own topic.
        assertEquals(Topics.SEARCH_ID, a.topic.id)
        assertEquals("Midterms", a.topic.title)
        assertEquals("politik", c.topic.id)
        assertEquals("Politics", c.topic.title)
        assertEquals(listOf("Midterms", "EZB"), ui.recent)
    }

    @Test
    fun `the query counts as followed only while its own topic is followed`() {
        val (followed, id) = SettingsOps.followSearch(settings, "Midterms")
        val query = results("midterms")
        assertEquals(id, buildSearchUi(followed, AppLanguage.DE, query, emptyMap(), null, false, emptyMap()).followedTopicId)
        val unfollowed = SettingsOps.unfollow(followed, id)
        assertNull(buildSearchUi(unfollowed, AppLanguage.DE, query, emptyMap(), null, false, emptyMap()).followedTopicId)
        assertNull(buildSearchUi(followed, AppLanguage.DE, SearchResults(), emptyMap(), null, false, emptyMap()).followedTopicId)
    }

    @Test
    fun `results wear the color the query has as a followed topic`() {
        val (followed, id) = SettingsOps.followSearch(settings, "Midterms")
        val own = followed.customTopics.single { it.id == id }
        val stand = searchResultsTopic("Midterms", null, AppLanguage.DE)
        // Same palette and pick as the topic following the search creates.
        assertEquals(own.seed, stand.seed)
        assertEquals("Midterms", stand.title)
        val asTopic = searchResultsTopic("Midterms", own, AppLanguage.DE)
        assertEquals(Topics.SEARCH_ID, asTopic.id)
        assertEquals(own.seed, asTopic.seed)
        // Nothing typed: the plain search topic.
        assertEquals(Topics.searchTopic(AppLanguage.DE), searchResultsTopic("", null, AppLanguage.DE))
    }

    @Test
    fun `trending shows the newest top stories without switched off sources`() {
        val top = TopicNews(
            Topics.TOP_ID,
            "2026-11-05T08:00:00+01:00",
            (1..8).map { item("t$it", topic = Topics.TOP_ID, source = if (it == 2) "off" else "gn") },
            listOf(SourceStatus("gn", "Google News", ok = true, count = 8)),
        )
        val ui = buildSearchUi(settings.copy(disabledFeeds = setOf("off")), AppLanguage.DE, SearchResults(), emptyMap(), top, topLoading = true, statuses = emptyMap())
        assertEquals(listOf("t1", "t3", "t4", "t5", "t6"), ui.trending.map { it.id })
        assertEquals(TRENDING_SIZE, ui.trending.size)
        assertTrue(ui.trendingLoading)
    }
}

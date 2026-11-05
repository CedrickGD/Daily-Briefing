package com.cedrickgd.morgenbriefing.ui.today

import com.cedrickgd.morgenbriefing.data.SettingsOps
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.domain.WeatherFixtures
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.ui.brief.toWeatherUi
import com.cedrickgd.morgenbriefing.ui.news.FeedArticle
import com.cedrickgd.morgenbriefing.ui.news.FeedFilter
import com.cedrickgd.morgenbriefing.ui.news.FeedPrefs
import com.cedrickgd.morgenbriefing.ui.news.NewsFeed
import com.cedrickgd.morgenbriefing.ui.news.SectionUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class TodayFeedTest {

    private val ki = Topic("ki", "KI", "🤖", 0xFF000000)
    private val sport = Topic("sport", "Sport", "⚽", 0xFF000000)

    private fun item(id: String, topic: String) = NewsItem(id, "H$id", url = "https://x/$id", source = "S", sourceId = "s", topicId = topic)

    private val kiItems = (1..6).map { item("k$it", "ki") }
    private val sportItems = (1..3).map { item("s$it", "sport") }
    private val feed = NewsFeed(
        sections = listOf(
            SectionUi(0, ki, kiItems, kiItems.take(2), fetchedAt = null),
            SectionUi(1, sport, sportItems, sportItems.take(2), fetchedAt = null),
        ),
        latest = (kiItems.map { FeedArticle(it, ki) } + sportItems.map { FeedArticle(it, sport) }),
        fetchedAt = null,
    )

    @Test
    fun `the prefetch order follows what the view shows`() {
        assertEquals(listOf("k1", "k2", "s1", "s2"), viewItems(FeedFilter.ForYou, feed, revealed = 0).map { it.id })
        assertEquals(listOf("k1", "k2", "k3"), viewItems(FeedFilter.Latest, feed, revealed = 3).map { it.id })
        assertEquals(listOf("s1", "s2", "s3"), viewItems(FeedFilter.OneTopic("sport"), feed, revealed = 20).map { it.id })
        assertTrue(viewItems(FeedFilter.OneTopic("gone"), feed, revealed = 20).isEmpty())
    }

    @Test
    fun `load more only for pageable topics that did not reach their end`() {
        val prefs = FeedPrefs(listOf(ki), AppLanguage.DE, pageable = setOf("ki"))
        val state = TodayUiState(now = ZonedDateTime.now(), prefs = prefs)
        assertTrue(state.canLoadMore("ki"))
        assertFalse(state.canLoadMore("sport"))
        assertFalse(state.copy(endReached = setOf("ki")).canLoadMore("ki"))
        assertFalse(TodayUiState(now = ZonedDateTime.now()).canLoadMore("ki"))
    }

    @Test
    fun `source signatures change with the edition and with readable only`() {
        val base = AppSettings(uiLanguage = AppLanguage.DE, edition = NewsEdition.GERMANY, followed = listOf("politik/wahlen"))
        val signature = TodayViewModel.sourceSignatures(base)
        assertEquals(setOf("politik/wahlen"), signature.keys)
        assertEquals(signature, TodayViewModel.sourceSignatures(base.copy(uiLanguage = AppLanguage.EN)))
        val us = NewsEdition.ALL.first { it.country == "US" }
        assertNotEquals(signature, TodayViewModel.sourceSignatures(base.copy(edition = us)))
        assertNotEquals(signature, TodayViewModel.sourceSignatures(base.copy(readableOnly = false)))
        assertTrue("politik" in TodayViewModel.sourceSignatures(base.copy(followed = listOf("politik/wahlen", "politik"))))
    }

    @Test
    fun `switching readable only off fetches top stories, sections and places again`() {
        // Their addresses take no exclusions, yet the repository drops walled sites while fetching.
        val zurich = Place("Zürich", 47.37, 8.54, "Zürich, Schweiz", "CH")
        val (withPlace, placeId) = SettingsOps.addLocalPlace(
            AppSettings(edition = NewsEdition.GERMANY, followed = listOf(Topics.TOP_ID, "welt", "region")),
            zurich,
        )
        val on = TodayViewModel.sourceSignatures(withPlace)
        val off = TodayViewModel.sourceSignatures(withPlace.copy(readableOnly = false))
        listOf(Topics.TOP_ID, "welt", "region", placeId).forEach { id ->
            assertNotEquals(id, on.getValue(id), off.getValue(id))
        }
        // The setting itself is the only difference: no other source moved.
        assertEquals(on.getValue(Topics.TOP_ID).dropLast(1), off.getValue(Topics.TOP_ID).dropLast(1))
    }

    @Test
    fun `the weather texts follow a new interface language without a new fetch`() {
        val german = WeatherFixtures.foggyAutumn.toWeatherUi(AppLanguage.DE)
        val english = german.inLanguage(AppLanguage.EN)
        assertEquals(AppLanguage.EN, english.language)
        assertSame(german.snapshot, english.snapshot)
        assertEquals(WeatherFixtures.foggyAutumn.toWeatherUi(AppLanguage.EN), english)
        assertNotEquals(german.warnings.map { it.title }, english.warnings.map { it.title })
        // Already in that language: the same object, nothing derived again.
        assertSame(german, german.inLanguage(AppLanguage.DE))
    }
}

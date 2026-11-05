package com.cedrickgd.morgenbriefing.ui.discover

import com.cedrickgd.morgenbriefing.data.SettingsOps
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverStateTest {

    private val washington = Place("Washington", 38.895, -77.0366, "District of Columbia, Vereinigte Staaten", "US")
    private val paris = Place("Paris", 48.8534, 2.3488, "Île-de-France, Frankreich", "FR")
    private val atlantis = Place("Atlantis", 10.0, 10.0, "", "")

    private val settings = AppSettings(
        edition = NewsEdition.GERMANY,
        followed = listOf("top", "wirtschaft", "wirtschaft/boerse", "wirtschaft/krypto", "ki/ki"),
    )

    private fun item(id: String, topic: String, source: String = "src") = NewsItem(
        id = id, headline = "Headline $id", url = "https://example.com/$id", source = "Example", sourceId = source, topicId = topic,
    )

    private fun news(topic: String, vararg items: NewsItem) =
        topic to TopicNews(topic, "2026-11-05T08:00:00+01:00", items.toList(), listOf(SourceStatus("src", "Example", ok = true, count = items.size)))

    private fun build(
        s: AppSettings = settings,
        lang: AppLanguage = AppLanguage.DE,
        news: Map<String, TopicNews> = emptyMap(),
        loading: Set<String> = emptySet(),
        failed: Set<String> = emptySet(),
        selection: DiscoverSelection = DiscoverSelection(),
    ) = buildDiscoverState(s, lang, news, loading, failed, selection)

    @Test
    fun `the grid shows the twelve categories in taxonomy order with their titles`() {
        val state = build()
        assertTrue(state.loaded)
        assertEquals(Taxonomy.CATEGORIES.map { it.id }, state.categories.map { it.topic.id })
        assertEquals(12, state.categories.size)
        val money = state.categories.first { it.topic.id == "wirtschaft" }
        assertEquals("Wirtschaft & Finanzen", money.topic.title)
        assertEquals("Märkte, Unternehmen und dein Geld", money.subtitle)
        assertEquals(Taxonomy.children("wirtschaft").size, money.subCount)
        assertNull(state.detail)
    }

    @Test
    fun `titles follow the interface language`() {
        val english = build(lang = AppLanguage.EN).categories.first { it.topic.id == "top" }
        assertEquals("Top stories", english.topic.title)
        assertEquals("The day's most important news at a glance", english.subtitle)
    }

    @Test
    fun `tiles count the category and its followed sub-topics`() {
        val tiles = build().categories.associateBy { it.topic.id }
        assertEquals(3, tiles.getValue("wirtschaft").followedCount)
        assertTrue(tiles.getValue("wirtschaft").categoryFollowed)
        // Only a sub-topic of "Tech & KI" is followed, not the category.
        assertEquals(1, tiles.getValue("ki").followedCount)
        assertFalse(tiles.getValue("ki").categoryFollowed)
        assertEquals(0, tiles.getValue("sport").followedCount)
        assertEquals(1, tiles.getValue("top").followedCount)
        assertEquals(0, tiles.getValue("top").subCount)
        assertEquals(5, build().followedCount)
    }

    @Test
    fun `the region tile is titled with the chosen Bundesland in Germany`() {
        val region = build(settings.copy(region = "sachsen")).categories.first { it.topic.id == Topics.REGION_ID }
        assertEquals(Topics.resolve(Topics.REGION_ID, settings.copy(region = "sachsen"))?.title, region.topic.title)
    }

    @Test
    fun `an open category lists its sub-topics with their follow state`() {
        val detail = build(selection = DiscoverSelection(categoryId = "wirtschaft")).detail
        assertNotNull(detail!!)
        assertEquals("wirtschaft", detail.category.topic.id)
        assertTrue(detail.category.followed)
        assertEquals(Taxonomy.children("wirtschaft").map { it.id }, detail.subTopics.map { it.topic.id })
        val followed = detail.subTopics.filter { it.followed }.map { it.topic.id }
        assertEquals(listOf("wirtschaft/boerse", "wirtschaft/krypto"), followed)
        assertEquals("Börse & Märkte", detail.subTopics.first().topic.title)
        assertTrue(detail.subTopics.all { it.subtitle.isNotBlank() })
        assertNull(detail.expandedSubId)
        assertNull(detail.subPreview)
    }

    @Test
    fun `an unknown or non-category selection shows the grid`() {
        assertNull(build(selection = DiscoverSelection(categoryId = "nope")).detail)
        assertNull(build(selection = DiscoverSelection(categoryId = "wirtschaft/boerse")).detail)
    }

    @Test
    fun `the category preview shows its three newest headlines without switched off sources`() {
        val items = arrayOf(item("1", "wirtschaft"), item("2", "wirtschaft", source = "off"), item("3", "wirtschaft"), item("4", "wirtschaft"), item("5", "wirtschaft"))
        val state = build(
            s = settings.copy(disabledFeeds = setOf("off")),
            news = mapOf(news("wirtschaft", *items)),
            selection = DiscoverSelection(categoryId = "wirtschaft"),
        )
        val preview = state.detail!!.preview
        assertEquals(listOf("1", "3", "4"), preview.items.map { it.id })
        assertEquals(PREVIEW_SIZE, preview.items.size)
        assertFalse(preview.waiting)
        assertFalse(preview.empty)
    }

    @Test
    fun `preview states tell waiting, empty and failed apart`() {
        val selection = DiscoverSelection(categoryId = "sport")
        // Never fetched: skeleton.
        assertTrue(build(selection = selection).detail!!.preview.waiting)
        // Fetching: skeleton too.
        assertTrue(build(selection = selection, loading = setOf("sport")).detail!!.preview.waiting)
        // Fetched, nothing there.
        val empty = build(selection = selection, news = mapOf(news("sport"))).detail!!.preview
        assertTrue(empty.empty)
        assertFalse(empty.waiting)
        // Failed with nothing cached.
        val failed = build(selection = selection, failed = setOf("sport")).detail!!.preview
        assertTrue(failed.failed)
        assertFalse(failed.waiting)
        // Failed, but the cache still has stories: they show, no failure.
        val cached = build(selection = selection, failed = setOf("sport"), news = mapOf(news("sport", item("a", "sport")))).detail!!.preview
        assertFalse(cached.failed)
        assertEquals(1, cached.items.size)
    }

    @Test
    fun `a tapped sub-topic opens its own preview under its row`() {
        val state = build(
            news = mapOf(news("wirtschaft/krypto", item("k", "wirtschaft/krypto"))),
            selection = DiscoverSelection(categoryId = "wirtschaft", previewId = "wirtschaft/krypto"),
        )
        val detail = state.detail!!
        assertEquals("wirtschaft/krypto", detail.expandedSubId)
        assertEquals(listOf("k"), detail.subPreview!!.items.map { it.id })
        assertEquals("wirtschaft", detail.preview.topicId)
        // A preview id of another category is ignored.
        val other = build(selection = DiscoverSelection(categoryId = "wirtschaft", previewId = "sport/tennis")).detail!!
        assertNull(other.expandedSubId)
    }

    @Test
    fun `the last followed topic cannot be switched off`() {
        val single = AppSettings(followed = listOf("sport"))
        val detail = build(s = single, selection = DiscoverSelection(categoryId = "sport")).detail!!
        assertTrue(detail.category.followed)
        assertFalse(detail.category.canToggle)
        assertTrue(detail.subTopics.all { it.canToggle })
        assertTrue(build(selection = DiscoverSelection(categoryId = "wirtschaft")).detail!!.category.canToggle)
    }

    @Test
    fun `followed places read their own country's edition`() {
        val withPlaces = SettingsOps.addLocalPlace(SettingsOps.addLocalPlace(settings, washington).first, atlantis).first
        val state = build(s = withPlaces)
        assertEquals(listOf("Washington", "Atlantis"), state.places.map { it.place.name })
        val dc = state.places.first()
        assertEquals(Topics.localId(washington), dc.topicId)
        assertTrue(dc.followed)
        assertEquals("Englisch", dc.language)
        assertEquals("Vereinigte Staaten", dc.country)
        // No edition for the place's country: the main edition.
        assertEquals("Deutsch", state.places[1].language)
        assertEquals("Deutschland", state.places[1].country)
        assertNull(dc.preview)
    }

    @Test
    fun `edition names follow the interface language and its writing`() {
        val us = NewsEdition.forCountry("US")!!
        assertEquals("English", displayLanguage(us, AppLanguage.EN.locale))
        assertEquals("United States", displayCountry(us, AppLanguage.EN.locale))
        assertEquals("anglais", displayLanguage(us, AppLanguage.FR.locale))
        assertEquals("inglés", displayLanguage(us, AppLanguage.ES.locale))
        assertEquals("inglese", displayLanguage(us, AppLanguage.IT.locale))
        assertEquals(NewsEdition.forCountry("FR"), placeEdition(paris, settings))
        assertEquals(NewsEdition.GERMANY, placeEdition(atlantis, settings))
    }

    @Test
    fun `an open place shows its headlines`() {
        val withPlace = SettingsOps.addLocalPlace(settings, washington).first
        val id = Topics.localId(washington)
        val state = build(s = withPlace, news = mapOf(news(id, item("w", id))), selection = DiscoverSelection(expandedPlaceId = id))
        assertEquals(listOf("w"), state.places.single().preview!!.items.map { it.id })
    }

    @Test
    fun `followed searches are listed with their follow state, own feed topics are not`() {
        val (followedSearch, searchId) = SettingsOps.followSearch(settings, "Midterms")
        val unfollowed = SettingsOps.unfollow(followedSearch, searchId)
        val feedTopic = com.cedrickgd.morgenbriefing.data.model.Topic("custom:feeds", "Verein", "⚽", 0xFF00AA00)
        val state = build(s = SettingsOps.addCustomTopic(unfollowed, feedTopic))
        val search = state.searches.single()
        assertEquals("Midterms", search.topic.title)
        assertFalse(search.followed)
        assertEquals("Suche „Midterms“", search.subtitle)
        assertTrue(build(s = followedSearch).searches.single().followed)
    }

    @Test
    fun `the refreshed topic is the open category, its open sub-topic or the open place`() {
        assertNull(previewTarget(DiscoverSelection()))
        assertEquals("wirtschaft", previewTarget(DiscoverSelection(categoryId = "wirtschaft")))
        assertEquals("wirtschaft/boerse", previewTarget(DiscoverSelection(categoryId = "wirtschaft", previewId = "wirtschaft/boerse")))
        assertEquals("local:x", previewTarget(DiscoverSelection(expandedPlaceId = "local:x")))
        // Inside a category the place rows are not on screen.
        assertEquals("sport", previewTarget(DiscoverSelection(categoryId = "sport", expandedPlaceId = "local:x")))
    }
}

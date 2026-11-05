package com.cedrickgd.morgenbriefing.ui.settings

import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.NewsSection
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.data.remote.FeedError
import com.cedrickgd.morgenbriefing.data.remote.FeedException
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.SourcesStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesLogicTest {

    private val german = AppSettings(edition = NewsEdition.GERMANY, followed = listOf("top", "wirtschaft", "wirtschaft/boerse", "ki"))

    private fun googleOf(topicId: String, settings: AppSettings): FeedSource =
        TopicSources.resolve(topicId, settings, includeDisabled = true).single { it.isGoogleNews }

    @Test
    fun `searches are shown in a few readable words`() {
        assertEquals("White House, Congress, Senate …", SourcesLogic.shortQuery("\"White House\" OR Congress OR Senate OR lawmakers"))
        assertEquals("Midterms", SourcesLogic.shortQuery("Midterms"))
        assertEquals("Tesla, SpaceX", SourcesLogic.shortQuery("Tesla OR SpaceX when:7d -site:nytimes.com"))
        assertEquals("", SourcesLogic.shortQuery("  "))
    }

    @Test
    fun `each Google News source says what it reads`() {
        assertEquals(GoogleNewsLine.TopStories, SourcesLogic.googleLine(googleOf("top", german), german))
        assertEquals(GoogleNewsLine.Section(NewsSection.BUSINESS), SourcesLogic.googleLine(googleOf("wirtschaft", german), german))
        val boerse = SourcesLogic.googleLine(googleOf("wirtschaft/boerse", german), german)
        assertTrue(boerse is GoogleNewsLine.Search && boerse.query.isNotBlank() && "OR" !in boerse.query)

        val washington = Place("Washington", 38.8951, -77.0364, "District of Columbia, United States", "US")
        val withPlace = german.copy(localPlaces = listOf(washington))
        val local = googleOf(Topics.localId(washington), withPlace)
        assertEquals(GoogleNewsLine.Local("Washington"), SourcesLogic.googleLine(local, withPlace))

        val midterms = Topic("custom:mid", "Midterms", "🔎", 0xFF2F6FDE, custom = true, query = "Midterms OR \"House races\"")
        val withSearch = german.copy(customTopics = listOf(midterms), followed = german.followed + midterms.id)
        assertEquals(GoogleNewsLine.Search("Midterms, House races"), SourcesLogic.googleLine(googleOf(midterms.id, withSearch), withSearch))
    }

    @Test
    fun `google lines are worded per language`() {
        assertEquals("Google News · Schlagzeilen", SourcesLogic.describe(GoogleNewsLine.TopStories, AppLanguage.DE))
        assertEquals("Google News · Rubrik Wirtschaft", SourcesLogic.describe(GoogleNewsLine.Section(NewsSection.BUSINESS), AppLanguage.DE))
        assertEquals("Google News · Business section", SourcesLogic.describe(GoogleNewsLine.Section(NewsSection.BUSINESS), AppLanguage.EN))
        assertEquals("Google News · Suche: DAX, Börse", SourcesLogic.describe(GoogleNewsLine.Search("DAX, Börse"), AppLanguage.DE))
        assertEquals("Google News · Local news from Leipzig", SourcesLogic.describe(GoogleNewsLine.Local("Leipzig"), AppLanguage.EN))
        for (lang in AppLanguage.entries) {
            NewsSection.entries.forEach { assertTrue(SourcesStrings.of(lang).section(it).isNotBlank()) }
        }
    }

    @Test
    fun `the last fetch of a source becomes a health with a typed reason`() {
        assertNull(SourcesLogic.health(null))
        assertEquals(SourceHealth.Ok(20), SourcesLogic.health(SourceStatus("a", "A", ok = true, count = 20)))
        assertEquals(
            SourceHealth.Failed(FeedError.NOT_FOUND, 404),
            SourcesLogic.health(SourceStatus("a", "A", ok = false, error = FeedException(FeedError.NOT_FOUND, 404).reason)),
        )
        // Caches of version 2 hold German sentences.
        assertEquals(SourceHealth.Failed(FeedError.BLOCKED, 403), SourcesLogic.health(SourceStatus("a", "A", ok = false, error = "Die Seite blockt den Zugriff (403)")))
        assertEquals(SourceHealth.Failed(FeedError.UNREACHABLE, null), SourcesLogic.health(SourceStatus("a", "A", ok = false, error = "Nicht erreichbar")))
        assertEquals(SourceHealth.Failed(FeedError.HTTP_ERROR, 410), SourcesLogic.health(SourceStatus("a", "A", ok = false, error = "HTTP 410")))
        assertEquals(SourceHealth.Failed(FeedError.UNREADABLE, null), SourcesLogic.health(SourceStatus("a", "A", ok = false, error = "")))
        assertEquals(SourceHealth.Failed(FeedError.UNREADABLE, null), SourcesLogic.health(SourceStatus("a", "A", ok = false, error = "Kaputt")))
    }

    @Test
    fun `failures are explained in every language`() {
        assertEquals("Feed nicht gefunden (404)", SourcesLogic.healthLabel(SourceHealth.Failed(FeedError.NOT_FOUND, 404), AppLanguage.DE))
        assertEquals("Feed not found (404)", SourcesLogic.healthLabel(SourceHealth.Failed(FeedError.NOT_FOUND, 404), AppLanguage.EN))
        assertEquals("Nicht erreichbar", SourcesLogic.healthLabel(SourceHealth.Failed(FeedError.UNREACHABLE, null), AppLanguage.DE))
        assertEquals("20 Meldungen", SourcesLogic.healthLabel(SourceHealth.Ok(20), AppLanguage.DE))
        assertEquals("1 story", SourcesLogic.healthLabel(SourceHealth.Ok(1), AppLanguage.EN))
        for (lang in AppLanguage.entries) {
            FeedError.entries.forEach { error ->
                val text = SourcesStrings.of(lang).feedError(error, 500)
                assertTrue("$lang $error", text.isNotBlank() && text == text.trim())
            }
        }
    }

    @Test
    fun `feed exception codes round-trip`() {
        FeedError.entries.forEach { error ->
            assertEquals(error to null, FeedException.parse(FeedException.code(error)))
            assertEquals(error to 503, FeedException.parse(FeedException.code(error, 503)))
        }
        assertNull(FeedException.parse(null))
        assertEquals(FeedError.BLOCKED, FeedError.forHttp(403))
        assertEquals(FeedError.BLOCKED, FeedError.forHttp(401))
        assertEquals(FeedError.NOT_FOUND, FeedError.forHttp(410))
        assertEquals(FeedError.RATE_LIMITED, FeedError.forHttp(429))
        assertEquals(FeedError.SERVER_ERROR, FeedError.forHttp(502))
        assertEquals(FeedError.HTTP_ERROR, FeedError.forHttp(418))
    }

    @Test
    fun `groups follow the feed, paused own topics come last`() {
        val own = Topic("custom:blog", "Blogs", "📰", 0xFF7B61FF, custom = true)
        val settings = german.copy(customTopics = listOf(own))
        val groups = SourcesLogic.groups(settings, KnownWalls.DOMAINS, AppLanguage.DE)
        assertEquals(listOf("top", "wirtschaft", "wirtschaft/boerse", "ki", own.id), groups.map { it.topic.id })
        assertTrue(groups.dropLast(1).all { it.followed })
        assertEquals(false, groups.last().followed)
        assertTrue(groups.first { it.topic.id == "top" }.sources.any { it.isGoogleNews })
    }

    @Test
    fun `readable only hides walled feeds and says how many`() {
        val ki = SourcesLogic.groups(german, KnownWalls.DOMAINS, AppLanguage.DE).first { it.topic.id == "ki" }
        assertTrue("golem and ZEIT Digital are walled", ki.hiddenUnreadable >= 2)
        assertTrue(ki.sources.none { it.id == "golem" })
        val everything = SourcesLogic.groups(german.copy(readableOnly = false), KnownWalls.DOMAINS, AppLanguage.DE).first { it.topic.id == "ki" }
        assertEquals(0, everything.hiddenUnreadable)
        assertTrue(everything.sources.any { it.id == "golem" })
    }

    @Test
    fun `the row summary counts what is switched on`() {
        val group = SourcesLogic.groups(german, KnownWalls.DOMAINS, AppLanguage.DE).first { it.topic.id == "wirtschaft" }
        val total = group.sources.size
        assertEquals("$total Quellen", SourcesLogic.summary(group, emptySet(), AppLanguage.DE))
        val off = setOf(group.sources.first().id)
        assertEquals("${total - 1} von $total Quellen an", SourcesLogic.summary(group, off, AppLanguage.DE))
        assertEquals("${total - 1} of $total sources on", SourcesLogic.summary(group, off, AppLanguage.EN))
        val paused = group.copy(followed = false, sources = emptyList())
        assertEquals("Noch keine Quelle – füge eine hinzu · nicht im Feed", SourcesLogic.summary(paused, emptySet(), AppLanguage.DE))
    }
}

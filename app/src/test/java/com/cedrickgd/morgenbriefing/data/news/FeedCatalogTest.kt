package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedCatalogTest {

    private val uk = NewsEdition("GB", "en")
    private val austria = NewsEdition("AT", "de")

    @Test
    fun `built-in sources have unique ids, https urls, a language and known topics`() {
        val all = FeedCatalog.builtIn + Regions.ALL.flatMap { it.feeds }
        assertEquals(all.size, all.map { it.id }.distinct().size)
        all.forEach { source ->
            assertTrue(source.id, source.url.startsWith("https://"))
            assertTrue(source.id, source.name.isNotBlank())
            assertTrue(source.id, Taxonomy.isBuiltIn(source.topicId))
            assertTrue(source.id, source.builtIn)
            assertFalse(source.id, source.isGoogleNews)
            assertFalse("ids end up in a comma separated setting: ${source.id}", ',' in source.id)
        }
        FeedCatalog.entries.forEach { feed ->
            assertTrue(feed.source.id, feed.language in setOf("de", "en"))
            feed.countries.forEach { assertTrue(feed.source.id, NewsEdition.forCountry(it) != null) }
        }
    }

    @Test
    fun `every category has publisher feeds in German, the big ones in American and British English`() {
        val german = FeedCatalog.forEdition(NewsEdition.GERMANY).map { it.source.topicId }.toSet()
        Taxonomy.CATEGORIES.filter { it.id != Taxonomy.REGION }.forEach { category ->
            val members = setOf(category.id) + Taxonomy.children(category.id).map { it.id }
            assertTrue(category.id, german.any { it in members })
        }
        for (edition in listOf(NewsEdition.USA, uk)) {
            val english = FeedCatalog.forEdition(edition).map { it.source.topicId }.toSet()
            listOf("top", "politik", "welt", "wirtschaft", "ki", "wissenschaft", "gesundheit", "klima", "sport", "entertainment").forEach { id ->
                assertTrue("${edition.key} $id", id in english)
            }
        }
    }

    @Test
    fun `feeds serve their editions only`() {
        assertTrue(FeedCatalog.forEdition(NewsEdition.GERMANY).all { it.language == "de" })
        assertTrue(FeedCatalog.forEdition(NewsEdition.USA).all { it.language == "en" })
        val us = FeedCatalog.forEdition(NewsEdition.USA).map { it.source.id }
        val gb = FeedCatalog.forEdition(uk).map { it.source.id }
        assertTrue("npr-politics" in us && "npr-politics" !in gb)
        assertTrue("bbc-politics" in gb && "bbc-politics" !in us)
        assertTrue("bbc-world" in us && "bbc-world" in gb)
        // German domestic desks stay in Germany, world desks travel to Austria.
        val at = FeedCatalog.forEdition(austria).map { it.source.id }
        assertTrue("tagesschau-inland" !in at && "tagesschau-ausland" in at)
        assertTrue(FeedCatalog.forEdition(NewsEdition("FR", "fr")).isEmpty())
    }

    @Test
    fun `no built-in feed of a known wall is read when only readable articles are wanted`() {
        val walled = FeedCatalog.builtIn.filter { KnownWalls.matches(GoogleNews.domainOf(it.url)) }.map { it.id }
        assertTrue("golem" in walled && "zeit-politik" in walled)
        val ki = FeedCatalog.sourcesFor("ki", AppSettings(edition = NewsEdition.GERMANY))
        assertTrue(ki.none { it.id in walled })
        val all = FeedCatalog.sourcesFor("ki", AppSettings(edition = NewsEdition.GERMANY, readableOnly = false))
        assertTrue(all.any { it.id == "golem" })
    }

    @Test
    fun `general news desks are top stories, not a section`() {
        val byId = FeedCatalog.entries.associateBy { it.source.id }
        listOf("dlf-nachrichten", "zdfheute", "dw").forEach { assertEquals(it, "top", byId.getValue(it).source.topicId) }
        // A business section reads business desks, not the publisher's front page.
        val business = FeedCatalog.forEdition(NewsEdition.GERMANY).filter { it.source.topicId == "wirtschaft" }
        assertTrue(business.none { "schlagzeilen" in it.source.url })
    }

    @Test
    fun `every region has at least one feed`() {
        // 16 Bundesländer, Berlin and Brandenburg share one preset.
        assertEquals(15, Regions.ALL.size)
        Regions.ALL.forEach { assertTrue(it.key, it.feeds.isNotEmpty()) }
        assertEquals(Regions.DEFAULT, Regions.byKey("gibts-nicht").key)
        assertTrue(Regions.servesEdition(NewsEdition.GERMANY))
        assertFalse(Regions.servesEdition(austria))
    }

    @Test
    fun `sources of a topic follow the settings`() {
        val own = FeedSource("custom:1", "Mein Blog", "https://blog.example/feed", "ki", builtIn = false)
        val settings = AppSettings(region = "sachsen", customFeeds = listOf(own), disabledFeeds = setOf("heise-top"), readableOnly = false)
        val ki = FeedCatalog.sourcesFor("ki", settings)
        assertTrue(ki.none { it.id == "heise-top" })
        assertEquals("custom:1", ki.last().id)
        assertTrue(FeedCatalog.allSourcesFor("ki", settings).any { it.id == "heise-top" })
        assertEquals(listOf("mdr-sachsen", "lvz", "l-iz"), FeedCatalog.sourcesFor(Topics.REGION_ID, settings).filterNot { it.isGoogleNews }.map { it.id })
        assertEquals(own, FeedCatalog.byId("custom:1", settings))
    }

    @Test
    fun `region is guessed from the place label`() {
        assertEquals("sachsen", Regions.guess(Place("Bad Düben", 51.59, 12.58, "Sachsen, Deutschland"))?.key)
        assertEquals("sachsen-anhalt", Regions.guess(Place("Halle", 51.48, 11.97, "Sachsen-Anhalt, Deutschland"))?.key)
        assertEquals("berlin-brandenburg", Regions.guess(Place("Potsdam", 52.4, 13.06, "Brandenburg, Deutschland"))?.key)
        assertEquals("nordrhein-westfalen", Regions.guess(Place("Köln", 50.93, 6.95, "Nordrhein-Westfalen, Deutschland"))?.key)
        assertNull(Regions.guess(Place("Wien", 48.2, 16.37, "Wien, Österreich")))
        assertNull(Regions.guess(Place("Irgendwo", 0.0, 0.0)))
    }

    @Test
    fun `catalogue puts the followed topics first and titles the region`() {
        val custom = Topic("custom:ab", "Fußball", "⚽", 0xFF123456)
        val settings = AppSettings(
            followed = listOf("politik", "wirtschaft/boerse", "custom:ab"),
            customTopics = listOf(custom),
            region = "bayern",
            uiLanguage = AppLanguage.DE,
        )
        val catalogue = Topics.catalogue(settings)
        assertEquals(listOf("politik", "wirtschaft/boerse", "custom:ab"), catalogue.take(3).map { it.id })
        assertEquals("Börse & Märkte", catalogue[1].title)
        assertEquals("Bayern", catalogue.first { it.id == Topics.REGION_ID }.title)
        assertTrue(catalogue.single { it.id == "custom:ab" }.custom)
        // Every category is in the catalogue, sub-topics only when followed.
        assertTrue(Topics.DEFAULT_ORDER.all { id -> catalogue.any { it.id == id } })
        assertFalse(catalogue.any { it.id == "wirtschaft/krypto" })
        assertEquals("Fußball", Topics.resolve("custom:ab", settings)?.title)
        assertEquals(Topics.NEUTRAL_SEED, Topics.resolveOrNeutral("custom:zz", settings).seed)
        assertEquals(setOf("politik", "wirtschaft/boerse", "custom:ab"), catalogue.map { it.id }.toSet() - Topics.hidden(settings))
        assertEquals(3, Topics.visible(settings).size)
    }
}

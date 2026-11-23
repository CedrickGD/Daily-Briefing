package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sources from beyond the edition: newsrooms from abroad in its language and English feeds ([TopicSources.mixes]). */
class TopicSourcesMixTest {

    private val catalogue = FeedCatalog.entries.associateBy { it.source.id }
    private val germany = AppSettings(edition = NewsEdition.GERMANY)
    private val austria = NewsEdition("AT", "de")
    private val switzerland = NewsEdition("CH", "de")
    private val usaInGerman = NewsEdition("US", "de")
    private val germanEditions = listOf(NewsEdition.GERMANY, austria, switzerland, usaInGerman)

    private fun feeds(topicId: String, settings: AppSettings) =
        TopicSources.resolve(topicId, settings, includeDisabled = true).filterNot { it.isGoogleNews }

    private fun isEnglish(source: FeedSource) = catalogue[source.id]?.language == "en"

    private fun isAbroad(source: FeedSource, edition: NewsEdition) = catalogue[source.id]?.servesAbroad(edition) == true

    private fun bothOff(settings: AppSettings) = settings.copy(internationalSources = false, englishSources = false)

    @Test
    fun `newsrooms from abroad join a German reader's world, tech and science news behind their switch`() {
        val world = feeds("welt", germany).map { it.id }
        assertTrue(world.toString(), listOf("derstandard-international", "srf-international", "euronews-de").all { it in world })
        assertTrue(feeds("ki", germany).map { it.id }.containsAll(listOf("derstandard-web", "watson-digital")))
        assertTrue("derstandard-wissenschaft" in feeds("wissenschaft", germany).map { it.id })
        assertTrue("businessinsider-de" in feeds("wirtschaft", germany).map { it.id })
        val off = germany.copy(internationalSources = false)
        listOf("welt", "ki", "wissenschaft", "wirtschaft").forEach { id ->
            assertTrue(id, feeds(id, off).none { catalogue.getValue(it.id).abroad })
        }
        // Home desks of Austria and Switzerland (front pages, their own economy) never travel.
        val everything = Taxonomy.ALL.flatMap { feeds(it.id, germany) }.map { it.id }.toSet()
        assertTrue(everything.none { it in setOf("derstandard", "orf", "diepresse", "srf", "derstandard-wirtschaft", "srf-wirtschaft") })
    }

    @Test
    fun `English feeds join a German reader's tech news behind their switch, one by one switchable`() {
        val tech = feeds("ki", germany)
        assertEquals(listOf("techcrunch-ai", "verge", "wired"), tech.filter(::isEnglish).map { it.id })
        assertTrue(tech.all { it.topicId == "ki" })
        assertTrue(feeds("ki", germany.copy(englishSources = false)).none(::isEnglish))
        // A sub-topic takes its own English feeds: The Verge's AI desk for the AI news.
        assertEquals(listOf("verge-ai"), feeds("ki/ki", germany).filter(::isEnglish).map { it.id })
        // Every mixed feed has its switch like any source of the topic.
        val withoutWired = germany.copy(disabledFeeds = setOf("wired", "derstandard-web"))
        assertTrue(TopicSources.resolve("ki", withoutWired).none { it.id == "wired" || it.id == "derstandard-web" })
        assertTrue(TopicSources.resolve("ki", withoutWired, includeDisabled = true).map { it.id }.containsAll(listOf("wired", "derstandard-web")))
    }

    @Test
    fun `news about the reader's country stay with the edition`() {
        val leipzig = Place.LEIPZIG
        val own = Topic("custom:ab", "Bundesliga", "⚽", 0xFF123456, query = "Bundesliga")
        val home = listOf(
            Topics.TOP_ID, "politik", "politik/inland", "politik/wahlen",
            "wirtschaft/konjunktur", "wirtschaft/geld", "wirtschaft/immobilien",
            Topics.REGION_ID, Topics.localId(leipzig), own.id,
        )
        for (edition in germanEditions) {
            val on = AppSettings(edition = edition, localPlaces = listOf(leipzig), customTopics = listOf(own))
            home.forEach { id ->
                assertEquals("${edition.key} $id", TopicSources.resolve(id, bothOff(on), includeDisabled = true), TopicSources.resolve(id, on, includeDisabled = true))
            }
        }
        listOf("top", "politik", "politik/inland", "wirtschaft/geld").forEach { assertFalse(it, TopicSources.mixes(Taxonomy.byId(it)!!)) }
        listOf("wirtschaft", "ki", "welt", "wissenschaft", "entertainment/games", "politik/usa").forEach { assertTrue(it, TopicSources.mixes(Taxonomy.byId(it)!!)) }
    }

    @Test
    fun `what joins stays a complement - at most half as many feeds as the edition reads itself`() {
        for (edition in germanEditions) {
            for (readableOnly in listOf(true, false)) {
                val settings = AppSettings(edition = edition, readableOnly = readableOnly)
                for (def in Taxonomy.ALL) {
                    val label = "${edition.key} ${def.id} readableOnly=$readableOnly"
                    val sources = TopicSources.resolve(def.id, settings, includeDisabled = true)
                    val english = sources.filter(::isEnglish)
                    val abroad = sources.filter { isAbroad(it, edition) }
                    val own = sources.size - english.size - abroad.size
                    assertTrue(label, english.size <= TopicSources.MAX_ENGLISH && 2 * english.size <= own + abroad.size)
                    assertTrue(label, abroad.size <= TopicSources.MAX_ABROAD && 2 * abroad.size <= own)
                    // English feeds for one country's readers stay with them.
                    english.forEach { assertTrue("$label ${it.id}", catalogue.getValue(it.id).countries.isEmpty()) }
                    if (!TopicSources.mixes(def)) assertTrue(label, english.isEmpty() && abroad.isEmpty())
                }
            }
        }
    }

    @Test
    fun `walled newsrooms are not mixed in when only readable articles are wanted`() {
        val blocked = KnownWalls.DOMAINS + "derstandard.at"
        val world = TopicSources.resolve("welt", germany, blocked = blocked).map { it.id }
        assertTrue(world.none { it.startsWith("derstandard") })
        // The next newsroom takes the place.
        assertTrue(world.containsAll(listOf("srf-international", "euronews-de")))
        val everything = TopicSources.resolve("welt", germany.copy(readableOnly = false), blocked = blocked).map { it.id }
        assertTrue("derstandard-international" in everything)
    }

    @Test
    fun `readers of English and of languages without publisher feeds get nothing mixed in`() {
        for (edition in listOf(NewsEdition.USA, NewsEdition("GB", "en"), NewsEdition("DE", "en"), NewsEdition("FR", "fr"))) {
            val settings = AppSettings(edition = edition)
            Taxonomy.ALL.forEach { def ->
                assertEquals("${edition.key} ${def.id}", TopicSources.resolve(def.id, bothOff(settings)), TopicSources.resolve(def.id, settings))
            }
            assertFalse(edition.key, TopicSources.readsEnglish(edition))
            assertFalse(edition.key, TopicSources.readsAbroad(edition))
        }
        germanEditions.forEach {
            assertTrue(it.key, TopicSources.readsEnglish(it))
            assertTrue(it.key, TopicSources.readsAbroad(it))
        }
    }

    @Test
    fun `an Austrian reader gets Austrian newsrooms at home and Swiss ones from abroad`() {
        val settings = AppSettings(edition = austria)
        val top = feeds(Topics.TOP_ID, settings).map { it.id }
        assertTrue(top.containsAll(listOf("derstandard", "orf", "diepresse")))
        assertTrue("tagesschau-top" !in top && "srf" !in top)
        val alone = feeds("welt", settings.copy(internationalSources = false)).map { it.id }
        assertTrue("derstandard-international" in alone && "srf-international" !in alone)
        assertTrue("srf-international" in feeds("welt", settings).map { it.id })
    }
}

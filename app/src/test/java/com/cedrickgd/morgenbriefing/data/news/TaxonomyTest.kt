package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicIcon
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.TopicStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaxonomyTest {

    @Test
    fun `ids are unique, sub-topics sit under an existing category`() {
        val ids = Taxonomy.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        Taxonomy.ALL.filterNot { it.isCategory }.forEach { sub ->
            val parent = Taxonomy.byId(sub.parentId!!)
            assertTrue(sub.id, parent != null && parent.isCategory)
            assertEquals(sub.id, "${sub.parentId}/", sub.id.substringBefore('/') + "/")
            assertTrue(sub.id, sub in Taxonomy.children(sub.parentId))
        }
        ids.forEach { assertFalse("ids are stored comma separated: $it", ',' in it) }
    }

    @Test
    fun `the old built-in ids are still categories`() {
        Topics.LEGACY_ORDER.forEach { assertTrue(it, Taxonomy.byId(it)?.isCategory == true) }
        assertEquals(12, Taxonomy.CATEGORIES.size)
        assertEquals(Taxonomy.CATEGORIES.map { it.id }, Topics.DEFAULT_ORDER)
    }

    @Test
    fun `the sub-topics of the plan exist`() {
        listOf(
            "wirtschaft/boerse", "wirtschaft/trading", "wirtschaft/krypto", "wirtschaft/konjunktur", "wirtschaft/unternehmen",
            "wirtschaft/geld", "wirtschaft/immobilien", "wirtschaft/startups", "politik/inland", "politik/wahlen", "politik/eu",
            "politik/usa", "politik/sicherheit", "welt/europa", "welt/amerika", "welt/asien", "welt/nahost", "welt/afrika",
            "ki/ki", "ki/smartphones", "ki/software", "ki/security", "ki/hardware", "ki/netz", "wissenschaft/raumfahrt",
            "wissenschaft/physik", "wissenschaft/natur", "wissenschaft/geschichte", "gesundheit/medizin", "gesundheit/ernaehrung",
            "gesundheit/psyche", "klima/klimawandel", "klima/energie", "klima/umwelt", "sport/fussball", "sport/motorsport",
            "sport/tennis", "sport/basketball", "sport/football", "sport/wintersport", "entertainment/games", "entertainment/film",
            "entertainment/musik", "entertainment/buecher", "entertainment/leute", "auto/elektro", "auto/verkehr",
        ).forEach { assertTrue(it, Taxonomy.byId(it) != null) }
    }

    @Test
    fun `a category's icon is its own, not one of its sub-topics'`() {
        assertEquals(TopicIcon.DEVICES, Taxonomy.byId("ki")?.icon)
        Taxonomy.CATEGORIES.forEach { category ->
            val children = Taxonomy.children(category.id).map { it.icon }
            assertTrue(category.id, category.icon !in children)
        }
    }

    @Test
    fun `every topic has a valid icon and a seed`() {
        Taxonomy.ALL.forEach { topic ->
            assertEquals(topic.id, topic.icon, TopicIcon.fromKey(topic.icon.name))
            assertTrue(topic.id, topic.seed ushr 24 == 0xFFL)
            assertTrue(topic.id, topic.emoji.isNotBlank())
        }
    }

    @Test
    fun `every topic can be read by section, top stories or searches in all five languages`() {
        Taxonomy.ALL.filter { it.id != Taxonomy.REGION }.forEach { topic ->
            assertTrue(topic.id, topic.topStories || topic.section != null || topic.queries.isNotEmpty())
            if (!topic.topStories) {
                for (lang in listOf("de", "en", "fr", "es", "it")) {
                    val query = topic.queries[lang]
                    assertTrue("${topic.id} $lang", !query.isNullOrBlank())
                    assertFalse("${topic.id} $lang carries no operators", query!!.contains("when:") || query.contains("site:"))
                    assertEquals("${topic.id} $lang: quotes are paired", 0, query.count { it == '"' } % 2)
                }
            }
        }
        // Sub-topics are searches: a section is the whole category.
        Taxonomy.ALL.filterNot { it.isCategory }.forEach { assertNull(it.id, it.section) }
    }

    @Test
    fun `queries follow the edition, with country refinements and an English fallback`() {
        val inland = Taxonomy.byId("politik/inland")!!
        assertTrue(inland.queryFor(NewsEdition.GERMANY)!!.contains("Bundestag"))
        assertTrue(inland.queryFor(NewsEdition("AT", "de"))!!.contains("Nationalrat"))
        assertTrue(inland.queryFor(NewsEdition("GB", "en"))!!.contains("Westminster"))
        assertTrue(inland.queryFor(NewsEdition.USA)!!.contains("Congress"))
        val krypto = Taxonomy.byId("wirtschaft/krypto")!!
        assertEquals(krypto.queries["en"], krypto.queryFor(NewsEdition("NL", "nl")))
        assertFalse(krypto.speaks(NewsEdition("NL", "nl")))
        assertTrue(krypto.speaks(NewsEdition("FR", "fr")))
    }

    @Test
    fun `fresh installs follow the essentials, the region only in Germany`() {
        val german = Taxonomy.defaultFollowed(NewsEdition.GERMANY)
        assertTrue(Taxonomy.REGION in german)
        assertTrue("wirtschaft/boerse" in german && Taxonomy.TOP in german)
        assertFalse(Taxonomy.REGION in Taxonomy.defaultFollowed(NewsEdition.USA))
        german.forEach { assertTrue(it, Taxonomy.isBuiltIn(it)) }
    }

    @Test
    fun `every built-in topic has a title and a subtitle in every language`() {
        for (lang in AppLanguage.entries) {
            val strings = TopicStrings.of(lang)
            (Taxonomy.ALL.map { it.id } + Topics.SEARCH_ID).forEach { id ->
                assertTrue("$lang $id title", !strings.title(id).isNullOrBlank())
                assertTrue("$lang $id subtitle", !strings.subtitle(id).isNullOrBlank())
            }
            assertTrue(strings.localSubtitle("Washington").contains("Washington"))
            assertTrue(strings.searchSubtitle("Midterms").contains("Midterms"))
            assertNull(strings.title("gibts/nicht"))
            // Titles within one language are distinct, so two tiles never read the same.
            val titles = Taxonomy.ALL.map { strings.title(it.id) }
            assertEquals("$lang", titles.size, titles.distinct().size)
        }
        assertEquals("Börse & Märkte", TopicStrings.of(AppLanguage.DE).title("wirtschaft/boerse"))
        assertEquals("Stock market", TopicStrings.of(AppLanguage.EN).title("wirtschaft/boerse"))
    }

    @Test
    fun `built-in titles follow the interface language, own topics keep theirs`() {
        val own = Topic("custom:1", "Mein Thema", "🦉", 0xFF123456, custom = true)
        val settings = AppSettings(customTopics = listOf(own), followed = listOf("sport/football", "custom:1"))
        assertEquals("American Football", Topics.resolve("sport/football", settings, AppLanguage.DE)?.title)
        assertEquals("Football américain", Topics.resolve("sport/football", settings, AppLanguage.FR)?.title)
        assertEquals("Mein Thema", Topics.resolve("custom:1", settings, AppLanguage.EN)?.title)
        assertEquals(TopicIcon.FOOTBALL.name, Topics.resolve("sport/football", settings, AppLanguage.EN)?.icon)
        assertEquals("sport", Topics.resolve("sport/football", settings, AppLanguage.EN)?.parentId)
        assertEquals(listOf("sport/football", "custom:1"), Topics.visible(settings, AppLanguage.EN).map { it.id })
        assertEquals(listOf("Calcio", "Motori", "Tennis", "Basket", "Football americano", "Sport invernali"), Topics.children("sport", AppLanguage.IT).map { it.title })
    }

    @Test
    fun `places get stable distinct ids`() {
        val dc = Place("Washington", 38.895, -77.036, "District of Columbia, Vereinigte Staaten", "US")
        val state = Place("Washington", 47.38, -120.45, "Washington, Vereinigte Staaten", "US")
        val id = Topics.localId(dc)
        assertTrue(id, id.startsWith("local:washington-us-"))
        assertEquals(id, Topics.localId(dc.copy(region = "anders")))
        assertNotEquals(id, Topics.localId(state))
        assertTrue(Topics.localId(Place("Bad Düben", 51.59, 12.58, "", "DE")).startsWith("local:bad-duben-de-"))
        val settings = AppSettings(localPlaces = listOf(dc), followed = listOf(id), uiLanguage = AppLanguage.DE)
        val topic = Topics.resolve(id, settings)!!
        assertEquals("Washington", topic.title)
        assertEquals(TopicIcon.LOCATION.name, topic.icon)
        assertEquals("Lokale Nachrichten aus Washington", Topics.subtitle(topic, settings))
        assertNull(Topics.resolve("local:weg-xx-0000", settings))
    }

    @Test
    fun `settings without followed topics show the old order, as version 2 did`() {
        val settings = AppSettings(topicOrder = listOf("sport", "politik", "ki"), hiddenTopics = setOf("politik"))
        val ids = Topics.followedIds(settings)
        assertEquals(listOf("sport", "ki"), ids.take(2))
        assertFalse("politik" in ids)
        assertEquals(Topics.DEFAULT_ORDER, Topics.followedIds(AppSettings()))
    }
}

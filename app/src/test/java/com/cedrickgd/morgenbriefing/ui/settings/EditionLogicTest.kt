package com.cedrickgd.morgenbriefing.ui.settings

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class EditionLogicTest {

    @Test
    fun `flags are built from the country code`() {
        assertEquals("🇩🇪", flagEmoji("DE"))
        assertEquals("🇺🇸", flagEmoji(" us "))
        assertEquals("", flagEmoji(""))
        assertEquals("", flagEmoji("USA"))
        assertEquals("", flagEmoji("1A"))
    }

    @Test
    fun `countries are named in the interface language, languages in themselves`() {
        assertEquals("Deutschland", EditionLabels.country("DE", AppLanguage.DE))
        assertEquals("Germany", EditionLabels.country("DE", AppLanguage.EN))
        assertEquals("Allemagne", EditionLabels.country("DE", AppLanguage.FR))
        assertEquals("Alemania", EditionLabels.country("DE", AppLanguage.ES))
        assertEquals("Germania", EditionLabels.country("DE", AppLanguage.IT))
        assertEquals("Deutsch", EditionLabels.nativeLanguage(NewsEdition.GERMANY))
        assertEquals("English", EditionLabels.nativeLanguage(NewsEdition.USA))
        assertEquals("Français", EditionLabels.nativeLanguage(NewsEdition("FR", "fr")))
        assertEquals("Español", EditionLabels.nativeLanguage(NewsEdition("ES", "es")))
        assertEquals("Englisch", EditionLabels.languageIn(NewsEdition.USA, AppLanguage.DE))
    }

    @Test
    fun `every edition gets a flag, a country name and a language name`() {
        for (lang in AppLanguage.entries) {
            val options = EditionLabels.options(lang)
            assertEquals(NewsEdition.ALL, options.map { it.edition })
            options.forEach { option ->
                assertTrue("${option.edition.key} $lang", option.flag.isNotEmpty())
                assertTrue("${option.edition.key} $lang", option.country.isNotBlank() && option.country != option.edition.country)
                assertTrue("${option.edition.key} $lang", option.language.isNotBlank() && option.language.first().isUpperCase() || option.edition.language == "ja")
            }
        }
    }

    @Test
    fun `the edition search finds countries and languages in several tongues`() {
        val options = EditionLabels.options(AppLanguage.DE)
        fun keys(query: String) = EditionSearch.filter(options, query, AppLanguage.DE).map { it.edition.key }
        assertEquals(NewsEdition.ALL.size, keys("  ").size)
        assertEquals(listOf("US:en"), keys("vereinigte staaten"))
        assertEquals(listOf("US:en", "GB:en"), keys("vereinigte"))
        assertEquals(listOf("US:en"), keys("United States"))
        assertEquals(listOf("AT:de"), keys("osterreich"))
        assertTrue("CH:de" in keys("schweiz") && "CH:fr" in keys("schweiz"))
        assertTrue(keys("français").containsAll(listOf("FR:fr", "BE:fr", "CH:fr")))
        assertTrue(keys("englisch").containsAll(listOf("US:en", "GB:en", "IN:en")))
        assertEquals(listOf("ES:es"), keys("espana"))
        assertTrue(keys("xyz").isEmpty())
    }

    @Test
    fun `suggestions start with the current edition and fit the phone`() {
        val suggested = EditionSearch.suggestions(NewsEdition.USA, Locale.GERMANY, AppLanguage.DE)
        assertEquals(NewsEdition.USA, suggested.first())
        assertEquals(NewsEdition.GERMANY, suggested[1])
        assertTrue(NewsEdition("AT", "de") in suggested)
        assertEquals(suggested.distinct(), suggested)
        assertTrue(suggested.size <= EditionSearch.MAX_SUGGESTIONS)
        val french = EditionSearch.suggestions(NewsEdition("FR", "fr"), Locale.FRANCE, AppLanguage.FR)
        assertEquals(listOf("FR:fr", "BE:fr", "CH:fr"), french.map { it.key })
    }

    @Test
    fun `picking the phone's language follows the phone`() {
        assertNull(LanguageChoice.preference(null, Locale.GERMANY))
        assertNull(LanguageChoice.preference(AppLanguage.DE, Locale.GERMANY))
        assertEquals(AppLanguage.EN, LanguageChoice.preference(AppLanguage.EN, Locale.GERMANY))
        // A Polish phone shows English, but picking English pins it (Polish is not ours).
        assertEquals(AppLanguage.EN, LanguageChoice.preference(AppLanguage.EN, Locale.forLanguageTag("pl-PL")))
        assertEquals(listOf(null) + AppLanguage.entries, LanguageChoice.OPTIONS)
    }

    @Test
    fun `a new edition moves the default topics along, an arranged feed stays`() {
        val fresh = AppSettings(edition = NewsEdition.GERMANY, followed = Taxonomy.defaultFollowed(NewsEdition.GERMANY))
        val american = EditionChange.apply(fresh, NewsEdition.USA)
        assertEquals(NewsEdition.USA, american.edition)
        assertEquals(Taxonomy.defaultFollowed(NewsEdition.USA), american.followed)
        assertFalse(Topics.REGION_ID in american.followed)
        assertTrue(Topics.REGION_ID in EditionChange.apply(american, NewsEdition.GERMANY).followed)

        val arranged = fresh.copy(followed = listOf("sport", "ki"))
        assertEquals(listOf("sport", "ki"), EditionChange.apply(arranged, NewsEdition.USA).followed)
        assertSame(arranged, EditionChange.apply(arranged, NewsEdition.GERMANY))
        assertTrue(EditionChange.showsRegionPresets(NewsEdition.GERMANY))
        assertFalse(EditionChange.showsRegionPresets(NewsEdition("AT", "de")))
    }

    @Test
    fun `a place abroad reads the news of its own country`() {
        val washington = Place("Washington", 38.8951, -77.0364, "District of Columbia, United States", "US")
        val leipzig = Place.LEIPZIG
        val settings = AppSettings(
            edition = NewsEdition.GERMANY,
            localPlaces = listOf(washington, leipzig),
            followed = listOf("top", Topics.localId(washington)),
        )
        val rows = LocalPlaces.rows(settings)
        assertEquals(listOf("Washington", "Leipzig"), rows.map { it.place.name })
        assertEquals(NewsEdition.USA, rows[0].edition)
        assertTrue(rows[0].followed)
        assertEquals(NewsEdition.GERMANY, rows[1].edition)
        assertFalse(rows[1].followed)
    }

    @Test
    fun `the quick picks fit the target`() {
        assertEquals(WorldPlaces, quickPlacesFor(PlaceTarget.LOCAL, germanEdition = true))
        assertEquals(QuickPlaces, quickPlacesFor(PlaceTarget.HOME, germanEdition = true))
        assertEquals(WorldPlaces, quickPlacesFor(PlaceTarget.DAY, germanEdition = false))
        assertTrue(WorldPlaces.all { flagEmoji(it.countryCode).isNotEmpty() })
    }
}

package com.cedrickgd.morgenbriefing.ui.settings

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.RegionStrings
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
        assertEquals("Deutsch", EditionLabels.nativeLanguage("de"))
        assertEquals("English", EditionLabels.nativeLanguage("en"))
        assertEquals("Français", EditionLabels.nativeLanguage("fr"))
        assertEquals("Español", EditionLabels.nativeLanguage("es"))
        assertEquals("Englisch", EditionLabels.languageIn("en", AppLanguage.DE))
    }

    @Test
    fun `a language is named as a label and as a sentence writes it`() {
        assertEquals("Deutsch", EditionLabels.languageInSentence("de", AppLanguage.DE))
        assertEquals("German", EditionLabels.languageInSentence("de", AppLanguage.EN))
        assertEquals("allemand", EditionLabels.languageInSentence("de", AppLanguage.FR))
        assertEquals("alemán", EditionLabels.languageInSentence("de", AppLanguage.ES))
        assertEquals("tedesco", EditionLabels.languageInSentence("de", AppLanguage.IT))
        assertEquals("Allemand", EditionLabels.languageIn("de", AppLanguage.FR))
        // The hint under a pair Google News has no edition of names both.
        val hint = RegionStrings.of(AppLanguage.DE).newsSearchedHint("Vereinigte Staaten", "Deutsch")
        assertTrue(hint, hint.startsWith("Vereinigte Staaten auf Deutsch"))
    }

    @Test
    fun `every region and every news language gets its names in every interface language`() {
        for (lang in AppLanguage.entries) {
            val regions = EditionLabels.regions(lang)
            assertEquals(NewsEdition.COUNTRIES, regions.map { it.country })
            regions.forEach { option ->
                assertTrue("${option.country} $lang", option.flag.isNotEmpty())
                assertTrue("${option.country} $lang", option.name.isNotBlank() && option.name != option.country)
            }
            val languages = EditionLabels.languages(lang)
            assertEquals(NewsEdition.LANGUAGES, languages.map { it.language })
            languages.forEach { option ->
                assertTrue("${option.language} $lang", option.nativeName.isNotBlank() && option.nativeName != option.language)
                assertTrue("${option.language} $lang", option.nativeName.first().isUpperCase() || option.language == "ja")
                assertTrue("${option.language} $lang", option.name.isNotBlank() && option.name != option.language)
            }
            val option = EditionLabels.option(NewsEdition("US", "de"), lang)
            assertEquals("🇺🇸", option.flag)
            assertEquals("Deutsch", option.language)
        }
    }

    @Test
    fun `the region search finds countries in several tongues`() {
        val options = EditionLabels.regions(AppLanguage.DE)
        fun found(query: String) = EditionSearch.filterRegions(options, query).map { it.country }
        assertEquals(NewsEdition.COUNTRIES, found("  "))
        assertEquals(listOf("US"), found("vereinigte staaten"))
        assertEquals(listOf("US", "GB"), found("vereinigte"))
        assertEquals(listOf("US"), found("United States"))
        assertEquals(listOf("AT"), found("osterreich"))
        // A country is found in each of its own languages: Switzerland in German and French, Belgium in Dutch.
        assertEquals(listOf("CH"), found("schweiz"))
        assertEquals(listOf("CH"), found("suisse"))
        assertEquals(listOf("BE"), found("belgie"))
        assertEquals(listOf("ES"), found("espana"))
        assertEquals(listOf("JP"), found("jp"))
        assertTrue(found("xyz").isEmpty())
    }

    @Test
    fun `region suggestions start with the current region and fit the phone and the news language`() {
        val suggested = EditionSearch.regionSuggestions(NewsEdition("US", "de"), Locale.GERMANY)
        assertEquals(listOf("US", "DE", "AT", "CH"), suggested)
        val french = EditionSearch.regionSuggestions(NewsEdition("FR", "fr"), Locale.FRANCE)
        assertEquals(listOf("FR", "BE", "CH", "CA"), french)
        val english = EditionSearch.regionSuggestions(NewsEdition.USA, Locale.forLanguageTag("nb-NO"))
        assertEquals("US", english.first())
        assertEquals(english.distinct(), english)
        assertEquals(EditionSearch.MAX_SUGGESTIONS, english.size)
    }

    @Test
    fun `the language picker lists a region's own languages first, then every other`() {
        val options = EditionLabels.languages(AppLanguage.DE)
        val (own, others) = EditionSearch.languagesFor("CH", options)
        assertEquals(listOf("de", "fr"), own.map { it.language })
        assertEquals(NewsEdition.LANGUAGES - setOf("de", "fr"), others.map { it.language })
        val (american, rest) = EditionSearch.languagesFor("US", options)
        assertEquals(listOf("en", "es"), american.map { it.language })
        assertTrue("de" in rest.map { it.language })
        // Every language is offered for every region, once.
        for (country in NewsEdition.COUNTRIES) {
            val (first, second) = EditionSearch.languagesFor(country, options)
            assertTrue(country, first.isNotEmpty())
            assertEquals(country, NewsEdition.LANGUAGES.toSet(), (first + second).map { it.language }.toSet())
            assertEquals(country, NewsEdition.LANGUAGES.size, first.size + second.size)
        }
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
    fun `a new region moves the default topics along, an arranged feed stays`() {
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
        // The Bundesland broadcasters are German: not for Germany read in English.
        assertFalse(EditionChange.showsRegionPresets(NewsEdition("DE", "en")))
    }

    @Test
    fun `region and language are picked independently, any country with any language`() {
        val start = AppSettings(edition = NewsEdition.GERMANY, uiLanguage = AppLanguage.DE, followed = listOf("sport", "ki"))
        // Region USA, the news stay German (a pair Google News has no edition of).
        val usaInGerman = EditionChange.region(start, "US")
        assertEquals(NewsEdition("US", "de"), usaInGerman.edition)
        assertFalse(usaInGerman.edition.isNative)
        // Region Germany, news in English.
        val germanyInEnglish = EditionChange.language(start, "en")
        assertEquals(NewsEdition("DE", "en"), germanyInEnglish.edition)
        // Neither choice touches the app language or an arranged feed.
        for (changed in listOf(usaInGerman, germanyInEnglish)) {
            assertEquals(AppLanguage.DE, changed.uiLanguage)
            assertEquals(start.followed, changed.followed)
        }
        // Every pair can be reached and is stored under its own key.
        for (country in NewsEdition.COUNTRIES) {
            for (language in NewsEdition.LANGUAGES) {
                val picked = EditionChange.language(EditionChange.region(start, country), language)
                assertEquals(NewsEdition(country, language), picked.edition)
                assertEquals(picked.edition, NewsEdition.fromKey(picked.edition.key))
            }
        }
        // What the app does not offer changes nothing.
        assertSame(start, EditionChange.region(start, "XX"))
        assertSame(start, EditionChange.language(start, "xx"))
        assertSame(start, EditionChange.language(start, "de"))
    }

    @Test
    fun `another news language alone never touches the followed topics`() {
        val fresh = AppSettings(edition = NewsEdition.GERMANY, followed = Taxonomy.defaultFollowed(NewsEdition.GERMANY))
        assertTrue(Topics.REGION_ID in fresh.followed)
        // The default feed keeps its "Region" topic (it reads the home place where the presets do not apply) …
        val english = EditionChange.language(fresh, "en")
        assertEquals(fresh.followed, english.followed)
        // … and is still the default feed: back in German nothing changed, another region moves it along.
        assertEquals(fresh, EditionChange.language(english, "de"))
        assertEquals(Taxonomy.defaultFollowed(NewsEdition.USA), EditionChange.region(english, "US").followed)
        // A feed with the user's own topics stays whatever changes.
        val place = Place("Washington", 38.8951, -77.0364, "District of Columbia, United States", "US")
        val own = fresh.copy(localPlaces = listOf(place), followed = fresh.followed + Topics.localId(place))
        assertEquals(own.followed, EditionChange.language(own, "fr").followed)
        assertEquals(own.followed, EditionChange.region(own, "FR").followed)
    }

    @Test
    fun `a place abroad is read in the news language, with its own country as the region`() {
        val washington = Place("Washington", 38.8951, -77.0364, "District of Columbia, United States", "US")
        val leipzig = Place.LEIPZIG
        val settings = AppSettings(
            edition = NewsEdition.GERMANY,
            localPlaces = listOf(washington, leipzig),
            followed = listOf("top", Topics.localId(washington)),
        )
        val rows = LocalPlaces.rows(settings)
        assertEquals(listOf("Washington", "Leipzig"), rows.map { it.place.name })
        assertEquals(NewsEdition("US", "de"), rows[0].edition)
        assertTrue(rows[0].followed)
        assertEquals(NewsEdition.GERMANY, rows[1].edition)
        assertFalse(rows[1].followed)
        // A reader of English news gets both places in English.
        val english = LocalPlaces.rows(settings.copy(edition = NewsEdition("DE", "en")))
        assertEquals(listOf(NewsEdition.USA, NewsEdition("DE", "en")), english.map { it.edition })
    }

    @Test
    fun `the quick picks fit the target`() {
        assertEquals(WorldPlaces, quickPlacesFor(PlaceTarget.LOCAL, germanEdition = true))
        assertEquals(QuickPlaces, quickPlacesFor(PlaceTarget.HOME, germanEdition = true))
        assertEquals(WorldPlaces, quickPlacesFor(PlaceTarget.DAY, germanEdition = false))
        assertTrue(WorldPlaces.all { flagEmoji(it.countryCode).isNotEmpty() })
    }
}

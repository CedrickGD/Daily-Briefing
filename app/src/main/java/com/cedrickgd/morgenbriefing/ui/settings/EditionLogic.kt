package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.Regions
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import java.text.Normalizer
import java.util.Locale

/*
 * Pure logic of "Sprache & Region": three independent choices – the app language, the news
 * region (a country) and the news language. How regions and languages are named in the
 * interface language, the two pickers' search and suggestions, and what changing the region
 * or the language does to the followed topics. Unit-tested.
 */

/** 🇩🇪 for "DE"; empty for unknown codes. */
internal fun flagEmoji(countryCode: String): String {
    val code = countryCode.trim().uppercase(Locale.ROOT)
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
    return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

/** A region and a news language together, worded in the interface language (a place's news, the sources' header). */
@Immutable
internal data class EditionOption(
    val edition: NewsEdition,
    val flag: String,
    /** The country in the interface language: "Vereinigte Staaten". */
    val country: String,
    /** The news language in itself: "English", "Français". */
    val language: String,
)

/** One row of the region picker. */
@Immutable
internal data class RegionOption(
    /** ISO 3166-1 alpha-2. */
    val country: String,
    val flag: String,
    /** The country in the interface language: "Vereinigte Staaten". */
    val name: String,
)

/** One row of the news-language picker. */
@Immutable
internal data class NewsLanguageOption(
    /** ISO 639-1. */
    val language: String,
    /** The language in itself: "English", "Français". */
    val nativeName: String,
    /** The language in the interface language: "Englisch". */
    val name: String,
)

internal object EditionLabels {

    /** [country] (ISO alpha-2) named in [lang]; the code itself when the platform knows no name. */
    fun country(country: String, lang: AppLanguage): String {
        val code = country.uppercase(Locale.ROOT)
        val name = regionLocale(code)?.getDisplayCountry(lang.locale).orEmpty()
        return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: code
    }

    /** [language] (ISO 639-1) in itself, capitalised: "Deutsch", "Français", "日本語". */
    fun nativeLanguage(language: String): String {
        val locale = languageLocale(language)
        val name = locale.getDisplayLanguage(locale)
        return name.takeIf { it.isNotBlank() && it != language }?.capitalized(locale) ?: language
    }

    /** [language] named in [lang], capitalised for a label: "Englisch", "Anglais". */
    fun languageIn(language: String, lang: AppLanguage): String =
        languageInSentence(language, lang).capitalized(lang.locale)

    /** [language] named in [lang] as a sentence writes it: "Englisch", but "anglais", "inglés", "inglese". */
    fun languageInSentence(language: String, lang: AppLanguage): String {
        val name = languageLocale(language).getDisplayLanguage(lang.locale)
        return name.takeIf { it.isNotBlank() && it != language } ?: language
    }

    fun option(edition: NewsEdition, lang: AppLanguage): EditionOption =
        EditionOption(edition, flagEmoji(edition.country), country(edition.country, lang), nativeLanguage(edition.language))

    /** Every region in [NewsEdition.COUNTRIES] order, worded in [lang]. */
    fun regions(lang: AppLanguage): List<RegionOption> = NewsEdition.COUNTRIES.map { region(it, lang) }

    fun region(country: String, lang: AppLanguage): RegionOption = RegionOption(country, flagEmoji(country), country(country, lang))

    /** Every news language in [NewsEdition.LANGUAGES] order, worded in [lang]. */
    fun languages(lang: AppLanguage): List<NewsLanguageOption> = NewsEdition.LANGUAGES.map { language(it, lang) }

    fun language(language: String, lang: AppLanguage): NewsLanguageOption =
        NewsLanguageOption(language, nativeLanguage(language), languageIn(language, lang))

    /** A locale of just [code]'s country; null for a code the platform rejects. */
    internal fun regionLocale(code: String): Locale? = runCatching { Locale.Builder().setRegion(code).build() }.getOrNull()

    internal fun languageLocale(code: String): Locale = runCatching { Locale.Builder().setLanguage(code).build() }.getOrDefault(Locale.ROOT)

    private fun String.capitalized(locale: Locale): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

internal object EditionSearch {

    /**
     * The regions matching [query]: the country in the interface language, in English or in
     * one of its own languages, or its code ("us"). Accents and case are ignored; a blank
     * query keeps everything.
     */
    fun filterRegions(options: List<RegionOption>, query: String): List<RegionOption> {
        val needle = fold(query)
        if (needle.isEmpty()) return options
        return options.filter { option -> regionHaystack(option).any { it.contains(needle) } }
    }

    /**
     * A handful of regions that fit: the current one, the phone's country and the countries
     * Google News has an edition of in the current news language. At most [MAX_SUGGESTIONS].
     */
    fun regionSuggestions(current: NewsEdition, phone: Locale): List<String> {
        val picks = LinkedHashSet<String>()
        picks += current.country
        phone.country.uppercase(Locale.ROOT).takeIf { it in NewsEdition.COUNTRIES }?.let { picks += it }
        NewsEdition.ALL.filter { it.language == current.language }.mapTo(picks) { it.country }
        return picks.take(MAX_SUGGESTIONS)
    }

    /**
     * The news languages split for [country]: first the ones Google News has an edition of the
     * country in (its own languages, the main one first), then all the others.
     */
    fun languagesFor(country: String, options: List<NewsLanguageOption>): Pair<List<NewsLanguageOption>, List<NewsLanguageOption>> {
        val own = NewsEdition.languagesOf(country)
        val byCode = options.associateBy { it.language }
        return own.mapNotNull { byCode[it] } to options.filter { it.language !in own }
    }

    const val MAX_SUGGESTIONS = 5

    private fun regionHaystack(option: RegionOption): List<String> {
        val region = EditionLabels.regionLocale(option.country)
        val own = NewsEdition.languagesOf(option.country).map { region?.getDisplayCountry(EditionLabels.languageLocale(it)).orEmpty() }
        return (listOf(option.name, EditionLabels.country(option.country, AppLanguage.EN), option.country) + own).map(::fold)
    }

    /** Lower case without accents: "Österreich" → "osterreich", "España" → "espana". */
    internal fun fold(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
}

/** The app language choice: null follows the phone. */
internal object LanguageChoice {

    /** The options in picker order: "Wie das Handy", then every language. */
    val OPTIONS: List<AppLanguage?> = listOf<AppLanguage?>(null) + AppLanguage.entries

    /**
     * The stored preference after picking [picked] on a phone set to [phone]: picking the phone's
     * own language means "follow the phone" (null), so changing the phone later carries the app
     * along; any other language is pinned.
     */
    fun preference(picked: AppLanguage?, phone: Locale = Locale.getDefault()): AppLanguage? =
        if (picked == null || picked == AppLanguage.system(phone) && AppLanguage.fromCode(phone.language) != null) null else picked
}

internal object EditionChange {

    /** [settings] with the news region [country]; the news language stays ([apply]). */
    fun region(settings: AppSettings, country: String): AppSettings =
        NewsEdition.of(country, settings.edition.language)?.let { apply(settings, it) } ?: settings

    /** [settings] with the news language [language]; the region and the followed topics stay ([apply]). */
    fun language(settings: AppSettings, language: String): AppSettings =
        NewsEdition.of(settings.edition.country, language)?.let { apply(settings, it) } ?: settings

    /**
     * [settings] reading [edition]. Another language alone changes nothing else: the topics
     * are the same in every language. Another region moves a feed that still is the default
     * one to the defaults of the new edition (the "Region" topic belongs where its presets
     * are: Germany in German); a feed the user arranged stays as it is.
     */
    fun apply(settings: AppSettings, edition: NewsEdition): AppSettings {
        if (edition == settings.edition) return settings
        val next = settings.copy(edition = edition)
        if (edition.country == settings.edition.country) return next
        return if (isDefaultFeed(Topics.followedIds(settings))) next.copy(followed = Taxonomy.defaultFollowed(edition)) else next
    }

    /** Whether [followed] is a default feed, with or without the "Region" topic (a language change keeps it). */
    private fun isDefaultFeed(followed: List<String>): Boolean =
        followed - Taxonomy.REGION == Taxonomy.defaultFollowed(NewsEdition.GERMANY) - Taxonomy.REGION

    /** Whether the Bundesland presets apply to [edition] (Germany in German only). */
    fun showsRegionPresets(edition: NewsEdition): Boolean = Regions.servesEdition(edition)
}

/** A followed place as "Sprache & Region" lists it. */
@Immutable
internal data class LocalPlaceRow(
    val topicId: String,
    val place: Place,
    /** The edition its news are read in: its country in the news language, else the main edition. */
    val edition: NewsEdition,
    val followed: Boolean,
)

internal object LocalPlaces {

    /** The places of [settings] in their stored order, with their edition and whether the feed shows them. */
    fun rows(settings: AppSettings): List<LocalPlaceRow> {
        val followed = Topics.followedIds(settings).toSet()
        return settings.localPlaces.map { place ->
            val id = Topics.localId(place)
            LocalPlaceRow(id, place, TopicSources.editionFor(id, settings), id in followed)
        }
    }
}

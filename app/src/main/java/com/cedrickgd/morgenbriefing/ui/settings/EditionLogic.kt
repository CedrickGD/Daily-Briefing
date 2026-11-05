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
 * Pure logic of "Sprache & Region": how a news edition is named in the interface language, the
 * edition picker's search and suggestions, the app-language choice and what changing the
 * edition does to the followed topics. Unit-tested.
 */

/** 🇩🇪 for "DE"; empty for unknown codes. */
internal fun flagEmoji(countryCode: String): String {
    val code = countryCode.trim().uppercase(Locale.ROOT)
    if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
    return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

/** One row of the edition picker, already worded in the interface language. */
@Immutable
internal data class EditionOption(
    val edition: NewsEdition,
    val flag: String,
    /** The country in the interface language: "Vereinigte Staaten". */
    val country: String,
    /** The edition's language in itself: "English", "Français". */
    val language: String,
)

internal object EditionLabels {

    /** [country] (ISO alpha-2) named in [lang]; the code itself when the platform knows no name. */
    fun country(country: String, lang: AppLanguage): String {
        val code = country.uppercase(Locale.ROOT)
        val name = regionLocale(code)?.getDisplayCountry(lang.locale).orEmpty()
        return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: code
    }

    /** The edition's language in itself, capitalised: "Deutsch", "Français", "日本語". */
    fun nativeLanguage(edition: NewsEdition): String {
        val locale = languageLocale(edition.language)
        val name = locale.getDisplayLanguage(locale)
        return name.takeIf { it.isNotBlank() && it != edition.language }?.capitalized(locale) ?: edition.language
    }

    /** The edition's language named in [lang]: "Englisch". */
    fun languageIn(edition: NewsEdition, lang: AppLanguage): String {
        val name = languageLocale(edition.language).getDisplayLanguage(lang.locale)
        return name.takeIf { it.isNotBlank() && it != edition.language }?.capitalized(lang.locale) ?: edition.language
    }

    fun option(edition: NewsEdition, lang: AppLanguage): EditionOption =
        EditionOption(edition, flagEmoji(edition.country), country(edition.country, lang), nativeLanguage(edition))

    /** Every edition in [NewsEdition.ALL] order, worded in [lang]. */
    fun options(lang: AppLanguage): List<EditionOption> = NewsEdition.ALL.map { option(it, lang) }

    /** A locale of just [code]'s country; null for a code the platform rejects. */
    internal fun regionLocale(code: String): Locale? = runCatching { Locale.Builder().setRegion(code).build() }.getOrNull()

    private fun languageLocale(code: String): Locale = runCatching { Locale.Builder().setLanguage(code).build() }.getOrDefault(Locale.ROOT)

    private fun String.capitalized(locale: Locale): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}

internal object EditionSearch {

    /**
     * The options matching [query]: the country in the interface language, in English or in the
     * edition's own language, the language's names, or the codes ("us", "en-US"). Accents and
     * case are ignored; a blank query keeps everything.
     */
    fun filter(options: List<EditionOption>, query: String, lang: AppLanguage): List<EditionOption> {
        val needle = fold(query)
        if (needle.isEmpty()) return options
        return options.filter { option -> haystack(option, lang).any { it.contains(needle) } }
    }

    /**
     * A handful of editions that fit this phone: the current one, the phone's country, the
     * editions in the interface language and the phone language. At most [MAX_SUGGESTIONS].
     */
    fun suggestions(current: NewsEdition, phone: Locale, lang: AppLanguage): List<NewsEdition> {
        val picks = LinkedHashSet<NewsEdition>()
        picks += current
        NewsEdition.default(phone).let { picks += it }
        NewsEdition.ALL.filterTo(picks) { it.language == lang.code }
        NewsEdition.ALL.filterTo(picks) { it.language == phone.language }
        return picks.take(MAX_SUGGESTIONS)
    }

    const val MAX_SUGGESTIONS = 5

    private fun haystack(option: EditionOption, lang: AppLanguage): List<String> {
        val edition = option.edition
        return listOf(
            option.country,
            option.language,
            EditionLabels.country(edition.country, AppLanguage.EN),
            EditionLabels.regionLocale(edition.country)?.getDisplayCountry(edition.locale).orEmpty(),
            EditionLabels.languageIn(edition, lang),
            EditionLabels.languageIn(edition, AppLanguage.EN),
            edition.country,
            edition.hl,
        ).map(::fold)
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

    /**
     * [settings] reading [edition]. A feed still on the defaults of the old edition gets the
     * defaults of the new one (the "Region" topic exists only where its presets do); a feed the
     * user arranged stays as it is.
     */
    fun apply(settings: AppSettings, edition: NewsEdition): AppSettings {
        if (edition == settings.edition) return settings
        val followed = Topics.followedIds(settings)
        val untouched = followed == Taxonomy.defaultFollowed(settings.edition)
        val next = settings.copy(edition = edition)
        return if (untouched) next.copy(followed = Taxonomy.defaultFollowed(edition)) else next
    }

    /** Whether the Bundesland presets apply to [edition] (German edition only). */
    fun showsRegionPresets(edition: NewsEdition): Boolean = Regions.servesEdition(edition)
}

/** A followed place as "Sprache & Region" lists it. */
@Immutable
internal data class LocalPlaceRow(
    val topicId: String,
    val place: Place,
    /** The edition its news are read in: its country's, else the main edition. */
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

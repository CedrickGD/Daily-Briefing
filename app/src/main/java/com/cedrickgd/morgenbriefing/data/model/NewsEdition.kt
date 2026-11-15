package com.cedrickgd.morgenbriefing.data.model

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Where the news are about and which language they are in: a region (country) plus a language,
 * chosen independently of each other and of the app's interface language
 * ([com.cedrickgd.morgenbriefing.i18n.AppLanguage]). Every pair of [COUNTRIES] × [LANGUAGES] is
 * valid: Germany in German, the USA in German, Germany in English.
 *
 * Google News publishes an edition of its own for some pairs ([ALL], `hl=en-US&gl=US&ceid=US:en`).
 * For every other pair it silently answers with the home edition of the language – `US:de`
 * gives the German top stories of Germany, the country is dropped (checked 15.11.2026). Such a
 * pair ([isNative] = false) therefore reads the edition of its language ([google]) and finds its
 * country by name (see [com.cedrickgd.morgenbriefing.data.news.TopicSources]).
 *
 * A local-news place abroad (Washington, D.C. while the region is Germany) is read in the
 * user's language with the place's country as the region.
 */
@Serializable
data class NewsEdition(
    /** ISO 3166-1 alpha-2, upper case: `DE`, `US`. */
    val country: String,
    /** ISO 639-1, lower case: `de`, `en`. */
    val language: String,
) {
    /** Whether Google News has an edition of its own for this country in this language. */
    val isNative: Boolean get() = this in ALL

    /**
     * The Google News edition this pair is read from: itself when Google News has it, else the
     * home edition of its language (`US:de` → `DE:de`).
     */
    val google: NewsEdition get() = if (isNative) this else home(language) ?: this

    /** Google News `hl` parameter: `en-US`, `de` (of [google], so it never names a pair Google News lacks). */
    val hl: String get() = google.let { if (it.language == "de" && it.country == "DE") "de" else "${it.language}-${it.country}" }

    /** Google News `gl` parameter: the country of [google]. */
    val gl: String get() = google.country

    /** Google News `ceid` parameter: `US:en` (of [google]). */
    val ceid: String get() = google.let { "${it.country}:${it.language}" }

    /** Stable storage key: `US:en`, `US:de`. */
    val key: String get() = "$country:$language"

    /** Accept-Language header value for requests in this edition. */
    val acceptLanguage: String get() = "$language-$country,$language;q=0.9,en;q=0.5"

    val locale: Locale get() = Locale.Builder().setLanguage(language).setRegion(country).build()

    companion object {
        val GERMANY = NewsEdition("DE", "de")
        val USA = NewsEdition("US", "en")

        /**
         * The editions Google News publishes itself among the countries and languages the app
         * offers: a country's main language comes first of its editions, a language's home
         * country first of the language's. Curated publisher feeds exist for some (see
         * FeedCatalog). French Canada, Dutch-speaking Belgium and the Spanish edition of the USA
         * stay in their country like the others (checked 15.11.2026).
         */
        val ALL: List<NewsEdition> = listOf(
            GERMANY,
            NewsEdition("AT", "de"),
            NewsEdition("CH", "de"),
            USA,
            NewsEdition("GB", "en"),
            NewsEdition("CA", "en"),
            NewsEdition("AU", "en"),
            NewsEdition("IE", "en"),
            NewsEdition("IN", "en"),
            NewsEdition("FR", "fr"),
            NewsEdition("BE", "fr"),
            NewsEdition("CH", "fr"),
            NewsEdition("CA", "fr"),
            NewsEdition("ES", "es"),
            NewsEdition("MX", "es"),
            NewsEdition("AR", "es"),
            NewsEdition("US", "es"),
            NewsEdition("IT", "it"),
            NewsEdition("NL", "nl"),
            NewsEdition("BE", "nl"),
            NewsEdition("PL", "pl"),
            NewsEdition("PT", "pt"),
            NewsEdition("BR", "pt"),
            NewsEdition("SE", "sv"),
            NewsEdition("TR", "tr"),
            NewsEdition("JP", "ja"),
        )

        /** The regions the user can choose, in display order. */
        val COUNTRIES: List<String> = ALL.map { it.country }.distinct()

        /** The news languages the user can choose, in display order. */
        val LANGUAGES: List<String> = ALL.map { it.language }.distinct()

        /** The edition stored as [key]: any offered country with any offered language; null for anything else. */
        fun fromKey(key: String?): NewsEdition? {
            val parts = key?.split(':') ?: return null
            if (parts.size != 2) return null
            return of(parts[0], parts[1])
        }

        /** [country] read in [language], or null when the app offers one of them not. */
        fun of(country: String, language: String): NewsEdition? =
            NewsEdition(country, language).takeIf { country in COUNTRIES && language in LANGUAGES }

        /** The main edition of [country] (first in [ALL]), or null when we offer none. */
        fun forCountry(country: String?): NewsEdition? =
            country?.takeIf { it.isNotBlank() }?.uppercase()?.let { c -> ALL.firstOrNull { it.country == c } }

        /** The home edition of [language] (first in [ALL]): `de` → Germany, `en` → USA. */
        fun home(language: String): NewsEdition? = ALL.firstOrNull { it.language == language }

        /** The languages Google News has an edition of [country] in, the main one first. */
        fun languagesOf(country: String): List<String> = ALL.filter { it.country == country }.map { it.language }

        /**
         * A sensible first edition for a phone set to [locale]: its country in its language when
         * the app offers both (an English phone in Germany reads about Germany in English), else
         * its country in the country's main language, else the home edition of its language,
         * else [USA].
         */
        fun default(locale: Locale = Locale.getDefault()): NewsEdition =
            of(locale.country.uppercase(), locale.language)
                ?: forCountry(locale.country)
                ?: home(locale.language)
                ?: USA
    }
}

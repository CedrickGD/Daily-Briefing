package com.cedrickgd.morgenbriefing.data.model

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Where and in which language the news come from: a country plus a language, exactly as
 * Google News editions are keyed (`hl=en-US&gl=US&ceid=US:en`). Independent of the app's
 * interface language ([com.cedrickgd.morgenbriefing.i18n.AppLanguage]).
 *
 * The user picks one main edition; a local-news place abroad (e.g. Washington, D.C. while the
 * main edition is Germany) is read in the edition of its own country ([forCountry]).
 */
@Serializable
data class NewsEdition(
    /** ISO 3166-1 alpha-2, upper case: `DE`, `US`. */
    val country: String,
    /** ISO 639-1, lower case: `de`, `en`. */
    val language: String,
) {
    /** Google News `hl` parameter: `en-US`, `de`. */
    val hl: String get() = if (language == "de" && country == "DE") "de" else "$language-$country"

    /** Google News `ceid` parameter: `US:en`. */
    val ceid: String get() = "$country:$language"

    /** Stable storage key: `US:en`. */
    val key: String get() = ceid

    /** Accept-Language header value for requests in this edition. */
    val acceptLanguage: String get() = "$language-$country,$language;q=0.9,en;q=0.5"

    val locale: Locale get() = Locale.Builder().setLanguage(language).setRegion(country).build()

    companion object {
        val GERMANY = NewsEdition("DE", "de")
        val USA = NewsEdition("US", "en")

        /**
         * The editions offered in the picker, in display order. All of them are Google News
         * editions; curated publisher feeds exist for some (see FeedCatalog).
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
            NewsEdition("ES", "es"),
            NewsEdition("MX", "es"),
            NewsEdition("AR", "es"),
            NewsEdition("IT", "it"),
            NewsEdition("NL", "nl"),
            NewsEdition("PL", "pl"),
            NewsEdition("PT", "pt"),
            NewsEdition("BR", "pt"),
            NewsEdition("SE", "sv"),
            NewsEdition("TR", "tr"),
            NewsEdition("JP", "ja"),
        )

        fun fromKey(key: String?): NewsEdition? = ALL.firstOrNull { it.key == key }

        /** The main edition of [country] (first in [ALL]), or null when we offer none. */
        fun forCountry(country: String?): NewsEdition? =
            country?.takeIf { it.isNotBlank() }?.uppercase()?.let { c -> ALL.firstOrNull { it.country == c } }

        /** A sensible first edition for a phone set to [locale]: its country, else its language, else [USA]. */
        fun default(locale: Locale = Locale.getDefault()): NewsEdition {
            val exact = ALL.firstOrNull { it.country == locale.country.uppercase() && it.language == locale.language }
            return exact ?: forCountry(locale.country) ?: ALL.firstOrNull { it.language == locale.language } ?: USA
        }
    }
}

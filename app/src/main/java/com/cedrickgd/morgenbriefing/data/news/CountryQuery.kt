package com.cedrickgd.morgenbriefing.data.news

import java.util.Locale

/**
 * How news name a country in a language: the Google News search words that find "news about
 * the USA in German" where Google News has no edition for the pair
 * ([com.cedrickgd.morgenbriefing.data.model.NewsEdition.isNative]). Pure, unit-tested.
 *
 * For the five interface languages the names are written down, with the short forms headlines
 * prefer ("USA" rather than "Vereinigte Staaten", "UK", "EE. UU."); the other news languages
 * take the country's name from the platform's locale data ("Almanya" for Germany in Turkish).
 * `USA OR "Vereinigte Staaten"` in the German edition: 100 stories of the last two days
 * about the USA; `Germany` in the US edition: 100 about Germany (checked 15.11.2026).
 */
object CountryQuery {

    /**
     * The search words for [country] (ISO alpha-2) in [language] (ISO 639-1): alternatives
     * joined by `OR`, names of several words quoted. They narrow a query they are put in front
     * of (`USA OR "Vereinigte Staaten" Wirtschaft OR Konjunktur`: `OR` binds tighter than the
     * space between the two groups).
     */
    fun terms(country: String, language: String): String {
        val code = country.uppercase(Locale.ROOT)
        val names = NAMES[code]?.get(language) ?: listOfNotNull(platformName(code, language))
        return names.ifEmpty { listOf(code) }.joinToString(" OR ") { name -> if (name.any { it.isWhitespace() }) "\"$name\"" else name }
    }

    /** [country] in [language] as the platform names it; null when it only knows the code. */
    private fun platformName(country: String, language: String): String? {
        val region = runCatching { Locale.Builder().setRegion(country).build() }.getOrNull() ?: return null
        val name = region.getDisplayCountry(Locale.forLanguageTag(language)).trim()
        return name.takeIf { it.isNotEmpty() && !it.equals(country, ignoreCase = true) }
    }

    private fun names(de: String, en: String, fr: String, es: String, it: String): Map<String, List<String>> =
        mapOf("de" to de, "en" to en, "fr" to fr, "es" to es, "it" to it).mapValues { (_, names) -> names.split('|') }

    /** Country → language → names, alternatives separated by `|`. */
    private val NAMES: Map<String, Map<String, List<String>>> = mapOf(
        "DE" to names("Deutschland", "Germany", "Allemagne", "Alemania", "Germania"),
        "AT" to names("Österreich", "Austria", "Autriche", "Austria", "Austria"),
        "CH" to names("Schweiz", "Switzerland", "Suisse", "Suiza", "Svizzera"),
        "US" to names("USA|Vereinigte Staaten", "United States|U.S.|USA", "États-Unis|USA", "Estados Unidos|EE. UU.|EEUU", "Stati Uniti|Usa"),
        "GB" to names("Großbritannien|Vereinigtes Königreich", "United Kingdom|UK|Britain", "Royaume-Uni|Grande-Bretagne", "Reino Unido|Gran Bretaña", "Regno Unito|Gran Bretagna"),
        "CA" to names("Kanada", "Canada", "Canada", "Canadá", "Canada"),
        "AU" to names("Australien", "Australia", "Australie", "Australia", "Australia"),
        "IE" to names("Irland", "Ireland", "Irlande", "Irlanda", "Irlanda"),
        "IN" to names("Indien", "India", "Inde", "India", "India"),
        "FR" to names("Frankreich", "France", "France", "Francia", "Francia"),
        "BE" to names("Belgien", "Belgium", "Belgique", "Bélgica", "Belgio"),
        "ES" to names("Spanien", "Spain", "Espagne", "España", "Spagna"),
        "MX" to names("Mexiko", "Mexico", "Mexique", "México", "Messico"),
        "AR" to names("Argentinien", "Argentina", "Argentine", "Argentina", "Argentina"),
        "IT" to names("Italien", "Italy", "Italie", "Italia", "Italia"),
        "NL" to names("Niederlande|Holland", "Netherlands|Dutch", "Pays-Bas", "Países Bajos|Holanda", "Paesi Bassi|Olanda"),
        "PL" to names("Polen", "Poland", "Pologne", "Polonia", "Polonia"),
        "PT" to names("Portugal", "Portugal", "Portugal", "Portugal", "Portogallo"),
        "BR" to names("Brasilien", "Brazil", "Brésil", "Brasil", "Brasile"),
        "SE" to names("Schweden", "Sweden", "Suède", "Suecia", "Svezia"),
        "TR" to names("Türkei", "Turkey|Türkiye", "Turquie", "Turquía", "Turchia"),
        "JP" to names("Japan", "Japan", "Japon", "Japón", "Giappone"),
    )
}

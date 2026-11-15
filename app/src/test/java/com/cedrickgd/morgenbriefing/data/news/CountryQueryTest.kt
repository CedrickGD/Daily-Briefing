package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words that find a country's news in another language (checked against Google News, 15.11.2026). */
class CountryQueryTest {

    @Test
    fun `countries are named as headlines name them`() {
        assertEquals("USA OR \"Vereinigte Staaten\"", CountryQuery.terms("US", "de"))
        assertEquals("\"United States\" OR U.S. OR USA", CountryQuery.terms("US", "en"))
        assertEquals("\"Estados Unidos\" OR \"EE. UU.\" OR EEUU", CountryQuery.terms("US", "es"))
        assertEquals("Germany", CountryQuery.terms("DE", "en"))
        assertEquals("Allemagne", CountryQuery.terms("DE", "fr"))
        assertEquals("Germania", CountryQuery.terms("de", "it"))
        assertEquals("Großbritannien OR \"Vereinigtes Königreich\"", CountryQuery.terms("GB", "de"))
        assertEquals("Pays-Bas", CountryQuery.terms("NL", "fr"))
    }

    @Test
    fun `other news languages take the country's name from the platform`() {
        assertEquals("Almanya", CountryQuery.terms("DE", "tr"))
        assertEquals("Duitsland", CountryQuery.terms("DE", "nl"))
        assertEquals("Niemcy", CountryQuery.terms("DE", "pl"))
        assertEquals("Alemanha", CountryQuery.terms("DE", "pt"))
        assertEquals("Tyskland", CountryQuery.terms("DE", "sv"))
        assertEquals("ドイツ", CountryQuery.terms("DE", "ja"))
        // A name of several words is quoted.
        assertEquals("\"Verenigde Staten\"", CountryQuery.terms("US", "nl"))
    }

    @Test
    fun `every region has search words in every news language`() {
        for (country in NewsEdition.COUNTRIES) {
            for (language in NewsEdition.LANGUAGES) {
                val terms = CountryQuery.terms(country, language)
                val label = "$country $language: $terms"
                assertTrue(label, terms.isNotBlank())
                // A name, not the bare code (no locale data would give "DE").
                assertFalse(label, terms == country)
                // Quotes are balanced and alternatives joined by OR, so the words stay one group in a query.
                assertEquals(label, 0, terms.count { it == '"' } % 2)
                terms.split(" OR ").forEach { name ->
                    assertTrue(label, name.isNotBlank())
                    assertEquals(label, name.any { it.isWhitespace() }, name.startsWith("\"") && name.endsWith("\""))
                }
            }
        }
    }
}

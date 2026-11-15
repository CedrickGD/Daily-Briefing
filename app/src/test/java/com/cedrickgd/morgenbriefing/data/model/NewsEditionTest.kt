package com.cedrickgd.morgenbriefing.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Region and news language are chosen independently; Google News has an edition of its own only for some pairs. */
class NewsEditionTest {

    @Test
    fun `any offered country goes with any offered language`() {
        assertEquals(22, NewsEdition.COUNTRIES.size)
        assertEquals(listOf("de", "en", "fr", "es", "it", "nl", "pl", "pt", "sv", "tr", "ja"), NewsEdition.LANGUAGES)
        assertEquals(listOf("DE", "AT", "CH", "US"), NewsEdition.COUNTRIES.take(4))
        for (country in NewsEdition.COUNTRIES) {
            for (language in NewsEdition.LANGUAGES) {
                val edition = NewsEdition.of(country, language)!!
                assertEquals("$country:$language", edition.key)
                assertEquals(edition, NewsEdition.fromKey(edition.key))
            }
        }
        assertEquals(NewsEdition("US", "de"), NewsEdition.fromKey("US:de"))
        assertEquals(NewsEdition("DE", "en"), NewsEdition.fromKey("DE:en"))
        for (unknown in listOf(null, "", "US", "us:en", "US:EN", "XX:de", "DE:xx", "US:en:x", "US-en")) {
            assertNull(unknown, NewsEdition.fromKey(unknown))
        }
        assertNull(NewsEdition.of("NO", "de"))
    }

    @Test
    fun `the editions the old picker offered are all there, as Google News editions`() {
        val old = listOf(
            "DE:de", "AT:de", "CH:de", "US:en", "GB:en", "CA:en", "AU:en", "IE:en", "IN:en", "FR:fr", "BE:fr", "CH:fr",
            "ES:es", "MX:es", "AR:es", "IT:it", "NL:nl", "PL:pl", "PT:pt", "BR:pt", "SE:sv", "TR:tr", "JP:ja",
        )
        old.forEach { key ->
            val edition = NewsEdition.fromKey(key)!!
            assertTrue(key, edition.isNative)
            assertEquals(key, edition, edition.google)
            // The parameters Google News was asked with before.
            assertEquals(key, edition.country, edition.gl)
            assertEquals(key, key, edition.ceid)
            assertEquals(key, if (key == "DE:de") "de" else "${edition.language}-${edition.country}", edition.hl)
        }
        assertEquals(old + listOf("CA:fr", "US:es", "BE:nl"), old + (NewsEdition.ALL.map { it.key } - old.toSet()))
        assertEquals(NewsEdition.ALL.distinct(), NewsEdition.ALL)
    }

    @Test
    fun `a pair without an edition reads the home edition of its language but stays itself`() {
        val usaInGerman = NewsEdition("US", "de")
        assertFalse(usaInGerman.isNative)
        assertEquals(NewsEdition.GERMANY, usaInGerman.google)
        assertEquals(listOf("de", "DE", "DE:de"), listOf(usaInGerman.hl, usaInGerman.gl, usaInGerman.ceid))
        // Its key, language and country are its own: stored, read aloud and labelled as chosen.
        assertEquals("US:de", usaInGerman.key)
        assertEquals("de", usaInGerman.locale.language)
        assertEquals("US", usaInGerman.locale.country)
        assertTrue(usaInGerman.acceptLanguage.startsWith("de-US,de;"))
        val germanyInEnglish = NewsEdition("DE", "en")
        assertEquals(NewsEdition.USA, germanyInEnglish.google)
        assertEquals("en-US", germanyInEnglish.hl)
        // Every pair reads an edition Google News has, in its own language.
        for (country in NewsEdition.COUNTRIES) {
            for (language in NewsEdition.LANGUAGES) {
                val edition = NewsEdition(country, language)
                assertTrue(edition.key, edition.google.isNative)
                assertEquals(edition.key, language, edition.google.language)
                assertEquals(edition.key, edition.isNative, edition.google == edition)
            }
        }
    }

    @Test
    fun `countries and languages have a main edition`() {
        // A country's main language comes first …
        assertEquals(NewsEdition.USA, NewsEdition.forCountry("us"))
        assertEquals(NewsEdition("CH", "de"), NewsEdition.forCountry("CH"))
        assertEquals(NewsEdition("CA", "en"), NewsEdition.forCountry("CA"))
        assertEquals(NewsEdition("BE", "fr"), NewsEdition.forCountry("BE"))
        assertNull(NewsEdition.forCountry("NO"))
        assertNull(NewsEdition.forCountry(""))
        assertEquals(listOf("de", "fr"), NewsEdition.languagesOf("CH"))
        assertEquals(listOf("en", "es"), NewsEdition.languagesOf("US"))
        assertEquals(listOf("fr", "nl"), NewsEdition.languagesOf("BE"))
        // … and a language's home country first: French is France, not Canada.
        assertEquals(NewsEdition.GERMANY, NewsEdition.home("de"))
        assertEquals(NewsEdition.USA, NewsEdition.home("en"))
        assertEquals(NewsEdition("FR", "fr"), NewsEdition.home("fr"))
        assertEquals(NewsEdition("ES", "es"), NewsEdition.home("es"))
        assertEquals(NewsEdition("NL", "nl"), NewsEdition.home("nl"))
        assertEquals(NewsEdition("PT", "pt"), NewsEdition.home("pt"))
        assertNull(NewsEdition.home("xx"))
        NewsEdition.LANGUAGES.forEach { assertTrue(it, NewsEdition.home(it)!!.isNative) }
        NewsEdition.COUNTRIES.forEach { assertTrue(it, NewsEdition.languagesOf(it).isNotEmpty()) }
    }
}

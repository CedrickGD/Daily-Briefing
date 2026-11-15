package com.cedrickgd.morgenbriefing.location

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.RegionStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceNamingTest {

    @Test
    fun `the town is the most specific named part`() {
        assertEquals("Bad Düben", PlaceNaming.townName(" Bad Düben ", "Nordsachsen", "Sachsen", null))
        assertEquals("Nordsachsen", PlaceNaming.townName(null, "Nordsachsen", "Sachsen", "Feld"))
        assertEquals("Feld", PlaceNaming.townName("", " ", null, "Feld"))
        assertNull(PlaceNaming.townName(null, "", " ", null))
    }

    @Test
    fun `region joins state and country without repeating the town`() {
        assertEquals("Sachsen, Deutschland", PlaceNaming.region("Leipzig", "Sachsen", "Deutschland"))
        assertEquals("Deutschland", PlaceNaming.region("Berlin", "berlin", "Deutschland"))
        assertEquals("Italia", PlaceNaming.region("Milano", null, " Italia "))
    }

    @Test
    fun `local name is kept only when it differs`() {
        assertEquals("Milano", PlaceNaming.localName("Mailand", " Milano "))
        assertEquals("", PlaceNaming.localName("Berlin", "Berlin"))
        assertEquals("", PlaceNaming.localName("Milan", null))
    }

    @Test
    fun `a spot without a town is labelled by its coordinates`() {
        assertEquals("51.59, 12.59", PlaceNaming.coordinatesLabel(51.5928, 12.5853))
    }

    @Test
    fun `the unnamed fallback is worded in every interface language`() {
        assertEquals("Mein Standort", RegionStrings.of(AppLanguage.DE).myLocation)
        assertEquals("My location", RegionStrings.of(AppLanguage.EN).myLocation)
        for (lang in AppLanguage.entries) {
            val text = RegionStrings.of(lang).locationNoTown
            assertTrue("$lang: $text", text.isNotBlank() && text == text.trim())
        }
    }

    @Test
    fun `local news page with the name the news language knows the place by`() {
        // A German user added Milan, shown as "Mailand": German news call it that …
        val milan = Place("Mailand", 45.46427, 9.18951, "Lombardei, Italien", "IT", localName = "Milano")
        val id = Topics.localId(milan)
        val settings = AppSettings(edition = NewsEdition.GERMANY, localPlaces = listOf(milan), followed = listOf(id))
        val german = TopicSources.pagingQuery(id, settings)!!
        assertEquals(NewsEdition("IT", "de"), german.edition)
        assertEquals("\"Mailand\"", german.query)
        // … Italian news, for a reader of Italian news, "Milano".
        val paging = TopicSources.pagingQuery(id, settings.copy(edition = NewsEdition("DE", "it")))!!
        assertEquals(NewsEdition("IT", "it"), paging.edition)
        assertEquals("\"Milano\"", paging.query)
        // Places stored before v3.0 have no local name and keep their name.
        val old = milan.copy(localName = "")
        val oldId = Topics.localId(old)
        assertEquals("\"Mailand\"", TopicSources.pagingQuery(oldId, settings.copy(edition = NewsEdition("DE", "it"), localPlaces = listOf(old)))!!.query)
        // The home place in a country whose language is the main edition's: its own name.
        assertEquals("Leipzig", Place.LEIPZIG.newsName("de"))
        assertEquals("Milan", Place("Milan", 45.46, 9.19, "", "IT", localName = "Milano").newsName("en"))
    }
}

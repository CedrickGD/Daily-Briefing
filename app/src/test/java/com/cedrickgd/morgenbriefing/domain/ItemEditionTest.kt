package com.cedrickgd.morgenbriefing.domain

import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemEditionTest {

    private val washington = Place("Washington", 38.89511, -77.03637, "District of Columbia, USA", "US")
    private val washingtonId = Topics.localId(washington)
    private val german = AppSettings(edition = NewsEdition.GERMANY, localPlaces = listOf(washington), followed = listOf("politik", washingtonId))

    private fun item(sourceId: String, topicId: String = "politik") = NewsItem(
        id = "id-$sourceId", headline = "Headline", url = "https://example.com/a", source = "Source",
        sourceId = sourceId, topicId = topicId,
    )

    @Test
    fun `Washington local news are German for a reader of German news, English for a reader of English ones`() {
        val source = TopicSources.resolve(washingtonId, german).single()
        val local = item(source.id, washingtonId)
        assertEquals(NewsEdition("US", "de"), ItemEdition.of(local, german))
        assertEquals("de", ItemEdition.languageOf(local))
        // Paged older articles of the same search carry the paging suffix.
        assertEquals(NewsEdition("US", "de"), ItemEdition.of(item(source.id + NewsRepository.PAGE_SUFFIX, washingtonId), german))
        // What the place page in the place's own language added (too little in German) is known to be English.
        val added = item(source.fallback!!.id, washingtonId)
        assertEquals(NewsEdition.USA, ItemEdition.of(added, german))
        assertEquals("en", ItemEdition.languageOf(added))
        val english = german.copy(edition = NewsEdition.USA)
        assertEquals(NewsEdition.USA, ItemEdition.of(item(TopicSources.resolve(washingtonId, english).single().id, washingtonId), english))
    }

    @Test
    fun `articles of a region read in another language are in that language`() {
        val usaInGerman = german.copy(edition = NewsEdition("US", "de"))
        val top = TopicSources.resolve("top", usaInGerman).single()
        assertEquals(NewsEdition("US", "de"), ItemEdition.of(item(top.id, "top"), usaInGerman))
        assertEquals("de", ItemEdition.languageOf(item(top.id, "top")))
        // A German world desk under it stays German, in the reader's edition.
        assertEquals(NewsEdition("US", "de"), ItemEdition.of(item("tagesschau-ausland", "welt"), usaInGerman))
        assertEquals("en", ItemEdition.languageOf(item("gn:top@DE:en", "top")))
    }

    @Test
    fun `feeds mixed into a German reader's topics keep their own language`() {
        val tech = TopicSources.resolve("ki", german).map { it.id }
        assertEquals(true, "verge" in tech && "derstandard-web" in tech)
        // English feeds read as English (Zuhören, hyphenation), whatever edition the reader has.
        assertEquals("en", ItemEdition.languageOf(item("verge", "ki")))
        assertEquals("en", ItemEdition.of(item("verge", "ki"), german).language)
        assertEquals("en", ItemEdition.of(item("wired#page", "ki"), german).language)
        // A newsroom from abroad in German is German, from its own country.
        assertEquals(NewsEdition("AT", "de"), ItemEdition.of(item("derstandard-web", "ki"), german))
        assertEquals(NewsEdition.GERMANY, ItemEdition.of(item("euronews-de", "welt"), german))
        assertEquals("de", ItemEdition.languageOf(item("srf-international", "welt")))
    }

    @Test
    fun `Google News items name their edition`() {
        assertEquals(NewsEdition.GERMANY, ItemEdition.of(item("gn:politik@DE:de"), german))
        assertEquals(NewsEdition("FR", "fr"), ItemEdition.of(item("gn:${Topics.SEARCH_ID}@FR:fr", Topics.SEARCH_ID), german))
        assertEquals(NewsEdition("GB", "en"), ItemEdition.googleNewsEdition("gn:welt@GB:en#page"))
        assertNull(ItemEdition.googleNewsEdition("gn:welt@XX:yy"))
        assertNull(ItemEdition.googleNewsEdition("tagesschau-top"))
    }

    @Test
    fun `publisher feeds speak their catalogue language`() {
        // An English feed read under a German main edition (e.g. kept from a US edition before).
        assertEquals(NewsEdition.USA, ItemEdition.of(item("npr-news", "top"), german))
        assertEquals(NewsEdition("GB", "en"), ItemEdition.of(item("bbc-top", "top"), german))
        assertEquals(NewsEdition.GERMANY, ItemEdition.of(item("tagesschau-top", "top"), german))
        // The main edition wins when it speaks the feed's language and country.
        val british = german.copy(edition = NewsEdition("GB", "en"))
        assertEquals(NewsEdition("GB", "en"), ItemEdition.of(item("bbc-top", "top"), british))
        assertEquals("de", ItemEdition.languageOf(item("tagesschau-top", "top")))
    }

    @Test
    fun `Bundesland presets are German`() {
        val us = german.copy(edition = NewsEdition.USA)
        assertEquals(NewsEdition.GERMANY, ItemEdition.of(item("mdr-sachsen", Topics.REGION_ID), us))
        assertEquals("de", ItemEdition.languageOf(item("mdr-sachsen", Topics.REGION_ID)))
    }

    @Test
    fun `the user's own feeds count as the edition of their topic`() {
        val custom = FeedSource("custom:abcd1234", "My blog", "https://blog.example/feed", washingtonId, builtIn = false)
        val settings = german.copy(customFeeds = listOf(custom))
        assertNull(ItemEdition.languageOf(item(custom.id, washingtonId)))
        assertEquals(NewsEdition("US", "de"), ItemEdition.of(item(custom.id, washingtonId), settings))
        assertEquals(NewsEdition.GERMANY, ItemEdition.of(item(custom.id, "politik"), settings))
    }
}

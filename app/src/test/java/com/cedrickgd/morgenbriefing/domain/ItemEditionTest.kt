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
    fun `Washington local news are English while the main edition is German`() {
        val source = TopicSources.resolve(washingtonId, german).single()
        val local = item(source.id, washingtonId)
        assertEquals(NewsEdition.USA, ItemEdition.of(local, german))
        assertEquals("en", ItemEdition.languageOf(local))
        // Paged older articles of the same search carry the paging suffix.
        assertEquals(NewsEdition.USA, ItemEdition.of(item(source.id + NewsRepository.PAGE_SUFFIX, washingtonId), german))
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
        assertEquals(NewsEdition.USA, ItemEdition.of(item(custom.id, washingtonId), settings))
        assertEquals(NewsEdition.GERMANY, ItemEdition.of(item(custom.id, "politik"), settings))
    }
}

package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFreshnessTest {

    private val germany = AppSettings(edition = NewsEdition.GERMANY, followed = listOf("top", "wirtschaft/boerse"))
    private val us = germany.copy(edition = NewsEdition.fromKey("US:en")!!)

    /** The topic as the repository would have cached it after fetching every source of [settings]. */
    private fun fetched(topicId: String, settings: AppSettings, sources: List<FeedSource> = TopicSources.resolve(topicId, settings)) =
        TopicNews(topicId, "2026-11-05T08:00:00+01:00", sources = sources.map { SourceStatus(it.id, it.name, ok = true) })

    @Test
    fun `a topic fetched for the current settings is current`() {
        assertTrue(readsCurrentSources(Topics.TOP_ID, fetched(Topics.TOP_ID, germany), germany))
        assertTrue(readsCurrentSources("wirtschaft/boerse", fetched("wirtschaft/boerse", germany), germany))
    }

    @Test
    fun `a cache of another edition is outdated however fresh it is`() {
        assertFalse(readsCurrentSources(Topics.TOP_ID, fetched(Topics.TOP_ID, germany), us))
        assertFalse(readsCurrentSources("wirtschaft/boerse", fetched("wirtschaft/boerse", germany), us))
        assertFalse(readsCurrentSources(Topics.TOP_ID, fetched(Topics.TOP_ID, us), germany))
    }

    @Test
    fun `nothing cached is never current`() {
        assertFalse(readsCurrentSources(Topics.TOP_ID, null, germany))
    }

    @Test
    fun `walled or switched off publisher feeds and learned walls do not make a topic outdated`() {
        val all = TopicSources.resolve(Topics.TOP_ID, germany, blocked = emptyList(), includeDisabled = true)
        val curated = all.first { !it.isGoogleNews }
        // Fetched without one publisher feed (a wall the phone learned, or switched off since).
        val without = fetched(Topics.TOP_ID, germany, all - curated)
        assertTrue(readsCurrentSources(Topics.TOP_ID, without, germany))
        assertTrue(readsCurrentSources(Topics.TOP_ID, fetched(Topics.TOP_ID, germany), germany.copy(disabledFeeds = setOf(curated.id))))
    }

    @Test
    fun `a feed the user added to the topic needs a new fetch`() {
        val own = FeedSource("custom:1", "Mein Blog", "https://blog.example/feed", Topics.TOP_ID, builtIn = false)
        val withOwn = germany.copy(customFeeds = listOf(own))
        assertFalse(readsCurrentSources(Topics.TOP_ID, fetched(Topics.TOP_ID, germany), withOwn))
        assertTrue(readsCurrentSources(Topics.TOP_ID, fetched(Topics.TOP_ID, withOwn), withOwn))
    }

    @Test
    fun `a cache from an older app version with unknown sources is outdated`() {
        val old = TopicNews(Topics.TOP_ID, "2026-11-05T08:00:00+01:00", sources = listOf(SourceStatus("tagesschau-alt", "tagesschau", ok = true)))
        assertFalse(readsCurrentSources(Topics.TOP_ID, old, germany))
    }
}

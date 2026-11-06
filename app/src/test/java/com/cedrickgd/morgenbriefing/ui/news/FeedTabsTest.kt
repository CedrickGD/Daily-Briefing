package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTabsTest {

    private val strings = FeedStrings.of(AppLanguage.DE)
    private val ki = Topic("tech/ki", "KI", "", 0xFF6A5AD6, icon = "SMART_TOY")
    private val place = Topic("local:washington", "Washington, D.C.", "", 0xFFC6613F, icon = "LOCATION")

    private fun item(id: String, topic: String) = NewsItem(id, "H$id", url = "https://x/$id", source = "S", sourceId = "s", topicId = topic)

    private val kiItems = (1..3).map { item("k$it", ki.id) }
    private val feed = NewsFeed(
        sections = listOf(
            SectionUi(0, ki, kiItems, kiItems.take(2), fetchedAt = null),
            SectionUi(1, place, emptyList(), emptyList(), fetchedAt = null),
        ),
        latest = kiItems.map { FeedArticle(it, ki) },
        fetchedAt = null,
    )

    @Test
    fun `the bar shows for you, latest, one tab per followed topic and the plus`() {
        val tabs = feedTabs(feed, strings)
        assertEquals(listOf(TabKind.FOR_YOU, TabKind.LATEST, TabKind.TOPIC, TabKind.TOPIC, TabKind.ADD), tabs.map { it.kind })
        assertEquals(listOf("Für dich", "Neueste", "KI", "Washington, D.C.", strings.manageTopics), tabs.map { it.label })
        assertEquals(listOf(FeedFilter.ForYou, FeedFilter.Latest, FeedFilter.OneTopic(ki.id), FeedFilter.OneTopic(place.id), null), tabs.map { it.filter })
        // Topic tabs carry their topic (icon and color).
        assertEquals(ki, tabs[2].topic)
        assertEquals(place, tabs[3].topic)
        // Keys stay unique, also for the plus.
        assertEquals(tabs.size, tabs.map { it.key }.toSet().size)
    }

    @Test
    fun `without a feed yet the bar has the two views and the plus`() {
        val tabs = feedTabs(null, strings)
        assertEquals(listOf(TabKind.FOR_YOU, TabKind.LATEST, TabKind.ADD), tabs.map { it.kind })
        assertNull(tabs.last().filter)
    }

    @Test
    fun `an unfollowed topic falls back to for you`() {
        val tabs = feedTabs(feed, strings)
        assertEquals(FeedFilter.OneTopic(ki.id), resolveFilter(FeedFilter.OneTopic(ki.id), tabs))
        assertEquals(FeedFilter.Latest, resolveFilter(FeedFilter.Latest, tabs))
        assertEquals(FeedFilter.ForYou, resolveFilter(FeedFilter.OneTopic("gone"), tabs))
    }

    @Test
    fun `a tab tap starts the new view below the pinned bar, never in place`() {
        assertEquals(TabSwitch(FeedFilter.Latest, scrollToBar = true), tabSwitch(FeedFilter.Latest, FeedFilter.ForYou, parked = true))
        // The bar still sits in the page: nothing scrolls.
        assertEquals(TabSwitch(FeedFilter.Latest, scrollToBar = false), tabSwitch(FeedFilter.Latest, FeedFilter.ForYou, parked = false))
        // The view on screen tapped again: stay where you are.
        assertEquals(TabSwitch(FeedFilter.ForYou, scrollToBar = false), tabSwitch(FeedFilter.ForYou, FeedFilter.ForYou, parked = true))
    }

    @Test
    fun `all selects the topic's tab and goes back to the top of the bar`() {
        val switch = showAllSwitch(feed.sections[0])
        assertEquals(FeedFilter.OneTopic(ki.id), switch.filter)
        assertTrue(switch.scrollToBar)
        // The selected tab exists in the bar: the view is visible, not a hidden mode.
        assertTrue(feedTabs(feed, strings).any { it.filter == switch.filter })
    }

    @Test
    fun `all shows for topics with stories or stories being checked`() {
        assertTrue(canShowAll(feed.sections[0]))
        assertFalse(canShowAll(feed.sections[1]))
        assertTrue(canShowAll(feed.sections[1].copy(waiting = listOf(item("w", place.id)))))
    }
}

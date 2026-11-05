package com.cedrickgd.morgenbriefing.ui.settings

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicIcon
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowedLogicTest {

    private val order = listOf("a", "b", "c", "d")
    private val heights = mapOf("a" to 60f, "b" to 100f, "c" to 60f, "d" to 60f)
    private val gap = 10f
    private fun step(id: String, offset: Float) = RowReorder.step(order, id, offset, { heights.getValue(it) }, gap)

    @Test
    fun `a row swaps once it travelled half of its neighbour's pitch`() {
        // b's pitch is 110: "a" moving down swaps after 55 px.
        assertEquals(RowReorder.Result(order, 50f), step("a", 50f))
        val swapped = step("a", 60f)
        assertEquals(listOf("b", "a", "c", "d"), swapped.order)
        assertEquals(60f - 110f, swapped.offset)
    }

    @Test
    fun `a long drag passes several rows and keeps the rest of the offset`() {
        val result = step("a", 110f + 70f + 30f)
        assertEquals(listOf("b", "c", "a", "d"), result.order)
        assertEquals(30f, result.offset)
        val up = step("d", -(70f + 110f + 20f))
        assertEquals(listOf("a", "d", "b", "c"), up.order)
        assertEquals(-20f, up.offset)
    }

    @Test
    fun `rows stop at the ends and unknown ids change nothing`() {
        assertEquals(order, step("a", -500f).order)
        assertEquals(order, step("d", 500f).order)
        assertEquals(RowReorder.Result(order, 30f), step("x", 30f))
    }

    @Test
    fun `paused are own topics and places that are not followed`() {
        val own = Topic("custom:x", "Mine", "📰", 0xFF7B61FF, custom = false)
        val followedOwn = Topic("custom:y", "Shown", "📰", 0xFF7B61FF)
        val place = Place("Washington", 38.9, -77.0, "District of Columbia, United States", "US")
        val settings = AppSettings(
            customTopics = listOf(own, followedOwn),
            localPlaces = listOf(place, Place.LEIPZIG),
            followed = listOf("top", followedOwn.id, Topics.localId(Place.LEIPZIG)),
        )
        val paused = FollowedTopics.paused(settings)
        assertEquals(listOf(own.id, Topics.localId(place)), paused.map { it.id })
        assertTrue(paused.first().custom)
        assertEquals(listOf("top", followedOwn.id, Topics.localId(Place.LEIPZIG)), FollowedTopics.followed(settings, AppLanguage.DE).map { it.id })
    }

    @Test
    fun `an own topic needs a name, a search topic also its words`() {
        assertFalse(TopicDraft(title = "  ").canSave)
        assertTrue(TopicDraft(title = "Formel 1").canSave)
        val search = TopicDraft(title = "Midterms", content = TopicContent.SEARCH)
        assertFalse(search.canSave)
        assertTrue(search.copy(query = " Midterms  2026 ").canSave)
        assertEquals("Midterms 2026", search.copy(query = " Midterms  2026 ").cleanQuery)
        assertEquals(TopicDraft.MAX_TITLE, TopicDraft(title = "x".repeat(90)).cleanTitle.length)
    }

    @Test
    fun `drafts become topics and back`() {
        val feeds = TopicDraft(title = " Formel  1 ", icon = TopicIcon.MOTORSPORTS, seed = 0xFF2E8B57).toTopic("custom:f1")
        assertEquals(Topic("custom:f1", "Formel 1", "📰", 0xFF2E8B57, custom = true, icon = "MOTORSPORTS", query = null), feeds)
        val search = TopicDraft(title = "Midterms", icon = TopicIcon.SEARCH, content = TopicContent.SEARCH, query = "Midterms", emoji = "").toTopic("custom:m")
        assertEquals("Midterms", search.query)
        assertEquals("🔎", search.emoji)
        assertEquals("SEARCH", search.icon)

        // An old emoji-only topic keeps its emoji until an icon is picked.
        val old = Topic("custom:old", "Leipzig", "🦁", 0xFFC6613F, custom = true)
        val draft = TopicDraft.of(old)
        assertNull(draft.icon)
        assertEquals(TopicContent.FEEDS, draft.content)
        assertEquals(old, draft.toTopic(old.id))
        assertEquals(TopicContent.SEARCH, TopicDraft.of(search).content)
    }

    @Test
    fun `switching to a search swaps the default icon only`() {
        val fresh = TopicDraft(title = "x")
        assertEquals(TopicIcon.SEARCH, TopicDraft.forContent(fresh, TopicContent.SEARCH).icon)
        assertEquals(TopicIcon.NEWSPAPER, TopicDraft.forContent(TopicDraft.forContent(fresh, TopicContent.SEARCH), TopicContent.FEEDS).icon)
        val chosen = fresh.copy(icon = TopicIcon.ROCKET_STARTUP)
        assertEquals(TopicIcon.ROCKET_STARTUP, TopicDraft.forContent(chosen, TopicContent.SEARCH).icon)
        assertEquals(chosen, TopicDraft.forContent(chosen, TopicContent.FEEDS))
    }

    @Test
    fun `the own-topic palette has twelve distinct colors`() {
        assertEquals(12, TopicSeeds.size)
        assertEquals(TopicSeeds.size, TopicSeeds.distinct().size)
    }
}

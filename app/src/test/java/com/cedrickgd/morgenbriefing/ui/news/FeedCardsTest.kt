package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedCardsTest {

    private val boerse = Topic("wirtschaft/boerse", "Börse & Märkte", "", 0xFF2E7D32, parentId = "wirtschaft")
    private val wirtschaft = Topic("wirtschaft", "Wirtschaft", "", 0xFF1565C0)

    private fun item(id: String, image: String? = null, sourceId: String = "handelsblatt", source: String = "Handelsblatt", aggregator: String? = null) =
        NewsItem(
            id = id,
            headline = "H$id",
            url = "https://example.com/$id",
            source = source,
            sourceId = sourceId,
            topicId = boerse.id,
            image = image?.let { ArticleImage(it) },
            aggregator = aggregator,
        )

    private fun section(top: List<NewsItem>, all: List<NewsItem> = top, topWaiting: List<NewsItem> = emptyList(), waiting: List<NewsItem> = topWaiting) =
        SectionUi(index = 0, topic = boerse, all = all, top = top, fetchedAt = "2026-11-06T07:00:00+01:00", waiting = waiting, topWaiting = topWaiting)

    @Test
    fun `a card spread over several items rounds only its ends`() {
        assertEquals(CardPart.WHOLE, cardPart(0, 1))
        assertEquals(listOf(CardPart.TOP, CardPart.BOTTOM), (0 until 2).map { cardPart(it, 2) })
        assertEquals(listOf(CardPart.TOP, CardPart.MIDDLE, CardPart.MIDDLE, CardPart.BOTTOM), (0 until 4).map { cardPart(it, 4) })
        assertTrue(CardPart.WHOLE.roundTop && CardPart.WHOLE.roundBottom)
        assertTrue(CardPart.TOP.roundTop && !CardPart.TOP.roundBottom)
        assertTrue(!CardPart.MIDDLE.roundTop && !CardPart.MIDDLE.roundBottom)
    }

    @Test
    fun `a for-you card is a header, the first story large and the rest as compact rows`() {
        val stories = (1..4).map { item("b$it") }
        val rows = sectionRows(section(top = stories), verifyOffline = false)
        assertEquals(
            listOf(SectionRowKind.HEADER, SectionRowKind.STORY, SectionRowKind.STORY, SectionRowKind.STORY, SectionRowKind.STORY),
            rows.map { it.kind },
        )
        assertEquals(listOf(StoryStyle.LEAD, StoryStyle.ROW, StoryStyle.ROW, StoryStyle.ROW), rows.drop(1).map { it.style })
        assertEquals(stories, rows.drop(1).map { it.item })
        assertEquals(listOf(0, 1, 2, 3), rows.drop(1).map { it.storyIndex })
        assertEquals(listOf(CardPart.TOP, CardPart.MIDDLE, CardPart.MIDDLE, CardPart.MIDDLE, CardPart.BOTTOM), rows.map { it.part })
        // Hairlines between the stories, none after the last one and none under the header.
        assertEquals(listOf(false, true, true, true, false), rows.map { it.divider })
    }

    @Test
    fun `the section size decides how many stories a card shows`() {
        val all = (1..9).map { item("b$it") }
        val rows = sectionRows(section(top = all.take(2), all = all), verifyOffline = false)
        assertEquals(2, rows.count { it.kind == SectionRowKind.STORY })
        assertEquals(StoryStyle.LEAD, rows[1].style)
        assertEquals(StoryStyle.ROW, rows[2].style)
    }

    @Test
    fun `candidates still being checked close the card with a quiet row, offline not`() {
        val shown = listOf(item("b1"))
        val pending = listOf(item("b2"))
        val checking = sectionRows(section(top = shown, topWaiting = pending), verifyOffline = false)
        assertEquals(listOf(SectionRowKind.HEADER, SectionRowKind.STORY, SectionRowKind.CHECKING), checking.map { it.kind })
        assertEquals(CardPart.BOTTOM, checking.last().part)
        assertTrue("a hairline separates the story from the note", checking[1].divider)
        val offline = sectionRows(section(top = shown, topWaiting = pending), verifyOffline = true)
        assertEquals(listOf(SectionRowKind.HEADER, SectionRowKind.STORY), offline.map { it.kind })
        assertFalse(offline.last().divider)
    }

    @Test
    fun `an empty topic shows its header and one note`() {
        val rows = sectionRows(section(top = emptyList()), verifyOffline = false)
        assertEquals(listOf(SectionRowKind.HEADER, SectionRowKind.EMPTY), rows.map { it.kind })
        assertEquals(listOf(CardPart.TOP, CardPart.BOTTOM), rows.map { it.part })
    }

    @Test
    fun `a topic's list opens large, the latest list is rows only`() {
        assertEquals(StoryStyle.LEAD, listStoryStyle(0, leadFirst = true))
        assertEquals(StoryStyle.ROW, listStoryStyle(1, leadFirst = true))
        assertEquals(StoryStyle.ROW, listStoryStyle(0, leadFirst = false))
    }

    @Test
    fun `every story has its picture slot, the topic tile only with pictures off`() {
        // Whether or not a picture is known yet: the slot is there (the generated cover shows
        // until the picture does), so a card never changes its height when one arrives.
        assertEquals(StoryVisual.PICTURE, storyVisual(showImages = true))
        // "Bilder anzeigen" off: tiles everywhere, nothing is downloaded.
        assertEquals(StoryVisual.TOPIC_TILE, storyVisual(showImages = false))
    }

    @Test
    fun `the picture is the item's own, else the one the prefetcher found`() {
        assertEquals("https://own.example/a.jpg", storyImageUrl(item("a", image = "https://own.example/a.jpg"), hero = "https://hero.example/b.jpg"))
        assertEquals("https://hero.example/b.jpg", storyImageUrl(item("a"), hero = "https://hero.example/b.jpg"))
        assertNull(storyImageUrl(item("a"), hero = null))
        assertNull(storyImageUrl(item("a"), hero = " "))
    }

    private fun menu(
        article: FeedArticle = FeedArticle(item("a"), boerse),
        listening: Boolean = false,
        paused: Boolean = false,
        follow: Topic? = null,
        canUnfollow: Boolean = true,
        canHideSource: Boolean = true,
    ) = storyMenu(article, listening, paused, follow, canUnfollow, canHideSource, storySource(article.item)).map { it.action }

    @Test
    fun `the story menu offers listening, sharing, the link, the browser, the topic and the source`() {
        assertEquals(
            listOf(
                StoryAction.LISTEN, StoryAction.SHARE, StoryAction.COPY_LINK, StoryAction.OPEN_IN_BROWSER,
                StoryAction.UNFOLLOW_TOPIC, StoryAction.HIDE_SOURCE,
            ),
            menu(),
        )
    }

    @Test
    fun `listening turns into stop while read and into resume while paused`() {
        assertEquals(StoryAction.STOP_LISTENING, menu(listening = true).first())
        assertEquals(StoryAction.RESUME_LISTENING, menu(paused = true).first())
    }

    @Test
    fun `a sub-topic to follow is offered for a category story`() {
        val article = FeedArticle(item("a"), wirtschaft)
        val items = storyMenu(article, false, false, followSuggestion = boerse, canUnfollow = true, canHideSource = true, sourceName = "Handelsblatt")
        val follow = items.single { it.action == StoryAction.FOLLOW_TOPIC }
        assertEquals(boerse, follow.topic)
        assertEquals(wirtschaft, items.single { it.action == StoryAction.UNFOLLOW_TOPIC }.topic)
        // Never "follow" the very topic the story is shown under.
        assertFalse(StoryAction.FOLLOW_TOPIC in menu(article = FeedArticle(item("a"), boerse), follow = boerse))
    }

    @Test
    fun `the last topic and google news sources are not offered`() {
        assertFalse(StoryAction.UNFOLLOW_TOPIC in menu(canUnfollow = false))
        val google = FeedArticle(item("g", sourceId = "gn:wirtschaft/boerse@DE:de", aggregator = "google"), boerse)
        assertFalse(StoryAction.HIDE_SOURCE in menu(article = google))
        val nameless = FeedArticle(item("n", source = "").copy(url = ""), boerse)
        assertFalse(StoryAction.HIDE_SOURCE in menu(article = nameless))
        assertFalse(StoryAction.HIDE_SOURCE in menu(canHideSource = false))
    }

    @Test
    fun `the source a story names falls back to its site`() {
        assertEquals("Handelsblatt", storySource(item("a")))
        assertEquals("example.com", storySource(item("a", source = " ")))
    }
}

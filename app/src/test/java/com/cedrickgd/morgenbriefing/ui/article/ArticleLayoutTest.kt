package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleLayoutTest {

    private fun content(image: String? = null, vararg blocks: ArticleBlock) =
        ArticleContent(url = "https://example.com/a", title = "T", blocks = blocks.toList(), image = image, fetchedAt = "2026-11-05T08:00:00+01:00")

    private fun item(image: String? = null) = NewsItem(
        id = "id", headline = "Eine Überschrift mit einigen Wörtern", url = "https://example.com/a", source = "Test", sourceId = "t", topicId = "welt",
        image = image?.let { ArticleImage(it) },
    )

    @Test
    fun `the feed picture comes first, then the announced lead picture`() {
        val page = content("https://img.example.com/lead.jpg", ArticleBlock.image("https://img.example.com/inline.jpg"))
        assertEquals("https://img.example.com/feed.jpg", ArticleViewModel.arrange(page, item("https://img.example.com/feed.jpg")).hero)
        assertEquals("https://img.example.com/lead.jpg", ArticleViewModel.arrange(page, item()).hero)
    }

    @Test
    fun `an early picture in the text becomes the hero and is not shown twice`() {
        val page = content(
            null,
            ArticleBlock.paragraph("Intro"), ArticleBlock.image("https://img.example.com/first.jpg"), ArticleBlock.paragraph("More"),
            ArticleBlock.image("https://img.example.com/first.jpg?width=640"), ArticleBlock.image("https://img.example.com/second.jpg"),
        )
        val arranged = ArticleViewModel.arrange(page, item())
        assertEquals("https://img.example.com/first.jpg", arranged.hero)
        // Gone from the text, also its other rendition further down; the second picture stays.
        assertEquals(listOf("https://img.example.com/second.jpg"), arranged.content.blocks.filter { it.type == ArticleBlock.IMAGE }.map { it.url })
        assertEquals(listOf("Intro", "More"), arranged.content.blocks.filter { it.type == ArticleBlock.PARAGRAPH }.map { it.text })
    }

    @Test
    fun `a picture deep in the text or without https stays where it is`() {
        val deep = content(
            null,
            ArticleBlock.paragraph("1"), ArticleBlock.paragraph("2"), ArticleBlock.paragraph("3"),
            ArticleBlock.image("https://img.example.com/late.jpg"),
        )
        val arranged = ArticleViewModel.arrange(deep, item())
        assertNull(arranged.hero)
        assertEquals(1, arranged.content.blocks.count { it.type == ArticleBlock.IMAGE })
        val plain = ArticleViewModel.arrange(content(null, ArticleBlock.image("http://img.example.com/plain.jpg")), item())
        assertNull(plain.hero)
        assertEquals(1, plain.content.blocks.size)
    }

    @Test
    fun `the alt text is printed under a picture only when no caption follows`() {
        val blocks = listOf(
            ArticleBlock.image("https://img.example.com/a.jpg", alt = "Demonstrierende mit einem Banner"),
            ArticleBlock.caption("\"Gemeinsam für das Klima\" heißt es auf dem Banner Bild: AFP"),
            ArticleBlock.image("https://img.example.com/b.jpg", alt = "Ein Kreuz auf dem Friedhof"),
            ArticleBlock.paragraph("Text"),
            ArticleBlock.image("https://img.example.com/c.jpg"),
        )
        assertFalse(showsAlt(blocks, 0))
        assertTrue(showsAlt(blocks, 2))
        assertFalse(showsAlt(blocks, 4)) // no alt text
        assertFalse(showsAlt(blocks, 3)) // not a picture
        assertFalse(showsAlt(blocks, 9))
    }

    @Test
    fun `an alt text printed under its picture is not also the picture's content description`() {
        // Printed as text: a screen reader reads that line, the picture stays silent.
        assertNull(imageDescription("Dominic Johnson", altShown = true))
        // With a caption of its own the alt text is not printed: it describes the picture to a screen reader.
        assertEquals("Zwei Männer am Rednerpult", imageDescription("Zwei Männer am Rednerpult", altShown = false))
        assertNull(imageDescription("", altShown = false))
        assertNull(imageDescription("  ", altShown = false))
    }

    @Test
    fun `walls only offer the browser, network trouble offers a retry first`() {
        listOf(ReadOutcome.PAYWALL, ReadOutcome.CONSENT, ReadOutcome.BLOCKED, ReadOutcome.NOT_ARTICLE).forEach { outcome ->
            assertEquals(listOf(FailureAction.OPEN_IN_BROWSER), failureActions(ArticleFailure(outcome, "Site")))
        }
        assertEquals(listOf(FailureAction.RETRY, FailureAction.OPEN_IN_BROWSER), failureActions(ArticleFailure(ReadOutcome.NETWORK)))
        assertEquals(listOf(FailureAction.RETRY, FailureAction.OPEN_IN_BROWSER), failureActions(null))
    }

    @Test
    fun `every failure has its own symbol`() {
        assertEquals(FailureIcon.PAYWALL, failureIcon(ArticleFailure(ReadOutcome.PAYWALL)))
        assertEquals(FailureIcon.CONSENT, failureIcon(ArticleFailure(ReadOutcome.CONSENT)))
        assertEquals(FailureIcon.BLOCKED, failureIcon(ArticleFailure(ReadOutcome.BLOCKED, httpCode = 403)))
        assertEquals(FailureIcon.GONE, failureIcon(ArticleFailure(ReadOutcome.NOT_ARTICLE, httpCode = 404)))
        assertEquals(FailureIcon.NOT_ARTICLE, failureIcon(ArticleFailure(ReadOutcome.NOT_ARTICLE)))
        assertEquals(FailureIcon.OFFLINE, failureIcon(ArticleFailure(ReadOutcome.NETWORK)))
        assertEquals(FailureIcon.ERROR, failureIcon(null))
    }
}

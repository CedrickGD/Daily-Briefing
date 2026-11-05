package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleLayoutTest {

    private fun content(image: String? = null, vararg blocks: ArticleBlock) =
        ArticleContent(url = "https://example.com/a", title = "T", blocks = blocks.toList(), image = image, fetchedAt = "2026-11-05T08:00:00+01:00")

    @Test
    fun `the feed picture comes first, then the announced lead picture`() {
        val page = content("https://img/lead.jpg", ArticleBlock.image("https://img/inline.jpg"))
        assertEquals(HeroPick("https://img/feed.jpg"), pickHero("https://img/feed.jpg", page))
        assertEquals(HeroPick("https://img/lead.jpg"), pickHero(null, page))
        assertEquals(HeroPick(null), pickHero(null, null))
    }

    @Test
    fun `an early picture in the text becomes the hero and is not shown twice`() {
        val page = content(null, ArticleBlock.paragraph("Intro"), ArticleBlock.image("https://img/first.jpg"), ArticleBlock.paragraph("More"))
        assertEquals(HeroPick("https://img/first.jpg", skipBlock = 1), pickHero(null, page))
    }

    @Test
    fun `a picture deep in the text or without https stays where it is`() {
        val deep = content(
            null,
            ArticleBlock.paragraph("1"), ArticleBlock.paragraph("2"), ArticleBlock.paragraph("3"),
            ArticleBlock.image("https://img/late.jpg"),
        )
        assertEquals(HeroPick(null), pickHero(null, deep))
        assertNull(pickHero(null, content(null, ArticleBlock.image("http://img/plain.jpg"))).url)
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

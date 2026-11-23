package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import org.junit.Assert.assertEquals
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

    private fun pictures(arranged: com.cedrickgd.morgenbriefing.data.article.ArticleCleaner.Arranged) =
        arranged.content.blocks.filter { it.type == ArticleBlock.IMAGE || it.type == ArticleBlock.CAPTION }

    @Test
    fun `the one picture is the feed's, then the card's cover, then the announced lead picture`() {
        val page = content("https://img.example.com/lead.jpg", ArticleBlock.image("https://img.example.com/inline.jpg"), ArticleBlock.paragraph("Text"))
        assertEquals("https://img.example.com/feed.jpg", ArticleViewModel.arrange(page, item("https://img.example.com/feed.jpg"), cover = "https://img.example.com/cover.jpg").hero)
        assertEquals("https://img.example.com/cover.jpg", ArticleViewModel.arrange(page, item(), cover = "https://img.example.com/cover.jpg").hero)
        assertEquals("https://img.example.com/lead.jpg", ArticleViewModel.arrange(page, item()).hero)
        assertTrue(pictures(ArticleViewModel.arrange(page, item())).isEmpty())
    }

    @Test
    fun `without one the first picture of the text becomes the picture - and the text keeps none`() {
        val page = content(
            null,
            ArticleBlock.paragraph("Intro"), ArticleBlock.image("https://img.example.com/first.jpg"), ArticleBlock.caption("Die erste Bildunterschrift"),
            ArticleBlock.paragraph("More"), ArticleBlock.image("https://img.example.com/first.jpg?width=640"), ArticleBlock.image("https://img.example.com/second.jpg"),
            ArticleBlock.caption("Foto: dpa"),
        )
        val arranged = ArticleViewModel.arrange(page, item())
        assertEquals("https://img.example.com/first.jpg", arranged.hero)
        // Gone from the text: the hero's other rendition, the second picture (a gallery's), every caption.
        assertTrue(pictures(arranged).isEmpty())
        assertEquals(listOf("Intro", "More"), arranged.content.blocks.map { it.text })
    }

    @Test
    fun `a picture deep in the text is the one picture too, one without https is none`() {
        val deep = content(
            null,
            ArticleBlock.paragraph("1"), ArticleBlock.paragraph("2"), ArticleBlock.paragraph("3"), ArticleBlock.paragraph("4"),
            ArticleBlock.image("https://img.example.com/late.jpg"),
        )
        val arranged = ArticleViewModel.arrange(deep, item())
        assertEquals("https://img.example.com/late.jpg", arranged.hero)
        assertTrue(pictures(arranged).isEmpty())
        val plain = ArticleViewModel.arrange(content(null, ArticleBlock.image("http://img.example.com/plain.jpg"), ArticleBlock.paragraph("Text")), item())
        assertNull(plain.hero)
        assertEquals(listOf("Text"), plain.content.blocks.map { it.text })
    }

    @Test
    fun `a short paragraph that repeats a picture's alt text is its caption and goes with it`() {
        val page = content(
            null,
            ArticleBlock.paragraph("Ein langer erster Absatz, der die Geschichte erzählt und mit dem Bild nichts zu tun hat, außer dass er davor steht."),
            ArticleBlock.image("https://img.example.com/a.jpg", alt = "Leone XIV in volo verso Santa Cruz"),
            ArticleBlock.paragraph("Leone XIV in volo verso Santa Cruz (VATICAN MEDIA)"),
            ArticleBlock.paragraph("Der zweite Absatz erzählt weiter."),
        )
        val arranged = ArticleViewModel.arrange(page, item("https://img.example.com/feed.jpg"))
        assertEquals(2, arranged.content.blocks.size)
        assertTrue(arranged.content.blocks.none { it.text.contains("VATICAN") })
    }

    @Test
    fun `a line that only names the newsroom goes, a sentence with its name stays`() {
        val newsroom = NewsItem(
            id = "id", headline = "Eine Überschrift mit einigen Wörtern", url = "https://www.tagesschau.de/inland/a.html", source = "tagesschau", sourceId = "t",
            topicId = "welt", publisherDomain = "tagesschau.de",
        )
        val page = content(
            null,
            ArticleBlock.paragraph("Der Bundestag hat am Donnerstag beschlossen, was die tagesschau am Morgen gemeldet hatte."),
            ArticleBlock.paragraph("tagesschau.de"),
            ArticleBlock.paragraph("tagesschau"),
        )
        assertEquals(listOf("Der Bundestag hat am Donnerstag beschlossen, was die tagesschau am Morgen gemeldet hatte."), ArticleViewModel.arrange(page, newsroom).content.blocks.map { it.text })
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

    // The short version ------------------------------------------------------------------

    /** A paragraph of [words] words in sentences of ten ("Satz wort2 … ende."); [tag] tells paragraphs apart. */
    private fun para(words: Int, tag: String = "p"): ArticleBlock {
        val text = (1..words).joinToString(" ") { i ->
            when {
                i % 10 == 0 || i == words -> "ende."
                i % 10 == 1 -> "Satz"
                else -> "wort$tag$i"
            }
        }
        return ArticleBlock.paragraph(text)
    }

    private fun words(blocks: List<ArticleBlock>) = blocks.sumOf(::wordsOf)

    @Test
    fun `a short article stays whole`() {
        assertNull(shorten(listOf(para(200), para(150))))
        assertNull(shorten(listOf(para(350))))
    }

    @Test
    fun `whole paragraphs are taken until about 220 words, never past 300`() {
        val blocks = List(10) { para(60, "n$it") }
        val short = shorten(blocks)!!
        assertEquals(blocks.take(4), short.short)
        assertEquals(blocks, short.full)
        assertEquals(360, short.restWords)
        assertEquals(2, short.restMinutes)
        val bigSecond = shorten(listOf(para(160), para(250), para(200)))!!
        // 160 + 250 would pass 300: the short version stops after the first paragraph.
        assertEquals(1, bigSecond.short.size)
        assertTrue(words(bigSecond.short) <= SHORT_MAX_WORDS)
    }

    @Test
    fun `a huge first paragraph is cut at a sentence, and the whole text keeps both halves`() {
        val first = para(500, "a")
        val blocks = listOf(first, para(100, "b"))
        val short = shorten(blocks)!!
        val head = short.short.single()
        val count = wordsOf(head)
        assertTrue("$count", count in SHORT_TARGET_WORDS..SHORT_MAX_WORDS)
        assertTrue(head.text.endsWith("."))
        // The short version is a prefix of the whole one: opening the rest only adds below.
        assertEquals(short.short, short.full.take(short.short.size))
        assertEquals(first.text, short.full[0].text + " " + short.full[1].text)
        assertEquals(blocks[1], short.full.last())
        assertEquals(words(blocks), words(short.full))
        assertEquals(words(blocks) - count, short.restWords)
    }

    @Test
    fun `the short version never ends on a heading`() {
        val blocks = listOf(para(100, "a"), para(100, "b"), ArticleBlock.heading("Was jetzt kommt"), para(200, "c"))
        val short = shorten(blocks)!!
        assertEquals(blocks.take(2), short.short)
        assertEquals(blocks, short.full)
    }

    @Test
    fun `too little left to open stays whole`() {
        assertNull(shorten(listOf(para(280), para(75))))
        assertTrue(shorten(listOf(para(250), para(120))) != null)
    }

    @Test
    fun `abbreviations, initials and dates are no sentence ends`() {
        val text = "Es geht z. B. um Dr. Müller und den 3. Oktober. Dann kommt der zweite Satz. Und ein dritter."
        val (head, tail) = cutAtSentence(text, target = 9, max = 12)
        assertEquals("Es geht z. B. um Dr. Müller und den 3. Oktober.", head)
        assertEquals("Dann kommt der zweite Satz. Und ein dritter.", tail)
        assertEquals("" to text, cutAtSentence(text, target = 2, max = 3))
    }
}

package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleViewModelLogicTest {

    private fun content(vararg blocks: ArticleBlock) =
        ArticleContent(url = "https://x.de/a", title = "T", blocks = blocks.toList(), fetchedAt = "2026-10-30T18:00:00+01:00")

    @Test
    fun `the lead paragraph that repeats the teaser is dropped`() {
        val teaser = "Am ersten Tag der Kunstrad-WM in Ludwigsburg steigt die Eröffnungsfeier."
        val c = content(ArticleBlock.heading("H"), ArticleBlock.paragraph("Am ersten Tag der Kunstrad-WM in Ludwigsburg steigt die Eröffnungsfeier!"), ArticleBlock.paragraph("Weiter geht es."))
        val trimmed = ArticleViewModel.withoutTeaser(c, teaser)
        assertEquals(listOf("H", "Weiter geht es."), trimmed.blocks.map { it.text })
    }

    @Test
    fun `a teaser cut with an ellipsis matches the paragraph it starts`() {
        val c = content(ArticleBlock.paragraph("Drei entscheidende Zahlen fehlen im Börsenprospekt von Anthropic. Die Financial Times erklärt alles."))
        val trimmed = ArticleViewModel.withoutTeaser(c, "Drei entscheidende Zahlen fehlen im Börsenprospekt von Anthropic. Die Financial Times…")
        assertEquals(0, trimmed.blocks.size)
    }

    @Test
    fun `a different first paragraph stays`() {
        val c = content(ArticleBlock.paragraph("Ganz anderer Einstieg in den Text, der nichts mit dem Teaser zu tun hat."))
        assertEquals(1, ArticleViewModel.withoutTeaser(c, "Der Teaser der Redaktion ist ein anderer Satz.").blocks.size)
        assertEquals(1, ArticleViewModel.withoutTeaser(c, "").blocks.size)
    }

    @Test
    fun `an item without a feed teaser (Google News) gets the page's own, without repeating it`() {
        val description = "Iranian leaders have hailed what they say is Americans’ rejection of the war, while Republicans look for answers."
        val c = content(
            ArticleBlock.paragraph(description),
            ArticleBlock.paragraph("Iranian leaders hailed the Democratic landslide in the U.S. midterms on Tuesday as a rejection of the war."),
        ).copy(description = description, image = "https://static.example.com/assets/6aec3f9cd52f4907d2036b3f/tehran.jpg?width=1024")
        val item = NewsItem(id = "g", headline = "What the Midterms Mean for the Iran War", url = "https://news.google.com/rss/articles/X", source = "TIME", sourceId = "g", topicId = "welt", aggregator = "google")
        val arranged = ArticleViewModel.arrange(c.copy(blocks = listOf(ArticleBlock.image("https://static.example.com/assets/6aec3f9cd52f4907d2036b3f/tehran.jpg?width=3840")) + c.blocks), item)
        assertEquals(description, arranged.teaser)
        assertEquals(listOf(ArticleBlock.PARAGRAPH), arranged.content.blocks.map { it.type })
    }

    @Test
    fun `a long article opens as its short version with the whole one beside it, a short one as it is`() {
        val long = content(*Array(8) { i -> ArticleBlock.paragraph((1..60).joinToString(" ") { "wort$i$it" } + ".") })
        val split = ArticleViewModel.split(long)
        assertEquals(4, split.content.blocks.size)
        assertEquals(long.blocks, split.full!!.blocks)
        assertEquals(long.title, split.content.title)
        assertEquals(1, split.restMinutes)
        val state = ArticleUiState(item = NewsItem(id = "a", headline = "H", url = long.url, source = "S", sourceId = "s", topicId = "welt"),
            topic = com.cedrickgd.morgenbriefing.data.model.Topics.resolveOrNeutral("welt", com.cedrickgd.morgenbriefing.data.model.AppSettings()),
            content = split.content, full = split.full, restMinutes = split.restMinutes, now = java.time.ZonedDateTime.now())
        assertTrue(state.canExpand)
        // "Zuhören" reads what is shown.
        assertEquals(split.content.text, state.shown!!.text)
        val opened = state.copy(expanded = true)
        assertFalse(opened.canExpand)
        assertEquals(long.text, opened.shown!!.text)

        val short = content(ArticleBlock.paragraph("Ein kurzer Text."))
        val whole = ArticleViewModel.split(short)
        assertEquals(short, whole.content)
        assertEquals(null, whole.full)
    }
}

package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import com.cedrickgd.morgenbriefing.ui.article.ArticleViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/**
 * The duplicate-intro causes found on live pages (ArticleLiveEvaluation, 05.11.2026), each
 * reproduced with a trimmed copy of the real markup (src/test/resources/article).
 */
class ArticleDuplicatesTest {

    private val now: ZonedDateTime = ZonedDateTime.parse("2026-11-05T12:00:00+01:00")

    private fun html(name: String): String = javaClass.getResource("/article/$name")!!.readText()

    private fun read(name: String, url: String): ArticleContent {
        val page = ArticleExtractor.read(html(name), url, now)
        assertEquals(ReadOutcome.OK, page.outcome)
        return assertNotNull(page.content).let { page.content!! }
    }

    private fun texts(content: ArticleContent) = content.blocks.filter { it.text.isNotBlank() }.map { it.text }

    private fun item(url: String, headline: String, summary: String = "", image: String? = null) = NewsItem(
        id = "id", headline = headline, summary = summary, url = url, source = "Test", sourceId = "t", topicId = "welt",
        image = image?.let { ArticleImage(it) },
    )

    @Test
    fun `tagesschau - kicker headline, Stand line, teaser paragraph and lead picture show once`() {
        val url = "https://www.tagesschau.de/ausland/amerika/eilmeldung-midterms-usa-100.html"
        val content = read("tagesschau.html", url)
        // The page itself: headline (with its kicker) and "Stand:" are gone already.
        assertTrue(texts(content).none { it.startsWith("US-Zwischenwahl") || it.startsWith("Stand:") })
        assertEquals(ArticleContent.FORMAT, content.format)
        assertTrue(content.description.startsWith("Bei den Midterms"))

        val teaser = "Bei den Midterms haben sich die Demokraten eine knappe Mehrheit im Senat gesichert. Sie konnten mehrere Bundesstaaten erobern, die lange in der Hand der Republikaner waren."
        val feedImage = "https://images.tagesschau.de/image/8e6be664-a867-424d-82e0-a1f8ce29c500/AAABoaTfkVw/AAABnSSvrFg/16x9-big/demokraten-124.jpg?width=1920"
        val arranged = ArticleViewModel.arrange(content, item(url, "US-Zwischenwahl: Demokraten erobern Mehrheit im Senat", teaser, feedImage))
        assertEquals(teaser, arranged.teaser)
        val blocks = arranged.content.blocks
        assertEquals("the hero picture is not repeated in the text", 0, blocks.count { it.type == ArticleBlock.IMAGE })
        assertTrue(blocks.first().text.startsWith("Die Demokraten haben bei den Zwischenwahlen"))
        assertTrue(blocks.none { TextMatch.same(it.text, teaser) })
        assertEquals(5, blocks.size) // four paragraphs and the subheading, nothing lost
    }

    @Test
    fun `deutschlandfunk - headline with kicker dash and the bare date line go, the lead picture is the hero`() {
        val url = "https://www.deutschlandfunk.de/bundestag-debattiert-ueber-aufarbeitung-der-nsu-verbrechen-100.html"
        val content = read("dlf.html", url)
        assertTrue(texts(content).none { it.startsWith("Rechtsextremismus") || it == "05.11.2026" })
        // In the text the picture stays (it is not the reader's hero yet) …
        assertEquals(1, content.blocks.count { it.type == ArticleBlock.IMAGE })
        // … and goes when the feed's other rendition of it is shown on top, with its caption.
        val feedImage = "https://bilder.deutschlandfunk.de/73/cb/28/3f/73cb283f-b56a-4199-b844-d73ddded4d3b/alexander-throm-cdu-100-1920x1920.jpg"
        val arranged = ArticleViewModel.arrange(content, item(url, "Rechtsextremismus - Bundestag debattiert über Aufarbeitung der NSU-Verbrechen", image = feedImage))
        assertTrue(arranged.content.blocks.none { it.type == ArticleBlock.IMAGE || it.type == ArticleBlock.CAPTION })
        assertTrue(arranged.content.blocks.first().text.startsWith("Das entsprechende Stiftungsgesetz"))
    }

    @Test
    fun `header noise - breadcrumb, meta box, author box with avatar, date line, caption and standards line go`() {
        val content = read("header-noise.html", "https://www.handelsblatt.com/politik/deutschland/freuding/100267616.html")
        val all = texts(content) + content.blocks.flatMap { it.items }
        assertTrue(all.none { it.contains("Startseite") || it.contains("Lesedauer") || it.contains("Posts from this author") })
        assertTrue(all.none { it.contains("Lesezeit") || it.contains("Foto: Kay Nietfeld") || it.startsWith("Veröffentlicht nach") })
        assertTrue(content.blocks.none { it.type == ArticleBlock.IMAGE }) // the author's avatar
        assertEquals(3, content.blocks.size)
        assertTrue(content.blocks.first().text.startsWith("Berlin. Heeresinspekteur"))
    }

    @Test
    fun `pull quote with attribution, alt text repeated as caption and the same paragraph twice`() {
        val content = read("pull-quote.html", "https://www.tagesspiegel.de/politik/aiwanger-16243216.html")
        assertTrue(content.blocks.none { it.type == ArticleBlock.QUOTE })
        assertTrue(texts(content).none { it == "Markus Söder, CSU-Chef" })
        assertEquals(1, texts(content).count { it.contains("Mit Hubert Aiwanger geht ein echtes Mannsbild") })
        assertEquals(1, texts(content).count { it.startsWith("Im bayerischen Wirtschaftsministerium") })
        val image = content.blocks.indexOfFirst { it.type == ArticleBlock.IMAGE }
        assertEquals("", content.blocks[image].alt) // shown once: as the caption (with its credit)
        assertEquals("Hubert Aiwanger und Markus Söder bei einer Pressekonferenz (Foto: picture alliance / dpa)", content.blocks[image + 1].text)
        assertEquals(ArticleBlock.CAPTION, content.blocks[image + 1].type)
    }

    @Test
    fun `split sections are read in full, player leftovers and teaser link lists go, caption parts join`() {
        val content = read("split-sections.html", "https://www1.wdr.de/sport/fussball/dfb-nominierung-klopp-nrw100.html")
        val all = texts(content)
        listOf("Überraschung bei der zweiten", "Für Wael Mohya ist", "Für Said El Mala", "Im Verein machte", "Mit Nico Schlotterbeck", "Die deutsche Nationalmannschaft")
            .forEach { start -> assertTrue("missing: $start", all.any { it.startsWith(start) }) }
        assertTrue(all.none { it.contains("Audio starten") || it.contains("Verfügbar bis") || it.contains("Diesen Artikel teilen") })
        assertTrue(content.blocks.none { block -> block.items.any { it.contains("Mehr") || it == "Facebook" } })
        val image = content.blocks.indexOfFirst { it.type == ArticleBlock.IMAGE }
        assertEquals(ArticleBlock.CAPTION, content.blocks[image + 1].type)
        assertEquals("Erneut von Klopp für den DFB nominiert: Said El Mala Bildquelle: IMAGO/Chai v.d. Laage", content.blocks[image + 1].text)
    }

    @Test
    fun `widgets before the text, related links, author bio, comment count and consent dialog go`() {
        val content = read("widgets.html", "https://arstechnica.com/gaming/2026/11/nintendo-rom-supplier/")
        val all = texts(content)
        assertTrue(all.first().startsWith("For years, the AI industry"))
        assertTrue(all.none { it in setOf("Size", "Learn more", "Listen", "Narrated by TIME AI.", "37 Comments", "Zustimmen") })
        assertTrue(all.none { it.startsWith("Read More:") || it.startsWith("Kyle Orland") || it.startsWith("00:00") || it.contains("Werbung") })
        assertTrue(content.blocks.none { it.type == ArticleBlock.IMAGE })
        val starts = listOf("For years", "Chip manufacturer", "Still limping along", "This doesn’t mean", "That could have")
        assertEquals(starts.size, all.size)
        starts.zip(all).forEach { (start, text) -> assertTrue("$text should start with $start", text.startsWith(start)) }
    }

    @Test
    fun `a text Readability misses (header-heavy page) is found in the container with most paragraphs`() {
        val content = read("header-heavy.html", "https://time.com/article/2026/11/05/iran-us-midterms/")
        assertEquals(6, content.blocks.count { it.type == ArticleBlock.PARAGRAPH })
        assertTrue(texts(content).none { it.startsWith("©") || it.startsWith("Iranian Leaders Cheer") })
        assertFalse(content.looksTruncated)
    }
}

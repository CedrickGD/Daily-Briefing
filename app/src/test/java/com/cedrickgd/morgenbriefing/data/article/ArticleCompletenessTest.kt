package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.article.ArticleCompleteness.Gap
import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/**
 * The strict judgement of "nur vollständige Artikel" on real pages (`resources/article/`, trimmed
 * from the live evaluation of 05.11.2026): only a complete article is verified.
 */
class ArticleCompletenessTest {

    private val now: ZonedDateTime = ZonedDateTime.parse("2026-11-05T12:00:00+01:00")

    private fun html(name: String): String = javaClass.getResource("/article/$name")!!.readText()

    /** Extracts [name] as the reader does; the extraction itself must succeed. */
    private fun read(name: String, url: String): ArticleContent {
        val page = ArticleExtractor.read(html(name), url, now)
        assertEquals("$name extracts", ReadOutcome.OK, page.outcome)
        return page.content!!
    }

    private fun paragraphs(n: Int, prefix: String = "Absatz", from: Int = 1) = (from until from + n).joinToString("") { i ->
        "<p>$prefix $i erzählt in aller Ruhe, was an diesem Tag in der Stadt passiert ist, wer dabei war und warum das " +
            "für die Menschen vor Ort eine Rolle spielt, mit $i Stimmen aus der Nachbarschaft.</p>"
    }

    @Test
    fun `genuine short news items are complete`() {
        val dlf = read("dlf-short.html", "https://www.deutschlandfunk.de/freuding-wird-generalinspekteur-der-bundeswehr-100.html")
        assertTrue("short: ${dlf.wordCount} words", dlf.wordCount < ArticleContent.SHORT_WORDS)
        assertNull(ArticleCompleteness.gap(dlf, "Der bisherige Heeresinspekteur Christian Freuding wird neuer Generalinspekteur der Bundeswehr.", "Freuding wird Generalinspekteur der Bundeswehr"))

        val police = read("police-short.html", "https://www.augsburger-allgemeine.de/augsburg/unbekannte-stehlen-mehrere-autos-der-marke-toyota-115639346")
        assertEquals(true, police.free)
        assertNull(ArticleCompleteness.gap(police, police.description, "Unbekannte stehlen mehrere Autos der Marke Toyota"))

        // The earlier fixtures: a Deutschlandfunk item, a tagesschau article, a short free SPIEGEL article.
        assertNull(ArticleCompleteness.gap(read("dlf.html", "https://www.deutschlandfunk.de/bundestag-debattiert-ueber-aufarbeitung-der-nsu-verbrechen-100.html")))
        assertNull(ArticleCompleteness.gap(read("tagesschau.html", "https://www.tagesschau.de/ausland/amerika/us-wahl-senat-100.html")))
        assertNull(ArticleCompleteness.gap(read("free-short.html", "https://www.spiegel.de/wirtschaft/erbschaftsteuer-a-f63369a2")))
        // The full text the publisher openly publishes in its JSON-LD.
        assertNull(ArticleCompleteness.gap(read("jsonld-body.html", "https://www.example-daily.com/news/tram")))
    }

    @Test
    fun `a page that shows only the lead is a teaser, even without any paywall markup`() {
        val url = "https://www.abendblatt.de/schleswig-holstein/article413598620/massiver-stromausfall-im-norden.html"
        val teaser = read("teaser-lead.html", url)
        assertEquals(Gap.ONLY_LEAD, ArticleCompleteness.gap(teaser, headline = "Massiver Stromausfall im Norden: 5000 Betroffene machen 16 Millionen Euro Schäden geltend"))
        assertFalse(ArticleCompleteness.isComplete(teaser))
    }

    @Test
    fun `a publisher that declares a far longer article than the reader found is not complete`() {
        val quarks = read("declared-longer.html", "https://www.quarks.de/gesundheit/ernaehrung/manuka-honig-erkaeltung-immunsystem/")
        assertEquals(2257, quarks.declaredWords)
        assertEquals(Gap.DECLARED_LONGER, ArticleCompleteness.gap(quarks, headline = "Manuka-Honig: Immunbooster oder einfach nur teuer?"))
        // A declared length the text reaches is fine.
        assertNull(ArticleCompleteness.gap(quarks.copy(declaredWords = quarks.wordCount + 20)))
    }

    @Test
    fun `video pages, front pages, quote pages and unrelated pages hold no article`() {
        val video = read("video-page.html", "https://www.bbc.co.uk/news/videos/c9dr8713zjgyo")
        assertEquals(Gap.NO_ARTICLE, ArticleCompleteness.gap(video, "BBC correspondent Sima Kotecha explains how the trial unfolded.", "Watch: How rapist Brand was never far from controversy"))

        val front = read("landing-page.html", "https://www.4investors.de/")
        assertEquals(Gap.NO_ARTICLE, ArticleCompleteness.gap(front, headline = "Rheinmetall Aktie: Gewinnwarnung zieht den Boden weg - 688 Euro Ziel oder Erholung?"))
        // The same text on an article address, but about something else than the headline: still no article.
        assertEquals(Gap.NO_ARTICLE, ArticleCompleteness.gap(front.copy(url = "https://www.4investors.de/nachrichten/rheinmetall-123"), headline = "Rheinmetall Aktie: Gewinnwarnung zieht den Boden weg"))

        val quote = read("quote-page.html", "https://de.tradingview.com/symbols/AMEX-SIL/")
        assertEquals(Gap.NO_ARTICLE, ArticleCompleteness.gap(quote, headline = "SIL ETF-Preis und Chart — AMEX:SIL"))

        // A long listing is an article's content (reviersport.de, 05.11.2026: one paragraph, then the fixtures).
        val fixtures = ArticleContent(
            url = "https://www.reviersport.de/fussball/1bundesliga/a631901---bundesliga-terminierung-bis-18-spieltag.html",
            title = "Bundesliga: Terminierung bis 18. Spieltag - Auch Schalke-Derby gegen BVB terminiert", fetchedAt = "x",
            blocks = listOf(
                ArticleBlock.paragraph(
                    "Die Bundesliga hat am Donnerstag die letzten Begegnungen des Jahres 2026 und die ersten Spieltage des Jahres 2027 " +
                        "der Bundesliga zeitgenau angesetzt. Damit sind nun in der Bundesliga die Termine vom 12. bis 18. Spieltag festgelegt.",
                ),
            ) + (12..18).flatMap { day ->
                listOf("$day. Spieltag", "Freitag, $day. Dezember, 20:30 Uhr", "FC Bayern München - SC Paderborn 07", "Samstag, $day. Dezember, 15:30 Uhr",
                    "RB Leipzig - 1. FSV Mainz 05", "Bayer 04 Leverkusen - Borussia Mönchengladbach", "FC Schalke 04 - Borussia Dortmund")
                    .map(ArticleBlock::paragraph)
            },
        )
        assertTrue(fixtures.wordCount > ArticleContent.SHORT_WORDS)
        assertNull(ArticleCompleteness.gap(fixtures, headline = "Bundesliga: Terminierung bis 18. Spieltag - Auch Schalke-Derby gegen BVB terminiert"))
    }

    @Test
    fun `subscriber marks at the end of a longer text make it a teaser`() {
        // 300 words of free text, then the paywall wording: the extractor still reads it (no PAYWALL outcome), but it is cut.
        val page = "<html><body><article><h1>Haushalt beschlossen</h1>" + paragraphs(9) +
            "<p>Sie möchten weiterlesen? Jetzt weiterlesen mit SZ Plus – für 1 Euro im ersten Monat.</p></article></body></html>"
        val read = ArticleExtractor.read(page, "https://www.sueddeutsche.de/muenchen/haushalt-li.1", now)
        assertEquals(ReadOutcome.OK, read.outcome)
        assertTrue(read.content!!.paywallHints)
        assertEquals(Gap.PAYWALL_MARKS, ArticleCompleteness.gap(read.content!!))

        // A long free article that mentions a paid brand in the middle stays complete.
        val free = "<html><body><article><h1>Haushalt beschlossen</h1>" + paragraphs(6) + "<p>Mehr dazu bei SPIEGEL+ und im Newsletter.</p>" +
            paragraphs(8, "Weiter", from = 7) + "</article></body></html>"
        val ok = ArticleExtractor.read(free, "https://www.spiegel.de/b", now).content!!
        assertFalse(ok.paywallHints)
        assertNull(ArticleCompleteness.gap(ok))
    }

    @Test
    fun `a short text that ends cut off or sends the reader elsewhere is a teaser`() {
        fun content(vararg texts: String) = ArticleContent(
            url = "https://www.example-news.de/politik/artikel-1", title = "Stadtrat beschließt Haushalt", fetchedAt = "x",
            blocks = texts.map(ArticleBlock::paragraph),
        )
        val long = "Der Stadtrat hat am Abend nach langer Debatte den Haushalt für das kommende Jahr beschlossen, mit Geld für Schulen und Straßen."
        assertNull(ArticleCompleteness.gap(content(long, long.replace("Abend", "Morgen"), long.replace("Schulen", "Kitas"))))
        assertEquals(Gap.CUT_OFF, ArticleCompleteness.gap(content(long, long.replace("Abend", "Morgen"), "Die Opposition kritisierte vor allem, dass …")))
        assertEquals(Gap.CUT_OFF, ArticleCompleteness.gap(content(long, long.replace("Abend", "Morgen"), "Was die Opposition sagt, lesen Sie hier. Weiterlesen")))
        // Too short to be the whole story: one paragraph, or fewer than 50 words.
        assertEquals(Gap.TOO_SHORT, ArticleCompleteness.gap(content(long + " " + long)))
        assertEquals(Gap.ONLY_LEAD, ArticleCompleteness.gap(content("Der Stadtrat hat den Haushalt beschlossen.", "Die Opposition war dagegen.")))
        // A page the publisher marks as free needs fewer words.
        assertNull(ArticleCompleteness.gap(content(long + " " + long).copy(free = true)))
    }

    @Test
    fun `the reader's note follows the same judgement`() {
        // Short but complete: no "das war vermutlich nur der Anfang" any more.
        val dlf = read("dlf-short.html", "https://www.deutschlandfunk.de/freuding-wird-generalinspekteur-der-bundeswehr-100.html")
        assertTrue(dlf.looksTruncated)
        assertTrue(ArticleCompleteness.isComplete(dlf))
    }
}

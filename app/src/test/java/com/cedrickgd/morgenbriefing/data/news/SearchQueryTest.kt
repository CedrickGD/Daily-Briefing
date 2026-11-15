package com.cedrickgd.morgenbriefing.data.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Query understanding, with the queries and headlines of the Google News checks of 05.11.2026. */
class SearchQueryTest {

    private fun google(text: String) = SearchQuery.parse(text).googleQuery

    @Test
    fun `tickers ISINs and WKNs go to Google News quoted, common abbreviations do not`() {
        // Quoted, Google's US edition gives 26 SLV stories; unquoted 41, with Indian gold ETF stories that never name SLV.
        assertEquals("\"SLV\" ETF", google("SLV ETF"))
        assertEquals("\"SLV\" etf", google("SLV etf"))
        assertEquals("\"NVDA\" Aktie", google("NVDA Aktie"))
        assertEquals("\"QQQ\"", google("\$QQQ"))
        assertEquals("\"BRK.B\"", google("BRK.B"))
        assertEquals("\"US46428Q1094\"", google("US46428Q1094"))
        assertEquals("\"A0S9GB\"", google("A0S9GB"))
        // Typed in lower case next to a finance word, a short word is a ticker too (phones capitalize the first letter).
        assertEquals("\"Slv\" etf", google("Slv etf"))
        assertEquals("\"slv\" ETF", google("slv ETF"))
        // Not: common abbreviations (Google's synonyms help them), ordinary words, years.
        assertEquals("Bitcoin ETF", google("Bitcoin ETF"))
        assertEquals("EU Gipfel", google("EU Gipfel"))
        assertEquals("Gold ETF", google("Gold ETF"))
        assertEquals("die Aktie", google("die Aktie"))
        assertEquals("Bundestagswahl 2025", google("Bundestagswahl 2025"))
        assertEquals("Rheinmetall Aktie", google("Rheinmetall Aktie"))
        assertEquals("Champions League", google("Champions League"))
        assertEquals("Nvidia Earnings", google("Nvidia Earnings"))
        assertEquals("Midterms", google("Midterms"))
    }

    @Test
    fun `the user's quotes, OR, minus and operators stay as typed`() {
        assertEquals("\"Champions League\" -Bayern", google("  \"Champions League\"   -Bayern "))
        assertEquals("\"Champions League\"", google("„Champions League“"))
        assertEquals("Midterms OR Senat site:reuters.com when:7d", google("Midterms OR Senat site:reuters.com when:7d"))
        assertEquals("\"SLV\" OR \"GLD\" -crash", google("SLV OR GLD -crash"))
        // A dangling or doubled OR is no operator.
        assertEquals("Midterms", google("Midterms OR"))
        assertEquals("Midterms", google("OR Midterms"))
        assertEquals("Midterms OR Senat", google("Midterms OR OR Senat"))
    }

    @Test
    fun `words match whole, with common endings, never inside other words`() {
        val q = SearchQuery.parse("Rheinmetall Aktie")
        assertTrue(q.matchesAll("Rheinmetall-Aktie stürzt um 16 Prozent ab", ""))
        assertTrue(q.matchesAll("Aktien von Rheinmetall brechen nach dieser Schocker-Nachricht ein", ""))
        assertFalse("both words are needed", q.matchesAll("Rheinmetall kassiert Jahresziele", ""))
        assertFalse(q.matchesAll("Rheinmetallwerk wächst, Aktienrückkauf geplant", ""))
        val midterms = SearchQuery.parse("Midterms")
        assertTrue(midterms.matchesAll("Republicans call for Trump to change course after midterm drubbing", ""))
        assertTrue(midterms.matchesAll("Liveblog zu den US-\"Midterms\"", ""))
        val etf = SearchQuery.parse("Bitcoin ETF")
        assertTrue(etf.matchesAll("Bitcoin ETFs Lose \$1 Billion as Midterms Weigh", ""))
        assertTrue(etf.matchesAll("Citi empfiehlt Kunden Bitcoin-ETFs trotz Abflüssen", ""))
        // Accents and case fold.
        assertTrue(SearchQuery.parse("borse").matchesAll("Börse: DAX auf Rekordhoch", ""))
        // Soft hyphens and zero-width characters in a headline do not split its words.
        assertEquals("iran greift ziele in bahrain an", SearchQuery.fold("Iran greift Zie­le in Bah­rain​ an"))
        assertEquals(SearchQuery.fold("Bundestagswahl"), SearchQuery.fold("Bundes­tags­wahl"))
        // Short words are exact: "US" is not in "uses".
        assertFalse(SearchQuery.parse("US").matchesAll("Everyone uses it", ""))
    }

    @Test
    fun `a ticker matches only as a whole word in capitals`() {
        val q = SearchQuery.parse("SLV ETF")
        assertTrue(q.matchesAll("Silver and SLV ETF Prices Lost Crucial Support", ""))
        assertTrue(q.matchesAll("Silber Preis: 109,66 Tonnen Zufluss in SLV", "Der weltgrößte Silber-ETF wächst."))
        assertTrue(q.matchesAll("Does iShares SLV ETF Really Hold Silver? (NYSEARCA:SLV)", ""))
        assertFalse("SLV is not SLVP", q.matchesAll("The Silver Dilemma: Why Risk-Takers Choose SLVP", "A silver ETF"))
        assertFalse("ETF alone is not enough", q.matchesAll("Silber-ETF vor dem nächsten Sprung?", ""))
        assertFalse("a ticker typed in capitals wants capitals", q.matchesAll("Die slv-Gewerkschaft und ihr ETF", ""))
        // Typed in lower case: any case, still whole.
        val soft = SearchQuery.parse("slv etf")
        assertTrue(soft.matchesAll("Silver and SLV ETF Prices Lost Crucial Support", ""))
        assertFalse(soft.matchesAll("SLVP ETF draws money", ""))
        assertTrue(SearchQuery.parse("\$QQQ").matchesAll("Invesco QQQ (QQQ) Buys \$19.6 Million More NVIDIA", ""))
    }

    @Test
    fun `phrases, OR, exclusions and sites`() {
        val phrase = SearchQuery.parse("\"Champions League\"")
        assertTrue(phrase.matchesAll("Champions League: RB Leipzig verliert 2:3", ""))
        assertFalse(phrase.matchesAll("League of Champions", ""))
        val or = SearchQuery.parse("Midterms OR Zwischenwahlen USA")
        assertTrue(or.matchesAll("Zwischenwahlen in den USA: Alle Ergebnisse", ""))
        assertTrue(or.matchesAll("USA: Midterms bringen Machtwechsel", ""))
        assertFalse(or.matchesAll("Zwischenwahlen in Brandenburg", ""))
        val minus = SearchQuery.parse("Champions League -Bayern")
        assertFalse(minus.matchesAll("Champions League: Bayern siegt", ""))
        assertTrue(minus.matchesAll("Champions League: Dortmund siegt", ""))
        val site = SearchQuery.parse("Midterms site:tagesschau.de")
        assertTrue(site.matchesAll("Liveblog zu den Midterms", "", "tagesschau.de"))
        assertFalse(site.matchesAll("Liveblog zu den Midterms", "", "zdf.de"))
        // Only operators: nothing a cached article could be matched by.
        assertFalse(SearchQuery.parse("site:reuters.com").hasTerms)
    }

    @Test
    fun `a match tells where the words are`() {
        val q = SearchQuery.parse("SLV ETF")
        val head = q.match("SLV: The Largest Silver ETF in the World", "", "The Motley Fool fool.com")
        assertEquals(2, head.inHeadline)
        assertEquals(1, head.tickersInHeadline)
        assertFalse("not in a row", head.phraseInHeadline)
        assertTrue(q.match("Silver and SLV ETF Prices Lost Crucial Support").phraseInHeadline)
        val teaser = q.match("Silber Preis: Tonnen Zufluss", "Mehr Silber im SLV, dem größten ETF")
        assertEquals(0, teaser.inHeadline)
        assertEquals(2, teaser.inText)
        val publisher = q.match("BlackRock warnt vor Silberengpässen", "", "ETF Stream etfstream.com")
        assertEquals(0, publisher.inText)
        assertTrue(publisher.sharesAnyTerm)
        assertFalse(q.match("Gold vs Silver before Diwali").sharesAnyTerm)
    }
}

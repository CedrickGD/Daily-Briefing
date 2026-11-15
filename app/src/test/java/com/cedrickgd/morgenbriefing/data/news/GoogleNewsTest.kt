package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoogleNewsTest {

    @Test
    fun `feed urls carry the edition`() {
        assertEquals("https://news.google.com/rss?hl=de&gl=DE&ceid=DE:de", GoogleNews.topStoriesUrl(NewsEdition.GERMANY))
        assertEquals(
            "https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=en-US&gl=US&ceid=US:en",
            GoogleNews.sectionUrl(NewsSection.BUSINESS, NewsEdition.USA),
        )
        val geo = GoogleNews.geoUrl("Washington, District of Columbia", NewsEdition.USA).toHttpUrl()
        assertEquals(listOf("rss", "headlines", "section", "geo", "Washington, District of Columbia"), geo.pathSegments)
        val search = GoogleNews.searchUrl("\"Wall Street\" OR DAX -site:zeit.de", NewsEdition.GERMANY).toHttpUrl()
        assertEquals("/rss/search", search.encodedPath)
        assertEquals("\"Wall Street\" OR DAX -site:zeit.de", search.queryParameter("q"))
        assertEquals("DE:de", search.queryParameter("ceid"))
        assertEquals("fr-BE", GoogleNews.searchUrl("x", NewsEdition("BE", "fr")).toHttpUrl().queryParameter("hl"))
    }

    @Test
    fun `a pair Google News has no edition of asks the edition of its language`() {
        // hl=de&gl=US&ceid=US:de is redirected to the German edition and loses the country (checked 15.11.2026).
        val usaInGerman = NewsEdition("US", "de")
        assertEquals("https://news.google.com/rss?hl=de&gl=DE&ceid=DE:de", GoogleNews.topStoriesUrl(usaInGerman))
        val search = GoogleNews.searchUrl("USA OR \"Vereinigte Staaten\"", usaInGerman).toHttpUrl()
        assertEquals(listOf("de", "DE", "DE:de"), listOf("hl", "gl", "ceid").map { search.queryParameter(it) })
        assertEquals("USA OR \"Vereinigte Staaten\"", search.queryParameter("q"))
        val germanyInEnglish = GoogleNews.sectionUrl(NewsSection.WORLD, NewsEdition("DE", "en")).toHttpUrl()
        assertEquals(listOf("en-US", "US", "US:en"), listOf("hl", "gl", "ceid").map { germanyInEnglish.queryParameter(it) })
        // Every pair asks for an edition Google News has, in the pair's language; its own when it has it.
        for (country in NewsEdition.COUNTRIES) {
            for (language in NewsEdition.LANGUAGES) {
                val edition = NewsEdition(country, language)
                val url = GoogleNews.topStoriesUrl(edition).toHttpUrl()
                val asked = NewsEdition(url.queryParameter("gl")!!, url.queryParameter("ceid")!!.substringAfter(':'))
                assertTrue(edition.key, asked in NewsEdition.ALL)
                assertEquals(edition.key, language, asked.language)
                assertEquals(edition.key, asked.country, url.queryParameter("ceid")!!.substringBefore(':'))
                if (edition.isNative) assertEquals(edition.key, edition, asked)
            }
        }
        // The editions that joined the list keep their country.
        assertEquals("https://news.google.com/rss?hl=fr-CA&gl=CA&ceid=CA:fr", GoogleNews.topStoriesUrl(NewsEdition("CA", "fr")))
        assertEquals("https://news.google.com/rss?hl=nl-BE&gl=BE&ceid=BE:nl", GoogleNews.topStoriesUrl(NewsEdition("BE", "nl")))
        assertEquals("https://news.google.com/rss?hl=es-US&gl=US&ceid=US:es", GoogleNews.topStoriesUrl(NewsEdition("US", "es")))
    }

    @Test
    fun `a search can be limited to the last days`() {
        assertEquals("when:14d \"Washington\"", GoogleNews.recent(" \"Washington\" ", 14))
        // The limit stays in front, exclusions behind.
        assertEquals("when:7d USA -site:zeit.de", GoogleNews.buildQuery(GoogleNews.recent("USA", 7), listOf("zeit.de")))
    }

    @Test
    fun `exclusions of a pair without an edition are ranked for the edition it reads`() {
        assertEquals(
            GoogleNews.rankExclusions(KnownWalls.DOMAINS, NewsEdition.GERMANY),
            GoogleNews.rankExclusions(KnownWalls.DOMAINS, NewsEdition("US", "de")),
        )
        assertEquals(
            GoogleNews.rankExclusions(KnownWalls.DOMAINS, NewsEdition.USA),
            GoogleNews.rankExclusions(KnownWalls.DOMAINS, NewsEdition("DE", "en")),
        )
    }

    @Test
    fun `queries get exclusions and date windows`() {
        val window = GoogleNews.DateWindow(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 27))
        assertEquals(
            "after:2026-10-20 before:2026-10-27 Midterms -site:nytimes.com -site:wsj.com",
            GoogleNews.buildQuery("  Midterms ", listOf("www.nytimes.com", "wsj.com", "nytimes.com"), window),
        )
        val many = (1..30).map { "site$it.com" }
        assertEquals(GoogleNews.MAX_EXCLUSIONS, GoogleNews.buildQuery("x", many).split(' ').count { it.startsWith("-site:") })
        assertEquals("a -site:zeit.de -site:bild.de", GoogleNews.buildQuery("a -site:zeit.de", listOf("zeit.de", "bild.de")))
        assertEquals("x", GoogleNews.buildQuery("x"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a window must not end before it starts`() {
        GoogleNews.DateWindow(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 20))
    }

    @Test
    fun `exclusions are ranked for the edition`() {
        val german = GoogleNews.rankExclusions(KnownWalls.DOMAINS, NewsEdition.GERMANY)
        val germanOwn = KnownWalls.DOMAINS.count { it.endsWith(".de") || it == "faz.net" }
        assertTrue(german.take(germanOwn).all { it.endsWith(".de") || it == "faz.net" })
        assertEquals("youtube.com", german[germanOwn])
        assertTrue(german.takeLast(5).all { it in setOf("tiktok.com", "instagram.com", "facebook.com", "x.com", "twitter.com") })
        val american = GoogleNews.rankExclusions(KnownWalls.DOMAINS, NewsEdition.USA)
        assertEquals("nytimes.com", american.first())
        assertTrue("reuters.com" in american.take(GoogleNews.MAX_EXCLUSIONS))
        assertTrue(american.indexOf("zeit.de") > american.indexOf("seekingalpha.com"))
        assertTrue(american.indexOf("faz.net") > american.indexOf("youtube.com"))
        val british = GoogleNews.rankExclusions(KnownWalls.DOMAINS, NewsEdition("GB", "en"))
        assertEquals(listOf("thetimes.co.uk", "telegraph.co.uk"), british.take(2))
        // Learned domains keep their order within a rank.
        assertEquals(listOf("b.de", "a.de", "x.com"), GoogleNews.rankExclusions(listOf("x.com", "b.de", "a.de"), NewsEdition.GERMANY))
    }

    @Test
    fun `article links are recognized`() {
        val link = "https://news.google.com/rss/articles/CBMic0FVX3lxTE45eE5aUXhYMVFkZ0lRNzk1UTdrUkFY?oc=5"
        assertEquals("CBMic0FVX3lxTE45eE5aUXhYMVFkZ0lRNzk1UTdrUkFY", GoogleNews.articleId(link))
        assertTrue(GoogleNews.isArticleLink("https://news.google.com/articles/CBMic0FVX3lxTE45eE5aUXhYMVFk?hl=de"))
        assertFalse(GoogleNews.isArticleLink("https://www.tagesschau.de/rss/articles/CBMic0FVX3lxTE45eE5aUXhYMVFk"))
        assertNull(GoogleNews.articleId("https://news.google.com/stories/CAAqNggKIjBDQklTSGpvSmMzUnZjbmt0"))
        assertNull(GoogleNews.articleId("nonsense"))
    }

    @Test
    fun `the publisher suffix leaves the headline`() {
        assertEquals("Dax springt auf Rekordhoch", GoogleNews.stripPublisher("Dax springt auf Rekordhoch - Spiegel", "Spiegel"))
        assertEquals(
            "Leipzigs Ehrendirigent Herbert Blomstedt ist gestorben",
            GoogleNews.stripPublisher("Leipzigs Ehrendirigent Herbert Blomstedt ist gestorben - LVZ - Leipziger Volkszeitung", "LVZ - Leipziger Volkszeitung"),
        )
        // A dash inside the headline stays when the publisher is known.
        assertEquals("Berlin - Bundestag debattiert", GoogleNews.stripPublisher("Berlin - Bundestag debattiert - Deutschlandfunk", "Deutschlandfunk"))
        assertEquals("Ohne Quelle", GoogleNews.stripPublisher("Ohne Quelle - BBC", ""))
        assertEquals("Nur ein Titel", GoogleNews.stripPublisher("Nur ein Titel", "BBC"))
        assertEquals("BBC", GoogleNews.stripPublisher("BBC", "BBC"))
    }

    @Test
    fun `a site's own name in its page title leaves the headline too`() {
        // Titles as Google News delivered them on 15.11.2026: the site names itself, Google appends the name once more.
        assertEquals(
            "Kreise: Koalition verschiebt Einführung von Zuckersteuer auf 2028",
            GoogleNews.stripPublisher("Kreise: Koalition verschiebt Einführung von Zuckersteuer auf 2028 - boerse.de - boerse.de", "boerse.de", "boerse.de"),
        )
        // … with its desk in front of the name.
        assertEquals(
            "Steuern: Existenzminimum wird angehoben",
            GoogleNews.stripPublisher("Steuern: Existenzminimum wird angehoben - Politik - SZ.de - SZ.de", "SZ.de", "sueddeutsche.de"),
        )
        assertEquals(
            "WTA Finals in den USA - Tennisspielerin Sabalenka entschuldigt sich nach Wutanfall",
            GoogleNews.stripPublisher("WTA Finals in den USA - Tennisspielerin Sabalenka entschuldigt sich nach Wutanfall - Sport - SZ.de - SZ.de", "SZ.de", "sueddeutsche.de"),
        )
        // The site as its address, Google's name for it another one.
        assertEquals(
            "Neun Kinder und Politik: Wie Stadträtin Alexandra Gaßmann das alles schafft",
            GoogleNews.stripPublisher("Neun Kinder und Politik: Wie Stadträtin Alexandra Gaßmann das alles schafft | BR.de - BR", "BR", "www.br.de"),
        )
        // A desk's name at the end of a headline stays when the site did not name itself: it is part of the headline.
        assertEquals("Streit in der Koalition - Politik", GoogleNews.stripPublisher("Streit in der Koalition - Politik - Spiegel", "Spiegel", "spiegel.de"))
        assertEquals("Haushaltsstreit: Finanzprobleme der Koalition werden größer - Video", GoogleNews.stripPublisher("Haushaltsstreit: Finanzprobleme der Koalition werden größer - Video - WELT", "WELT", "welt.de"))
        // Never down to nothing.
        assertEquals("SZ.de", GoogleNews.stripPublisher("SZ.de - SZ.de", "SZ.de", "sueddeutsche.de"))
        assertEquals("Politik", GoogleNews.stripPublisher("Politik - SZ.de - SZ.de", "SZ.de"))
    }

    @Test
    fun `domains and teasers`() {
        assertEquals("bbc.co.uk", GoogleNews.domainOf("https://www.bbc.co.uk/news/x"))
        assertEquals("", GoogleNews.domainOf("kein link"))
        assertEquals("", GoogleNews.teaser("Dax springt auf Rekordhoch  Spiegel", "Dax springt auf Rekordhoch", "Spiegel"))
        assertEquals("", GoogleNews.teaser("Eins RND.de\nZwei Spiegel\nDrei ZEIT", "Eins", "RND.de"))
        val real = "Der Leitindex legte am Donnerstag um mehr als zwei Prozent zu, getragen von Banken, Autowerten und Versorgern."
        assertEquals(real, GoogleNews.teaser("Dax steigt $real", "Dax steigt", ""))
    }

    @Test
    fun `a search reads the English editions too unless the edition is English`() {
        val gb = NewsEdition("GB", "en")
        assertEquals(listOf(NewsEdition.GERMANY, NewsEdition.USA, gb), GoogleNews.searchEditions(NewsEdition.GERMANY, allLanguages = true))
        assertEquals(listOf(NewsEdition.GERMANY), GoogleNews.searchEditions(NewsEdition.GERMANY, allLanguages = false))
        assertEquals(listOf(NewsEdition.USA), GoogleNews.searchEditions(NewsEdition.USA, allLanguages = true))
        assertEquals(listOf(gb), GoogleNews.searchEditions(gb, allLanguages = true))
        assertEquals(listOf(NewsEdition("FR", "fr"), NewsEdition.USA, gb), GoogleNews.searchEditions(NewsEdition("FR", "fr"), allLanguages = true))
        // The user's query as Google gets it, with exclusions and a date window around it.
        val q = GoogleNews.buildQuery(GoogleNews.userQuery("SLV ETF"), listOf("wsj.com"), GoogleNews.DateWindow(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 6)))
        assertEquals("after:2026-10-29 before:2026-11-06 \"SLV\" ETF -site:wsj.com", q)
    }

    @Test
    fun `quote pages and subscriber-only headlines are recognized`() {
        assertTrue(GoogleNews.isQuotePage("DAX Turbo S 27083.00813 open end (SG) | Kurs, Chart, Analysen | DE000FG2TZV5"))
        assertFalse(GoogleNews.isQuotePage("Dax steigt auf Rekordhoch – drei Treiber für den Leitindex"))
        assertFalse(GoogleNews.isQuotePage("NASA SPACEX launch delayed to 2027"))
        // Quote, chart and fund pages a search for "SLV ETF" or "Rheinmetall Aktie" brought (05.11.2026).
        listOf(
            "iShares Silver Trust (SLV) Stock Price, News, Quote & History",
            "iShares Silver Trust (SLV) stock price, news, quote and history",
            "SLV: iShares Silver Stock Price Today, News, Analysis, and Trading Ideas",
            "SLV ETF Price and Chart — AMEX:SLV",
            "SLV ETF-Preis und Chart — AMEX:SLV",
            "SLV-ETF Bestandsliste — AMEX:SLV",
            "SLV ETF-Profil: Dividenden, Renditen (AMEX:SLV)",
            "iShares Silver Trust (SLV) Aktienkurs, Nachrichten, Kurs und Verlauf",
            "iShares Silver Trust (SLV) Bestände",
            "iShares Silver Trust (SLV) Optionskette",
            "iShares Silver Trust (SLV) Performance-Verlauf",
            "CBOE SILVER ETF VOLATILITY INDE (^VXSLV) Charts, Daten und Nachrichten",
            "Silver ETF VIX aktuell | Index | Stand | Kurs",
            "Direct Market Index: Kurs, Chart & Einzelwerte",
            "Rheinmetall (XTRA:RHM) Aktie: Kurs, Prognose & Analyse",
            "Aktueller SLV ETF | SLV Kurs & Chart",
            "SLV – iShares Silver Trust – ETF Stock Quote",
            "USO vs SLV - ETF Comparison",
        ).forEach { assertTrue(it, GoogleNews.isQuotePage(it)) }
        // News about the same, which must stay.
        listOf(
            "Silver and SLV ETF Prices Lost Crucial Support: Will US NFP Data Turn the Tide?",
            "Silver ETF (SLV) Hits New 52-Week High",
            "Does iShares SLV ETF Really Hold Silver? (NYSEARCA:SLV)",
            "Silber Preis: 109,66 Tonnen Zufluss in SLV",
            "Rheinmetall-Aktie: Kurs gibt nach",
            "Nvidia-Aktienkursprognose: Aktie fällt nach siebentägiger Verlustserie auf fast Dreimonatstief",
            "Is NVIDIA Stock A Buy Right Now?",
            "Rheinmetall Stock 2026: Why It Slumped and What Comes Next",
            "FINMA genehmigt ersten Schweizer Bitcoin-ETF – mit Verspätung, aber mit Fondsschutz | News | Aktuell",
            "Champions League highlights: Fenerbahçe 3-2 Liverpool | Highlights",
        ).forEach { assertFalse(it, GoogleNews.isQuotePage(it)) }
        assertTrue(GoogleNews.isPaywallHeadline("(S+) Elon Musk wollte die US-Midterms für Trump retten"))
        assertTrue(GoogleNews.isPaywallHeadline("Z+ Wie die Demokraten gewannen"))
        assertTrue(GoogleNews.isPaywallHeadline("Analyse: WELTplus – Was jetzt kommt"))
        assertFalse(GoogleNews.isPaywallHeadline("Zverev verliert in Paris"))
        assertFalse(GoogleNews.isPaywallHeadline("C++ 26 bringt Reflection"))
    }
}

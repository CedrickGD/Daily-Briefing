package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Ranking with the answers Google News gave on 05.11.2026 (headlines, publishers, dates as
 * seen; abridged), in the German edition and the English ones.
 */
class SearchRankingTest {

    private val now: Instant = Instant.parse("2026-11-05T12:00:00Z")

    private fun gn(headline: String, day: String, domain: String, edition: String = "DE:de", summary: String = "") = NewsItem(
        id = Integer.toHexString((headline + edition).hashCode()),
        headline = headline,
        summary = summary,
        url = "https://news.google.com/rss/articles/CBMi${(headline + day).hashCode()}",
        source = domain,
        sourceId = "gn:search@$edition",
        topicId = "search",
        publishedAt = "${day}T10:00:00Z",
        publisherDomain = domain,
        aggregator = "google",
    )

    private fun ranked(query: String, main: List<NewsItem>, vararg english: List<NewsItem>, cached: List<NewsItem> = emptyList()) =
        NewsRepository.searchResults(
            SearchQuery.parse(query),
            cached,
            listOf(SearchRanking.Found(main, main = true)) + english.map { SearchRanking.Found(it, main = false) },
            now,
        ).map { it.headline }

    @Test
    fun `SLV ETF - stories naming SLV first, quote pages and old explainers out`() {
        val german = listOf(
            gn("SLV ETF-Profil: Dividenden, Renditen (AMEX:SLV)", "2026-07-14", "tradingview.com"),
            gn("Morgan Stanley mit riesigem Investment beim weltgrößten Silber-ETF", "2026-08-18", "boerse-online.de"),
            gn("Silber-ETF vor dem nächsten Sprung? Wichtige Marke rückt in den Fokus", "2026-06-01", "investing.com"),
            gn("Silber Preis: 109,66 Tonnen Zufluss in SLV", "2026-07-27", "boerse-express.com"),
            gn("iShares Silver Trust (SLV) Aktienkurs, Nachrichten, Kurs und Verlauf", "2017-04-28", "yahoo.com"),
            gn("SLV Prognose: Jetzt entscheidet sich, ob Silber den nächsten Schub startet", "2026-05-26", "investing.com"),
        )
        val us = listOf(
            gn("Silver and SLV ETF Prices Lost Crucial Support: Will US NFP Data Turn the Tide?", "2026-10-31", "benzinga.com", "US:en"),
            gn("This Silver ETF Has Been Crashing. Is Now the Time to Buy?", "2026-06-16", "yahoo.com", "US:en"),
            gn("SLV: The Largest Silver ETF in the World -- but Is It Still the Best Way to Own Silver in 2026?", "2026-05-01", "fool.com", "US:en"),
            gn("iShares Silver Trust (SLV) Stock Price, News, Quote & History", "2017-04-13", "yahoo.com", "US:en"),
            gn("SLV ETF Price and Chart — AMEX:SLV", "2017-07-19", "tradingview.com", "US:en"),
            gn("SLV: iShares Silver Trust ETF", "2017-03-25", "investopedia.com", "US:en"),
        )
        assertEquals(
            listOf(
                "Silver and SLV ETF Prices Lost Crucial Support: Will US NFP Data Turn the Tide?",
                "SLV: The Largest Silver ETF in the World -- but Is It Still the Best Way to Own Silver in 2026?",
                "Silber Preis: 109,66 Tonnen Zufluss in SLV",
                "SLV Prognose: Jetzt entscheidet sich, ob Silber den nächsten Schub startet",
                // Silver-ETF stories without the ticker follow.
                "Morgan Stanley mit riesigem Investment beim weltgrößten Silber-ETF",
                "Silber-ETF vor dem nächsten Sprung? Wichtige Marke rückt in den Fokus",
                "This Silver ETF Has Been Crashing. Is Now the Time to Buy?",
            ),
            ranked("SLV ETF", german, us),
        )
    }

    @Test
    fun `Rheinmetall Aktie - the German stories lead, English ones that name it follow, the rest goes`() {
        val german = listOf(
            gn("Rheinmetall-Aktie stürzt um 16 Prozent ab – das sind die Gründe", "2026-11-05", "bild.de"),
            gn("Rheinmetall Aktie News: Rheinmetall am Donnerstagnachmittag massiv unter Druck", "2026-11-05", "finanzen.ch"),
            gn("Nach Absturz: Das sagen die Analysten jetzt zur Aktie von Rheinmetall", "2026-11-05", "boerse-online.de"),
            gn("Rheinmetall kassiert Jahresziele: Aktie gerät unter Druck - Erster Analyst reagiert", "2026-11-04", "finanzen.net"),
        )
        val us = listOf(
            gn("Rheinmetall Stock 2026: Why It Slumped and What Comes Next", "2026-10-16", "admirals.com", "US:en"),
            gn("Direct Market Index: Kurs, Chart & Einzelwerte", "2026-11-03", "boerse-express.com", "US:en"),
            gn("JPMorgan Leads Analyst Chorus on Rheinmetall Shares (ETR: RHM)", "2025-07-06", "asktraders.com", "US:en"),
            gn("Rheinmetall (XTRA:RHM) Aktie: Kurs, Prognose & Analyse", "2024-04-03", "simplywall.st", "US:en"),
            gn("Bundeswehr klagt über Probleme bei Rheinmetall", "2026-08-20", "capital.de", "US:en"),
        )
        val result = ranked("Rheinmetall Aktie", german, us)
        assertEquals(german.map { it.headline }, result.take(4))
        assertEquals(setOf("Rheinmetall Stock 2026: Why It Slumped and What Comes Next", "Bundeswehr klagt über Probleme bei Rheinmetall"), result.drop(4).toSet())
    }

    @Test
    fun `Champions League - Google's top hits without the words stay, but after those with them`() {
        val german = listOf(
            gn("Champions League: RB Leipzig verliert 2:3 gegen Manchester City", "2026-11-05", "bild.de"),
            gn("Haaland hat das letzte Wort: Leipzig kommt zweimal zurück und verliert doch", "2026-11-04", "kicker.de"),
            gn("RB Leipzig: Bundestrainer Klopp nominiert Gruda für DFB-Team", "2026-11-05", "lvz.de"),
        ) + (1..10).map { gn("Bundesliga-Meldung Nummer $it vom Spieltag", "2026-11-03", "sport.de") } + listOf(
            gn("Como schreibt Geschichte! Höchster Champions-League-Sieg aller Zeiten", "2026-11-04", "sport1.de"),
            // Beyond Google's first ten, no word in headline nor teaser: Google saw more than the app, but the teaser says enough.
            gn("Transfermarkt: Wer im Winter kommt", "2026-11-04", "sport.de", summary = "Die Vereine planen den Winter, Gerüchte um Neuzugänge und Abgänge machen die Runde in der Liga."),
        )
        val gb = listOf(gn("Champions League highlights - RB Leipzig 2-3 Manchester City", "2026-11-04", "bbc.co.uk", "GB:en"))
        val result = ranked("Champions League", german, gb)
        assertEquals(
            listOf(
                "Champions League: RB Leipzig verliert 2:3 gegen Manchester City",
                "Champions League highlights - RB Leipzig 2-3 Manchester City",
                // Only Google's 14th hit in the German edition, but it names the competition.
                "Como schreibt Geschichte! Höchster Champions-League-Sieg aller Zeiten",
            ),
            result.take(3),
        )
        // Then Google's own top hits without the words, in Google's order.
        assertEquals("Haaland hat das letzte Wort: Leipzig kommt zweimal zurück und verliert doch", result[3])
        assertEquals("RB Leipzig: Bundestrainer Klopp nominiert Gruda für DFB-Team", result[4])
        assertTrue("Transfermarkt: Wer im Winter kommt" !in result)
    }

    @Test
    fun `Bundestagswahl - old English-edition pages stay out, a top hit without the word comes last`() {
        val german = listOf(
            gn("Umfragewerte im Bund: Wie die Bürgerinnen und Bürger wählen würden", "2026-11-05", "rnd.de"),
            gn("Umfrage zur Bundestagswahl: Politbarometer: AfD bleibt vor CDU", "2026-10-23", "zdf.de"),
            gn("Neuauszählung der Bundestagswahl: Letzte Hoffnung für das BSW", "2026-02-11", "deutschlandfunk.de"),
        )
        val us = listOf(
            gn("Bundestagswahl 2025: The Grand Coalition?", "2025-02-10", "rothschildandco.com", "US:en"),
            gn("Welche Themen entschieden die Wahl?", "2021-09-26", "tagesschau.de", "US:en"),
            gn("Bundestagswahl am 24. September 2017", "2017-09-24", "wahlrecht.de", "US:en"),
        )
        assertEquals(
            listOf(
                "Umfrage zur Bundestagswahl: Politbarometer: AfD bleibt vor CDU",
                "Neuauszählung der Bundestagswahl: Letzte Hoffnung für das BSW",
                "Umfragewerte im Bund: Wie die Bürgerinnen und Bürger wählen würden",
            ),
            ranked("Bundestagswahl", german, us),
        )
    }

    @Test
    fun `forums go, pages older than a year follow the fresh results`() {
        val german = listOf(
            gn("Aktueller SLV ETF | SLV Kurs & Chart", "2019-05-14", "etoro.com"),
            gn("SLV ETF | iShares Silver Preis", "2017-01-13", "investing.com"),
            gn("Silber-ETF vor dem nächsten Sprung?", "2026-06-01", "investing.com"),
        )
        val gb = listOf(
            gn("Has anyone requested physical delivery of their SLV ETF holdings?", "2025-12-31", "reddit.com", "GB:en"),
            gn("SLV Crashes To Near 52-Week Lows, GLD Falls Pre-Market", "2026-11-03", "finance.yahoo.com", "GB:en"),
        )
        assertEquals(
            listOf(
                "SLV Crashes To Near 52-Week Lows, GLD Falls Pre-Market",
                // Only "ETF", but this year's.
                "Silber-ETF vor dem nächsten Sprung?",
                // Both words, but from 2017: after the fresh ones.
                "SLV ETF | iShares Silver Preis",
            ),
            ranked("SLV ETF", german, gb),
        )
    }

    @Test
    fun `the same story from two editions or publishers shows once`() {
        val german = listOf(
            gn("James Wynn bleibt bei seiner Prognose eines Bitcoin-Crashs, trotz neuer ETF-Zuflüsse", "2026-11-05", "beincrypto.com"),
            gn("Bitcoin ETF Abflüsse über 1 Milliarde Dollar: Warum Institutionelle nach den Midterms verkaufen", "2026-11-04", "cryptoticker.io"),
            gn("James Wynn bleibt bei seiner Prognose zum Bitcoin-Crash trotz neuer ETF-Zuflüsse", "2026-11-05", "cryptonews.net"),
        )
        val shared = gn("Bitcoin ETFs Lose \$1 Billion as Midterms, Binance Hack Weigh on Sentiment", "2026-11-03", "bloomberg.com", "US:en")
        val result = ranked("Bitcoin ETF", german, listOf(shared), listOf(shared.copy(sourceId = "gn:search@GB:en")))
        assertEquals(1, result.count { it.startsWith("James Wynn") })
        assertEquals(1, result.count { it.startsWith("Bitcoin ETFs Lose") })
        assertEquals(3, result.size)
    }

    @Test
    fun `an article the phone holds shows as its own copy and freshness is mild`() {
        val held = gn("Midterms: Machtwechsel in Texas stellt die US-Politik auf den Kopf", "2026-11-05", "handelsblatt.com").copy(topicId = "politik/usa", sourceId = "handelsblatt")
        val found = listOf(gn("Midterms: Machtwechsel in Texas stellt die US-Politik auf den Kopf", "2026-11-05", "handelsblatt.com"))
        val result = NewsRepository.searchResults(SearchQuery.parse("Midterms"), listOf(held), listOf(SearchRanking.Found(found, main = true)), now)
        assertEquals(listOf("politik/usa"), result.map { it.topicId })
        assertEquals(16.0, SearchRanking.freshness(0), 0.0)
        assertEquals(0.0, SearchRanking.freshness(200), 0.0)
        assertEquals(-12.0, SearchRanking.freshness(400), 0.0)
    }

    @Test
    fun `an old analysis naming the ticker ranks after this week's stories`() {
        val us = listOf(
            gn("SLV ETF stock analysis as silver prices momentum gains steam", "2025-12-12", "tradingview.com", "US:en"),
            gn("Silver and SLV ETF Prices Lost Crucial Support: Will US NFP Data Turn the Tide?", "2026-10-31", "benzinga.com", "US:en"),
            gn("Silver ETFs rally as SLV draws record inflows", "2026-11-04", "cnbc.com", "US:en"),
        )
        val result = ranked("SLV ETF", emptyList(), us)
        assertEquals(3, result.size)
        // The phrase match outranks the bare ticker among this week's stories; the 328-day-old analysis comes last.
        assertEquals("SLV ETF stock analysis as silver prices momentum gains steam", result.last())
    }

    @Test
    fun `followed searches keep only English articles that name the query`() {
        val items = listOf(
            gn("BTC jumps as ETF flows return", "2026-11-05", "coindesk.com", "US:en"),
            gn("Weather turns cold this weekend", "2026-11-05", "weather.com", "US:en"),
            gn("BTC hits a record in a year long gone", "2024-03-01", "coindesk.com", "US:en"),
        )
        assertEquals(listOf("BTC jumps as ETF flows return"), SearchRanking.relevantExtra(SearchQuery.parse("BTC"), items, now).map { it.headline })
    }
}

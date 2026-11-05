package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topics

/**
 * A built-in publisher feed and the news editions it belongs to.
 *
 * @property language ISO 639-1 of the feed's articles.
 * @property countries the editions' countries (ISO alpha-2) that show it; empty = every
 *   edition of [language] (a world or science desk), set for domestic desks (tagesschau
 *   Inland → `DE`, NPR Politics → `US`).
 */
data class CatalogFeed(
    val source: FeedSource,
    val language: String,
    val countries: Set<String> = emptySet(),
) {
    /** Whether readers of [edition] get this feed. */
    fun serves(edition: NewsEdition): Boolean =
        language == edition.language && (countries.isEmpty() || edition.country in countries)
}

/**
 * The built-in RSS/Atom sources: newsrooms with public feeds whose article pages the in-app
 * reader can show, each attached to the (sub-)topic it covers best. A category also reads the
 * feeds of its sub-topics ([TopicSources]). Every feed was checked with curl and the app's
 * headers (German ones 29./30.10. and again 05.11.2026, English ones 05.11.2026, each with one
 * article page under a mobile Chrome UA). Left out on purpose: PBS NewsHour and ESPN (bot wall
 * on every article), Politico US, Sky News, phys.org (403), MarketWatch (401), USA Today (402),
 * Yahoo Finance and Investopedia (no feed any more).
 *
 * Every source can be switched off in the settings; the user adds their own with
 * [AppSettings.customFeeds]. The "Region" topic takes its sources from [Regions].
 */
object FeedCatalog {

    private fun de(id: String, name: String, topic: String, url: String, vararg countries: String) =
        CatalogFeed(FeedSource(id, name, url, topic), "de", countries.toSet())

    private fun en(id: String, name: String, topic: String, url: String, vararg countries: String) =
        CatalogFeed(FeedSource(id, name, url, topic), "en", countries.toSet())

    private const val DE = "DE"
    private val US = arrayOf("US")
    private val UK = arrayOf("GB", "IE")
    private val NORTH_AMERICA = arrayOf("US", "CA")

    val entries: List<CatalogFeed> = listOf(
        // ---- Deutsch ------------------------------------------------------------------------

        // Schlagzeilen
        de("tagesschau-top", "tagesschau", "top", "https://www.tagesschau.de/index~rss2.xml", DE),
        de("spiegel-top", "SPIEGEL", "top", "https://www.spiegel.de/schlagzeilen/index.rss", DE),
        de("ntv-top", "n-tv", "top", "https://www.n-tv.de/rss", DE),
        // General news desks: home and world stories mixed (Aiwanger's death, the DFB squad;
        // 05.11.2026), so they are top stories, not world news.
        de("dlf-nachrichten", "Deutschlandfunk", "top", "https://www.deutschlandfunk.de/nachrichten-100.rss", DE),
        de("zdfheute", "ZDFheute", "top", "https://www.zdf.de/rss/zdf/nachrichten", DE),
        de("dw", "Deutsche Welle", "top", "https://rss.dw.com/rdf/rss-de-all"),

        // Tech & KI
        de("heise-top", "heise online", "ki", "https://www.heise.de/rss/heise-top-atom.xml"),
        de("golem", "Golem.de", "ki", "https://rss.golem.de/rss.php?feed=RSS2.0"),
        de("t3n", "t3n", "ki", "https://t3n.de/rss.xml"),
        de("spiegel-netzwelt", "SPIEGEL Netzwelt", "ki", "https://www.spiegel.de/netzwelt/index.rss"),
        de("zeit-digital", "ZEIT Digital", "ki", "https://newsfeed.zeit.de/digital/index"),
        de("sz-digital", "SZ Digital", "ki", "https://rss.sueddeutsche.de/rss/Digital"),
        de("the-decoder", "The Decoder", "ki/ki", "https://the-decoder.de/feed/"),
        de("heise-mobil", "heise Mobiles", "ki/smartphones", "https://www.heise.de/rss/heise-Rubrik-Mobiles-atom.xml"),
        de("spiegel-gadgets", "SPIEGEL Gadgets", "ki/smartphones", "https://www.spiegel.de/netzwelt/gadgets/index.rss"),
        de("heise-it", "heise IT", "ki/software", "https://www.heise.de/rss/heise-Rubrik-IT-atom.xml"),
        de("heise-security", "heise Security", "ki/security", "https://www.heise.de/security/rss/news-atom.xml"),
        de("computerbase", "ComputerBase", "ki/hardware", "https://www.computerbase.de/rss/news.xml"),
        de("netzpolitik", "netzpolitik.org", "ki/netz", "https://netzpolitik.org/feed/"),
        de("heise-netzpolitik", "heise Netzpolitik", "ki/netz", "https://www.heise.de/rss/heise-Rubrik-Netzpolitik-atom.xml"),
        de("spiegel-web", "SPIEGEL Web", "ki/netz", "https://www.spiegel.de/netzwelt/web/index.rss"),

        // Wirtschaft & Finanzen
        de("tagesschau-wirtschaft", "tagesschau", "wirtschaft", "https://www.tagesschau.de/wirtschaft/index~rss2.xml"),
        // Handelsblatt's and WirtschaftsWoche's "Schlagzeilen" are general news (Aiwanger's death,
        // bomb threats, GTA 6; 05.11.2026); their finance and companies desks are business only.
        de("handelsblatt", "Handelsblatt", "wirtschaft", "https://feeds.cms.handelsblatt.com/finanzen"),
        de("spiegel-wirtschaft", "SPIEGEL Wirtschaft", "wirtschaft", "https://www.spiegel.de/wirtschaft/index.rss"),
        de("zeit-wirtschaft", "ZEIT Wirtschaft", "wirtschaft", "https://newsfeed.zeit.de/wirtschaft/index"),
        de("faz-wirtschaft", "FAZ Wirtschaft", "wirtschaft", "https://www.faz.net/rss/aktuell/wirtschaft/"),
        de("sz-wirtschaft", "SZ Wirtschaft", "wirtschaft", "https://rss.sueddeutsche.de/rss/Wirtschaft"),
        de("wiwo", "WirtschaftsWoche", "wirtschaft", "https://feeds.cms.wiwo.de/rss/unternehmen"),
        de("ntv-wirtschaft", "n-tv Wirtschaft", "wirtschaft", "https://www.n-tv.de/wirtschaft/rss"),
        de("tagesschau-finanzen", "tagesschau Finanzen", "wirtschaft/boerse", "https://www.tagesschau.de/wirtschaft/finanzen/index~rss2.xml"),
        de("ntv-boerse", "n-tv Börse", "wirtschaft/boerse", "https://www.n-tv.de/wirtschaft/boerse/rss"),
        de("btc-echo", "BTC-ECHO", "wirtschaft/krypto", "https://www.btc-echo.de/feed/"),
        de("tagesschau-konjunktur", "tagesschau Konjunktur", "wirtschaft/konjunktur", "https://www.tagesschau.de/wirtschaft/konjunktur/index~rss2.xml"),
        de("tagesschau-unternehmen", "tagesschau Unternehmen", "wirtschaft/unternehmen", "https://www.tagesschau.de/wirtschaft/unternehmen/index~rss2.xml"),
        de("spiegel-unternehmen", "SPIEGEL Unternehmen", "wirtschaft/unternehmen", "https://www.spiegel.de/wirtschaft/unternehmen/index.rss"),
        de("tagesschau-verbraucher", "tagesschau Verbraucher", "wirtschaft/geld", "https://www.tagesschau.de/wirtschaft/verbraucher/index~rss2.xml", DE),
        de("gruenderszene", "Gründerszene", "wirtschaft/startups", "https://www.businessinsider.de/gruenderszene/feed/"),

        // Politik
        de("tagesschau-inland", "tagesschau", "politik/inland", "https://www.tagesschau.de/inland/index~rss2.xml", DE),
        de("spiegel-politik", "SPIEGEL Politik", "politik", "https://www.spiegel.de/politik/index.rss", DE),
        de("zeit-politik", "ZEIT Politik", "politik", "https://newsfeed.zeit.de/politik/index", DE),
        de("faz-politik", "FAZ Politik", "politik", "https://www.faz.net/rss/aktuell/politik/", DE),
        de("sz-politik", "SZ Politik", "politik", "https://rss.sueddeutsche.de/rss/Politik", DE),
        de("dlf-politik", "Deutschlandfunk", "politik", "https://www.deutschlandfunk.de/politikportal-100.rss", DE),
        de("welt-politik", "WELT Politik", "politik", "https://www.welt.de/feeds/section/politik.rss", DE),
        de("taz-politik", "taz Politik", "politik", "https://taz.de/!p4615;rss/", DE),

        // Welt
        de("tagesschau-ausland", "tagesschau", "welt", "https://www.tagesschau.de/ausland/index~rss2.xml"),
        de("spiegel-ausland", "SPIEGEL Ausland", "welt", "https://www.spiegel.de/ausland/index.rss"),
        de("tagesschau-europa", "tagesschau Europa", "welt/europa", "https://www.tagesschau.de/ausland/europa/index~rss2.xml"),
        de("tagesschau-amerika", "tagesschau Amerika", "welt/amerika", "https://www.tagesschau.de/ausland/amerika/index~rss2.xml"),
        de("tagesschau-asien", "tagesschau Asien", "welt/asien", "https://www.tagesschau.de/ausland/asien/index~rss2.xml"),
        de("tagesschau-afrika", "tagesschau Afrika", "welt/afrika", "https://www.tagesschau.de/ausland/afrika/index~rss2.xml"),

        // Sport
        de("kicker", "kicker", "sport", "https://newsfeed.kicker.de/news/aktuell"),
        de("sportschau", "Sportschau", "sport", "https://www.sportschau.de/index~rss2.xml"),
        de("spiegel-sport", "SPIEGEL Sport", "sport", "https://www.spiegel.de/sport/index.rss"),
        de("zeit-sport", "ZEIT Sport", "sport", "https://newsfeed.zeit.de/sport/index"),
        de("sz-sport", "SZ Sport", "sport", "https://rss.sueddeutsche.de/rss/Sport"),
        de("ntv-sport", "n-tv Sport", "sport", "https://www.n-tv.de/sport/rss"),
        de("sportschau-fussball", "Sportschau Fußball", "sport/fussball", "https://www.sportschau.de/fussball/index~rss2.xml"),
        de("spiegel-fussball", "SPIEGEL Fußball", "sport/fussball", "https://www.spiegel.de/sport/fussball/index.rss"),
        de("sportschau-motorsport", "Sportschau Motorsport", "sport/motorsport", "https://www.sportschau.de/motorsport/index~rss2.xml"),
        de("spiegel-formel1", "SPIEGEL Formel 1", "sport/motorsport", "https://www.spiegel.de/sport/formel1/index.rss"),
        de("sportschau-tennis", "Sportschau Tennis", "sport/tennis", "https://www.sportschau.de/tennis/index~rss2.xml"),
        de("sportschau-basketball", "Sportschau Basketball", "sport/basketball", "https://www.sportschau.de/basketball/index~rss2.xml"),
        de("sportschau-football", "Sportschau Football", "sport/football", "https://www.sportschau.de/american-football/index~rss2.xml"),
        de("sportschau-wintersport", "Sportschau Wintersport", "sport/wintersport", "https://www.sportschau.de/wintersport/index~rss2.xml"),

        // Wissenschaft
        de("spektrum", "Spektrum.de", "wissenschaft", "https://www.spektrum.de/alias/rss/spektrum-de-rss-feed/996406"),
        de("scinexx", "scinexx", "wissenschaft", "https://www.scinexx.de/feed/"),
        de("tagesschau-wissen", "tagesschau Wissen", "wissenschaft", "https://www.tagesschau.de/wissen/index~rss2.xml"),
        de("spiegel-wissenschaft", "SPIEGEL Wissenschaft", "wissenschaft", "https://www.spiegel.de/wissenschaft/index.rss"),
        de("wissenschaft-de", "wissenschaft.de", "wissenschaft", "https://www.wissenschaft.de/feed/"),
        de("zdf-wissen", "ZDF Wissen", "wissenschaft", "https://www.zdf.de/rss/zdf/nachrichten/wissen"),
        de("quarks", "Quarks", "wissenschaft", "https://www.quarks.de/feed/"),
        de("mdr-wissen", "MDR Wissen", "wissenschaft", "https://www.mdr.de/wissen/index-rss.xml"),
        de("esa", "ESA", "wissenschaft/raumfahrt", "https://www.esa.int/rssfeed/Germany"),
        de("raumfahrer", "Raumfahrer.net", "wissenschaft/raumfahrt", "https://www.raumfahrer.net/feed/"),
        de("spiegel-geschichte", "SPIEGEL Geschichte", "wissenschaft/geschichte", "https://www.spiegel.de/geschichte/index.rss"),

        // Gesundheit
        de("spiegel-gesundheit", "SPIEGEL Gesundheit", "gesundheit", "https://www.spiegel.de/gesundheit/index.rss"),
        de("zeit-gesundheit", "ZEIT Gesundheit", "gesundheit", "https://newsfeed.zeit.de/gesundheit/index"),
        de("sz-gesundheit", "SZ Gesundheit", "gesundheit", "https://rss.sueddeutsche.de/rss/Gesundheit"),
        de("tagesschau-gesundheit", "tagesschau Gesundheit", "gesundheit", "https://www.tagesschau.de/wissen/gesundheit/index~rss2.xml"),
        de("aerzteblatt", "Ärzteblatt", "gesundheit/medizin", "https://www.aerzteblatt.de/rss/news.asp"),
        de("spiegel-psychologie", "SPIEGEL Psychologie", "gesundheit/psyche", "https://www.spiegel.de/psychologie/index.rss"),

        // Klima & Energie
        de("taz-oeko", "taz Öko", "klima", "https://taz.de/!p4610;rss/"),
        de("riffreporter", "RiffReporter", "klima", "https://www.riffreporter.de/de/rss"),
        de("tagesschau-klima", "tagesschau Klima", "klima/klimawandel", "https://www.tagesschau.de/wissen/klima/index~rss2.xml"),
        de("klimareporter", "klimareporter°", "klima/klimawandel", "https://www.klimareporter.de/?format=feed&type=rss"),
        de("energiezukunft", "energiezukunft", "klima/energie", "https://www.energiezukunft.eu/rss"),
        de("tagesschau-energie", "tagesschau Energie", "klima/energie", "https://www.tagesschau.de/wirtschaft/energie/index~rss2.xml"),
        de("dlf-umwelt", "Deutschlandfunk Umwelt", "klima/umwelt", "https://www.deutschlandfunk.de/umwelt-und-verbraucher-100.rss"),
        de("spiegel-natur", "SPIEGEL Natur", "klima/umwelt", "https://www.spiegel.de/wissenschaft/natur/index.rss"),
        de("utopia", "Utopia", "klima/umwelt", "https://www.utopia.de/feed/"),

        // Kultur & Unterhaltung
        de("spiegel-kultur", "SPIEGEL Kultur", "entertainment", "https://www.spiegel.de/kultur/index.rss"),
        de("tagesschau-kultur", "tagesschau Kultur", "entertainment", "https://www.tagesschau.de/kultur/index~rss2.xml"),
        de("sz-kultur", "SZ Kultur", "entertainment", "https://rss.sueddeutsche.de/rss/Kultur"),
        de("dlf-kultur", "Deutschlandfunk Kultur", "entertainment", "https://www.deutschlandfunk.de/kulturportal-100.rss"),
        de("gamestar", "GameStar", "entertainment/games", "https://www.gamestar.de/rss/gamestar.rss"),
        de("gamepro", "GamePro", "entertainment/games", "https://www.gamepro.de/rss/gamepro.rss"),
        de("golem-games", "Golem.de Games", "entertainment/games", "https://rss.golem.de/rss.php?feed=RSS2.0&tp=games"),
        de("spiegel-games", "SPIEGEL Games", "entertainment/games", "https://www.spiegel.de/netzwelt/games/index.rss"),
        de("moviepilot", "Moviepilot", "entertainment/film", "https://www.moviepilot.de/rss/moviepilot-standard"),
        de("serienjunkies", "Serienjunkies", "entertainment/film", "https://www.serienjunkies.de/rss/news.xml"),
        de("spiegel-kino", "SPIEGEL Kino", "entertainment/film", "https://www.spiegel.de/kultur/kino/index.rss"),
        de("spiegel-musik", "SPIEGEL Musik", "entertainment/musik", "https://www.spiegel.de/kultur/musik/index.rss"),
        de("spiegel-literatur", "SPIEGEL Literatur", "entertainment/buecher", "https://www.spiegel.de/kultur/literatur/index.rss"),
        de("spiegel-leute", "SPIEGEL Leute", "entertainment/leute", "https://www.spiegel.de/panorama/leute/index.rss"),

        // Auto & Mobilität
        de("heise-autos", "heise Autos", "auto", "https://www.heise.de/autos/rss/news-atom.xml"),
        de("electrive-de", "electrive.net", "auto/elektro", "https://www.electrive.net/feed/"),

        // ---- English ------------------------------------------------------------------------

        // Top stories
        en("npr-news", "NPR", "top", "https://feeds.npr.org/1001/rss.xml", *US),
        en("abc-top", "ABC News", "top", "https://feeds.abcnews.com/abcnews/topstories", *US),
        en("cbs-latest", "CBS News", "top", "https://www.cbsnews.com/latest/rss/main", *US),
        en("bbc-top", "BBC News", "top", "https://feeds.bbci.co.uk/news/rss.xml", *UK),

        // Politics
        en("npr-politics", "NPR Politics", "politik", "https://feeds.npr.org/1014/rss.xml", *US),
        en("cbs-politics", "CBS News Politics", "politik", "https://www.cbsnews.com/latest/rss/politics", *US),
        en("abc-politics", "ABC News Politics", "politik", "https://feeds.abcnews.com/abcnews/politicsheadlines", *US),
        en("thehill", "The Hill", "politik", "https://thehill.com/feed/", *US),
        en("bbc-politics", "BBC Politics", "politik", "https://feeds.bbci.co.uk/news/politics/rss.xml", *UK),
        en("guardian-politics", "The Guardian Politics", "politik", "https://www.theguardian.com/politics/rss", *UK),
        en("guardian-uk", "The Guardian UK", "top", "https://www.theguardian.com/uk-news/rss", *UK),
        en("guardian-us-politics", "The Guardian US Politics", "politik/usa", "https://www.theguardian.com/us-news/us-politics/rss"),
        en("bbc-us", "BBC US & Canada", "politik/usa", "https://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml"),
        en("politico-eu", "POLITICO Europe", "politik/eu", "https://www.politico.eu/feed/"),
        en("guardian-eu", "The Guardian EU", "politik/eu", "https://www.theguardian.com/world/eu/rss"),

        // World
        en("bbc-world", "BBC World", "welt", "https://feeds.bbci.co.uk/news/world/rss.xml"),
        en("guardian-world", "The Guardian World", "welt", "https://www.theguardian.com/world/rss"),
        en("npr-world", "NPR World", "welt", "https://feeds.npr.org/1004/rss.xml", *US),
        en("aljazeera", "Al Jazeera", "welt", "https://www.aljazeera.com/xml/rss/all.xml"),
        en("bbc-europe", "BBC Europe", "welt/europa", "https://feeds.bbci.co.uk/news/world/europe/rss.xml"),
        en("guardian-europe", "The Guardian Europe", "welt/europa", "https://www.theguardian.com/world/europe-news/rss"),
        en("euronews", "Euronews", "welt/europa", "https://www.euronews.com/rss"),
        en("guardian-americas", "The Guardian Americas", "welt/amerika", "https://www.theguardian.com/world/americas/rss"),
        en("bbc-asia", "BBC Asia", "welt/asien", "https://feeds.bbci.co.uk/news/world/asia/rss.xml"),
        en("guardian-asia", "The Guardian Asia", "welt/asien", "https://www.theguardian.com/world/asia/rss"),
        en("bbc-mideast", "BBC Middle East", "welt/nahost", "https://feeds.bbci.co.uk/news/world/middle_east/rss.xml"),
        en("guardian-mideast", "The Guardian Middle East", "welt/nahost", "https://www.theguardian.com/world/middleeast/rss"),
        en("bbc-africa", "BBC Africa", "welt/afrika", "https://feeds.bbci.co.uk/news/world/africa/rss.xml"),
        en("guardian-africa", "The Guardian Africa", "welt/afrika", "https://www.theguardian.com/world/africa/rss"),

        // Business & finance
        en("bbc-business", "BBC Business", "wirtschaft", "https://feeds.bbci.co.uk/news/business/rss.xml"),
        en("guardian-business", "The Guardian Business", "wirtschaft", "https://www.theguardian.com/business/rss"),
        en("npr-business", "NPR Business", "wirtschaft", "https://feeds.npr.org/1006/rss.xml", *US),
        en("cnbc-top", "CNBC", "wirtschaft", "https://www.cnbc.com/id/100003114/device/rss/rss.html", *NORTH_AMERICA),
        en("cnbc-investing", "CNBC Investing", "wirtschaft/trading", "https://www.cnbc.com/id/15839069/device/rss/rss.html", *NORTH_AMERICA),
        en("coindesk", "CoinDesk", "wirtschaft/krypto", "https://www.coindesk.com/arc/outboundfeeds/rss/"),
        en("cointelegraph", "Cointelegraph", "wirtschaft/krypto", "https://cointelegraph.com/rss"),
        en("decrypt", "Decrypt", "wirtschaft/krypto", "https://decrypt.co/feed"),
        en("cnbc-economy", "CNBC Economy", "wirtschaft/konjunktur", "https://www.cnbc.com/id/20910258/device/rss/rss.html", *NORTH_AMERICA),
        en("cnbc-earnings", "CNBC Earnings", "wirtschaft/unternehmen", "https://www.cnbc.com/id/15839135/device/rss/rss.html", *NORTH_AMERICA),
        en("cnbc-personal-finance", "CNBC Personal Finance", "wirtschaft/geld", "https://www.cnbc.com/id/21324812/device/rss/rss.html", *US),
        en("guardian-money", "The Guardian Money", "wirtschaft/geld", "https://www.theguardian.com/money/rss", *UK),
        en("cnbc-real-estate", "CNBC Real Estate", "wirtschaft/immobilien", "https://www.cnbc.com/id/10000115/device/rss/rss.html", *US),
        en("guardian-property", "The Guardian Property", "wirtschaft/immobilien", "https://www.theguardian.com/money/property/rss", *UK),
        en("techcrunch", "TechCrunch", "wirtschaft/startups", "https://techcrunch.com/feed/"),

        // Tech & AI
        en("verge", "The Verge", "ki", "https://www.theverge.com/rss/index.xml"),
        en("ars", "Ars Technica", "ki", "https://feeds.arstechnica.com/arstechnica/index"),
        en("bbc-tech", "BBC Technology", "ki", "https://feeds.bbci.co.uk/news/technology/rss.xml"),
        en("guardian-tech", "The Guardian Technology", "ki", "https://www.theguardian.com/technology/rss"),
        en("npr-tech", "NPR Technology", "ki", "https://feeds.npr.org/1019/rss.xml", *US),
        en("cnbc-tech", "CNBC Tech", "ki", "https://www.cnbc.com/id/19854910/device/rss/rss.html", *NORTH_AMERICA),
        en("verge-ai", "The Verge AI", "ki/ki", "https://www.theverge.com/rss/ai-artificial-intelligence/index.xml"),
        en("guardian-ai", "The Guardian AI", "ki/ki", "https://www.theguardian.com/technology/artificialintelligenceai/rss"),
        en("verge-phones", "The Verge Phones", "ki/smartphones", "https://www.theverge.com/rss/phones/index.xml"),
        en("theregister", "The Register", "ki/software", "https://www.theregister.com/headlines.atom"),
        en("bleepingcomputer", "BleepingComputer", "ki/security", "https://www.bleepingcomputer.com/feed/"),
        en("guardian-security", "The Guardian Cybersecurity", "ki/security", "https://www.theguardian.com/technology/data-computer-security/rss"),
        en("tomshardware", "Tom's Hardware", "ki/hardware", "https://www.tomshardware.com/feeds/all"),

        // Science
        en("bbc-science", "BBC Science", "wissenschaft", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml"),
        en("guardian-science", "The Guardian Science", "wissenschaft", "https://www.theguardian.com/science/rss"),
        en("npr-science", "NPR Science", "wissenschaft", "https://feeds.npr.org/1007/rss.xml", *US),
        en("sciencedaily", "ScienceDaily", "wissenschaft", "https://www.sciencedaily.com/rss/all.xml"),
        en("livescience", "Live Science", "wissenschaft", "https://www.livescience.com/feeds/all"),
        en("ars-science", "Ars Technica Science", "wissenschaft", "https://feeds.arstechnica.com/arstechnica/science"),
        en("nasa", "NASA", "wissenschaft/raumfahrt", "https://www.nasa.gov/news-release/feed/"),
        en("spacecom", "Space.com", "wissenschaft/raumfahrt", "https://www.space.com/feeds/all"),
        en("guardian-space", "The Guardian Space", "wissenschaft/raumfahrt", "https://www.theguardian.com/science/space/rss"),
        en("guardian-physics", "The Guardian Physics", "wissenschaft/physik", "https://www.theguardian.com/science/physics/rss"),
        en("guardian-wildlife", "The Guardian Wildlife", "wissenschaft/natur", "https://www.theguardian.com/environment/wildlife/rss"),
        en("guardian-archaeology", "The Guardian Archaeology", "wissenschaft/geschichte", "https://www.theguardian.com/science/archaeology/rss"),
        en("smithsonian", "Smithsonian Magazine", "wissenschaft/geschichte", "https://www.smithsonianmag.com/rss/latest_articles/"),

        // Health
        en("bbc-health", "BBC Health", "gesundheit", "https://feeds.bbci.co.uk/news/health/rss.xml"),
        en("npr-health", "NPR Health", "gesundheit", "https://feeds.npr.org/1128/rss.xml", *US),
        en("guardian-health", "The Guardian Health", "gesundheit", "https://www.theguardian.com/society/health/rss", *UK),
        en("statnews", "STAT", "gesundheit/medizin", "https://www.statnews.com/feed/"),
        en("psypost", "PsyPost", "gesundheit/psyche", "https://www.psypost.org/feed/"),
        en("guardian-psychology", "The Guardian Psychology", "gesundheit/psyche", "https://www.theguardian.com/science/psychology/rss"),

        // Climate & energy
        en("guardian-environment", "The Guardian Environment", "klima", "https://www.theguardian.com/environment/rss"),
        en("insideclimate", "Inside Climate News", "klima", "https://insideclimatenews.org/feed/"),
        en("carbonbrief", "Carbon Brief", "klima", "https://www.carbonbrief.org/feed/"),
        en("guardian-climate", "The Guardian Climate Crisis", "klima/klimawandel", "https://www.theguardian.com/environment/climate-crisis/rss"),
        en("guardian-energy", "The Guardian Energy", "klima/energie", "https://www.theguardian.com/environment/energy/rss"),

        // Sport
        en("bbc-sport", "BBC Sport", "sport", "https://feeds.bbci.co.uk/sport/rss.xml", *UK),
        en("guardian-sport", "The Guardian Sport", "sport", "https://www.theguardian.com/sport/rss"),
        en("cbssports", "CBS Sports", "sport", "https://www.cbssports.com/rss/headlines/", *US),
        en("bbc-football", "BBC Football", "sport/fussball", "https://feeds.bbci.co.uk/sport/football/rss.xml"),
        en("guardian-football", "The Guardian Football", "sport/fussball", "https://www.theguardian.com/football/rss"),
        en("bbc-f1", "BBC Formula 1", "sport/motorsport", "https://feeds.bbci.co.uk/sport/formula1/rss.xml"),
        en("guardian-f1", "The Guardian Formula One", "sport/motorsport", "https://www.theguardian.com/sport/formulaone/rss"),
        en("autosport", "Autosport", "sport/motorsport", "https://www.autosport.com/rss/f1/news/"),
        en("motorsportcom", "Motorsport.com", "sport/motorsport", "https://www.motorsport.com/rss/f1/news/"),
        en("bbc-tennis", "BBC Tennis", "sport/tennis", "https://feeds.bbci.co.uk/sport/tennis/rss.xml"),
        en("guardian-tennis", "The Guardian Tennis", "sport/tennis", "https://www.theguardian.com/sport/tennis/rss"),
        en("guardian-nba", "The Guardian NBA", "sport/basketball", "https://www.theguardian.com/sport/nba/rss"),
        en("bbc-basketball", "BBC Basketball", "sport/basketball", "https://feeds.bbci.co.uk/sport/basketball/rss.xml"),
        en("cbssports-nba", "CBS Sports NBA", "sport/basketball", "https://www.cbssports.com/rss/headlines/nba/", *US),
        en("guardian-nfl", "The Guardian NFL", "sport/football", "https://www.theguardian.com/sport/nfl/rss"),
        en("bbc-nfl", "BBC American Football", "sport/football", "https://feeds.bbci.co.uk/sport/american-football/rss.xml"),
        en("cbssports-nfl", "CBS Sports NFL", "sport/football", "https://www.cbssports.com/rss/headlines/nfl/", *US),
        en("bbc-winter", "BBC Winter Sports", "sport/wintersport", "https://feeds.bbci.co.uk/sport/winter-sports/rss.xml"),

        // Culture & entertainment
        en("bbc-entertainment", "BBC Entertainment & Arts", "entertainment", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml"),
        en("guardian-culture", "The Guardian Culture", "entertainment", "https://www.theguardian.com/culture/rss"),
        en("variety", "Variety", "entertainment", "https://variety.com/feed/"),
        en("ign", "IGN", "entertainment/games", "https://feeds.ign.com/ign/all"),
        en("polygon", "Polygon", "entertainment/games", "https://www.polygon.com/rss/index.xml"),
        en("kotaku", "Kotaku", "entertainment/games", "https://kotaku.com/rss"),
        en("eurogamer", "Eurogamer", "entertainment/games", "https://www.eurogamer.net/feed"),
        en("guardian-games", "The Guardian Games", "entertainment/games", "https://www.theguardian.com/games/rss"),
        en("guardian-film", "The Guardian Film", "entertainment/film", "https://www.theguardian.com/film/rss"),
        en("hollywoodreporter", "The Hollywood Reporter", "entertainment/film", "https://www.hollywoodreporter.com/feed/"),
        en("deadline", "Deadline", "entertainment/film", "https://deadline.com/feed/"),
        en("guardian-music", "The Guardian Music", "entertainment/musik", "https://www.theguardian.com/music/rss"),
        en("rollingstone", "Rolling Stone", "entertainment/musik", "https://www.rollingstone.com/feed/"),
        en("guardian-books", "The Guardian Books", "entertainment/buecher", "https://www.theguardian.com/books/rss"),

        // Cars & mobility
        en("ars-cars", "Ars Technica Cars", "auto", "https://feeds.arstechnica.com/arstechnica/cars"),
        en("caranddriver", "Car and Driver", "auto", "https://www.caranddriver.com/rss/news.xml/"),
        en("electrek", "Electrek", "auto/elektro", "https://electrek.co/feed/"),
        en("electrive", "electrive.com", "auto/elektro", "https://www.electrive.com/feed/"),
    )

    /** Every built-in feed (all editions), for lookups by id and the settings. */
    val builtIn: List<FeedSource> = entries.map { it.source }

    private val builtInById = builtIn.associateBy { it.id }

    /** The built-in feeds [edition] reads, in catalogue order. */
    fun forEdition(edition: NewsEdition): List<CatalogFeed> = entries.filter { it.serves(edition) }

    /** Every source the app knows for [settings]: built-in, the region's, and the user's own (switched-off ones included). */
    fun allSources(settings: AppSettings): List<FeedSource> =
        builtIn + Regions.byKey(settings.region).feeds + settings.customFeeds

    /** The sources of [topicId] that are switched on, as a refresh reads them ([TopicSources.resolve]). */
    fun sourcesFor(topicId: String, settings: AppSettings): List<FeedSource> = TopicSources.resolve(topicId, settings)

    /** All sources of [topicId], switched-off ones included (for the settings). */
    fun allSourcesFor(topicId: String, settings: AppSettings): List<FeedSource> =
        TopicSources.resolve(topicId, settings, includeDisabled = true)

    fun byId(id: String, settings: AppSettings): FeedSource? =
        builtInById[id] ?: Regions.byKey(settings.region).feeds.firstOrNull { it.id == id } ?: settings.customFeeds.firstOrNull { it.id == id }

    /** The id of a new custom feed. */
    fun newCustomId(): String = FeedSource.USER_ID_PREFIX + java.util.UUID.randomUUID().toString().take(8)
}

/** A regional preset behind the "Region" topic: one or two public newsrooms of a Bundesland. */
data class Region(val key: String, val name: String, val feeds: List<FeedSource>)

object Regions {
    const val DEFAULT = "berlin-brandenburg"

    private fun src(id: String, name: String, url: String) = FeedSource(id, name, url, Topics.REGION_ID)

    val ALL: List<Region> = listOf(
        Region("baden-wuerttemberg", "Baden-Württemberg", listOf(src("swr-bw", "SWR Aktuell", "https://www.swr.de/~rss/swraktuell/baden-wuerttemberg/index.xml"))),
        Region("bayern", "Bayern", listOf(src("br24", "BR24", "https://feeds.br24.de/rss/nachrichten"))),
        Region(
            "berlin-brandenburg", "Berlin & Brandenburg",
            listOf(
                src("rbb24", "rbb24", "https://www.rbb24.de/aktuell/index.xml/feed=rss.xml"),
                src("tagesspiegel-berlin", "Tagesspiegel", "https://www.tagesspiegel.de/contentexport/feed/berlin"),
            ),
        ),
        Region("bremen", "Bremen", listOf(src("butenunbinnen", "buten un binnen", "https://www.butenunbinnen.de/feed/rss/neuste-inhalte100.xml"))),
        Region("hamburg", "Hamburg", listOf(src("ndr-hamburg", "NDR Hamburg", "https://www.ndr.de/nachrichten/hamburg/index-rss.xml"))),
        Region("hessen", "Hessen", listOf(src("hessenschau", "hessenschau", "https://www.hessenschau.de/index.rss"))),
        Region("mecklenburg-vorpommern", "Mecklenburg-Vorpommern", listOf(src("ndr-mv", "NDR Mecklenburg-Vorpommern", "https://www.ndr.de/nachrichten/mecklenburg-vorpommern/index-rss.xml"))),
        Region("niedersachsen", "Niedersachsen", listOf(src("ndr-nds", "NDR Niedersachsen", "https://www.ndr.de/nachrichten/niedersachsen/index-rss.xml"))),
        Region("nordrhein-westfalen", "Nordrhein-Westfalen", listOf(src("wdr", "WDR", "https://www1.wdr.de/nachrichten/uebersicht-nachrichten-100.feed"))),
        Region("rheinland-pfalz", "Rheinland-Pfalz", listOf(src("swr-rlp", "SWR Aktuell", "https://www.swr.de/~rss/swraktuell/rheinland-pfalz/index.xml"))),
        // SR (Saarländischer Rundfunk) publishes no feed (checked 29.10.2026); the neighbouring SWR feed
        // stands in and is named for what it is.
        Region("saarland", "Saarland", listOf(src("swr-saar", "SWR Aktuell (Rheinland-Pfalz)", "https://www.swr.de/~rss/swraktuell/rheinland-pfalz/index.xml"))),
        Region(
            "sachsen", "Sachsen",
            listOf(
                src("mdr-sachsen", "MDR Sachsen", "https://www.mdr.de/nachrichten/sachsen/index-rss.xml"),
                src("lvz", "LVZ", "https://www.lvz.de/rss/feed/lvz_leipzig"),
                src("l-iz", "L-IZ", "https://www.l-iz.de/feed"),
            ),
        ),
        Region("sachsen-anhalt", "Sachsen-Anhalt", listOf(src("mdr-sachsen-anhalt", "MDR Sachsen-Anhalt", "https://www.mdr.de/nachrichten/sachsen-anhalt/index-rss.xml"))),
        Region("schleswig-holstein", "Schleswig-Holstein", listOf(src("ndr-sh", "NDR Schleswig-Holstein", "https://www.ndr.de/nachrichten/schleswig-holstein/index-rss.xml"))),
        Region("thueringen", "Thüringen", listOf(src("mdr-thueringen", "MDR Thüringen", "https://www.mdr.de/nachrichten/thueringen/index-rss.xml"))),
    )

    private val byKey = ALL.associateBy { it.key }

    /** The preset for [key]; unknown keys fall back to [DEFAULT]. */
    fun byKey(key: String?): Region = byKey[key] ?: byKey.getValue(DEFAULT)

    /** The presets are German public broadcasters: they serve the German edition only. */
    fun servesEdition(edition: NewsEdition): Boolean = edition.country == "DE"

    /**
     * The preset a place belongs to, judged by the Bundesland in its region label
     * ("Sachsen, Deutschland"); null when it names none (abroad, or unknown).
     */
    fun guess(place: Place): Region? {
        val label = place.region.lowercase()
        if (label.isEmpty()) return null
        // Longest alias first: "Sachsen-Anhalt" must not match "Sachsen".
        return ALL.flatMap { region -> region.aliases.map { alias -> alias to region } }
            .sortedByDescending { it.first.length }
            .firstOrNull { (alias, _) -> label.contains(alias) }
            ?.second
    }

    private val Region.aliases: List<String>
        get() = when (key) {
            "berlin-brandenburg" -> listOf("berlin", "brandenburg")
            "baden-wuerttemberg" -> listOf("baden-württemberg", "baden-wuerttemberg")
            "nordrhein-westfalen" -> listOf("nordrhein-westfalen", "north rhine")
            "rheinland-pfalz" -> listOf("rheinland-pfalz")
            "mecklenburg-vorpommern" -> listOf("mecklenburg")
            "sachsen-anhalt" -> listOf("sachsen-anhalt", "saxony-anhalt")
            "sachsen" -> listOf("sachsen", "saxony")
            "thueringen" -> listOf("thüringen", "thuringia")
            else -> listOf(name.lowercase())
        }
}

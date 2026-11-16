package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import com.cedrickgd.morgenbriefing.data.news.GoogleNews
import com.cedrickgd.morgenbriefing.data.news.RssParser
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import com.cedrickgd.morgenbriefing.ui.article.ArticleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * "Does the reader show anything twice?" – measured on real pages, OFF by default (the unit tests
 * stay offline). Three steps, all working on one folder (`MB_SURVEY_DIR`, outside the repository):
 *
 * 1. [snapshot] (`MB_LIVE=1`): the newest items of the app's feeds (German newsrooms, English
 *    ones, French/Spanish/Italian ones through Google News like the app reads them) and their
 *    pages are saved: `NNN.html` + `NNN.json` (the feed item).
 * 2. [survey]: offline. Every saved page goes through the reader's real pipeline (extractor →
 *    cleaner → arrangement under the head → hero choice) and what the screen would print is
 *    searched for repeats: hero vs. pictures in the text, any picture twice, headline/teaser vs.
 *    text, any passage twice, alt text vs. caption. `survey-<label>.txt` holds the counts,
 *    `rendered-<label>.json` what was shown (`MB_SURVEY_LABEL`, default "run").
 *    When the folder holds `hashes.tsv` (one line per picture: URL, a tab, `<w>x<h> <hex>,<hex>,…` –
 *    perceptual hashes of the URLs in `images-<label>.txt`, made by `scripts/HashImages.java`:
 *    java.awt is not on the classpath of Android unit tests), "the same picture" is also judged
 *    by its pixels, not only by the URL rules under test. `MB_SURVEY_REPLAY=<label>` judges what
 *    an earlier run rendered (the state before a change) with today's rules.
 * 3. [diff] (`MB_SURVEY_BASE`, `MB_SURVEY_LABEL`): what one run shows less than another – and
 *    whether each removed piece is still on screen once (justified) or gone for good (over-deleted).
 *
 * ```
 * MB_LIVE=1 MB_SURVEY_DIR=C:/tmp/survey ./gradlew.bat testDebugUnitTest --tests '*ArticleDuplicateSurvey.snapshot' --rerun
 * MB_SURVEY_DIR=C:/tmp/survey MB_SURVEY_LABEL=before ./gradlew.bat testDebugUnitTest --tests '*ArticleDuplicateSurvey.survey' --rerun
 * java scripts/HashImages.java C:/tmp/survey/images-before.txt C:/tmp/survey/hashes.tsv
 * MB_SURVEY_DIR=C:/tmp/survey MB_SURVEY_BASE=before MB_SURVEY_LABEL=after ./gradlew.bat testDebugUnitTest --tests '*ArticleDuplicateSurvey.diff' --rerun
 * ```
 *
 * 15.11.2026, 162 pages, 132 readable: 35 articles showed something twice (9 a picture, 32 a
 * text); after the fixes 11 are still flagged – quotes a caption or subheading takes from the
 * text, live blogs that retell an entry, one obituary page whose picture has two unrelated URLs.
 */
class ArticleDuplicateSurvey {

    private data class Feed(val name: String, val url: String, val take: Int = 2, val google: Boolean = false, val lang: String = "de")

    private val feeds = listOf(
        // German newsrooms of the app's catalogue
        Feed("tagesschau", "https://www.tagesschau.de/index~rss2.xml", take = 4),
        Feed("tagesschau Wirtschaft", "https://www.tagesschau.de/wirtschaft/index~rss2.xml"),
        Feed("SPIEGEL", "https://www.spiegel.de/schlagzeilen/index.rss", take = 5),
        Feed("n-tv", "https://www.n-tv.de/rss", take = 5),
        Feed("Deutschlandfunk", "https://www.deutschlandfunk.de/nachrichten-100.rss"),
        Feed("ZDFheute", "https://www.zdf.de/rss/zdf/nachrichten", take = 4),
        Feed("DW", "https://rss.dw.com/rdf/rss-de-all"),
        Feed("heise", "https://www.heise.de/rss/heise-top-atom.xml", take = 4),
        Feed("Golem", "https://rss.golem.de/rss.php?feed=RSS2.0"),
        Feed("t3n", "https://t3n.de/rss.xml"),
        Feed("ZEIT", "https://newsfeed.zeit.de/politik/index", take = 4),
        Feed("ZEIT Digital", "https://newsfeed.zeit.de/digital/index"),
        Feed("FAZ", "https://www.faz.net/rss/aktuell/politik/", take = 4),
        Feed("SZ", "https://rss.sueddeutsche.de/rss/Politik", take = 4),
        Feed("WELT", "https://www.welt.de/feeds/section/politik.rss", take = 4),
        Feed("taz", "https://taz.de/!p4615;rss/"),
        Feed("Handelsblatt", "https://feeds.cms.handelsblatt.com/finanzen"),
        Feed("WiWo", "https://feeds.cms.wiwo.de/rss/unternehmen"),
        Feed("The Decoder", "https://the-decoder.de/feed/"),
        Feed("ComputerBase", "https://www.computerbase.de/rss/news.xml"),
        Feed("netzpolitik", "https://netzpolitik.org/feed/"),
        Feed("BTC-ECHO", "https://www.btc-echo.de/feed/"),
        Feed("Sportschau", "https://www.sportschau.de/index~rss2.xml"),
        Feed("kicker", "https://newsfeed.kicker.de/news/aktuell"),
        Feed("Stern", "https://www.stern.de/feed/standard/alle-nachrichten/"),
        Feed("BR24", "https://feeds.br24.de/rss/nachrichten"),
        Feed("NDR", "https://www.ndr.de/nachrichten/hamburg/index-rss.xml"),
        Feed("WDR", "https://www1.wdr.de/nachrichten/uebersicht-nachrichten-100.feed"),
        Feed("MDR", "https://www.mdr.de/nachrichten/sachsen/index-rss.xml"),
        Feed("hessenschau", "https://www.hessenschau.de/index.rss"),
        Feed("rbb24", "https://www.rbb24.de/aktuell/index.xml/feed=rss.xml"),
        Feed("SWR", "https://www.swr.de/~rss/swraktuell/baden-wuerttemberg/index.xml"),
        Feed("Spektrum", "https://www.spektrum.de/alias/rss/spektrum-de-rss-feed/996406"),
        Feed("t-online via Google", gn("site:t-online.de when:1d", "de", "DE"), take = 4, google = true),
        Feed("FOCUS via Google", gn("site:focus.de when:1d", "de", "DE"), take = 2, google = true),
        Feed("RND via Google", gn("site:rnd.de when:1d", "de", "DE"), take = 2, google = true),
        Feed("Merkur via Google", gn("site:merkur.de when:1d", "de", "DE"), take = 2, google = true),
        Feed("Top DE via Google", "https://news.google.com/rss?hl=de&gl=DE&ceid=DE:de", take = 8, google = true),
        // English
        Feed("BBC", "https://feeds.bbci.co.uk/news/rss.xml", take = 4, lang = "en"),
        Feed("Guardian", "https://www.theguardian.com/world/rss", take = 4, lang = "en"),
        Feed("NPR", "https://feeds.npr.org/1001/rss.xml", take = 3, lang = "en"),
        Feed("CNBC", "https://www.cnbc.com/id/100003114/device/rss/rss.html", take = 3, lang = "en"),
        Feed("ABC News", "https://feeds.abcnews.com/abcnews/topstories", lang = "en"),
        Feed("CBS News", "https://www.cbsnews.com/latest/rss/main", lang = "en"),
        Feed("The Hill", "https://thehill.com/feed/", lang = "en"),
        Feed("POLITICO Europe", "https://www.politico.eu/feed/", lang = "en"),
        Feed("Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", lang = "en"),
        Feed("Euronews", "https://www.euronews.com/rss", lang = "en"),
        Feed("The Verge", "https://www.theverge.com/rss/index.xml", lang = "en"),
        Feed("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", lang = "en"),
        Feed("CoinDesk", "https://www.coindesk.com/arc/outboundfeeds/rss/", lang = "en"),
        Feed("AP via Google", gn("site:apnews.com when:1d", "en-US", "US"), take = 3, google = true, lang = "en"),
        Feed("Top US via Google", "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", take = 6, google = true, lang = "en"),
        // French, Spanish, Italian: the app reads these editions through Google News
        Feed("Top FR via Google", "https://news.google.com/rss?hl=fr&gl=FR&ceid=FR:fr", take = 8, google = true, lang = "fr"),
        Feed("Le Monde via Google", gn("site:lemonde.fr when:1d", "fr", "FR"), google = true, lang = "fr"),
        Feed("franceinfo via Google", gn("site:francetvinfo.fr when:1d", "fr", "FR"), google = true, lang = "fr"),
        Feed("Le Figaro via Google", gn("site:lefigaro.fr when:1d", "fr", "FR"), google = true, lang = "fr"),
        Feed("Top ES via Google", "https://news.google.com/rss?hl=es&gl=ES&ceid=ES:es", take = 8, google = true, lang = "es"),
        Feed("El País via Google", gn("site:elpais.com when:1d", "es", "ES"), google = true, lang = "es"),
        Feed("20minutos via Google", gn("site:20minutos.es when:1d", "es", "ES"), google = true, lang = "es"),
        Feed("El Mundo via Google", gn("site:elmundo.es when:1d", "es", "ES"), google = true, lang = "es"),
        Feed("Top IT via Google", "https://news.google.com/rss?hl=it&gl=IT&ceid=IT:it", take = 8, google = true, lang = "it"),
        Feed("Repubblica via Google", gn("site:repubblica.it when:1d", "it", "IT"), google = true, lang = "it"),
        Feed("ANSA via Google", gn("site:ansa.it when:1d", "it", "IT"), google = true, lang = "it"),
        Feed("Corriere via Google", gn("site:corriere.it when:1d", "it", "IT"), google = true, lang = "it"),
    )

    private fun gn(query: String, hl: String, gl: String): String {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        return "https://news.google.com/rss/search?q=$q&hl=$hl&gl=$gl&ceid=$gl:${hl.substringBefore('-')}"
    }

    private val live: Boolean get() = System.getProperty("live") == "true" || System.getenv("MB_LIVE") == "1"
    private val dir: File? get() = System.getenv("MB_SURVEY_DIR")?.takeIf { it.isNotBlank() }?.let(::File)
    private val label: String get() = System.getenv("MB_SURVEY_LABEL")?.takeIf { it.isNotBlank() } ?: "run"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    private data class Saved(
        val feed: String,
        val lang: String,
        val headline: String,
        val summary: String,
        val url: String,
        val pageUrl: String,
        val image: String? = null,
        val imageAlt: String = "",
        val google: Boolean = false,
    )

    @Serializable
    private data class Rendered(val name: String, val feed: String, val url: String, val units: List<SurveyUnit>, val og: String? = null)

    // ---------------------------------------------------------------------------- 1. snapshot

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val pages: OkHttpClient = http.newBuilder().followRedirects(false).followSslRedirects(false).build()

    /** GETs [url] like the reader does (redirects by hand, the article's own cookies): the landed URL and the page. */
    private fun fetch(url: String, lang: String): Pair<String, String> {
        var current = url.toHttpUrlOrNull() ?: error("no url")
        val cookies = SessionCookies()
        repeat(10) {
            val builder = Request.Builder().url(current)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", if (lang == "en") "en-US,en;q=0.9" else "$lang,en;q=0.7")
                .header("User-Agent", ArticleRepository.USER_AGENT)
            cookies.header(current)?.let { builder.header("Cookie", it) }
            pages.newCall(builder.build()).execute().use { response ->
                cookies.save(current, response.headers("Set-Cookie"))
                if (response.isRedirect) {
                    current = response.header("Location")?.let { current.resolve(it) } ?: error("redirect without location")
                    if (current.scheme == "http") current = current.newBuilder().scheme("https").build()
                    return@repeat
                }
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val landed = current.toString()
                return landed to Jsoup.parse(ByteArrayInputStream(response.body.bytes()), response.body.contentType()?.charset()?.name(), landed).outerHtml()
            }
        }
        error("too many redirects")
    }

    @Test
    fun snapshot() {
        val out = dir
        assumeTrue("set MB_LIVE=1 and MB_SURVEY_DIR", live && out != null)
        out!!.mkdirs()
        val google = GoogleNewsTestResolver(http)
        val parser = RssParser { KXmlParser() }
        val saved = runBlocking {
            val items = feeds.map { feed -> async(Dispatchers.IO) { feed to items(http, feed, parser) } }.awaitAll()
            val gate = Semaphore(6)
            items.flatMap { (feed, list) -> list.map { feed to it } }.mapIndexed { index, (feed, item) ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        try {
                            val target = if (feed.google) google.resolve(item.url) else item.url
                            if (target.toHttpUrlOrNull()?.host == "news.google.com") return@withPermit "UNRESOLVED ${feed.name}"
                            val (landed, html) = fetch(target, feed.lang)
                            val name = "%03d".format(index)
                            File(out, "$name.html").writeText(html)
                            val meta = Saved(feed.name, feed.lang, item.headline, item.summary, item.url, landed, item.image?.url, item.image?.alt.orEmpty(), feed.google)
                            File(out, "$name.json").writeText(json.encodeToString(Saved.serializer(), meta))
                            "OK $name ${feed.name} $landed"
                        } catch (e: Exception) {
                            "FAIL ${feed.name} ${item.url.take(80)} $e"
                        }
                    }
                }
            }.awaitAll()
        }
        File(out, "snapshot.txt").writeText(saved.joinToString("\n"))
        println("snapshot: ${saved.count { it.startsWith("OK") }} pages of ${saved.size} items in $out")
    }

    private fun items(http: OkHttpClient, feed: Feed, parser: RssParser): List<NewsItem> {
        val request = Request.Builder().url(feed.url)
            .header("User-Agent", RssClient.FEED_USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/rdf+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5")
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList<NewsItem>().also { println("feed ${feed.name}: HTTP ${response.code}") }
                parser.parse(response.body.byteStream()).entries.filter { it.link.startsWith("https://") && it.title.isNotBlank() }.take(feed.take).map { entry ->
                    // As RssClient builds its items (publisherItem / googleNewsItem).
                    val publisher = entry.sourceName.trim()
                    val headline = if (feed.google) GoogleNews.stripPublisher(entry.title, publisher) else entry.title.trim()
                    NewsItem(
                        id = RssClient.stableId(entry.link),
                        headline = headline,
                        summary = (if (feed.google) GoogleNews.teaser(entry.summary, headline, publisher) else entry.summary).take(600),
                        url = entry.link,
                        source = feed.name,
                        sourceId = feed.name,
                        topicId = "welt",
                        image = if (feed.google) null else entry.imageUrl?.takeIf { it.startsWith("https://") }?.let { ArticleImage(it, entry.imageAlt) },
                        aggregator = if (feed.google) "google" else null,
                    )
                }
            }
        } catch (e: Exception) {
            println("feed ${feed.name} failed: $e")
            emptyList()
        }
    }

    // ------------------------------------------------------------------------- render + survey

    /** What the reader's screen prints for one saved page, in order; null when the page is not readable. */
    private fun render(name: String, meta: Saved, html: String): Rendered? {
        val page = ArticleExtractor.read(html, meta.pageUrl)
        val content = page.content?.takeIf { page.outcome == ReadOutcome.OK } ?: return null
        val item = NewsItem(
            id = name, headline = meta.headline, summary = meta.summary, url = meta.pageUrl, source = meta.feed, sourceId = meta.feed, topicId = "welt",
            image = meta.image?.let { ArticleImage(it, meta.imageAlt) }, aggregator = if (meta.google) "google" else null,
        )
        val arranged = ArticleViewModel.arrange(content, item)
        val shown = arranged.content
        val units = ArrayList<SurveyUnit>()
        arranged.hero?.let { units += SurveyUnit("hero", url = it) }
        units += SurveyUnit("headline", item.headline.ifBlank { shown.title })
        if (arranged.teaser.isNotBlank()) units += SurveyUnit("teaser", arranged.teaser)
        shown.blocks.forEachIndexed { i, block ->
            when (block.type) {
                // The reader keeps no picture in the text (one picture, on top); should one slip through, it counts.
                ArticleBlock.IMAGE -> units += SurveyUnit("img", url = block.url, block = i)
                ArticleBlock.LIST -> block.items.forEach { units += SurveyUnit("li", it, block = i) }
                else -> units += SurveyUnit(block.type, block.text, block = i)
            }
        }
        return Rendered(name, meta.feed, meta.pageUrl, units, og = content.image)
    }

    private fun renderAll(out: File): Pair<List<Rendered>, List<String>> {
        val unreadable = ArrayList<String>()
        val rendered = out.listFiles { f -> f.name.matches(Regex("\\d{3}\\.html")) }.orEmpty().sortedBy { it.name }.mapNotNull { file ->
            val name = file.name.removeSuffix(".html")
            val meta = json.decodeFromString(Saved.serializer(), File(out, "$name.json").readText())
            render(name, meta, file.readText()) ?: null.also { unreadable += "$name ${meta.feed} ${meta.pageUrl}" }
        }
        return rendered to unreadable
    }

    @Test
    fun survey() {
        val out = dir
        assumeTrue("set MB_SURVEY_DIR to a folder written by snapshot()", out != null && out.isDirectory)
        // MB_SURVEY_REPLAY=<label>: judge what an earlier run rendered (rendered-<label>.json) with today's rules.
        val replay = System.getenv("MB_SURVEY_REPLAY")?.takeIf { it.isNotBlank() }
        val (rendered, unreadable) = if (replay != null) {
            json.decodeFromString(ListSerializer(Rendered.serializer()), File(out, "rendered-$replay.json").readText()) to emptyList()
        } else {
            renderAll(out!!)
        }
        val hashes = Hashes.load(File(out, "hashes.tsv"))
        val lines = StringBuilder()
        val perKind = LinkedHashMap<String, Int>()
        var withAny = 0
        var withImage = 0
        var withText = 0
        File(out, "dump-$label").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        for (r in rendered) {
            val findings = Findings.of(r.units, hashes) + ogAlias(r, hashes)
            val dump = buildString {
                append(r.url).append('\n')
                findings.forEach { append("FINDING ").append(it).append('\n') }
                append('\n')
                r.units.forEachIndexed { i, u -> append("[%03d] %-8s %s\n".format(i, u.kind, (u.url.ifEmpty { u.text }).take(400))) }
            }
            File(out, "dump-$label/${r.name}-${r.feed.replace(Regex("[^A-Za-z0-9]+"), "_")}.txt").writeText(dump)
            val kinds = findings.map { it.substringBefore(':') }.distinct()
            kinds.forEach { perKind[it] = (perKind[it] ?: 0) + 1 }
            if (kinds.any { !it.startsWith("NOTE") }) withAny++
            if (kinds.any { it.startsWith("IMG") }) withImage++
            if (kinds.any { it.startsWith("TXT") }) withText++
            lines.append("${r.name} ${r.feed.padEnd(24)} units=%3d imgs=%2d ".format(r.units.size, r.units.count { it.kind == "img" }))
            if (kinds.isNotEmpty()) lines.append(kinds.joinToString(" ", prefix = "DUP[", postfix = "] "))
            lines.append(r.url).append('\n')
            findings.forEach { lines.append("      ").append(it.take(260)).append('\n') }
        }
        lines.append("\nreadable: ${rendered.size}, not readable: ${unreadable.size}\n")
        lines.append("articles with any duplicate: $withAny (pictures: $withImage, text: $withText)\n")
        lines.append("by kind (articles): ").append(perKind.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" }).append('\n')
        lines.append("pictures shown: ${rendered.sumOf { r -> r.units.count { it.kind == "img" || it.kind == "hero" } }}, text units shown: ${rendered.sumOf { r -> r.units.count { it.text.isNotBlank() } }}\n")
        lines.append("\nnot readable:\n").append(unreadable.joinToString("\n")).append('\n')
        File(out, "survey-$label.txt").writeText(lines.toString())
        File(out, "rendered-$label.json").writeText(json.encodeToString(ListSerializer(Rendered.serializer()), rendered))
        // For the picture hashes (scripts outside the unit tests: java.awt is not on Android's test classpath).
        File(out, "images-$label.txt").writeText(rendered.flatMap { r -> r.units.filter { it.url.isNotEmpty() }.take(MAX_IMAGES_PER_ARTICLE).map { it.url } + listOfNotNull(r.og) }.distinct().joinToString("\n"))
        println(lines.lines().takeLast(unreadable.size + 8).take(6).joinToString("\n"))
    }

    /**
     * The feed's picture is on top and the page announces another URL as its lead picture: is a
     * picture in the text that one (by the URL rules) – and is it the hero in other clothes?
     */
    private fun ogAlias(r: Rendered, hashes: Hashes): List<String> {
        val hero = r.units.firstOrNull { it.kind == "hero" }?.url ?: return emptyList()
        val og = r.og?.takeIf { !ImageMatch.same(it, hero) } ?: return emptyList()
        val pixels = if (hashes.known(hero) && hashes.known(og)) (if (hashes.same(hero, og)) "same pixels" else "other picture") else "pixels unknown"
        val inText = r.units.withIndex().filter { it.value.kind == "img" && ImageMatch.same(it.value.url, og) }.map { it.index }
        return listOf("NOTE_OG_OTHER_URL: og:image vs hero: $pixels; og:image in the text at $inText: ${og.takeLast(80)}")
    }

    // ------------------------------------------------------------------------------- 4. diff

    @Test
    fun diff() {
        val out = dir
        val baseLabel = System.getenv("MB_SURVEY_BASE")
        assumeTrue("set MB_SURVEY_DIR, MB_SURVEY_BASE and MB_SURVEY_LABEL", out != null && baseLabel != null)
        val hashes = Hashes.load(File(out, "hashes.tsv"))
        fun load(l: String) = json.decodeFromString(ListSerializer(Rendered.serializer()), File(out, "rendered-$l.json").readText()).associateBy { it.name }
        val before = load(baseLabel!!)
        val after = load(label)
        val lines = StringBuilder()
        var removedText = 0
        var removedImages = 0
        var unjustified = 0
        for ((name, b) in before) {
            val a = after[name]
            if (a == null) {
                lines.append("$name ${b.feed}: no longer readable\n")
                unjustified++
                continue
            }
            fun key(u: SurveyUnit) = u.kind.let { if (it == "alt" || it == "caption") "cap" else if (it == "hero") "img" else it } + "|" + (u.url.ifEmpty { Findings.norm(u.text) })
            val left = a.units.groupingBy(::key).eachCount().toMutableMap()
            val removed = b.units.filter { u -> val k = key(u); val n = left[k] ?: 0; if (n > 0) { left[k] = n - 1; false } else true }
            val leftB = b.units.groupingBy(::key).eachCount().toMutableMap()
            val added = a.units.filter { u -> val k = key(u); val n = leftB[k] ?: 0; if (n > 0) { leftB[k] = n - 1; false } else true }
            if (removed.isEmpty() && added.isEmpty()) continue
            lines.append("$name ${b.feed} ${b.url}\n")
            for (u in removed) {
                var how = ""
                val still = if (u.url.isNotEmpty()) {
                    val others = a.units.filter { it.url.isNotEmpty() }
                    how = when {
                        others.any { it.url == u.url } -> "same url"
                        others.any { hashes.same(it.url, u.url) } -> "same pixels"
                        others.any { ImageMatch.same(it.url, u.url) } ->
                            if (hashes.known(u.url) && others.filter { ImageMatch.same(it.url, u.url) }.all { hashes.known(it.url) }) "URL RULE ONLY, PIXELS DIFFER" else "url rule, pixels unknown"
                        else -> ""
                    }
                    how.isNotEmpty()
                } else {
                    val n = Findings.norm(u.text)
                    a.units.any { o -> o.text.isNotBlank() && (Findings.norm(o.text).let { it == n || " $it ".contains(" $n ") } || Findings.near(o.text, u.text)) } ||
                        Findings.sentences(u.text).all { s -> a.units.any { o -> " ${Findings.norm(o.text)} ".contains(" $s ") } }
                }
                if (u.url.isNotEmpty()) removedImages++ else removedText++
                if (!still) unjustified++
                lines.append("   - ${if (still) "ok  " else "GONE"} ${u.kind.padEnd(8)} ${if (how.isEmpty()) "" else "($how) "}${(u.url.ifEmpty { u.text }).take(300)}\n")
            }
            for (u in added) lines.append("   + ${u.kind.padEnd(8)} ${(u.url.ifEmpty { u.text }).take(300)}\n")
        }
        lines.append("\nremoved: $removedText text units, $removedImages pictures; not shown anywhere any more (check by hand): $unjustified\n")
        File(out, "diff-$baseLabel-$label.txt").writeText(lines.toString())
        println(lines.lines().takeLast(3).joinToString("\n"))
    }

    private companion object {
        const val MAX_IMAGES_PER_ARTICLE = 40
    }
}

/** Perceptual hashes (difference hash, whole picture and centre crops) of the pictures of a survey. */
internal class Hashes(val byUrl: Map<String, List<Long>>) {

    /** True when both pictures were decoded and one of their hashes (nearly) agrees. */
    fun same(a: String, b: String): Boolean {
        if (a == b) return true
        val ha = byUrl[a].orEmpty()
        val hb = byUrl[b].orEmpty()
        if (ha.isEmpty() || hb.isEmpty()) return false
        // The whole pictures (first hash) may differ a little (another size, another compression);
        // a centre crop must agree almost exactly – flat charts look alike in their crops.
        if (java.lang.Long.bitCount(ha[0] xor hb[0]) <= MAX_DISTANCE) return true
        return ha.withIndex().any { (i, x) -> hb.withIndex().any { (j, y) -> (i > 0 || j > 0) && java.lang.Long.bitCount(x xor y) <= CROP_DISTANCE } }
    }

    fun known(url: String): Boolean = byUrl[url]?.isNotEmpty() == true

    companion object {
        private const val MAX_DISTANCE = 6
        private const val CROP_DISTANCE = 1

        fun load(file: File): Hashes {
            if (!file.isFile) return Hashes(emptyMap())
            return Hashes(
                file.readLines().filter { it.contains('\t') }.associate { line ->
                    val (url, value) = line.split('\t', limit = 2)
                    url to if (value.startsWith("-")) emptyList() else value.substringAfter(' ').split(',').map { java.lang.Long.parseUnsignedLong(it, 16) }
                },
            )
        }
    }
}

/**
 * What counts as "shown twice" on the reader's screen. Deliberately not built on the cleaner's
 * own matching (`TextMatch`): plain word sequences, so the survey also sees what the cleaner's
 * rules miss.
 */
internal object Findings {
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val SENTENCE = Regex("(?<=[.!?…:»«\"“”])\\s+")
    private const val MIN_WORDS = 8
    private const val MIN_SENTENCE_WORDS = 9
    private val TEXT_KINDS = setOf("headline", "teaser", "p", "h", "quote", "li", "alt", "caption")

    fun norm(text: String): String = NON_WORD.replace(text.lowercase(Locale.ROOT), " ").trim()

    private fun wordsOf(text: String): List<String> = norm(text).split(' ').filter { it.isNotEmpty() }

    /** Long sentences of [text], normalised. */
    fun sentences(text: String): List<String> = SENTENCE.split(text).map(::norm).filter { it.split(' ').size >= MIN_SENTENCE_WORDS }

    /** Nearly the same words in the same order: at least 80 % of both texts' word triples shared. */
    fun near(a: String, b: String): Boolean {
        val wa = wordsOf(a)
        val wb = wordsOf(b)
        if (wa.size < MIN_WORDS || wb.size < MIN_WORDS) return false
        val sa = wa.windowed(3).map { it.joinToString(" ") }.toSet()
        val sb = wb.windowed(3).map { it.joinToString(" ") }.toSet()
        val common = sa.count { it in sb }
        return common >= 0.8 * sa.size && common >= 0.8 * sb.size
    }

    fun of(units: List<SurveyUnit>, hashes: Hashes): List<String> {
        val found = ArrayList<String>()
        // Pictures: the hero against the text's pictures, and those against each other.
        val pictures = units.withIndex().filter { it.value.url.isNotEmpty() }
        for (x in pictures.indices) for (y in x + 1 until pictures.size) {
            val (i, a) = pictures[x]
            val (j, b) = pictures[y]
            val byUrl = a.url == b.url || ImageMatch.same(a.url, b.url)
            val byPixels = hashes.same(a.url, b.url)
            if (!byUrl && !byPixels) continue
            val kind = if (a.kind == "hero") "IMG_HERO_BODY" else "IMG_BODY_TWICE"
            val how = when { a.url == b.url -> "same url"; byUrl -> "same by url rules"; else -> "same pixels, urls differ" }
            found += "$kind: [$i]/[$j] $how: ${a.url.takeLast(90)} | ${b.url.takeLast(90)}"
        }
        // Text: every printed text against every other.
        val texts = units.withIndex().filter { it.value.kind in TEXT_KINDS && it.value.text.isNotBlank() }
        val norms = texts.map { norm(it.value.text) }
        val reported = HashSet<Pair<Int, Int>>()
        for (x in texts.indices) for (y in x + 1 until texts.size) {
            val (i, a) = texts[x]
            val (j, b) = texts[y]
            val na = norms[x]
            val nb = norms[y]
            val wa = na.split(' ').size
            val wb = nb.split(' ').size
            val captions = a.kind in setOf("alt", "caption") && b.kind in setOf("alt", "caption")
            val headings = a.kind == "h" && b.kind == "h"
            val minWords = if (captions || headings) 3 else MIN_WORDS
            val whole = when {
                na == nb -> minOf(wa, wb) >= minOf(minWords, 4) || captions || headings
                minOf(wa, wb) < minWords -> false
                wa <= wb -> " $nb ".contains(" $na ")
                else -> " $na ".contains(" $nb ")
            } || near(a.text, b.text)
            if (whole) {
                reported += i to j
                found += "${kindOf(a, b)}: [$i ${a.kind}]/[$j ${b.kind}] ${a.text.take(70)} | ${b.text.take(70)}"
            }
        }
        // A long sentence printed in two places (a teaser sentence inside a paragraph, a re-chunked body).
        for (x in texts.indices) {
            val (i, a) = texts[x]
            for (s in sentences(a.text)) {
                for (y in texts.indices) {
                    if (y == x) continue
                    val (j, b) = texts[y]
                    if ((minOf(i, j) to maxOf(i, j)) in reported) continue
                    if (" ${norms[y]} ".contains(" $s ")) {
                        reported += minOf(i, j) to maxOf(i, j)
                        found += "TXT_SENTENCE: [$i ${a.kind}]/[$j ${b.kind}] ${s.take(110)}"
                    }
                }
            }
        }
        return found
    }

    private fun kindOf(a: SurveyUnit, b: SurveyUnit): String {
        val kinds = setOf(a.kind, b.kind)
        val captions = setOf("alt", "caption")
        return when {
            "headline" in kinds -> "TXT_HEADLINE"
            "teaser" in kinds -> "TXT_TEASER"
            kinds.all { it in captions } -> if (a.block == b.block || b.block == a.block + 1) "TXT_ALT_CAPTION" else "TXT_CAPTION_TWICE"
            kinds.any { it in captions } -> "TXT_CAPTION_BODY"
            else -> "TXT_BODY_REPEAT"
        }
    }
}

/** One thing the screen prints: `hero`, `headline`, `teaser`, `p`, `h`, `quote`, `li`, `img`, `alt`, `caption`. */
@Serializable
internal data class SurveyUnit(val kind: String, val text: String = "", val url: String = "", val block: Int = -1)

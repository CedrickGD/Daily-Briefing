package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.AppJson
import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.news.RssParser
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import com.cedrickgd.morgenbriefing.ui.article.ArticleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Live evaluation of the reader against real, current article pages – OFF by default, so the
 * unit tests stay offline. Run it with
 *
 * ```
 * MB_LIVE=1 ./gradlew.bat testDebugUnitTest --tests '*ArticleLiveEvaluation*' --rerun
 * ```
 *
 * (or `-Dlive=true` on the test JVM). It takes the newest articles of the app's own curated feeds
 * plus English publishers (direct RSS and Google News search), reads each one through the
 * reader's pipeline (ArticleRepository → ArticleExtractor → ArticleViewModel's arrangement with the
 * feed's teaser) and reports per article: outcome, blocks, words and every kind of duplicate the
 * reader would show. Report: `app/build/live/article-eval.txt`, one dump per article next to it.
 */
class ArticleLiveEvaluation {

    private data class Feed(val name: String, val url: String, val take: Int = 2, val google: Boolean = false)

    private val feeds = listOf(
        // German curated feeds of the app
        Feed("tagesschau", "https://www.tagesschau.de/inland/index~rss2.xml"),
        Feed("tagesschau Ausland", "https://www.tagesschau.de/ausland/index~rss2.xml"),
        Feed("tagesschau Wirtschaft", "https://www.tagesschau.de/wirtschaft/index~rss2.xml"),
        Feed("SPIEGEL", "https://www.spiegel.de/politik/index.rss", take = 3),
        Feed("SPIEGEL Wirtschaft", "https://www.spiegel.de/wirtschaft/index.rss"),
        Feed("Deutschlandfunk", "https://www.deutschlandfunk.de/nachrichten-100.rss"),
        Feed("Deutschlandfunk Politik", "https://www.deutschlandfunk.de/politikportal-100.rss"),
        Feed("DW", "https://rss.dw.com/rdf/rss-de-all"),
        Feed("heise", "https://www.heise.de/rss/heise-top-atom.xml"),
        Feed("taz", "https://taz.de/!p4615;rss/"),
        Feed("Handelsblatt", "https://www.handelsblatt.com/contentexport/feed/schlagzeilen", take = 3),
        Feed("Tagesspiegel", "https://www.tagesspiegel.de/contentexport/feed/berlin", take = 3),
        Feed("rbb24", "https://www.rbb24.de/aktuell/index.xml/feed=rss.xml"),
        Feed("MDR Sachsen", "https://www.mdr.de/nachrichten/sachsen/index-rss.xml"),
        Feed("NDR Hamburg", "https://www.ndr.de/nachrichten/hamburg/index-rss.xml"),
        Feed("BR24", "https://feeds.br24.de/rss/nachrichten"),
        Feed("WDR", "https://www1.wdr.de/nachrichten/uebersicht-nachrichten-100.feed"),
        Feed("hessenschau", "https://www.hessenschau.de/index.rss"),
        Feed("SWR", "https://www.swr.de/~rss/swraktuell/baden-wuerttemberg/index.xml"),
        Feed("Spektrum", "https://www.spektrum.de/alias/rss/spektrum-de-rss-feed/996406"),
        Feed("Sportschau", "https://www.sportschau.de/index~rss2.xml"),
        Feed("The Decoder", "https://the-decoder.de/feed/"),
        Feed("ComputerBase", "https://www.computerbase.de/rss/news.xml"),
        Feed("t3n", "https://t3n.de/rss.xml"),
        Feed("netzpolitik", "https://netzpolitik.org/feed/"),
        Feed("SZ", "https://rss.sueddeutsche.de/rss/Politik"),
        Feed("FAZ", "https://www.faz.net/rss/aktuell/politik/"),
        Feed("WELT", "https://www.welt.de/feeds/section/politik.rss"),
        Feed("WiWo", "https://www.wiwo.de/contentexport/feed/rss/schlagzeilen"),
        Feed("ZEIT", "https://newsfeed.zeit.de/politik/index"),
        Feed("Golem", "https://rss.golem.de/rss.php?feed=RSS2.0"),
        Feed("kicker", "https://newsfeed.kicker.de/news/aktuell"),
        Feed("scinexx", "https://www.scinexx.de/feed/"),
        Feed("Quarks", "https://www.quarks.de/feed/"),
        Feed("Utopia", "https://www.utopia.de/feed/"),
        Feed("GameStar", "https://www.gamestar.de/rss/gamestar.rss"),
        Feed("Moviepilot", "https://www.moviepilot.de/rss/moviepilot-standard"),
        Feed("Ärzteblatt", "https://www.aerzteblatt.de/rss/news.asp"),
        Feed("LVZ", "https://www.lvz.de/rss/feed/lvz_leipzig"),
        // English publishers
        Feed("BBC", "https://feeds.bbci.co.uk/news/rss.xml", take = 3),
        Feed("Guardian", "https://www.theguardian.com/world/rss", take = 3),
        Feed("NPR", "https://feeds.npr.org/1001/rss.xml", take = 3),
        Feed("CNBC", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=100003114", take = 3),
        Feed("PBS", "https://www.pbs.org/newshour/feeds/rss/headlines"),
        Feed("The Verge", "https://www.theverge.com/rss/index.xml"),
        Feed("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index"),
        Feed("ESPN", "https://www.espn.com/espn/rss/news"),
        // Google News search (publisher links decoded like the app does)
        Feed("AP via Google", gn("site:apnews.com when:1d"), take = 3, google = true),
        Feed("CBS via Google", gn("site:cbsnews.com when:1d"), take = 2, google = true),
        Feed("Midterms via Google", gn("midterms when:1d"), take = 8, google = true),
        Feed("Börse via Google", gn("DAX Börse when:1d", de = true), take = 6, google = true),
    )

    private fun gn(query: String, de: Boolean = false): String {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        return if (de) "https://news.google.com/rss/search?q=$q&hl=de&gl=DE&ceid=DE:de"
        else "https://news.google.com/rss/search?q=$q&hl=en-US&gl=US&ceid=US:en"
    }

    private val live: Boolean
        get() = System.getProperty("live") == "true" || System.getenv("MB_LIVE") == "1"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    @Test
    fun evaluate() {
        assumeTrue("live evaluation: set MB_LIVE=1 or -Dlive=true", live)
        val out = File(System.getenv("MB_LIVE_OUT") ?: "build/live").apply { mkdirs() }
        out.listFiles()?.forEach { it.delete() }
        val store = Files.createTempDirectory("articles").toFile()
        val google = GoogleNewsTestResolver(http)
        val readability = ReadabilityStore(File(store, "readability.json"), AppJson)
        val repository = ArticleRepository(http, store, AppJson, linkResolver = google, readability = readability)
        val parser = RssParser { KXmlParser() }

        val report = runBlocking {
            val items = feeds.map { feed -> async(Dispatchers.IO) { feed to items(feed, parser) } }.awaitAll()
            val gate = Semaphore(6)
            items.flatMap { (feed, list) -> list.map { feed to it } }.map { (feed, item) ->
                async(Dispatchers.IO) {
                    gate.withPermit { evaluate(feed, item, repository) }
                }
            }.awaitAll()
        }

        val lines = StringBuilder()
        report.forEachIndexed { index, r ->
            File(out, "%03d-%s.txt".format(index, r.feed.replace(Regex("[^A-Za-z0-9]+"), "_"))).writeText(r.dump)
            lines.append(r.line).append('\n')
        }
        lines.append('\n').append(summary(report))
        lines.append("learned blocked domains: ").append(readability.blockedDomains().filter { it !in KnownWalls.DOMAINS }.joinToString()).append('\n')
        lines.append("readability notes: ").append(readability.snapshot().entries.sortedBy { it.key }.joinToString { (d, s) -> "$d ${s.ok}/${s.ok + s.failed}${s.lastFailure?.let { f -> " " + f.name }.orEmpty()}" }).append('\n')
        File(out, "article-eval.txt").writeText(lines.toString())
        println(lines)
        store.deleteRecursively()
    }

    /**
     * Extracts one saved page and prints its blocks – for looking into a single site:
     * `MB_PAGE=<file.html> MB_PAGE_URL=<url> ./gradlew.bat testDebugUnitTest --tests '*ArticleLiveEvaluation.page*' --rerun`.
     */
    @Test
    fun page() {
        val path = System.getenv("MB_PAGE")
        assumeTrue("set MB_PAGE to a saved html file (or a folder of them, each with a <name>.url next to it)", path != null)
        val source = File(path!!)
        val files = if (source.isDirectory) source.listFiles { f -> f.name.endsWith(".html") }.orEmpty().sortedBy { it.name } else listOf(source)
        File("build/live").mkdirs()
        val summary = StringBuilder()
        for (file in files) {
            val url = File(file.path.removeSuffix(".html") + ".url").takeIf { it.isFile }?.readText()?.trim()
                ?: System.getenv("MB_PAGE_URL") ?: "https://example.org/"
            val page = ArticleExtractor.read(file.readText(), url)
            val out = StringBuilder("$url\noutcome=${page.outcome} amp=${page.ampUrl}\n")
            page.content?.let { c ->
                out.append("title=${c.title}\ndesc=${c.description}\nimage=${c.image}\nwords=${c.wordCount}\n")
                c.blocks.forEachIndexed { i, b -> out.append("[%02d] %-7s %s\n".format(i, b.type, (if (b.type == ArticleBlock.IMAGE) b.url + "  alt=" + b.alt else b.text + b.items.joinToString(" | ")).take(240))) }
            }
            File("build/live/page-${file.name.removeSuffix(".html")}.txt").writeText(out.toString())
            summary.append("${file.name}: ${page.outcome} words=${page.content?.wordCount} blocks=${page.content?.blocks?.size}\n")
        }
        File("build/live/pages.txt").writeText(summary.toString())
    }

    private suspend fun items(feed: Feed, parser: RssParser): List<NewsItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(feed.url)
            .header("User-Agent", RssClient.FEED_USER_AGENT)
            .header("Accept", "application/rss+xml, application/atom+xml, application/rdf+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5")
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val parsed = parser.parse(response.body.byteStream())
                parsed.entries.filter { it.link.startsWith("https://") && it.title.isNotBlank() }.take(feed.take).map { entry ->
                    NewsItem(
                        id = RssClient.stableId(entry.link),
                        headline = if (feed.google) entry.title.substringBeforeLast(" - ") else entry.title,
                        summary = if (feed.google) "" else entry.summary,
                        url = entry.link,
                        source = feed.name,
                        sourceId = feed.name,
                        topicId = "welt",
                        image = entry.imageUrl?.takeIf { it.startsWith("https://") }?.let { ArticleImage(it, entry.imageAlt) },
                        aggregator = if (feed.google) "google" else null,
                    )
                }
            }
        } catch (e: Exception) {
            println("feed ${feed.name} failed: $e")
            emptyList()
        }
    }

    private class Result(val feed: String, val line: String, val dump: String, val ok: Boolean, val truncated: Boolean, val findings: List<String>, val failure: String?, val host: String)

    private suspend fun evaluate(feed: Feed, item: NewsItem, repository: ArticleRepository): Result {
        val content = try {
            repository.load(item, language = if (feed.url.contains("hl=de") || !feed.google && !ENGLISH.containsMatchIn(feed.url)) "de" else "en")
        } catch (e: ArticleException) {
            val host = item.publisherDomain.ifBlank { item.url.toHttpUrlOrNull()?.host?.removePrefix("www.").orEmpty() }
            val reason = e.outcome.name + (e.httpCode?.let { " $it" }.orEmpty()) + (e.cause?.let { " ${it.javaClass.simpleName}: ${it.message?.take(80)}" }.orEmpty())
            return Result(feed.name, "${feed.name.padEnd(22)} ${reason.padEnd(16)} ${item.url}", "FAIL $reason\n${item.url}\n", false, false, emptyList(), reason, host)
        }
        val readable = item.copy(url = content.url)
        val host = content.url.toHttpUrlOrNull()?.host?.removePrefix("www.").orEmpty()
        val arranged = ArticleViewModel.arrange(content, item)
        val shown = arranged.content
        val teaser = arranged.teaser
        val hero = item.image?.url ?: content.image
        val findings = LiveDuplicates.find(item.headline, content.title, teaser, hero, shown)
        val words = shown.wordCount
        val truncated = shown.looksTruncated
        val line = buildString {
            append(feed.name.padEnd(22)).append(" OK    ")
            append("blocks=%3d words=%5d ".format(shown.blocks.size, words))
            if (truncated) append("TRUNC ")
            if (findings.isNotEmpty()) append(findings.joinToString(" ", prefix = "DUP[", postfix = "] ") { it.substringBefore(':') })
            append(readable.url)
        }
        val dump = buildString {
            append(readable.url).append('\n')
            append("headline: ").append(item.headline).append('\n')
            append("og:title: ").append(content.title).append('\n')
            append("feed:     ").append(item.summary).append('\n')
            append("og:desc:  ").append(content.description).append('\n')
            append("teaser:   ").append(teaser).append('\n')
            append("hero:     ").append(hero).append('\n')
            findings.forEach { append("FINDING ").append(it).append('\n') }
            append('\n')
            shown.blocks.forEachIndexed { i, b ->
                append("[%02d] %-7s ".format(i, b.type))
                append(
                    when (b.type) {
                        ArticleBlock.IMAGE -> "${b.url}  alt=${b.alt}"
                        ArticleBlock.LIST -> b.items.joinToString(" | ")
                        else -> b.text
                    }.take(300),
                )
                append('\n')
            }
        }
        return Result(feed.name, line, dump, true, truncated, findings, null, host)
    }

    private val ENGLISH = Regex("bbc|guardian|npr|cnbc|pbs|theverge|arstechnica|espn")

    private fun summary(results: List<Result>): String = buildString {
        val ok = results.filter { it.ok }
        append("articles: ${results.size}, readable: ${ok.size}, truncated: ${ok.count { it.truncated }}, ")
        append("with duplicates: ${ok.count { it.findings.isNotEmpty() }}\n")
        val kinds = ok.flatMap { r -> r.findings.map { it.substringBefore(':') }.distinct() }.groupingBy { it }.eachCount()
        append("duplicate kinds (articles): ").append(kinds.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" }).append('\n')
        val failures = results.filter { !it.ok }.groupingBy { it.failure.orEmpty().take(60) }.eachCount()
        append("failures by host: ").append(results.filter { !it.ok }.groupBy { it.host }.entries.sortedBy { it.key }.joinToString { (h, rs) -> "$h " + rs.joinToString("/") { it.failure.orEmpty() } }).append('\n')
        append("failures: ").append(failures.entries.sortedByDescending { it.value }.joinToString { "${it.key} ×${it.value}" }).append('\n')
        val hosts = results.groupBy { it.host }.mapValues { (_, rs) -> "${rs.count { it.ok && !it.truncated }}/${rs.size}" }
        append("full text per host: ").append(hosts.entries.sortedBy { it.key }.joinToString { "${it.key} ${it.value}" }).append('\n')
    }
}

/** What the live evaluation counts as a duplicate the reader shows. */
internal object LiveDuplicates {
    private val NOISE = Regex(
        "(?i)^(stand:|aktualisiert|veröffentlicht|zuletzt aktualisiert|updated|published|last updated|lesezeit|" +
            "\\d+\\s*min(\\.|uten)? (lesezeit|read)|von [\\p{Lu}][\\p{L}.-]+( [\\p{Lu}][\\p{L}.-]+){0,3}(,|$| und)|by [\\p{Lu}][\\p{L}.-]+ [\\p{Lu}])",
    )

    fun find(headline: String, pageTitle: String, teaser: String, hero: String?, content: ArticleContent): List<String> {
        val found = ArrayList<String>()
        val blocks = content.blocks
        val texts = blocks.withIndex().filter { it.value.type in TEXT && it.value.text.isNotBlank() }
        texts.take(3).forEach { (i, b) ->
            if (TextMatch.same(b.text, headline) || (pageTitle.isNotBlank() && TextMatch.same(b.text, pageTitle))) found += "TITLE: [$i] ${b.text.take(80)}"
        }
        if (teaser.isNotBlank()) {
            texts.take(4).forEach { (i, b) ->
                val t = TextMatch.withoutEllipsis(teaser)
                if (TextMatch.same(b.text, teaser) || TextMatch.covers(b.text, t) || TextMatch.startsWithCut(b.text, teaser) ||
                    (TextMatch.words(b.text).size >= 6 && TextMatch.covers(teaser, b.text))
                ) {
                    found += "TEASER: [$i] ${b.text.take(80)}"
                }
            }
        }
        for (x in texts.indices) for (y in x + 1 until texts.size) {
            val (i, a) = texts[x]
            val (j, b) = texts[y]
            val heading = a.type == ArticleBlock.HEADING || b.type == ArticleBlock.HEADING
            val repeat = TextMatch.same(a.text, b.text) || !heading && (
                (x < 8 && TextMatch.words(a.text).size >= 8 && TextMatch.covers(b.text, a.text)) ||
                    (x < 8 && TextMatch.words(b.text).size >= 8 && TextMatch.covers(a.text, b.text))
                )
            if (repeat) found += "REPEAT: [$i]/[$j] ${a.text.take(60)}"
        }
        blocks.take(4).withIndex().firstOrNull { it.value.type == ArticleBlock.IMAGE }?.let { (i, b) ->
            if (ImageMatch.same(b.url, hero)) found += "HERO: [$i] ${b.url}"
        }
        val images = blocks.withIndex().filter { it.value.type == ArticleBlock.IMAGE }
        for (x in images.indices) for (y in x + 1 until images.size) {
            if (ImageMatch.same(images[x].value.url, images[y].value.url)) found += "IMAGE_TWICE: [${images[x].index}]/[${images[y].index}]"
        }
        blocks.forEachIndexed { i, b ->
            if (b.type == ArticleBlock.CAPTION && i > 0 && blocks[i - 1].type == ArticleBlock.IMAGE) {
                val alt = blocks[i - 1].alt
                if (alt.isNotBlank() && (TextMatch.same(alt, b.text) || TextMatch.covers(b.text, alt) || TextMatch.covers(alt, b.text))) {
                    found += "CAPTION: [$i] ${b.text.take(60)}"
                }
            }
            if (b.type == ArticleBlock.CAPTION && blocks.withIndex().any { (k, o) -> k < i && o.type == ArticleBlock.CAPTION && TextMatch.same(o.text, b.text) }) {
                found += "CAPTION_TWICE: [$i]"
            }
        }
        val edge = texts.take(4) + texts.takeLast(3)
        edge.distinctBy { it.index }.forEach { (i, b) ->
            if (b.text.length <= 140 && NOISE.containsMatchIn(b.text)) found += "NOISE: [$i] ${b.text.take(80)}"
        }
        return found
    }

    private val TEXT = setOf(ArticleBlock.PARAGRAPH, ArticleBlock.HEADING, ArticleBlock.QUOTE)
}

/**
 * Test-only Google News link decoder (the app's real one lives in the news engine): the two
 * requests documented in the v3 spec.
 */
internal class GoogleNewsTestResolver(private val http: OkHttpClient) : LinkResolver {
    override suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        val id = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull() ?: return@withContext url
        val page = Request.Builder().url("https://news.google.com/rss/articles/$id?oc=5&hl=en-US&gl=US&ceid=US:en")
            .header("Cookie", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36")
            .build()
        val html = http.newCall(page).execute().use { it.body.string() }
        val sg = Regex("data-n-a-sg=\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: return@withContext url
        val ts = Regex("data-n-a-ts=\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: return@withContext url
        val inner = "[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"$id\",$ts,\"$sg\"]"
        val fReq = "[[[\"Fbv4je\",${kotlinx.serialization.json.JsonPrimitive(inner)},null,\"generic\"]]]"
        val post = Request.Builder().url("https://news.google.com/_/DotsSplashUi/data/batchexecute")
            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .post(FormBody.Builder().add("f.req", fReq).build())
            .build()
        val body = http.newCall(post).execute().use { it.body.string() }
        Regex("garturlres\\\\\",\\\\\"(https?://[^\"\\\\]+)").find(body)?.groupValues?.get(1) ?: url
    }
}

package com.cedrickgd.morgenbriefing.data.article

import android.util.Log
import com.cedrickgd.morgenbriefing.data.local.JsonFiles
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import com.cedrickgd.morgenbriefing.data.remote.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The reader could not show the article. [outcome] says why (never [ReadOutcome.OK]); [site] is
 * the newsroom's name when known, [httpCode] the server's status when it answered with an
 * error. The reader turns this into text with `ReaderStrings`.
 */
class ArticleException(
    val outcome: ReadOutcome,
    val site: String = "",
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : IOException(buildString {
    append(outcome.name)
    if (httpCode != null) append(" (HTTP ").append(httpCode).append(')')
    if (site.isNotBlank()) append(" – ").append(site)
}, cause)

/**
 * Loads the readable text of an article: resolves aggregator links (Google News) to the
 * publisher's page with [linkResolver], fetches the page once like a browser would (one cookie
 * jar per load, so sites that set a cookie and redirect work; `Accept-Language` in the
 * article's language; no scripts), extracts the article with [ArticleExtractor] and keeps the
 * result as JSON (`<dir>/articles/<id>.json`) and in memory, so a second visit is instant and
 * offline. Every outcome is noted in [readability] for the publisher's domain.
 *
 * Publishers are respected: no paywall, consent or bot-check is worked around. The only second
 * try is the AMP version a page links to itself, when the page gave no readable text.
 */
class ArticleRepository(
    http: OkHttpClient,
    dir: File,
    private val json: Json,
    private val linkResolver: LinkResolver = LinkResolver.Identity,
    /** Readability bookkeeping per domain; null = do not learn (tests). */
    val readability: ReadabilityStore? = null,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
    /** Only https pages (the app never loads cleartext); false only for tests against a local mock server. */
    private val requireHttps: Boolean = true,
) {
    private val client: OkHttpClient = http.newBuilder()
        .callTimeout(25, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        // Redirects are followed by hand (fetch), with the load's own cookies.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val folder = File(dir, "articles")
    private val lock = Mutex()
    private val memory = object : LinkedHashMap<String, ArticleContent>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArticleContent>): Boolean = size > MEMORY_ENTRIES
    }
    @Volatile private var pruned = false

    /**
     * The article behind [item], from the cache or freshly read from the page.
     *
     * @param language ISO 639-1 language of the item's edition, for `Accept-Language` when the
     *   site's domain does not tell (a `.com`); null = guess from the domain, else English.
     * @throws ArticleException when the page is unreachable or holds no readable article.
     */
    suspend fun load(item: NewsItem, refresh: Boolean = false, language: String? = null): ArticleContent {
        val id = item.id
        if (!refresh) cachedOrNull(item)?.let { return it }
        val url = resolve(item)
        val host = url.toHttpUrlOrNull()?.host.orEmpty()
        val site = item.source.ifBlank { item.publisherDomain }
        val content = try {
            read(url, site, language)
        } catch (e: ArticleException) {
            readability?.record(host, e.outcome)
            throw e
        }
        readability?.record(host, ReadOutcome.OK)
        synchronized(memory) { memory[id] = content }
        store(id, content)
        return content
    }

    /** The cached article of [item] in memory (no disk, no network), or null. */
    fun peek(item: NewsItem): ArticleContent? = synchronized(memory) { memory[item.id] }?.takeIf(::isCurrent)

    /** The cached article of [item] (memory, then disk; never the network), or null. */
    suspend fun cachedOrNull(item: NewsItem): ArticleContent? {
        val id = item.id
        synchronized(memory) { memory[id] }?.let { content ->
            if (isCurrent(content)) return content
            synchronized(memory) { memory.remove(id) }
        }
        val content = cached(id) ?: return null
        if (!isCurrent(content)) {
            // Read by an older extractor (other cleanup) or a consent page: forget it, read the page again.
            withContext(Dispatchers.IO) { lock.withLock { fileFor(id).delete() } }
            return null
        }
        synchronized(memory) { memory[id] = content }
        return content
    }

    private fun isCurrent(content: ArticleContent): Boolean =
        content.format >= ArticleContent.FORMAT && !ArticleExtractor.looksLikeConsentWall(content)

    /** Removes cached articles older than [maxAge]; runs once per process. */
    suspend fun pruneOnce(maxAge: Duration = KEEP_FOR) {
        if (pruned) return
        pruned = true
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - maxAge.toMillis()
            lock.withLock {
                folder.listFiles()?.forEach { file -> if (file.isFile && file.lastModified() < cutoff) file.delete() }
                JsonFiles.pruneTemp(folder)
            }
        }
    }

    /** The publisher's URL of [item]: aggregator links go through [linkResolver] first. */
    private suspend fun resolve(item: NewsItem): String {
        val direct = item.url.toHttpUrlOrNull()
        val aggregated = item.aggregator != null || direct?.host?.let(::isAggregatorHost) == true
        if (!aggregated) return item.url
        val site = item.source.ifBlank { item.publisherDomain }
        val resolved = try {
            linkResolver.resolve(item.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ArticleException(ReadOutcome.NETWORK, site, cause = e)
        }
        val url = resolved.toHttpUrlOrNull()
        if (url == null || isAggregatorHost(url.host)) throw ArticleException(ReadOutcome.NOT_ARTICLE, site)
        return url.toString()
    }

    /** Fetches and extracts [url]; the AMP version as a second try when the page gave no text. */
    private suspend fun read(url: String, site: String, language: String?): ArticleContent {
        val page = fetch(url, site, language)
        val first = withContext(Dispatchers.Default) { ArticleExtractor.read(page.html, page.url, clock()) }
        val siteName = site.ifBlank { first.content?.site.orEmpty() }
        val content = first.content
        val weak = first.outcome == ReadOutcome.NOT_ARTICLE || (first.outcome == ReadOutcome.OK && content != null && content.looksTruncated)
        val amp = first.ampUrl
        if (weak && amp != null && amp != page.url && sameSite(amp, page.url)) {
            val second = try {
                val ampPage = fetch(amp, siteName, language)
                withContext(Dispatchers.Default) { ArticleExtractor.read(ampPage.html, ampPage.url, clock()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ArticleException) {
                null
            }
            val better = second?.content
            if (second?.outcome == ReadOutcome.OK && better != null && better.wordCount > (content?.wordCount ?: 0)) {
                return better.copy(url = page.url, image = content?.image ?: better.image)
            }
        }
        if (first.outcome != ReadOutcome.OK || content == null) throw ArticleException(first.outcome, siteName)
        return content
    }

    private class Fetched(val html: String, val url: String)

    /**
     * GETs [url] like a browser tab would: redirects followed by hand (at most [MAX_REDIRECTS],
     * http upgraded to https) with one [SessionCookies] jar for the whole chain, so a site that
     * sets a cookie and redirects to itself gets it back; a chain that keeps going in circles
     * is a bot check ([ReadOutcome.BLOCKED]), a hop to a consent page is [ReadOutcome.CONSENT].
     */
    private suspend fun fetch(url: String, site: String, language: String?): Fetched {
        var current = url.toHttpUrlOrNull()?.takeIf { it.isHttps || !requireHttps } ?: throw ArticleException(ReadOutcome.NOT_ARTICLE, site)
        val cookies = SessionCookies()
        val visited = HashMap<String, Int>()
        repeat(MAX_REDIRECTS + 1) {
            val builder = Request.Builder()
                .url(current)
                .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", acceptLanguage(language, current))
                .header("User-Agent", USER_AGENT)
                .get()
            cookies.header(current)?.let { builder.header("Cookie", it) }
            val response = try {
                client.newCall(builder.build()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                throw ArticleException(ReadOutcome.NETWORK, site, cause = e)
            }
            cookies.save(current, response.headers("Set-Cookie"))
            if (response.isRedirect) {
                val location = response.header("Location")
                response.close()
                val next = location?.let { current.resolve(it) }
                    ?.let { if (it.scheme == "http" && requireHttps) it.newBuilder().scheme("https").build() else it }
                    ?: throw ArticleException(ReadOutcome.BLOCKED, site, response.code)
                if (PageSignals.isConsentRedirect(url, next.toString())) throw ArticleException(ReadOutcome.CONSENT, site)
                // The same address a third time: a loop that only a browser running the page's scripts escapes.
                val seen = (visited[next.toString()] ?: 0) + 1
                if (seen > 2) throw ArticleException(ReadOutcome.BLOCKED, site, response.code)
                visited[next.toString()] = seen
                current = next
                return@repeat
            }
            return readResponse(response, current.toString(), site)
        }
        throw ArticleException(ReadOutcome.BLOCKED, site)
    }

    private suspend fun readResponse(response: Response, landed: String, site: String): Fetched = response.use { r ->
        if (!r.isSuccessful) {
            val head = withContext(Dispatchers.IO) { runCatching { r.body.byteStream().use { it.readCapped(ERROR_BYTES) }.toString(Charsets.UTF_8) }.getOrDefault("") }
            throw ArticleException(outcomeOf(r.code, head), site, r.code)
        }
        val type = r.body.contentType()
        if (type != null && type.type != "text" && type.subtype != "xhtml+xml" && type.subtype != "html") {
            throw ArticleException(ReadOutcome.NOT_ARTICLE, site)
        }
        val html = withContext(Dispatchers.IO) {
            try {
                val bytes = r.body.byteStream().use { it.readCapped(MAX_HTML_BYTES) }
                // jsoup sniffs the charset (BOM, meta) when none is given; the header wins when it is.
                val charset = type?.charset()?.name()
                Jsoup.parse(ByteArrayInputStream(bytes), charset, landed).outerHtml()
            } catch (e: IOException) {
                throw ArticleException(ReadOutcome.NETWORK, site, cause = e)
            }
        }
        Fetched(html, landed)
    }

    private suspend fun cached(id: String): ArticleContent? = withContext(Dispatchers.IO) {
        val file = fileFor(id)
        val text = JsonFiles.readOrNull(file) ?: return@withContext null
        try {
            json.decodeFromString(ArticleContent.serializer(), text)
        } catch (_: SerializationException) {
            lock.withLock { file.delete() }
            null
        } catch (_: IllegalArgumentException) {
            lock.withLock { file.delete() }
            null
        }
    }

    private suspend fun store(id: String, content: ArticleContent) {
        val payload = json.encodeToString(ArticleContent.serializer(), content)
        try {
            withContext(Dispatchers.IO) {
                lock.withLock {
                    JsonFiles.writeAtomically(fileFor(id), payload)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache article $id", e)
        }
    }

    private fun fileFor(id: String): File =
        File(folder, id.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("") + ".json")

    private fun InputStream.readCapped(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (total < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - total))
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "ArticleRepository"
        private const val MEMORY_ENTRIES = 30
        private const val MAX_HTML_BYTES = 3 * 1024 * 1024
        private const val ERROR_BYTES = 64 * 1024
        private const val MAX_REDIRECTS = 10

        /** Cached articles are kept this long (the feed itself forgets articles after a few days). */
        val KEEP_FOR: Duration = Duration.ofDays(7)

        /** What a mobile browser sends — newsrooms serve their normal article page to it. */
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36 DailyBriefing/3.0"

        internal fun isFresh(content: ArticleContent, now: Instant, maxAge: Duration): Boolean =
            runCatching { Duration.between(ZonedDateTime.parse(content.fetchedAt).toInstant(), now) <= maxAge }.getOrDefault(false)

        internal fun isAggregatorHost(host: String): Boolean = host.equals("news.google.com", ignoreCase = true)

        /** What an error status means for the reader ([body] = the start of the error page). */
        internal fun outcomeOf(code: Int, body: String): ReadOutcome = when {
            code == 401 || code == 403 || code == 451 -> ReadOutcome.BLOCKED
            PageSignals.isBotCheck(body) -> ReadOutcome.BLOCKED
            code in 300..399 -> ReadOutcome.BLOCKED
            code == 404 || code == 410 -> ReadOutcome.NOT_ARTICLE
            code == 408 || code == 429 || code >= 500 -> ReadOutcome.NETWORK
            else -> ReadOutcome.NOT_ARTICLE
        }

        private val LANGUAGE_BY_TLD = mapOf(
            "de" to "de", "at" to "de", "ch" to "de", "fr" to "fr", "be" to "fr", "es" to "es", "mx" to "es", "ar" to "es",
            "it" to "it", "nl" to "nl", "pl" to "pl", "pt" to "pt", "br" to "pt", "se" to "sv", "tr" to "tr", "jp" to "ja",
            "uk" to "en", "us" to "en", "ie" to "en", "au" to "en", "ca" to "en", "nz" to "en", "in" to "en",
        )

        /**
         * `Accept-Language` for [url]: the language its country domain implies, else [language]
         * (the item's edition), else English – always with English as the fallback.
         */
        internal fun acceptLanguage(language: String?, url: HttpUrl): String {
            val tld = url.host.substringAfterLast('.').lowercase()
            val lang = LANGUAGE_BY_TLD[tld] ?: language?.lowercase()?.takeIf { it.length == 2 } ?: "en"
            return if (lang == "en") "en-US,en;q=0.9" else "$lang,en;q=0.7"
        }

        private fun sameSite(a: String, b: String): Boolean {
            val da = a.toHttpUrlOrNull()?.let { ReadabilityStore.domainOf(it.host) }
            return da != null && da == b.toHttpUrlOrNull()?.let { ReadabilityStore.domainOf(it.host) }
        }
    }
}

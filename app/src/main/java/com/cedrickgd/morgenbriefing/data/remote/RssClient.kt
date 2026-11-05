package com.cedrickgd.morgenbriefing.data.remote

import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.news.GoogleNews
import com.cedrickgd.morgenbriefing.data.news.HtmlText
import com.cedrickgd.morgenbriefing.data.news.ParsedEntry
import com.cedrickgd.morgenbriefing.data.news.ParsedFeed
import com.cedrickgd.morgenbriefing.data.news.RssParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Dispatcher as HttpDispatcher
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Why a feed could not be read; the settings word it in the interface language. */
enum class FeedError {
    /** What the user typed is no web address at all. */
    NOT_A_URL,

    /** The address cannot be requested (malformed). */
    INVALID_URL,

    /** No answer: offline, DNS, timeout, TLS. */
    UNREACHABLE,

    /** HTTP 401/403: the site refuses readers like this app. */
    BLOCKED,

    /** HTTP 404/410. */
    NOT_FOUND,

    /** HTTP 429. */
    RATE_LIMITED,

    /** HTTP 5xx. */
    SERVER_ERROR,

    /** Any other unsuccessful HTTP status ([FeedException.httpCode]). */
    HTTP_ERROR,

    /** The answer is no RSS/Atom feed (or broken XML). */
    NOT_A_FEED,

    /** A publisher feed without a single article. */
    EMPTY,

    /** Neither the address nor the page behind it led to a feed. */
    NO_FEED_FOUND,

    /** Something unexpected went wrong while reading. */
    UNREADABLE,
    ;

    companion object {
        /** The error of an unsuccessful HTTP [status]. */
        fun forHttp(status: Int): FeedError = when (status) {
            401, 403 -> BLOCKED
            404, 410 -> NOT_FOUND
            429 -> RATE_LIMITED
            in 500..599 -> SERVER_ERROR
            else -> HTTP_ERROR
        }
    }
}

/**
 * A feed that could not be read. [error] says why (the settings word it per language, see
 * `i18n/SourcesStrings`); the exception message is a short English text for the log only.
 */
class FeedException(
    val error: FeedError,
    val httpCode: Int? = null,
    cause: Throwable? = null,
) : IOException(describe(error, httpCode, cause), cause) {

    /**
     * Stable code stored with a source's status
     * ([com.cedrickgd.morgenbriefing.data.model.SourceStatus.error]): `NOT_FOUND:404`,
     * `UNREACHABLE`. [parse] reads it back.
     */
    val reason: String get() = code(error, httpCode)

    companion object {
        fun code(error: FeedError, httpCode: Int? = null): String =
            if (httpCode != null) "${error.name}:$httpCode" else error.name

        /**
         * The error and HTTP status behind a stored [code]; null for an empty one. App version 2
         * stored German sentences instead: the known ones are recognised, any other text counts
         * as [FeedError.UNREADABLE].
         */
        fun parse(code: String?): Pair<FeedError, Int?>? {
            val text = code?.trim().orEmpty()
            if (text.isEmpty()) return null
            val name = text.substringBefore(':')
            FeedError.entries.firstOrNull { it.name == name }?.let { return it to text.substringAfter(':', "").toIntOrNull() }
            val status = LEGACY_STATUS.find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
            val legacy = when {
                text.startsWith("Nicht erreichbar") -> FeedError.UNREACHABLE
                text.startsWith("Die Seite blockt") -> FeedError.BLOCKED
                text.startsWith("Feed nicht gefunden") -> FeedError.NOT_FOUND
                text.startsWith("Zu viele Anfragen") -> FeedError.RATE_LIMITED
                text.startsWith("Server antwortet nicht") -> FeedError.SERVER_ERROR
                text.startsWith("HTTP ") -> FeedError.HTTP_ERROR
                text.contains("keine Meldungen") -> FeedError.EMPTY
                text.contains("kein RSS") || text.contains("kein gültiges XML") || text.contains("kann nicht gelesen") -> FeedError.NOT_A_FEED
                text.startsWith("Ungültige Adresse") -> FeedError.INVALID_URL
                text.startsWith("Das ist keine Web-Adresse") -> FeedError.NOT_A_URL
                text.startsWith("Hier ist kein Feed") -> FeedError.NO_FEED_FOUND
                else -> FeedError.UNREADABLE
            }
            return legacy to status
        }

        private val LEGACY_STATUS = Regex("\\((\\d{3})\\)|^HTTP (\\d{3})")

        private fun describe(error: FeedError, httpCode: Int?, cause: Throwable?): String = buildString {
            append(error.name.lowercase().replace('_', ' '))
            if (httpCode != null) append(" (HTTP ").append(httpCode).append(')')
            cause?.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
        }
    }
}

/** What [RssClient.discover] found behind a URL the user typed. */
data class DiscoveredFeed(
    /** The feed's own URL (may differ from the typed page URL). */
    val url: String,
    /** The feed's title, for the source name ("heise online News"). */
    val title: String,
    val itemCount: Int,
)

/**
 * Fetches and parses RSS/Atom feeds. Runs on the shared OkHttp client with a 20 s read timeout
 * per feed; a feed that fails never throws anything but [FeedException] (besides cancellation),
 * so one broken newsroom cannot take a topic down.
 */
class RssClient(
    http: OkHttpClient,
    private val parser: RssParser,
) {
    private val client: OkHttpClient = http.newBuilder()
        // The news repository limits its fetches itself (a pool for refreshes, one for what the
        // user waits for). OkHttp's own 5 per host would queue a search behind a refresh's
        // Google News feeds, since they all go to news.google.com.
        .dispatcher(HttpDispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
        .callTimeout(25, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * The articles of [source], in feed order. Publisher feeds name the feed as the source;
     * Google News items name their publisher and may legitimately be none (a narrow search).
     * @throws FeedException when the feed is unreachable, not a feed, or (publisher feeds) empty.
     */
    suspend fun fetch(source: FeedSource): List<NewsItem> {
        val feed = fetchFeed(source.url)
        if (source.isGoogleNews) return feed.entries.mapNotNull { googleNewsItem(it, source) }
        val items = feed.entries.mapNotNull { publisherItem(it, source) }
        if (items.isEmpty()) throw FeedException(FeedError.EMPTY)
        return items
    }

    /**
     * Finds the feed behind [input]: the URL itself when it is a feed, else the RSS/Atom link
     * the page announces in its `<head>`, else common paths (`/feed`, `/rss`, `/rss.xml`).
     * @throws FeedException when nothing usable is found.
     */
    suspend fun discover(input: String): DiscoveredFeed {
        val typed = normalizeUrl(input) ?: throw FeedException(FeedError.NOT_A_URL)
        val candidates = mutableListOf(typed)
        var lastError: FeedException? = null
        var pageHtml: String? = null
        for (round in 0..1) {
            val url = candidates.removeFirstOrNull() ?: break
            try {
                val feed = fetchFeed(url)
                if (feed.entries.isNotEmpty()) {
                    return DiscoveredFeed(url = url, title = feed.title.ifBlank { hostOf(url) }, itemCount = feed.entries.size)
                }
                lastError = FeedException(FeedError.EMPTY)
            } catch (e: FeedException) {
                lastError = e
                if (round == 0) {
                    pageHtml = fetchHtmlOrNull(url)
                    val announced = pageHtml?.let { discoverFeedLinks(it, url) }.orEmpty()
                    candidates += announced
                    if (announced.isEmpty()) candidates += commonFeedPaths(url)
                }
            }
        }
        // Try the remaining announced/common paths (at most a few).
        for (url in candidates.take(4)) {
            try {
                val feed = fetchFeed(url)
                if (feed.entries.isNotEmpty()) {
                    return DiscoveredFeed(url = url, title = feed.title.ifBlank { hostOf(url) }, itemCount = feed.entries.size)
                }
            } catch (e: FeedException) {
                lastError = e
            }
        }
        throw lastError ?: FeedException(FeedError.NO_FEED_FOUND)
    }

    private suspend fun fetchFeed(url: String): ParsedFeed {
        val request = Request.Builder()
            .url(url.toHttpUrlOrNull() ?: throw FeedException(FeedError.INVALID_URL))
            .header("Accept", ACCEPT)
            .header("User-Agent", FEED_USER_AGENT)
            .get()
            .build()
        val response = try {
            client.newCall(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw FeedException(FeedError.UNREACHABLE, cause = e)
        }
        return response.use { r ->
            if (!r.isSuccessful) throw FeedException(FeedError.forHttp(r.code), httpCode = r.code)
            withContext(Dispatchers.IO) {
                try {
                    r.body.byteStream().use { stream -> parser.parse(stream) }
                } catch (e: IOException) {
                    throw FeedException(FeedError.NOT_A_FEED, cause = e)
                }
            }
        }
    }

    private suspend fun fetchHtmlOrNull(url: String): String? {
        val request = Request.Builder()
            .url(url.toHttpUrlOrNull() ?: return null)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .header("User-Agent", PAGE_USER_AGENT)
            .get()
            .build()
        return try {
            client.newCall(request).await().use { r ->
                if (!r.isSuccessful) return null
                withContext(Dispatchers.IO) {
                    // Only the head matters; a few hundred KB are plenty.
                    val bytes = r.body.byteStream().use { it.readNBytesCompat(MAX_HTML_BYTES) }
                    String(bytes, Charsets.UTF_8)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            null
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
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
        /** Above what the news repository runs at once (6 refresh + 2 interactive fetches). */
        private const val MAX_REQUESTS_PER_HOST = 10
        private const val MAX_SUMMARY = 600
        private const val MAX_HTML_BYTES = 400 * 1024
        private const val ACCEPT = "application/rss+xml, application/atom+xml, application/rdf+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5"

        /** Honest, descriptive, with a contact — what newsroom servers expect from a reader. */
        const val FEED_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) DailyBriefing/3.0 (RSS reader; +https://github.com/CedrickGD/Daily-Briefing)"
        private const val PAGE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) DailyBriefing/3.0"

        /** One item of a publisher's feed, or null when it has no headline or no web link. */
        internal fun publisherItem(entry: ParsedEntry, source: FeedSource): NewsItem? {
            val link = entry.link.trim()
            val headline = entry.title.trim()
            if (headline.isEmpty() || !link.startsWith("http", ignoreCase = true)) return null
            // tagesschau lists its broadcast as an item titled just "tagesschau": no article.
            if (headline.equals(source.name, ignoreCase = true)) return null
            return NewsItem(
                id = stableId(link),
                headline = headline,
                summary = entry.summary.take(MAX_SUMMARY),
                url = link,
                source = source.name,
                sourceId = source.id,
                topicId = source.topicId,
                publishedAt = entry.publishedAt,
                image = entry.imageUrl?.takeIf { it.startsWith("https://", ignoreCase = true) }?.let { ArticleImage(it, entry.imageAlt) },
                publisherDomain = GoogleNews.domainOf(link),
            )
        }

        /**
         * One Google News item: the headline without its " - Publisher" suffix, the publisher
         * from `<source>` as the source name and domain, no teaser (the description only repeats
         * the headline), no picture; [NewsItem.url] stays the news.google.com link until the
         * reader resolves it.
         */
        internal fun googleNewsItem(entry: ParsedEntry, source: FeedSource): NewsItem? {
            val link = entry.link.trim()
            if (!link.startsWith("http", ignoreCase = true)) return null
            val publisher = entry.sourceName.trim()
            val headline = GoogleNews.stripPublisher(entry.title, publisher)
            if (headline.isEmpty() || GoogleNews.isQuotePage(headline)) return null
            val domain = GoogleNews.domainOf(entry.sourceUrl).ifEmpty { GoogleNews.domainOf(link).takeIf { it != GoogleNews.HOST }.orEmpty() }
            return NewsItem(
                id = stableId(link),
                headline = headline,
                summary = GoogleNews.teaser(entry.summary, headline, publisher).take(MAX_SUMMARY),
                url = link,
                source = publisher.ifEmpty { domain.ifEmpty { source.name } },
                sourceId = source.id,
                topicId = source.topicId,
                publishedAt = entry.publishedAt,
                publisherDomain = domain,
                aggregator = GoogleNews.AGGREGATOR,
            )
        }

        private val FEED_LINK = Regex("(?is)<link\\b[^>]*>")
        private val ATTR = Regex("(?is)\\b([a-z-]+)\\s*=\\s*([\"'])(.*?)\\2")

        /** The first 16 hex digits of SHA-1 over the normalized link: short, stable, unique enough. */
        fun stableId(link: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest(normalizeLinkForId(link).toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(16)
        }

        /** Lower-cased host, no scheme, no tracking query, no fragment, no trailing slash. */
        fun normalizeLinkForId(link: String): String {
            val trimmed = link.trim()
            val noFragment = trimmed.substringBefore('#')
            val query = noFragment.substringAfter('?', "")
            val base = noFragment.substringBefore('?')
            val keptQuery = query.split('&').filter { part ->
                val key = part.substringBefore('=').lowercase()
                part.isNotEmpty() && !key.startsWith("utm_") && key !in setOf("ref", "wt_mc", "maca", "cid", "fbclid", "gclid", "s", "source")
            }.joinToString("&")
            val withoutScheme = base.replaceFirst(Regex("(?i)^https?://"), "").replaceFirst(Regex("(?i)^www\\."), "")
            val path = withoutScheme.trimEnd('/').lowercase()
            return if (keptQuery.isEmpty()) path else "$path?$keptQuery"
        }

        /** Adds `https://` when the scheme is missing; null when the result is no URL. */
        fun normalizeUrl(input: String): String? {
            val text = input.trim()
            if (text.isEmpty() || text.any { it.isWhitespace() }) return null
            val withScheme = if (Regex("(?i)^https?://").containsMatchIn(text)) text else "https://$text"
            return withScheme.toHttpUrlOrNull()?.toString()
        }

        /** The feed URLs a page announces (`<link rel="alternate" type="application/rss+xml">`), resolved against [pageUrl]. */
        fun discoverFeedLinks(html: String, pageUrl: String): List<String> {
            val head = html.substringBefore("</head>", html).take(MAX_HTML_BYTES)
            return FEED_LINK.findAll(head).mapNotNull { match ->
                val attrs = ATTR.findAll(match.value).associate { it.groupValues[1].lowercase() to HtmlText.decodeEntities(it.groupValues[3]) }
                val type = attrs["type"].orEmpty().lowercase()
                if (!type.contains("rss") && !type.contains("atom")) return@mapNotNull null
                val href = attrs["href"]?.trim().orEmpty()
                if (href.isEmpty()) return@mapNotNull null
                resolve(pageUrl, href)
            }.distinct().toList()
        }

        private fun commonFeedPaths(url: String): List<String> {
            val base = url.toHttpUrlOrNull() ?: return emptyList()
            val root = "${base.scheme}://${base.host}"
            return listOf("$root/feed/", "$root/rss", "$root/rss.xml", "$root/feed.xml", "$root/atom.xml", "$root/index.rss")
        }

        private fun resolve(pageUrl: String, href: String): String? {
            val base = pageUrl.toHttpUrlOrNull() ?: return null
            return base.resolve(href)?.toString()
        }

        private fun hostOf(url: String): String = url.toHttpUrlOrNull()?.host?.removePrefix("www.") ?: url
    }
}

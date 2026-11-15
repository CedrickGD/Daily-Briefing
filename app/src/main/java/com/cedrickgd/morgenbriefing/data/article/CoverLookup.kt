package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.remote.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What looking for an article's cover picture ended in ([CoverLookup.find]). */
sealed interface CoverAnswer {
    /** The page names this picture (https). */
    data class Found(val url: String) : CoverAnswer

    /** The page was read and names no picture the app may load (or the link holds no page). */
    data object None : CoverAnswer

    /** The site answered with an error status ([code]): a bot check, a moved page. */
    data class Refused(val host: String, val code: Int) : CoverAnswer

    /** The aggregator link could not be resolved now ([code]: its HTTP status, 429 when it rate-limits). */
    data class Unresolved(val code: Int?) : CoverAnswer

    /** No connection, a timeout: says nothing about the article. */
    data object Unreachable : CoverAnswer
}

/**
 * Finds the cover picture of an article whose feed item carried none (every Google News item):
 * a light lookup instead of reading the whole article. The publisher's address comes from the
 * [LinkResolver] (cached there, so a link is resolved once), then only the start of the page is
 * read – up to its `</head>`, at most [MAX_HEAD_BYTES] – for `og:image` / `twitter:image`
 * ([CoverMeta]). Every news site that answered named its picture within the first 100 kB of the
 * page, mostly 8 kB on the wire (23 of 32 Google News stories, DE and US, checked 15.11.2026);
 * the others refused the request (403) – their cards keep the generated cover.
 *
 * Https only: the page is asked for over https, a hop to plain http is not followed, and a
 * picture address is upgraded to https or dropped.
 */
class CoverLookup(
    http: OkHttpClient,
    private val linkResolver: LinkResolver,
    private val requireHttps: Boolean = true,
) {
    private val client: OkHttpClient = http.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    /** The cover of [item]; [language] is the edition's language for `Accept-Language`. Never throws (but cancellation). */
    suspend fun find(item: NewsItem, language: String?): CoverAnswer {
        val address = try {
            linkResolver.resolveOrThrow(item.url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkResolveException) {
            return CoverAnswer.Unresolved(e.httpCode)
        } catch (e: IOException) {
            return CoverAnswer.Unresolved(null)
        }
        val url = address.toHttpUrlOrNull()
            ?.let { if (it.scheme == "http" && requireHttps) it.newBuilder().scheme("https").build() else it }
            ?.takeUnless { ArticleRepository.isAggregatorHost(it.host) }
            ?: return CoverAnswer.None
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", ArticleRepository.acceptLanguage(language, url))
            .header("User-Agent", ArticleRepository.USER_AGENT)
            .get()
            .build()
        return try {
            client.newCall(request).await().use { response -> read(response, url) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            CoverAnswer.Unreachable
        }
    }

    private suspend fun read(response: Response, asked: HttpUrl): CoverAnswer {
        val landed = response.request.url
        // A hop to plain http is not followed: the answer is that redirect, not a page.
        if (response.isRedirect || (requireHttps && !landed.isHttps)) return CoverAnswer.None
        if (!response.isSuccessful) return CoverAnswer.Refused(asked.host, response.code)
        val type = response.body.contentType()
        if (type != null && type.subtype != "html" && type.subtype != "xhtml+xml") return CoverAnswer.None
        val head = withContext(Dispatchers.IO) {
            val source = response.body.source()
            val buffer = Buffer()
            var scanned = 0L
            while (buffer.size < MAX_HEAD_BYTES) {
                if (source.read(buffer, CHUNK_BYTES) == -1L) break
                val from = (scanned - HEAD_END_OVERLAP).coerceAtLeast(0)
                if (HEAD_ENDS.any { buffer.indexOf(it, from) >= 0 }) break
                scanned = buffer.size
            }
            buffer.readString(type?.charset() ?: Charsets.UTF_8)
        }
        return CoverMeta.find(head, landed)?.let(CoverAnswer::Found) ?: CoverAnswer.None
    }

    companion object {
        /** The most that is read of a page: its head lies within this even on sites with inlined styles. */
        const val MAX_HEAD_BYTES = 384L * 1024
        private const val CHUNK_BYTES = 16L * 1024
        private const val HEAD_END_OVERLAP = 16L
        private val HEAD_ENDS = listOf("</head>".encodeUtf8(), "</HEAD>".encodeUtf8())
    }
}

/**
 * The cover picture a page names in its head. Pure, unit-tested.
 *
 * In this order: `og:image:secure_url`, `og:image`, `og:image:url`, `twitter:image`,
 * `twitter:image:src`, `<link rel="image_src">`. The address is made absolute against the page,
 * `http` becomes `https` (newsroom CDNs serve both), and what the app cannot show is skipped: no
 * https address, an SVG (logos), an over-long address.
 */
internal object CoverMeta {
    private val TAG = Regex("<(meta|link)\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val ATTRIBUTE = Regex("([a-zA-Z_:.-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|amp|quot|apos|lt|gt);")
    private val ORDER = listOf("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src", LINK_IMAGE)
    private const val LINK_IMAGE = "link:image_src"
    private const val MAX_URL_LENGTH = 2_048

    /** The picture [html] (the start of a page loaded from [page]) names, or null. */
    fun find(html: String, page: HttpUrl): String? {
        val named = HashMap<String, String>()
        for (tag in TAG.findAll(html)) {
            val attributes = HashMap<String, String>()
            for (match in ATTRIBUTE.findAll(tag.value)) {
                val value = match.groupValues[2].ifEmpty { match.groupValues[3] }.ifEmpty { match.groupValues[4] }
                attributes.putIfAbsent(match.groupValues[1].lowercase(), value)
            }
            if (tag.groupValues[1].equals("link", ignoreCase = true)) {
                if (attributes["rel"]?.lowercase() == "image_src") attributes["href"]?.let { named.putIfAbsent(LINK_IMAGE, it) }
            } else {
                val name = (attributes["property"] ?: attributes["name"])?.trim()?.lowercase() ?: continue
                val content = attributes["content"]?.takeIf { it.isNotBlank() } ?: continue
                named.putIfAbsent(name, content)
            }
        }
        return ORDER.firstNotNullOfOrNull { key -> named[key]?.let { address(it, page) } }
    }

    /** [raw] as an absolute https address the app may load, or null. */
    internal fun address(raw: String, page: HttpUrl): String? {
        val text = unescape(raw).trim()
        if (text.isEmpty() || text.startsWith("data:", ignoreCase = true)) return null
        val resolved = page.resolve(text) ?: return null
        val url = if (resolved.scheme == "http") resolved.newBuilder().scheme("https").build() else resolved
        if (!url.isHttps || url.username.isNotEmpty() || url.port != 443) return null
        if (url.encodedPath.endsWith(".svg", ignoreCase = true)) return null
        return url.toString().takeIf { it.length <= MAX_URL_LENGTH }
    }

    private fun unescape(text: String): String = if ('&' !in text) text else ENTITY.replace(text) { match ->
        val name = match.groupValues[1]
        when {
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
            name == "amp" -> "&"
            name == "quot" -> "\""
            name == "apos" -> "'"
            name == "lt" -> "<"
            name == "gt" -> ">"
            else -> null
        } ?: match.value
    }

    private fun codePoint(code: Int): String? = if (Character.isValidCodePoint(code) && code >= 0x20) String(Character.toChars(code)) else null
}

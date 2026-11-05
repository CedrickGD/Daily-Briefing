package com.cedrickgd.morgenbriefing.data.remote

import android.util.Log
import com.cedrickgd.morgenbriefing.data.article.LinkResolveException
import com.cedrickgd.morgenbriefing.data.article.LinkResolver
import com.cedrickgd.morgenbriefing.data.local.JsonFiles
import com.cedrickgd.morgenbriefing.data.news.GoogleNews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.decodeBase64
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Resolves Google News article links (`https://news.google.com/rss/articles/<ID>`) to the
 * publisher's URL, so the reader can load the article itself. Lazy: only links that are opened
 * or prefetched are resolved, never a whole feed.
 *
 * 1. Old-style ids are base64 of a small protobuf that holds the URL: decoded offline.
 * 2. Current ids (`AU_yqL…`) need Google: the article page carries a signature and a timestamp
 *    (`data-n-a-sg`, `data-n-a-ts`), which the `batchexecute` endpoint trades for the URL
 *    (`["garturlres","https://…",1]`). The page is asked for with Google's "reject all"
 *    consent cookie, otherwise EU requests end on consent.google.com (checked 05.11.2026).
 *
 * Results are kept in a small JSON file ([cacheFile], at most [maxEntries], oldest dropped
 * first). Failures (offline, timeout, an error status, a changed page) are not remembered: the
 * link is tried again next time. [resolve] then returns the original link, which the browser can
 * still open; [resolveOrThrow] says why, so the reader can offer to try again.
 */
class GoogleNewsDecoder(
    http: OkHttpClient,
    private val cacheFile: File,
    private val json: Json,
    private val baseUrl: HttpUrl = DEFAULT_BASE,
    private val maxEntries: Int = MAX_ENTRIES,
) : LinkResolver {

    private val client: OkHttpClient = http.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val lock = Mutex()

    /** id → publisher URL, oldest first. Guarded by [lock]. */
    private var links: LinkedHashMap<String, String>? = null

    /** A copy of [links] for [peek], replaced after every change (no lock needed to read it). */
    @Volatile
    private var snapshot: Map<String, String> = emptyMap()

    /**
     * Decodes in flight, so two screens asking for the same link share one round trip. A decode
     * that ends hands its result – the URL or why it failed – to everyone waiting; one that is
     * cancelled (its screen was left) cancels the deferred, and a waiter that still wants the
     * link decodes it itself.
     */
    private val inFlight = HashMap<String, CompletableDeferred<Result<String>>>()

    /** The publisher URL, or the original link when it cannot be resolved now (the browser can still open it). */
    override suspend fun resolve(url: String): String = try {
        resolveOrThrow(url)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Log.i(TAG, "Google News link not resolved: ${e.message}")
        url
    } catch (e: Exception) {
        Log.w(TAG, "Google News link not resolved", e)
        url
    }

    /**
     * The publisher URL; [url] itself when it is no Google News article link.
     *
     * @throws LinkResolveException when Google could not be asked or did not tell (offline,
     *   timeout, an error status, a changed page) – it is tried again next time.
     */
    override suspend fun resolveOrThrow(url: String): String {
        val id = GoogleNews.articleId(url) ?: return url
        cached(id)?.let { return it }
        decodeOffline(id)?.let { found ->
            remember(id, found)
            return found
        }
        while (true) {
            val (deferred, owner) = synchronized(inFlight) {
                inFlight[id]?.let { it to false } ?: CompletableDeferred<Result<String>>().also { inFlight[id] = it }.let { it to true }
            }
            if (owner) return decodeShared(id, url, deferred)
            val result = try {
                deferred.await()
            } catch (e: CancellationException) {
                // Either this caller was cancelled (rethrow) or the decode it waited for was: decode here.
                currentCoroutineContext().ensureActive()
                null
            }
            if (result != null) return result.getOrThrow()
        }
    }

    /** Decodes [id] online as the owner of [deferred]; whoever waits on it gets the same result. */
    private suspend fun decodeShared(id: String, url: String, deferred: CompletableDeferred<Result<String>>): String {
        var result: Result<String>? = null
        try {
            result = Result.success(decodeOnline(id, url))
        } catch (e: CancellationException) {
            throw e
        } catch (e: LinkResolveException) {
            result = Result.failure(e)
        } catch (e: IOException) {
            result = Result.failure(LinkResolveException(e.message ?: "Google News not reachable", null, e))
        } catch (e: Exception) {
            // A changed page or answer format must never break the reader: it is a failure to resolve, nothing more.
            Log.w(TAG, "Google News answer not understood", e)
            result = Result.failure(LinkResolveException("Google News answer not understood", null, e))
        } finally {
            synchronized(inFlight) { inFlight.remove(id) }
            // Cancelled (no result, or a read that failed because the call was cancelled): the
            // waiters must not take that for "Google did not tell".
            val outcome = result
            if (outcome == null || !currentCoroutineContext().isActive) deferred.cancel() else deferred.complete(outcome)
        }
        val found = result!!.getOrThrow()
        remember(id, found)
        return found
    }

    /** The publisher URL [url] resolved to before, without any I/O; null when not known (yet). */
    fun peek(url: String): String? {
        val id = GoogleNews.articleId(url) ?: return null
        return snapshot[id]
    }

    /** Reads the cache file once, so [peek] knows earlier results from the start. */
    suspend fun warm() {
        lock.withLock { loaded() }
    }

    private suspend fun cached(id: String): String? = lock.withLock { loaded()[id] }

    private suspend fun remember(id: String, target: String) {
        lock.withLock {
            val map = loaded()
            map.remove(id)
            map[id] = target
            while (map.size > maxEntries) map.remove(map.keys.first())
            snapshot = HashMap(map)
            val payload = json.encodeToString(CacheFile.serializer(), CacheFile(map))
            try {
                withContext(Dispatchers.IO) { JsonFiles.writeAtomically(cacheFile, payload) }
            } catch (e: IOException) {
                Log.w(TAG, "Could not store resolved links", e)
            }
        }
    }

    /** Must be called with [lock] held. */
    private suspend fun loaded(): LinkedHashMap<String, String> {
        links?.let { return it }
        val text = withContext(Dispatchers.IO) { JsonFiles.readOrNull(cacheFile) }
        val map = LinkedHashMap<String, String>()
        if (text != null) {
            try {
                map.putAll(json.decodeFromString(CacheFile.serializer(), text).links)
            } catch (_: SerializationException) {
                withContext(Dispatchers.IO) { cacheFile.delete() }
            } catch (_: IllegalArgumentException) {
                withContext(Dispatchers.IO) { cacheFile.delete() }
            }
        }
        links = map
        snapshot = HashMap(map)
        return map
    }

    /** Step 1 (article page → signature + timestamp) and step 2 (batchexecute → URL). */
    private suspend fun decodeOnline(id: String, original: String): String {
        val edition = original.toHttpUrlOrNull()
        val page = baseUrl.newBuilder()
            .addPathSegments("rss/articles").addPathSegment(id)
            .addQueryParameter("oc", "5")
            .addQueryParameter("hl", edition?.queryParameter("hl") ?: "en-US")
            .addQueryParameter("gl", edition?.queryParameter("gl") ?: "US")
            .addQueryParameter("ceid", edition?.queryParameter("ceid") ?: "US:en")
            .build()
        val pageRequest = Request.Builder().url(page)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", CONSENT_COOKIE)
            .get()
            .build()
        val html = client.newCall(pageRequest).await().use { r ->
            // Some links still redirect straight to the publisher.
            val landed = r.request.url
            if (landed.host != baseUrl.host && isPublisherUrl(landed.toString())) return landed.toString()
            if (!r.isSuccessful) throw LinkResolveException("article page HTTP ${r.code}", r.code)
            withContext(Dispatchers.IO) { r.body.string() }
        }
        val params = signatureOf(html) ?: throw LinkResolveException("article page without signature", null)
        val body = "f.req=" + URLEncoder.encode(batchRequest(id, params.timestamp, params.signature), "UTF-8")
        val post = Request.Builder().url(baseUrl.newBuilder().addPathSegments("_/DotsSplashUi/data/batchexecute").build())
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Referer", "https://${GoogleNews.HOST}/")
            .post(body.toRequestBody(FORM))
            .build()
        val answer = client.newCall(post).await().use { r ->
            if (!r.isSuccessful) throw LinkResolveException("batchexecute HTTP ${r.code}", r.code)
            withContext(Dispatchers.IO) { r.body.string() }
        }
        return parseBatchResponse(answer) ?: throw LinkResolveException("batchexecute without URL", null)
    }

    @Serializable
    private data class CacheFile(val links: Map<String, String> = emptyMap(), val version: Int = 1)

    /** `data-n-a-sg` and `data-n-a-ts` of an article page. */
    internal data class Signature(val signature: String, val timestamp: Long)

    companion object {
        private const val TAG = "GoogleNewsDecoder"
        const val MAX_ENTRIES = 1000
        private val DEFAULT_BASE = "https://${GoogleNews.HOST}/".toHttpUrl()
        private val FORM = "application/x-www-form-urlencoded;charset=UTF-8".toMediaType()

        /** Google's "reject all" consent: without it, EU requests are sent to consent.google.com. */
        internal const val CONSENT_COOKIE = "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg"

        /** Google serves the article page with its parameters only to browsers. */
        internal const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        private val SIGNATURE = Regex("data-n-a-sg=\"([^\"]+)\"")
        private val TIMESTAMP = Regex("data-n-a-ts=\"(\\d+)\"")
        private val RESULT_FALLBACK = Regex("garturlres\\\\\",\\\\\"(https?://[^\\\\\"]+)")

        /**
         * The URL an old-style id carries (base64url of `08 13 22 <len> <url>…`), or null for
         * current ids, which only Google can resolve.
         */
        internal fun decodeOffline(id: String): String? {
            val padded = id.replace('-', '+').replace('_', '/').let { it + "=".repeat((4 - it.length % 4) % 4) }
            val bytes = padded.decodeBase64()?.toByteArray() ?: return null
            if (bytes.size < 5 || bytes[0] != 0x08.toByte() || bytes[2] != 0x22.toByte()) return null
            var length = 0
            var shift = 0
            var index = 3
            while (index < bytes.size) {
                val b = bytes[index].toInt() and 0xFF
                length = length or ((b and 0x7F) shl shift)
                index++
                if (b and 0x80 == 0) break
                shift += 7
                if (shift > 21) return null
            }
            if (length <= 0 || index + length > bytes.size) return null
            val text = String(bytes, index, length, Charsets.UTF_8)
            return text.takeIf { isPublisherUrl(it) }
        }

        internal fun signatureOf(html: String): Signature? {
            val sg = SIGNATURE.find(html)?.groupValues?.get(1) ?: return null
            val ts = TIMESTAMP.find(html)?.groupValues?.get(1)?.toLongOrNull() ?: return null
            return Signature(sg, ts)
        }

        /** The `f.req` value of the URL request for one article. */
        internal fun batchRequest(id: String, timestamp: Long, signature: String): String {
            val inner = "[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1]," +
                "\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],${JsonPrimitive(id)},$timestamp,${JsonPrimitive(signature)}]"
            return buildJsonArray {
                addJsonArray {
                    addJsonArray {
                        add("Fbv4je")
                        add(inner)
                        add(JsonNull)
                        add("generic")
                    }
                }
            }.toString()
        }

        /** The publisher URL in a batchexecute answer (`)]}'` + JSON), or null. */
        internal fun parseBatchResponse(body: String): String? {
            val payload = body.substringAfter(")]}'", body).trim()
            val fromJson = try {
                Json.parseToJsonElement(payload).jsonArray.firstNotNullOfOrNull { entry ->
                    val row = entry as? JsonArray ?: return@firstNotNullOfOrNull null
                    if (row.getOrNull(1)?.jsonPrimitive?.contentOrNull != "Fbv4je") return@firstNotNullOfOrNull null
                    val inner = row.getOrNull(2)?.jsonPrimitive?.contentOrNull ?: return@firstNotNullOfOrNull null
                    val result = Json.parseToJsonElement(inner).jsonArray
                    result.getOrNull(1)?.jsonPrimitive?.contentOrNull?.takeIf { result.firstOrNull()?.jsonPrimitive?.contentOrNull == "garturlres" }
                }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
            val found = fromJson ?: RESULT_FALLBACK.find(body)?.groupValues?.get(1)
            return found?.takeIf { isPublisherUrl(it) }
        }

        private fun isPublisherUrl(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            return !parsed.host.endsWith("google.com")
        }
    }
}

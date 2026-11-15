package com.cedrickgd.morgenbriefing.data.article

import android.util.Log
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Cover pictures for the cards of articles whose feed item carried none (every Google News
 * item): resolved ahead of the list – the cards about to show and the next page – so the picture
 * is there when the card scrolls in.
 *
 * Independent of "Artikel vorladen" ([com.cedrickgd.morgenbriefing.data.model.PrefetchMode],
 * which is about the full text): a lookup reads only the start of the publisher's page
 * ([CoverLookup]) and runs on any connection, mobile data included. The caller does not ask at
 * all with "Bilder anzeigen" off.
 *
 * - [request] with the articles in priority order: known ones are skipped, the others queued –
 *   at most [parallel] lookups at a time ([lightParallel] while [light]: a budget phone, the
 *   battery saver), each article once. Waiting lookups of articles no longer asked for are
 *   dropped (the newest window comes first), running ones finish.
 * - [covers]: per item id the picture's address. Kept on disk ([CoverStore]) with the articles
 *   that have none, so a restart shows them at once and nothing is asked twice.
 * - The reader's and the prefetcher's finds count too ([follow], [cached]): an article that was
 *   read needs no lookup.
 * - Polite: a resolved Google News link is never resolved again (the [LinkResolver] keeps it);
 *   when Google cannot resolve links (429, an error status, three failures in a row) no further
 *   link is sent there for a pause that doubles each time ([GOOGLE_PAUSE_MS] up to
 *   [PAUSE_MAX_MS]) – articles whose link is already known ([resolved]) still get their cover.
 *   A site that refused [HOST_STRIKES] lookups in a row is left alone for [HOST_PAUSE_MS]; an
 *   article that failed for network reasons is tried again after a pause that doubles. Offline
 *   nothing is tried and nothing is noted.
 */
class CoverResolver(
    private val source: Source,
    private val network: NetworkMeter,
    private val store: CoverStore? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val parallel: Int = PARALLEL,
    private val lightParallel: Int = PARALLEL_LIGHT,
    private val light: () -> Boolean = { false },
    /** Epoch millis. */
    private val clock: () -> Long = System::currentTimeMillis,
    /** The publisher address an aggregator link resolved to before, without I/O; null when not known. */
    private val resolved: (String) -> String? = { null },
    /** The lead picture of the article as the reader cached it (no network), if any. */
    private val cached: suspend (NewsItem) -> String? = { null },
) {
    /** Where covers come from ([CoverLookup]; a fake in tests). */
    fun interface Source {
        suspend fun find(item: NewsItem, language: String?): CoverAnswer
    }

    private val lock = Any()
    private val gate = Semaphore(parallel)
    private val lightGate = Semaphore(lightParallel.coerceIn(1, parallel))

    /** Queued and running lookups, oldest first. Guarded by [lock]. */
    private val jobs = LinkedHashMap<String, Job>()
    private val running = HashSet<String>()

    /** When an item failed for network reasons, and how often in a row. Guarded by [lock]. */
    private class Failure(val at: Long, val attempts: Int)
    private val failures = HashMap<String, Failure>()

    /** Items found to have no picture in this process (also without a [store]). Guarded by [lock]. */
    private val missing = HashMap<String, Long>()

    /** Sites that refused lookups: strikes in a row, and until when they are left alone. Guarded by [lock]. */
    private val hostStrikes = HashMap<String, Int>()
    private val hostPausedUntil = HashMap<String, Long>()

    /** Guarded by [lock]. */
    private var googleFailures = 0
    private var googlePauses = 0
    private var googlePausedUntil = 0L

    private val _covers = MutableStateFlow<Map<String, String>>(emptyMap())
    val covers: StateFlow<Map<String, String>> = _covers.asStateFlow()

    /** Completes once the kept covers are in [covers] (at once without a [CoverStore]). */
    private val restored = CompletableDeferred<Unit>()

    init {
        val kept = store
        if (kept == null) {
            restored.complete(Unit)
        } else {
            scope.launch {
                try {
                    val known = kept.restore()
                    if (known.isNotEmpty()) _covers.update { live -> known + live }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Kept cover pictures unavailable", e)
                } finally {
                    restored.complete(Unit)
                }
            }
        }
    }

    /**
     * Looks up the covers of [items] (in priority order) that carry no picture of their own;
     * [language] is the edition's language for `Accept-Language`.
     */
    fun request(items: List<NewsItem>, language: String? = null) {
        val window = items.asSequence().filter { it.image == null }.distinctBy { it.id }.toList()
        dropWaitingExcept(window.mapTo(HashSet()) { it.id })
        if (window.isEmpty() || !network.isOnline()) return
        val known = _covers.value
        // Before the kept covers are read nothing is judged here (the store reads its file): the lookup itself checks again.
        val ready = restored.isCompleted
        for (item in window) {
            if (item.id in known || (ready && !mayTry(item))) continue
            synchronized(lock) {
                if (jobs[item.id]?.isActive == true) return@synchronized
                val job = scope.launch(start = CoroutineStart.LAZY) { lookUp(item, language) }
                jobs[item.id] = job
                job.invokeOnCompletion {
                    synchronized(lock) {
                        if (jobs[item.id] === job) jobs.remove(item.id)
                        running.remove(item.id)
                    }
                }
                job.start()
            }
        }
    }

    /** Stops the waiting lookups (the screen was left); running ones finish. */
    fun stop() = dropWaitingExcept(emptySet())

    /** A picture found elsewhere (the reader, the prefetcher) for [itemId]. */
    fun learn(itemId: String, url: String) {
        if (itemId.isBlank() || !url.startsWith("https://") || _covers.value[itemId] == url) return
        _covers.update { it + (itemId to url) }
        store?.record(itemId, url)
    }

    /** Takes over every picture [images] reports (the prefetcher's lead pictures, by item id). */
    fun follow(images: Flow<Map<String, String>>) {
        scope.launch {
            restored.await()
            images.collect { found -> found.forEach { (id, url) -> learn(id, url) } }
        }
    }

    private suspend fun lookUp(item: NewsItem, language: String?) {
        restored.await()
        if (item.id in _covers.value || !mayTry(item)) return
        val stored = try {
            cached(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (stored != null && stored.startsWith("https://")) {
            learn(item.id, stored)
            return
        }
        withSlot {
            // The wait may have been long: found meanwhile, offline now, or the site or Google paused?
            if (item.id !in _covers.value && network.isOnline() && mayTry(item)) {
                synchronized(lock) { running += item.id }
                settle(item, source.find(item, language))
            }
        }
    }

    /** Runs [block] in one of the [parallel] lookup slots – one of the [lightParallel] ones while [light]. */
    private suspend inline fun withSlot(crossinline block: suspend () -> Unit) {
        if (light()) lightGate.withPermit { gate.withPermit { block() } } else gate.withPermit { block() }
    }

    /** False while [item] is known to have no picture, failed a moment ago, or its site or Google is paused. */
    private fun mayTry(item: NewsItem): Boolean {
        if (store?.isKnownMissing(item.id) == true) return false
        val now = clock()
        return synchronized(lock) {
            missing[item.id]?.let { if (now - it < CoverStore.RETRY_AFTER.toMillis()) return@synchronized false }
            failures[item.id]?.let { failure ->
                val pause = minOf(PAUSE_MAX_MS, RETRY_AFTER_MS shl (failure.attempts - 1).coerceIn(0, 10))
                if (now - failure.at < pause) return@synchronized false
            }
            val known = resolved(item.url)
            if (item.aggregator != null && known == null && now < googlePausedUntil) return@synchronized false
            val host = hostOf(item, known)
            host.isEmpty() || now >= (hostPausedUntil[host] ?: 0L)
        }
    }

    private fun settle(item: NewsItem, answer: CoverAnswer) {
        val now = clock()
        when (answer) {
            is CoverAnswer.Found -> {
                synchronized(lock) {
                    failures.remove(item.id)
                    hostStrikes.remove(hostOf(item, resolved(item.url)))
                    if (item.aggregator != null) resetGoogle()
                }
                learn(item.id, answer.url)
            }
            CoverAnswer.None -> {
                synchronized(lock) {
                    failures.remove(item.id)
                    hostStrikes.remove(hostOf(item, resolved(item.url)))
                    if (item.aggregator != null) resetGoogle()
                }
                noteMissing(item.id, now)
            }
            is CoverAnswer.Refused -> {
                synchronized(lock) {
                    if (item.aggregator != null) resetGoogle()
                    val host = answer.host.lowercase().removePrefix("www.")
                    val strikes = (hostStrikes[host] ?: 0) + 1
                    hostStrikes[host] = strikes
                    if (strikes >= HOST_STRIKES) {
                        hostPausedUntil[host] = now + HOST_PAUSE_MS
                        hostStrikes.remove(host)
                    }
                }
                noteMissing(item.id, now)
            }
            is CoverAnswer.Unresolved -> synchronized(lock) {
                fail(item.id, now)
                googleFailures++
                val code = answer.code
                if (code == 429 || code == 403 || (code != null && code >= 500) || googleFailures >= GOOGLE_FAILURES) {
                    googlePausedUntil = now + minOf(PAUSE_MAX_MS, GOOGLE_PAUSE_MS shl googlePauses.coerceIn(0, 10))
                    googlePauses++
                    googleFailures = 0
                    Log.i(TAG, "Google News links paused (${code ?: "no answer"})")
                }
            }
            CoverAnswer.Unreachable -> synchronized(lock) { fail(item.id, now) }
        }
    }

    /** Must be called with [lock] held. */
    private fun resetGoogle() {
        googleFailures = 0
        googlePauses = 0
    }

    /** Must be called with [lock] held. */
    private fun fail(itemId: String, now: Long) {
        failures[itemId] = Failure(now, (failures[itemId]?.attempts ?: 0) + 1)
        if (failures.size > MAX_TRACKED) failures.remove(failures.minBy { it.value.at }.key)
    }

    private fun noteMissing(itemId: String, now: Long) {
        synchronized(lock) {
            missing[itemId] = now
            if (missing.size > MAX_TRACKED) missing.remove(missing.minBy { it.value }.key)
        }
        store?.record(itemId, null)
    }

    /** The site of [item]: Google News' publisher domain, else the host of its (resolved) link. */
    private fun hostOf(item: NewsItem, known: String?): String =
        item.publisherDomain.trim().lowercase().removePrefix("www.").ifEmpty {
            (known ?: item.url).toHttpUrlOrNull()?.host?.takeUnless(ArticleRepository::isAggregatorHost)?.lowercase()?.removePrefix("www.").orEmpty()
        }

    /** Waiting lookups of items that are no longer asked for ([wanted] are) go; running ones finish. */
    private fun dropWaitingExcept(wanted: Set<String>) {
        val dropped = synchronized(lock) {
            jobs.entries.filter { it.key !in wanted && it.key !in running }.map { it.key }.mapNotNull { jobs.remove(it) }
        }
        dropped.forEach { it.cancel() }
    }

    companion object {
        private const val TAG = "CoverResolver"

        /** Lookups at a time. */
        const val PARALLEL = 3

        /** Lookups at a time with light effects. */
        const val PARALLEL_LIGHT = 2

        /** The first pause before an article that failed for network reasons is tried again. */
        const val RETRY_AFTER_MS = 60_000L

        /** The first pause after Google could not resolve links. */
        const val GOOGLE_PAUSE_MS = 2 * 60_000L

        /** Link failures in a row (without a status that says "slow down") that start a pause as well. */
        const val GOOGLE_FAILURES = 3

        /** The longest pause (articles and Google). */
        const val PAUSE_MAX_MS = 30 * 60_000L

        /** Refusals in a row after which a site is left alone. */
        const val HOST_STRIKES = 3
        const val HOST_PAUSE_MS = 6 * 60 * 60_000L

        private const val MAX_TRACKED = 2000
    }
}

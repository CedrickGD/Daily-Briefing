package com.cedrickgd.morgenbriefing.data.article

import android.util.Log
import com.cedrickgd.morgenbriefing.data.local.JsonFiles
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.time.Duration

/**
 * Which sites the in-app reader can read, learned on the phone: per domain the count of
 * readable and unreadable articles and the last reason. Seeded with [KnownWalls]. Each article
 * counts once (the same page loaded again – prefetch after a restart, opening it twice – teaches
 * nothing new). A site joins the blocked ones when failures clearly outweigh successes:
 *
 * - a consent or bot wall (the whole site) after two in a row,
 * - paywalls, which close single articles, only when (nearly) everything is paid: at least
 *   [PAYWALL_MIN] paid articles, [PAYWALL_STREAK] in a row and [PAYWALL_MIN] per free one –
 *   a newsroom with many paid articles (SPIEGEL+, about half) stays readable; its paid items are
 *   hidden one by one by their own status,
 * - pages without an article only on a site that never had a readable one.
 *
 * A seeded site that reads fine twice in a row is let back in. A learned block expires after
 * [LEARNED_FOR] ([PAYWALL_LEARNED_FOR] for paywalls, whose mix changes): the site's items come
 * back, the next readable article clears it, the next wall blocks it again.
 *
 * Domains are registrable domains (`bbc.co.uk`, `spiegel.de`); a subdomain counts for its
 * parent. Persisted as JSON (`<dir>/readability.json`), thread-safe, at most [MAX_DOMAINS].
 */
class ReadabilityStore(
    private val file: File,
    private val json: Json,
    private val seed: Set<String> = KnownWalls.DOMAINS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** What the store knows about one domain. */
    @Serializable
    data class DomainStats(
        val ok: Int = 0,
        val failed: Int = 0,
        /** Readable articles in a row (since the last failure). */
        val okStreak: Int = 0,
        /** Unreadable articles in a row (since the last success). */
        val failStreak: Int = 0,
        /** The last recorded outcome. */
        val last: ReadOutcome? = null,
        /** Why the last failure failed. */
        val lastFailure: ReadOutcome? = null,
        /** Epoch millis of the last record. */
        val updatedAt: Long = 0,
        /** Of [failed]: articles behind a paywall. */
        val paywalled: Int = 0,
    )

    /** An article already counted, with what it was counted as. */
    @Serializable
    private data class Counted(val article: String, val outcome: ReadOutcome)

    @Serializable
    private data class Snapshot(
        val version: Int = 1,
        val domains: Map<String, DomainStats> = emptyMap(),
        /** The articles counted last, oldest first (at most [MAX_ARTICLES]). */
        val articles: List<Counted> = emptyList(),
    )

    private val lock = Any()
    private val writeLock = Mutex()
    private var domains: MutableMap<String, DomainStats>? = null

    /** Article key → what it was counted as, oldest first. Guarded by [lock]. */
    private val counted = LinkedHashMap<String, ReadOutcome>()
    private val _blocked = MutableStateFlow(seed.toCollection(LinkedHashSet()) as Set<String>)

    /** [blockedDomains] as a flow: the engine re-filters when the reader learns a new wall. */
    val blocked: StateFlow<Set<String>>
        get() {
            ensureLoaded()
            return _blocked.asStateFlow()
        }

    /**
     * Seeded and learned domains the reader cannot read, most certain first: learned ones by
     * their number of failures, then seeded ones seen failing here, then the rest of the seed.
     */
    fun blockedDomains(): Set<String> {
        ensureLoaded()
        return _blocked.value
    }

    /** True when articles from [host] (or its parent domain) are known to be unreadable. */
    fun isBlocked(host: String): Boolean {
        val domain = domainOf(host) ?: return false
        return KnownWalls.matches(domain, blockedDomains())
    }

    /** Why [host] is blocked (the last failure seen, [ReadOutcome.BLOCKED] for seeded sites), null when it is not. */
    fun blockReason(host: String): ReadOutcome? {
        if (!isBlocked(host)) return null
        val stats = statsFor(host)
        return stats?.lastFailure?.takeIf { it.isWall } ?: ReadOutcome.BLOCKED
    }

    /** Counts for [host]'s domain, null when nothing was recorded. */
    fun statsFor(host: String): DomainStats? {
        val domain = domainOf(host) ?: return null
        ensureLoaded()
        return synchronized(lock) { domains!![domain] }
    }

    /** All recorded domains with their counts. */
    fun snapshot(): Map<String, DomainStats> {
        ensureLoaded()
        return synchronized(lock) { domains!!.toMap() }
    }

    /**
     * Records how reading an article from [host] went. [ReadOutcome.NETWORK] says nothing about
     * the site and is ignored, as are aggregator hosts (news.google.com). [article] (its URL)
     * makes the record count once: the same article with the same outcome again is ignored.
     */
    suspend fun record(host: String, outcome: ReadOutcome, article: String? = null) {
        if (outcome == ReadOutcome.NETWORK) return
        val domain = domainOf(host) ?: return
        if (domain in IGNORED) return
        ensureLoaded()
        val key = article?.let(::articleKey)
        synchronized(lock) {
            if (key != null) {
                if (counted[key] == outcome) return
                counted.remove(key)
                counted[key] = outcome
                while (counted.size > MAX_ARTICLES) counted.remove(counted.keys.first())
            }
            val map = domains!!
            val old = map[domain] ?: DomainStats()
            val now = clock()
            map[domain] = if (outcome == ReadOutcome.OK) {
                old.copy(ok = old.ok + 1, okStreak = old.okStreak + 1, failStreak = 0, last = outcome, updatedAt = now)
            } else {
                old.copy(
                    failed = old.failed + 1, failStreak = old.failStreak + 1, okStreak = 0, last = outcome, lastFailure = outcome, updatedAt = now,
                    paywalled = old.paywalled + if (outcome == ReadOutcome.PAYWALL) 1 else 0,
                )
            }
            if (map.size > MAX_DOMAINS) {
                map.entries.sortedBy { it.value.updatedAt }.take(map.size - MAX_DOMAINS).map { it.key }.forEach(map::remove)
            }
            _blocked.value = computeBlocked(map)
        }
        try {
            withContext(Dispatchers.IO) {
                writeLock.withLock {
                    // Taken inside the write lock: whoever writes last writes the newest notes.
                    val payload = synchronized(lock) {
                        val articles = counted.map { (article, result) -> Counted(article, result) }
                        json.encodeToString(Snapshot.serializer(), Snapshot(domains = domains!!.toMap(), articles = articles))
                    }
                    JsonFiles.writeAtomically(file, payload)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the readability notes", e)
        }
    }

    private fun ensureLoaded() {
        if (domains != null) return
        synchronized(lock) {
            if (domains != null) return
            val loaded = JsonFiles.readOrNull(file)?.let { text ->
                try {
                    json.decodeFromString(Snapshot.serializer(), text)
                } catch (e: Exception) {
                    Log.w(TAG, "Readability notes unreadable, starting over", e)
                    null
                }
            }
            val map = HashMap(loaded?.domains.orEmpty())
            loaded?.articles?.takeLast(MAX_ARTICLES)?.forEach { counted[it.article] = it.outcome }
            domains = map
            _blocked.value = computeBlocked(map)
        }
    }

    private fun computeBlocked(map: Map<String, DomainStats>): Set<String> {
        val now = clock()
        val learned = map.filter { (domain, s) -> domain !in seed && isLearnedBlock(s, now) }
            .entries.sortedWith(compareByDescending<Map.Entry<String, DomainStats>> { it.value.failed }.thenBy { it.key })
            .map { it.key }
        val seededSeen = seed.filter { map[it] != null && !isCleared(map.getValue(it)) && map.getValue(it).failed > 0 }
            .sortedByDescending { map.getValue(it).failed }
        val seededRest = seed.filter { map[it] == null || (!isCleared(map.getValue(it)) && map.getValue(it).failed == 0) }
        return LinkedHashSet<String>().apply {
            addAll(learned)
            addAll(seededSeen)
            addAll(seededRest)
        }
    }

    private fun isLearnedBlock(s: DomainStats, now: Long): Boolean {
        val failure = s.lastFailure ?: return false
        val lasts = if (failure == ReadOutcome.PAYWALL) PAYWALL_LEARNED_FOR else LEARNED_FOR
        if (now - s.updatedAt > lasts.toMillis()) return false
        if (s.failed < 2 * s.ok) return false
        return when (failure) {
            // A paywall closes one article, not the site: only a site that is (nearly) all paid goes.
            ReadOutcome.PAYWALL -> s.paywalled >= PAYWALL_MIN && s.failStreak >= PAYWALL_STREAK && s.paywalled >= PAYWALL_MIN * s.ok
            ReadOutcome.CONSENT, ReadOutcome.BLOCKED -> s.failStreak >= 2
            ReadOutcome.NOT_ARTICLE -> s.ok == 0 && s.failStreak >= 4
            ReadOutcome.OK, ReadOutcome.NETWORK -> false
        }
    }

    /** A seeded site that read fine twice in a row is readable after all. */
    private fun isCleared(s: DomainStats): Boolean = s.okStreak >= 2

    companion object {
        private const val TAG = "ReadabilityStore"
        const val MAX_DOMAINS = 500

        /** How many counted articles are remembered (so a repeat is not counted twice). */
        const val MAX_ARTICLES = 400

        /**
         * Paid articles before a site counts as paywalled as a whole – also how many paid ones
         * per free one it takes – and how many of them in a row.
         */
        const val PAYWALL_MIN = 6
        const val PAYWALL_STREAK = 3
        val LEARNED_FOR: Duration = Duration.ofDays(30)
        val PAYWALL_LEARNED_FOR: Duration = Duration.ofDays(7)
        private val IGNORED = setOf("google.com", "news.google.com", "googleusercontent.com")

        /** One article, however its URL is written: host in lower case, no `www.`, no fragment. */
        internal fun articleKey(url: String): String {
            val parsed = url.trim().toHttpUrlOrNull() ?: return url.trim()
            val path = parsed.encodedPath.trimEnd('/')
            val query = parsed.encodedQuery?.let { "?$it" }.orEmpty()
            return parsed.host.lowercase().removePrefix("www.") + path + query
        }

        /** The registrable domain of [host] (or of a URL): `www.bbc.co.uk` → `bbc.co.uk`. */
        fun domainOf(host: String): String? {
            val trimmed = host.trim().lowercase()
            if (trimmed.isEmpty()) return null
            val url = (if ("://" in trimmed) trimmed else "https://$trimmed/").toHttpUrlOrNull() ?: return null
            val labels = url.host.split('.').filter { it.isNotEmpty() }
            if (labels.size <= 2 || labels.last().all(Char::isDigit)) return url.host.removePrefix("www.")
            // Country suffixes with a second level ("co.uk", "com.au"): keep three labels there.
            val suffix = labels.takeLast(2).joinToString(".")
            val keep = if (labels[labels.size - 2] in SECOND_LEVEL || suffix in SECOND_LEVEL_EXACT) 3 else 2
            return labels.takeLast(keep).joinToString(".")
        }

        /**
         * Second-level labels under country domains that are public suffixes themselves – the
         * common ones of news sites; no full public-suffix list needed for the reader's bookkeeping.
         */
        private val SECOND_LEVEL = setOf("co", "com", "org", "net", "gov", "ac", "edu", "ne", "or", "gv")
        private val SECOND_LEVEL_EXACT = setOf("qc.ca", "on.ca")
    }
}

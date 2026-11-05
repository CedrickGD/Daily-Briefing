package com.cedrickgd.morgenbriefing.data.article

import android.util.Log
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.PrefetchMode
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Verifies the articles the lists offer before they are shown, and loads articles ahead of time
 * so they open instantly (and offline); tells the lists what it learned about each one.
 *
 * "Verified" means: the article was fetched, extracted by the reader and judged a complete
 * article ([ArticleCompleteness]) – [ArticleStatus.READABLE]. A page that only shows the free
 * lead is [ArticleStatus.PAYWALL], like a paywall. Verdicts are kept per article across app
 * starts ([VerdictStore]), the text itself in the article cache.
 *
 * Public API for the lists (UI):
 * - [prefetch] with the articles to check, in priority order, and the settings. With
 *   [AppSettings.readableOnly] ("nur vollständige Artikel") every candidate is verified on any
 *   connection – the user asked for it; without, [AppSettings.prefetch] decides:
 *   [PrefetchMode.OFF] never loads, [PrefetchMode.WIFI_ONLY] only on an unmetered connection
 *   ([NetworkMeter], checked again right before each load: the phone may have left the Wi-Fi
 *   while the load waited), [PrefetchMode.ALWAYS] always. Known articles are answered at once,
 *   the rest is queued – at most [parallel] loads at a time ([lightParallel] while [light] says
 *   so: a budget phone or the battery saver), every item once (de-duplicated).
 *   Waiting loads of items no longer asked for are dropped, so the newest request comes first;
 *   more than [maxQueued] waiting are cut to the newest. A page without an article is not loaded
 *   again, a network failure only after a pause that doubles each time ([RETRY_AFTER_MS] up to
 *   [RETRY_MAX_MS]). Offline nothing is tried; [waitingForNetwork] says so, and the last request
 *   is taken up again once the phone is back online.
 * - [status]: per item id an [ArticleStatus] (missing = [ArticleStatus.UNKNOWN]) – the lists show
 *   only [ArticleStatus.READABLE] items with "nur vollständige Artikel"; [statusOf] for one item.
 * - [images]: per item id the article's lead picture once the page was read – feed cards of
 *   items without a picture (Google News) can show it.
 * - [cancel] / [cancelAll] stop waiting and running loads (leaving the screen, pull-to-refresh).
 * - [report]: the reader tells what happened when an article was opened.
 * - [verify]: checks one article right now and waits for the verdict (the morning notification).
 */
class ArticlePrefetcher(
    private val source: Source,
    private val network: NetworkMeter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val parallel: Int = PARALLEL,
    private val maxQueued: Int = 40,
    /** Epoch millis, for the pause before a failed item is tried again. */
    private val clock: () -> Long = System::currentTimeMillis,
    /** Verdicts kept across app starts; null = only for this process (tests). */
    private val verdicts: VerdictStore? = null,
    /** Loads at a time while [light] is true (light effects: budget phone, battery saver). */
    private val lightParallel: Int = PARALLEL_LIGHT,
    /** Asked before every load: true keeps to [lightParallel]. */
    private val light: () -> Boolean = { false },
) {
    /** Where the prefetcher gets articles from (the [ArticleRepository]; a fake in tests). */
    interface Source {
        /** The cached article, memory or disk, without the network. */
        suspend fun cached(item: NewsItem): ArticleContent?

        /** Reads the article (network). @throws ArticleException */
        suspend fun load(item: NewsItem, language: String?): ArticleContent

        /** Why [item]'s site is known to be unreadable, null when it is not. */
        fun blockReason(item: NewsItem): ReadOutcome?
    }

    constructor(repository: ArticleRepository, network: NetworkMeter, verdicts: VerdictStore? = null, light: () -> Boolean = { false }) :
        this(RepositorySource(repository), network, verdicts = verdicts, light = light)

    /** How a request may load: [mode] for plain prefetching; [verify] loads on any connection. */
    private data class Policy(val mode: PrefetchMode, val verify: Boolean)

    /** The last request, taken up again when the phone comes back online. */
    private class Request(val items: List<NewsItem>, val policy: Policy, val language: String?)

    private val lock = Any()
    private val gate = Semaphore(parallel)

    /**
     * Taken before [gate] by loads that start while [light] is true: at most [lightParallel] of
     * them run, and never more than [parallel] loads in all. Loads that started before the
     * switch finish as they are.
     */
    private val lightGate = Semaphore(lightParallel.coerceIn(1, parallel))

    /** Queued and running loads, oldest first. Guarded by [lock]. */
    private val jobs = LinkedHashMap<String, Job>()
    private val running = HashSet<String>()

    /** Why an item could not be read, when, and how often in a row. Guarded by [lock]. */
    private class Failure(val outcome: ReadOutcome?, val at: Long, val attempts: Int)
    private val failures = HashMap<String, Failure>()

    /** The policy of the newest [prefetch] call: what the settings say now, also for loads queued before. */
    @Volatile private var latest: Policy? = null

    /** Guarded by [lock]. */
    private var lastRequest: Request? = null
    private var networkWatch: Job? = null

    private val _status = MutableStateFlow<Map<String, ArticleStatus>>(emptyMap())
    val status: StateFlow<Map<String, ArticleStatus>> = _status.asStateFlow()

    private val _images = MutableStateFlow<Map<String, String>>(emptyMap())
    val images: StateFlow<Map<String, String>> = _images.asStateFlow()

    private val _waitingForNetwork = MutableStateFlow(false)

    /** Verification was asked for while the phone is offline: only articles verified before can show. */
    val waitingForNetwork: StateFlow<Boolean> = _waitingForNetwork.asStateFlow()

    /** Completes once the kept verdicts are in [status] (at once without a [VerdictStore]). */
    private val restored = CompletableDeferred<Unit>()

    init {
        val store = verdicts
        if (store == null) {
            restored.complete(Unit)
        } else {
            scope.launch {
                try {
                    val known = store.restore()
                    if (known.isNotEmpty()) {
                        _status.update { live -> known + live }
                        val now = clock()
                        synchronized(lock) {
                            known.filterValues { it == ArticleStatus.FAILED }.keys
                                .forEach { failures.putIfAbsent(it, Failure(ReadOutcome.NOT_ARTICLE, now, 1)) }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Article verdicts unavailable", e)
                } finally {
                    restored.complete(Unit)
                }
            }
        }
    }

    fun statusOf(itemId: String): ArticleStatus = _status.value[itemId] ?: ArticleStatus.UNKNOWN

    /**
     * Checks [items] (in priority order) as [settings] allow: verified on any connection with
     * [AppSettings.readableOnly], else warmed as [AppSettings.prefetch] says; the edition's
     * language goes out as `Accept-Language`.
     */
    fun prefetch(items: List<NewsItem>, settings: AppSettings) =
        prefetch(items, settings.prefetch, settings.edition.language, verify = settings.readableOnly)

    /**
     * Warms [items] in [mode]; [language] is the edition's language for `Accept-Language`.
     * [verify] ("nur vollständige Artikel") loads on any connection, whatever [mode] says.
     */
    fun prefetch(items: List<NewsItem>, mode: PrefetchMode, language: String? = null, verify: Boolean = false) {
        val policy = Policy(mode, verify)
        latest = policy
        val window = items.distinctBy { it.id }
        synchronized(lock) { lastRequest = Request(window, policy, language) }
        dropWaitingExcept(window.mapTo(HashSet()) { it.id })
        if (verify) {
            if (network.isOnline()) _waitingForNetwork.value = false else if (window.any { statusOf(it.id) !in SETTLED }) watchNetwork()
        }
        val now = clock()
        for (item in window) {
            if (statusOf(item.id) in SETTLED) continue
            synchronized(lock) {
                if (jobs[item.id]?.isActive != true && mayTryAgain(item.id, now)) {
                    val job = scope.launch(start = CoroutineStart.LAZY) { warm(item, policy, language, followLatest = true) }
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
        trimQueue()
    }

    /**
     * Verifies [item] now, on any connection, and returns its status: [ArticleStatus.READABLE]
     * when it is a complete article. Offline or after a network failure the status is not final
     * ([ArticleStatus.UNKNOWN] / [ArticleStatus.FAILED]). For the morning notification.
     */
    suspend fun verify(item: NewsItem, language: String?): ArticleStatus {
        restored.await()
        statusOf(item.id).takeIf { it in SETTLED }?.let { return it }
        // The feed is checking it already: wait for that load instead of a second one.
        synchronized(lock) { jobs[item.id] }?.join()
        statusOf(item.id).takeIf { it in SETTLED }?.let { return it }
        if (!mayTryAgain(item.id, clock())) return statusOf(item.id)
        warm(item, Policy(PrefetchMode.OFF, verify = true), language, followLatest = false)
        return statusOf(item.id)
    }

    /** Stops the loads of [itemIds] (waiting or running); their status goes back to unknown. */
    fun cancel(itemIds: Collection<String>) {
        val ids = itemIds.toHashSet()
        val cancelled = synchronized(lock) {
            lastRequest?.let { last ->
                val left = last.items.filterNot { it.id in ids }
                lastRequest = if (left.isEmpty()) null else Request(left, last.policy, last.language)
            }
            ids.mapNotNull { jobs.remove(it) }
        }
        cancelled.forEach { it.cancel() }
    }

    /** Stops every load (and forgets the request to take up again once online). */
    fun cancelAll() {
        val cancelled = synchronized(lock) {
            lastRequest = null
            networkWatch?.cancel()
            networkWatch = null
            jobs.values.toList().also { jobs.clear() }
        }
        _waitingForNetwork.value = false
        cancelled.forEach { it.cancel() }
    }

    /** The reader read [item]: it is verified now, or a teaser. */
    fun report(item: NewsItem, content: ArticleContent) = settle(item, content)

    /** The reader could not read [item]. */
    fun report(item: NewsItem, error: Throwable) = fail(item.id, (error as? ArticleException)?.outcome)

    private suspend fun warm(item: NewsItem, policy: Policy, language: String?, followLatest: Boolean) {
        restored.await()
        if (statusOf(item.id) in SETTLED) return
        source.cached(item)?.let {
            settle(item, it)
            return
        }
        source.blockReason(item)?.let {
            setStatus(item.id, ArticleStatus.of(it))
            return
        }
        if (!mayLoad(policy, followLatest)) return
        withLoadSlot {
            // The wait may have been long: still allowed (still online / on Wi-Fi, setting unchanged)?
            if (mayLoad(policy, followLatest)) load(item, language)
        }
    }

    /** Runs [block] in one of the [parallel] load slots – one of the [lightParallel] ones while [light]. */
    private suspend inline fun withLoadSlot(crossinline block: suspend () -> Unit) {
        if (light()) lightGate.withPermit { gate.withPermit { block() } } else gate.withPermit { block() }
    }

    private suspend fun load(item: NewsItem, language: String?) {
        synchronized(lock) { running += item.id }
        setStatus(item.id, ArticleStatus.LOADING)
        try {
            settle(item, source.load(item, language))
        } catch (e: CancellationException) {
            _status.update { if (it[item.id] == ArticleStatus.LOADING) it - item.id else it }
            throw e
        } catch (e: ArticleException) {
            fail(item.id, e.outcome)
        } catch (e: Exception) {
            fail(item.id, null)
        }
    }

    /** True when [policy] (and, with [followLatest], the newest request's policy) lets a load go out now. */
    private fun mayLoad(policy: Policy, followLatest: Boolean): Boolean {
        if (!network.isOnline()) {
            if (policy.verify) watchNetwork()
            return false
        }
        return allowed(policy) && (!followLatest || allowed(latest ?: policy))
    }

    private fun allowed(policy: Policy): Boolean = policy.verify || when (policy.mode) {
        PrefetchMode.OFF -> false
        PrefetchMode.WIFI_ONLY -> network.isUnmetered()
        PrefetchMode.ALWAYS -> true
    }

    /** Offline with verifications asked for: says so, and takes the last request up again once online. */
    private fun watchNetwork() {
        _waitingForNetwork.value = true
        synchronized(lock) {
            if (networkWatch?.isActive == true) return
            networkWatch = scope.launch {
                while (!network.isOnline()) delay(NETWORK_POLL_MS)
                _waitingForNetwork.value = false
                val again = synchronized(lock) {
                    networkWatch = null
                    lastRequest
                }
                again?.let { prefetch(it.items, it.policy.mode, it.language, it.policy.verify) }
            }
        }
    }

    private fun settle(item: NewsItem, content: ArticleContent) {
        synchronized(lock) { failures.remove(item.id) }
        val status = if (ArticleCompleteness.isComplete(content, item)) ArticleStatus.READABLE else ArticleStatus.PAYWALL
        setStatus(item.id, status)
        verdicts?.record(item.id, status)
        content.leadImage?.takeIf { it.startsWith("https://") }?.let { url -> _images.update { it + (item.id to url) } }
    }

    /** [itemId] could not be read because of [outcome] (null = an unexpected error). */
    private fun fail(itemId: String, outcome: ReadOutcome?) {
        synchronized(lock) {
            val attempts = (failures[itemId]?.attempts ?: 0) + 1
            failures[itemId] = Failure(outcome, clock(), attempts)
            if (failures.size > MAX_FAILURES) failures.remove(failures.minBy { it.value.at }.key)
        }
        val status = outcome?.let(ArticleStatus::of) ?: ArticleStatus.FAILED
        setStatus(itemId, status)
        // Walls and pages without an article stay what they are; a network failure says nothing.
        if (outcome != null && outcome != ReadOutcome.NETWORK) verdicts?.record(itemId, status)
    }

    /**
     * A page without an article stays one (the reader can still open it); a network failure is
     * tried again after a pause that doubles with every failure in a row.
     */
    private fun mayTryAgain(itemId: String, now: Long): Boolean = synchronized(lock) {
        val failure = failures[itemId] ?: return@synchronized true
        if (failure.outcome == ReadOutcome.NOT_ARTICLE) return@synchronized false
        val pause = minOf(RETRY_MAX_MS, RETRY_AFTER_MS shl (failure.attempts - 1).coerceIn(0, 10))
        now - failure.at >= pause
    }

    /** Waiting loads of items that are no longer asked for ([shown] are) go; running ones finish. */
    private fun dropWaitingExcept(shown: Set<String>) {
        val dropped = synchronized(lock) {
            jobs.entries.filter { it.key !in shown && it.key !in running }.map { it.key }.mapNotNull { jobs.remove(it) }
        }
        dropped.forEach { it.cancel() }
    }

    private fun setStatus(id: String, status: ArticleStatus) {
        _status.update { if (it[id] == status) it else it + (id to status) }
    }

    /** More than [maxQueued] waiting: the oldest waiting loads (scrolled away long ago) go. */
    private fun trimQueue() {
        val dropped = synchronized(lock) {
            // The first waiting ones take the free slots next (the gate is fair): they stay.
            val slots = if (light()) lightParallel.coerceIn(1, parallel) else parallel
            val free = (slots - running.size).coerceAtLeast(0)
            val waiting = jobs.entries.filter { it.key !in running && it.value.isActive }.drop(free)
            val excess = waiting.size - maxQueued
            if (excess <= 0) emptyList() else waiting.take(excess).map { (id, job) -> jobs.remove(id); job }
        }
        dropped.forEach { it.cancel() }
    }

    private class RepositorySource(private val repository: ArticleRepository) : Source {
        override suspend fun cached(item: NewsItem): ArticleContent? = repository.cachedOrNull(item)

        // Nobody waits for these: parsed on the background dispatcher (bounded on budget phones).
        override suspend fun load(item: NewsItem, language: String?): ArticleContent =
            repository.load(item, language = language, background = true)

        override fun blockReason(item: NewsItem): ReadOutcome? {
            val store = repository.readability ?: return null
            val host = item.publisherDomain.ifBlank {
                item.url.toHttpUrlOrNull()?.host?.takeUnless(ArticleRepository::isAggregatorHost).orEmpty()
            }
            return if (host.isBlank()) null else store.blockReason(host)
        }
    }

    companion object {
        private const val TAG = "ArticlePrefetcher"

        /** Known for good: not loaded again. [ArticleStatus.FAILED] is tried again later (see [mayTryAgain]). */
        private val SETTLED = setOf(ArticleStatus.READABLE, ArticleStatus.PAYWALL, ArticleStatus.BLOCKED)

        /** Loads at a time. */
        const val PARALLEL = 3

        /** Loads at a time with light effects: page parsing is heavy on budget phones' cores. */
        const val PARALLEL_LIGHT = 2

        /** The first pause before an item that failed for network reasons is loaded again. */
        const val RETRY_AFTER_MS = 60_000L

        /** The longest pause. */
        const val RETRY_MAX_MS = 30 * 60_000L

        /** How often an offline phone is asked whether it is back online. */
        const val NETWORK_POLL_MS = 5_000L

        private const val MAX_FAILURES = 500
    }
}

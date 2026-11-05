package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.PrefetchMode
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Loads the articles the feed shows ahead of time, so they open instantly (and offline), and
 * tells the feed what it learned about each one.
 *
 * Public API for the feed (UI):
 * - [prefetch] with the items now on screen and the settings: cached and known-unreadable items
 *   are answered at once, the rest is queued – at most [parallel] loads at a time, every item
 *   once (de-duplicated), the oldest waiting ones dropped when more than [maxQueued] wait
 *   (they scrolled away). [AppSettings.prefetch]: [PrefetchMode.OFF] never loads,
 *   [PrefetchMode.WIFI_ONLY] only on an unmetered connection ([NetworkMeter]),
 *   [PrefetchMode.ALWAYS] always.
 * - [status]: per item id an [ArticleStatus] (missing = [ArticleStatus.UNKNOWN]) – for badges
 *   and for hiding paywalled items; [statusOf] for one item.
 * - [images]: per item id the article's lead picture once the page was read – feed cards of
 *   items without a picture (Google News) can show it.
 * - [cancel] / [cancelAll] stop waiting and running loads (leaving the screen, pull-to-refresh).
 * - [report]: the reader tells what happened when an article was opened.
 */
class ArticlePrefetcher(
    private val source: Source,
    private val network: NetworkMeter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val parallel: Int = 2,
    private val maxQueued: Int = 40,
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

    constructor(repository: ArticleRepository, network: NetworkMeter) : this(RepositorySource(repository), network)

    private val lock = Any()
    private val gate = Semaphore(parallel)

    /** Queued and running loads, oldest first. */
    private val jobs = LinkedHashMap<String, Job>()
    private val running = HashSet<String>()

    private val _status = MutableStateFlow<Map<String, ArticleStatus>>(emptyMap())
    val status: StateFlow<Map<String, ArticleStatus>> = _status.asStateFlow()

    private val _images = MutableStateFlow<Map<String, String>>(emptyMap())
    val images: StateFlow<Map<String, String>> = _images.asStateFlow()

    fun statusOf(itemId: String): ArticleStatus = _status.value[itemId] ?: ArticleStatus.UNKNOWN

    /** Warms [items] as [settings] allow ([AppSettings.prefetch], the edition's language). */
    fun prefetch(items: List<NewsItem>, settings: AppSettings) = prefetch(items, settings.prefetch, settings.edition.language)

    /** Warms [items] in [mode]; [language] is the edition's language for `Accept-Language`. */
    fun prefetch(items: List<NewsItem>, mode: PrefetchMode, language: String? = null) {
        for (item in items.distinctBy { it.id }) {
            if (statusOf(item.id) in SETTLED) continue
            synchronized(lock) {
                if (jobs[item.id]?.isActive != true) {
                    val job = scope.launch(start = CoroutineStart.LAZY) { warm(item, mode, language) }
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

    /** Stops the loads of [itemIds] (waiting or running); their status goes back to unknown. */
    fun cancel(itemIds: Collection<String>) {
        val cancelled = synchronized(lock) { itemIds.mapNotNull { jobs.remove(it) } }
        cancelled.forEach { it.cancel() }
    }

    /** Stops every load. */
    fun cancelAll() {
        val cancelled = synchronized(lock) { jobs.values.toList().also { jobs.clear() } }
        cancelled.forEach { it.cancel() }
    }

    /** The reader read [item]: it is readable now. */
    fun report(item: NewsItem, content: ArticleContent) = settle(item, content)

    /** The reader could not read [item]. */
    fun report(item: NewsItem, error: Throwable) {
        setStatus(item.id, (error as? ArticleException)?.outcome?.let(ArticleStatus::of) ?: ArticleStatus.FAILED)
    }

    private suspend fun warm(item: NewsItem, mode: PrefetchMode, language: String?) {
        source.cached(item)?.let {
            settle(item, it)
            return
        }
        source.blockReason(item)?.let {
            setStatus(item.id, ArticleStatus.of(it))
            return
        }
        val allowed = when (mode) {
            PrefetchMode.OFF -> false
            PrefetchMode.WIFI_ONLY -> network.isUnmetered()
            PrefetchMode.ALWAYS -> true
        }
        if (!allowed) return
        gate.withPermit {
            synchronized(lock) { running += item.id }
            setStatus(item.id, ArticleStatus.LOADING)
            try {
                settle(item, source.load(item, language))
            } catch (e: CancellationException) {
                _status.update { if (it[item.id] == ArticleStatus.LOADING) it - item.id else it }
                throw e
            } catch (e: ArticleException) {
                setStatus(item.id, ArticleStatus.of(e.outcome))
            } catch (e: Exception) {
                setStatus(item.id, ArticleStatus.FAILED)
            }
        }
    }

    private fun settle(item: NewsItem, content: ArticleContent) {
        setStatus(item.id, ArticleStatus.READABLE)
        content.leadImage?.takeIf { it.startsWith("https://") }?.let { url -> _images.update { it + (item.id to url) } }
    }

    private fun setStatus(id: String, status: ArticleStatus) {
        _status.update { if (it[id] == status) it else it + (id to status) }
    }

    /** More than [maxQueued] waiting: the oldest waiting loads (scrolled away long ago) go. */
    private fun trimQueue() {
        val dropped = synchronized(lock) {
            // The first waiting ones take the free slots next (the gate is fair): they stay.
            val free = (parallel - running.size).coerceAtLeast(0)
            val waiting = jobs.entries.filter { it.key !in running && it.value.isActive }.drop(free)
            val excess = waiting.size - maxQueued
            if (excess <= 0) emptyList() else waiting.take(excess).map { (id, job) -> jobs.remove(id); job }
        }
        dropped.forEach { it.cancel() }
    }

    private class RepositorySource(private val repository: ArticleRepository) : Source {
        override suspend fun cached(item: NewsItem): ArticleContent? = repository.cachedOrNull(item)

        override suspend fun load(item: NewsItem, language: String?): ArticleContent = repository.load(item, language = language)

        override fun blockReason(item: NewsItem): ReadOutcome? {
            val store = repository.readability ?: return null
            val host = item.publisherDomain.ifBlank {
                item.url.toHttpUrlOrNull()?.host?.takeUnless(ArticleRepository::isAggregatorHost).orEmpty()
            }
            return if (host.isBlank()) null else store.blockReason(host)
        }
    }

    private companion object {
        /** Known for good: not loaded again. [ArticleStatus.FAILED] is tried again later. */
        val SETTLED = setOf(ArticleStatus.READABLE, ArticleStatus.PAYWALL, ArticleStatus.BLOCKED)
    }
}

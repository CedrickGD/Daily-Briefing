package com.cedrickgd.morgenbriefing.data

import android.util.Log
import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.local.NewsCache
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SearchPage
import com.cedrickgd.morgenbriefing.data.model.SourceKind
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.GoogleNews
import com.cedrickgd.morgenbriefing.data.news.PagingQuery
import com.cedrickgd.morgenbriefing.data.news.RssDates
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.data.remote.FeedError
import com.cedrickgd.morgenbriefing.data.remote.FeedException
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import com.cedrickgd.morgenbriefing.util.parseInstantOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

/** What a refresh of several topics ended in. */
data class RefreshOutcome(
    /** Topics for which at least one source answered. */
    val refreshed: Set<String>,
    /** Topics for which every source failed (offline, or all feeds down). */
    val failed: Set<String>,
) {
    val allFailed: Boolean get() = refreshed.isEmpty() && failed.isNotEmpty()
}

/**
 * The news of the app: every topic's articles, merged from its sources ([TopicSources]:
 * publisher feeds and Google News), kept in memory ([state]) and on disk ([NewsCache]).
 *
 * - [refresh] fetches the sources of the wanted topics in parallel (at most [PARALLEL_FETCHES]
 *   at a time) and publishes each topic as soon as its sources are in. A source that fails keeps
 *   the articles it delivered last time, so a newsroom that is down for a moment never empties a
 *   topic. The same story from several sources shows once ([dedupe]).
 * - [loadMore] makes a topic endless: it asks Google News for the next older days (a date window) and appends
 *   (up to [MAX_PER_TOPIC] articles per topic).
 * - [search] answers a query from Google News and the cached articles, page by page into the
 *   past; results are not cached.
 *
 * @param blockedDomains sites the reader cannot show, most certain first ([KnownWalls] plus
 *   what the phone learned); with [AppSettings.readableOnly] their articles never show.
 * @param resolvedUrl the publisher URL a Google News link resolved to before, if known (no
 *   I/O); lets [dedupe] recognize a Google News item and the publisher's own item as one.
 * @param warmUp runs once with [warm] (loads what [resolvedUrl] knows from disk).
 */
class NewsRepository(
    private val client: RssClient,
    private val cache: NewsCache,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(BERLIN) },
    private val blockedDomains: () -> Collection<String> = { KnownWalls.DOMAINS },
    private val resolvedUrl: (String) -> String? = { null },
    private val warmUp: suspend () -> Unit = {},
) {
    data class State(
        val topics: Map<String, TopicNews> = emptyMap(),
        /** Topic ids whose refresh is running. */
        val loading: Set<String> = emptySet(),
        /** The cache was read once (so "nothing yet" means nothing, not "not looked"). */
        val warmed: Boolean = false,
        /** Topic ids for which [loadMore] is running. */
        val loadingMore: Set<String> = emptySet(),
        /** Topic ids whose endless list reached its end (nothing older, or [MAX_PER_TOPIC]). */
        val endReached: Set<String> = emptySet(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val fetchLimit = Semaphore(PARALLEL_FETCHES)
    private val warmLock = Mutex()

    /** Where [loadMore] continues per topic; reset when the topic's query or edition changes. */
    private val cursors = HashMap<String, PageCursor>()
    private val pageLocks = HashMap<String, Mutex>()

    /** Loads the cached topics once; later calls are no-ops. */
    suspend fun warm() {
        if (_state.value.warmed) return
        warmLock.withLock {
            if (_state.value.warmed) return
            try {
                warmUp()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Warm-up failed", e)
            }
            val cached = try {
                cache.loadAll()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "News cache unreadable", e)
                emptyMap()
            }
            _state.update { s -> s.copy(topics = cached + s.topics, warmed = true) }
        }
    }

    /** The newest fetch time over [topicIds], or null when none of them was ever fetched. */
    fun fetchedAt(topicIds: Collection<String>): Instant? =
        topicIds.mapNotNull { _state.value.topics[it]?.fetchedAt?.let(::parseInstantOrNull) }.maxOrNull()

    /**
     * True when [topicId] was never fetched, every source failed the last time (so the next
     * chance to fetch should be taken), or its last fetch is older than [maxAge].
     */
    fun isStale(topicId: String, maxAge: Duration): Boolean {
        val news = _state.value.topics[topicId] ?: return true
        if (news.allFailed) return true
        val at = parseInstantOrNull(news.fetchedAt) ?: return true
        return Duration.between(at, clock().toInstant()) > maxAge
    }

    /**
     * Fetches every enabled source of [topicIds] (per [settings]) and merges the results into
     * [state] and the cache. Topics that are already being refreshed are skipped.
     */
    suspend fun refresh(topicIds: Collection<String>, settings: AppSettings): RefreshOutcome {
        warm()
        val wanted = topicIds.distinct().filter { it !in _state.value.loading }
        if (wanted.isEmpty()) return RefreshOutcome(emptySet(), emptySet())
        _state.update { it.copy(loading = it.loading + wanted) }
        val refreshed = HashSet<String>()
        val failed = HashSet<String>()
        try {
            coroutineScope {
                wanted.map { topicId ->
                    async {
                        val result = refreshTopic(topicId, settings)
                        synchronized(refreshed) { if (result) refreshed += topicId else failed += topicId }
                    }
                }.awaitAll()
            }
        } finally {
            _state.update { it.copy(loading = it.loading - wanted.toSet()) }
        }
        return RefreshOutcome(refreshed, failed)
    }

    /** Whether [loadMore] can bring older articles for [topicId] (a Google News backed topic that has not reached its end). */
    fun canLoadMore(topicId: String, settings: AppSettings): Boolean =
        topicId !in _state.value.endReached && TopicSources.pagingQuery(topicId, settings) != null

    /**
     * Appends the next older articles of [topicId]: Google News is asked for the days before
     * the oldest Google News article the topic holds (3 days at first, wider when little comes;
     * empty windows are skipped, up to [MAX_EMPTY_WINDOWS] per call, at most
     * [MAX_HISTORY_DAYS] back). Returns whether anything
     * new came; false at the end of the list, for topics without a search (own feeds only, top
     * stories) and when Google News is not reachable (try again later).
     */
    suspend fun loadMore(topicId: String, settings: AppSettings): Boolean {
        warm()
        val paging = TopicSources.pagingQuery(topicId, settings) ?: return false
        val lock = synchronized(pageLocks) { pageLocks.getOrPut(topicId) { Mutex() } }
        return lock.withLock {
            _state.update { it.copy(loadingMore = it.loadingMore + topicId) }
            try {
                loadMoreLocked(topicId, settings, paging)
            } finally {
                _state.update { it.copy(loadingMore = it.loadingMore - topicId) }
            }
        }
    }

    private suspend fun loadMoreLocked(topicId: String, settings: AppSettings, paging: PagingQuery): Boolean {
        val today = clock().toLocalDate()
        val previous = _state.value.topics[topicId]
        if ((previous?.items?.size ?: 0) >= MAX_PER_TOPIC) {
            markEnd(topicId, true)
            return false
        }
        var cursor = synchronized(cursors) { cursors[topicId] }?.takeIf { it.paging == paging }
            ?: PageCursor(paging, before = initialCursor(previous, paging, today), days = FIRST_WINDOW_DAYS)
        val blocked = blockedDomains().toSet()
        val exclusions = TopicSources.exclusions(settings, paging.edition, blocked)
        repeat(MAX_EMPTY_WINDOWS) {
            if (cursor.before.isBefore(today.minusDays(MAX_HISTORY_DAYS))) {
                remember(topicId, cursor)
                markEnd(topicId, true)
                return false
            }
            val window = GoogleNews.DateWindow(after = cursor.before.minusDays(cursor.days), before = cursor.before)
            val source = FeedSource(
                id = paging.sourceId + PAGE_SUFFIX,
                name = GoogleNews.SOURCE_NAME,
                url = GoogleNews.searchUrl(GoogleNews.buildQuery(paging.query, exclusions, window), paging.edition),
                topicId = topicId,
                kind = SourceKind.GOOGLE_NEWS,
                edition = paging.edition.key,
            )
            val fetched = try {
                fetchLimit.withPermit { client.fetch(source) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.i(TAG, "More for $topicId failed: ${e.message}")
                remember(topicId, cursor)
                return false
            }
            val page = if (settings.readableOnly) readable(fetched, blocked) else fetched
            val current = _state.value.topics[topicId] ?: previous
            val (merged, added) = appendPage(topicId, current, page, clock().toInstant(), resolvedUrl)
            // A thin window widens the next window; a full one keeps it.
            val next = PageCursor(paging, before = window.after, days = if (added < THIN_PAGE) (cursor.days * 2).coerceAtMost(MAX_WINDOW_DAYS) else cursor.days)
            if (added > 0) {
                publish(merged)
                remember(topicId, next)
                markEnd(topicId, merged.items.size >= MAX_PER_TOPIC)
                return true
            }
            cursor = next
        }
        remember(topicId, cursor)
        return false
    }

    /**
     * One page of results for [query]: page 0 is Google News' best current matches plus the
     * cached articles of every topic that contain all of the query's words; page n ≥ 1 is the
     * n-th week back. Newest first, the same story once. Nothing is cached.
     */
    suspend fun search(query: String, settings: AppSettings, page: Int = 0): SearchPage {
        warm()
        val text = query.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return SearchPage(text, page, emptyList(), hasMore = false)
        val edition = settings.edition
        val today = clock().toLocalDate()
        val window = if (page <= 0) null else GoogleNews.DateWindow(after = today.minusDays(7L * page), before = today.minusDays(7L * (page - 1)).plusDays(1))
        val blocked = blockedDomains().toSet()
        val source = FeedSource(
            id = "gn:${Topics.SEARCH_ID}@${edition.key}",
            name = GoogleNews.SOURCE_NAME,
            url = GoogleNews.searchUrl(GoogleNews.buildQuery(text, TopicSources.exclusions(settings, edition, blocked), window), edition),
            topicId = Topics.SEARCH_ID,
            kind = SourceKind.GOOGLE_NEWS,
            edition = edition.key,
        )
        var failed = false
        val found = try {
            fetchLimit.withPermit { client.fetch(source) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.i(TAG, "Search failed: ${e.message}")
            failed = true
            emptyList()
        }
        val local = if (page <= 0) cachedMatches(text) else emptyList()
        val all = local + if (settings.readableOnly) readable(found, blocked) else found
        val items = dedupe(all, resolvedUrl).sortedWith(NEWEST_FIRST)
        return SearchPage(
            query = text,
            page = page,
            items = items,
            hasMore = !failed && found.isNotEmpty() && page + 1 < MAX_SEARCH_PAGES,
            failed = failed,
        )
    }

    /** Forgets a topic (a custom topic was deleted, a place unfollowed). */
    suspend fun forget(topicId: String) {
        _state.update { it.copy(topics = it.topics - topicId, endReached = it.endReached - topicId) }
        synchronized(cursors) { cursors.remove(topicId) }
        try {
            cache.delete(topicId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete cached news of $topicId", e)
        }
    }

    /** Refreshes one topic; returns whether at least one source answered. */
    private suspend fun refreshTopic(topicId: String, settings: AppSettings): Boolean = coroutineScope {
        val blocked = blockedDomains()
        val sources = TopicSources.resolve(topicId, settings, blocked)
        val previous = _state.value.topics[topicId]
        val results = sources.map { source ->
            async {
                fetchLimit.withPermit {
                    try {
                        FetchResult(source, client.fetch(source), null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: FeedException) {
                        Log.i(TAG, "${source.name}: ${e.reason}")
                        FetchResult(source, emptyList(), e.reason)
                    } catch (e: IOException) {
                        Log.w(TAG, "${source.name} failed", e)
                        FetchResult(source, emptyList(), FeedException.code(FeedError.UNREACHABLE))
                    } catch (e: Exception) {
                        Log.w(TAG, "${source.name} failed unexpectedly", e)
                        FetchResult(source, emptyList(), FeedException.code(FeedError.UNREADABLE))
                    }
                }
            }
        }.awaitAll()
        val merged = merge(
            topicId, previous, results, now = clock().toInstant(),
            blocked = if (settings.readableOnly) blocked.toSet() else null,
            resolved = resolvedUrl,
        )
        publish(merged)
        // A new query or edition (settings changed) starts the endless list afresh.
        val paging = TopicSources.pagingQuery(topicId, settings)
        val stale = synchronized(cursors) { cursors[topicId]?.takeIf { it.paging != paging }?.also { cursors.remove(topicId) } }
        if (stale != null) markEnd(topicId, false)
        results.any { it.error == null } || sources.isEmpty()
    }

    private suspend fun publish(news: TopicNews) {
        _state.update { it.copy(topics = it.topics + (news.topicId to news)) }
        try {
            cache.save(news)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache news of ${news.topicId}", e)
        }
    }

    private fun remember(topicId: String, cursor: PageCursor) {
        synchronized(cursors) { cursors[topicId] = cursor }
    }

    private fun markEnd(topicId: String, end: Boolean) {
        _state.update { it.copy(endReached = if (end) it.endReached + topicId else it.endReached - topicId) }
    }

    /** Cached articles of every topic that contain all words of [query] (headline or teaser). */
    private fun cachedMatches(query: String): List<NewsItem> {
        val words = searchWords(query)
        if (words.isEmpty()) return emptyList()
        return _state.value.topics.values.asSequence()
            .flatMap { it.items.asSequence() }
            .filter { item -> normalizeText(item.headline + " " + item.summary).let { text -> words.all { it in text } } }
            .take(MAX_CACHED_MATCHES)
            .toList()
    }

    internal class FetchResult(val source: FeedSource, val items: List<NewsItem>, val error: String?)

    /** Where the next [loadMore] of a topic starts: the window `[before - days, before]`. */
    private data class PageCursor(val paging: PagingQuery, val before: LocalDate, val days: Long)

    companion object {
        private const val TAG = "NewsRepository"
        private const val PARALLEL_FETCHES = 6

        /** Articles kept per topic (the newest); paging stops here. */
        const val MAX_PER_TOPIC = 1000

        /** Suffix of the source id of articles [loadMore] appended (`gn:<topic>@<edition>#page`). */
        const val PAGE_SUFFIX = "#page"

        /** Search pages reach this many weeks back. */
        const val MAX_SEARCH_PAGES = 26

        private const val FIRST_WINDOW_DAYS = 3L
        private const val MAX_WINDOW_DAYS = 28L
        private const val MAX_EMPTY_WINDOWS = 4
        private const val MAX_HISTORY_DAYS = 365L
        private const val THIN_PAGE = 10
        private const val MAX_CACHED_MATCHES = 200

        /** Headlines shorter than this are too generic to merge stories by ("Liveblog"). */
        private const val MIN_HEADLINE_KEY = 16

        /** Articles of a failed source are kept from the last fetch for this long. */
        private val KEEP_FAILED_FOR: Duration = Duration.ofDays(3)

        /**
         * Merges fetch results into the topic's articles: failed sources keep what they delivered
         * before ([previous], up to [KEEP_FAILED_FOR]), articles [loadMore] appended stay while
         * their Google News source is still part of the topic, articles of [blocked] sites go,
         * the same story shows once ([dedupe]), the newest article comes first and undated ones
         * follow the dated ones. Pure.
         */
        internal fun merge(
            topicId: String,
            previous: TopicNews?,
            results: List<FetchResult>,
            now: Instant,
            blocked: Set<String>? = null,
            resolved: (String) -> String? = { null },
        ): TopicNews {
            val items = ArrayList<NewsItem>()
            val statuses = ArrayList<SourceStatus>()
            results.forEach { result ->
                if (result.error == null) {
                    items += result.items
                    statuses += SourceStatus(result.source.id, result.source.name, ok = true, count = result.items.size)
                } else {
                    val kept = previous?.items.orEmpty().filter { item ->
                        item.sourceId == result.source.id && !isOlderThan(item.publishedAt, now, KEEP_FAILED_FOR)
                    }
                    items += kept
                    statuses += SourceStatus(result.source.id, result.source.name, ok = false, count = kept.size, error = result.error)
                }
            }
            val sourceIds = results.map { it.source.id }.toSet()
            items += previous?.items.orEmpty().filter { it.sourceId.endsWith(PAGE_SUFFIX) && it.sourceId.removeSuffix(PAGE_SUFFIX) in sourceIds }
            val shown = if (blocked == null) items else readable(items, blocked)
            // A round in which no source answered is not a fetch: the topic stays as old as it was,
            // so the next refresh does not skip it as "fresh".
            val reached = results.isEmpty() || results.any { it.error == null }
            return TopicNews(
                topicId = topicId,
                fetchedAt = if (reached || previous == null) RssDates.format(now) else previous.fetchedAt,
                items = dedupe(shown, resolved).sortedWith(NEWEST_FIRST).take(MAX_PER_TOPIC),
                sources = statuses,
            )
        }

        /**
         * Adds a page of older articles to [previous]; returns the topic and how many articles
         * are new to it. Pure.
         */
        internal fun appendPage(
            topicId: String,
            previous: TopicNews?,
            page: List<NewsItem>,
            now: Instant,
            resolved: (String) -> String? = { null },
        ): Pair<TopicNews, Int> {
            val before = previous?.items.orEmpty()
            val merged = dedupe(before + page, resolved).sortedWith(NEWEST_FIRST).take(MAX_PER_TOPIC)
            val known = before.mapTo(HashSet()) { it.id }
            val added = merged.count { it.id !in known }
            val news = previous?.copy(items = merged) ?: TopicNews(topicId, RssDates.format(now), merged)
            return news to added
        }

        /**
         * One article per story: the same id, the same (resolved) link, or the same headline once
         * case, accents and punctuation are folded ([normalizeText]). Of a duplicate pair the
         * publisher's own item wins over Google News', one with a picture or teaser over one
         * without; the survivor keeps the position of the first. Pure.
         */
        internal fun dedupe(items: List<NewsItem>, resolved: (String) -> String? = { null }): List<NewsItem> {
            val result = ArrayList<NewsItem>(items.size)
            val index = HashMap<String, Int>()
            for (item in items) {
                val keys = dedupeKeys(item, resolved)
                val hit = keys.firstNotNullOfOrNull { index[it] }
                if (hit == null) {
                    result += item
                    keys.forEach { index[it] = result.lastIndex }
                } else {
                    if (quality(item) > quality(result[hit])) result[hit] = item
                    keys.forEach { index.putIfAbsent(it, hit) }
                }
            }
            return result
        }

        private fun dedupeKeys(item: NewsItem, resolved: (String) -> String?): List<String> {
            val link = resolved(item.url) ?: item.url
            val headline = normalizeText(item.headline)
            return listOfNotNull(
                "id:" + item.id,
                "url:" + RssClient.normalizeLinkForId(link),
                headline.takeIf { it.length >= MIN_HEADLINE_KEY }?.let { "h:$it" },
            )
        }

        private fun quality(item: NewsItem): Int =
            (if (item.aggregator == null) 4 else 0) + (if (item.image != null) 2 else 0) + (if (item.summary.isNotBlank()) 1 else 0)

        /** Lower case, accents and punctuation removed, single spaces: "„Börse: DAX-Rekord!“" → "borse dax rekord". */
        internal fun normalizeText(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .lowercase(Locale.ROOT)
                .replace("ß", "ss")
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()

        /** The words a cached article must contain: operators and `OR` are left out. */
        internal fun searchWords(query: String): List<String> =
            query.split(Regex("\\s+"))
                .filterNot { it.equals("OR", ignoreCase = false) || it.startsWith("-") || it.contains(':') }
                .map { normalizeText(it) }
                .flatMap { it.split(' ') }
                .filter { it.length >= 2 }
                .distinct()

        /**
         * Without the articles the reader cannot show: those of [blocked] sites (by publisher
         * domain, else the link's host) and those whose headline marks them as subscriber-only
         * ("(S+) …", see [GoogleNews.isPaywallHeadline]). Pure.
         */
        internal fun readable(items: List<NewsItem>, blocked: Set<String>): List<NewsItem> =
            items.filterNot { item ->
                val domain = item.publisherDomain.ifEmpty { GoogleNews.domainOf(item.url) }
                (domain.isNotEmpty() && KnownWalls.matches(domain, blocked)) || GoogleNews.isPaywallHeadline(item.headline)
            }

        /**
         * Where paging starts: the day after the oldest Google News article of the topic (its
         * first page covers the newest days), else tomorrow.
         */
        private fun initialCursor(previous: TopicNews?, paging: PagingQuery, today: LocalDate): LocalDate {
            val oldest = previous?.items.orEmpty()
                .filter { it.sourceId == paging.sourceId || it.sourceId == paging.sourceId + PAGE_SUFFIX }
                .mapNotNull { parseInstantOrNull(it.publishedAt) }
                .minOrNull()
            val tomorrow = today.plusDays(1)
            val start = oldest?.atZone(BERLIN)?.toLocalDate()?.plusDays(1) ?: return tomorrow
            return if (start.isAfter(tomorrow)) tomorrow else start
        }

        private fun isOlderThan(iso: String?, now: Instant, maxAge: Duration): Boolean {
            val at = parseInstantOrNull(iso) ?: return false
            return Duration.between(at, now) > maxAge
        }

        private val NEWEST_FIRST: Comparator<NewsItem> = Comparator { a, b ->
            val ta = parseInstantOrNull(a.publishedAt)
            val tb = parseInstantOrNull(b.publishedAt)
            when {
                ta == null && tb == null -> 0
                ta == null -> 1
                tb == null -> -1
                else -> tb.compareTo(ta)
            }
        }
    }
}

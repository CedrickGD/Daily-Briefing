package com.cedrickgd.morgenbriefing.data

import android.util.Log
import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.local.NewsCache
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SearchPage
import com.cedrickgd.morgenbriefing.data.model.SourceKind
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.GoogleNews
import com.cedrickgd.morgenbriefing.data.news.PagingQuery
import com.cedrickgd.morgenbriefing.data.news.RssDates
import com.cedrickgd.morgenbriefing.data.news.SearchQuery
import com.cedrickgd.morgenbriefing.data.news.SearchRanking
import com.cedrickgd.morgenbriefing.data.news.StoryTwins
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.data.remote.FeedError
import com.cedrickgd.morgenbriefing.data.remote.FeedException
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import com.cedrickgd.morgenbriefing.util.parseInstantOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** What a refresh of several topics ended in. */
data class RefreshOutcome(
    /** Topics for which at least one source answered. */
    val refreshed: Set<String>,
    /** Topics for which every source failed (offline, or all feeds down). */
    val failed: Set<String>,
) {
    val allFailed: Boolean get() = refreshed.isEmpty() && failed.isNotEmpty()
}

/** What asking for older articles of a topic ended in ([NewsRepository.loadOlder]). */
enum class MoreResult {
    /** New articles were appended. */
    GREW,

    /** The windows asked for held nothing new; the next call looks further back (wider windows). */
    EMPTY,

    /** Google News could not be reached (offline, an error status): try again later. */
    FAILED,

    /** Nothing older exists for the topic (its end, or a topic that cannot page). */
    END,
}

/**
 * The news of the app: every topic's articles, merged from its sources ([TopicSources]:
 * publisher feeds and Google News), kept in memory ([state]) and on disk ([NewsCache]).
 *
 * - [refresh] fetches the sources of the wanted topics in parallel (at most [PARALLEL_FETCHES]
 *   at a time) and publishes each topic as soon as its sources are in. A source that fails keeps
 *   the articles it delivered last time, so a newsroom that is down for a moment never empties a
 *   topic. The same story from several sources shows once ([dedupe]), and what a publisher's feed
 *   only drags along – rubric pages, articles of months ago – is left out ([current]).
 * - [loadMore] makes a topic endless: it asks Google News for the next older days (a date window) and appends
 *   (up to [MAX_PER_TOPIC] articles per topic).
 * - [search] answers a query from Google News and the cached articles, page by page into the
 *   past; results are not cached.
 *
 * What the user waits for ([search], [loadMore]) has its own [INTERACTIVE_FETCHES] connections,
 * so it never queues behind the dozens of feeds a refresh asks for. Merging, sorting and
 * encoding run on [compute], never on the caller's (main) thread.
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
    private val compute: CoroutineDispatcher = Dispatchers.Default,
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
    private val interactiveLimit = Semaphore(INTERACTIVE_FETCHES)
    private val warmLock = Mutex()

    /** Where [loadMore] continues per topic; reset when the topic's query or edition changes. */
    private val cursors = HashMap<String, PageCursor>()

    /**
     * One per topic: [loadMore] holds it for a whole load, [refresh] while it merges and
     * publishes, so neither works from articles the other is about to replace.
     */
    private val topicLocks = HashMap<String, Mutex>()

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
     * chance to fetch should be taken), it was fetched from other sources than [settings] give
     * it now (another edition, a source switched on or off) or its last fetch is older than
     * [maxAge].
     */
    fun isStale(topicId: String, maxAge: Duration, settings: AppSettings): Boolean {
        val news = _state.value.topics[topicId] ?: return true
        if (news.allFailed) return true
        val wanted = sourcesOf(topicId, settings, blockedDomains()).mapTo(HashSet()) { it.id }
        if (news.sources.mapTo(HashSet()) { it.sourceId } != wanted) return true
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
     * new came; false at the end of the list, for topics without a search (own feeds only)
     * and when Google News is not reachable (try again later). [loadOlder] tells these apart.
     */
    suspend fun loadMore(topicId: String, settings: AppSettings): Boolean = loadOlder(topicId, settings) == MoreResult.GREW

    /**
     * [loadMore] with the reason when nothing came: [MoreResult.EMPTY] (quiet days – the next
     * call continues further back with wider windows, so the caller may simply ask again),
     * [MoreResult.FAILED] (Google News not reachable) or [MoreResult.END].
     */
    suspend fun loadOlder(topicId: String, settings: AppSettings): MoreResult {
        warm()
        val paging = TopicSources.pagingQuery(topicId, settings) ?: return MoreResult.END
        return topicLock(topicId).withLock {
            _state.update { it.copy(loadingMore = it.loadingMore + topicId) }
            try {
                loadMoreLocked(topicId, settings, paging)
            } finally {
                _state.update { it.copy(loadingMore = it.loadingMore - topicId) }
            }
        }
    }

    private suspend fun loadMoreLocked(topicId: String, settings: AppSettings, paging: PagingQuery): MoreResult {
        val today = clock().toLocalDate()
        val previous = _state.value.topics[topicId]
        if ((previous?.items?.size ?: 0) >= MAX_PER_TOPIC) {
            markEnd(topicId, true)
            return MoreResult.END
        }
        var cursor = synchronized(cursors) { cursors[topicId] }?.takeIf { it.paging == paging }
            ?: PageCursor(paging, before = initialCursor(previous, paging, today), days = FIRST_WINDOW_DAYS)
        val blocked = blockedDomains().toSet()
        val exclusions = TopicSources.searchExclusions(topicId, settings, paging.edition, blocked)
        repeat(MAX_EMPTY_WINDOWS) {
            if (cursor.before.isBefore(today.minusDays(MAX_HISTORY_DAYS))) {
                remember(topicId, cursor)
                markEnd(topicId, true)
                return MoreResult.END
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
                interactiveLimit.withPermit { client.fetch(source) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.i(TAG, "More for $topicId failed: ${e.message}")
                remember(topicId, cursor)
                return MoreResult.FAILED
            }
            // Refreshes wait for this lock before they publish, so the topic is still as read here.
            val current = _state.value.topics[topicId] ?: previous
            val (merged, added) = withContext(compute) {
                val page = inWindow(if (settings.readableOnly) readable(fetched, blocked) else fetched, window)
                appendPage(topicId, current, page, clock().toInstant(), resolvedUrl)
            }
            // A thin window widens the next window; a full one keeps it.
            val next = PageCursor(paging, before = window.after, days = if (added < THIN_PAGE) (cursor.days * 2).coerceAtMost(MAX_WINDOW_DAYS) else cursor.days)
            if (added > 0) {
                publish(merged)
                remember(topicId, next)
                markEnd(topicId, merged.items.size >= MAX_PER_TOPIC)
                return MoreResult.GREW
            }
            cursor = next
        }
        remember(topicId, cursor)
        return MoreResult.EMPTY
    }

    /**
     * One page of results for [query]: page 0 is Google News' best current matches plus the
     * cached articles of every topic that contain the whole query ([SearchQuery.matchesAll]);
     * page n ≥ 1 is the n-th week back. Each page reads the main edition and, with
     * [allLanguages], the English ones too ([GoogleNews.searchEditions]), in parallel. Most
     * relevant first ([SearchRanking]), the same story once. Nothing is cached.
     *
     * The query goes to Google News as [SearchQuery.googleQuery] (tickers quoted, the user's
     * operators kept). A week without results does not end the search ([SearchPage.hasMore]
     * stays true up to [MAX_SEARCH_PAGES]): a story of a small town can be quiet for weeks; the
     * caller decides how many quiet weeks in a row it skips. Only an empty page 0 (no window:
     * nothing at all) ends it. [SearchPage.failed] when no edition answered.
     */
    suspend fun search(query: String, settings: AppSettings, page: Int = 0, allLanguages: Boolean = true): SearchPage {
        warm()
        val text = query.trim().replace(WHITESPACE, " ")
        if (text.isEmpty()) return SearchPage(text, page, emptyList(), hasMore = false)
        val parsed = SearchQuery.parse(text)
        val today = clock().toLocalDate()
        val window = if (page <= 0) null else GoogleNews.DateWindow(after = today.minusDays(7L * page), before = today.minusDays(7L * (page - 1)).plusDays(1))
        val blocked = blockedDomains().toSet()
        val editions = GoogleNews.searchEditions(settings.edition, allLanguages)
        val answers = coroutineScope {
            editions.map { edition ->
                async {
                    val source = searchSource(parsed, edition, window)
                    try {
                        interactiveLimit.withPermit { client.fetch(source) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        Log.i(TAG, "Search in ${edition.key} failed: ${e.message}")
                        null
                    }
                }
            }.awaitAll()
        }
        val failed = answers.all { it == null }
        val items = withContext(compute) {
            val local = if (page <= 0) cachedMatches(parsed) else emptyList()
            val found = answers.mapIndexed { index, answer ->
                val delivered = answer.orEmpty()
                SearchRanking.Found(if (settings.readableOnly) readable(delivered, blocked) else delivered, main = index == 0)
            }
            searchResults(parsed, local, found, clock().toInstant(), resolvedUrl)
        }
        val any = answers.any { !it.isNullOrEmpty() }
        val more = page + 1 < MAX_SEARCH_PAGES && !failed && (page > 0 || any)
        return SearchPage(query = text, page = page, items = items, hasMore = more, failed = failed)
    }

    /**
     * The search in [edition]: without `-site:` exclusions – any `site:` operator makes Google
     * News answer loosely (100 results whatever the query: "Bundestagswahl" gives 18 results,
     * 15 naming it, with one `-site:` 100 results, 4 naming it; checked 05.11.2026). Walled
     * sites are left out by domain afterwards ([readable]).
     */
    private fun searchSource(query: SearchQuery, edition: NewsEdition, window: GoogleNews.DateWindow?) = FeedSource(
        id = TopicSources.googleNewsId(Topics.SEARCH_ID, edition),
        name = GoogleNews.SOURCE_NAME,
        url = GoogleNews.searchUrl(GoogleNews.buildQuery(query.googleQuery, window = window), edition),
        topicId = Topics.SEARCH_ID,
        kind = SourceKind.GOOGLE_NEWS,
        edition = edition.key,
    )

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
        val sources = sourcesOf(topicId, settings, blocked)
        val followed = followedSearch(topicId, settings)
        val results = sources.map { source ->
            async {
                fetchLimit.withPermit {
                    try {
                        val items = client.fetch(source)
                        FetchResult(source, if (followed != null && isExtraEdition(source, settings)) SearchRanking.relevantExtra(followed, items, clock().toInstant()) else items, null)
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
        // Merged with the topic as it is now, not as it was before the fetches: a [loadMore]
        // that finished meanwhile appended articles that must stay.
        topicLock(topicId).withLock {
            val current = _state.value.topics[topicId]
            val merged = withContext(compute) {
                merge(
                    topicId, current, results, now = clock().toInstant(),
                    blocked = if (settings.readableOnly) blocked.toSet() else null,
                    resolved = resolvedUrl,
                )
            }
            publish(merged)
            // The endless list starts afresh for a new query or edition (settings changed), and
            // when the fresh first page left no paged articles to continue from (they no longer
            // joined it, see [merge]): the old cursor would skip the days in between.
            val paging = TopicSources.pagingQuery(topicId, settings)
            val answered = paging != null && results.any { it.source.id == paging.sourceId && it.error == null }
            val pageId = paging?.let { it.sourceId + PAGE_SUFFIX }
            val restart = answered && merged.items.none { it.sourceId == pageId }
            val stale = synchronized(cursors) {
                val cursor = cursors[topicId]
                (cursor != null && (cursor.paging != paging || restart)).also { if (it) cursors.remove(topicId) }
            }
            if (stale || restart) markEnd(topicId, false)
        }
        results.any { it.error == null } || sources.isEmpty()
    }

    private suspend fun publish(news: TopicNews) {
        _state.update { it.copy(topics = it.topics + (news.topicId to news)) }
        try {
            withContext(compute) { cache.save(news) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache news of ${news.topicId}", e)
        }
    }

    private fun topicLock(topicId: String): Mutex = synchronized(topicLocks) { topicLocks.getOrPut(topicId) { Mutex() } }

    private fun remember(topicId: String, cursor: PageCursor) {
        synchronized(cursors) { cursors[topicId] = cursor }
    }

    private fun markEnd(topicId: String, end: Boolean) {
        _state.update { it.copy(endReached = if (end) it.endReached + topicId else it.endReached - topicId) }
    }

    private fun cachedMatches(query: SearchQuery): List<NewsItem> = matching(_state.value.topics.values, query)

    /** What a refresh of [topicId] reads: its sources plus, for a followed search, the English editions ([TopicSources.extraEditions]). */
    private fun sourcesOf(topicId: String, settings: AppSettings, blocked: Collection<String>): List<FeedSource> =
        TopicSources.resolve(topicId, settings, blocked) + TopicSources.extraEditions(topicId, settings, blocked)

    /** The query of [topicId] when it is a followed search ("Diesem Thema folgen"), else null. */
    private fun followedSearch(topicId: String, settings: AppSettings): SearchQuery? =
        if (!Topics.isCustomId(topicId)) null else settings.customTopics.firstOrNull { it.id == topicId }?.query?.takeIf { it.isNotBlank() }?.let(SearchQuery::parse)

    /** A Google News source of another edition than the main one: the English breadth of a followed search. */
    private fun isExtraEdition(source: FeedSource, settings: AppSettings): Boolean =
        source.isGoogleNews && source.edition != null && source.edition != settings.edition.key

    internal class FetchResult(val source: FeedSource, val items: List<NewsItem>, val error: String?)

    /** Where the next [loadMore] of a topic starts: the window `[before - days, before]`. */
    private data class PageCursor(val paging: PagingQuery, val before: LocalDate, val days: Long)

    companion object {
        private const val TAG = "NewsRepository"
        private const val PARALLEL_FETCHES = 6

        /** Connections of their own for [search] and [loadMore], which the user waits for (a search reads up to three editions at once). */
        private const val INTERACTIVE_FETCHES = 3

        /** Articles kept per topic (the newest); paging stops here. */
        const val MAX_PER_TOPIC = 1000

        /** Suffix of the source id of articles [loadMore] appended (`gn:<topic>@<edition>#page`). */
        const val PAGE_SUFFIX = NewsItem.PAGE_SUFFIX

        /** Search pages reach this many weeks back. */
        const val MAX_SEARCH_PAGES = 26

        private const val FIRST_WINDOW_DAYS = 3L
        private const val MAX_WINDOW_DAYS = 28L
        private const val MAX_EMPTY_WINDOWS = 4
        private const val MAX_HISTORY_DAYS = 365L
        private const val THIN_PAGE = 10
        internal const val MAX_CACHED_MATCHES = 200

        /** Headlines shorter than this are too generic to merge stories by ("Liveblog"). */
        private const val MIN_HEADLINE_KEY = 16

        /**
         * What a publisher's feed still lists after this long is no news. Measured on the German
         * top feeds (15.11.2026): everything older was a rubric page or a house notice (ZDFheute:
         * 25 of 205 items, 70 to 2579 days old – "Nachrichten aus Sachsen", "Fragen und
         * Antworten", the lottery numbers), a rain radar and a programme page (tagesschau, 162
         * and 571 days) or an evergreen feature (Deutsche Welle, 65 and 95 days); the articles
         * of slow desks ended at 59 days.
         */
        internal val STALE_AFTER: Duration = Duration.ofDays(60)

        /** First path segments of rubric and topic pages (`zdfheute.de/thema/sachsen-news-100.html`). */
        private val INDEX_SEGMENTS = setOf("thema", "themen", "topic", "topics", "tag", "tags")

        /** A page of older articles may reach this far before its window (time zones, a story updated since). */
        private const val WINDOW_SLACK_DAYS = 2L

        /** Articles of a failed source are kept from the last fetch for this long. */
        private val KEEP_FAILED_FOR: Duration = Duration.ofDays(3)

        /** A first-page article at least this much before the next newer one can be a stray old hit ([bulkOldest]). */
        private val MIN_STRAY_GAP: Duration = Duration.ofDays(2)

        /** How many usual gaps between two first-page articles a stray old hit is away ([bulkOldest]). */
        private const val STRAY_FACTOR = 8L

        /** Paged articles still join a fresh first page when they end at most this long before it. */
        private val JOIN_SLACK: Duration = Duration.ofDays(1)

        private val WHITESPACE = Regex("\\s+")

        /**
         * Merges fetch results into the topic's articles: failed sources keep what they delivered
         * before ([previous], up to [KEEP_FAILED_FOR]), articles [loadMore] appended stay while
         * they still join the topic's Google News first page ([pagedToKeep]), the leftovers of
         * publishers' feeds go ([current]), articles of [blocked] sites go (except from feeds the user added: the user chose them), the same
         * story shows once ([dedupe]), the newest article comes first and undated ones follow the
         * dated ones. A source's status counts the articles of it that can show. Pure.
         */
        internal fun merge(
            topicId: String,
            previous: TopicNews?,
            results: List<FetchResult>,
            now: Instant,
            blocked: Set<String>? = null,
            resolved: (String) -> String? = { null },
        ): TopicNews {
            val before = previous?.items.orEmpty()
            val perSource = results.map { result ->
                val delivered = if (result.error == null) {
                    current(result.source, result.items, now)
                } else {
                    before.filter { item -> item.sourceId == result.source.id && !isOlderThan(item.publishedAt, now, KEEP_FAILED_FOR) }
                }
                result to if (blocked == null) delivered else readable(delivered, blocked)
            }
            val statuses = perSource.map { (result, shown) ->
                SourceStatus(result.source.id, result.source.name, ok = result.error == null, count = shown.size, error = result.error.orEmpty())
            }
            val items = perSource.flatMap { it.second }.toMutableList()
            results.filter { it.source.isGoogleNews }.forEach { result ->
                val paged = pagedToKeep(result, before)
                items += if (blocked == null) paged else readable(paged, blocked)
            }
            // A round in which no source answered is not a fetch: the topic stays as old as it was,
            // so the next refresh does not skip it as "fresh".
            val reached = results.isEmpty() || results.any { it.error == null }
            return TopicNews(
                topicId = topicId,
                fetchedAt = if (reached || previous == null) RssDates.format(now) else previous.fetchedAt,
                items = newestFirst(dedupe(items, resolved)).take(MAX_PER_TOPIC),
                sources = statuses,
            )
        }

        /**
         * The paged articles of the Google News source of [result] that stay. When the source
         * failed, or its fresh first page has no dates to compare, they all stay.
         *
         * Otherwise they stay only while the articles held so far still reach the fresh first
         * page (their newest at most [JOIN_SLACK] before its bulk, [bulkOldest]): after a week
         * away the fresh page starts days after the old one ended, and a list that jumped over
         * those days could never fill them, since paging only goes further back – then nothing
         * stays and paging starts again from the fresh page. When they do reach it, the previous
         * first page's articles older than the fresh one (its bulk, not its stray old hits) stay
         * as paged articles: they are the days between the fresh page and the paged ones.
         */
        private fun pagedToKeep(result: FetchResult, before: List<NewsItem>): List<NewsItem> {
            val pageId = result.source.id + PAGE_SUFFIX
            val paged = before.filter { it.sourceId == pageId }
            if (paged.isEmpty() || result.error != null) return paged
            val freshStart = bulkOldest(result.items) ?: return paged
            val firstPage = before.filter { it.sourceId == result.source.id }
            val newestHeld = (firstPage + paged).mapNotNull { parseInstantOrNull(it.publishedAt) }.maxOrNull() ?: return paged
            if (newestHeld < freshStart - JOIN_SLACK) return emptyList()
            val oldStart = bulkOldest(firstPage)
            val bridge = firstPage
                .filter { item -> parseInstantOrNull(item.publishedAt)?.let { it < freshStart && (oldStart == null || it >= oldStart) } == true }
                .map { it.copy(sourceId = pageId) }
            return bridge + paged
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
            val merged = newestFirst(dedupe(before + page, resolved)).take(MAX_PER_TOPIC)
            val known = before.mapTo(HashSet()) { it.id }
            val added = merged.count { it.id !in known }
            val news = previous?.copy(items = merged) ?: TopicNews(topicId, RssDates.format(now), merged)
            return news to added
        }

        /**
         * What [source] delivered without the leftovers of a publisher's feed: rubric and topic
         * pages ([isIndexPage]) and articles older than [STALE_AFTER] – such a feed lists them
         * among today's news, and at the end of an endless list they read as a dead end
         * ("Nachrichten aus Sachsen", 800 days old). A feed with nothing newer keeps what it has
         * (a quiet desk is not stale) and undated articles stay. Google News keeps everything (a
         * small town's place page is months deep, and paging asks for old articles on purpose),
         * and so do the feeds the user added. Pure.
         */
        internal fun current(source: FeedSource, items: List<NewsItem>, now: Instant): List<NewsItem> {
            if (source.isGoogleNews || source.isCustom) return items
            val articles = items.filterNot { isIndexPage(it.url) }
            return articles.filterNot { isOlderThan(it.publishedAt, now, STALE_AFTER) }.ifEmpty { articles }
        }

        /** Whether [url] is a rubric or topic page of a site, not an article (by its first path segment, [INDEX_SEGMENTS]). */
        internal fun isIndexPage(url: String): Boolean =
            url.toHttpUrlOrNull()?.pathSegments?.firstOrNull()?.lowercase() in INDEX_SEGMENTS

        /**
         * [page] without what lies before [window] (by more than [WINDOW_SLACK_DAYS]): should a
         * search with a date window return a stray old hit, it would make the list look read
         * back to it ([initialCursor] continues from the oldest paged article). Undated articles
         * stay. Pure.
         */
        internal fun inWindow(page: List<NewsItem>, window: GoogleNews.DateWindow): List<NewsItem> {
            val earliest = window.after.minusDays(WINDOW_SLACK_DAYS).atStartOfDay(BERLIN).toInstant()
            return page.filter { item -> parseInstantOrNull(item.publishedAt)?.let { it >= earliest } ?: true }
        }

        /**
         * One article per story: the same id, the same (resolved) link, the same headline once
         * case, accents and punctuation are folded ([normalizeText]), or the same publisher with
         * nearly the same headline at nearly the same time ([StoryTwins]: a publisher's own feed
         * and Google News word a headline a little differently now and then). Of a duplicate pair
         * the publisher's own item wins over Google News', one with a picture or teaser over one
         * without – and takes the picture or teaser only its twin carries. The survivor keeps the
         * position of the first. Pure.
         */
        internal fun dedupe(items: List<NewsItem>, resolved: (String) -> String? = { null }): List<NewsItem> {
            val result = ArrayList<NewsItem>(items.size)
            val index = HashMap<String, Int>()
            val twins = StoryTwins.Finder()
            for (item in items) {
                val keys = dedupeKeys(item, resolved)
                val hit = keys.firstNotNullOfOrNull { index[it] } ?: twins.find(item)
                if (hit == null) {
                    result += item
                    keys.forEach { index[it] = result.lastIndex }
                    twins.add(item, result.lastIndex)
                } else {
                    result[hit] = StoryTwins.merged(result[hit], item)
                    keys.forEach { index.putIfAbsent(it, hit) }
                }
            }
            return result
        }

        /** The key stories with the same headline share; null for a headline too short to tell ([MIN_HEADLINE_KEY]). */
        internal fun headlineKey(item: NewsItem): String? =
            StoryTwins.headline(item.headline).text.takeIf { it.length >= MIN_HEADLINE_KEY }

        private fun dedupeKeys(item: NewsItem, resolved: (String) -> String?): List<String> {
            val link = resolved(item.url) ?: item.url
            val headline = normalizeText(item.headline)
            return listOfNotNull(
                "id:" + item.id,
                "url:" + RssClient.normalizeLinkForId(link),
                headline.takeIf { it.length >= MIN_HEADLINE_KEY }?.let { "h:$it" },
            )
        }

        /** Lower case, accents and punctuation removed, single spaces: "„Börse: DAX-Rekord!“" → "borse dax rekord". */
        internal fun normalizeText(text: String): String = SearchQuery.fold(text)

        /**
         * The articles of [topics] that meet the whole [query] (every word as a whole word in
         * headline or teaser, see [SearchQuery.matchesAll]): the newest [MAX_CACHED_MATCHES]
         * over all topics, newest first. Pure.
         */
        internal fun matching(topics: Collection<TopicNews>, query: SearchQuery): List<NewsItem> {
            if (!query.hasTerms) return emptyList()
            val matches = topics.asSequence()
                .flatMap { it.items.asSequence() }
                .filter { item -> query.matchesAll(item.headline, item.summary, item.publisherDomain.ifEmpty { GoogleNews.domainOf(item.url) }) }
                .toList()
            return newestFirst(matches).take(MAX_CACHED_MATCHES)
        }

        internal fun matching(topics: Collection<TopicNews>, query: String): List<NewsItem> = matching(topics, SearchQuery.parse(query))

        /**
         * One page of search results: [found] (per edition, Google's order) and [local] (cached
         * matches) ranked ([SearchRanking.rank]), then the same story once – the same link or
         * headline ([dedupe], the better copy at the better place; an article the phone holds
         * shows as its cached copy, which names its topic) or a near-identical headline
         * ([SearchRanking.dropNearDuplicates]). Pure.
         */
        internal fun searchResults(
            query: SearchQuery,
            local: List<NewsItem>,
            found: List<SearchRanking.Found>,
            now: Instant,
            resolved: (String) -> String? = { null },
        ): List<NewsItem> {
            val held = local.associateBy { it.id }
            val ranked = SearchRanking.rank(query, local, found, now).map { held[it.id] ?: it }
            return SearchRanking.dropNearDuplicates(dedupe(ranked, resolved))
        }

        /**
         * Without the articles the reader cannot show: those of [blocked] sites (by publisher
         * domain, else the link's host) and those whose headline marks them as subscriber-only
         * ("(S+) …", see [GoogleNews.isPaywallHeadline]). Articles of feeds the user added stay:
         * the user chose that site. Pure.
         */
        internal fun readable(items: List<NewsItem>, blocked: Set<String>): List<NewsItem> =
            items.filterNot { item ->
                if (item.fromUserFeed) return@filterNot false
                val domain = item.publisherDomain.ifEmpty { GoogleNews.domainOf(item.url) }
                (domain.isNotEmpty() && KnownWalls.matches(domain, blocked)) || GoogleNews.isPaywallHeadline(item.headline)
            }

        /**
         * The oldest date of the bulk of [items], a first page newest first: Google News' first
         * page of a search carries stray old hits (a Raumfahrt search: 88 from November, 9 from
         * April; Börse back to 2017), and paging from those would skip the months in between.
         * Going back from the newest, an article ends the bulk when it is older than the next newer
         * one by more than [MIN_STRAY_GAP], the span of the newer ones and [STRAY_FACTOR] times the
         * usual gap between two articles – so a quiet place whose first page has a story every
         * week keeps all of it. Ending too early only re-reads known days; too late would skip.
         * Null without dates. Pure.
         */
        internal fun bulkOldest(items: List<NewsItem>): Instant? {
            val dates = items.mapNotNull { parseInstantOrNull(it.publishedAt) }.sortedDescending()
            val newest = dates.firstOrNull() ?: return null
            val gaps = dates.zipWithNext { a, b -> Duration.between(b, a) }.sorted()
            val usual = gaps.getOrNull((gaps.size - 1) / 2)?.multipliedBy(STRAY_FACTOR) ?: Duration.ZERO
            var oldest = newest
            for (next in dates.drop(1)) {
                val gap = Duration.between(next, oldest)
                val span = Duration.between(oldest, newest)
                if (gap > maxOf(MIN_STRAY_GAP, span, usual)) break
                oldest = next
            }
            return oldest
        }

        /**
         * Where paging starts: the day after the oldest article paging appended before (its
         * windows are contiguous), else after the bulk of the topic's Google News first page
         * ([bulkOldest]: it covers the newest days), else tomorrow.
         */
        private fun initialCursor(previous: TopicNews?, paging: PagingQuery, today: LocalDate): LocalDate {
            val items = previous?.items.orEmpty()
            val oldest = items.filter { it.sourceId == paging.sourceId + PAGE_SUFFIX }
                .mapNotNull { parseInstantOrNull(it.publishedAt) }
                .minOrNull()
                ?: bulkOldest(items.filter { it.sourceId == paging.sourceId })
            val tomorrow = today.plusDays(1)
            val start = oldest?.atZone(BERLIN)?.toLocalDate()?.plusDays(1) ?: return tomorrow
            return if (start.isAfter(tomorrow)) tomorrow else start
        }

        private fun isOlderThan(iso: String?, now: Instant, maxAge: Duration): Boolean {
            val at = parseInstantOrNull(iso) ?: return false
            return Duration.between(at, now) > maxAge
        }

        /**
         * Newest first, undated articles after the dated ones, otherwise in the given order. Each
         * date is parsed once (not twice per comparison). Pure.
         */
        internal fun newestFirst(items: List<NewsItem>): List<NewsItem> =
            items.map { it to parseInstantOrNull(it.publishedAt) }
                .sortedWith { (_, ta), (_, tb) ->
                    when {
                        ta == null && tb == null -> 0
                        ta == null -> 1
                        tb == null -> -1
                        else -> tb.compareTo(ta)
                    }
                }
                .map { it.first }
    }
}

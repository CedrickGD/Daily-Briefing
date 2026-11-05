package com.cedrickgd.morgenbriefing.ui.search

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SearchPage

/** Where a search stands. */
enum class SearchPhase {
    /** Nothing searched (the field is empty or too short): suggestions show. */
    IDLE,

    /** The first page is on its way. */
    LOADING,

    /** Results (possibly none) are here; more may follow. */
    READY,

    /** The first page failed and nothing is known for the query. */
    FAILED,
}

/**
 * The results of one query, page by page ([NewsRepository.search]: page 0 = the newest, each
 * further page one week further back).
 */
@Immutable
data class SearchResults(
    /** The query as the repository normalizes it (trimmed, single spaces). */
    val query: String = "",
    val items: List<NewsItem> = emptyList(),
    /** The page to ask for next. */
    val nextPage: Int = 0,
    val hasMore: Boolean = false,
    val phase: SearchPhase = SearchPhase.IDLE,
    val loadingMore: Boolean = false,
    /** The last "more" request failed; offer to try again. */
    val moreFailed: Boolean = false,
    /** The news search could not be reached: [items] are only articles already on the phone. */
    val offline: Boolean = false,
    /** Pages in a row that brought nothing new (a quiet week); the paging stops after a few. */
    val emptyStreak: Int = 0,
) {
    val isEnd: Boolean get() = phase == SearchPhase.READY && !hasMore && !loadingMore
}

/**
 * The search's paging as a pure state machine (unit-tested). The view model feeds it the pages
 * the repository returns; anything that arrives for an older query or an unexpected page (a
 * search the user already replaced) is ignored, so a slow answer never mixes into new results.
 */
object SearchPaging {
    /** Quiet weeks in a row after which the search stops asking further back. */
    const val MAX_EMPTY_STREAK = 3

    /** Queries shorter than this are not searched (one letter finds everything). */
    const val MIN_QUERY_LENGTH = 2

    /** The query as the repository normalizes it. */
    fun normalize(query: String): String = query.trim().replace(WHITESPACE, " ")

    fun isSearchable(query: String): Boolean = normalize(query).length >= MIN_QUERY_LENGTH

    /** A new search for [query] starts: its first page is loading. */
    fun start(query: String): SearchResults = SearchResults(query = normalize(query), phase = SearchPhase.LOADING)

    /** Whether the next page can be asked for now. */
    fun canLoadMore(state: SearchResults): Boolean =
        state.phase == SearchPhase.READY && state.hasMore && !state.loadingMore

    /** The next page is requested; null when there is nothing to request (see [canLoadMore]). */
    fun beginMore(state: SearchResults): SearchResults? =
        if (canLoadMore(state)) state.copy(loadingMore = true, moreFailed = false) else null

    /**
     * Folds the repository's [page] into [state]. Page 0 replaces the results (an unreachable
     * search with cached matches still shows them, marked [SearchResults.offline]); later pages
     * append only stories not shown yet (same id or the same headline).
     */
    fun onPage(state: SearchResults, page: SearchPage): SearchResults {
        if (page.query != state.query || page.page != state.nextPage) return state
        if (page.page == 0) {
            if (state.phase != SearchPhase.LOADING) return state
            if (page.failed && page.items.isEmpty()) {
                return state.copy(phase = SearchPhase.FAILED, items = emptyList(), hasMore = false, offline = true)
            }
            return state.copy(
                items = distinct(page.items),
                nextPage = 1,
                hasMore = page.hasMore,
                phase = SearchPhase.READY,
                offline = page.failed,
                emptyStreak = 0,
            )
        }
        if (!state.loadingMore) return state
        if (page.failed) return state.copy(loadingMore = false, moreFailed = true)
        val fresh = appendable(state.items, page.items)
        val streak = if (fresh.isEmpty()) state.emptyStreak + 1 else 0
        return state.copy(
            items = state.items + fresh,
            nextPage = page.page + 1,
            hasMore = page.hasMore && streak < MAX_EMPTY_STREAK,
            loadingMore = false,
            moreFailed = false,
            emptyStreak = streak,
        )
    }

    /** An unexpected error while loading [page] of [query] (the repository normally reports failures in the page). */
    fun onError(state: SearchResults, query: String, page: Int): SearchResults {
        if (normalize(query) != state.query || page != state.nextPage) return state
        return if (page == 0) {
            if (state.phase == SearchPhase.LOADING) state.copy(phase = SearchPhase.FAILED, hasMore = false) else state
        } else {
            state.copy(loadingMore = false, moreFailed = true)
        }
    }

    /**
     * True when the last page brought nothing new but older ones may: the view model asks for
     * the next page right away instead of waiting for the user to reach the end of nothing.
     */
    fun wantsMore(state: SearchResults): Boolean = canLoadMore(state) && state.emptyStreak > 0

    /** [items] without repeats (same id or same headline), in order. */
    internal fun distinct(items: List<NewsItem>): List<NewsItem> = appendable(emptyList(), items)

    /** The stories of [page] that [shown] does not hold yet (same id or same headline), in order. */
    internal fun appendable(shown: List<NewsItem>, page: List<NewsItem>): List<NewsItem> {
        val ids = shown.mapTo(HashSet()) { it.id }
        val headlines = shown.mapTo(HashSet()) { headlineKey(it) }
        return page.filter { item ->
            val headline = headlineKey(item)
            val new = item.id !in ids && (headline.isEmpty() || headline !in headlines)
            if (new) {
                ids += item.id
                if (headline.isNotEmpty()) headlines += headline
            }
            new
        }
    }

    private fun headlineKey(item: NewsItem): String = NewsRepository.normalizeText(item.headline)

    private val WHITESPACE = Regex("\\s+")
}

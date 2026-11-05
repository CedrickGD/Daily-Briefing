package com.cedrickgd.morgenbriefing.ui.search

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cedrickgd.morgenbriefing.AppContainer
import com.cedrickgd.morgenbriefing.MorgenbriefingApp
import com.cedrickgd.morgenbriefing.data.SettingsOps
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.ui.news.FeedArticle
import com.cedrickgd.morgenbriefing.ui.news.readsCurrentSources
import com.cedrickgd.morgenbriefing.util.attempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.Locale

/** Everything the search screen shows besides the text field. */
@Immutable
data class SearchUiState(
    val results: SearchResults = SearchResults(),
    /** [SearchResults.items] ready for the feed's entries (topic, and the picture once the prefetcher knows it). */
    val articles: List<FeedArticle> = emptyList(),
    val recent: List<String> = emptyList(),
    /** "Gerade wichtig": the edition's newest top stories. */
    val trending: List<NewsItem> = emptyList(),
    val trendingLoading: Boolean = false,
    /** The own topic that follows the current query, when it is followed. */
    val followedTopicId: String? = null,
    val showImages: Boolean = true,
)

/** One-off messages for the snackbar. */
sealed interface SearchEvent {
    data class Followed(val query: String) : SearchEvent
    data class Unfollowed(val query: String) : SearchEvent
    data object SaveFailed : SearchEvent
}

/** Top stories shown as "Gerade wichtig". */
internal const val TRENDING_SIZE = 5

/**
 * Builds the search screen's state. Pure (unit-tested).
 *
 * @param images lead pictures the prefetcher found, by item id (Google News items have none).
 */
internal fun buildSearchUi(
    settings: AppSettings,
    lang: AppLanguage,
    results: SearchResults,
    images: Map<String, String>,
    top: TopicNews?,
    topLoading: Boolean,
): SearchUiState {
    val ownTopic = results.query.takeIf { it.isNotEmpty() }?.let { query ->
        settings.customTopics.firstOrNull { it.query.equals(query, ignoreCase = true) }
    }
    val resultsTopic = searchResultsTopic(results.query, ownTopic, lang)
    val articles = results.items.map { item ->
        val picture = images[item.id]
        val shown = if (item.image == null && picture != null) item.copy(image = ArticleImage(picture)) else item
        val topic = if (item.topicId == Topics.SEARCH_ID) resultsTopic else Topics.resolveOrNeutral(item.topicId, settings, lang)
        FeedArticle(shown, topic)
    }
    val followedTopic = ownTopic?.id?.takeIf { it in Topics.followedIds(settings) }
    return SearchUiState(
        results = results,
        articles = articles,
        recent = settings.recentSearches,
        trending = top?.items.orEmpty().asSequence().filter { it.sourceId !in settings.disabledFeeds }.take(TRENDING_SIZE).toList(),
        trendingLoading = topLoading,
        followedTopicId = followedTopic,
        showImages = settings.showImages,
    )
}

/**
 * The topic Google News results of [query] show (covers, badges): the query's own topic when it
 * is followed or was, else a stand-in titled with the query in the color the query would get
 * as an own topic – so the results look like the topic they can become, not a grey "Suche".
 */
internal fun searchResultsTopic(query: String, own: Topic?, lang: AppLanguage): Topic {
    if (own != null) return own.copy(id = Topics.SEARCH_ID)
    val base = Topics.searchTopic(lang)
    if (query.isBlank()) return base
    val seed = SEARCH_SEEDS[Math.floorMod(query.lowercase(Locale.ROOT).hashCode(), SEARCH_SEEDS.size)]
    return base.copy(title = query.take(SettingsOps.MAX_SEARCH_TITLE), seed = seed)
}

/** The colors of followed searches – the same palette and pick as [SettingsOps.followSearch]. */
private val SEARCH_SEEDS = listOf(0xFF2F6FDE, 0xFF0F9D9A, 0xFF2E8B57, 0xFF7B61FF, 0xFFD81B60, 0xFF8E24AA, 0xFF3949AB, 0xFF00838F)

/**
 * The news search: typing searches after a short pause ([DEBOUNCE_MS]; the IME's search key
 * at once), every new query cancels the previous one, results page endlessly into the past
 * ([SearchPaging]), the visible results are prefetched for the reader, and a query can be
 * followed as an own topic. The text in the field survives process death.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val container: AppContainer,
    private val saved: SavedStateHandle,
    initialQuery: String?,
) : ViewModel() {

    private val _query = MutableStateFlow(saved.get<String>(KEY_QUERY) ?: initialQuery.orEmpty())

    /** The text in the field. */
    val query: StateFlow<String> = _query.asStateFlow()

    private val results = MutableStateFlow(SearchResults())
    private var searchJob: Job? = null
    private var moreJob: Job? = null

    private val settingsFlow = container.settings.settings
        .catch { e ->
            Log.w(TAG, "Settings unavailable", e)
            emit(AppSettings())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<SearchUiState> = combine(
        settingsFlow.filterNotNull(),
        results,
        container.prefetcher.images,
        container.news.state,
    ) { settings, current, images, news ->
        buildSearchUi(settings, settings.language, current, images, news.topics[Topics.TOP_ID], Topics.TOP_ID in news.loading)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchUiState())

    private val _events = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<SearchEvent> = _events.asSharedFlow()

    /** Prefetches requested by this screen, cancelled when it goes. */
    private val prefetched = LinkedHashSet<String>()

    init {
        // A query handed over (a followed search, a link) runs at once and counts as searched.
        val start = _query.value
        if (SearchPaging.isSearchable(start)) submit(start)
        viewModelScope.launch {
            _query.drop(1)
                .map(SearchPaging::normalize)
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MS)
                .collect { text -> if (SearchPaging.isSearchable(text)) search(text) else clear() }
        }
        viewModelScope.launch {
            attempt { container.news.warm() }
            val settings = currentSettings() ?: return@launch
            // "Gerade wichtig" are the edition's top stories: a cache of another edition is outdated too.
            val top = container.news.state.value.topics[Topics.TOP_ID]
            if (container.news.isStale(Topics.TOP_ID, TRENDING_MAX_AGE) || !readsCurrentSources(Topics.TOP_ID, top, settings)) {
                attempt { container.news.refresh(listOf(Topics.TOP_ID), settings) }
            }
        }
    }

    fun onQueryChange(text: String) {
        _query.value = text
        saved[KEY_QUERY] = text
        // Emptying the field brings the suggestions back at once, without the pause.
        if (!SearchPaging.isSearchable(text)) clear()
    }

    /** The search key, a recent search or a handed-over query: searches now and remembers it. */
    fun submit(text: String = _query.value) {
        val normalized = SearchPaging.normalize(text)
        if (_query.value != text) onQueryChange(text)
        if (!SearchPaging.isSearchable(normalized)) return
        search(normalized)
        rememberQuery(normalized)
    }

    /** Searches again after a failure. */
    fun retry() {
        val current = results.value
        if (current.phase == SearchPhase.FAILED) search(current.query, force = true) else loadMore()
    }

    /** The list neared its end: the next page, if there is one. */
    fun loadMore() {
        val begun = SearchPaging.beginMore(results.value) ?: return
        results.value = begun
        val query = begun.query
        val page = begun.nextPage
        moreJob?.cancel()
        moreJob = viewModelScope.launch {
            val settings = currentSettings() ?: return@launch
            val result = attempt { container.news.search(query, settings, page) }
            results.update { current ->
                result.fold(
                    onSuccess = { SearchPaging.onPage(current, it) },
                    onFailure = { e ->
                        Log.w(TAG, "More results failed", e)
                        SearchPaging.onError(current, query, page)
                    },
                )
            }
            // A quiet week: go straight on to the one before.
            if (SearchPaging.wantsMore(results.value)) loadMore()
        }
    }

    /** An article of the results was opened: the query is worth remembering. */
    fun onOpened() {
        rememberQuery(results.value.query)
    }

    /** Warms the reader for [items] (those on screen). */
    fun prefetch(items: List<NewsItem>) {
        val settings = settingsFlow.value ?: return
        if (items.isEmpty()) return
        items.forEach { prefetched += it.id }
        container.prefetcher.prefetch(items, settings)
    }

    /** Follows the current query as an own topic, or stops following it again. */
    fun toggleFollow() {
        val query = results.value.query
        if (query.isEmpty()) return
        val followedId = state.value.followedTopicId
        viewModelScope.launch {
            attempt {
                if (followedId == null) {
                    container.settings.followSearch(query)
                    _events.tryEmit(SearchEvent.Followed(query))
                } else {
                    container.settings.unfollow(followedId)
                    _events.tryEmit(SearchEvent.Unfollowed(query))
                }
            }.onFailure { e ->
                Log.w(TAG, "Could not store the followed search", e)
                _events.tryEmit(SearchEvent.SaveFailed)
            }
        }
    }

    fun removeRecent(query: String) = write { container.settings.removeRecentSearch(query) }

    fun clearRecent() = write { container.settings.clearRecentSearches() }

    private fun search(query: String, force: Boolean = false) {
        val current = results.value
        // The debounce catching up with a query the search key already started.
        if (!force && current.query == query && current.phase != SearchPhase.IDLE && current.phase != SearchPhase.FAILED) return
        searchJob?.cancel()
        moreJob?.cancel()
        results.value = SearchPaging.start(query)
        searchJob = viewModelScope.launch {
            val settings = currentSettings() ?: return@launch
            val result = attempt { container.news.search(query, settings, 0) }
            results.update { state ->
                result.fold(
                    onSuccess = { SearchPaging.onPage(state, it) },
                    onFailure = { e ->
                        Log.w(TAG, "Search failed", e)
                        SearchPaging.onError(state, query, 0)
                    },
                )
            }
            if (SearchPaging.wantsMore(results.value)) loadMore()
        }
    }

    private fun clear() {
        searchJob?.cancel()
        moreJob?.cancel()
        results.value = SearchResults()
    }

    private fun rememberQuery(query: String) {
        if (!SearchPaging.isSearchable(query)) return
        write { container.settings.addRecentSearch(query) }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            attempt { block() }.onFailure { e ->
                Log.w(TAG, "Could not store a change", e)
                _events.tryEmit(SearchEvent.SaveFailed)
            }
        }
    }

    private suspend fun currentSettings(): AppSettings? =
        settingsFlow.value ?: attempt { container.settings.current() }.getOrNull()

    override fun onCleared() {
        container.prefetcher.cancel(prefetched)
        super.onCleared()
    }

    companion object {
        private const val TAG = "SearchViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val KEY_QUERY = "query"

        /** Pause after the last keystroke before searching. */
        const val DEBOUNCE_MS = 450L

        private val TRENDING_MAX_AGE: Duration = Duration.ofMinutes(15)

        fun factory(initialQuery: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MorgenbriefingApp
                SearchViewModel(app.container, createSavedStateHandle(), initialQuery)
            }
        }
    }
}

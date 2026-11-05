package com.cedrickgd.morgenbriefing.ui.discover

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
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import com.cedrickgd.morgenbriefing.util.attempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration

/** Place search inside the "Ort hinzufügen" sheet. */
@Immutable
data class PlaceSearchUi(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Place> = emptyList(),
    val failed: Boolean = false,
    /** The query [results] belong to; tells "nothing found" from "not searched yet". */
    val searchedFor: String? = null,
) {
    val tooShort: Boolean get() = query.trim().length < MIN_PLACE_QUERY
}

internal const val MIN_PLACE_QUERY = 2

/** One-off messages for the snackbar. */
sealed interface DiscoverEvent {
    data class PlaceAdded(val name: String) : DiscoverEvent
    data class SearchRemoved(val title: String) : DiscoverEvent
    data object SaveFailed : DiscoverEvent
}

/**
 * "Themen entdecken": the settings, the cached news and the user's [DiscoverSelection] become a
 * [DiscoverUiState] ([buildDiscoverState], off the main thread). The topic shown in a preview
 * is refreshed on demand (any built-in id, place or own topic works), at most every
 * [PREVIEW_MAX_AGE]. Every follow change writes through the settings repository; the feed picks
 * it up from there.
 *
 * The selection is kept in [SavedStateHandle], so the open category survives process death.
 */
class DiscoverViewModel(
    private val container: AppContainer,
    private val saved: SavedStateHandle,
    initialCategory: String?,
) : ViewModel() {

    private val selection = MutableStateFlow(
        DiscoverSelection(
            categoryId = saved.get<String>(KEY_CATEGORY) ?: initialCategory?.takeIf { Taxonomy.byId(it)?.isCategory == true },
            previewId = saved.get<String>(KEY_PREVIEW),
            expandedPlaceId = saved.get<String>(KEY_PLACE),
        ),
    )
    private val failed = MutableStateFlow<Set<String>>(emptySet())
    private val settingsFlow = container.settings.settings
        .catch { e ->
            Log.w(TAG, "Settings unavailable", e)
            emit(AppSettings())
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<DiscoverUiState> = combine(
        settingsFlow.filterNotNull(),
        container.news.state,
        selection,
        failed,
    ) { settings, news, picked, failedIds ->
        buildDiscoverState(settings, settings.language, news.topics, news.loading, failedIds, picked)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DiscoverUiState())

    private val _events = MutableSharedFlow<DiscoverEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<DiscoverEvent> = _events.asSharedFlow()

    private val _placeSearch = MutableStateFlow(PlaceSearchUi())
    val placeSearch: StateFlow<PlaceSearchUi> = _placeSearch.asStateFlow()
    private var placeJob: Job? = null

    init {
        viewModelScope.launch { attempt { container.news.warm() } }
        // Keep the shown preview fresh: whenever another topic is previewed, refresh it if stale.
        viewModelScope.launch {
            selection.map(::previewTarget).distinctUntilChanged().collect { id -> if (id != null) refreshIfStale(id) }
        }
    }

    /** Opens a category (null: back to the grid). */
    fun selectCategory(id: String?) = updateSelection { it.copy(categoryId = id, previewId = null) }

    /** Opens (or closes again) the newest headlines of a sub-topic under its row. */
    fun toggleSubPreview(topicId: String) =
        updateSelection { it.copy(previewId = if (it.previewId == topicId) null else topicId) }

    /** Opens or closes the headlines of a followed place. */
    fun togglePlace(topicId: String) =
        updateSelection { it.copy(expandedPlaceId = if (it.expandedPlaceId == topicId) null else topicId) }

    /** Follows or stops following any topic; the last followed one stays (see [SettingsOps.canUnfollow]). */
    fun setFollowed(topicId: String, follow: Boolean) = write {
        if (follow) container.settings.follow(topicId) else container.settings.unfollow(topicId)
    }

    /** Retries the preview that failed. */
    fun retryPreview(topicId: String) {
        viewModelScope.launch { refresh(topicId) }
    }

    fun removePlace(topicId: String) = write {
        container.settings.removeLocalPlace(topicId)
        updateSelection { if (it.expandedPlaceId == topicId) it.copy(expandedPlaceId = null) else it }
        attempt { container.news.forget(topicId) }
    }

    /** Deletes a followed search (own topic with a query) for good. */
    fun removeSearch(topicId: String, title: String) = write {
        container.settings.update { SettingsOps.deleteCustomTopic(it, topicId) }
        attempt { container.news.forget(topicId) }
        _events.tryEmit(DiscoverEvent.SearchRemoved(title))
    }

    fun onPlaceQuery(query: String) {
        _placeSearch.update { it.copy(query = query) }
        placeJob?.cancel()
        val text = query.trim()
        if (text.length < MIN_PLACE_QUERY) {
            _placeSearch.update { it.copy(loading = false, results = emptyList(), failed = false, searchedFor = null) }
            return
        }
        placeJob = viewModelScope.launch {
            delay(PLACE_DEBOUNCE_MS)
            _placeSearch.update { it.copy(loading = true, failed = false) }
            val result = attempt { container.openMeteo.searchPlaces(text, (settingsFlow.value ?: container.settings.current()).language) }
            _placeSearch.update { current ->
                if (current.query.trim() != text) {
                    current
                } else {
                    result.fold(
                        onSuccess = { current.copy(loading = false, results = it, failed = false, searchedFor = text) },
                        onFailure = { current.copy(loading = false, results = emptyList(), failed = true, searchedFor = text) },
                    )
                }
            }
        }
    }

    fun resetPlaceSearch() {
        placeJob?.cancel()
        _placeSearch.value = PlaceSearchUi()
    }

    /** Follows [place]'s local news and opens its row with the first headlines. */
    fun addPlace(place: Place) = write {
        val id = container.settings.addLocalPlace(place)
        resetPlaceSearch()
        updateSelection { it.copy(categoryId = null, expandedPlaceId = id) }
        _events.tryEmit(DiscoverEvent.PlaceAdded(place.name))
    }

    private fun updateSelection(transform: (DiscoverSelection) -> DiscoverSelection) {
        val next = transform(selection.value)
        selection.value = next
        saved[KEY_CATEGORY] = next.categoryId
        saved[KEY_PREVIEW] = next.previewId
        saved[KEY_PLACE] = next.expandedPlaceId
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            attempt { block() }.onFailure { e ->
                Log.w(TAG, "Could not store a change", e)
                _events.tryEmit(DiscoverEvent.SaveFailed)
            }
        }
    }

    private suspend fun refreshIfStale(topicId: String) {
        attempt { container.news.warm() }
        if (container.news.isStale(topicId, PREVIEW_MAX_AGE)) refresh(topicId)
    }

    private suspend fun refresh(topicId: String) {
        val settings = settingsFlow.value ?: attempt { container.settings.current() }.getOrNull() ?: return
        val outcome = attempt { container.news.refresh(listOf(topicId), settings) }.getOrNull()
        val ok = outcome != null && topicId !in outcome.failed
        failed.update { if (ok) it - topicId else it + topicId }
    }

    companion object {
        private const val TAG = "DiscoverViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val PLACE_DEBOUNCE_MS = 300L
        private const val KEY_CATEGORY = "category"
        private const val KEY_PREVIEW = "preview"
        private const val KEY_PLACE = "place"

        /** A preview older than this is fetched again when shown. */
        private val PREVIEW_MAX_AGE: Duration = Duration.ofMinutes(20)

        fun factory(initialCategory: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MorgenbriefingApp
                DiscoverViewModel(app.container, createSavedStateHandle(), initialCategory)
            }
        }
    }
}

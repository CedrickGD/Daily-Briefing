package com.cedrickgd.morgenbriefing.ui.favorites

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cedrickgd.morgenbriefing.AppContainer
import com.cedrickgd.morgenbriefing.MorgenbriefingApp
import com.cedrickgd.morgenbriefing.data.BERLIN
import com.cedrickgd.morgenbriefing.data.local.HistoryEntry
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.ui.history.HistoryDay
import com.cedrickgd.morgenbriefing.ui.history.dayOf
import com.cedrickgd.morgenbriefing.ui.history.matchesQuery
import com.cedrickgd.morgenbriefing.util.attempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The favorites list (unit-tested): the starred [entries] that match [query] (title, source or
 * site, folded like the archive's search), newest star first, grouped by the day they were
 * starred in [zone].
 */
internal fun favoriteDays(entries: List<HistoryEntry>, query: String, zone: ZoneId): List<HistoryDay> {
    val shown = entries.filter { it.starred && matchesQuery(it, query) }.sortedByDescending { it.starredAt }
    val days = LinkedHashMap<LocalDate, MutableList<HistoryEntry>>()
    for (entry in shown) days.getOrPut(dayOf(entry, zone)) { ArrayList() } += entry
    return days.map { (date, list) -> HistoryDay(date, list) }
}

/** What "Favoriten" shows. */
@Immutable
data class FavoritesUiState(
    /** The starred stories that pass the search, by day, newest first. */
    val days: List<HistoryDay> = emptyList(),
    /** Every starred story, searched or not. */
    val count: Int = 0,
    val query: String = "",
    val today: LocalDate = LocalDate.now(BERLIN),
    val showImages: Boolean = true,
    /** The topic of every entry shown, by topic id (the cover of a story without a picture). */
    val topics: Map<String, Topic> = emptyMap(),
    /** The favorites were read from disk (an empty list really is empty). */
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = count == 0
}

/**
 * "Favoriten": every story the user starred ([com.cedrickgd.morgenbriefing.data.local.FavoritesStore]),
 * searched by title or source and grouped by the day it was starred. A row's star comes off with
 * [remove] and back with [restore] ("Rückgängig").
 */
class FavoritesViewModel(
    private val container: AppContainer,
    private val zone: ZoneId = BERLIN,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(BERLIN) },
) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<FavoritesUiState> = combine(
        container.favorites.state,
        query,
        container.settings.settings.catch { e -> Log.w(TAG, "Settings unavailable", e) },
    ) { favorites, q, settings ->
        val days = favoriteDays(favorites.entries, q, zone)
        val topics = days.asSequence()
            .flatMap { it.entries.asSequence() }
            .map { it.topicId }
            .distinct()
            .associateWith { Topics.resolveOrNeutral(it, settings) }
        FavoritesUiState(
            days = days,
            count = favorites.size,
            query = q,
            today = clock().withZoneSameInstant(zone).toLocalDate(),
            showImages = settings.showImages,
            topics = topics,
            loaded = true,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FavoritesUiState(today = clock().toLocalDate()))

    init {
        viewModelScope.launch {
            attempt { container.favorites.warm() }.onFailure { Log.w(TAG, "Favorites unavailable", it) }
        }
    }

    fun setQuery(text: String) {
        query.value = text
    }

    /** The story's star comes off (it leaves the list). */
    fun remove(entry: HistoryEntry) {
        attempt { container.favorites.remove(entry.key) }.onFailure { Log.w(TAG, "Could not remove ${entry.key}", it) }
    }

    /** Puts [entry] back as it was ("Rückgängig"). */
    fun restore(entry: HistoryEntry) {
        attempt { container.favorites.restore(entry) }.onFailure { Log.w(TAG, "Could not restore ${entry.key}", it) }
    }

    companion object {
        private const val TAG = "FavoritesViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MorgenbriefingApp
                FavoritesViewModel(container = app.container)
            }
        }
    }
}

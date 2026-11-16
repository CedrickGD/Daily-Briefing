package com.cedrickgd.morgenbriefing.ui.history

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

/** What "Gelesen & ausgeblendet" shows. */
@Immutable
data class HistoryUiState(
    /** The entries that pass the search and the filter, by day, newest first. */
    val days: List<HistoryDay> = emptyList(),
    val readCount: Int = 0,
    val hiddenCount: Int = 0,
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val today: LocalDate = LocalDate.now(BERLIN),
    val showImages: Boolean = true,
    /** The topic of every entry shown, by topic id (the cover of a story without a picture). */
    val topics: Map<String, Topic> = emptyMap(),
    /** The history was read from disk (an empty archive really is empty). */
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = readCount == 0 && hiddenCount == 0
    val shownCount: Int get() = days.sumOf { it.entries.size }
}

/**
 * "Gelesen & ausgeblendet": every story the user read or hid ([com.cedrickgd.morgenbriefing.data.local.ReadHistoryStore]),
 * searched by title or source, filtered (all, read, hidden) and grouped by day. Rows bring a
 * story back into the feed ([showAgain]), count it as unread ([markUnread]) – both undone by
 * [restore] – and the whole history can be cleared ([clear]).
 */
class HistoryViewModel(
    private val container: AppContainer,
    private val zone: ZoneId = BERLIN,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(BERLIN) },
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val state: StateFlow<HistoryUiState> = combine(
        container.history.state,
        query,
        filter,
        container.settings.settings.catch { e -> Log.w(TAG, "Settings unavailable", e) },
    ) { history, q, f, settings ->
        val days = historyDays(history.entries, q, f, zone)
        val topics = days.asSequence()
            .flatMap { it.entries.asSequence() }
            .map { it.topicId }
            .distinct()
            .associateWith { Topics.resolveOrNeutral(it, settings) }
        HistoryUiState(
            days = days,
            readCount = history.readCount,
            hiddenCount = history.hiddenCount,
            query = q,
            filter = f,
            today = clock().withZoneSameInstant(zone).toLocalDate(),
            showImages = settings.showImages,
            topics = topics,
            loaded = true,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HistoryUiState(query = "", today = clock().toLocalDate()))

    init {
        viewModelScope.launch {
            attempt { container.history.warm() }.onFailure { Log.w(TAG, "Reading history unavailable", it) }
        }
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setFilter(value: HistoryFilter) {
        filter.value = value
    }

    /** The story shows in the feed again (it stays listed while it counts as read). */
    fun showAgain(entry: HistoryEntry) {
        attempt { container.history.setHidden(entry.key, false) }.onFailure { Log.w(TAG, "Could not show ${entry.key} again", it) }
    }

    /** The story counts as unread again (it leaves the archive unless it is hidden). */
    fun markUnread(entry: HistoryEntry) {
        attempt { container.history.setRead(entry.key, false) }.onFailure { Log.w(TAG, "Could not mark ${entry.key} unread", it) }
    }

    /** Puts [entry] back as it was before a row action ("Rückgängig"). */
    fun restore(entry: HistoryEntry) {
        attempt { container.history.restore(entry) }.onFailure { Log.w(TAG, "Could not restore ${entry.key}", it) }
    }

    /** Forgets every entry ("Verlauf löschen"). */
    fun clear() {
        attempt { container.history.clear() }.onFailure { Log.w(TAG, "Could not clear the history", it) }
    }

    companion object {
        private const val TAG = "HistoryViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MorgenbriefingApp
                HistoryViewModel(container = app.container)
            }
        }
    }
}

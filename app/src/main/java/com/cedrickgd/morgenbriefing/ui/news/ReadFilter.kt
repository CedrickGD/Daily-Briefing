package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.local.StoryIndex
import com.cedrickgd.morgenbriefing.data.model.NewsItem

/*
 * What the reading history does to the news lists, as pure values (unit-tested): hidden stories
 * leave every list at once, read ones – with "Gelesene Artikel ausblenden" – only when a list is
 * built anew, so nothing vanishes under the finger after closing the reader.
 */

/**
 * The stories the news lists leave out ([buildFeed]).
 *
 * @property hidden the stories hidden by hand (⋮ → "Ausblenden"): out of every list at once –
 *   it follows the history as it changes ([com.cedrickgd.morgenbriefing.data.local.ReadHistory.hidden]).
 * @property read the stories read as of the last time the lists were built anew (a snapshot of
 *   [com.cedrickgd.morgenbriefing.data.local.ReadHistory.read] taken at start, on a refresh, when the tab changes):
 *   out of every list while [FeedPrefs.hideRead] is on. A story read since shows, calmer, until
 *   the next build (app start, refresh, another tab).
 */
@Immutable
data class ReadFilter(val hidden: StoryIndex = StoryIndex.EMPTY, val read: StoryIndex = StoryIndex.EMPTY) {
    /** Nothing to leave out. */
    val isEmpty: Boolean get() = hidden.isEmpty && read.isEmpty

    /** True when a list shows [item]: not hidden, and not read while [hideRead] is on. */
    fun keeps(item: NewsItem, hideRead: Boolean): Boolean = item !in hidden && !(hideRead && item in read)

    companion object {
        val NONE = ReadFilter()
    }
}

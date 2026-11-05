package com.cedrickgd.morgenbriefing.ui.today

import com.cedrickgd.morgenbriefing.data.model.NewsItem

/**
 * What Today asked the prefetcher to warm, between leaving the screen and coming back. Pure
 * bookkeeping (unit-tested); [TodayViewModel] does the prefetcher calls.
 *
 * Leaving the screen (pause) cancels every warm-up it asked for ([stop]). Coming back (resume)
 * does not make the list ask again – the visible articles are the same, so nothing new is
 * reported – hence the last window is kept and handed out once by [resume].
 */
internal class PrefetchSession(private val maxTracked: Int = MAX_TRACKED) {

    /** Article ids handed to the prefetcher since the screen was last left (oldest first). */
    private val requested = LinkedHashSet<String>()

    /** The window the list asked for last; sent again after a pause. */
    private var lastWindow: List<NewsItem> = emptyList()

    /** The list asks to warm [items] (on screen and just below). */
    fun request(items: List<NewsItem>) {
        if (items.isEmpty()) return
        lastWindow = items
        items.forEach { item ->
            // Re-adding moves the id to the end: the newest requests are tracked longest.
            requested.remove(item.id)
            requested += item.id
        }
        while (requested.size > maxTracked) requested.remove(requested.first())
    }

    /** The screen was left: the ids whose warm-ups should be cancelled (empty when none). */
    fun stop(): List<String> {
        val ids = requested.toList()
        requested.clear()
        return ids
    }

    /**
     * The screen is back: the window to warm again (empty when nothing was cancelled since
     * then). It counts as requested again, so the next pause cancels it as well.
     */
    fun resume(): List<NewsItem> {
        if (requested.isNotEmpty()) return emptyList()
        val window = lastWindow
        request(window)
        return window
    }

    private companion object {
        const val MAX_TRACKED = 200
    }
}

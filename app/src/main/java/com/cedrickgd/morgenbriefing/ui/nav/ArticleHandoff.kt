package com.cedrickgd.morgenbriefing.ui.nav

import com.cedrickgd.morgenbriefing.data.model.NewsItem

/**
 * Items opened in the reader from lists the news repository does not keep (search results):
 * the reader finds their headline, publisher and picture here instead of showing a bare
 * Google News link. A small in-memory LRU – after process death the reader falls back to the
 * page's own title, as for any unknown link.
 */
internal object ArticleHandoff {
    private const val MAX = 40

    private val items = object : LinkedHashMap<String, NewsItem>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NewsItem>?): Boolean = size > MAX
    }

    /** Remembers [item] for the reader. */
    fun put(item: NewsItem) {
        synchronized(items) { items[item.id] = item }
    }

    /** The item handed over with [itemId], else the one with [url]; null when none was. */
    fun find(itemId: String?, url: String): NewsItem? = synchronized(items) {
        itemId?.let { items[it] } ?: items.values.firstOrNull { it.url == url }
    }

    /** Tests only. */
    internal fun clear() {
        synchronized(items) { items.clear() }
    }
}

package com.cedrickgd.morgenbriefing.ui.today

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.ui.news.FeedFilter
import com.cedrickgd.morgenbriefing.ui.news.NewsFeed
import com.cedrickgd.morgenbriefing.ui.news.entryItemId
import com.cedrickgd.morgenbriefing.ui.news.forYouSectionOf
import com.cedrickgd.morgenbriefing.ui.news.forYouVerifyWindow
import com.cedrickgd.morgenbriefing.ui.news.prefetchWindow
import com.cedrickgd.morgenbriefing.ui.news.sectionFor
import com.cedrickgd.morgenbriefing.ui.news.verifyAhead

/**
 * What Today hands the prefetcher for the list on screen ([visibleKeys] = the lazy-list keys in
 * view), in priority order. Pure (unit-tested).
 *
 * - "Nur vollständige Artikel" ([NewsFeed.verifying]): the candidates to verify next – for
 *   "Für dich" the ones that fill the sections from the one on screen on
 *   ([forYouVerifyWindow]), for "Neueste" and a topic's endless list the next waiting ones once
 *   fewer than [com.cedrickgd.morgenbriefing.ui.news.VERIFY_AHEAD] verified articles lie below
 *   the screen ([verifyAhead]). The articles on screen are verified already.
 * - Otherwise the articles on screen and the next few ([prefetchWindow]), warmed as
 *   "Artikel vorladen" allows.
 */
internal fun warmWindow(
    selected: FeedFilter,
    feed: NewsFeed,
    revealed: Int,
    visibleKeys: List<Any?>,
    sectionSize: Int,
): List<NewsItem> {
    val visibleIds = visibleKeys.mapNotNull(::entryItemId)
    if (!feed.verifying) {
        val items = viewItems(selected, feed, revealed)
        val byId = items.associateBy { it.id }
        return prefetchWindow(items.map { it.id }, visibleIds).mapNotNull(byId::get)
    }
    return when (selected) {
        FeedFilter.ForYou ->
            forYouVerifyWindow(feed.sections, sectionSize, visibleKeys.firstNotNullOfOrNull(::forYouSectionOf) ?: 0)
        FeedFilter.Latest -> verifyAhead(feed.latest.map { it.item.id }, visibleIds, feed.latestWaiting)
        is FeedFilter.OneTopic -> feed.sectionFor(selected.topicId)
            ?.let { section -> verifyAhead(section.all.map(NewsItem::id), visibleIds, section.waiting) }
            .orEmpty()
    }
}

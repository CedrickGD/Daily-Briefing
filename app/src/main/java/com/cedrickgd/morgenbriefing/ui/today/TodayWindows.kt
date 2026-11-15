package com.cedrickgd.morgenbriefing.ui.today

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.ui.news.FeedFilter
import com.cedrickgd.morgenbriefing.ui.news.NewsFeed
import com.cedrickgd.morgenbriefing.ui.news.PAGE_SIZE
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
 *   ([forYouVerifyWindow]) and, once its last card or the list below it is on screen, the next
 *   ones of that list; for "Neueste" and a topic's endless list the next waiting ones once
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
        FeedFilter.ForYou -> {
            val sections = visibleKeys.mapNotNull(::forYouSectionOf)
            val cards = forYouVerifyWindow(feed.sections, sectionSize, sections.firstOrNull() ?: 0)
            val more = feed.more.map { it.item.id }
            val nearEnd = (sections.maxOrNull() ?: -1) >= feed.sections.lastIndex || visibleIds.any(more.toHashSet()::contains)
            if (!nearEnd) cards else (cards + verifyAhead(more, visibleIds, feed.timelineWaiting)).distinctBy { it.id }
        }
        FeedFilter.Latest -> verifyAhead(feed.timeline.map { it.item.id }, visibleIds, feed.timelineWaiting)
        is FeedFilter.OneTopic -> feed.sectionFor(selected.topicId)
            ?.let { section -> verifyAhead(section.all.map(NewsItem::id), visibleIds, section.waiting) }
            .orEmpty()
    }
}

/** A story whose cover the list wants; [lead]: it sits in the large picture slot (else a row's thumbnail). */
internal data class CoverTarget(val item: NewsItem, val lead: Boolean)

/**
 * The stories of the current view, top to bottom, with what the next page will reveal
 * ([upcoming]) – for the covers, which are looked up ahead of the list. The first story of a
 * "Für dich" card and of a topic's list is the large one.
 */
internal fun viewStories(selected: FeedFilter, feed: NewsFeed, revealed: Int, upcoming: Int = PAGE_SIZE): List<CoverTarget> = when (selected) {
    FeedFilter.ForYou ->
        feed.sections.flatMap { section -> section.top.mapIndexed { index, item -> CoverTarget(item, lead = index == 0) } } +
            feed.more.take(revealed + upcoming).map { CoverTarget(it.item, lead = false) }
    FeedFilter.Latest -> feed.timeline.take(revealed + upcoming).map { CoverTarget(it.item, lead = false) }
    is FeedFilter.OneTopic -> feed.sectionFor(selected.topicId)?.all?.take(revealed + upcoming)
        ?.mapIndexed { index, item -> CoverTarget(item, lead = index == 0) }
        .orEmpty()
}

/**
 * The stories whose covers are wanted now, in priority order: the ones on screen ([visibleKeys] =
 * the lazy-list keys in view), then the next [ahead] below. While no story is on screen yet (the
 * page rests at its top, above the feed) the first [ahead] stories: their covers are there when
 * the feed scrolls in. Each story once, as the large one if it is large anywhere. Pure.
 */
internal fun coverWindow(stories: List<CoverTarget>, visibleKeys: List<Any?>, ahead: Int): List<CoverTarget> {
    if (stories.isEmpty()) return emptyList()
    val byId = LinkedHashMap<String, CoverTarget>()
    for (story in stories) {
        val known = byId[story.item.id]
        if (known == null || (story.lead && !known.lead)) byId[story.item.id] = story
    }
    val ordered = stories.map { it.item.id }
    val window = prefetchWindow(ordered, visibleKeys.mapNotNull(::entryItemId), ahead)
    val ids = window.ifEmpty { ordered.distinct().take(ahead) }
    return ids.mapNotNull(byId::get)
}

/** Stories below the screen whose covers are looked up ahead (about a page). */
internal const val COVER_LOOKUP_AHEAD = 24

/** Stories below the screen whose pictures are loaded ahead (about a screen of rows). */
internal const val COVER_PRELOAD_AHEAD = 8

/** [COVER_PRELOAD_AHEAD] with light effects: a budget phone keeps fewer decoded pictures. */
internal const val COVER_PRELOAD_AHEAD_LIGHT = 5

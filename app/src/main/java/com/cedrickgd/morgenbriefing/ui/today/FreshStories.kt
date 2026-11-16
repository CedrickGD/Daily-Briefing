package com.cedrickgd.morgenbriefing.ui.today

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.ui.news.FeedFilter
import com.cedrickgd.morgenbriefing.ui.news.NewsFeed
import com.cedrickgd.morgenbriefing.ui.news.sectionFor

/*
 * A refresh like Google News, as pure functions (unit-tested): which stories a refresh brought,
 * which of them still wait for their check (they go first – they belong at the top), and how
 * many of them the list on screen shows ("12 neue Meldungen").
 *
 * Why new stories did not show before: a refresh sorts the lists afresh, newest first, but with
 * "nur vollständige Artikel" a new story shows only once it is verified – and the lists verified
 * only what lies below the screen and only while fewer than 30 verified stories waited there,
 * "Für dich" only for cards that were not full. A full list never checked the new stories at its
 * top, so they never appeared. And a story that did appear landed above the item the list was
 * anchored to, out of sight. Now the fresh ones are checked first, and the page goes to the top
 * and says how many arrived.
 */

/** Fresh stories still waiting for their check that are verified ahead of everything else. */
internal const val FRESH_VERIFY_CAP = 24

/**
 * What one refresh brought ([TodayUiState.fresh]): the new story ids, and whether the user asked
 * for it (pull-to-refresh: the page always answers, also "Keine neuen Meldungen") or it ran by
 * itself on start or return (the page speaks up only when something new is shown).
 *
 * @property round counts the refreshes, so the same result twice is announced twice.
 */
@Immutable
data class FreshRound(val round: Int, val ids: Set<String>, val byUser: Boolean)

/** Every story id the repository holds for [topics]. */
internal fun storyIds(topics: Map<String, TopicNews>): Set<String> =
    topics.values.flatMapTo(HashSet()) { news -> news.items.map(NewsItem::id) }

/**
 * The stories a refresh brought: held in [after], not held [before] – first pages only (older
 * pages the endless lists loaded meanwhile are no news). Nothing when nothing was held before (a
 * first start: everything is new).
 */
internal fun freshIds(before: Set<String>, after: Map<String, TopicNews>): Set<String> {
    if (before.isEmpty()) return emptySet()
    return after.values.asSequence()
        .flatMap { it.items.asSequence() }
        .filter { it.id !in before && !it.sourceId.endsWith(NewsItem.PAGE_SUFFIX) }
        .mapTo(HashSet()) { it.id }
}

/**
 * The fresh stories ([fresh]) of [feed] still waiting for their check, in list order, each once,
 * at most [cap]: verified first, so they take their place at the top of the lists.
 */
internal fun freshWaiting(feed: NewsFeed, fresh: Set<String>, cap: Int = FRESH_VERIFY_CAP): List<NewsItem> {
    if (fresh.isEmpty() || !feed.verifying) return emptyList()
    val out = LinkedHashMap<String, NewsItem>()
    (feed.timelineWaiting.asSequence() + feed.sections.asSequence().flatMap { it.waiting.asSequence() })
        .filter { it.id in fresh }
        .forEach { out.putIfAbsent(it.id, it) }
    return out.values.take(cap)
}

/** How many of the [fresh] stories the view [selected] shows (each story once). */
internal fun freshShown(selected: FeedFilter, feed: NewsFeed, fresh: Set<String>): Int {
    if (fresh.isEmpty()) return 0
    val ids: Sequence<String> = when (selected) {
        FeedFilter.ForYou -> feed.sections.asSequence().flatMap { it.top.asSequence().map(NewsItem::id) } + feed.more.asSequence().map { it.item.id }
        FeedFilter.Latest -> feed.timeline.asSequence().map { it.item.id }
        is FeedFilter.OneTopic -> feed.sectionFor(selected.topicId)?.all?.asSequence()?.map(NewsItem::id).orEmpty()
    }
    return ids.filter { it in fresh }.toSet().size
}

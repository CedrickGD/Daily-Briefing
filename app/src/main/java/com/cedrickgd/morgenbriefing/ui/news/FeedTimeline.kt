package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.util.parseInstantOrNull
import java.time.Duration
import java.time.Instant

/*
 * The endless timeline behind "Neueste" and the end of "Für dich" as pure functions
 * (unit-tested): everything the followed topics hold, newest first, as far back as every topic
 * that can still page has been read – so older pages only ever append.
 */

/** The list key of the endless timeline under "nur vollständige Artikel" ("Neueste" keeps [LATEST_LIST]). */
internal const val TIMELINE_LIST = "~timeline"

/** Topics asked for older articles in one step of the timeline. */
internal const val OLDER_TOPICS_PER_STEP = 4

/** Topics whose lists end within this time of the shallowest one are paged together with it. */
private val SAME_STEP: Duration = Duration.ofHours(24)

/** Stories of the last day always show, however far back the topics have been read. */
internal val TIMELINE_FLOOR: Duration = Duration.ofHours(24)

/**
 * How far back the list of a topic that can page is complete: the bulk of its Google News first
 * page ([NewsRepository.bulkOldest] – stray old hits do not count), where paging starts, or the
 * oldest article paging appended (its windows are contiguous) – whichever reaches further back,
 * so a page that brought only newer stories never makes the list look shorter. A topic with
 * articles but no Google News ones is complete only up to [now]; null for a topic without dated
 * articles (it holds nothing back).
 */
internal fun pagedDepth(items: List<NewsItem>, now: Instant): Instant? {
    val (pages, others) = items.partition { it.sourceId.endsWith(NewsItem.PAGE_SUFFIX) }
    val paged = pages.mapNotNull { parseInstantOrNull(it.publishedAt) }.minOrNull()
    val firstPage = NewsRepository.bulkOldest(others.filter { it.aggregator != null })
    if (paged != null || firstPage != null) return listOfNotNull(paged, firstPage).min()
    return if (items.any { parseInstantOrNull(it.publishedAt) != null }) now else null
}

/**
 * The endless timeline: every story of [everything] (per followed topic, what it holds) newest
 * first, each once under its most specific topic – whatever the age setting says, which shapes
 * only the "Für dich" cards. Stories older than [horizon] wait: a topic that can still page has
 * not been read that far back, and its older pages would land in the middle of what is on
 * screen. The stories since [floor] (the last day) never wait, and undated ones count as that
 * old. Null [horizon] (no topic can page): everything shows.
 */
internal fun timelineOf(
    everything: List<Pair<Topic, List<NewsItem>>>,
    horizon: Instant?,
    floor: Instant,
): List<FeedArticle> {
    val byId = LinkedHashMap<String, FeedArticle>()
    for ((topic, items) in everything) {
        for (item in items) {
            val current = byId[item.id]
            if (current == null || isMoreSpecific(topic, current.topic, item)) byId[item.id] = FeedArticle(item, topic)
        }
    }
    // Each date is parsed once, not per comparison.
    return byId.values
        .mapNotNull { article ->
            val at = parseInstantOrNull(article.item.publishedAt) ?: floor
            if (horizon != null && at < horizon && at < floor) null else article to at
        }
        .sortedByDescending { it.second }
        .map { it.first }
}

/**
 * The topics to ask for older articles next, given how far back each topic that can still page
 * is complete ([depths]): the shallowest one – it holds the timeline back – and the ones that
 * end within a day of it, at most [max]. Empty when nothing can page.
 */
internal fun nextToPage(depths: Map<String, Instant>, max: Int = OLDER_TOPICS_PER_STEP): List<String> {
    val shallowest = depths.values.maxOrNull() ?: return emptyList()
    return depths.entries
        .sortedByDescending { it.value }
        .takeWhile { Duration.between(it.value, shallowest) <= SAME_STEP }
        .take(max)
        .map { it.key }
}

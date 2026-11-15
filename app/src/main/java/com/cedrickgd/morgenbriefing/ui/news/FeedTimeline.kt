package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.news.StoryTwins
import com.cedrickgd.morgenbriefing.util.parseInstantOrNull
import java.time.Duration
import java.time.Instant

/*
 * The endless lists as pure functions (unit-tested).
 *
 * - The timeline behind "Neueste" and the end of "Für dich": everything the followed topics
 *   hold, newest first, each story once ([storiesOnce]), as far back as every topic that can
 *   still page has been read ([timelineOf]).
 * - A topic's own list the same way: as far back as the topic has been read ([readSoFar]).
 * - Both only ever grow at their end: what a list showed keeps its place, and whatever older
 *   pages bring is appended ([appendOnly], [FeedOrder]) – nothing moves under the reader's eyes.
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
 * screen. The stories since [floor] (the last day) never wait, undated ones count as that old,
 * and what the list [shown] before stays. Null [horizon] (no topic can page): everything shows.
 */
internal fun timelineOf(
    everything: List<Pair<Topic, List<NewsItem>>>,
    horizon: Instant?,
    floor: Instant,
    shown: Collection<String> = emptyList(),
): List<FeedArticle> = timelineStories(everything, horizon, floor, shown).articles

/** [timelineOf] with the twins it folded ([storiesOnce]). */
internal fun timelineStories(
    everything: List<Pair<Topic, List<NewsItem>>>,
    horizon: Instant?,
    floor: Instant,
    shown: Collection<String> = emptyList(),
): Stories {
    val byId = LinkedHashMap<String, FeedArticle>()
    for ((topic, items) in everything) {
        for (item in items) {
            val current = byId[item.id]
            if (current == null || isMoreSpecific(topic, current.topic, item)) byId[item.id] = FeedArticle(item, topic)
        }
    }
    val known = shown as? Set<String> ?: shown.toHashSet()
    // Each date is parsed once, not per comparison.
    val sorted = byId.values
        .mapNotNull { article ->
            val at = parseInstantOrNull(article.item.publishedAt) ?: floor
            if (horizon != null && at < horizon && at < floor && article.item.id !in known) null else article to at
        }
        .sortedByDescending { it.second }
        .map { it.first }
    return storiesOnce(sorted, known)
}

/** A list with every story once, and the article that stands for each one folded into it (twin id → shown id). */
internal class Stories(val articles: List<FeedArticle>, val twins: Map<String, String>)

/**
 * [articles] with every story once. The topics' lists hold a story once each, but the same
 * article reaches two topics through two feeds – "taz Politik" in one, Google News' "taz.de" in
 * another, "heise online" and "heise IT" – with two links and so two ids: back to back in a list
 * of all topics. Folded here: the same headline, or the same publisher with nearly the same
 * headline at nearly the same time ([StoryTwins]). The publisher's own article wins over Google
 * News', one with a picture over one without ([StoryTwins.quality]) – but an article the list
 * [shown] before stays the one (nothing changes under the reader's eyes) – and it takes the
 * picture or teaser only its twin carries. It stands where the first of the two stood.
 */
internal fun storiesOnce(articles: List<FeedArticle>, shown: Set<String> = emptySet()): Stories {
    val out = ArrayList<FeedArticle>(articles.size)
    val twins = HashMap<String, String>()
    val headlines = HashMap<String, Int>()
    val finder = StoryTwins.Finder()
    for (article in articles) {
        val item = article.item
        val headline = NewsRepository.headlineKey(item)
        val hit = headline?.let(headlines::get) ?: finder.find(item)
        if (hit == null) {
            out += article
            headline?.let { headlines[it] = out.lastIndex }
            finder.add(item, out.lastIndex)
            continue
        }
        val first = out[hit]
        val firstStays = first.item.id in shown || (item.id !in shown && StoryTwins.quality(item) <= StoryTwins.quality(first.item))
        val (winner, twin) = if (firstStays) first to article else article to first
        out[hit] = winner.copy(item = StoryTwins.withTwin(winner.item, twin.item))
        if (!firstStays) twins.keys.filter { twins[it] == twin.item.id }.forEach { twins[it] = winner.item.id }
        twins[twin.item.id] = winner.item.id
        headline?.let { headlines.putIfAbsent(it, hit) }
    }
    return Stories(out, twins)
}

/** A topic's list always shows its newest stories, however little of the topic has been read. */
internal const val ALWAYS_LISTED = PAGE_SIZE

/**
 * A topic's own list while the topic can still page: its stories ([items], newest first) as far
 * back as it has been read ([depth], [pagedDepth]). What is older waits – the tail of a
 * publisher's feed, a stray old hit of Google News' first page: the topic's older pages are the
 * days in between, and they would land above it, in the middle of what is on screen. The newest
 * [atLeast] stories and the ones since [floor] (the last day) never wait, undated ones neither
 * (their place is the feed's), and what the list [shown] before stays.
 */
internal fun readSoFar(
    items: List<NewsItem>,
    depth: Instant,
    floor: Instant,
    shown: Collection<String> = emptyList(),
    atLeast: Int = ALWAYS_LISTED,
): List<NewsItem> {
    val known = shown as? Set<String> ?: shown.toHashSet()
    return items.filterIndexed { index, item ->
        if (index < atLeast || item.id in known) return@filterIndexed true
        val at = parseInstantOrNull(item.publishedAt) ?: return@filterIndexed true
        at >= depth || at >= floor
    }
}

/**
 * [sorted] (a list's stories, newest first) as the list goes on from what it [shown] before: the
 * stories it showed keep their order, everything new follows them (newest first). Older pages
 * overlap the days already on screen – a window starts on the day the list ends, Google News
 * dates a story a little differently than the publisher – and sorted in by date they would push
 * the stories on screen down. With nothing shown before: [sorted] as it is.
 */
internal fun <T> appendOnly(sorted: List<T>, shown: List<String>, id: (T) -> String): List<T> {
    if (shown.isEmpty() || sorted.isEmpty()) return sorted
    val byId = HashMap<String, T>(sorted.size * 2)
    sorted.forEach { byId[id(it)] = it }
    val kept = shown.mapNotNull(byId::get)
    if (kept.isEmpty()) return sorted
    if (kept.size == sorted.size) return kept
    val known = kept.mapTo(HashSet(kept.size * 2), id)
    return kept + sorted.filter { id(it) !in known }
}

/**
 * What the endless lists listed at the last build, per list (a topic's id, [TIMELINE_LIST]): the
 * ids in order – the candidates, verified or not. The next build goes on from it ([appendOnly]),
 * as long as the list still comes from the same fetch ([stamps]): a refresh brings new stories
 * for the top and sorts the list afresh.
 */
@Immutable
internal class FeedOrder(
    private val stamps: Map<String, String> = emptyMap(),
    private val ids: Map<String, List<String>> = emptyMap(),
) {
    /** What [list] listed, in order – nothing when it was listed from another fetch than [stamp]. */
    fun of(list: String, stamp: String): List<String> = if (stamps[list] == stamp) ids[list].orEmpty() else emptyList()

    /** Collects the order of the lists of one build. */
    class Builder {
        private val stamps = HashMap<String, String>()
        private val ids = HashMap<String, List<String>>()

        fun put(list: String, stamp: String, order: List<String>) {
            stamps[list] = stamp
            ids[list] = order
        }

        fun build(): FeedOrder = FeedOrder(stamps, ids)
    }

    companion object {
        val NONE = FeedOrder()
    }
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

package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import com.cedrickgd.morgenbriefing.i18n.AppLanguage

/*
 * The feed's decisions as pure functions (unit-tested): what "readable only" hides, which
 * articles are warmed ahead, when an endless list reveals or loads more, which topics the
 * feed offers to follow and which teaser a card shows.
 */

/**
 * The teaser a card shows under [headline]: [summary], unless it only repeats the headline.
 * Publishers' feeds often put the title into the description as well (tagesschau: the same
 * sentence twice on the card); compared without case, accents and punctuation.
 *
 * - the same text as the headline, or a cut-off part of it: no teaser;
 * - the headline first, then more: only what follows it ([MIN_TEASER_REST] letters at least,
 *   else no teaser – "Headline – tagesschau.de" is no teaser either);
 * - anything else: [summary] as it is.
 */
internal fun cardTeaser(headline: String, summary: String): String {
    val text = summary.trim()
    if (text.isEmpty()) return ""
    val head = NewsRepository.normalizeText(headline)
    if (head.isEmpty()) return text
    val body = NewsRepository.normalizeText(text)
    if (body.isEmpty() || head.startsWith(body)) return ""
    if (!body.startsWith(head)) return text
    // Find the word that completes the headline and keep what comes after it.
    val words = text.split(WHITESPACE)
    var seen = ""
    for ((index, word) in words.withIndex()) {
        val normalized = NewsRepository.normalizeText(word)
        if (normalized.isNotEmpty()) seen = if (seen.isEmpty()) normalized else "$seen $normalized"
        if (seen == head) {
            val rest = words.drop(index + 1).joinToString(" ").trimStart { !it.isLetterOrDigit() && it != '„' && it != '"' && it != '(' }
            return if (NewsRepository.normalizeText(rest).length >= MIN_TEASER_REST) rest else ""
        }
        if (!head.startsWith(seen)) break
    }
    // The headline ends inside a word ("Midterms" vs. "Midterms-Wahl"): no clean cut, keep it all.
    return text
}

/** Letters a teaser must add to the headline it starts with. */
private const val MIN_TEASER_REST = 20

private val WHITESPACE = Regex("\\s+")

/**
 * What "nur lesbare Artikel" hides, frozen at one moment (a refresh): the sites the reader cannot
 * show and the articles the prefetcher found behind a wall. The feed takes a new snapshot only
 * when it is rebuilt for a refresh, so nothing disappears under the user's finger while scrolling.
 *
 * @property domains sites without `www.` ([KnownWalls] plus what the phone learned).
 * @property itemIds articles whose prefetch ended in [ArticleStatus.PAYWALL] or [ArticleStatus.BLOCKED].
 */
@Immutable
data class ReadabilityHides(val domains: Set<String> = emptySet(), val itemIds: Set<String> = emptySet()) {
    companion object {
        val NONE = ReadabilityHides()

        /** A snapshot of [blocked] sites and the walled articles among [statuses]. */
        fun of(blocked: Collection<String>, statuses: Map<String, ArticleStatus>): ReadabilityHides =
            ReadabilityHides(
                domains = blocked.toSet(),
                itemIds = statuses.filterValues { it == ArticleStatus.PAYWALL || it == ArticleStatus.BLOCKED }.keys,
            )
    }
}

/**
 * The publisher's host of [item]: Google News' `<source url>` when known, else the link's host —
 * except for aggregator links (news.google.com), which name no publisher.
 */
internal fun publisherHost(item: NewsItem): String =
    item.publisherDomain.trim().lowercase().removePrefix("www.").ifEmpty {
        if (item.aggregator != null) "" else hostOf(item.url)
    }

/** True when [hides] says the reader cannot show [item]. */
internal fun isHidden(item: NewsItem, hides: ReadabilityHides): Boolean {
    if (item.id in hides.itemIds) return true
    val host = publisherHost(item)
    return host.isNotEmpty() && hides.domains.isNotEmpty() && KnownWalls.matches(host, hides.domains)
}

/** [items] without the ones [hides] rules out; everything when [readableOnly] is off. */
internal fun readableItems(items: List<NewsItem>, hides: ReadabilityHides, readableOnly: Boolean): List<NewsItem> =
    if (!readableOnly || (hides.domains.isEmpty() && hides.itemIds.isEmpty())) items else items.filterNot { isHidden(it, hides) }

/** The small readability hint a feed card shows, if any. */
enum class CardBadge { FULL_TEXT, PAYWALL, BROWSER_ONLY }

/** The hint for an article's prefetch [status]; nothing while unknown, loading or failed. */
internal fun cardBadge(status: ArticleStatus): CardBadge? = when (status) {
    ArticleStatus.READABLE -> CardBadge.FULL_TEXT
    ArticleStatus.PAYWALL -> CardBadge.PAYWALL
    ArticleStatus.BLOCKED -> CardBadge.BROWSER_ONLY
    ArticleStatus.UNKNOWN, ArticleStatus.LOADING, ArticleStatus.FAILED -> null
}

/** Articles warmed beyond the last one on screen. */
internal const val PREFETCH_AHEAD = 4

/**
 * The articles to warm now: the ones on screen ([visibleIds]) in feed order, then the next
 * [ahead] after the last visible one. Empty when none is on screen. Duplicates (the same story
 * in two sections) count once.
 *
 * @param orderedIds the article ids of the current view, top to bottom.
 */
internal fun prefetchWindow(orderedIds: List<String>, visibleIds: Collection<String>, ahead: Int = PREFETCH_AHEAD): List<String> {
    if (visibleIds.isEmpty() || orderedIds.isEmpty()) return emptyList()
    val visible = visibleIds.toHashSet()
    val first = orderedIds.indexOfFirst { it in visible }
    if (first < 0) return emptyList()
    val last = orderedIds.indexOfLast { it in visible }
    val window = LinkedHashSet<String>()
    for (i in first..last) if (orderedIds[i] in visible) window += orderedIds[i]
    var added = 0
    var i = last + 1
    while (i < orderedIds.size && added < ahead) {
        if (window.add(orderedIds[i])) added++
        i++
    }
    return window.toList()
}

private const val ENTRY_KEY_PREFIX = "entry|"

/**
 * The lazy-list key of a feed entry: unique per view ([prefix]) and position, and readable back
 * to the article id ([entryItemId]) so the screen knows which articles are on screen.
 */
internal fun entryKey(prefix: String, index: Int, itemId: String): String = "$ENTRY_KEY_PREFIX$prefix|$index|$itemId"

/** The article id inside a key made by [entryKey]; null for every other list item. */
internal fun entryItemId(key: Any?): String? {
    val text = key as? String ?: return null
    if (!text.startsWith(ENTRY_KEY_PREFIX)) return null
    val parts = text.removePrefix(ENTRY_KEY_PREFIX).split('|', limit = 3)
    return parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
}

/** What an endless list does when its end comes into view. */
enum class EndAction {
    NONE,

    /** Show the next page of the articles already in memory. */
    REVEAL,

    /** Ask the repository for older articles ([com.cedrickgd.morgenbriefing.data.NewsRepository.loadMore]). */
    LOAD_MORE,
}

/**
 * The next step of an endless list whose end is near: reveal what is already loaded first; only
 * once everything is on screen ask for older articles — when the topic can page ([canLoadMore]),
 * no load is running ([loadingMore]) and the last load did not come back empty ([stalled]; then
 * the user asks again by tapping, so a dead network is never hammered). An empty list waits for
 * its refresh instead of paging.
 */
internal fun endAction(revealed: Int, total: Int, canLoadMore: Boolean, loadingMore: Boolean, stalled: Boolean): EndAction = when {
    revealed < total -> EndAction.REVEAL
    total > 0 && canLoadMore && !loadingMore && !stalled -> EndAction.LOAD_MORE
    else -> EndAction.NONE
}

/** What the end of a topic's list shows. */
enum class ListTail { REVEALING, LOADING_OLDER, STALLED, END }

/** The tail of a topic list: still revealing, loading older articles, a retry after an empty load, or the end. */
internal fun listTail(revealed: Int, total: Int, canLoadMore: Boolean, loadingMore: Boolean, stalled: Boolean): ListTail = when {
    revealed < total -> ListTail.REVEALING
    loadingMore -> ListTail.LOADING_OLDER
    canLoadMore && stalled -> ListTail.STALLED
    canLoadMore -> ListTail.LOADING_OLDER
    else -> ListTail.END
}

/**
 * True when the ⋮ menu may offer to switch off the source of [item]. Not for Google News
 * articles: their source is the topic's whole Google News feed, not the publisher named on them.
 */
internal fun canHideSource(item: NewsItem): Boolean =
    item.aggregator == null && !item.sourceId.startsWith(GOOGLE_NEWS_SOURCE_PREFIX) && item.sourceId.isNotBlank()

/** Ids of Google News sources ([com.cedrickgd.morgenbriefing.data.news.TopicSources.googleNewsId]). */
private const val GOOGLE_NEWS_SOURCE_PREFIX = "gn:"

/** True when [id] may be unfollowed: the last followed topic stays (as [com.cedrickgd.morgenbriefing.data.SettingsOps.canUnfollow]). */
internal fun canUnfollow(followedIds: List<String>, id: String): Boolean = id !in followedIds || followedIds.size > 1

/**
 * The sub-topic an article of a category section really belongs to, when the user does not
 * follow it yet ("„Börse & Märkte“ folgen" in the ⋮ menu of a Wirtschaft article); null when
 * the article's own topic is the section's or is followed already.
 */
internal fun followSuggestion(article: FeedArticle, followedIds: Collection<String>, lang: AppLanguage): Topic? {
    val own = article.item.topicId
    if (own == article.topic.id || own in followedIds) return null
    val def = Taxonomy.byId(own) ?: return null
    if (def.parentId == null || def.parentId != article.topic.id) return null
    return Topics.builtIn(own, lang)
}

/** Fewer followed topics than this: the feed invites to discover more at its top. */
internal const val FEW_TOPICS = 4

/** True when the feed should invite to follow more topics. */
internal fun invitesToDiscover(followedCount: Int): Boolean = followedCount < FEW_TOPICS

/** Suggestions shown at the end of "Für dich". */
internal const val MAX_SUGGESTIONS = 8

/**
 * Categories worth suggesting: the ones the user follows neither directly nor through one of
 * their sub-topics, in catalogue order, at most [max].
 */
internal fun discoverSuggestions(categories: List<Topic>, followedIds: Collection<String>, max: Int = MAX_SUGGESTIONS): List<Topic> {
    val followed = followedIds.toHashSet()
    val parentsFollowed = followedIds.mapNotNullTo(HashSet()) { Taxonomy.byId(it)?.parentId }
    return categories.filter { it.id !in followed && it.id !in parentsFollowed }.take(max)
}

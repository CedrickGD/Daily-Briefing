package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.util.parseInstantOrNull
import java.time.Duration
import java.time.Instant

/**
 * The order of search results: most relevant first, not newest first – a weakly related story
 * from this morning must not push the stories about the query down. Pure, unit-tested.
 *
 * Each result gets a score from how it meets the [SearchQuery] ([SearchQuery.match]): the
 * words in the headline count most (a ticker twice: "SLV" tells an SLV story from any
 * silver-ETF story), all of them there more, the query as a phrase there and a ticker there
 * more again; words only in the teaser or the publisher's name count less. Google News' own
 * order is a prior (its first hit gets the most), the freshness a mild factor, the main
 * edition a small edge over the English ones. Pages older than a year come after the fresh
 * results (hardly news: quote pages and explainers from 2017 that name "SLV ETF" in full), and
 * results that share no word of the query at all (Google matched the article's body, which the
 * app does not see) come last.
 *
 * Dropped are only results that are certainly no answer: quote and product pages
 * ([GoogleNews.isQuotePage]), forums ([FORUMS]), results with an excluded word,
 * English-edition results that share no word with the query or are older than a year (the
 * extra editions are breadth, not the place for guesses), and main-edition results that share
 * no word and either are older than a year or come with a teaser (so the app saw more than the
 * headline) beyond Google's first [TOP_KEEP] hits.
 */
object SearchRanking {

    /** One edition's answer to a search, in Google News' order. */
    class Found(val items: List<NewsItem>, val main: Boolean)

    /** Google's first this many hits stay even when they share no word with the query. */
    const val TOP_KEEP = 10

    /** Older than this (days) is no news any more. */
    const val STALE_DAYS = 365L

    private const val HEADLINE = 32.0
    private const val TEXT = 12.0
    private const val ALL_IN_HEADLINE = 14.0
    private const val PHRASE = 10.0
    private const val TICKER = 8.0
    private const val PUBLISHER = 4.0
    private const val PRIOR = 16.0
    private const val CACHED_PRIOR = 8.0
    private const val MAIN_EDITION = 3.0

    /** Near-identical headlines: at least this share of their words in common ([dropNearDuplicates]). */
    private const val NEAR_SHARE = 0.75

    /** Headlines with fewer words are too short to call near-identical. */
    private const val NEAR_MIN_WORDS = 6

    /**
     * [found] (per edition, in Google's order) and [cached] (articles on the phone that match
     * the whole query, see [SearchQuery.matchesAll]) as one list, most relevant first, without
     * what is certainly no answer (see the class description). Duplicates stay for the caller
     * to fold (it knows resolved links).
     */
    fun rank(query: SearchQuery, cached: List<NewsItem>, found: List<Found>, now: Instant): List<NewsItem> {
        val scored = ArrayList<Scored>()
        var order = 0
        for (list in found) {
            list.items.forEachIndexed { index, item ->
                score(query, item, Position(index, list.items.size), list.main, now)?.let { scored += it.copy(order = order) }
                order++
            }
        }
        for (item in cached) {
            score(query, item, position = null, main = true, now = now)?.let { scored += it.copy(order = order) }
            order++
        }
        return scored
            .sortedWith(compareBy<Scored> { it.tier }.thenByDescending { it.score }.thenBy { it.order })
            .map { it.item }
    }

    /** Where a result stood in Google News' list of [size] results. */
    internal data class Position(val index: Int, val size: Int)

    /** A result with its rank: lower [tier] first ([TIER_RELATED], [TIER_STALE], [TIER_UNRELATED]), then higher [score]. */
    internal data class Scored(val item: NewsItem, val tier: Int, val score: Double, val order: Int = 0)

    /**
     * How [item] ranks for [query]; null when it is certainly no answer. [position] is its place
     * in Google News' answer (null: a cached article), [main] whether it came from the main
     * edition.
     */
    internal fun score(query: SearchQuery, item: NewsItem, position: Position?, main: Boolean, now: Instant): Scored? {
        if (GoogleNews.isQuotePage(item.headline) || isForum(item.publisherDomain)) return null
        val match = query.match(item.headline, item.summary, publisherText(item))
        if (match.excluded) return null
        val age = ageInDays(item.publishedAt, now)
        val stale = age != null && age > STALE_DAYS
        val related = !query.hasTerms || match.sharesAnyTerm
        if (position != null) {
            if (!main && (!related || stale)) return null
            if (main && !related && (stale || (item.summary.isNotBlank() && position.index >= TOP_KEEP))) return null
        }
        val weight = match.weight.coerceAtLeast(1).toDouble()
        var score = HEADLINE * match.weightInHeadline / weight
        score += TEXT * (match.weightInText - match.weightInHeadline) / weight
        if (match.groups >= 2 && match.inHeadline == match.groups) score += ALL_IN_HEADLINE
        if (match.phraseInHeadline) score += PHRASE
        score += TICKER * match.tickersInHeadline.coerceAtMost(2)
        if (match.inPublisher > 0) score += PUBLISHER
        score += if (position == null) CACHED_PRIOR else PRIOR * (1.0 - position.index.toDouble() / position.size.coerceAtLeast(1))
        score += freshness(age)
        if (main) score += MAIN_EDITION
        val tier = when {
            !related -> TIER_UNRELATED
            stale -> TIER_STALE
            else -> TIER_RELATED
        }
        return Scored(item, tier = tier, score = score)
    }

    /** Results that share a word with the query come first, those older than [STALE_DAYS] after them, those that share none last. */
    private const val TIER_RELATED = 0
    private const val TIER_STALE = 1
    private const val TIER_UNRELATED = 2

    /** Forums and social sites Google News lists among the results: discussions, not news ("Who else buys the SLV ETF?"). */
    private val FORUMS = listOf("reddit.com", "quora.com", "stocktwits.com", "x.com", "twitter.com", "facebook.com", "instagram.com", "tiktok.com")

    private fun isForum(domain: String): Boolean {
        val host = domain.lowercase().removePrefix("www.")
        return host.isNotEmpty() && FORUMS.any { host == it || host.endsWith(".$it") }
    }

    /**
     * The articles of a followed search's English edition worth showing in the feed: those that
     * share a word with [query] and are at most a year old (see the class description).
     */
    fun relevantExtra(query: SearchQuery, items: List<NewsItem>, now: Instant): List<NewsItem> =
        items.filter { item ->
            val match = query.match(item.headline, item.summary, publisherText(item))
            val age = ageInDays(item.publishedAt, now)
            !match.excluded && (!query.hasTerms || match.sharesAnyTerm) && (age == null || age <= STALE_DAYS)
        }

    /**
     * [items] without the later of two near-identical headlines (the same story from two
     * editions or publishers, worded a little differently): at least [NEAR_SHARE] of the words
     * of both in common, both at least [NEAR_MIN_WORDS] words long.
     */
    fun dropNearDuplicates(items: List<NewsItem>): List<NewsItem> {
        val kept = ArrayList<NewsItem>(items.size)
        val words = ArrayList<Set<String>>(items.size)
        for (item in items) {
            val own = SearchQuery.fold(item.headline).split(' ').filter { it.isNotEmpty() }.toSet()
            val near = own.size >= NEAR_MIN_WORDS && words.any { other -> other.size >= NEAR_MIN_WORDS && share(own, other) >= NEAR_SHARE }
            if (!near) {
                kept += item
                words += own
            }
        }
        return kept
    }

    /**
     * The mild freshness bonus: +12 today, falling to 0 after three months; a page older than a
     * year gets −12 (a stray old hit, an evergreen explainer). Undated: 0.
     */
    internal fun freshness(ageDays: Long?): Double = when {
        ageDays == null -> 0.0
        ageDays <= 1 -> 12.0
        ageDays <= 3 -> 10.0
        ageDays <= 7 -> 8.0
        ageDays <= 30 -> 5.0
        ageDays <= 90 -> 2.0
        ageDays <= STALE_DAYS -> 0.0
        else -> -12.0
    }

    private fun ageInDays(publishedAt: String?, now: Instant): Long? =
        parseInstantOrNull(publishedAt)?.let { Duration.between(it, now).toDays().coerceAtLeast(0) }

    private fun publisherText(item: NewsItem): String = item.source + " " + item.publisherDomain

    /** Words in common over all words of both, endings tolerated ("Crash" ~ "Crashs", [SearchQuery.wordMatches]). */
    private fun share(a: Set<String>, b: Set<String>): Double {
        val common = a.count { word -> word in b || b.any { SearchQuery.wordMatches(word, it) } }
        val all = a.size + b.size - common
        return if (all <= 0) 0.0 else common.toDouble() / all
    }
}

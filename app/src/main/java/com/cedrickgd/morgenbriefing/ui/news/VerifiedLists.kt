package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem

/*
 * "Nur vollständige Artikel" as pure functions (unit-tested): which candidates a list shows (only
 * the verified ones, appended in order, never taken away before the next build) and which
 * candidates are verified next (in list order, ahead of the position on screen).
 */

/** What "nur vollständige Artikel" knows about one candidate article. */
enum class Check {
    /** Verified complete (or shown since the last build): it shows. */
    VERIFIED,

    /** A paywall, a teaser, a wall or no article: it never shows. */
    REJECTED,

    /** Not judged yet: it waits for its check. */
    PENDING,
}

/**
 * The verification state lists are built with.
 *
 * @property enabled "nur vollständige Artikel" is on; off, every candidate shows (as before).
 * @property statuses the prefetcher's verdicts by item id
 *   ([com.cedrickgd.morgenbriefing.data.article.ArticlePrefetcher.status]).
 * @property kept per list (its [verifiedList] key): the articles it showed since the last build.
 *   They stay until the next build, whatever their status says now, so nothing disappears under
 *   the user's finger.
 */
@Immutable
data class Verification(
    val enabled: Boolean,
    val statuses: Map<String, ArticleStatus> = emptyMap(),
    val kept: Map<String, Set<String>> = emptyMap(),
) {
    /** What [list] may do with the article [id]. */
    fun check(list: String, id: String): Check = when {
        !enabled -> Check.VERIFIED
        kept[list]?.contains(id) == true -> Check.VERIFIED
        else -> checkOf(statuses[id])
    }

    companion object {
        val OFF = Verification(enabled = false)

        /** The [Check] an [ArticleStatus] stands for (a network failure counts as rejected until it is tried again). */
        fun checkOf(status: ArticleStatus?): Check = when (status) {
            ArticleStatus.READABLE -> Check.VERIFIED
            ArticleStatus.PAYWALL, ArticleStatus.BLOCKED, ArticleStatus.FAILED -> Check.REJECTED
            null, ArticleStatus.UNKNOWN, ArticleStatus.LOADING -> Check.PENDING
        }

        /** Only the final verdicts of [statuses] – loading and unknown say nothing for the lists (fewer rebuilds). */
        fun settled(statuses: Map<String, ArticleStatus>): Map<String, ArticleStatus> =
            statuses.filterValues { it != ArticleStatus.LOADING && it != ArticleStatus.UNKNOWN }
    }
}

/** A list under "nur vollständige Artikel": what it shows and the candidates still waiting for their check (in order). */
@Immutable
data class VerifiedList<T>(val shown: List<T>, val waiting: List<T>)

/**
 * Splits [candidates] (in list order) for the list [key]: the verified ones show, in order; the
 * first candidate still pending stops the list there – later ones show once it is judged, so
 * articles are only ever appended at the end and nothing shifts. A pending candidate above the
 * articles the list showed before ([Verification.kept], new stories on top after a refresh) does
 * not stop it: it shows in its place once it is verified. Rejected ones never show. Everything
 * shows when [Verification.enabled] is off.
 */
internal fun <T> verifiedList(candidates: List<T>, key: String, verification: Verification, id: (T) -> String): VerifiedList<T> {
    if (!verification.enabled) return VerifiedList(candidates, emptyList())
    val kept = verification.kept[key].orEmpty()
    val frontier = if (kept.isEmpty()) -1 else candidates.indexOfLast { id(it) in kept }
    val shown = ArrayList<T>()
    val waiting = ArrayList<T>()
    var stopped = false
    candidates.forEachIndexed { index, candidate ->
        when (verification.check(key, id(candidate))) {
            Check.VERIFIED -> if (!stopped) shown += candidate
            Check.REJECTED -> Unit
            Check.PENDING -> {
                waiting += candidate
                if (index > frontier) stopped = true
            }
        }
    }
    return VerifiedList(shown, waiting)
}

/** The list key of "Neueste" (topic lists use their topic id). */
internal const val LATEST_LIST = "~latest"

/** Candidates verified beyond the last article on screen of an endless list. */
internal const val VERIFY_AHEAD = 20

/** A few more than strictly missing keep every load slot busy (some candidates turn out to be teasers). */
internal const val VERIFY_EXTRA = 3

/** Fewer unverified candidates than this left at the end of an endless list: ask for older articles. */
internal const val VERIFY_LOW_WATER = 10

/**
 * What an endless list verifies next: when fewer than [ahead] verified articles lie below the
 * last one on screen, the next waiting candidates (in list order) – as many as are missing plus
 * [extra], at most [ahead]. Nothing while enough are ready, so an untouched list does not verify
 * itself to the end.
 *
 * @param shownIds the ids the list shows, top to bottom.
 * @param visibleIds the ids on screen now.
 */
internal fun verifyAhead(
    shownIds: List<String>,
    visibleIds: Collection<String>,
    waiting: List<NewsItem>,
    ahead: Int = VERIFY_AHEAD,
    extra: Int = VERIFY_EXTRA,
): List<NewsItem> {
    if (waiting.isEmpty()) return emptyList()
    val visible = visibleIds.toHashSet()
    val last = shownIds.indexOfLast { it in visible }
    val missing = ahead - (shownIds.size - (last + 1))
    return if (missing <= 0) emptyList() else waiting.take(minOf(ahead, missing + extra))
}

/**
 * What "Für dich" verifies next: for every section that shows fewer than [size] articles its
 * next waiting candidates (as many as are missing, plus [extra]) – the section on screen first,
 * then the ones below, then the ones above (closest first); each article once, at most [cap].
 *
 * @param firstVisible index of the topmost section on screen (0 when unknown).
 */
internal fun forYouVerifyWindow(
    sections: List<SectionUi>,
    size: Int,
    firstVisible: Int,
    cap: Int = VERIFY_AHEAD,
    extra: Int = 1,
): List<NewsItem> {
    val start = firstVisible.coerceIn(0, (sections.size - 1).coerceAtLeast(0))
    val order = (start until sections.size) + (start - 1 downTo 0)
    val out = LinkedHashMap<String, NewsItem>()
    for (i in order) {
        val section = sections.getOrNull(i) ?: continue
        val missing = size - section.top.size
        if (missing <= 0) continue
        section.topWaiting.take(missing + extra).forEach { out.putIfAbsent(it.id, it) }
        if (out.size >= cap) break
    }
    return out.values.take(cap)
}

private val SECTION_KEY = Regex("foryou-s(\\d+)-")

/** The section index inside a "Für dich" list key (`foryou-s3-…`), null for other keys. */
internal fun forYouSectionOf(key: Any?): Int? =
    (key as? String)?.let { SECTION_KEY.find(it)?.groupValues?.get(1)?.toIntOrNull() }

/** Every list keeps what it shows in [feed] (on top of what it [kept] before). */
internal fun keptAfter(kept: Map<String, Set<String>>, feed: NewsFeed): Map<String, Set<String>> {
    val out = HashMap(kept)
    fun keep(key: String, ids: List<String>) {
        if (ids.isEmpty()) return
        val before = out[key].orEmpty()
        if (before.containsAll(ids)) return
        out[key] = before + ids
    }
    feed.sections.forEach { keep(it.id, it.all.map(NewsItem::id)) }
    keep(LATEST_LIST, feed.latest.map { it.item.id })
    keep(TIMELINE_LIST, feed.timeline.map { it.item.id })
    return out
}

/** At a new build (fresh news, other settings): only what is still verified stays kept – removals happen now. */
internal fun keptForBuild(kept: Map<String, Set<String>>, statuses: Map<String, ArticleStatus>): Map<String, Set<String>> =
    kept.mapValues { (_, ids) -> ids.filterTo(HashSet()) { statuses[it] == ArticleStatus.READABLE } }.filterValues { it.isNotEmpty() }

package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsAge
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.PrefetchMode
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.util.parseInstantOrNull
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** A topic keeps at least this many articles on screen even when the age filter leaves fewer. */
internal const val MIN_PER_TOPIC = 3

/**
 * The user's feed settings that shape what the news screens show, resolved for the interface
 * [language] (titles and subtitles of built-in topics follow it).
 */
@Immutable
data class FeedPrefs(
    /** Followed topics in the user's order. */
    val topics: List<Topic>,
    /** The interface language the titles and subtitles are in. */
    val language: AppLanguage,
    val sectionSize: Int = AppSettings.DEFAULT_SECTION_SIZE,
    val newsAge: NewsAge = NewsAge.DAY,
    val showImages: Boolean = true,
    /** Articles open in the app's reader; false: in the browser. */
    val readInApp: Boolean = true,
    /** Every topic the user has, unfollowed ones included (for the topic sheet). */
    val catalogue: List<Topic> = topics,
    val hidden: Set<String> = emptySet(),
    /** Sources the user switched off; their articles never show, even before the next fetch. */
    val disabledFeeds: Set<String> = emptySet(),
    /** The followed topic ids in order ([AppSettings.followed]). */
    val followedIds: List<String> = topics.map { it.id },
    /** One line per topic id on what it covers (followed topics and [suggestions]). */
    val subtitles: Map<String, String> = emptyMap(),
    /** Followed topics whose list can page into the past ([com.cedrickgd.morgenbriefing.data.NewsRepository.loadMore]). */
    val pageable: Set<String> = emptySet(),
    /** Hide articles the in-app reader cannot show. */
    val readableOnly: Boolean = true,
    /** "Gelesene Artikel ausblenden": read stories leave the lists when they are built anew ([ReadFilter]). */
    val hideRead: Boolean = false,
    /** "Lange Artikel ausblenden": live blogs and articles measured long leave the lists ([shortItems]). */
    val hideLong: Boolean = true,
    val prefetch: PrefetchMode = PrefetchMode.WIFI_ONLY,
    /** Language of the news edition (`de`), for the prefetcher's `Accept-Language`. */
    val editionLanguage: String = "",
    /** Categories the user does not follow yet, for "Mehr entdecken". */
    val suggestions: List<Topic> = emptyList(),
) {
    constructor(settings: AppSettings) : this(settings, settings.language)

    private constructor(settings: AppSettings, lang: AppLanguage) : this(
        topics = Topics.visible(settings, lang),
        language = lang,
        sectionSize = settings.sectionSize,
        newsAge = settings.newsAge,
        showImages = settings.showImages,
        readInApp = settings.readInApp,
        catalogue = Topics.catalogue(settings, lang),
        hidden = Topics.hidden(settings),
        disabledFeeds = settings.disabledFeeds,
        followedIds = Topics.followedIds(settings).distinct(),
        subtitles = subtitlesFor(settings, lang),
        pageable = Topics.followedIds(settings).filterTo(HashSet()) { TopicSources.pagingQuery(it, settings) != null },
        readableOnly = settings.readableOnly,
        hideRead = settings.hideRead,
        hideLong = settings.hideLong,
        prefetch = settings.prefetch,
        editionLanguage = settings.edition.language,
        suggestions = suggestionsFor(settings, lang),
    )

    private companion object {
        fun suggestionsFor(settings: AppSettings, lang: AppLanguage): List<Topic> {
            val categories = Taxonomy.CATEGORIES.mapNotNull { Topics.resolve(it.id, settings, lang) }
            return discoverSuggestions(categories, Topics.followedIds(settings))
        }

        fun subtitlesFor(settings: AppSettings, lang: AppLanguage): Map<String, String> =
            (Topics.visible(settings, lang) + suggestionsFor(settings, lang))
                .associate { it.id to Topics.subtitle(it, settings, lang) }
                .filterValues { it.isNotBlank() }
    }
}

/** One article as the feed shows it, with the topic it is shown under. */
@Immutable
data class FeedArticle(val item: NewsItem, val topic: Topic) {
    val feedKey: String get() = item.key
}

/**
 * One topic of the feed, ready to render: [top] opens "Für dich", [all] is what the topic's
 * own chip scrolls through (endless, newest first, older pages appended by load-more).
 *
 * @property index position in the arranged list — keeps lazy-list keys unique.
 */
@Immutable
data class SectionUi(
    val index: Int,
    val topic: Topic,
    val all: List<NewsItem>,
    val top: List<NewsItem>,
    /** When these articles were fetched (ISO-8601) or null when the topic was never fetched. */
    val fetchedAt: String?,
    /** Sources that failed last time, by name. */
    val failedSources: List<String> = emptyList(),
    /** One line on what the topic covers; may be empty. */
    val subtitle: String = "",
    /** A category with sub-topics ("Unterthemen" lead to the discover screen). */
    val browsable: Boolean = false,
    /**
     * "Nur vollständige Artikel": the topic's candidates still waiting for their check, in list
     * order ([verifiedList]) – what its own endless list verifies next.
     */
    val waiting: List<NewsItem> = emptyList(),
    /** Of [waiting], the ones that could still fill [top] ("Für dich" verifies these). */
    val topWaiting: List<NewsItem> = emptyList(),
    /** Articles the topic has before verification (minus switched-off sources and walled sites). */
    val candidates: Int = all.size,
    /**
     * Articles the list leaves out because the user hid them, or read them with "Gelesene Artikel
     * ausblenden" ([ReadFilter]) – they still count for paging, so a list of read stories pages on.
     */
    val heldBack: Int = 0,
) {
    val id: String get() = topic.id
    val count: Int get() = all.size
    val hasMore: Boolean get() = all.size > top.size
    val isEmpty: Boolean get() = all.isEmpty()

    /** [top] is not full yet and candidates for it are being checked. */
    val checking: Boolean get() = topWaiting.isNotEmpty()

    /** Candidates were there, none of them is a complete article (and none is left to check). */
    val noneComplete: Boolean get() = all.isEmpty() && waiting.isEmpty() && candidates > 0
}

/** Everything the news part of "Heute" shows. */
@Immutable
data class NewsFeed(
    /** The followed topics in the user's order (also the ones without articles). */
    val sections: List<SectionUi>,
    /** The fresh articles of every followed topic, newest first, each story once ("Neueste"). */
    val latest: List<FeedArticle>,
    /** The newest fetch time over all followed topics. */
    val fetchedAt: Instant?,
    /** "Nur vollständige Artikel" is on: the lists show verified articles only. */
    val verifying: Boolean = false,
    /** "Neueste" candidates still waiting for their check, in list order. */
    val latestWaiting: List<NewsItem> = emptyList(),
    /** "Neueste" candidates before verification. */
    val latestCandidates: Int = latest.size,
    /**
     * "Neueste" as an endless list ([timelineOf]): every story the followed topics hold, newest
     * first, as far back as all topics that can still page have been read.
     */
    val timeline: List<FeedArticle> = latest,
    /** [timeline] candidates still waiting for their check, in list order. */
    val timelineWaiting: List<NewsItem> = latestWaiting,
    /** [timeline] candidates before verification. */
    val timelineCandidates: Int = latestCandidates,
    /** [timeline] without the stories the "Für dich" cards show: what "Für dich" goes on with below them. */
    val more: List<FeedArticle> = emptyList(),
    /** The topics to ask for older articles when [timeline] ends ([nextToPage]); empty: nothing older can come. */
    val olderNext: List<String> = emptyList(),
    /** [timeline] stories left out as hidden or read ([ReadFilter]); they still count for paging. */
    val timelineHeldBack: Int = 0,
) {
    val isEmpty: Boolean get() = latest.isEmpty()
    val articleCount: Int get() = latest.size

    /** Topics that have something to show. */
    val filledSections: List<SectionUi> get() = sections.filter { !it.isEmpty }

    /** Some list still has candidates waiting for their check. */
    val checking: Boolean get() = latestWaiting.isNotEmpty() || sections.any { it.waiting.isNotEmpty() }

    /** There were articles, but not one of them is complete (and nothing is left to check). */
    val noneComplete: Boolean get() = verifying && latest.isEmpty() && !checking && (latestCandidates > 0 || sections.any { it.candidates > 0 })
}

/**
 * Builds the feed for the followed topics of [prefs] from the repository's [news]. Pure.
 *
 * - A topic's own list ([SectionUi.all]) holds everything it has (minus switched-off sources,
 *   with [FeedPrefs.readableOnly] what [hides] rules out, with [FeedPrefs.hideLong] live blogs
 *   and the long reads measured by the last refresh) – older pages included, so the list
 *   really is endless. "Für dich" and "Neueste" keep to [FeedPrefs.newsAge].
 * - The same story often sits in several followed topics (a category also reads its
 *   sub-topics' feeds): "Für dich" shows it in the first section only, "Neueste" once, under the
 *   most specific topic (its own topic, else the sub-topic rather than its category).
 *
 * - With [FeedPrefs.readableOnly] every list shows only the articles [verification] says are
 *   verified complete ([verifiedList]): appended in list order as they are judged, never taken
 *   away before the next build. Section sizes count verified articles; what still waits for its
 *   check is listed per topic ([SectionUi.waiting], [SectionUi.topWaiting]) and for "Neueste"
 *   ([NewsFeed.latestWaiting]).
 *
 * - The age setting is no dead end: [NewsFeed.timeline] is what the "Neueste" tab scrolls
 *   through – everything, as far back as the topics have been read ([timelineOf]) – and
 *   [NewsFeed.olderNext] names the topics to page next.
 *
 * - The endless lists only grow at their end: a topic that can still page lists its stories as
 *   far back as it has been read ([readSoFar]; everything once a load of older ones failed,
 *   [released]), and every list goes on from what it listed at the last build ([order],
 *   [appendOnly]) – older pages never land above a story on screen. A refresh sorts afresh.
 *
 * - The reading history takes stories out of every list ([reads]): the ones hidden by hand, and
 *   – with [FeedPrefs.hideRead] – the ones read when the lists were last built anew.
 *
 * @param now reference for the age filter.
 * @param hides the readable-only snapshot of the last refresh.
 * @param verification the verdicts, and what each list showed since the last build.
 * @param open the followed topics that can still bring older articles (can page, not at their end).
 * @param order what the endless lists listed at the last build ([BuiltFeed.order]).
 * @param released topics whose last load of older articles failed: their lists show all they hold.
 * @param reads the stories the reading history leaves out: hidden ones always, read ones with
 *   [FeedPrefs.hideRead] ([ReadFilter]). They keep their place in [order], so a story shown again
 *   (unhidden, marked unread) comes back where it was.
 */
internal fun buildNewsFeed(
    news: Map<String, TopicNews>,
    prefs: FeedPrefs,
    now: ZonedDateTime,
    hides: ReadabilityHides = ReadabilityHides.NONE,
    verification: Verification = Verification.OFF,
    open: Set<String> = emptySet(),
    order: FeedOrder = FeedOrder.NONE,
    released: Set<String> = emptySet(),
    reads: ReadFilter = ReadFilter.NONE,
): NewsFeed = buildFeed(news, prefs, now, hides, verification, open, order, released, reads).feed

/** A built feed and the order its endless lists have now – what the next build goes on from. */
internal class BuiltFeed(val feed: NewsFeed, val order: FeedOrder)

/** [buildNewsFeed] together with the order of its lists, for the next build. Pure. */
internal fun buildFeed(
    news: Map<String, TopicNews>,
    prefs: FeedPrefs,
    now: ZonedDateTime,
    hides: ReadabilityHides = ReadabilityHides.NONE,
    verification: Verification = Verification.OFF,
    open: Set<String> = emptySet(),
    order: FeedOrder = FeedOrder.NONE,
    released: Set<String> = emptySet(),
    reads: ReadFilter = ReadFilter.NONE,
): BuiltFeed {
    val reference = now.toInstant()
    // The history's stories leave the lists after their order is taken (a story shown again keeps its place).
    val filtering = !reads.hidden.isEmpty || (prefs.hideRead && !reads.read.isEmpty)
    val keep: (NewsItem) -> Boolean = { reads.keeps(it, prefs.hideRead) }
    val floor = reference.minus(TIMELINE_FLOOR)
    val size = prefs.sectionSize.coerceAtLeast(1)
    val verify = if (prefs.readableOnly && verification.enabled) verification else Verification.OFF
    val onTop = HashSet<String>()
    val fresh = ArrayList<Pair<Topic, List<NewsItem>>>(prefs.topics.size)
    val everything = ArrayList<Pair<Topic, List<NewsItem>>>(prefs.topics.size)
    val depths = LinkedHashMap<String, Instant>()
    val listed = FeedOrder.Builder()
    val sections = prefs.topics.mapIndexed { index, topic ->
        val topicNews = news[topic.id]
        val held = shortItems(
            readableItems(topicNews?.items.orEmpty().filter { it.feedSourceId !in prefs.disabledFeeds }, hides, prefs.readableOnly),
            hides,
            prefs.hideLong,
        )
        val depth = if (topic.id in open) pagedDepth(topicNews?.items.orEmpty(), reference) else null
        if (depth != null) depths[topic.id] = depth
        // The topic's own list: as far back as the topic has been read, in the order it had before.
        val stamp = topicNews?.fetchedAt.orEmpty()
        val before = order.of(topic.id, stamp)
        val reached = if (depth == null || topic.id in released) held else readSoFar(held, depth, floor, before)
        val ordered = appendOnly(reached, before, NewsItem::id)
        listed.put(topic.id, stamp, ordered.map(NewsItem::id))
        val candidates = if (filtering) ordered.filter(keep) else ordered
        val checked = verifiedList(candidates, topic.id, verify, NewsItem::id)
        val items = checked.shown
        val recent = filterByAge(items, prefs.newsAge, reference)
        fresh += topic to filterByAge(if (filtering) held.filter(keep) else held, prefs.newsAge, reference)
        everything += topic to held
        // A section whose stories all opened earlier sections still shows them: never empty-looking.
        val top = recent.filter { it.id !in onTop }.ifEmpty { recent }.take(size)
        top.forEach { onTop += it.id }
        // Candidates that can still fill the section: within the age window (any age while it has fewer than the minimum).
        val topWaiting = if (top.size >= size) {
            emptyList()
        } else {
            checked.waiting.filter { top.size < MIN_PER_TOPIC || isWithinAge(it, prefs.newsAge, reference) }
        }
        SectionUi(
            index = index,
            topic = topic,
            all = items,
            top = top,
            fetchedAt = topicNews?.fetchedAt,
            failedSources = topicNews?.failedSources?.map { it.name }.orEmpty(),
            subtitle = prefs.subtitles[topic.id].orEmpty(),
            browsable = topic.parentId == null && !topic.custom && Taxonomy.children(topic.id).isNotEmpty(),
            waiting = checked.waiting,
            topWaiting = topWaiting,
            candidates = candidates.size,
            heldBack = ordered.size - candidates.size,
        )
    }
    val latestCandidates = latestOf(fresh)
    val latest = verifiedList(latestCandidates, LATEST_LIST, verify) { it.item.id }
    // The timeline of all topics: every story once, in the order it had before.
    val stamp = sections.joinToString("|") { "${it.id}@${it.fetchedAt.orEmpty()}" }
    val before = order.of(TIMELINE_LIST, stamp)
    val stories = timelineStories(everything, horizon = depths.values.maxOrNull(), floor = floor, shown = before)
    val timelineOrder = appendOnly(stories.articles, before) { it.item.id }
    listed.put(TIMELINE_LIST, stamp, timelineOrder.map { it.item.id })
    val timelineCandidates = if (filtering) timelineOrder.filter { keep(it.item) } else timelineOrder
    val timeline = verifiedList(timelineCandidates, TIMELINE_LIST, verify) { it.item.id }
    // A story on a "Für dich" card is not repeated below the cards – nor is its twin from another feed.
    val above = onTop + onTop.mapNotNull(stories.twins::get)
    val feed = NewsFeed(
        sections = sections,
        latest = latest.shown,
        fetchedAt = sections.mapNotNull { it.fetchedAt?.let(::parseInstantOrNull) }.maxOrNull(),
        verifying = verify.enabled,
        latestWaiting = latest.waiting.map { it.item },
        latestCandidates = latestCandidates.size,
        timeline = timeline.shown,
        timelineWaiting = timeline.waiting.map { it.item },
        timelineCandidates = timelineCandidates.size,
        more = timeline.shown.filter { it.item.id !in above },
        olderNext = nextToPage(depths),
        timelineHeldBack = timelineOrder.size - timelineCandidates.size,
    )
    return BuiltFeed(feed, listed.build())
}

/** True when [item] is within [age] of [now]; undated articles count as fresh. */
private fun isWithinAge(item: NewsItem, age: NewsAge, now: Instant): Boolean {
    if (age.hours <= 0) return true
    val at = parseInstantOrNull(item.publishedAt) ?: return true
    return Duration.between(at, now) <= Duration.ofHours(age.hours.toLong())
}

/**
 * Every story of [fresh] once, newest first (stable for equal times). A story in several topics
 * is shown under the most specific one: its own topic, else a sub-topic over its category.
 */
internal fun latestOf(fresh: List<Pair<Topic, List<NewsItem>>>): List<FeedArticle> {
    val byId = LinkedHashMap<String, FeedArticle>()
    for ((topic, items) in fresh) {
        for (item in items) {
            val current = byId[item.id]
            if (current == null || isMoreSpecific(topic, current.topic, item)) byId[item.id] = FeedArticle(item, topic)
        }
    }
    return byId.values.sortedWith(compareByDescending<FeedArticle> { parseInstantOrNull(it.item.publishedAt) ?: Instant.MIN })
}

/** True when [candidate] describes [item] better than [current]. */
internal fun isMoreSpecific(candidate: Topic, current: Topic, item: NewsItem): Boolean = when {
    current.id == item.topicId -> false
    candidate.id == item.topicId -> true
    else -> candidate.parentId != null && candidate.parentId == current.id
}

/**
 * The articles within [age] (newest first, as stored). A topic never shows fewer than
 * [MIN_PER_TOPIC] articles when it has them: a quiet newsroom still gets its latest stories.
 * Undated articles count as fresh (the feed order is all there is).
 */
internal fun filterByAge(items: List<NewsItem>, age: NewsAge, now: Instant, minimum: Int = MIN_PER_TOPIC): List<NewsItem> {
    if (age.hours <= 0) return items
    val window = Duration.ofHours(age.hours.toLong())
    val within = items.filter { item ->
        val at = parseInstantOrNull(item.publishedAt) ?: return@filter true
        Duration.between(at, now) <= window
    }
    return if (within.size >= minimum) within else items.take(minimum)
}

/** "www.tagesschau.de/…" → "tagesschau.de"; empty when [url] has no host. */
internal fun hostOf(url: String): String {
    val afterScheme = url.trim().substringAfter("://", missingDelimiterValue = "")
    if (afterScheme.isEmpty()) return ""
    return afterScheme
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
        .substringBefore(':')
        .lowercase()
        .removePrefix("www.")
}

/** The two parts of a feed meta line; either may be missing. */
internal data class FeedMetaParts(val time: String?, val source: String?)

/** "vor 3 Std." and "tagesschau" separately, so the time never gets shortened. */
internal fun feedMetaParts(item: NewsItem, now: ZonedDateTime, lang: AppLanguage): FeedMetaParts {
    val time = Fmt.relative(item.publishedAt, now, lang)
    val source = item.source.trim().ifEmpty { item.publisherDomain.trim() }.ifEmpty { hostOf(item.url) }
    return FeedMetaParts(time = time, source = source.ifEmpty { null })
}

/** "vor 3 Std. · tagesschau" — unknown parts are left out. */
internal fun feedMeta(item: NewsItem, now: ZonedDateTime, lang: AppLanguage): String {
    val parts = feedMetaParts(item, now, lang)
    return listOfNotNull(parts.time, parts.source).joinToString(" · ")
}

/** [feedMeta] in the interface language of the composition (used by the article screen). */
@Composable
@ReadOnlyComposable
internal fun feedMeta(item: NewsItem, now: ZonedDateTime): String = feedMeta(item, now, LocalAppLanguage.current)

/** "07:12" for the feed's title line; empty when nothing was fetched yet. */
internal fun fetchedLabel(fetchedAt: Instant?): String {
    val at = fetchedAt ?: return ""
    return Fmt.clock(at.toString())
}

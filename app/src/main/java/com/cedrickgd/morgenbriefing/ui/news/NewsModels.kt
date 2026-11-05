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
) {
    val id: String get() = topic.id
    val count: Int get() = all.size
    val hasMore: Boolean get() = all.size > top.size
    val isEmpty: Boolean get() = all.isEmpty()
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
) {
    val isEmpty: Boolean get() = latest.isEmpty()
    val articleCount: Int get() = latest.size

    /** Topics that have something to show. */
    val filledSections: List<SectionUi> get() = sections.filter { !it.isEmpty }
}

/**
 * Builds the feed for the followed topics of [prefs] from the repository's [news]. Pure.
 *
 * - A topic's own list ([SectionUi.all]) holds everything it has (minus switched-off sources and,
 *   with [FeedPrefs.readableOnly], what [hides] rules out) – older pages included, so the list
 *   really is endless. "Für dich" and "Neueste" keep to [FeedPrefs.newsAge].
 * - The same story often sits in several followed topics (a category also reads its
 *   sub-topics' feeds): "Für dich" shows it in the first section only, "Neueste" once, under the
 *   most specific topic (its own topic, else the sub-topic rather than its category).
 *
 * @param now reference for the age filter.
 * @param hides the readable-only snapshot of the last refresh.
 */
internal fun buildNewsFeed(
    news: Map<String, TopicNews>,
    prefs: FeedPrefs,
    now: ZonedDateTime,
    hides: ReadabilityHides = ReadabilityHides.NONE,
): NewsFeed {
    val reference = now.toInstant()
    val size = prefs.sectionSize.coerceAtLeast(1)
    val onTop = HashSet<String>()
    val fresh = ArrayList<Pair<Topic, List<NewsItem>>>(prefs.topics.size)
    val sections = prefs.topics.mapIndexed { index, topic ->
        val topicNews = news[topic.id]
        val items = readableItems(
            topicNews?.items.orEmpty().filter { it.feedSourceId !in prefs.disabledFeeds },
            hides,
            prefs.readableOnly,
        )
        val recent = filterByAge(items, prefs.newsAge, reference)
        fresh += topic to recent
        // A section whose stories all opened earlier sections still shows them: never empty-looking.
        val top = recent.filter { it.id !in onTop }.ifEmpty { recent }.take(size)
        top.forEach { onTop += it.id }
        SectionUi(
            index = index,
            topic = topic,
            all = items,
            top = top,
            fetchedAt = topicNews?.fetchedAt,
            failedSources = topicNews?.failedSources?.map { it.name }.orEmpty(),
            subtitle = prefs.subtitles[topic.id].orEmpty(),
            browsable = topic.parentId == null && !topic.custom && Taxonomy.children(topic.id).isNotEmpty(),
        )
    }
    return NewsFeed(
        sections = sections,
        latest = latestOf(fresh),
        fetchedAt = sections.mapNotNull { it.fetchedAt?.let(::parseInstantOrNull) }.maxOrNull(),
    )
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
private fun isMoreSpecific(candidate: Topic, current: Topic, item: NewsItem): Boolean = when {
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

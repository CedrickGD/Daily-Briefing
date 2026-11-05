package com.cedrickgd.morgenbriefing.data.model

import kotlinx.serialization.Serializable

/**
 * A topic of the feed: a built-in one from [Topics] or one the user created in the settings.
 * [seed] is the topic's own color (ARGB) for covers, chips and section accents, independent
 * of the app accent.
 */
@Serializable
data class Topic(
    val id: String,
    val title: String,
    val emoji: String,
    val seed: Long,
    /** Created by the user (Einstellungen → Themen → "Eigenes Thema"); has only its own sources. */
    val custom: Boolean = false,
    /** Key of a [TopicIcon] (built-in topics, new custom topics); null → [emoji] is shown. */
    val icon: String? = null,
    /** The category of a sub-topic (`wirtschaft` for `wirtschaft/boerse`); null for categories. */
    val parentId: String? = null,
    /** A custom topic that follows a search ("Midterms"): the Google News query. */
    val query: String? = null,
)

/** How a [FeedSource] is read. */
@Serializable
enum class SourceKind {
    /** A publisher's own RSS/Atom feed. */
    RSS,

    /**
     * A Google News RSS feed (top stories, a section, a place or a search): every item names its
     * publisher, links point to news.google.com until resolved.
     */
    GOOGLE_NEWS,
}

/**
 * One feed the app reads. Built-in sources come from
 * [com.cedrickgd.morgenbriefing.data.news.FeedCatalog]; Google News feeds are made per topic by
 * [com.cedrickgd.morgenbriefing.data.news.TopicSources]. The user can switch every one of them
 * off and add their own (`custom:` ids) to any topic.
 */
@Serializable
data class FeedSource(
    /** Stable id: `tagesschau-inland` (built-in), `gn:<topic>@<edition>` (Google News) or `custom:<uuid>` (added by the user). */
    val id: String,
    /** Shown as the source of an article: "tagesschau", "heise online". */
    val name: String,
    val url: String,
    val topicId: String,
    val builtIn: Boolean = true,
    val kind: SourceKind = SourceKind.RSS,
    /** [NewsEdition.key] a Google News source reads (`US:en`); null for publisher feeds. */
    val edition: String? = null,
) {
    val isCustom: Boolean get() = !builtIn
    val isGoogleNews: Boolean get() = kind == SourceKind.GOOGLE_NEWS
}

/** One article as a feed delivered it. The full text lives on the publisher's site ([url]). */
@Serializable
data class NewsItem(
    /** Stable key derived from the link; unique within the app. */
    val id: String,
    val headline: String,
    /** Teaser in plain text (HTML stripped), may be empty. */
    val summary: String = "",
    val url: String,
    /** Name of the source feed, e.g. "tagesschau". */
    val source: String,
    /** [FeedSource.id] the article came from. */
    val sourceId: String,
    val topicId: String,
    /** ISO-8601 with offset, e.g. `2026-10-29T13:02:19+01:00`; null when the feed gave none. */
    val publishedAt: String? = null,
    /** The feed's picture for the article, if it carried one. */
    val image: ArticleImage? = null,
    /** Host of the publisher (`bbc.com`), from Google News' `<source url>` or the link. */
    val publisherDomain: String = "",
    /** "google" when the item came from Google News (then [url] is a news.google.com link until resolved). */
    val aggregator: String? = null,
) {
    val key: String get() = id
}

@Serializable
data class ArticleImage(
    val url: String,
    /** Short description for accessibility, when the feed had one. */
    val alt: String = "",
)

/** What the last fetch of one source produced. */
@Serializable
data class SourceStatus(
    val sourceId: String,
    val name: String,
    val ok: Boolean,
    val count: Int = 0,
    /** Short German reason when [ok] is false. */
    val error: String = "",
)

/** One page of a search ([com.cedrickgd.morgenbriefing.data.NewsRepository.search]). */
data class SearchPage(
    val query: String,
    /** 0 = the newest results; each further page reaches one week further back. */
    val page: Int,
    /** Newest first, duplicates removed. Items found in Google News carry [Topics.SEARCH_ID]. */
    val items: List<NewsItem>,
    /** Whether asking for [page] + 1 can bring more. */
    val hasMore: Boolean,
    /** Google News could not be reached; [items] then holds only matching cached articles. */
    val failed: Boolean = false,
)

/** Everything the app holds for one topic: the merged articles of its sources. */
@Serializable
data class TopicNews(
    val topicId: String,
    /** ISO-8601 with offset: when these articles were fetched. */
    val fetchedAt: String,
    /** Newest first; articles without a date follow the dated ones. */
    val items: List<NewsItem> = emptyList(),
    val sources: List<SourceStatus> = emptyList(),
) {
    val failedSources: List<SourceStatus> get() = sources.filter { !it.ok }
    val allFailed: Boolean get() = sources.isNotEmpty() && sources.all { !it.ok }
}

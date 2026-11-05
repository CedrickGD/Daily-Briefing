package com.cedrickgd.morgenbriefing.domain

import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.news.FeedCatalog
import com.cedrickgd.morgenbriefing.data.news.Regions
import com.cedrickgd.morgenbriefing.data.news.TopicSources

/**
 * The edition (and so the language) one article is written in. Since v3 a single feed mixes
 * editions: Washington, D.C. local news are read in `US:en` while the main edition is `DE:de`,
 * so "Zuhören" and the hyphenation of a headline must ask the article, not the settings.
 *
 * The source tells: a Google News item names its edition in its source id
 * (`gn:<topic>@US:en`, paged ones `…#page`), a built-in publisher feed has its language in
 * [FeedCatalog], the Bundesland presets ([Regions]) are German. Only feeds the user added
 * have no known language; they count as the edition of their topic. Pure, unit-tested.
 */
object ItemEdition {

    /**
     * The edition of [item] for [settings]: from its source when known, else the edition its
     * topic reads ([TopicSources.editionFor]).
     */
    fun of(item: NewsItem, settings: AppSettings): NewsEdition {
        val main = settings.edition
        googleNewsEdition(item.sourceId)?.let { return it }
        sourceLanguage(item.sourceId)?.let { (language, countries) -> return editionFor(language, countries, main) }
        return TopicSources.editionFor(item.topicId, settings)
    }

    /**
     * The ISO 639-1 language of [item] as far as the item itself tells (no settings needed),
     * null for a feed the user added.
     */
    fun languageOf(item: NewsItem): String? =
        googleNewsEdition(item.sourceId)?.language ?: sourceLanguage(item.sourceId)?.first

    /** `gn:<topic>@<edition>` (optionally with the paging suffix) → that edition. */
    internal fun googleNewsEdition(sourceId: String): NewsEdition? {
        val id = sourceId.removeSuffix(NewsRepository.PAGE_SUFFIX)
        if (!id.startsWith(GOOGLE_NEWS_PREFIX)) return null
        return NewsEdition.fromKey(id.substringAfterLast('@', missingDelimiterValue = ""))
    }

    /** Language and countries of a built-in source, null for an unknown (the user's own) one. */
    private fun sourceLanguage(sourceId: String): Pair<String, Set<String>>? {
        val id = sourceId.removeSuffix(NewsRepository.PAGE_SUFFIX)
        FeedCatalog.entries.firstOrNull { it.source.id == id }?.let { return it.language to it.countries }
        if (Regions.ALL.any { region -> region.feeds.any { it.id == id } }) return NewsEdition.GERMANY.language to setOf(NewsEdition.GERMANY.country)
        return null
    }

    /**
     * The main edition when it speaks [language] (and is one of the feed's [countries]), else
     * the first edition of the feed's countries in that language, else any edition of it.
     */
    private fun editionFor(language: String, countries: Set<String>, main: NewsEdition): NewsEdition {
        if (main.language == language && (countries.isEmpty() || main.country in countries)) return main
        return NewsEdition.ALL.firstOrNull { it.language == language && it.country in countries }
            ?: NewsEdition.ALL.firstOrNull { it.language == language && it.country == main.country }
            ?: NewsEdition.ALL.firstOrNull { it.language == language }
            ?: main
    }

    private const val GOOGLE_NEWS_PREFIX = "gn:"
}

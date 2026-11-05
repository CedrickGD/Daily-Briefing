package com.cedrickgd.morgenbriefing.ui.search

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.domain.ItemEdition
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import java.util.Locale

/** How the results are ordered: as the search ranks them, or by date. */
enum class SearchSort { RELEVANCE, NEWEST }

/** The choices above the results: their order and whether the English editions are searched too. */
@Immutable
data class SearchOptions(val sort: SearchSort = SearchSort.RELEVANCE, val allLanguages: Boolean = true)

/**
 * What of the results shows. With "only readable articles" ([readableOnly]) only those the
 * reader fetched and judged complete ([ArticleStatus.READABLE]); [checking] are still being
 * verified, [candidates] the next of them to verify (in the order of the list), [unreadable]
 * turned out to be behind a paywall, a wall or no article.
 */
@Immutable
data class ResultsView(
    val shown: List<NewsItem>,
    val checking: Int = 0,
    val candidates: List<NewsItem> = emptyList(),
    val unreadable: Int = 0,
)

/** The results as the screen shows them. Pure, unit-tested. */
object SearchView {
    /** At most this many results are verified at a time (the prefetcher keeps the cards on screen first). */
    const val VERIFY_WINDOW = 24

    /** With this many results still being verified the list does not ask for more pages yet. */
    const val MAX_CHECKING_BEFORE_MORE = 12

    /** [items] in [sort]: as ranked, or newest first (undated last, otherwise as ranked). */
    fun ordered(items: List<NewsItem>, sort: SearchSort): List<NewsItem> = when (sort) {
        SearchSort.RELEVANCE -> items
        SearchSort.NEWEST -> NewsRepository.newestFirst(items)
    }

    /**
     * What of [items] shows for [statuses] (the prefetcher's, missing = unknown): everything,
     * or with [readableOnly] only the verified ones – the rest is waiting for its check or out.
     */
    fun readable(items: List<NewsItem>, statuses: Map<String, ArticleStatus>, readableOnly: Boolean): ResultsView {
        if (!readableOnly) return ResultsView(items)
        val shown = ArrayList<NewsItem>()
        val pending = ArrayList<NewsItem>()
        var unreadable = 0
        for (item in items) {
            when (statuses[item.id] ?: ArticleStatus.UNKNOWN) {
                ArticleStatus.READABLE -> shown += item
                ArticleStatus.UNKNOWN, ArticleStatus.LOADING -> pending += item
                // PAYWALL, BLOCKED, FAILED: not complete in the app.
                else -> unreadable++
            }
        }
        return ResultsView(shown, checking = pending.size, candidates = pending.take(VERIFY_WINDOW), unreadable = unreadable)
    }

    /** What a results list without a shown article says. */
    enum class Empty {
        /** Results are being verified (or more pages may still bring readable ones). */
        CHECKING,

        /** Results there were, none readable in full, and no more pages. */
        NONE_READABLE,

        /** Nothing found at all. */
        NOTHING,
    }

    /** What [ui] says when it shows no article; null when it shows some. */
    fun empty(ui: SearchUiState): Empty? = when {
        ui.articles.isNotEmpty() -> null
        ui.checking > 0 -> Empty.CHECKING
        ui.readableOnly && ui.unreadable > 0 -> if (ui.results.isEnd) Empty.NONE_READABLE else Empty.CHECKING
        else -> Empty.NOTHING
    }

    /**
     * Whether the list should ask for the next page: [SearchPaging.shouldLoadMore], and not
     * while many results still wait for their check (they may fill the screen yet).
     */
    fun shouldLoadMore(ui: SearchUiState, nearEnd: Boolean): Boolean =
        SearchPaging.shouldLoadMore(ui.results, nearEnd) && ui.checking < MAX_CHECKING_BEFORE_MORE

    /**
     * The language [item] is written in: its edition's ([ItemEdition.languageOf]) unless the
     * publisher's country domain says otherwise (a German site in the US edition's results);
     * null when unknown (a feed the user added).
     */
    fun languageOf(item: NewsItem): String? {
        val edition = ItemEdition.languageOf(item) ?: return null
        val tld = item.publisherDomain.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        return TLD_LANGUAGES[tld] ?: edition
    }

    /** "EN" for an article in another language than the interface's [ui], else null. */
    fun languageTag(item: NewsItem, ui: AppLanguage): String? =
        languageOf(item)?.takeIf { it != ui.code }?.uppercase(Locale.ROOT)

    /** Country domains whose sites write in one language (`.ch`, `.be`, `.ca` speak several). */
    private val TLD_LANGUAGES = mapOf(
        "de" to "de", "at" to "de", "fr" to "fr", "es" to "es", "it" to "it", "nl" to "nl", "pl" to "pl",
        "pt" to "pt", "br" to "pt", "se" to "sv", "tr" to "tr", "jp" to "ja", "uk" to "en", "ie" to "en",
        "au" to "en", "nz" to "en", "us" to "en",
    )
}

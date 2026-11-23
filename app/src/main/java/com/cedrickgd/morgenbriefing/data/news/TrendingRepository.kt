package com.cedrickgd.morgenbriefing.data.news

import android.util.Log
import com.cedrickgd.morgenbriefing.data.local.JsonFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** The search suggestions of the moment ([TrendingRepository.suggestions]). */
data class TrendSuggestions(
    /** What people search for, or (without trends) the subjects of the top stories. */
    val terms: List<String> = emptyList(),
    /** [terms] come from Google Trends; false: from the headlines ([TrendingTerms.fromHeadlines]). */
    val fromTrends: Boolean = false,
    /** Two of [terms] for the search bar, picked once per app start; empty: the bar's fixed examples. */
    val examples: List<String> = emptyList(),
) {
    companion object {
        val NONE = TrendSuggestions()
    }
}

/**
 * What people search for right now: Google Trends' daily RSS for the country of the news edition
 * (`trends.google.com/trending/rss?geo=DE`, checked 23.11.2026: RSS 2.0, ten `<item>`s whose
 * `<title>` is the search term in lower case, plus `ht:` fields the app does not need). Loaded on
 * every app start ([load]) and kept in `store/trending.json`, so a start without network still has
 * the last ones (for two days). Without trends the suggestions are the subjects of the top
 * stories ([headlines], [TrendingTerms.fromHeadlines]).
 */
class TrendingRepository(
    private val fetch: suspend (url: String) -> ParsedFeed,
    private val file: File,
    private val json: Json,
    headlines: Flow<List<String>>,
    scope: CoroutineScope,
    private val clock: () -> Instant = Instant::now,
    /** One seed per process: the search bar shows the same two examples until the next start. */
    private val seed: Long = System.nanoTime(),
) {
    private val trends = MutableStateFlow<List<String>>(emptyList())

    /** The terms for the search screen and the two examples for the search bar. */
    val suggestions: StateFlow<TrendSuggestions> = combine(trends, headlines.distinctUntilChanged()) { trend, titles ->
        suggestionsOf(trend, titles, Random(seed))
    }.stateIn(scope, SharingStarted.Eagerly, TrendSuggestions.NONE)

    /**
     * Shows the cached trends of [country] (when younger than two days), then loads the current
     * ones and keeps them. [language]: the edition's language ([TrendingTerms.fromFeed]). A failed
     * load keeps what is shown; it never throws (besides cancellation).
     */
    suspend fun load(country: String, language: String) {
        val code = country.uppercase()
        val cached = withContext(Dispatchers.IO) { readCache() }
        trends.value = cached?.takeIf { it.country == code && isFresh(it) }?.terms.orEmpty()
        val terms = try {
            TrendingTerms.fromFeed(fetch(urlFor(code)), language)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Trends of $code unavailable", e)
            return
        }
        if (terms.isEmpty()) return
        trends.value = terms
        withContext(Dispatchers.IO) {
            try {
                JsonFiles.writeAtomically(file, json.encodeToString(CachedTrends.serializer(), CachedTrends(code, clock().toString(), terms)))
            } catch (e: Exception) {
                Log.w(TAG, "Could not store the trends", e)
            }
        }
    }

    private fun readCache(): CachedTrends? = JsonFiles.readOrNull(file)?.let { text ->
        try {
            json.decodeFromString(CachedTrends.serializer(), text)
        } catch (e: Exception) {
            Log.w(TAG, "Trend cache unreadable", e)
            null
        }
    }

    private fun isFresh(cached: CachedTrends): Boolean {
        val at = runCatching { Instant.parse(cached.fetchedAt) }.getOrNull() ?: return false
        return Duration.between(at, clock()) <= MAX_AGE
    }

    @Serializable
    private data class CachedTrends(val country: String, val fetchedAt: String, val terms: List<String>)

    companion object {
        private const val TAG = "TrendingRepository"
        private val MAX_AGE: Duration = Duration.ofDays(2)

        /** Google Trends' daily RSS for [country] (ISO 3166-1, upper case). */
        fun urlFor(country: String): String = "https://trends.google.com/trending/rss?geo=${country.uppercase()}"

        /** The suggestions: [trends] when there are any, else the subjects of [headlines]; two of them as examples. */
        fun suggestionsOf(trends: List<String>, headlines: List<String>, random: Random): TrendSuggestions {
            val fromTrends = trends.isNotEmpty()
            val terms = if (fromTrends) trends else TrendingTerms.fromHeadlines(headlines)
            return TrendSuggestions(terms, fromTrends, TrendingTerms.examples(terms, random))
        }
    }
}

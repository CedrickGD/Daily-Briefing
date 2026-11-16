package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import java.util.Locale

/**
 * "Lange Artikel ausblenden" – normal-length news, no novels: what counts as a long read. Pure,
 * unit-tested.
 *
 * - **Measured**: an article whose text runs past [MAX_WORDS] words (about four to five minutes
 *   of reading) – a longread, a dossier, a reportage, a live blog read out in full. The length
 *   is the reader's own extraction ([ArticleContent.wordCount]) or what the publisher declares
 *   ([ArticleContent.declaredWords]), whichever is more: a teaser page still says how long the
 *   article behind it is. The prefetcher measures every article it reads and keeps the length
 *   with the verdict ([VerdictStore]), so nothing is fetched twice for it.
 * - **Known without reading**: live blogs and tickers name themselves in the headline or link
 *   ("Liveblog", "Liveticker", "+++ Live", `/liveblog/`, `/live/`) – they run long and keep
 *   changing, whatever the first check measured.
 */
object LongReads {

    /** More words than this is a long read (≈ 4–5 minutes at 200 words a minute). */
    const val MAX_WORDS = 900

    /** True when [words] (0 = not measured) is a long read. */
    fun isLong(words: Int): Boolean = words > MAX_WORDS

    /** The length of [content] as far as it is known: extracted or declared, whichever is more. */
    fun wordsOf(content: ArticleContent): Int = maxOf(content.wordCount, content.declaredWords)

    /** Words in a headline that make it a live blog or ticker (folded to lower case). */
    private val LIVE_HEADLINE = listOf(
        "liveblog", "live-blog", "live blog", "liveticker", "live-ticker", "live ticker", "newsblog", "news-blog",
        "live updates", "live-updates", "en direct", "in diretta",
    )

    /** Path pieces of live blogs and tickers. */
    private val LIVE_PATH = listOf("liveblog", "live-blog", "liveticker", "live-ticker", "newsblog", "/live/")

    /** True when [item] is a live blog or ticker by its headline or link. */
    fun isLiveBlog(item: NewsItem): Boolean {
        val headline = item.headline.lowercase(Locale.ROOT)
        if (LIVE_HEADLINE.any { it in headline }) return true
        if (headline.startsWith("live:") || headline.startsWith("+++ live")) return true
        val path = item.url.substringAfter("://", "").substringAfter('/', "").substringBefore('?').lowercase(Locale.ROOT)
        return LIVE_PATH.any { "/$path".contains(it) }
    }

    /** The ids among [lengths] (item id → words) that are long reads. */
    fun longIds(lengths: Map<String, Int>): Set<String> = lengths.filterValues(::isLong).keys
}

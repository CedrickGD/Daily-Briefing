package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.util.parseInstantOrNull
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * The same article from two feeds of one publisher: its own feed and Google News ("taz Politik" /
 * "taz.de", "n-tv" / "n-tv.de", "tagesschau" / "tagesschau.de"), or two of its own feeds ("heise
 * online" / "heise IT"). Their links differ (a news.google.com link, another tracking query), so
 * neither the id nor the link says they are one; the publisher and the headline do. Pure,
 * unit-tested.
 *
 * Two articles are twins when they belong to the same publisher [family], were published within
 * [WINDOW] of each other and their headlines are the same or nearly so ([sameStory]) – Google
 * News words a headline a little differently now and then (a kicker more or less). The same
 * headline from two publishers (an agency story) is folded by the lists themselves.
 */
object StoryTwins {

    /**
     * Twins are published at (nearly) the same time; half a day covers a story updated since,
     * and keeps tomorrow's edition of a daily format apart ("Marktbericht: DAX startet …").
     */
    private val WINDOW: Duration = Duration.ofHours(12)

    /** Shorter headlines are too generic to call nearly the same ("Die Lage am Morgen"). */
    private const val NEAR_MIN_WORDS = 5

    /**
     * The longer headline has the shorter one's words (a kicker in front, a clause behind) but
     * for one in every so many: none of a short headline ("Marktbericht: DAX steigt …" is not
     * "… DAX fällt …"), one of eight words, two of sixteen.
     */
    private const val NEAR_MISS_EVERY = 8

    /** Second-level labels of country domains (`bbc.co.uk`, `abc.net.au`). */
    private val SECOND_LEVEL = setOf("co", "com", "org", "net", "ac", "gov", "or", "ne")

    /** Publishers that write under two sites. */
    private val ALIASES = mapOf(
        "zdfheute.de" to "zdf.de",
        "bbc.co.uk" to "bbc.com",
        "sz.de" to "sueddeutsche.de",
    )

    /**
     * The publisher [item] belongs to: its site without sub-domains (`newsfeed.zeit.de` →
     * `zeit.de`), whatever the feed calls itself. Without a site (a Google News item that names
     * none) the source's name, folded. Empty when neither is known.
     */
    fun family(item: NewsItem): String {
        val domain = item.publisherDomain.ifBlank { if (item.aggregator != null) "" else GoogleNews.domainOf(item.url) }
        return family(domain, item.source)
    }

    /** [family] of a site ([domain], may be empty) with the name its feed gives it. */
    fun family(domain: String, source: String = ""): String {
        val host = domain.trim().lowercase(Locale.ROOT).removePrefix("www.")
        if (host.isEmpty()) return SearchQuery.fold(source).replace(" ", "").let { if (it.isEmpty()) "" else "name:$it" }
        val labels = host.split('.').filter { it.isNotEmpty() }
        val keep = if (labels.size >= 3 && labels.last().length == 2 && labels[labels.size - 2] in SECOND_LEVEL) 3 else 2
        val site = labels.takeLast(keep).joinToString(".")
        return ALIASES[site] ?: site
    }

    /** A headline folded for comparing: its text, its words and the numbers among them. */
    class Headline internal constructor(val text: String, val words: Set<String>, val numbers: Set<String>)

    /** Folding a headline normalises Unicode: done once per headline, the lists are built often. */
    private val headlines = object : LinkedHashMap<String, Headline>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Headline>): Boolean = size > MAX_CACHED_HEADLINES
    }

    private const val MAX_CACHED_HEADLINES = 6000

    /** [headline] without case, accents and punctuation ([SearchQuery.fold]), split into words. */
    fun headline(headline: String): Headline = synchronized(headlines) {
        headlines.getOrPut(headline) {
            val text = SearchQuery.fold(headline)
            val words = text.split(' ').filterTo(LinkedHashSet()) { it.isNotEmpty() }
            Headline(text, words, words.filterTo(HashSet()) { word -> word.any(Char::isDigit) })
        }
    }

    /**
     * Whether [a] and [b] head the same story: both at least [NEAR_MIN_WORDS] words long and
     * naming the same numbers ("Nachrichten vom 10. November" is not the 11th's), and the
     * longer one has the words of the shorter one – all but one in every [NEAR_MISS_EVERY]. A
     * short headline says too little, even word for word ("Liveblog").
     */
    fun sameStory(a: Headline, b: Headline): Boolean {
        if (a.numbers != b.numbers) return false
        val small = minOf(a.words.size, b.words.size)
        if (small < NEAR_MIN_WORDS) return false
        val common = a.words.count { it in b.words }
        return common >= small - small / NEAR_MISS_EVERY
    }

    /** How much of a story an item carries: the publisher's own beats Google News', a picture and a teaser count. */
    fun quality(item: NewsItem): Int =
        (if (item.aggregator == null) 4 else 0) + (if (item.image != null) 2 else 0) + (if (item.summary.isNotBlank()) 1 else 0)

    /** [winner] with what only its [twin] carries: the picture, the teaser. */
    fun withTwin(winner: NewsItem, twin: NewsItem): NewsItem {
        val image = winner.image ?: twin.image
        val summary = winner.summary.ifBlank { twin.summary }
        return if (image == winner.image && summary == winner.summary) winner else winner.copy(image = image, summary = summary)
    }

    /** Of two twins the better one ([quality]; [first] when they are equal), with the other's picture or teaser when it has none. */
    fun merged(first: NewsItem, second: NewsItem): NewsItem =
        if (quality(second) > quality(first)) withTwin(second, first) else withTwin(first, second)

    /**
     * Finds the twin of an article among the ones [add]ed before. Not thread-safe: one per pass
     * over a list.
     */
    class Finder {
        private class Entry(val headline: Headline, val at: Instant?, val index: Int)

        private val families = HashMap<String, ArrayList<Entry>>()

        /** The index [add] was given for the twin of [item], or null when it has none yet. */
        fun find(item: NewsItem): Int? {
            val entries = families[family(item)] ?: return null
            val headline = headline(item.headline)
            val at = parseInstantOrNull(item.publishedAt)
            return entries.firstOrNull { entry -> near(at, entry.at) && sameStory(headline, entry.headline) }?.index
        }

        /** Remembers [item] as the article at [index] of the caller's list. */
        fun add(item: NewsItem, index: Int) {
            val family = family(item)
            if (family.isEmpty()) return
            families.getOrPut(family) { ArrayList() } += Entry(headline(item.headline), parseInstantOrNull(item.publishedAt), index)
        }

        private fun near(a: Instant?, b: Instant?): Boolean = a == null || b == null || Duration.between(a, b).abs() <= WINDOW
    }
}

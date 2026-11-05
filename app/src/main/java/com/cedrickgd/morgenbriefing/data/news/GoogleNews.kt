package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.LocalDate

/**
 * Google News RSS: the feed URLs of an edition (top stories, sections, places, searches), the
 * search operators the app uses and the clean-up of its items. No key, no account. Pure,
 * unit-tested; the fetch itself is [com.cedrickgd.morgenbriefing.data.remote.RssClient].
 *
 * What a Google News item looks like (checked 05.11.2026):
 * `<title>Headline - Publisher</title>`, `<link>https://news.google.com/rss/articles/<ID>?oc=5`,
 * `<source url="https://www.bbc.com">BBC</source>`, a `<description>` that only repeats the
 * link and the publisher (top stories: a list of related links) and no picture.
 */
object GoogleNews {
    const val HOST = "news.google.com"

    /** [com.cedrickgd.morgenbriefing.data.model.NewsItem.aggregator] of Google News items. */
    const val AGGREGATOR = "google"

    /** Shown as the source name of Google News feeds in the settings. */
    const val SOURCE_NAME = "Google News"

    /** At most this many `-site:` exclusions per query: the query stays short and focused. */
    const val MAX_EXCLUSIONS = 12

    /** Every site is a Google News search operator target; these are the ones it can leave out first. */
    private val EVERYWHERE = setOf("youtube.com")

    /** `after:YYYY-MM-DD before:YYYY-MM-DD`: one step of paging into the past. */
    data class DateWindow(val after: LocalDate, val before: LocalDate) {
        init {
            require(!before.isBefore(after)) { "before ($before) must not be earlier than after ($after)" }
        }

        val operators: String get() = "after:$after before:$before"
    }

    /** The edition's top stories. */
    fun topStoriesUrl(edition: NewsEdition): String = base(edition, "rss").build().toString()

    /** One section of the edition (Google answers with a redirect to `/rss/topics/<id>`). */
    fun sectionUrl(section: NewsSection, edition: NewsEdition): String =
        base(edition, "rss/headlines/section/topic/${section.name}").build().toString()

    /**
     * The local news of a place, as Google News' own place pages have them. [place] is a name
     * Google knows ("Leipzig", "Washington, District of Columbia"); an unknown one gives an
     * empty feed.
     */
    fun geoUrl(place: String, edition: NewsEdition): String =
        base(edition, "rss/headlines/section/geo").addPathSegment(place.trim()).build().toString()

    /** A search in [edition]; [query] may carry operators (see [buildQuery]). */
    fun searchUrl(query: String, edition: NewsEdition): String =
        base(edition, "rss/search").addQueryParameter("q", query.trim()).build().toString()

    private fun base(edition: NewsEdition, path: String): HttpUrl.Builder =
        HttpUrl.Builder().scheme("https").host(HOST).addPathSegments(path)
            .addEncodedQueryParameter("hl", edition.hl)
            .addEncodedQueryParameter("gl", edition.country)
            .addEncodedQueryParameter("ceid", edition.ceid)

    /**
     * The date operators of [window], then [query], then `-site:` for the first
     * [MAX_EXCLUSIONS] of [exclusions] (already ranked, see [rankExclusions]); exclusions the
     * query already names are not repeated.
     *
     * The order matters: Google News reads only the first few dozen terms of a query, so date
     * operators at the end of a long query are silently ignored and the same newest stories
     * come back (checked 05.11.2026). Exclusions that fall off the end cost only result slots:
     * their articles are filtered out by domain anyway.
     */
    fun buildQuery(query: String, exclusions: List<String> = emptyList(), window: DateWindow? = null): String {
        val base = query.trim().replace(Regex("\\s+"), " ")
        val parts = mutableListOf<String>()
        if (window != null) parts += window.operators
        parts += base
        exclusions.asSequence()
            .map { it.trim().lowercase().removePrefix("www.") }
            .filter { it.isNotEmpty() && !it.contains(' ') && "site:$it" !in base }
            .distinct()
            .take(MAX_EXCLUSIONS)
            .forEach { parts += "-site:$it" }
        return parts.joinToString(" ")
    }

    /**
     * Orders [blocked] by how likely they show up in [edition], so the [MAX_EXCLUSIONS] that fit
     * into a query matter most: the edition's own sites first (`.de` for Germany, `.co.uk` for
     * Britain, `faz.net` for German), then for English editions the international `.com` sites
     * Google News lists most, then the other `.com` sites, YouTube, other countries' sites, and
     * social networks (rarely in news results) last. Within a rank the given order (most certain
     * first) is kept. What does not fit is still filtered out of the results by domain.
     */
    fun rankExclusions(blocked: Collection<String>, edition: NewsEdition): List<String> {
        val ownTlds = countryTlds(edition.country)
        val english = edition.language == "en"
        fun rank(domain: String): Int {
            val generic = domain.substringAfterLast('.') in GENERIC_TLDS
            val language = GENERIC_LANGUAGE[domain]
            return when {
                ownTlds.any { domain.endsWith(it) } || language == edition.language -> 0
                domain in SOCIAL -> 6
                domain in EVERYWHERE -> 3
                generic && language != null -> 5
                generic && english && domain in OFTEN_LISTED -> 1
                generic && english -> 2
                generic -> 4
                else -> 5
            }
        }
        return blocked.map { it.lowercase().removePrefix("www.") }.distinct()
            .withIndex()
            .sortedWith(compareBy<IndexedValue<String>> { rank(it.value) }.thenBy { it.index })
            .map { it.value }
    }

    private val GENERIC_TLDS = setOf("com", "net", "org", "info", "io", "co", "news")

    /** National publishers on international domains. */
    private val GENERIC_LANGUAGE = mapOf("faz.net" to "de", "handelsblatt.com" to "de")

    /** Walled sites seen most often in English Google News results (05.11.2026). */
    private val OFTEN_LISTED = setOf(
        "nytimes.com", "wsj.com", "washingtonpost.com", "bloomberg.com", "reuters.com", "ft.com",
        "barrons.com", "businessinsider.com", "economist.com",
    )

    /** Social networks: walled for the reader, but hardly ever in news results. */
    private val SOCIAL = setOf("tiktok.com", "instagram.com", "facebook.com", "x.com", "twitter.com")

    private fun countryTlds(country: String): List<String> = when (country) {
        "GB" -> listOf(".co.uk", ".uk")
        "AU" -> listOf(".com.au", ".au")
        "IN" -> listOf(".co.in", ".in")
        "BR" -> listOf(".com.br", ".br")
        "MX" -> listOf(".com.mx", ".mx")
        "AR" -> listOf(".com.ar", ".ar")
        "JP" -> listOf(".co.jp", ".jp")
        "TR" -> listOf(".com.tr", ".tr")
        "US" -> listOf(".us")
        else -> listOf("." + country.lowercase())
    }

    /** True for `https://news.google.com/rss/articles/<ID>` (and the `/articles/` web form). */
    fun isArticleLink(url: String): Boolean = articleId(url) != null

    /** The `<ID>` of a Google News article link, else null. */
    fun articleId(url: String): String? {
        val parsed = url.trim().toHttpUrlOrNull() ?: return null
        if (parsed.host != HOST) return null
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }
        val index = segments.indexOf("articles")
        if (index < 0 || index == segments.lastIndex) return null
        return segments[index + 1].takeIf { it.length >= 16 }
    }

    /**
     * The headline without the ` - Publisher` Google appends. With a known [publisher] only that
     * exact suffix goes (publishers like "LVZ - Leipziger Volkszeitung" contain the separator
     * themselves); without one the last ` - …` part goes when it is short enough to be a name.
     */
    fun stripPublisher(title: String, publisher: String): String {
        val clean = title.trim()
        val name = publisher.trim()
        if (name.isNotEmpty()) {
            for (separator in SEPARATORS) {
                val suffix = separator + name
                if (clean.endsWith(suffix, ignoreCase = true) && clean.length > suffix.length) {
                    return clean.dropLast(suffix.length).trim()
                }
            }
            return clean
        }
        val cut = clean.lastIndexOf(" - ")
        return if (cut > 0 && clean.length - cut - 3 in 2..40) clean.substring(0, cut).trim() else clean
    }

    private val SEPARATORS = listOf(" - ", " – ", " | ")

    /**
     * The host of [url] without `www.` (`https://www.bbc.co.uk/x` → `bbc.co.uk`), lower case;
     * empty when [url] is no web address.
     */
    fun domainOf(url: String): String =
        url.trim().toHttpUrlOrNull()?.host?.lowercase()?.removePrefix("www.").orEmpty()

    /**
     * A Google News description is the headline as a link plus the publisher (or, for top
     * stories, a list of related headlines) – never a teaser. Only text that is clearly more
     * than that ([MIN_TEASER] characters after removing the headline and the publisher) counts.
     */
    fun teaser(plainDescription: String, headline: String, publisher: String): String {
        var rest = plainDescription.replace(' ', ' ')
        listOf(headline, publisher).filter { it.isNotBlank() }.forEach { rest = rest.replace(it, " ", ignoreCase = true) }
        rest = rest.replace(Regex("\\s+"), " ").trim()
        return if (rest.length >= MIN_TEASER && !plainDescription.contains('\n')) rest else ""
    }

    private const val MIN_TEASER = 80

    /** An ISIN (`DE000FG2TZV5`): searches for "Aktien" also find stock quote pages, which are no articles. */
    private val ISIN = Regex("\\b[A-Z]{2}[A-Z0-9]{9}[0-9]\\b")

    /** True for search hits that are a quote or product page ("DAX Turbo … | Kurs, Chart | DE000…"). */
    fun isQuotePage(headline: String): Boolean = ISIN.containsMatchIn(headline)

    /**
     * Subscriber-only articles marked in the headline: "(S+)" / "SPIEGEL+", "Z+", "F+",
     * "WELTplus", "BILDplus", "SZ Plus" (seen in Google News titles, 05.11.2026).
     */
    private val PAYWALL_MARK = Regex("^\\s*(\\((S|Z|F)\\+\\)|(S|Z|F)\\+\\s)|SPIEGEL\\+|WELTplus|BILDplus|^\\s*SZ Plus\\b")

    /** Whether [headline] says the article is for subscribers only. */
    fun isPaywallHeadline(headline: String): Boolean = PAYWALL_MARK.containsMatchIn(headline)
}

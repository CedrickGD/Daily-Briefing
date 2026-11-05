package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.SourceKind
import com.cedrickgd.morgenbriefing.data.model.Topics

/** A Google News search that can page into the past ([com.cedrickgd.morgenbriefing.data.NewsRepository.loadMore]). */
data class PagingQuery(
    /** The search words, without exclusions and date operators. */
    val query: String,
    val edition: NewsEdition,
    /** The Google News source of the topic; paged articles carry `<sourceId>#page`. */
    val sourceId: String,
)

/**
 * Which feeds a topic reads, for the current settings. Pure, unit-tested.
 *
 * - **Category** (`wirtschaft`): the edition's publisher feeds of the category and of all its
 *   sub-topics, plus Google News (the edition's section, or a search for categories without one;
 *   `top` reads the edition's top stories).
 * - **Sub-topic** (`wirtschaft/boerse`): its publisher feeds plus a Google News search in the
 *   edition's language.
 * - **Region**: in Germany the chosen Bundesland preset ([Regions]), everywhere the Google News
 *   page of the home place (in the edition of the place's country).
 * - **Place** (`local:…`): the Google News page of the place, in its country's edition.
 * - **Own topic** (`custom:…`): the user's feeds and, for a followed search, that search.
 *
 * Feeds the user added to any topic come last. With [AppSettings.readableOnly] publisher feeds
 * of sites the reader cannot show are skipped and searches leave those sites out (`-site:`).
 */
object TopicSources {

    /**
     * The sources of [topicId], switched-off ones only with [includeDisabled]. [blocked] are the
     * sites the reader cannot show, most certain first ([KnownWalls.DOMAINS] plus what the phone
     * learned).
     */
    fun resolve(
        topicId: String,
        settings: AppSettings,
        blocked: Collection<String> = KnownWalls.DOMAINS,
        includeDisabled: Boolean = false,
    ): List<FeedSource> {
        val blockedSet = blocked.toSet()
        val own = settings.customFeeds.filter { it.topicId == topicId }
        val sources = when {
            Topics.isLocalId(topicId) -> listOfNotNull(Topics.placeFor(topicId, settings)?.let { localSource(topicId, it, settings) })
            Topics.isCustomId(topicId) -> listOfNotNull(customSearch(topicId, settings, blocked))
            topicId == Topics.REGION_ID -> regionSources(settings)
            else -> builtInSources(topicId, settings, blocked)
        }
        return (sources.filter { it.isGoogleNews || !isBlockedFeed(it, settings, blockedSet) } + own)
            .distinctBy { it.id }
            .filter { includeDisabled || it.id !in settings.disabledFeeds }
    }

    /**
     * The search behind [topicId]'s endless list: the topic's query in its edition (places: the
     * place's name in the edition's language, "Milano" not "Mailand" – see [Place.newsName]), or
     * null when the topic cannot page (top stories, own feeds only).
     */
    fun pagingQuery(topicId: String, settings: AppSettings): PagingQuery? {
        if (topicId == Topics.TOP_ID) return null
        val edition = editionFor(topicId, settings)
        val query = when {
            Topics.isLocalId(topicId) -> Topics.placeFor(topicId, settings)?.let { quoted(it.newsName(edition.language)) }
            Topics.isCustomId(topicId) -> settings.customTopics.firstOrNull { it.id == topicId }?.query?.takeIf { it.isNotBlank() }
            topicId == Topics.REGION_ID -> settings.home.takeIf { it.name.isNotBlank() }?.let { quoted(it.newsName(edition.language)) }
            else -> Taxonomy.byId(topicId)?.queryFor(edition)
        } ?: return null
        return PagingQuery(query, edition, googleNewsId(topicId, edition))
    }

    /** The edition a topic reads: a place's own country (else the main edition), otherwise [AppSettings.edition]. */
    fun editionFor(topicId: String, settings: AppSettings): NewsEdition = when {
        Topics.isLocalId(topicId) -> Topics.placeFor(topicId, settings)?.let { placeEdition(it, settings) } ?: settings.edition
        topicId == Topics.REGION_ID -> placeEdition(settings.home, settings)
        else -> settings.edition
    }

    /** Id of a topic's Google News source; it names the edition, so a new edition starts afresh. */
    fun googleNewsId(topicId: String, edition: NewsEdition): String = "gn:$topicId@${edition.key}"

    /** The sites [settings] leave out of searches, ranked for [edition] (empty when everything may show). */
    fun exclusions(settings: AppSettings, edition: NewsEdition, blocked: Collection<String>): List<String> =
        if (settings.readableOnly) GoogleNews.rankExclusions(blocked, edition) else emptyList()

    private fun builtInSources(topicId: String, settings: AppSettings, blocked: Collection<String>): List<FeedSource> {
        val def = Taxonomy.byId(topicId) ?: return emptyList()
        val edition = settings.edition
        val members = if (def.isCategory) setOf(topicId) + Taxonomy.children(topicId).map { it.id } else setOf(topicId)
        val curated = FeedCatalog.forEdition(edition)
            .filter { it.source.topicId in members }
            .map { it.source.copy(topicId = topicId) }
        val googleUrl = when {
            def.topStories -> GoogleNews.topStoriesUrl(edition)
            def.section != null && def.isCategory -> GoogleNews.sectionUrl(def.section, edition)
            else -> def.queryFor(edition)?.let { GoogleNews.searchUrl(GoogleNews.buildQuery(it, exclusions(settings, edition, blocked)), edition) }
        }
        val google = googleUrl?.let { googleSource(topicId, it, edition) }
        return curated + listOfNotNull(google)
    }

    private fun regionSources(settings: AppSettings): List<FeedSource> {
        val presets = if (Regions.servesEdition(settings.edition)) Regions.byKey(settings.region).feeds else emptyList()
        val home = settings.home.takeIf { it.name.isNotBlank() }?.let { place ->
            val edition = placeEdition(place, settings)
            googleSource(Topics.REGION_ID, GoogleNews.geoUrl(geoName(place), edition), edition)
        }
        return presets + listOfNotNull(home)
    }

    private fun localSource(topicId: String, place: Place, settings: AppSettings): FeedSource {
        val edition = placeEdition(place, settings)
        return googleSource(topicId, GoogleNews.geoUrl(geoName(place), edition), edition)
    }

    private fun customSearch(topicId: String, settings: AppSettings, blocked: Collection<String>): FeedSource? {
        val query = settings.customTopics.firstOrNull { it.id == topicId }?.query?.takeIf { it.isNotBlank() } ?: return null
        val edition = settings.edition
        return googleSource(topicId, GoogleNews.searchUrl(GoogleNews.buildQuery(query, exclusions(settings, edition, blocked)), edition), edition)
    }

    private fun googleSource(topicId: String, url: String, edition: NewsEdition) = FeedSource(
        id = googleNewsId(topicId, edition),
        name = GoogleNews.SOURCE_NAME,
        url = url,
        topicId = topicId,
        kind = SourceKind.GOOGLE_NEWS,
        edition = edition.key,
    )

    private fun placeEdition(place: Place, settings: AppSettings): NewsEdition {
        val main = settings.edition
        // Same country as the main edition (Zurich while reading Swiss French): keep the user's language.
        if (place.countryCode.equals(main.country, ignoreCase = true)) return main
        return NewsEdition.forCountry(place.countryCode) ?: main
    }

    /**
     * The name Google News knows the place by: "Washington, District of Columbia" – the first
     * part of the region label disambiguates (a bare "Washington" or "Springfield" finds
     * nothing). Google understands it in the picker's language too ("Los Angeles, Kalifornien",
     * checked 05.11.2026). Just the name when the label is only the country or repeats the name.
     */
    internal fun geoName(place: Place): String {
        val name = place.name.trim()
        val parts = place.region.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val state = parts.firstOrNull()?.takeIf { parts.size >= 2 && !it.equals(name, ignoreCase = true) }
        return if (state == null) name else "$name, $state"
    }

    private fun quoted(name: String): String = "\"" + name.trim().replace("\"", "") + "\""

    private fun isBlockedFeed(source: FeedSource, settings: AppSettings, blocked: Set<String>): Boolean =
        settings.readableOnly && source.builtIn && KnownWalls.matches(GoogleNews.domainOf(source.url), blocked)
}

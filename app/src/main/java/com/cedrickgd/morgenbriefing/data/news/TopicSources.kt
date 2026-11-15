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
 * - **Own topic** (`custom:…`): the user's feeds and, for a followed search, that search in
 *   the main edition (plus, read by the repository, the English editions: [extraEditions]).
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
            Topics.isCustomId(topicId) -> listOfNotNull(customSearch(topicId, settings, blocked, settings.edition))
            topicId == Topics.REGION_ID -> regionSources(settings)
            else -> builtInSources(topicId, settings, blocked)
        }
        return (sources.filter { it.isGoogleNews || !isBlockedFeed(it, settings, blockedSet) } + own)
            .distinctBy { it.id }
            .filter { includeDisabled || it.id !in settings.disabledFeeds }
    }

    /**
     * The search behind [topicId]'s endless list: the topic's query in its edition (places:
     * [placeQuery] – the place's name in the edition's language, "Milano" not "Mailand"), or null
     * when the topic cannot page (own feeds only, or its Google News source is switched off – the
     * older pages would be Google News articles). The top stories page with the headline desks'
     * query of the edition's language ([Taxonomy]); editions in other languages cannot (the
     * English fallback finds sports results there, checked 15.11.2026).
     */
    fun pagingQuery(topicId: String, settings: AppSettings): PagingQuery? {
        val edition = editionFor(topicId, settings)
        if (topicId == Topics.TOP_ID && Taxonomy.byId(topicId)?.speaks(edition) != true) return null
        val sourceId = googleNewsId(topicId, edition)
        if (sourceId in settings.disabledFeeds) return null
        val query = when {
            Topics.isLocalId(topicId) -> Topics.placeFor(topicId, settings)?.let { placeQuery(it, edition) }
            Topics.isCustomId(topicId) -> settings.customTopics.firstOrNull { it.id == topicId }?.query?.takeIf { it.isNotBlank() }?.let(GoogleNews::userQuery)
            topicId == Topics.REGION_ID -> settings.home.takeIf { it.name.isNotBlank() }?.let { placeQuery(it, edition) }
            else -> Taxonomy.byId(topicId)?.queryFor(edition)
        } ?: return null
        return PagingQuery(query, edition, sourceId)
    }

    /**
     * The search words for [place]'s older news in [edition]: the quoted name plus, where the
     * first page disambiguates by state too ([geoName]), the quoted state – a bare
     * `"Washington"` pages into Washington State, `"Washington" "District of Columbia"` stays in
     * D.C. (checked 05.11.2026). The state costs some hits (Leipzig: 64 → 18 per three days), but
     * an endless list of another town's news is worse than a slower one of the right town.
     *
     * Place labels come from the German place search, so the state is German: German editions
     * take it as it is, English ones through [ENGLISH_STATES] ("Kalifornien" finds 2 stories of
     * Los Angeles in three days, "California" 100). Other languages, and countries whose first
     * level is too coarse for news to name it ([STATE_COUNTRIES]: "England", "Munster"), get the
     * name alone.
     */
    internal fun placeQuery(place: Place, edition: NewsEdition): String {
        val name = quoted(place.newsName(edition.language))
        val state = stateOf(place)
            ?.takeIf { place.countryCode.uppercase() in STATE_COUNTRIES }
            ?.let { stateIn(it, edition.language) }
            ?: return name
        return "$name ${quoted(state)}"
    }

    private fun stateIn(germanState: String, language: String): String? = when (language) {
        "de" -> germanState
        "en" -> germanState.removePrefix("Bundesstaat ").let { ENGLISH_STATES[it] ?: it }
        else -> null
    }

    /** Countries whose news name the state with the town ("Austin, Texas", "Halle (Saale), Sachsen-Anhalt"). */
    private val STATE_COUNTRIES = setOf("US", "CA", "AU", "IN", "DE", "AT", "CH")

    /** The German state names of the place search that English differs from (seen 05.11.2026). */
    private val ENGLISH_STATES = mapOf(
        "Kalifornien" to "California",
        "Pennsylvanien" to "Pennsylvania",
        "Britisch-Kolumbien" to "British Columbia",
        "Neuschottland" to "Nova Scotia",
        "Neubraunschweig" to "New Brunswick",
        "Neufundland" to "Newfoundland",
        "Neufundland und Labrador" to "Newfoundland and Labrador",
        "Prinz-Edward-Insel" to "Prince Edward Island",
        "Nordwest-Territorien" to "Northwest Territories",
        "Neusüdwales" to "New South Wales",
        "Westaustralien" to "Western Australia",
        "Südaustralien" to "South Australia",
        "Tasmanien" to "Tasmania",
        "Nordterritorium" to "Northern Territory",
        "Westbengalen" to "West Bengal",
    )

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

    /**
     * The English editions a followed search reads besides its main-edition source, like the
     * search screen does ([GoogleNews.searchEditions]): empty for other topics, for an English
     * main edition and when the search's Google News source is switched off. They are not
     * among [resolve]'s sources – the settings list the search once, and switching it off
     * switches these off too; [com.cedrickgd.morgenbriefing.data.NewsRepository] reads them
     * with the topic and keeps only their articles that name the query.
     */
    fun extraEditions(topicId: String, settings: AppSettings, blocked: Collection<String> = KnownWalls.DOMAINS): List<FeedSource> {
        if (!Topics.isCustomId(topicId) || googleNewsId(topicId, settings.edition) in settings.disabledFeeds) return emptyList()
        return GoogleNews.searchEditions(settings.edition, allLanguages = true)
            .filter { it != settings.edition }
            .mapNotNull { edition -> customSearch(topicId, settings, blocked, edition) }
    }

    /**
     * A followed search in [edition]: the query as [GoogleNews.userQuery] gives it (tickers
     * exact, the user's operators kept), like the search screen without `-site:` exclusions
     * ([searchExclusions]). Only the main edition pages into the past ([pagingQuery]).
     */
    private fun customSearch(topicId: String, settings: AppSettings, blocked: Collection<String>, edition: NewsEdition): FeedSource? {
        val query = settings.customTopics.firstOrNull { it.id == topicId }?.query?.takeIf { it.isNotBlank() } ?: return null
        return googleSource(topicId, GoogleNews.searchUrl(GoogleNews.buildQuery(GoogleNews.userQuery(query), searchExclusions(topicId, settings, edition, blocked)), edition), edition)
    }

    /**
     * The `-site:` exclusions of [topicId]'s Google News search (its first page and its endless
     * list): none for a followed search – any `site:` operator makes Google News answer loosely,
     * 100 results whatever the query ("Bundestagswahl": 18 results, 15 naming it; with one
     * `-site:` 100 results, 4 naming it; checked 05.11.2026), and a followed search has no
     * ranking to sort that out. Walled sites still go by domain ([com.cedrickgd.morgenbriefing.data.NewsRepository.readable]).
     * Other topics: [exclusions].
     */
    fun searchExclusions(topicId: String, settings: AppSettings, edition: NewsEdition, blocked: Collection<String>): List<String> =
        if (Topics.isCustomId(topicId)) emptyList() else exclusions(settings, edition, blocked)

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
        val state = stateOf(place)
        return if (state == null) name else "$name, $state"
    }

    /** The first part of the region label when it disambiguates [place] (see [geoName]), else null. */
    private fun stateOf(place: Place): String? {
        val parts = place.region.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.firstOrNull()?.takeIf { parts.size >= 2 && !it.equals(place.name.trim(), ignoreCase = true) }
    }

    private fun quoted(name: String): String = "\"" + name.trim().replace("\"", "") + "\""

    private fun isBlockedFeed(source: FeedSource, settings: AppSettings, blocked: Set<String>): Boolean =
        settings.readableOnly && source.builtIn && KnownWalls.matches(GoogleNews.domainOf(source.url), blocked)
}

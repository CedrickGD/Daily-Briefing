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
 * - **Region**: in Germany (in German) the chosen Bundesland preset ([Regions]), everywhere the
 *   news of the home place, read like a place.
 * - **Place** (`local:…`): the news of the place in the user's language, with the place's
 *   country as the region ([placeSource]).
 * - **Own topic** (`custom:…`): the user's feeds and, for a followed search, that search in
 *   the main edition (plus, read by the repository, the English editions: [extraEditions]).
 *
 * Region and language are chosen independently, and Google News has an edition of its own
 * only for some pairs ([NewsEdition.isNative]). For the others – the USA in German – every
 * feed comes from the edition of the language ([NewsEdition.google]), so what belongs to the
 * country must name it: [TopicDef.domestic] topics (top stories, politics, the economy) become
 * searches for the country ([TopicDef.queryFor]) without publisher feeds, which report on the
 * language's home countries; other topics read the language's sections, searches and the
 * publisher feeds that are not bound to a country. A place becomes a search for its name.
 *
 * Topics that are not about the reader's country ([mixes]: tech, science, the world, the
 * economy …) also read newsrooms from beyond the edition, each kind behind its own switch and
 * capped so the edition's own sources stay the majority: newsrooms in the edition's language
 * from abroad ([CatalogFeed.abroad], [AppSettings.internationalSources], at most [MAX_ABROAD]
 * and half as many as the edition's own) and English feeds not bound to a country
 * ([AppSettings.englishSources], at most [MAX_ENGLISH] and half as many as those in the
 * edition's language).
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
            Topics.isLocalId(topicId) -> listOfNotNull(Topics.placeFor(topicId, settings)?.let { placeSource(topicId, it, settings) })
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
     * name alone – and so does a place read in a language Google News has no edition of its
     * country in: German news name "Washington" or "Los Angeles", hardly ever the state
     * (two weeks: 100 and 75 stories by name, 13 and 16 with the state; checked 15.11.2026).
     */
    internal fun placeQuery(place: Place, edition: NewsEdition): String {
        val name = quoted(place.newsName(edition.language))
        val state = stateOf(place)
            ?.takeIf { edition.isNative && place.countryCode.uppercase() in STATE_COUNTRIES }
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

    /** The edition a topic reads: a place's country in the user's language ([placeEdition]), otherwise [AppSettings.edition]. */
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
        // The publisher feeds of a language report on its home countries, not on a country abroad.
        val curated = if (searchesCountry(def, edition)) emptyList() else FeedCatalog.forEdition(edition)
            .filter { it.source.topicId in members }
            .map { it.source.copy(topicId = topicId) }
        val googleUrl = when (googleFeed(def, edition)) {
            GoogleFeed.TOP_STORIES -> GoogleNews.topStoriesUrl(edition)
            GoogleFeed.SECTION -> def.section?.let { GoogleNews.sectionUrl(it, edition) }
            GoogleFeed.SEARCH -> def.queryFor(edition)
                // Top stories found by the country's name: the last days only, a search answers by relevance.
                ?.let { if (def.topStories) GoogleNews.recent(it, TOP_SEARCH_DAYS) else it }
                ?.let { GoogleNews.searchUrl(GoogleNews.buildQuery(it, exclusions(settings, edition, blocked)), edition) }
        }
        val google = googleUrl?.let { googleSource(topicId, it, edition) }
        val own = curated + listOfNotNull(google)
        if (searchesCountry(def, edition) || !mixes(def)) return own
        // What joins from beyond the edition stays a complement: at most half as many feeds as
        // the edition reads itself (Google News counted, walled feeds not when they are skipped).
        val blockedSet = blocked.toSet()
        val readable = { feed: CatalogFeed -> !isBlockedFeed(feed.source, settings, blockedSet) }
        val homeCount = own.count { it.isGoogleNews || !isBlockedFeed(it, settings, blockedSet) }
        val abroad = if (!settings.internationalSources) emptyList() else mixIn(
            FeedCatalog.entries.filter { it.servesAbroad(edition) && readable(it) },
            topicId, members, limit = minOf(MAX_ABROAD, homeCount / 2),
        )
        val english = if (!settings.englishSources || edition.language == ENGLISH) emptyList() else mixIn(
            FeedCatalog.entries.filter { it.language == ENGLISH && it.countries.isEmpty() && readable(it) },
            topicId, members, limit = minOf(MAX_ENGLISH, (homeCount + abroad.size) / 2),
        )
        return curated + abroad + english + listOfNotNull(google)
    }

    /**
     * Whether [def] reads sources from beyond the edition ([AppSettings.internationalSources],
     * [AppSettings.englishSources]): every built-in topic but the news of the reader's own
     * country – top stories, politics at home and its elections, the economy at home (business
     * cycle, money, housing). The economy as a whole is world news too and mixes; the Region,
     * places and own topics never reach here.
     */
    internal fun mixes(def: TopicDef): Boolean = !def.topStories && def.id !in HOME_NEWS

    private val HOME_NEWS: Set<String> = Taxonomy.DOMESTIC - "wirtschaft"

    /** Whether readers of [edition] can get newsrooms from abroad at all (the settings show the switch only then). */
    fun readsAbroad(edition: NewsEdition): Boolean = FeedCatalog.entries.any { it.servesAbroad(edition) }

    /**
     * Whether English feeds can join for [edition]: it reads another language and has publisher
     * feeds of its own – with Google News alone half as many is none.
     */
    fun readsEnglish(edition: NewsEdition): Boolean =
        edition.language != ENGLISH && FeedCatalog.forEdition(edition).isNotEmpty()

    /**
     * Up to [limit] of [candidates] for [topicId]: the feeds of the topic itself first, then
     * those of its sub-topics ([members]) that mix themselves, each in catalogue order.
     */
    private fun mixIn(candidates: List<CatalogFeed>, topicId: String, members: Set<String>, limit: Int): List<FeedSource> {
        if (limit <= 0) return emptyList()
        return candidates
            .filter { feed -> feed.source.topicId in members && Taxonomy.byId(feed.source.topicId)?.let(::mixes) == true }
            .sortedBy { if (it.source.topicId == topicId) 0 else 1 }
            .take(limit)
            .map { it.source.copy(topicId = topicId) }
    }

    /** At most this many newsrooms from abroad in the edition's language per topic ([AppSettings.internationalSources]). */
    internal const val MAX_ABROAD = 3

    /** At most this many English feeds per topic ([AppSettings.englishSources]). */
    internal const val MAX_ENGLISH = 3

    private const val ENGLISH = "en"

    /** What the Google News source of a built-in topic is. */
    enum class GoogleFeed { TOP_STORIES, SECTION, SEARCH }

    /**
     * The Google News feed of [def] in [edition]: the edition's top stories, the section of a
     * category, else a search. Where Google News has no edition of the pair, top stories and
     * sections would be those of the language's home country, so a [TopicDef.domestic] topic
     * is a search for the country ([TopicDef.queryFor]).
     */
    fun googleFeed(def: TopicDef, edition: NewsEdition): GoogleFeed = when {
        searchesCountry(def, edition) -> GoogleFeed.SEARCH
        def.topStories -> GoogleFeed.TOP_STORIES
        def.section != null && def.isCategory -> GoogleFeed.SECTION
        else -> GoogleFeed.SEARCH
    }

    private fun searchesCountry(def: TopicDef, edition: NewsEdition): Boolean = def.domestic && !edition.isNative

    /** How far back the search that stands in for a country's top stories reaches. */
    private const val TOP_SEARCH_DAYS = 7

    private fun regionSources(settings: AppSettings): List<FeedSource> {
        val presets = if (Regions.servesEdition(settings.edition)) Regions.byKey(settings.region).feeds else emptyList()
        val home = settings.home.takeIf { it.name.isNotBlank() }?.let { placeSource(Topics.REGION_ID, it, settings) }
        return presets + listOfNotNull(home)
    }

    /**
     * The news of [place] in its edition ([placeEdition]). Where Google News has that edition
     * and the place lies in its country, its own place page ([geoName]). Otherwise place pages
     * are empty (Washington, D.C. in German, Leipzig in English: 0 stories, checked
     * 15.11.2026), so the place is searched by name in the user's language ([placeQuery], the
     * words its endless list pages with) over the last [PLACE_SEARCH_DAYS] days – without
     * `-site:` exclusions, which make Google News answer loosely ([searchExclusions]); walled
     * sites still go by domain.
     *
     * A small town has little written about it in another language (Boise in German: 7
     * stories a week, most of them about a company of that name; Washington, D.C. on the
     * Spanish place page of the USA: 6). A place read in another language than the main one of
     * its country therefore carries the place page in that main language as
     * [FeedSource.fallback], read as well when the first feed brings too little
     * ([com.cedrickgd.morgenbriefing.data.remote.RssClient.fetch]): the place never stays empty.
     */
    internal fun placeSource(topicId: String, place: Place, settings: AppSettings): FeedSource {
        val edition = placeEdition(place, settings)
        val url = if (edition.isNative && place.countryCode.equals(edition.country, ignoreCase = true)) {
            GoogleNews.geoUrl(geoName(place), edition)
        } else {
            GoogleNews.searchUrl(GoogleNews.recent(placeQuery(place, edition), PLACE_SEARCH_DAYS), edition)
        }
        val own = NewsEdition.forCountry(place.countryCode)?.takeIf { it != edition }
        return googleSource(topicId, url, edition)
            .copy(fallback = own?.let { googleSource(topicId, GoogleNews.geoUrl(geoName(place), it), it) })
    }

    /** How far back the first page of a place search reaches; older news come with the endless list. */
    private const val PLACE_SEARCH_DAYS = 14

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
     * The `-site:` exclusions of the first page of [topicId]'s Google News search (its endless
     * list asks without any: a loose answer ignores the date window too, see
     * [com.cedrickgd.morgenbriefing.data.NewsRepository.loadMore]): none for a followed search –
     * any `site:` operator makes Google News answer loosely,
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

    /**
     * The edition a place is read in: its own country in the user's news language – Washington,
     * D.C. in German for a reader of German news, Zurich in French for a reader of Swiss French.
     * A country the app does not offer keeps the main edition (and is searched by name).
     */
    private fun placeEdition(place: Place, settings: AppSettings): NewsEdition {
        val main = settings.edition
        return NewsEdition.of(place.countryCode.uppercase(), main.language) ?: main
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

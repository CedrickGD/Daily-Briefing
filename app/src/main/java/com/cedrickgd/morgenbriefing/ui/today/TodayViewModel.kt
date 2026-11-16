package com.cedrickgd.morgenbriefing.ui.today

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cedrickgd.morgenbriefing.AppContainer
import com.cedrickgd.morgenbriefing.MorgenbriefingApp
import com.cedrickgd.morgenbriefing.data.BERLIN
import com.cedrickgd.morgenbriefing.data.MoreResult
import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.article.LongReads
import com.cedrickgd.morgenbriefing.data.local.ReadHistory
import com.cedrickgd.morgenbriefing.data.local.StoryIndex
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.ui.brief.WeatherUi
import com.cedrickgd.morgenbriefing.ui.brief.toWeatherUi
import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.ui.news.BarrenLoads
import com.cedrickgd.morgenbriefing.ui.news.FeedOrder
import com.cedrickgd.morgenbriefing.ui.news.FeedPrefs
import com.cedrickgd.morgenbriefing.ui.news.NewsFeed
import com.cedrickgd.morgenbriefing.ui.news.ReadFilter
import com.cedrickgd.morgenbriefing.ui.news.ReadabilityHides
import com.cedrickgd.morgenbriefing.ui.news.TIMELINE_LIST
import com.cedrickgd.morgenbriefing.ui.news.Verification
import com.cedrickgd.morgenbriefing.ui.news.buildFeed
import com.cedrickgd.morgenbriefing.ui.news.keptAfter
import com.cedrickgd.morgenbriefing.ui.news.keptForBuild
import com.cedrickgd.morgenbriefing.ui.news.sectionFor
import com.cedrickgd.morgenbriefing.util.attempt
import com.cedrickgd.morgenbriefing.util.flatten
import com.cedrickgd.morgenbriefing.util.isOlderThan
import com.cedrickgd.morgenbriefing.util.isWithin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** Everything the Today screen renders. */
@Immutable
data class TodayUiState(
    /** Drives the date line, greeting and relative publish times; refreshed on every resume. */
    val now: ZonedDateTime,
    /** Live or cached weather; null until the first snapshot is known. */
    val weather: WeatherUi? = null,
    val weatherLoading: Boolean = true,
    /** The last weather refresh failed (the shown snapshot, if any, is older). */
    val weatherFailed: Boolean = false,
    /** Weather blocks the user wants besides the hero. */
    val weatherCards: Set<WeatherCard> = WeatherCard.ALL,
    /** The feed settings; null until the settings are known. */
    val prefs: FeedPrefs? = null,
    /** The feed built from the repository and [prefs]; null until both are known. */
    val feed: NewsFeed? = null,
    /** Topic ids whose refresh is running. */
    val loadingTopics: Set<String> = emptySet(),
    /** The news cache was read (so an empty feed really is empty). */
    val newsWarmed: Boolean = false,
    /** User-initiated pull-to-refresh is running. */
    val refreshing: Boolean = false,
    /** The last news refresh reached not a single source (offline). */
    val offline: Boolean = false,
    /** A news refresh finished at least once in this process. */
    val newsAttempted: Boolean = false,
    /** Topic ids whose older articles are being loaded (endless lists). */
    val loadingMore: Set<String> = emptySet(),
    /** Topic ids whose endless list reached its end. */
    val endReached: Set<String> = emptySet(),
    /** Topic ids whose last load of older articles failed (the user may ask again). */
    val stalled: Set<String> = emptySet(),
    /** Older articles for the timeline ("Neueste", below the "Für dich" cards) are being loaded. */
    val loadingOlder: Boolean = false,
    /** The last load of older articles for the timeline failed (the user may ask again). */
    val olderStalled: Boolean = false,
    /** "Nur vollständige Artikel" wants to check articles, but the phone is offline: only verified ones show. */
    val verifyOffline: Boolean = false,
    /** The compact weather card is expanded (remembered across launches). */
    val weatherExpanded: Boolean = false,
) {
    val today: LocalDate get() = now.toLocalDate()
    val newsLoading: Boolean get() = loadingTopics.isNotEmpty()

    /** Whether [topicId]'s list can still bring older articles. */
    fun canLoadMore(topicId: String): Boolean = prefs != null && topicId in prefs.pageable && topicId !in endReached
}

/**
 * Today: shows cached weather and the cached feed instantly, then refreshes both in parallel.
 * Weather is refreshed again on resume once it is older than 10 minutes, the news once they
 * are older than 15 minutes — and whenever the sources of a followed topic change in the
 * settings. The weather texts follow the interface language. Also drives following topics and
 * hiding sources from the feed, the endless lists ([loadMore] for a topic, [loadOlder] for the
 * timeline of all topics), warming the articles on screen ([prefetch]) and looking up the covers
 * of stories without a picture ([covers]).
 *
 * The reading history ([history]) shapes the lists: hidden stories leave them at once ([hide]),
 * and with "Gelesene Artikel ausblenden" read ones leave them whenever they are built anew – at
 * start, after a refresh, when the tab changes ([onViewChanged]) – never while the user looks at
 * them: a story read a moment ago shows, calmer, until then.
 */
class TodayViewModel(
    private val container: AppContainer,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(BERLIN) },
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState(now = clock()))
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    /** Onboarding sheet shown while `onboardingDone` is false. Starts hidden: no flash. */
    val onboarding: StateFlow<Boolean> = container.settings.settings
        .map { settings -> !settings.onboardingDone }
        .distinctUntilChanged()
        .catch { e -> Log.w(TAG, "Settings unavailable for onboarding", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Lead pictures the prefetcher found, by article id (for cards of articles without one). */
    val articleImages: StateFlow<Map<String, String>> get() = container.prefetcher.images

    /** Cover pictures looked up ahead of the lists, by article id (kept across starts). */
    val articleCovers: StateFlow<Map<String, String>> get() = container.covers.covers

    /** What the prefetcher knows about each article's readability, by article id. */
    val articleStatus: StateFlow<Map<String, ArticleStatus>> get() = container.prefetcher.status

    /** The stories the user read or hid (for the calm look of read cards and the ⋮ menu). */
    val history: StateFlow<ReadHistory> get() = container.history.state

    /**
     * The stories read when the lists were last built anew ([snapshotReads]): what "Gelesene
     * Artikel ausblenden" leaves out until the next build, so a card read a moment ago stays put.
     */
    private val readAtBuild = MutableStateFlow(StoryIndex.EMPTY)

    /**
     * The readable-only snapshot the feed is built with. Taken when a refresh ends (and once at
     * start), never while scrolling, so articles do not vanish under the user's finger.
     */
    private val hides = MutableStateFlow(ReadabilityHides.NONE)

    private var weatherJob: Job? = null
    private var newsJob: Job? = null
    private var lastWeatherAttempt: Instant? = null
    private var lastNewsAttempt: Instant? = null
    private var weatherToggled = false
    private val loadMoreJobs = HashMap<String, Job>()
    private var olderJob: Job? = null

    /**
     * Topics the timeline no longer waits for: their older pages failed twice while other topics
     * answered, and one broken search must not hold "Neueste" back for good. Until the next
     * pull-to-refresh.
     */
    private val skipped = MutableStateFlow<Set<String>>(emptySet())
    private val olderFailures = HashMap<String, Int>()

    /**
     * Stops a list from asking for older articles by itself once page after page brought nothing
     * it can show ("nur vollständige Artikel": every candidate a teaser or unreachable) – the
     * list then offers "Weiter zurück suchen" instead of checking for ever. Main thread only.
     */
    private val barren = BarrenLoads()

    /** The stories whose covers the list asked for last; asked again after a pause. */
    private var coverWindow: List<NewsItem> = emptyList()

    /** What the screen asked the prefetcher to warm (cancelled when it is left, renewed when it is back). */
    private val prefetchSession = PrefetchSession()

    init {
        viewModelScope.launch { showCachedWeather() }
        refreshWeather()
        observeFeed()
        viewModelScope.launch {
            attempt { container.history.warm() }.onFailure { Log.w(TAG, "Reading history unavailable", it) }
            snapshotReads()
            attempt { container.news.warm() }.onFailure { Log.w(TAG, "News cache unavailable", it) }
            snapshotHides()
            refreshNews(force = false)
        }
        observeInputs()
        observeLanguage()
        viewModelScope.launch {
            val stored = attempt { container.settings.weatherExpanded.first() }
                .onFailure { Log.w(TAG, "Weather card state unavailable", it) }
                .getOrNull() ?: return@launch
            // A tap that came first wins over the stored state.
            if (!weatherToggled) _state.update { it.copy(weatherExpanded = stored) }
        }
    }

    /** Expands or collapses the weather card; remembered for the next launch. */
    fun setWeatherExpanded(expanded: Boolean) {
        weatherToggled = true
        _state.update { it.copy(weatherExpanded = expanded) }
        viewModelScope.launch {
            attempt { container.settings.setWeatherExpanded(expanded) }
                .onFailure { Log.w(TAG, "Could not store the weather card state", it) }
        }
    }

    /** Pull-to-refresh: weather and news in parallel; joins refreshes that are already running. */
    fun refresh() {
        if (_state.value.refreshing) return
        olderFailures.clear()
        barren.reset()
        skipped.value = emptySet()
        _state.update { it.copy(refreshing = true, now = clock(), stalled = emptySet(), olderStalled = false) }
        viewModelScope.launch {
            try {
                joinAll(refreshWeather(), refreshNews(force = true))
            } finally {
                // The lists are built anew: what was read until now leaves them (also offline).
                snapshotReads()
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    /** Called on every resume of the screen (also after a notification tap). */
    fun onResume() {
        val now = clock()
        _state.update { it.copy(now = now) }
        val current = _state.value
        val instant = now.toInstant()

        val weatherStale = current.weather == null ||
            isOlderThan(current.weather.snapshot.fetchedAt, instant, WEATHER_MAX_AGE)
        if (weatherStale && !isWithin(lastWeatherAttempt, instant, WEATHER_MAX_AGE)) refreshWeather()

        if (!isWithin(lastNewsAttempt, instant, NEWS_RETRY_AFTER)) refreshNews(force = false)

        // Leaving the screen cancelled the warm-ups of the articles on screen; the list does not
        // ask again for the same articles, so the last window is sent once more.
        sendPrefetch(prefetchSession.resume())
        sendCovers(coverWindow)
    }

    /**
     * Another view of the feed was chosen (a tab, "Alle →"): it is built anew, so with "Gelesene
     * Artikel ausblenden" the stories read until now leave it.
     */
    fun onViewChanged() {
        viewModelScope.launch { snapshotReads() }
    }

    /** Hides [item]'s story from every list at once (⋮ → "Ausblenden"); [unhide] undoes it. */
    fun hide(item: NewsItem) {
        attempt { container.history.hide(item) }.onFailure { Log.w(TAG, "Could not hide ${item.id}", it) }
    }

    /** Shows [item]'s story again ("Rückgängig"). */
    fun unhide(item: NewsItem) {
        attempt { container.history.unhide(item) }.onFailure { Log.w(TAG, "Could not show ${item.id} again", it) }
    }

    /** Marks [item]'s story read or unread (⋮); the lists change only when they are built anew. */
    fun setRead(item: NewsItem, read: Boolean) {
        attempt { if (read) container.history.markRead(item) else container.history.markUnread(item) }
            .onFailure { Log.w(TAG, "Could not mark ${item.id}", it) }
    }

    fun retryWeather() {
        refreshWeather(force = true)
    }

    fun retryNews() {
        refreshNews(force = true)
    }

    /** Follows [topicId] (at [index], default: at the end) — the ⋮ menu and "Rückgängig". */
    fun follow(topicId: String, index: Int? = null) {
        viewModelScope.launch {
            attempt { container.settings.follow(topicId, index) }
                .onFailure { Log.w(TAG, "Could not follow $topicId", it) }
        }
    }

    /** Stops following [topicId]; the repository keeps the last followed topic. */
    fun unfollow(topicId: String) {
        viewModelScope.launch {
            attempt { container.settings.unfollow(topicId) }
                .onFailure { Log.w(TAG, "Could not unfollow $topicId", it) }
        }
    }

    /** Stores a new topic order ("Deine Themen"). */
    fun setTopicOrder(order: List<String>) {
        viewModelScope.launch {
            attempt { container.settings.reorderFollowed(order) }
                .onFailure { Log.w(TAG, "Could not store topic order", it) }
        }
    }

    /** Switches a source off (⋮ → "„tagesschau“ ausblenden"); [setSourceEnabled] undoes it. */
    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            attempt {
                container.settings.update { settings ->
                    settings.copy(disabledFeeds = if (enabled) settings.disabledFeeds - sourceId else settings.disabledFeeds + sourceId)
                }
            }.onFailure { Log.w(TAG, "Could not store source state", it) }
        }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            attempt { container.settings.update { it.copy(onboardingDone = true) } }
                .onFailure { Log.w(TAG, "Could not store onboarding state", it) }
        }
    }

    /**
     * Loads older articles of [topicId] (its list neared its end). Runs once per topic at a time
     * and only while the topic can page. Quiet days are skipped by themselves (the windows
     * widen, [olderOf]); only a load that failed – offline, Google News not reachable – marks
     * the topic as stalled, so the list offers "Weiter zurück suchen" instead of hammering a
     * dead network. So does a list that asked page after page without a single article it could
     * show ([BarrenLoads]); asking from that row (the topic is stalled) always loads.
     */
    fun loadMore(topicId: String) {
        if (loadMoreJobs[topicId]?.isActive == true) return
        val current = _state.value
        if (!current.canLoadMore(topicId)) return
        val section = current.feed?.sectionFor(topicId)
        // Stories left out as read or hidden count as judged: pages of them alone end in "Weiter zurück suchen".
        val judged = section?.let { it.candidates + it.heldBack - it.waiting.size } ?: 0
        if (!barren.mayLoad(topicId, shown = section?.count ?: 0, judged = judged, byUser = topicId in current.stalled)) {
            _state.update { it.copy(stalled = it.stalled + topicId) }
            return
        }
        _state.update { it.copy(stalled = it.stalled - topicId) }
        loadMoreJobs[topicId] = viewModelScope.launch {
            val settings = attempt { container.settings.current() }.getOrNull() ?: return@launch
            if (!container.news.canLoadMore(topicId, settings)) return@launch
            val result = olderOf(topicId, settings)
            if (result == MoreResult.FAILED || result == MoreResult.EMPTY) {
                _state.update { it.copy(stalled = it.stalled + topicId) }
            }
        }
    }

    /**
     * Loads older articles for the timeline ("Neueste" and the list below the "Für dich" cards
     * neared their end): the topics that reach back least far ([NewsFeed.olderNext]) read their
     * next older days, a few at a time (two with light effects; the repository bounds the
     * connections). The feed then shows everything up to the topic that reaches back least far
     * now, and the list asks again when that brought nothing to its end.
     *
     * Only a round in which nothing worked is a failure (offline): the list then offers
     * "Weiter zurück suchen". A topic that fails while others answer is tried once more and
     * then left out, so one broken search never ends the timeline. Rounds that bring nothing the
     * list can show end the same way ([BarrenLoads]).
     */
    fun loadOlder() {
        if (olderJob?.isActive == true) return
        val light = container.performance.light
        val current = _state.value
        val feed = current.feed ?: return
        val next = feed.olderNext.let { if (light) it.take(OLDER_TOPICS_LIGHT) else it }
        if (next.isEmpty()) return
        val judged = feed.timelineCandidates + feed.timelineHeldBack - feed.timelineWaiting.size
        if (!barren.mayLoad(TIMELINE_LIST, shown = feed.timeline.size, judged = judged, byUser = current.olderStalled)) {
            _state.update { it.copy(olderStalled = true) }
            return
        }
        _state.update { it.copy(loadingOlder = true, olderStalled = false) }
        olderJob = viewModelScope.launch {
            var stalled = false
            try {
                val settings = attempt { container.settings.current() }.getOrNull() ?: return@launch
                val results = coroutineScope {
                    next.map { id ->
                        async {
                            // The topic's own list is loading it already: wait for that instead of a second load.
                            val running = loadMoreJobs[id]?.takeIf { it.isActive }
                            id to if (running != null) running.join().let { MoreResult.GREW } else olderOf(id, settings)
                        }
                    }.awaitAll()
                }
                val failed = results.filter { it.second == MoreResult.FAILED || it.second == MoreResult.EMPTY }.map { it.first }
                val worked = results.size > failed.size
                results.filter { it.second == MoreResult.GREW }.forEach { olderFailures.remove(it.first) }
                if (worked) {
                    failed.forEach { olderFailures[it] = (olderFailures[it] ?: 0) + 1 }
                    val given = failed.filter { (olderFailures[it] ?: 0) >= OLDER_GIVE_UP }
                    if (given.isNotEmpty()) skipped.update { it + given }
                }
                stalled = failed.isNotEmpty() && !worked
            } finally {
                _state.update { it.copy(loadingOlder = false, olderStalled = stalled) }
            }
        }
    }

    /**
     * Older articles of [topicId]: asks again while the days asked for were quiet (each call
     * looks further back with wider windows), up to [MAX_EMPTY_ROUNDS] calls – a year back is
     * reached before that, so [MoreResult.EMPTY] comes back only for a search that never ends.
     */
    private suspend fun olderOf(topicId: String, settings: AppSettings): MoreResult {
        repeat(MAX_EMPTY_ROUNDS) {
            val result = attempt { container.news.loadOlder(topicId, settings) }
                .onFailure { Log.w(TAG, "Loading older articles of $topicId failed", it) }
                .getOrDefault(MoreResult.FAILED)
            if (result != MoreResult.EMPTY) return result
        }
        return MoreResult.EMPTY
    }

    /**
     * Warms [items] (on screen and just below) in the reader's cache, as the settings allow
     * (prefetch mode, edition language). Known articles are answered at once.
     */
    fun prefetch(items: List<NewsItem>) {
        if (_state.value.prefs == null || items.isEmpty()) return
        prefetchSession.request(items)
        sendPrefetch(items)
    }

    /**
     * Looks up the covers of [items] (on screen and the next page; the ones whose feed carried
     * no picture) – on any connection, whatever "Artikel vorladen" says: that setting is about
     * the full text. Nothing with "Bilder anzeigen" off.
     */
    fun covers(items: List<NewsItem>) {
        coverWindow = items
        sendCovers(items)
    }

    /** The screen was left: stop warming and looking up what it asked for ([onResume] asks again). */
    fun stopPrefetch() {
        attempt { container.covers.stop() }.onFailure { Log.w(TAG, "Cover stop failed", it) }
        val ids = prefetchSession.stop()
        if (ids.isEmpty()) return
        attempt { container.prefetcher.cancel(ids) }.onFailure { Log.w(TAG, "Prefetch cancel failed", it) }
    }

    private fun sendCovers(items: List<NewsItem>) {
        val prefs = _state.value.prefs ?: return
        if (items.isEmpty() || !prefs.showImages) return
        attempt { container.covers.request(items, prefs.editionLanguage.ifBlank { null }) }
            .onFailure { Log.w(TAG, "Cover lookup failed", it) }
    }

    private fun sendPrefetch(items: List<NewsItem>) {
        val prefs = _state.value.prefs ?: return
        if (items.isEmpty()) return
        // "Nur vollständige Artikel": verified on any connection, whatever "Artikel vorladen" says.
        attempt { container.prefetcher.prefetch(items, prefs.prefetch, prefs.editionLanguage.ifBlank { null }, verify = prefs.readableOnly) }
            .onFailure { Log.w(TAG, "Prefetch failed", it) }
    }

    /**
     * Loads live weather for the current settings. A running refresh is reused unless [force]
     * (settings changed), in which case it is replaced so the result matches the new places.
     */
    private fun refreshWeather(force: Boolean = false): Job {
        weatherJob?.let { running ->
            if (running.isActive) {
                if (!force) return running
                running.cancel()
            }
        }
        lastWeatherAttempt = clock().toInstant()
        return viewModelScope.launch {
            _state.update { it.copy(weatherLoading = true) }
            val result = attempt { container.weather.load(container.settings.current()) }.flatten()
            ensureActive()
            val snapshot = result.getOrNull()
            if (snapshot != null) {
                val ui = withContext(Dispatchers.Default) { snapshot.toWeatherUi(container.settings.current().language) }
                ensureActive()
                _state.update { it.copy(weather = ui, weatherLoading = false, weatherFailed = false) }
            } else {
                Log.w(TAG, "Weather refresh failed", result.exceptionOrNull())
                _state.update { it.copy(weatherLoading = false, weatherFailed = true) }
            }
        }.also { weatherJob = it }
    }

    /**
     * Fetches the sources of the followed topics ([topicIds]: only these). Without [force] only
     * topics whose articles are older than 15 minutes (or missing) are fetched. The repository
     * skips topics that are already being refreshed, so overlapping calls are harmless. A
     * finished refresh takes a new readable-only snapshot.
     */
    private fun refreshNews(force: Boolean, topicIds: Collection<String>? = null): Job {
        val running = newsJob?.takeIf { it.isActive }
        if (!force && running != null) return running
        lastNewsAttempt = clock().toInstant()
        return viewModelScope.launch {
            // A forced refresh (changed sources, pull-to-refresh) waits for a running one: the
            // repository skips topics that are mid-fetch, and that fetch still uses the old settings.
            running?.join()
            val settings = attempt { container.settings.current() }.getOrNull() ?: return@launch
            // The cached topics (and the sites the reader cannot show) come from disk once: wait
            // for them instead of judging staleness on empty state – and never read them here on
            // the main thread. Resolving every topic's sources is CPU work: off the main thread too.
            attempt { container.news.warm() }.onFailure { Log.w(TAG, "News cache unavailable", it) }
            val visible = Topics.visible(settings).map { it.id }
            // isStale also counts a topic read from other sources (a new edition, a moved region) as stale.
            val wanted = withContext(Dispatchers.Default) {
                (topicIds?.filter { it in visible } ?: visible)
                    .filter { force || container.news.isStale(it, NEWS_MAX_AGE, settings) }
            }
            if (wanted.isEmpty()) return@launch
            val outcome = attempt { container.news.refresh(wanted, settings) }
                .onFailure { Log.w(TAG, "News refresh failed", it) }
                .getOrNull() ?: return@launch
            ensureActive()
            snapshotHides()
            snapshotReads()
            // Only a round over every followed topic can say "offline"; one failing topic is just that.
            val coversAll = wanted.toSet() == visible.toSet()
            _state.update {
                it.copy(
                    offline = when {
                        coversAll -> outcome.allFailed
                        outcome.refreshed.isNotEmpty() -> false
                        else -> it.offline
                    },
                    newsAttempted = true,
                    stalled = it.stalled - outcome.refreshed,
                    now = clock(),
                )
            }
        }.also { newsJob = it }
    }

    /**
     * Freezes the stories read until now: with "Gelesene Artikel ausblenden" they leave the lists
     * from this build on (the index is built off the main thread).
     */
    private suspend fun snapshotReads() {
        val read = withContext(Dispatchers.Default) {
            attempt { container.history.state.value.read }.onFailure { Log.w(TAG, "Reading history unavailable", it) }.getOrNull()
        } ?: return
        readAtBuild.value = read
    }

    /** Freezes what "nur lesbare Artikel" hides until the next refresh (file read off the main thread). */
    private suspend fun snapshotHides() {
        val snapshot = withContext(Dispatchers.IO) {
            attempt { ReadabilityHides.of(container.readability.blockedDomains(), container.prefetcher.status.value, container.prefetcher.lengths.value) }
                .onFailure { Log.w(TAG, "Readability unavailable", it) }
                .getOrNull()
        } ?: return
        hides.value = snapshot
    }

    private suspend fun showCachedWeather() {
        val cached = attempt { container.weather.cached() }
            .onFailure { Log.w(TAG, "Cached weather unavailable", it) }
            .getOrNull() ?: return
        val ui = withContext(Dispatchers.Default) { cached.toWeatherUi(container.settings.current().language) }
        // Never overwrite a live result that arrived first.
        _state.update { if (it.weather == null) it.copy(weather = ui) else it }
    }

    /**
     * The feed on screen: repository state × feed settings × readable-only snapshot × the
     * verdicts of "nur vollständige Artikel" × the reading history ([ReadFilter]: hidden stories
     * at once, read ones as of the last build), rebuilt off the main thread.
     *
     * A build (new news, other settings, a new snapshot) applies removals; between builds new
     * verdicts only append articles, and what a list showed once stays ([keptAfter]). The lists
     * go on from the order they had ([FeedOrder]): older pages are appended, never sorted in
     * above what is on screen; only a refresh sorts a list afresh. A topic whose load of older
     * articles failed shows all it holds (its list has nothing to wait for).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeFeed() {
        val prefs = container.settings.settings
            .map { FeedPrefs(it) to it.weatherCards }
            .distinctUntilChanged()
            .catch { e -> Log.w(TAG, "Settings unavailable for the feed", e) }
        // Only final verdicts matter for the lists; none at all while the setting is off (no rebuilds).
        val verdicts = container.settings.settings
            .map { it.readableOnly }
            .distinctUntilChanged()
            .flatMapLatest { on ->
                if (on) container.prefetcher.status.map(Verification::settled).distinctUntilChanged() else flowOf(emptyMap())
            }
            .catch { e -> Log.w(TAG, "Article verdicts unavailable", e) }
        // Hidden stories follow the history at once; read ones only as of the last build (readAtBuild).
        val hidden = container.history.state
            .map { it.hidden }
            .distinctUntilChanged()
            .flowOn(Dispatchers.Default)
        val reads = combine(hidden, readAtBuild) { h, r -> ReadFilter(h, r) }.distinctUntilChanged()
        val filters = combine(hides, reads) { h, r -> h to r }
        viewModelScope.launch {
            var kept = emptyMap<String, Set<String>>()
            var built: Triple<Any?, FeedPrefs?, ReadabilityHides?>? = null
            var order = FeedOrder.NONE
            // The timeline waits for every topic that can still page – except the ones given up on.
            val stalled = _state.map { it.stalled }.distinctUntilChanged()
            val newsAndPaging = combine(container.news.state, skipped, stalled) { news, skip, stall -> Triple(news, skip, stall) }
            combine(newsAndPaging, prefs, filters, verdicts, container.prefetcher.waitingForNetwork) { n, p, f, v, off ->
                FeedInputs(n.first, p.first, p.second, f.first, v, off, n.second, n.third, f.second)
            }.collectLatest { inputs ->
                val news = inputs.news
                val feedPrefs = inputs.prefs
                val build = Triple<Any?, FeedPrefs?, ReadabilityHides?>(news.topics, feedPrefs, inputs.hides)
                val before = built
                // Lengths are published before verdicts: a verdict that arrives knows its length.
                val long = if (feedPrefs.hideLong) LongReads.longIds(container.prefetcher.lengths.value) else emptySet()
                if (before == null || before.first !== build.first || before.second != build.second || before.third != build.third) {
                    kept = keptForBuild(kept, inputs.verdicts, long)
                    built = build
                }
                val verification = Verification(feedPrefs.readableOnly, inputs.verdicts, kept, long)
                val open = feedPrefs.pageable - news.endReached - inputs.skipped
                val result = withContext(Dispatchers.Default) {
                    buildFeed(news.topics, feedPrefs, clock(), inputs.hides, verification, open, order, inputs.stalled, inputs.reads)
                }
                val feed = result.feed
                order = result.order
                if (feedPrefs.readableOnly) kept = keptAfter(kept, feed)
                _state.update {
                    it.copy(
                        prefs = feedPrefs,
                        weatherCards = inputs.cards,
                        feed = feed,
                        loadingTopics = news.loading,
                        newsWarmed = news.warmed,
                        loadingMore = news.loadingMore,
                        endReached = news.endReached,
                        verifyOffline = feedPrefs.readableOnly && inputs.offline,
                    )
                }
            }
        }
    }

    /** What the feed is built from. */
    private class FeedInputs(
        val news: NewsRepository.State,
        val prefs: FeedPrefs,
        val cards: Set<WeatherCard>,
        val hides: ReadabilityHides,
        val verdicts: Map<String, ArticleStatus>,
        val offline: Boolean,
        val skipped: Set<String>,
        /** Topics whose last load of older articles failed. */
        val stalled: Set<String>,
        /** What the reading history leaves out. */
        val reads: ReadFilter,
    )

    /**
     * Writes the weather texts (warnings, clothing advice, headline) again when the interface
     * language changes – from the snapshot on screen, without a new fetch. Also catches a fetch
     * that started before the change and arrives in the old language.
     */
    private fun observeLanguage() {
        val languages = container.settings.settings
            .map { it.language }
            .distinctUntilChanged()
            .catch { e -> Log.w(TAG, "Settings unavailable for the weather language", e) }
        viewModelScope.launch {
            combine(_state.map { it.weather }.distinctUntilChanged(), languages) { weather, lang -> weather to lang }
                .collectLatest { (weather, lang) ->
                    if (weather == null || weather.language == lang) return@collectLatest
                    val ui = withContext(Dispatchers.Default) { weather.inLanguage(lang) }
                    // Only if no newer snapshot arrived meanwhile (that one is checked on its own).
                    _state.update { if (it.weather === weather) it.copy(weather = ui) else it }
                }
        }
    }

    /** Reloads when the user changes places/time window or the sources of a followed topic. */
    private fun observeInputs() {
        viewModelScope.launch {
            container.settings.settings
                .map { WeatherInputs(it) }
                .distinctUntilChanged()
                .drop(1)
                .catch { e -> Log.w(TAG, "Settings unavailable", e) }
                .collect { refreshWeather(force = true) }
        }
        viewModelScope.launch {
            var previous: Map<String, List<String>>? = null
            container.settings.settings
                .map(::sourceSignatures)
                .distinctUntilChanged()
                .catch { e -> Log.w(TAG, "Settings unavailable", e) }
                .collect { signatures ->
                    val before = previous
                    previous = signatures
                    if (before == null) return@collect
                    val changed = signatures.filter { (id, sources) -> before[id] != sources }.keys
                    if (changed.isNotEmpty()) refreshNews(force = true, topicIds = changed)
                }
        }
    }

    /** The settings that change what the weather call returns. */
    private data class WeatherInputs(val home: Place, val day: Place, val start: Int, val end: Int) {
        constructor(settings: AppSettings) :
            this(settings.home, settings.day, settings.dayStartHour, settings.dayEndHour)
    }

    companion object {
        private const val TAG = "TodayViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L
        private val WEATHER_MAX_AGE: Duration = Duration.ofMinutes(10)
        private val NEWS_MAX_AGE: Duration = Duration.ofMinutes(15)
        private val NEWS_RETRY_AFTER: Duration = Duration.ofMinutes(3)

        /** Calls in a row that may find only quiet days before a topic's search counts as stuck. */
        private const val MAX_EMPTY_ROUNDS = 8

        /** Topics paged at a time for the timeline with light effects. */
        private const val OLDER_TOPICS_LIGHT = 2

        /** Failures (while other topics answered) after which the timeline goes on without a topic. */
        private const val OLDER_GIVE_UP = 2

        /**
         * What every followed topic reads (source ids and addresses): a new edition or a newly
         * followed topic changes it. "Nur lesbare Artikel" is part of every topic's signature:
         * the repository drops the walled sites' articles while fetching, so switching it off
         * must fetch every topic again – top stories, sections and places too, whose addresses
         * stay the same either way. The seed walls keep the signature stable while the phone
         * learns new ones.
         */
        internal fun sourceSignatures(settings: AppSettings): Map<String, List<String>> =
            Topics.visible(settings).map { it.id }.associateWith { id ->
                TopicSources.resolve(id, settings, KnownWalls.DOMAINS).map { "${it.id} ${it.url}" } +
                    "$READABLE_ONLY_MARK${settings.readableOnly}"
            }

        private const val READABLE_ONLY_MARK = "readable-only="

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MorgenbriefingApp
                TodayViewModel(container = app.container)
            }
        }
    }
}

/** The same weather in [lang]: the snapshot's texts derived again, or this one when they already are. */
internal fun WeatherUi.inLanguage(lang: AppLanguage): WeatherUi =
    if (language == lang) this else snapshot.toWeatherUi(lang)

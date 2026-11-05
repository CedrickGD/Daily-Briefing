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
import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.SettingsOps
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.ui.brief.WeatherUi
import com.cedrickgd.morgenbriefing.ui.brief.toWeatherUi
import com.cedrickgd.morgenbriefing.ui.news.FeedPrefs
import com.cedrickgd.morgenbriefing.ui.news.NewsFeed
import com.cedrickgd.morgenbriefing.ui.news.ReadabilityHides
import com.cedrickgd.morgenbriefing.ui.news.buildNewsFeed
import com.cedrickgd.morgenbriefing.util.attempt
import com.cedrickgd.morgenbriefing.util.flatten
import com.cedrickgd.morgenbriefing.util.isOlderThan
import com.cedrickgd.morgenbriefing.util.isWithin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    /** Topic ids whose last load of older articles brought nothing (the user may ask again). */
    val stalled: Set<String> = emptySet(),
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
 * hiding sources from the feed, the endless topic lists ([loadMore]) and warming the articles on
 * screen ([prefetch]).
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

    /** What the prefetcher knows about each article's readability, by article id. */
    val articleStatus: StateFlow<Map<String, ArticleStatus>> get() = container.prefetcher.status

    /**
     * The readable-only snapshot the feed is built with. Taken when a refresh ends (and once at
     * start), never while scrolling, so articles do not vanish under the user's finger.
     */
    private val hides = MutableStateFlow(ReadabilityHides.NONE)

    private var weatherJob: Job? = null
    private var newsJob: Job? = null
    private var lastWeatherAttempt: Instant? = null
    private var lastNewsAttempt: Instant? = null
    private val loadMoreJobs = HashMap<String, Job>()

    /** What the screen asked the prefetcher to warm (cancelled when it is left, renewed when it is back). */
    private val prefetchSession = PrefetchSession()

    init {
        viewModelScope.launch { showCachedWeather() }
        refreshWeather()
        observeFeed()
        viewModelScope.launch {
            attempt { container.news.warm() }.onFailure { Log.w(TAG, "News cache unavailable", it) }
            snapshotHides()
            refreshNews(force = false)
        }
        observeInputs()
        observeLanguage()
    }

    /** Pull-to-refresh: weather and news in parallel; joins refreshes that are already running. */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, now = clock(), stalled = emptySet()) }
        viewModelScope.launch {
            try {
                joinAll(refreshWeather(), refreshNews(force = true))
            } finally {
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

    /** Shows or hides a topic in the feed (the "Themen" sheet; same setting as the discover screen). */
    fun setTopicVisible(topicId: String, visible: Boolean) {
        viewModelScope.launch {
            attempt {
                container.settings.update { settings -> SettingsOps.setFollowed(settings, topicId, visible) }
            }.onFailure { Log.w(TAG, "Could not store topic visibility", it) }
        }
    }

    /** Stores a new topic order (the "Themen" sheet). */
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
     * and only while the topic can page; a load that brings nothing marks the topic as stalled,
     * so the list offers "Weiter zurück suchen" instead of asking again by itself.
     */
    fun loadMore(topicId: String) {
        if (loadMoreJobs[topicId]?.isActive == true) return
        if (!_state.value.canLoadMore(topicId)) return
        _state.update { it.copy(stalled = it.stalled - topicId) }
        loadMoreJobs[topicId] = viewModelScope.launch {
            val settings = attempt { container.settings.current() }.getOrNull() ?: return@launch
            if (!container.news.canLoadMore(topicId, settings)) return@launch
            val grew = attempt { container.news.loadMore(topicId, settings) }
                .onFailure { Log.w(TAG, "Loading older articles of $topicId failed", it) }
                .getOrDefault(false)
            if (!grew && topicId !in container.news.state.value.endReached) {
                _state.update { it.copy(stalled = it.stalled + topicId) }
            }
        }
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

    /** The screen was left: stop warming what it asked for ([onResume] asks again). */
    fun stopPrefetch() {
        val ids = prefetchSession.stop()
        if (ids.isEmpty()) return
        attempt { container.prefetcher.cancel(ids) }.onFailure { Log.w(TAG, "Prefetch cancel failed", it) }
    }

    private fun sendPrefetch(items: List<NewsItem>) {
        val prefs = _state.value.prefs ?: return
        if (items.isEmpty()) return
        attempt { container.prefetcher.prefetch(items, prefs.prefetch, prefs.editionLanguage.ifBlank { null }) }
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
            val visible = Topics.visible(settings).map { it.id }
            // isStale also counts a topic read from other sources (a new edition, a moved region) as stale.
            val wanted = (topicIds?.filter { it in visible } ?: visible)
                .filter { force || container.news.isStale(it, NEWS_MAX_AGE, settings) }
            if (wanted.isEmpty()) return@launch
            val outcome = attempt { container.news.refresh(wanted, settings) }
                .onFailure { Log.w(TAG, "News refresh failed", it) }
                .getOrNull() ?: return@launch
            ensureActive()
            snapshotHides()
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

    /** Freezes what "nur lesbare Artikel" hides until the next refresh (file read off the main thread). */
    private suspend fun snapshotHides() {
        val snapshot = withContext(Dispatchers.IO) {
            attempt { ReadabilityHides.of(container.readability.blockedDomains(), container.prefetcher.status.value) }
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

    /** The feed on screen: repository state × feed settings × readable-only snapshot, rebuilt off the main thread. */
    private fun observeFeed() {
        val prefs = container.settings.settings
            .map { FeedPrefs(it) to it.weatherCards }
            .distinctUntilChanged()
            .catch { e -> Log.w(TAG, "Settings unavailable for the feed", e) }
        viewModelScope.launch {
            combine(container.news.state, prefs, hides) { news, p, h -> Triple(news, p, h) }
                .collectLatest { (news, p, h) ->
                    val (feedPrefs, cards) = p
                    val feed = withContext(Dispatchers.Default) { buildNewsFeed(news.topics, feedPrefs, clock(), h) }
                    _state.update {
                        it.copy(
                            prefs = feedPrefs,
                            weatherCards = cards,
                            feed = feed,
                            loadingTopics = news.loading,
                            newsWarmed = news.warmed,
                            loadingMore = news.loadingMore,
                            endReached = news.endReached,
                        )
                    }
                }
        }
    }

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

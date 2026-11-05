package com.cedrickgd.morgenbriefing.ui.settings

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
import com.cedrickgd.morgenbriefing.data.SettingsOps
import com.cedrickgd.morgenbriefing.data.article.KnownWalls
import com.cedrickgd.morgenbriefing.data.model.AccentId
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.EffectsMode
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsAge
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.PrefetchMode
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.ThemeMode
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.data.model.WeatherSnapshot
import com.cedrickgd.morgenbriefing.perf.TierAssessment
import com.cedrickgd.morgenbriefing.data.news.FeedCatalog
import com.cedrickgd.morgenbriefing.data.news.Regions
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.data.remote.FeedError
import com.cedrickgd.morgenbriefing.data.remote.FeedException
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.SettingsStrings
import com.cedrickgd.morgenbriefing.location.LocationException
import com.cedrickgd.morgenbriefing.location.LocationIssue
import com.cedrickgd.morgenbriefing.util.attempt
import com.cedrickgd.morgenbriefing.util.flatten
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** "Mein Standort" in the place picker. */
@Immutable
data class LocationState(
    val searching: Boolean = false,
    /** Why the last attempt failed; null when it worked or never ran. */
    val issue: LocationIssue? = null,
)

/** "Quelle hinzufügen": the feed behind the typed address is looked up before it is saved. */
@Immutable
sealed interface AddFeedState {
    data object Idle : AddFeedState
    data object Checking : AddFeedState
    data class Found(val url: String, val title: String, val itemCount: Int) : AddFeedState
    data class Failed(val error: FeedError, val httpCode: Int? = null) : AddFeedState
    data object Saved : AddFeedState
}

/** One-off snackbars of the settings; worded in the interface language by [text]. */
@Immutable
sealed interface SettingsMessage {
    data class BriefingTime(val hour: Int, val minute: Int) : SettingsMessage
    data object KeepOneTopic : SettingsMessage
    data class TopicCreated(val title: String, val search: Boolean) : SettingsMessage
    data object TopicDeleted : SettingsMessage
    data class FeedAdded(val name: String) : SettingsMessage
    data object FeedRemoved : SettingsMessage
    data class PlaceAdded(val name: String) : SettingsMessage
    data class PlaceRemoved(val name: String) : SettingsMessage
    data object SaveFailed : SettingsMessage

    /** The snackbar text in [lang]. */
    fun text(lang: AppLanguage): String {
        val s = SettingsStrings.of(lang)
        return when (this) {
            is BriefingTime -> s.briefingTimeSet(clockLabel(hour, minute))
            KeepOneTopic -> s.keepOneTopic
            is TopicCreated -> if (search) s.topicCreatedSearch(title) else s.topicCreatedAddFeed(title)
            TopicDeleted -> s.topicDeleted
            is FeedAdded -> s.feedAdded(name)
            FeedRemoved -> s.feedRemoved
            is PlaceAdded -> s.placeAdded(name)
            is PlaceRemoved -> s.placeRemoved(name)
            SaveFailed -> s.saveFailed
        }
    }
}

/**
 * Settings: every change is written straight to the DataStore (theme, language and topics apply
 * live, the alarm is re-synced by [MorgenbriefingApp]). Also runs the place search and "Mein
 * Standort", the feed lookup for own sources, and the mini weather of the place cards, and
 * exposes how each source fared in the last refresh. The in-app updater has its own
 * [UpdateViewModel].
 */
class SettingsViewModel(
    private val container: AppContainer,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(BERLIN) },
) : ViewModel() {

    /** Null until the DataStore emitted. */
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .catch { e -> Log.w(TAG, "Settings unavailable", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The last fetch of every source, by source id (for the sources section). */
    val sourceStatus: StateFlow<Map<String, SourceStatus>> = container.news.state
        .map { state -> state.topics.values.flatMap { it.sources }.associateBy { it.sourceId } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyMap())

    /**
     * The sites the reader cannot show (seed + learned), as the news engine leaves them out.
     * The store reads its file on first access, so this starts off the main thread.
     */
    val blockedDomains: StateFlow<Set<String>> = flow { emitAll(container.readability.blocked) }
        .flowOn(Dispatchers.IO)
        .catch { e -> Log.w(TAG, "Readability unavailable", e) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), KnownWalls.DOMAINS)

    /** What the phone is (tier and why), for the "Effekte" hint; null for the first milliseconds. */
    val deviceTier: StateFlow<TierAssessment?> get() = container.performance.assessment

    private val _placeSearch = MutableStateFlow(PlaceSearchState())
    val placeSearch: StateFlow<PlaceSearchState> = _placeSearch.asStateFlow()

    private val _location = MutableStateFlow(LocationState())
    val location: StateFlow<LocationState> = _location.asStateFlow()

    private val _addFeed = MutableStateFlow<AddFeedState>(AddFeedState.Idle)
    val addFeed: StateFlow<AddFeedState> = _addFeed.asStateFlow()

    private val _weather = MutableStateFlow<WeatherSnapshot?>(null)

    /**
     * Newest known weather (live from "Heute", else the stored copy) for the mini weather on the
     * place cards. Null until one is known.
     */
    val weather: StateFlow<WeatherSnapshot?> = _weather.asStateFlow()

    private val _weatherLoading = MutableStateFlow(true)

    /** True until the first weather (stored or live) arrived or the stored copy turned out missing. */
    val weatherLoading: StateFlow<Boolean> = _weatherLoading.asStateFlow()

    private val _messages = Channel<SettingsMessage>(Channel.BUFFERED)

    /** One-off snackbars. */
    val messages: Flow<SettingsMessage> = _messages.receiveAsFlow()

    private var searchJob: Job? = null
    private var locationJob: Job? = null
    private var addFeedJob: Job? = null

    init {
        loadWeather()
        // Live weather fetched by "Heute" (or anywhere else) updates the place cards right away.
        viewModelScope.launch {
            container.weather.latest
                .filterNotNull()
                .catch { e -> Log.w(TAG, "Weather updates unavailable", e) }
                .collect {
                    _weather.value = it
                    _weatherLoading.value = false
                }
        }
        // No stored copy and no live answer for a while: the cards say so instead of waiting forever.
        viewModelScope.launch {
            delay(WEATHER_WAIT_MS)
            _weatherLoading.value = false
        }
    }

    /** Screen resumed: fresh weather for the place cards. */
    fun onResume() {
        loadWeather()
    }

    fun setThemeMode(mode: ThemeMode) = edit { it.copy(themeMode = mode) }

    fun setAccent(accent: AccentId) = edit { it.copy(accent = accent) }

    fun setEffects(mode: EffectsMode) = edit { it.copy(effects = mode) }

    /** The interface language; null follows the phone. Applies at once (AppRoot provides it). */
    fun setUiLanguage(language: AppLanguage?) = edit { it.copy(uiLanguage = language) }

    /** Where the news come from; a feed still on the old edition's defaults moves along ([EditionChange]). */
    fun setEdition(edition: NewsEdition) = edit { EditionChange.apply(it, edition) }

    fun setNotificationsEnabled(enabled: Boolean) = edit { it.copy(notificationsEnabled = enabled) }

    fun setNotifyTime(hour: Int, minute: Int) {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        edit(success = SettingsMessage.BriefingTime(h, m)) { it.copy(notifyHour = h, notifyMinute = m) }
    }

    /**
     * Stores [place] for [target]. A new home place in Germany also moves the "Region" topic to
     * its Bundesland when the region was never chosen by hand (the first place of a new install).
     * [PlaceTarget.LOCAL] follows the place's local news instead.
     */
    fun setPlace(target: PlaceTarget, place: Place) {
        when (target) {
            PlaceTarget.HOME -> edit {
                val guessed = Regions.guess(place)?.key
                it.copy(home = place, region = if (it.regionChosenByUser || guessed == null) it.region else guessed)
            }
            PlaceTarget.DAY -> edit { it.copy(day = place) }
            PlaceTarget.LOCAL -> edit(success = SettingsMessage.PlaceAdded(place.name)) { SettingsOps.addLocalPlace(it, place).first }
        }
    }

    /** Stops following a place and forgets it (and its cached articles). */
    fun removeLocalPlace(topicId: String) {
        viewModelScope.launch {
            var name: String? = null
            val saved = persist { current ->
                name = Topics.placeFor(topicId, current)?.name
                SettingsOps.removeLocalPlace(current, topicId)
            }
            if (saved) {
                attempt { container.news.forget(topicId) }.onFailure { Log.w(TAG, "Could not forget $topicId", it) }
                name?.let { _messages.send(SettingsMessage.PlaceRemoved(it)) }
            }
        }
    }

    fun setDayWindow(start: Int, end: Int) {
        val (s, e) = DayWindow.normalize(start, end)
        edit { it.copy(dayStartHour = s, dayEndHour = e) }
    }

    /** Follows a topic (own topic, place) again, at the end of the feed. */
    fun follow(id: String) = edit { SettingsOps.follow(it, id) }

    /** Stops following a topic; the last followed one stays, with a hint. */
    fun unfollow(id: String) {
        viewModelScope.launch {
            var refused = false
            val saved = persist { current ->
                refused = !SettingsOps.canUnfollow(current, id)
                SettingsOps.unfollow(current, id)
            }
            if (saved && refused) _messages.send(SettingsMessage.KeepOneTopic)
        }
    }

    /** Stores a new order of the followed topics (after a drag or a move action). */
    fun reorderFollowed(order: List<String>) = edit { SettingsOps.reorder(it, order) }

    /**
     * Creates an own topic from [draft] and follows it at the end. [onCreated] gets its id once it
     * is stored (the screen then asks for the first feed of a feed topic).
     */
    internal fun addCustomTopic(draft: TopicDraft, onCreated: (String) -> Unit = {}) {
        if (!draft.canSave) return
        val topic = draft.toTopic(Topics.newCustomId())
        viewModelScope.launch {
            val saved = persist { SettingsOps.addCustomTopic(it, topic) }
            if (saved) {
                onCreated(topic.id)
                _messages.send(SettingsMessage.TopicCreated(topic.title, search = topic.query != null))
            }
        }
    }

    /** Renames, re-icons, recolors an own topic or changes its search. */
    internal fun updateCustomTopic(id: String, draft: TopicDraft) {
        if (!draft.canSave) return
        edit { current ->
            current.copy(customTopics = current.customTopics.map { if (it.id == id) draft.toTopic(id) else it })
        }
    }

    /** Deletes an own topic with its sources; its cached articles go too. */
    fun deleteCustomTopic(id: String) {
        viewModelScope.launch {
            val saved = persist { SettingsOps.deleteCustomTopic(it, id) }
            if (saved) {
                attempt { container.news.forget(id) }.onFailure { Log.w(TAG, "Could not forget $id", it) }
                _messages.send(SettingsMessage.TopicDeleted)
            }
        }
    }

    /** Switches a built-in or own source on or off. */
    fun setSourceEnabled(sourceId: String, enabled: Boolean) = edit {
        it.copy(disabledFeeds = if (enabled) it.disabledFeeds - sourceId else it.disabledFeeds + sourceId)
    }

    /** All sources of [topicId] back on. */
    fun enableAllSources(topicId: String) = edit { current ->
        val ids = TopicSources.resolve(topicId, current, blockedDomains.value, includeDisabled = true).map { it.id }.toSet()
        current.copy(disabledFeeds = current.disabledFeeds - ids)
    }

    /** The region preset of the "Region" topic; chosen by hand from now on. */
    fun setRegion(key: String) = edit { it.copy(region = Regions.byKey(key).key, regionChosenByUser = true) }

    fun setSectionSize(size: Int) = edit { it.copy(sectionSize = size.coerceIn(AppSettings.MIN_SECTION_SIZE, AppSettings.MAX_SECTION_SIZE)) }

    fun setShowImages(show: Boolean) = edit { it.copy(showImages = show) }

    fun setReadInApp(inApp: Boolean) = edit { it.copy(readInApp = inApp) }

    fun setReadableOnly(only: Boolean) = edit { it.copy(readableOnly = only) }

    fun setPrefetch(mode: PrefetchMode) = edit { it.copy(prefetch = mode) }

    fun setNewsAge(age: NewsAge) = edit { it.copy(newsAge = age) }

    fun setWeatherCard(card: WeatherCard, shown: Boolean) = edit {
        it.copy(weatherCards = if (shown) it.weatherCards + card else it.weatherCards - card)
    }

    /**
     * "Quelle hinzufügen": looks the feed up behind [input] (a feed URL or a website) and reports
     * what it found in [addFeed]; [saveFoundFeed] stores it.
     */
    fun lookUpFeed(input: String) {
        addFeedJob?.cancel()
        val text = input.trim()
        if (text.isEmpty()) {
            _addFeed.value = AddFeedState.Idle
            return
        }
        addFeedJob = viewModelScope.launch {
            _addFeed.value = AddFeedState.Checking
            val result = attempt { container.rss.discover(text) }
            ensureActive()
            _addFeed.value = result.fold(
                onSuccess = { AddFeedState.Found(it.url, it.title, it.itemCount) },
                onFailure = { e ->
                    Log.i(TAG, "Feed lookup failed: ${e.message}")
                    val failure = e as? FeedException
                    AddFeedState.Failed(failure?.error ?: FeedError.NO_FEED_FOUND, failure?.httpCode)
                },
            )
        }
    }

    /** Stores the feed found by [lookUpFeed] for [topicId] under [name]. */
    fun saveFoundFeed(topicId: String, name: String) {
        val found = _addFeed.value as? AddFeedState.Found ?: return
        val clean = name.trim().ifEmpty { found.title }.take(TopicDraft.MAX_TITLE)
        viewModelScope.launch {
            val saved = persist { current ->
                if (current.customFeeds.any { it.url == found.url && it.topicId == topicId }) return@persist current
                current.copy(customFeeds = current.customFeeds + FeedSource(FeedCatalog.newCustomId(), clean, found.url, topicId, builtIn = false))
            }
            if (saved) {
                _addFeed.value = AddFeedState.Saved
                _messages.send(SettingsMessage.FeedAdded(clean))
            }
        }
    }

    fun resetAddFeed() {
        addFeedJob?.cancel()
        _addFeed.value = AddFeedState.Idle
    }

    /** Deletes an own source. */
    fun removeCustomFeed(sourceId: String) = edit(success = SettingsMessage.FeedRemoved) {
        it.copy(customFeeds = it.customFeeds.filterNot { f -> f.id == sourceId }, disabledFeeds = it.disabledFeeds - sourceId)
    }

    /** Debounced (350 ms) geocoding search; queries shorter than 2 characters clear the list. */
    fun onPlaceQueryChange(query: String) {
        _placeSearch.update { it.copy(query = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < PLACE_QUERY_MIN_LENGTH) {
            _placeSearch.update { it.copy(loading = false, failed = false, results = emptyList(), searchedFor = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _placeSearch.update { it.copy(loading = true, failed = false) }
            val result = attempt { container.weather.search(trimmed, (settings.value ?: container.settings.current()).language) }.flatten()
            ensureActive()
            val places = result.getOrNull()
            if (places == null) Log.w(TAG, "Place search failed", result.exceptionOrNull())
            _placeSearch.update {
                if (places != null) {
                    it.copy(loading = false, failed = false, results = places, searchedFor = trimmed)
                } else {
                    it.copy(loading = false, failed = true)
                }
            }
        }
    }

    /** Fresh, empty search whenever the picker opens. */
    fun resetPlaceSearch() {
        searchJob?.cancel()
        locationJob?.cancel()
        _placeSearch.value = PlaceSearchState()
        _location.value = LocationState()
    }

    /**
     * "Mein Standort": asks the system for the place and stores it for [target] through [onFound];
     * [needsTown] (local news) refuses a spot the geocoder cannot name.
     */
    fun useCurrentLocation(needsTown: Boolean, onFound: (Place) -> Unit) {
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch {
            _location.value = LocationState(searching = true)
            val result = attempt { container.location.currentPlace((settings.value ?: container.settings.current()).language, needsTown) }
            ensureActive()
            result.fold(
                onSuccess = { place ->
                    _location.value = LocationState()
                    onFound(place)
                },
                onFailure = { e ->
                    Log.i(TAG, "Location unavailable: ${e.message}")
                    _location.value = LocationState(issue = (e as? LocationException)?.issue ?: LocationIssue.UNAVAILABLE)
                },
            )
        }
    }

    private fun loadWeather() {
        viewModelScope.launch {
            val cached = attempt { container.weather.cached() }
                .onFailure { Log.w(TAG, "Cached weather unreadable", it) }
                .getOrNull()
            if (cached != null) {
                _weather.value = cached
                _weatherLoading.value = false
            }
        }
    }

    private fun edit(success: SettingsMessage? = null, transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            if (persist(transform) && success != null) _messages.send(success)
        }
    }

    /** Writes [transform] atomically; a failure becomes a snackbar. Returns whether it was saved. */
    private suspend fun persist(transform: (AppSettings) -> AppSettings): Boolean {
        val result = attempt { container.settings.update(transform) }
        if (result.isFailure) {
            Log.w(TAG, "Settings update failed", result.exceptionOrNull())
            _messages.send(SettingsMessage.SaveFailed)
        }
        return result.isSuccess
    }

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val WEATHER_WAIT_MS = 20_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MorgenbriefingApp
                SettingsViewModel(container = app.container)
            }
        }
    }
}

package com.cedrickgd.morgenbriefing.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cedrickgd.morgenbriefing.data.model.AccentId
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.ListenSettings
import com.cedrickgd.morgenbriefing.data.model.NewsAge
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.ThemeMode
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.data.news.Regions
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.IOException
import java.util.Locale

/** The one DataStore instance per process (DataStore forbids two instances on the same file). */
private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Preferences DataStore named "settings". Nothing secret lives here any more (no tokens, no
 * API keys), so the store takes part in Android backups. Places, custom topics, custom feeds,
 * the followed topics and recent searches are stored as JSON strings.
 *
 * Settings of older versions are migrated on read ([SettingsMigration]): the old topic order
 * and hidden topics become [AppSettings.followed], installs without a news edition read the
 * German news they always had. The next write stores the migrated values.
 *
 * The internal constructor takes any preferences store (tests use one in a temporary file).
 */
class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsStore)

    /**
     * Always emits; unreadable storage falls back to the defaults instead of crashing. The
     * stored JSON (places, topics, feeds) is decoded off the main thread, whichever thread
     * collects – the screens collect on the main thread.
     */
    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    /**
     * Atomically maps the stored settings through [transform] and writes every field back.
     * @throws IOException if DataStore cannot persist the change.
     */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    // ---------------------------------------------------------------------------------------
    // Operations the screens use (pure versions in SettingsOps)
    // ---------------------------------------------------------------------------------------

    /** Follows a topic (category, sub-topic, place or own topic), at [index] or at the end. */
    suspend fun follow(topicId: String, index: Int? = null) = update { SettingsOps.follow(it, topicId, index) }

    /** Stops following a topic; the last followed topic stays. */
    suspend fun unfollow(topicId: String) = update { SettingsOps.unfollow(it, topicId) }

    /** Stores a new order of the followed topics. */
    suspend fun reorderFollowed(order: List<String>) = update { SettingsOps.reorder(it, order) }

    /** Follows the local news of [place]; returns its topic id (`local:…`). */
    suspend fun addLocalPlace(place: Place): String {
        var id = ""
        update { current -> SettingsOps.addLocalPlace(current, place).also { id = it.second }.first }
        return id
    }

    /** Removes a followed place by its topic id (`local:…`). */
    suspend fun removeLocalPlace(topicId: String) = update { SettingsOps.removeLocalPlace(it, topicId) }

    /** Follows a search as an own topic ("Midterms"); returns its topic id. */
    suspend fun followSearch(query: String): String {
        var id = ""
        update { current -> SettingsOps.followSearch(current, query).also { id = it.second }.first }
        return id
    }

    /** Remembers a search at the top of the recent searches. */
    suspend fun addRecentSearch(query: String) = update { SettingsOps.addRecentSearch(it, query) }

    suspend fun removeRecentSearch(query: String) = update { SettingsOps.removeRecentSearch(it, query) }

    suspend fun clearRecentSearches() = update { SettingsOps.clearRecentSearches(it) }

    // ---------------------------------------------------------------------------------------
    // Page state that outlives a launch (not a setting: no AppSettings field, nothing rebuilds)
    // ---------------------------------------------------------------------------------------

    /** The weather card on "Heute" is expanded (default: collapsed). */
    val weatherExpanded: Flow<Boolean> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.WEATHER_EXPANDED] ?: false }
        .distinctUntilChanged()

    suspend fun setWeatherExpanded(expanded: Boolean) {
        store.edit { it[Keys.WEATHER_EXPANDED] = expanded }
    }

    // ---------------------------------------------------------------------------------------
    // Mapping
    // ---------------------------------------------------------------------------------------

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        val home = placeOrDefault(this[Keys.HOME_PLACE], defaults.home)
        val onboardingDone = this[Keys.ONBOARDING_DONE] ?: defaults.onboardingDone
        val customTopics = decodeList(this[Keys.CUSTOM_TOPICS], Topic.serializer()).filter { it.id.isNotBlank() && it.title.isNotBlank() }
        val localPlaces = decodeList(this[Keys.LOCAL_PLACES], Place.serializer()).filter { it.name.isNotBlank() }.distinctBy { Topics.localId(it) }
        val edition = SettingsMigration.edition(this[Keys.NEWS_EDITION], onboardingDone, Locale.getDefault())
        val customIds = customTopics.map { it.id }
        val localIds = localPlaces.map { Topics.localId(it) }.toSet()
        val followed = SettingsMigration.followed(
            stored = decodeListOrNull(this[Keys.FOLLOWED], String.serializer()),
            legacyOrder = this[Keys.TOPIC_ORDER]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() },
            legacyHidden = idSet(this[Keys.HIDDEN_TOPICS]),
            onboardingDone = onboardingDone,
            edition = edition,
            customIds = customIds,
            known = { id -> Taxonomy.isBuiltIn(id) || id in customIds || id in localIds },
        )
        return AppSettings(
            themeMode = enumOrDefault(this[Keys.THEME_MODE], defaults.themeMode),
            accent = enumOrDefault(this[Keys.ACCENT], defaults.accent),
            notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED] ?: defaults.notificationsEnabled,
            notifyHour = this[Keys.NOTIFY_HOUR]?.takeIf { it in 0..23 } ?: defaults.notifyHour,
            notifyMinute = this[Keys.NOTIFY_MINUTE]?.takeIf { it in 0..59 } ?: defaults.notifyMinute,
            home = home,
            day = placeOrDefault(this[Keys.DAY_PLACE], defaults.day),
            dayStartHour = this[Keys.DAY_START_HOUR]?.takeIf { it in 0..23 } ?: defaults.dayStartHour,
            dayEndHour = this[Keys.DAY_END_HOUR]?.takeIf { it in 0..23 } ?: defaults.dayEndHour,
            onboardingDone = onboardingDone,
            lastNotifiedDate = this[Keys.LAST_NOTIFIED_DATE]?.takeIf { it.isNotBlank() },
            topicOrder = topicOrderOrDefault(this[Keys.TOPIC_ORDER], defaults.topicOrder),
            hiddenTopics = idSet(this[Keys.HIDDEN_TOPICS]),
            customTopics = customTopics,
            customFeeds = decodeList(this[Keys.CUSTOM_FEEDS], FeedSource.serializer()).filter { it.id.isNotBlank() && it.url.isNotBlank() },
            disabledFeeds = idSet(this[Keys.DISABLED_FEEDS]),
            // Installs from before the region preset existed: guess it from the home place.
            region = this[Keys.REGION]?.takeIf { it.isNotBlank() } ?: Regions.guess(home)?.key ?: defaults.region,
            regionChosenByUser = this[Keys.REGION_CHOSEN] ?: defaults.regionChosenByUser,
            sectionSize = this[Keys.SECTION_SIZE]?.coerceIn(AppSettings.MIN_SECTION_SIZE, AppSettings.MAX_SECTION_SIZE) ?: defaults.sectionSize,
            showImages = this[Keys.SHOW_IMAGES] ?: defaults.showImages,
            readInApp = this[Keys.READ_IN_APP] ?: defaults.readInApp,
            newsAge = NewsAge.fromName(this[Keys.NEWS_AGE]) ?: defaults.newsAge,
            weatherCards = this[Keys.WEATHER_CARDS]?.let { raw ->
                idSet(raw).mapNotNull { name -> WeatherCard.entries.firstOrNull { it.name == name } }.toSet()
            } ?: defaults.weatherCards,
            uiLanguage = AppLanguage.fromCode(this[Keys.UI_LANGUAGE]),
            edition = edition,
            followed = followed,
            localPlaces = localPlaces,
            readableOnly = this[Keys.READABLE_ONLY] ?: defaults.readableOnly,
            hideRead = this[Keys.HIDE_READ] ?: defaults.hideRead,
            hideLong = this[Keys.HIDE_LONG] ?: defaults.hideLong,
            prefetch = enumOrDefault(this[Keys.PREFETCH], defaults.prefetch),
            recentSearches = decodeList(this[Keys.RECENT_SEARCHES], String.serializer())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(SettingsOps.MAX_RECENT_SEARCHES),
            effects = enumOrDefault(this[Keys.EFFECTS], defaults.effects),
            listen = listenSettings(defaults.listen),
        )
    }

    /** "Vorlesen"; installs up to 3.2 have none of these keys and read the defaults (as before). */
    private fun Preferences.listenSettings(defaults: ListenSettings): ListenSettings = ListenSettings(
        engine = this[Keys.LISTEN_ENGINE]?.trim()?.takeIf { it.isNotEmpty() },
        voices = decodeVoices(this[Keys.LISTEN_VOICES]),
        voicePreference = enumOrDefault(this[Keys.LISTEN_VOICE_PREFERENCE], defaults.voicePreference),
        rate = this[Keys.LISTEN_RATE]?.let(ListenSettings::snapRate) ?: defaults.rate,
        pitch = this[Keys.LISTEN_PITCH]?.let(ListenSettings::snapPitch) ?: defaults.pitch,
        language = ListenSettings.cleanLanguage(this[Keys.LISTEN_LANGUAGE]),
    )

    /** Language → voice name; unreadable JSON or malformed entries are dropped (automatic voice). */
    private fun decodeVoices(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val decoded = try {
            AppJson.decodeFromString(VOICES_SERIALIZER, raw)
        } catch (_: SerializationException) {
            return emptyMap()
        } catch (_: IllegalArgumentException) {
            return emptyMap()
        }
        return decoded.entries
            .mapNotNull { (language, voice) ->
                val key = ListenSettings.cleanLanguage(language) ?: return@mapNotNull null
                voice.trim().takeIf { it.isNotEmpty() }?.let { key to it }
            }
            .toMap()
    }

    /** Stored order first; catalogue topics added in a newer app version are appended. */
    private fun topicOrderOrDefault(raw: String?, default: List<String>): List<String> {
        val stored = raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
        if (stored.isEmpty()) return default
        return stored + default.filterNot { it in stored }
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.THEME_MODE] = s.themeMode.name
        this[Keys.ACCENT] = s.accent.name
        this[Keys.NOTIFICATIONS_ENABLED] = s.notificationsEnabled
        this[Keys.NOTIFY_HOUR] = s.notifyHour.coerceIn(0, 23)
        this[Keys.NOTIFY_MINUTE] = s.notifyMinute.coerceIn(0, 59)
        this[Keys.HOME_PLACE] = AppJson.encodeToString(Place.serializer(), s.home)
        this[Keys.DAY_PLACE] = AppJson.encodeToString(Place.serializer(), s.day)
        this[Keys.DAY_START_HOUR] = s.dayStartHour.coerceIn(0, 23)
        this[Keys.DAY_END_HOUR] = s.dayEndHour.coerceIn(0, 23)
        this[Keys.ONBOARDING_DONE] = s.onboardingDone
        val last = s.lastNotifiedDate
        if (last.isNullOrBlank()) remove(Keys.LAST_NOTIFIED_DATE) else this[Keys.LAST_NOTIFIED_DATE] = last
        // Only read to migrate version 2 installs; kept in step so a downgrade finds its order.
        this[Keys.TOPIC_ORDER] = s.topicOrder.filter { it.isNotBlank() && ',' !in it }.distinct().joinToString(",")
        this[Keys.HIDDEN_TOPICS] = s.hiddenTopics.filter { it.isNotBlank() && ',' !in it }.sorted().joinToString(",")
        this[Keys.CUSTOM_TOPICS] = AppJson.encodeToString(ListSerializer(Topic.serializer()), s.customTopics)
        this[Keys.CUSTOM_FEEDS] = AppJson.encodeToString(ListSerializer(FeedSource.serializer()), s.customFeeds)
        this[Keys.DISABLED_FEEDS] = s.disabledFeeds.filter { it.isNotBlank() && ',' !in it }.sorted().joinToString(",")
        this[Keys.REGION] = s.region
        this[Keys.REGION_CHOSEN] = s.regionChosenByUser
        this[Keys.SECTION_SIZE] = s.sectionSize.coerceIn(AppSettings.MIN_SECTION_SIZE, AppSettings.MAX_SECTION_SIZE)
        this[Keys.SHOW_IMAGES] = s.showImages
        this[Keys.READ_IN_APP] = s.readInApp
        this[Keys.NEWS_AGE] = s.newsAge.name
        this[Keys.WEATHER_CARDS] = s.weatherCards.map { it.name }.sorted().joinToString(",")
        val language = s.uiLanguage
        if (language == null) remove(Keys.UI_LANGUAGE) else this[Keys.UI_LANGUAGE] = language.code
        this[Keys.NEWS_EDITION] = s.edition.key
        // Settings built without followed topics mean "as the old order says" (Topics.followedIds).
        this[Keys.FOLLOWED] = AppJson.encodeToString(ListSerializer(String.serializer()), Topics.followedIds(s).filter { it.isNotBlank() }.distinct())
        this[Keys.LOCAL_PLACES] = AppJson.encodeToString(ListSerializer(Place.serializer()), s.localPlaces)
        this[Keys.READABLE_ONLY] = s.readableOnly
        this[Keys.HIDE_READ] = s.hideRead
        this[Keys.HIDE_LONG] = s.hideLong
        this[Keys.PREFETCH] = s.prefetch.name
        this[Keys.RECENT_SEARCHES] = AppJson.encodeToString(ListSerializer(String.serializer()), s.recentSearches.take(SettingsOps.MAX_RECENT_SEARCHES))
        this[Keys.EFFECTS] = s.effects.name
        writeListen(s.listen)
    }

    private fun MutablePreferences.writeListen(l: ListenSettings) {
        val engine = l.engine?.trim()
        if (engine.isNullOrEmpty()) remove(Keys.LISTEN_ENGINE) else this[Keys.LISTEN_ENGINE] = engine
        this[Keys.LISTEN_VOICES] = AppJson.encodeToString(VOICES_SERIALIZER, l.voices.toSortedMap())
        this[Keys.LISTEN_VOICE_PREFERENCE] = l.voicePreference.name
        this[Keys.LISTEN_RATE] = ListenSettings.snapRate(l.rate)
        this[Keys.LISTEN_PITCH] = ListenSettings.snapPitch(l.pitch)
        val language = ListenSettings.cleanLanguage(l.language)
        if (language == null) remove(Keys.LISTEN_LANGUAGE) else this[Keys.LISTEN_LANGUAGE] = language
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private fun idSet(raw: String?): Set<String> =
        raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    private fun placeOrDefault(raw: String?, default: Place): Place {
        if (raw.isNullOrBlank()) return default
        return try {
            AppJson.decodeFromString(Place.serializer(), raw)
        } catch (_: SerializationException) {
            default
        } catch (_: IllegalArgumentException) {
            default
        }
    }

    private fun <T> decodeList(raw: String?, serializer: KSerializer<T>): List<T> = decodeListOrNull(raw, serializer).orEmpty()

    /** The decoded list, or null when [raw] is missing or unreadable (then the caller migrates or defaults). */
    private fun <T> decodeListOrNull(raw: String?, serializer: KSerializer<T>): List<T>? {
        if (raw.isNullOrBlank()) return null
        return try {
            AppJson.decodeFromString(ListSerializer(serializer), raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val NOTIFY_HOUR = intPreferencesKey("notify_hour")
        val NOTIFY_MINUTE = intPreferencesKey("notify_minute")
        val HOME_PLACE = stringPreferencesKey("home_place")
        val DAY_PLACE = stringPreferencesKey("day_place")
        val DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val DAY_END_HOUR = intPreferencesKey("day_end_hour")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val LAST_NOTIFIED_DATE = stringPreferencesKey("last_notified_date")
        val TOPIC_ORDER = stringPreferencesKey("topic_order")
        val HIDDEN_TOPICS = stringPreferencesKey("hidden_topics")
        val CUSTOM_TOPICS = stringPreferencesKey("custom_topics")
        val CUSTOM_FEEDS = stringPreferencesKey("custom_feeds")
        val DISABLED_FEEDS = stringPreferencesKey("disabled_feeds")
        val REGION = stringPreferencesKey("region")
        val REGION_CHOSEN = booleanPreferencesKey("region_chosen")
        val SECTION_SIZE = intPreferencesKey("section_size")
        val SHOW_IMAGES = booleanPreferencesKey("show_images")
        val READ_IN_APP = booleanPreferencesKey("read_in_app")
        val NEWS_AGE = stringPreferencesKey("news_age")
        val WEATHER_CARDS = stringPreferencesKey("weather_cards")

        /** The compact weather card of "Heute" is expanded; read and written on its own ([weatherExpanded]). */
        val WEATHER_EXPANDED = booleanPreferencesKey("today_weather_expanded")
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val NEWS_EDITION = stringPreferencesKey("news_edition")
        val FOLLOWED = stringPreferencesKey("followed_topics")
        val LOCAL_PLACES = stringPreferencesKey("local_places")
        val READABLE_ONLY = booleanPreferencesKey("readable_only")

        /** "Gelesene Artikel ausblenden"; missing (older installs) = off. */
        val HIDE_READ = booleanPreferencesKey("hide_read")

        /** "Lange Artikel ausblenden"; missing (older installs) = on, like a new install. */
        val HIDE_LONG = booleanPreferencesKey("hide_long")
        val PREFETCH = stringPreferencesKey("prefetch")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")

        /** [com.cedrickgd.morgenbriefing.data.model.EffectsMode] by name; missing (installs up to 3.1) = automatic. */
        val EFFECTS = stringPreferencesKey("effects")

        // "Vorlesen" (since 3.3); missing = as before: default engine, automatic voice, normal speed.
        /** Engine package; missing = the phone's default engine. */
        val LISTEN_ENGINE = stringPreferencesKey("listen_engine")

        /** JSON object language → voice name. */
        val LISTEN_VOICES = stringPreferencesKey("listen_voices")
        val LISTEN_VOICE_PREFERENCE = stringPreferencesKey("listen_voice_preference")
        val LISTEN_RATE = floatPreferencesKey("listen_rate")
        val LISTEN_PITCH = floatPreferencesKey("listen_pitch")

        /** Fixed reading language (ISO 639-1); missing = each article in its own language. */
        val LISTEN_LANGUAGE = stringPreferencesKey("listen_language")
    }

    private companion object {
        val VOICES_SERIALIZER = MapSerializer(String.serializer(), String.serializer())
    }
}

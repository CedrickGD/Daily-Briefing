package com.cedrickgd.morgenbriefing.data.model

import com.cedrickgd.morgenbriefing.data.news.Regions
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import kotlinx.serialization.Serializable

@Serializable
data class Place(
    val name: String,
    val lat: Double,
    val lon: Double,
    /** e.g. "Sachsen, Deutschland" — only for display in the picker. */
    val region: String = "",
    /** ISO 3166-1 alpha-2 (`DE`), for the little flag; empty when unknown. */
    val countryCode: String = "",
    /**
     * The name in the language of the place's country ([NewsEdition.forCountry]) when [name]
     * (in the interface language) differs: "Milano" for "Mailand". Local news are searched with
     * it ([newsName]); empty when it is the same or unknown (places stored before v3.0).
     */
    val localName: String = "",
) {
    /** The name to search news of this place with in an edition of [language] (ISO 639-1). */
    fun newsName(language: String): String =
        localName.takeIf { it.isNotBlank() && NewsEdition.forCountry(countryCode)?.language == language } ?: name

    companion object {
        val BERLIN = Place("Berlin", 52.5244, 13.4105, "Berlin, Deutschland", "DE")
        val LEIPZIG = Place("Leipzig", 51.3397, 12.3731, "Sachsen, Deutschland", "DE")
        val BAD_DUEBEN = Place("Bad Düben", 51.5928, 12.5853, "Sachsen, Deutschland", "DE")
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Accent palettes. [DYNAMIC] = Material You wallpaper colors (Android 12+); on older
 * devices it falls back to [MORGENROT]. Seeds are ARGB. The names shown in the settings live in
 * `i18n/SettingsStrings` (stored by enum name, never rename one).
 */
enum class AccentId(val seed: Long) {
    DYNAMIC(0xFFC6613F),
    MORGENROT(0xFFC6613F),
    SONNE(0xFFE0A100),
    OZEAN(0xFF0E7C9B),
    WALD(0xFF2E8B57),
    LAVENDEL(0xFF7B61FF),
    BEERE(0xFFC2185B),
    GRAPHIT(0xFF5F6B7A),
}

/** How far back the feed reaches; [hours] = 0 means everything the sources deliver. Labels: `i18n/SettingsStrings`. */
enum class NewsAge(val hours: Int) {
    /** The last 24 hours — "tagesaktuell". */
    DAY(24),
    TWO_DAYS(48),
    WEEK(24 * 7),
    ALL(0),
    ;

    companion object {
        fun fromName(name: String?): NewsAge? = entries.firstOrNull { it.name == name }
    }
}

/** The weather blocks of "Heute" the user can switch off (the hero stays). Labels: `i18n/SettingsStrings`. */
enum class WeatherCard {
    WARNINGS,
    CURVE,
    CLOTHING,
    ;

    companion object {
        val ALL: Set<WeatherCard> = entries.toSet()
    }
}

/** When the reader may load articles ahead of time (so they open instantly and offline). */
enum class PrefetchMode { OFF, WIFI_ONLY, ALWAYS }

/**
 * "Effekte" (Einstellungen → Darstellung): [AUTO] lets the phone's tier decide
 * (`perf/DeviceTier.kt`), [FULL] always draws every effect, [LIGHT] always the light set.
 * Stored by enum name, never rename one. Labels: `i18n/SettingsStrings`.
 */
enum class EffectsMode { AUTO, FULL, LIGHT }

/**
 * "Vorlesen": the kind of voice to read with while no voice is chosen for a language. It only
 * counts where a voice says what it is (`listen/VoiceGenders`); everywhere else the engine's best
 * offline voice reads, exactly as with [ANY]. Stored by enum name, never rename one.
 */
enum class VoicePreference { ANY, FEMALE, MALE }

/**
 * How "Zuhören" sounds (Einstellungen → Vorlesen). The defaults read exactly as the app did before
 * these settings existed: the system's engine, the article's language, the engine's best offline
 * voice, normal speed and pitch.
 */
data class ListenSettings(
    /** Package of the text-to-speech engine ("com.samsung.SMT"); null = the phone's default engine. */
    val engine: String? = null,
    /** The chosen voice per language (ISO 639-1 → the engine's voice name); missing = automatic. */
    val voices: Map<String, String> = emptyMap(),
    val voicePreference: VoicePreference = VoicePreference.ANY,
    /** Speech rate, one of [RATES]; 1 = the phone's normal speed. */
    val rate: Float = DEFAULT_RATE,
    /** Pitch, one of [PITCHES]; 1 = the voice's own pitch. */
    val pitch: Float = DEFAULT_PITCH,
    /** Null = every article in its own language (automatic); else always this language (ISO 639-1). */
    val language: String? = null,
) {
    /** The voice chosen for [language], or null for automatic. */
    fun voiceFor(language: String): String? = voices[language.lowercase()]

    /** These settings with [name] chosen for [language] (null = back to automatic). */
    fun withVoice(language: String, name: String?): ListenSettings {
        val key = cleanLanguage(language) ?: return this
        val voice = name?.trim()?.takeIf { it.isNotEmpty() }
        return copy(voices = if (voice == null) voices - key else voices + (key to voice))
    }

    companion object {
        const val DEFAULT_RATE = 1f
        const val DEFAULT_PITCH = 1f

        /** The speeds on offer (slider steps). */
        val RATES: List<Float> = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

        /** The pitches on offer (slider steps). */
        val PITCHES: List<Float> = listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f)

        /** The mini player's speed chip: 1× → 1,25× → 1,5× → 2× → 0,75× → 1×. */
        val CHIP_RATES: List<Float> = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

        /** The offered speed closest to [value] (stored or dragged); nonsense reads as normal speed. */
        fun snapRate(value: Float): Float = nearest(RATES, value, DEFAULT_RATE)

        /** The offered pitch closest to [value]. */
        fun snapPitch(value: Float): Float = nearest(PITCHES, value, DEFAULT_PITCH)

        /**
         * The speed after one tap on the chip: the next one in [CHIP_RATES]; from a speed the chip
         * does not show (0,5× or 1,75× from the settings) the next faster chip speed, after the
         * fastest the slowest.
         */
        fun nextChipRate(current: Float): Float {
            val index = CHIP_RATES.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
            if (index >= 0) return CHIP_RATES[(index + 1) % CHIP_RATES.size]
            return CHIP_RATES.filter { it > current }.minOrNull() ?: CHIP_RATES.min()
        }

        /** A stored language code made safe ("DE " → "de"); null for blank or malformed input. */
        fun cleanLanguage(code: String?): String? =
            code?.trim()?.lowercase()?.takeIf { it.length in 2..3 && it.all { c -> c in 'a'..'z' } }

        private fun nearest(options: List<Float>, value: Float, default: Float): Float =
            if (value.isNaN()) default else options.minBy { kotlin.math.abs(it - value) }
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentId = AccentId.DYNAMIC,
    val notificationsEnabled: Boolean = true,
    val notifyHour: Int = 6,
    val notifyMinute: Int = 30,
    val home: Place = Place.BERLIN,
    val day: Place = Place.BERLIN,
    val dayStartHour: Int = 9,
    val dayEndHour: Int = 18,
    val onboardingDone: Boolean = false,
    /** `YYYY-MM-DD` of the last morning notification, to avoid duplicates. */
    val lastNotifiedDate: String? = null,
    /** Topic ids in the user's preferred order (see [Topics]); custom topics included. */
    val topicOrder: List<String> = Topics.DEFAULT_ORDER,
    /** Topic ids the user switched off. */
    val hiddenTopics: Set<String> = emptySet(),
    /** Topics the user created; their sources are in [customFeeds]. */
    val customTopics: List<Topic> = emptyList(),
    /** Feeds the user added (to built-in or custom topics). */
    val customFeeds: List<FeedSource> = emptyList(),
    /** Ids of feeds (built-in or custom) the user switched off. */
    val disabledFeeds: Set<String> = emptySet(),
    /** Key of the [Regions] preset behind the "Region" topic. */
    val region: String = Regions.DEFAULT,
    /** The user picked the region by hand (a new home place then no longer moves it). */
    val regionChosenByUser: Boolean = false,
    /** Articles a topic shows in "Für dich" before "Mehr zu …". */
    val sectionSize: Int = DEFAULT_SECTION_SIZE,
    /** Pictures in the feed (off: quiet typographic covers, less data). */
    val showImages: Boolean = true,
    /** Articles open in the app's own reader (full text, pictures, no ads); false: in the browser. */
    val readInApp: Boolean = true,
    val newsAge: NewsAge = NewsAge.DAY,
    /** Weather blocks shown on "Heute" besides the hero. */
    val weatherCards: Set<WeatherCard> = WeatherCard.ALL,
    /** Interface language; null = follow the phone (see [AppLanguage.resolve]). */
    val uiLanguage: AppLanguage? = null,
    /** The news region (country) and the news language: chosen independently of each other and of [uiLanguage]. */
    val edition: NewsEdition = NewsEdition.GERMANY,
    /**
     * The topics the feed shows, in order: categories (`wirtschaft`), sub-topics
     * (`wirtschaft/boerse`), local places (`local:…`) and custom topics (`custom:…`).
     * Replaces [topicOrder] + [hiddenTopics], which are only read to migrate old installs.
     */
    val followed: List<String> = emptyList(),
    /** Places anywhere on earth whose local news the user follows (`local:` topics). */
    val localPlaces: List<Place> = emptyList(),
    /** Hide articles of sites the in-app reader cannot show (paywall, consent wall, bot block). */
    val readableOnly: Boolean = true,
    /**
     * "Gelesene Artikel ausblenden": stories the user read leave the news lists – when a list is
     * built anew (start, refresh, another tab), never under the finger. Off: they show, calmer.
     */
    val hideRead: Boolean = false,
    /** Load the articles on screen ahead of time. */
    val prefetch: PrefetchMode = PrefetchMode.WIFI_ONLY,
    /** Recent search queries, newest first (at most a handful). */
    val recentSearches: List<String> = emptyList(),
    /** Visual effects: automatic (by the phone), full or light. */
    val effects: EffectsMode = EffectsMode.AUTO,
    /** "Vorlesen": engine, voices, speed, pitch and language of "Zuhören". */
    val listen: ListenSettings = ListenSettings(),
) {
    /** The interface language to use now. */
    val language: AppLanguage get() = AppLanguage.resolve(uiLanguage)

    companion object {
        const val DEFAULT_SECTION_SIZE = 4
        const val MIN_SECTION_SIZE = 2
        const val MAX_SECTION_SIZE = 10
    }
}

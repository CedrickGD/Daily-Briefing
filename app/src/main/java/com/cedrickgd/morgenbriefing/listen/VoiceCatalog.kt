package com.cedrickgd.morgenbriefing.listen

import com.cedrickgd.morgenbriefing.data.model.ListenSettings
import com.cedrickgd.morgenbriefing.data.model.VoicePreference
import java.util.Locale

/*
 * The voices of a text-to-speech engine as "Vorlesen" shows and picks them. Pure: built from
 * [VoiceInfo] (what `android.speech.tts.Voice` says, copied by the controller), so every rule
 * here is unit-tested without Android.
 */

/** What one voice of the engine says about itself (`android.speech.tts.Voice`, Android-free). */
data class VoiceInfo(
    /** The engine's name of the voice ("de-DE-SMTf00", "de-de-x-deb-local"); unique per engine. */
    val name: String,
    /** ISO 639-1, lower case ("de"); see [VoiceLanguages.language]. */
    val language: String,
    /** ISO 3166-1 alpha-2, upper case ("DE"); empty when the voice names none. */
    val country: String = "",
    /** `Voice.QUALITY_*`: 100 (very low) … 500 (very high). */
    val quality: Int = QUALITY_NORMAL,
    /** `Voice.LATENCY_*`: 100 (very low) … 500 (very high). */
    val latency: Int = LATENCY_NORMAL,
    /** Synthesised on a server: needs the internet. */
    val networkRequired: Boolean = false,
    /** Listed, but its data still has to be downloaded (`KEY_FEATURE_NOT_INSTALLED`). */
    val notInstalled: Boolean = false,
    val features: Set<String> = emptySet(),
) {
    val installed: Boolean get() = !notInstalled

    /** Installed and works without internet. */
    val offline: Boolean get() = installed && !networkRequired

    companion object {
        // The constants of android.speech.tts.Voice (stable since API 21).
        const val QUALITY_HIGH = 400
        const val QUALITY_VERY_HIGH = 500
        const val QUALITY_NORMAL = 300
        const val LATENCY_NORMAL = 300
        const val LATENCY_HIGH = 400
    }
}

/** A gender a voice reveals by its name or features. */
enum class VoiceGender { FEMALE, MALE }

/** Codes of a voice's locale as the app compares them. */
internal object VoiceLanguages {
    private val iso3Languages: Map<String, String> by lazy {
        Locale.getISOLanguages().mapNotNull { code ->
            runCatching { Locale.forLanguageTag(code).isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { it to code }
        }.toMap()
    }

    private val iso3Countries: Map<String, String> by lazy {
        Locale.getISOCountries().mapNotNull { code ->
            runCatching { Locale.Builder().setRegion(code).build().isO3Country }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { it to code }
        }.toMap()
    }

    /** ISO 639-1 for a voice's language; engines that use ISO 639-2 ("deu") are mapped back ("de"). */
    fun language(code: String): String {
        val lower = code.trim().lowercase()
        return if (lower.length == 3) iso3Languages[lower] ?: lower else lower
    }

    /** ISO 3166-1 alpha-2 for a voice's country ("DEU" → "DE"). */
    fun country(code: String): String {
        val upper = code.trim().uppercase()
        return if (upper.length == 3) iso3Countries[upper] ?: upper else upper
    }
}

/**
 * The gender of a voice where its engine reveals it; null everywhere else. Never guessed:
 *
 * * Any engine: the word "female" or "male" in the voice's name or one of its features
 *   (older Google voices were named like "en-us-x-sfg#female_1-local").
 * * Samsung (`com.samsung.SMT`): "de-DE-SMTf00" is female, "en-GB-SMTm00" male – the letter after
 *   "SMT" matches the voice packages Samsung publishes as "… Female" / "… Male"
 *   (`com.samsung.SMT.lang_de_de_f00`, `…lang_en_gb_m00`). Newer voices with other letters
 *   ("SMTl03", "SMTg02") say nothing about their gender.
 * * Google Speech Services: the three-letter voice code of "de-de-x-deb-local" against the table
 *   below, which two independent public lists agree on (Readium's speech voice list, CC0, and the
 *   RT-Voice documentation of Google TTS). The local and network variants share the gender.
 *   Codes outside the table – and the "de-DE-language" alias – stay unknown.
 */
internal object VoiceGenders {
    private val SAMSUNG = Regex("""(?:^|[-_])SMT([a-z])\d+""", RegexOption.IGNORE_CASE)
    private val GOOGLE = Regex("""^([a-z]{2,3})-([a-z]{2})-x-([a-z]{3})-(?:local|network)$""")
    private val WORDS = Regex("""[^a-z]+""")

    private val F = VoiceGender.FEMALE
    private val M = VoiceGender.MALE

    /** Google voice codes by locale ("de-de" → "deb" → male). */
    internal val GOOGLE_VOICES: Map<String, Map<String, VoiceGender>> = mapOf(
        "de-de" to mapOf("dea" to F, "nfh" to F, "deb" to M, "deg" to M),
        "en-us" to mapOf("iob" to F, "iog" to F, "sfg" to F, "tpc" to F, "tpf" to F, "iol" to M, "iom" to M, "tpd" to M),
        "en-gb" to mapOf("gba" to F, "gbc" to F, "gbg" to F, "gbb" to M, "gbd" to M, "rjs" to M),
        "fr-fr" to mapOf("fra" to F, "frc" to F, "vlf" to F, "frb" to M, "frd" to M),
        "es-es" to mapOf("eea" to F, "eec" to F, "eee" to F, "eed" to M, "eef" to M),
        "it-it" to mapOf("itb" to F, "kda" to F, "itc" to M, "itd" to M),
    )

    fun of(voice: VoiceInfo): VoiceGender? =
        fromWords(voice.name) ?: voice.features.firstNotNullOfOrNull(::fromWords) ?: samsung(voice.name) ?: google(voice.name)

    /** "female"/"male" as a whole word; both or neither → null. */
    internal fun fromWords(text: String): VoiceGender? {
        val words = text.lowercase(Locale.ROOT).split(WORDS).toSet()
        val female = "female" in words
        val male = "male" in words
        return when {
            female && !male -> F
            male && !female -> M
            else -> null
        }
    }

    internal fun samsung(name: String): VoiceGender? = when (SAMSUNG.find(name)?.groupValues?.get(1)?.lowercase(Locale.ROOT)) {
        "f" -> F
        "m" -> M
        else -> null
    }

    internal fun google(name: String): VoiceGender? {
        val match = GOOGLE.matchEntire(name.lowercase(Locale.ROOT)) ?: return null
        val (language, country, code) = match.destructured
        return GOOGLE_VOICES["$language-$country"]?.get(code)
    }
}

/** One voice as the settings list shows it: "Stimme 2 · weiblich · offline". */
data class VoiceEntry(
    /** The variant the app reads with (the offline one when a voice exists offline and online). */
    val voice: VoiceInfo,
    /** "Stimme [number]", counted per language in list order. */
    val number: Int,
    val gender: VoiceGender?,
)

/** How "Automatisch" picks a voice ([VoiceChoice.resolve]). */
enum class VoiceSource {
    /** The voice the user chose for the language. */
    CHOSEN,

    /** A voice of the preferred gender. */
    PREFERENCE,

    /** The engine's best offline voice of the language – the app's choice before these settings. */
    BEST_OFFLINE,

    /** No voice to pick: the engine's own default for the language. */
    ENGINE_DEFAULT,
}

data class VoicePick(
    /** The voice to set; null leaves the engine's default for the language. */
    val voice: VoiceInfo?,
    val source: VoiceSource,
    /** A voice was chosen for the language, but the engine no longer has it (installed). */
    val chosenMissing: Boolean = false,
)

/** Which voice reads a language: the chosen one, else one of the preferred gender, else the best offline voice. */
internal object VoiceChoice {
    fun resolve(
        voices: List<VoiceInfo>,
        language: String,
        chosen: String?,
        preference: VoicePreference,
        preferredCountry: String? = null,
    ): VoicePick {
        val installed = voices.filter { it.language == language && it.installed }
        if (chosen != null) {
            installed.firstOrNull { it.name == chosen }?.let { return VoicePick(it, VoiceSource.CHOSEN) }
        }
        val missing = chosen != null
        val gender = preference.gender
        if (gender != null) {
            val match = installed
                .filter { VoiceGenders.of(it) == gender }
                .sortedWith(
                    compareByDescending<VoiceInfo> { it.offline }
                        .thenByDescending { preferredCountry != null && it.country == preferredCountry }
                        .thenByDescending { it.quality }
                        .thenBy { it.latency }
                        .thenBy { it.name },
                )
                .firstOrNull()
            if (match != null) return VoicePick(match, VoiceSource.PREFERENCE, missing)
        }
        // The offline voice of the best quality ("sehr hohe Qualität" first); among equals the
        // edition's country, then the one that starts sooner, then the engine's order.
        val offline = installed.filter { it.offline }.sortedWith(bestFirst(preferredCountry)).firstOrNull()
        return if (offline != null) VoicePick(offline, VoiceSource.BEST_OFFLINE, missing) else VoicePick(null, VoiceSource.ENGINE_DEFAULT, missing)
    }

    /** Best quality first, then [preferredCountry], then lower latency; stable, so the engine's order breaks ties. */
    private fun bestFirst(preferredCountry: String?): Comparator<VoiceInfo> =
        compareByDescending<VoiceInfo> { it.quality }
            .thenByDescending { preferredCountry != null && it.country == preferredCountry }
            .thenBy { it.latency }

    /** The engine lists voices of [language], but none of them works offline (reading needs the internet). */
    fun onlineOnly(voices: List<VoiceInfo>, language: String): Boolean {
        val ofLanguage = voices.filter { it.language == language }
        return ofLanguage.isNotEmpty() && ofLanguage.none { it.offline }
    }
}

internal val VoicePreference.gender: VoiceGender?
    get() = when (this) {
        VoicePreference.ANY -> null
        VoicePreference.FEMALE -> VoiceGender.FEMALE
        VoicePreference.MALE -> VoiceGender.MALE
    }

/** Everything the "Stimme für …" part of the settings shows for one language. */
data class VoicePanel(
    val language: String,
    /** Installed voices of the language, numbered; the preferred country's first. */
    val entries: List<VoiceEntry>,
    /** Entries of the preferred country (all entries when none of them matches it). */
    val nearby: List<VoiceEntry>,
    /** Entries of other countries ("Stimmen aus anderen Regionen"). */
    val elsewhere: List<VoiceEntry>,
    /** The entry the user chose; null = automatic. */
    val chosen: VoiceEntry?,
    /** A voice was chosen but is gone (other engine, uninstalled): automatic reads instead. */
    val chosenMissing: Boolean,
    /** Id of the natural voice chosen for the language ([NeuralVoiceIds]); null = an engine voice or automatic. */
    val neuralChosen: String? = null,
    /** What "Automatisch" reads with right now, and its entry when it is one of the list. */
    val auto: VoicePick,
    val autoEntry: VoiceEntry?,
    /** Genders known among the entries (the preference pills only offer these). */
    val genders: Set<VoiceGender>,
    /** Voices of the language that still have to be downloaded. */
    val notInstalled: Int,
    /** Only network voices: reading this language needs the internet. */
    val onlineOnly: Boolean,
) {
    /** The engine has no voice at all for the language. */
    val noVoice: Boolean get() = entries.isEmpty()
}

/** Builds the voice list of the settings from an engine's voices. */
internal object VoiceCatalog {
    /**
     * The installed voices of [language] as list entries: a voice offered offline and online
     * ("…-local", "…-network") once, as its offline variant; the engine's alias for its default
     * ("de-DE-language") left out when real voices exist; the [preferredCountry] first, then by
     * country, the best quality first, then by name. Numbered 1… in that order.
     */
    fun entries(voices: List<VoiceInfo>, language: String, preferredCountry: String?): List<VoiceEntry> {
        val installed = voices.filter { it.language == language && it.installed }
        val real = installed.filterNot { isAlias(it) }.ifEmpty { installed }
        return real
            .groupBy { identity(it.name) }
            .values
            .map { variants ->
                variants.sortedWith(compareByDescending<VoiceInfo> { it.offline }.thenByDescending { it.quality }.thenBy { it.name }).first()
            }
            .sortedWith(
                compareByDescending<VoiceInfo> { preferredCountry != null && it.country == preferredCountry }
                    .thenBy { it.country }
                    .thenByDescending { it.quality }
                    .thenBy { identity(it.name).lowercase(Locale.ROOT) },
            )
            .mapIndexed { i, voice -> VoiceEntry(voice, number = i + 1, gender = VoiceGenders.of(voice)) }
    }

    fun panel(voices: List<VoiceInfo>, language: String, settings: ListenSettings, preferredCountry: String?): VoicePanel {
        val entries = entries(voices, language, preferredCountry)
        val nearby = entries.filter { it.voice.country == preferredCountry }.ifEmpty { entries }
        val stored = settings.voiceFor(language)
        // A natural voice is chosen outside the engine's list (see [NeuralVoiceIds]).
        val neural = NeuralVoiceIds.idOf(stored)
        val chosenName = stored.takeIf { neural == null }
        val chosen = chosenName?.let { name ->
            entries.firstOrNull { it.voice.name == name } ?: entries.firstOrNull { identity(it.voice.name) == identity(name) }
        }
        val auto = VoiceChoice.resolve(voices, language, chosen = null, preference = settings.voicePreference, preferredCountry = preferredCountry)
        val autoEntry = auto.voice?.let { picked -> entries.firstOrNull { identity(it.voice.name) == identity(picked.name) } }
        return VoicePanel(
            language = language,
            entries = entries,
            nearby = nearby,
            elsewhere = entries - nearby.toSet(),
            chosen = chosen,
            chosenMissing = chosenName != null && chosen == null,
            neuralChosen = neural,
            auto = auto,
            autoEntry = autoEntry,
            genders = entries.mapNotNull { it.gender }.toSet(),
            notInstalled = voices.count { it.language == language && it.notInstalled },
            onlineOnly = VoiceChoice.onlineOnly(voices, language),
        )
    }

    /** One voice offered both offline and online counts once: "de-de-x-deb-local" = "de-de-x-deb-network". */
    internal fun identity(name: String): String = name.removeSuffix("-local").removeSuffix("-network")

    /** Google's "de-DE-language": an alias of whichever voice is the default, not a voice of its own. */
    private fun isAlias(voice: VoiceInfo): Boolean = voice.name.endsWith("-language", ignoreCase = true)
}

/** An installed text-to-speech engine. */
data class TtsEngine(
    /** Package name ("com.google.android.tts"). */
    val name: String,
    /** The engine's own label ("Speech Services by Google"). */
    val label: String,
) {
    /** Short label for a pill: "Samsung", "Google", else the engine's own label. */
    val shortLabel: String
        get() = when {
            name == "com.samsung.SMT" -> "Samsung"
            name == "com.google.android.tts" -> "Google"
            label.isNotBlank() -> label.trim()
            else -> name.substringAfterLast('.')
        }
}

/** What the hint below the voice list suggests about "Google Sprachausgabe" (Speech Services by Google). */
enum class GoogleVoiceHint {
    /** Not on the phone: get it from the Play Store. */
    INSTALL,

    /** Installed, but another engine reads: switch to it. */
    USE,
}

/** Google's engine has free offline voices of high quality; [hint] decides when the settings mention it. */
internal object GoogleVoices {
    const val PACKAGE = "com.google.android.tts"

    /**
     * Nothing while the engines are unknown, when Google already reads, or when the engine in use
     * already has a voice of very high quality offline for [language] (a good setup stays quiet).
     * Otherwise [GoogleVoiceHint.USE] when Google is installed, [GoogleVoiceHint.INSTALL] when not.
     */
    fun hint(voices: ListenVoices, language: String): GoogleVoiceHint? {
        if (!voices.loaded || voices.engines.isEmpty()) return null
        if (voices.engine == PACKAGE) return null
        val excellent = voices.voices.any { it.language == language && it.offline && it.quality >= VoiceInfo.QUALITY_VERY_HIGH }
        return when {
            excellent -> null
            voices.engines.any { it.name == PACKAGE } -> GoogleVoiceHint.USE
            else -> GoogleVoiceHint.INSTALL
        }
    }
}

package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.cedrickgd.morgenbriefing.listen.NeuralVoice
import com.cedrickgd.morgenbriefing.listen.VoiceGender
import com.cedrickgd.morgenbriefing.listen.VoiceInfo
import com.cedrickgd.morgenbriefing.listen.VoiceSource
import java.util.Locale

/**
 * Texts of "Zuhören" (read aloud): the mini player, its hints, the settings card "Vorlesen", and
 * the few words the voice itself says ([sourceSentence], [sampleSentence]). Voice-related texts
 * name the language of the voice by its ISO code (`voice` = "de", "en" … — the language of the
 * news edition).
 */
interface ListenStrings {
    /** Spoken after the article: "Quelle: tagesschau." */
    fun sourceSentence(source: String): String

    /** Title of a reading whose article has no headline. */
    val articleFallback: String

    // Mini player
    val openArticle: String
    fun openReading(title: String): String
    val stop: String
    val resume: String
    val pause: String
    val statusPreparing: String
    val statusPaused: String
    val statusReading: String
    val statusOnlineVoice: String
    /** "NOCH ETWA 3 MIN." (upper-cased by the caller where needed). */
    fun remaining(minutes: Int): String
    val noEngineTitle: String
    val noEngineText: String
    val missingVoiceText: String
    val install: String
    val setUp: String
    val dismiss: String

    fun missingVoiceTitle(voice: String): String
    fun installOfflineVoice(voice: String): String
    /** Toast when no installer screen could be opened. */
    fun installVoiceHint(voice: String): String

    /** The name of a news language ("Englisch", "English", "anglais" …). */
    fun languageName(code: String): String

    // Speed (mini player chip and settings)
    /** "1,25×" — the speech rate as a factor, with the language's decimal separator. */
    fun rateLabel(rate: Float): String
    /** Screen reader text of the mini player's speed chip; [rate] is [rateLabel]. */
    fun speedChip(rate: String): String
    val speedChipAction: String

    /** Spoken by "Probehören" in the voice's own language (like [sourceSentence]). */
    val sampleSentence: String

    // Settings card "Vorlesen"
    val settingsOverline: String
    val settingsTitle: String
    val cardHint: String
    val engineOverline: String
    val languageOverline: String
    val languageAuto: String
    val languageAutoHint: String
    /** "Immer auf Deutsch – …" for a fixed reading language ([language] = ISO code). */
    fun languageFixedHint(language: String): String
    /** Above the language chips when voices of several languages can be chosen. */
    val voiceForOverline: String
    /** "Stimme für Deutsch". */
    fun voicesOverline(language: String): String
    val preferenceOverline: String
    val preferenceAny: String
    val preferenceFemale: String
    val preferenceMale: String
    val voiceAuto: String
    /** What "Automatisch" reads with; [number] is the voice it picks right now, when listed. */
    fun voiceAutoText(source: VoiceSource, gender: VoiceGender?, number: Int?): String
    /** "Stimme 1 · weiblich · offline" — [gender] only when the engine reveals it. */
    fun voiceLabel(number: Int, gender: VoiceGender?, offline: Boolean): String
    /** "Österreich · hohe Qualität"; [country] (ISO code) only when voices of several countries are listed. */
    fun voiceDetails(country: String?, quality: Int, slow: Boolean): String
    /** Screen reader text of a voice's sample button; [label] is [voiceLabel] or [voiceAuto]. */
    fun previewVoice(label: String): String
    val previewPlaying: String
    fun otherRegions(count: Int): String
    val fewerRegions: String
    fun notInstalledVoices(count: Int): String
    val installMoreVoices: String
    val chosenVoiceMissing: String
    /** The phone has no voice at all for [language]. */
    fun noVoiceFor(language: String): String
    /** Only network voices for [language]. */
    fun onlineOnlyFor(language: String): String
    val engineListsNoVoices: String
    val loadingVoices: String
    val voicesFailed: String
    val speedOverline: String
    val speedHint: String
    val speedDescription: String
    val pitchOverline: String
    /** "Etwas höher" for one of the offered pitches. */
    fun pitchLabel(pitch: Float): String
    val pitchDescription: String

    // "Natürliche Stimmen": downloadable voices (Piper via sherpa-onnx)
    val neuralOverline: String
    val neuralHint: String
    /** Shown while the phone is on mobile data. */
    val neuralMetered: String
    /** "Thorsten · männlich". */
    fun neuralVoiceTitle(name: String, gender: VoiceGender): String
    /** "Aufnahmen: Thorsten-Voice · CC0"; [licence] is a short licence name or `NeuralVoice.PUBLIC_DOMAIN`. */
    fun neuralVoiceDetails(dataset: String, licence: String): String
    /** Button of a voice that is not on the phone: "Laden · 21 MB". */
    fun neuralDownload(megabytes: Int): String
    /** "8 von 21 MB". */
    fun neuralProgress(doneMegabytes: Int, totalMegabytes: Int): String
    val neuralUnpacking: String
    /** Screen reader text of the cancel button while [name] downloads. */
    fun neuralCancel(name: String): String
    /** Screen reader text of the delete button of an installed voice. */
    fun neuralDelete(name: String): String
    val neuralFailed: String
    /** Where the voices come from (below the list). */
    val neuralSource: String

    // Hint about Google's text-to-speech engine ([com.cedrickgd.morgenbriefing.listen.GoogleVoiceHint])
    val googleInstallHint: String
    val googleInstallAction: String
    val googleUseHint: String
    val googleUseAction: String

    companion object {
        fun of(lang: AppLanguage): ListenStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }

        /** Name of [code] in [lang] from the platform, for a language the tables do not list. */
        internal fun platformName(code: String, lang: AppLanguage): String =
            Locale.forLanguageTag(code).getDisplayLanguage(lang.locale).ifBlank { code }

        /** Name of the country [code] (ISO 3166-1) in [lang] from the platform. */
        internal fun countryName(code: String, lang: AppLanguage): String =
            runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(lang.locale) }.getOrNull()?.ifBlank { null } ?: code

        /** 1.25 → "1.25", 1.0 → "1", 0.5 → "0,5" with [comma]. */
        internal fun factor(value: Float, comma: Boolean): String {
            val text = String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
            return if (comma) text.replace('.', ',') else text
        }

        /** Index of [pitch] among the offered pitches (0 = lowest … 4 = highest), for the labels. */
        internal fun pitchStep(pitch: Float): Int = when {
            pitch < 0.85f -> 0
            pitch < 0.95f -> 1
            pitch <= 1.05f -> 2
            pitch <= 1.15f -> 3
            else -> 4
        }

        /** 0 = very high … 3 = basic, from `Voice.QUALITY_*`. */
        internal fun qualityStep(quality: Int): Int = when {
            quality >= VoiceInfo.QUALITY_VERY_HIGH -> 0
            quality >= VoiceInfo.QUALITY_HIGH -> 1
            quality >= VoiceInfo.QUALITY_NORMAL -> 2
            else -> 3
        }

        /** The non-blank [parts] joined with " · ". */
        internal fun details(vararg parts: String?): String = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
    }

    private object De : ListenStrings {
        private val names = mapOf(
            "de" to "Deutsch", "en" to "Englisch", "fr" to "Französisch", "es" to "Spanisch", "it" to "Italienisch",
            "nl" to "Niederländisch", "pl" to "Polnisch", "pt" to "Portugiesisch", "sv" to "Schwedisch",
            "tr" to "Türkisch", "ja" to "Japanisch",
        )

        /** "deutsche", "englische" … — the adjective before "Stimme" (feminine, after "eine"/"keine"). */
        private val adjectives = mapOf(
            "de" to "deutsche", "en" to "englische", "fr" to "französische", "es" to "spanische", "it" to "italienische",
            "nl" to "niederländische", "pl" to "polnische", "pt" to "portugiesische", "sv" to "schwedische",
            "tr" to "türkische", "ja" to "japanische",
        )

        private fun adjective(code: String): String? = adjectives[code.lowercase()]

        override fun sourceSentence(source: String) = "Quelle: $source."
        override val articleFallback = "Artikel"
        override val openArticle = "Artikel öffnen"
        override fun openReading(title: String) = "Vorlesen: $title öffnen"
        override val stop = "Vorlesen beenden"
        override val resume = "Weiter vorlesen"
        override val pause = "Vorlesen pausieren"
        override val statusPreparing = "STIMME WIRD VORBEREITET"
        override val statusPaused = "PAUSIERT"
        override val statusReading = "LIEST VOR"
        override val statusOnlineVoice = "ONLINE-STIMME"
        override fun remaining(minutes: Int) = "NOCH ETWA $minutes MIN."
        override val noEngineTitle = "Vorlesen nicht verfügbar"
        override val noEngineText = "Richte eine Sprachausgabe ein."
        override val missingVoiceText = "Lade sie einmal herunter, dann klappt's."
        override val install = "Installieren"
        override val setUp = "Einrichten"
        override val dismiss = "Hinweis schließen"
        override fun missingVoiceTitle(voice: String) =
            adjective(voice)?.let { "Keine $it Stimme" } ?: "Keine Stimme für ${languageName(voice)}"
        override fun installOfflineVoice(voice: String) =
            adjective(voice)?.let { "${it.replaceFirstChar { c -> c.uppercase() }} Stimme offline installieren" }
                ?: "Stimme für ${languageName(voice)} offline installieren"
        override fun installVoiceHint(voice: String) =
            (adjective(voice)?.let { "Installiere eine $it Stimme" } ?: "Installiere eine Stimme für ${languageName(voice)}") +
                " unter Einstellungen › Sprachausgabe."
        override fun languageName(code: String) = names[code.lowercase()] ?: platformName(code, AppLanguage.DE)

        override fun rateLabel(rate: Float) = "${ListenStrings.factor(rate, comma = true)}×"
        override fun speedChip(rate: String) = "Vorlesetempo $rate"
        override val speedChipAction = "Tempo ändern"
        override val sampleSentence = "Guten Morgen! So klingt diese Stimme, wenn sie dir deine Nachrichten vorliest."
        override val settingsOverline = "So klingt Zuhören"
        override val settingsTitle = "Vorlesen"
        override val cardHint = "Stimme, Tempo und Sprache, wenn die App dir Artikel vorliest."
        override val engineOverline = "Sprachausgabe"
        override val languageOverline = "Sprache"
        override val languageAuto = "Automatisch"
        override val languageAutoHint = "Jeder Artikel in seiner eigenen Sprache – ein englischer Suchtreffer wird auf Englisch vorgelesen."
        override fun languageFixedHint(language: String) = "Immer auf ${languageName(language)} – auch Artikel in anderen Sprachen."
        override val voiceForOverline = "Stimme für"
        override fun voicesOverline(language: String) = "Stimme für ${languageName(language)}"
        override val preferenceOverline = "Am liebsten"
        override val preferenceAny = "Egal"
        override val preferenceFemale = "Weiblich"
        override val preferenceMale = "Männlich"
        override val voiceAuto = "Automatisch"
        override fun voiceAutoText(source: VoiceSource, gender: VoiceGender?, number: Int?): String {
            val what = when {
                source == VoiceSource.PREFERENCE && gender == VoiceGender.FEMALE -> "Eine weibliche Stimme"
                source == VoiceSource.PREFERENCE && gender == VoiceGender.MALE -> "Eine männliche Stimme"
                source == VoiceSource.ENGINE_DEFAULT -> "Die Standardstimme der Sprachausgabe"
                else -> "Die beste Offline-Stimme"
            }
            return if (number != null) "$what · gerade Stimme $number" else what
        }
        override fun voiceLabel(number: Int, gender: VoiceGender?, offline: Boolean) = ListenStrings.details(
            "Stimme $number",
            when (gender) {
                VoiceGender.FEMALE -> "weiblich"
                VoiceGender.MALE -> "männlich"
                null -> null
            },
            if (offline) "offline" else "online",
        )
        override fun voiceDetails(country: String?, quality: Int, slow: Boolean) = ListenStrings.details(
            country?.let { ListenStrings.countryName(it, AppLanguage.DE) },
            listOf("sehr hohe Qualität", "hohe Qualität", "normale Qualität", "einfache Qualität")[ListenStrings.qualityStep(quality)],
            if (slow) "startet etwas später" else null,
        )
        override fun previewVoice(label: String) = "$label probehören"
        override val previewPlaying = "Hörprobe läuft"
        override fun otherRegions(count: Int) = "Stimmen aus anderen Regionen ($count)"
        override val fewerRegions = "Weniger zeigen"
        override fun notInstalledVoices(count: Int) =
            if (count == 1) "Eine weitere Stimme kannst du noch herunterladen." else "$count weitere Stimmen kannst du noch herunterladen."
        override val installMoreVoices = "Weitere Stimmen installieren"
        override val chosenVoiceMissing = "Deine gewählte Stimme gibt es hier nicht mehr – bis du eine neue wählst, liest Automatisch."
        override fun noVoiceFor(language: String) =
            (adjective(language)?.let { "Auf diesem Handy ist keine $it Stimme installiert." } ?: "Auf diesem Handy ist keine Stimme für ${languageName(language)} installiert.") +
                " Solche Artikel liest dann die Stimme in der Sprache deines Handys – installiere eine, damit sie richtig klingen."
        override fun onlineOnlyFor(language: String) =
            "Für ${languageName(language)} gibt es nur Online-Stimmen – ohne Internet liest die App solche Artikel nicht vor."
        override val engineListsNoVoices = "Diese Sprachausgabe zeigt ihre Stimmen nicht – sie liest mit ihrer Standardstimme."
        override val loadingVoices = "Stimmen werden geladen …"
        override val voicesFailed = "Die Sprachausgabe antwortet gerade nicht."
        override val speedOverline = "Tempo"
        override val speedHint = "Gilt sofort, auch mitten im Artikel. Im Mini-Player wechselst du es mit einem Tipp."
        override val speedDescription = "Vorlesetempo"
        override val pitchOverline = "Tonhöhe"
        override fun pitchLabel(pitch: Float) = listOf("Tiefer", "Etwas tiefer", "Normal", "Etwas höher", "Höher")[ListenStrings.pitchStep(pitch)]
        override val pitchDescription = "Tonhöhe der Stimme"
        override val neuralOverline = "Natürliche Stimmen"
        override val neuralHint = "Trainiert mit Aufnahmen echter Sprecher, und sie lesen ohne Internet. Lade eine Stimme einmal, dann bleibt sie auf dem Handy."
        override val neuralMetered = "Du bist nicht im WLAN. Eine Stimme lädt dann über mobile Daten."
        override fun neuralVoiceTitle(name: String, gender: VoiceGender) =
            "$name · " + if (gender == VoiceGender.FEMALE) "weiblich" else "männlich"
        override fun neuralVoiceDetails(dataset: String, licence: String) =
            "Aufnahmen: $dataset · " + if (licence == NeuralVoice.PUBLIC_DOMAIN) "gemeinfrei" else licence
        override fun neuralDownload(megabytes: Int) = "Laden · $megabytes MB"
        override fun neuralProgress(doneMegabytes: Int, totalMegabytes: Int) = "$doneMegabytes von $totalMegabytes MB"
        override val neuralUnpacking = "Wird entpackt …"
        override fun neuralCancel(name: String) = "Download von $name abbrechen"
        override fun neuralDelete(name: String) = "$name löschen"
        override val neuralFailed = "Laden hat nicht geklappt. Prüf die Verbindung und tippe nochmal auf Laden."
        override val neuralSource = "Die Stimmen stammen aus dem Projekt Piper und kommen über sherpa-onnx (k2-fsa) von GitHub. Bei jeder Stimme stehen ihre Aufnahmen und deren Lizenz."
        override val googleInstallHint = "Google Sprachausgabe bringt kostenlose Offline-Stimmen in hoher Qualität mit."
        override val googleInstallAction = "Google Sprachausgabe laden"
        override val googleUseHint = "Google Sprachausgabe ist schon installiert. Ihre Offline-Stimmen klingen oft natürlicher."
        override val googleUseAction = "Google verwenden"
    }

    private object En : ListenStrings {
        private val names = mapOf(
            "de" to "German", "en" to "English", "fr" to "French", "es" to "Spanish", "it" to "Italian",
            "nl" to "Dutch", "pl" to "Polish", "pt" to "Portuguese", "sv" to "Swedish", "tr" to "Turkish", "ja" to "Japanese",
        )

        override fun sourceSentence(source: String) = "Source: $source."
        override val articleFallback = "Article"
        override val openArticle = "Open article"
        override fun openReading(title: String) = "Reading aloud: open $title"
        override val stop = "Stop reading"
        override val resume = "Continue reading"
        override val pause = "Pause reading"
        override val statusPreparing = "PREPARING THE VOICE"
        override val statusPaused = "PAUSED"
        override val statusReading = "READING ALOUD"
        override val statusOnlineVoice = "ONLINE VOICE"
        override fun remaining(minutes: Int) = "ABOUT $minutes MIN LEFT"
        override val noEngineTitle = "Reading aloud unavailable"
        override val noEngineText = "Set up a text-to-speech engine."
        override val missingVoiceText = "Download it once and you're set."
        override val install = "Install"
        override val setUp = "Set up"
        override val dismiss = "Dismiss hint"
        override fun missingVoiceTitle(voice: String) = "No ${languageName(voice)} voice"
        override fun installOfflineVoice(voice: String) = "Install an offline ${languageName(voice)} voice"
        override fun installVoiceHint(voice: String) =
            "Install a ${languageName(voice)} voice under Settings › Text-to-speech."
        override fun languageName(code: String) = names[code.lowercase()] ?: platformName(code, AppLanguage.EN)

        override fun rateLabel(rate: Float) = "${ListenStrings.factor(rate, comma = false)}×"
        override fun speedChip(rate: String) = "Reading speed $rate"
        override val speedChipAction = "Change speed"
        override val sampleSentence = "Good morning! This is how this voice sounds when it reads you the news."
        override val settingsOverline = "How listening sounds"
        override val settingsTitle = "Read aloud"
        override val cardHint = "Voice, speed and language when the app reads articles to you."
        override val engineOverline = "Text-to-speech engine"
        override val languageOverline = "Language"
        override val languageAuto = "Automatic"
        override val languageAutoHint = "Every article in its own language – a German search result is read in German."
        override fun languageFixedHint(language: String) = "Always in ${languageName(language)} – articles in other languages too."
        override val voiceForOverline = "Voice for"
        override fun voicesOverline(language: String) = "${languageName(language)} voice"
        override val preferenceOverline = "Prefer"
        override val preferenceAny = "Any"
        override val preferenceFemale = "Female"
        override val preferenceMale = "Male"
        override val voiceAuto = "Automatic"
        override fun voiceAutoText(source: VoiceSource, gender: VoiceGender?, number: Int?): String {
            val what = when {
                source == VoiceSource.PREFERENCE && gender == VoiceGender.FEMALE -> "A female voice"
                source == VoiceSource.PREFERENCE && gender == VoiceGender.MALE -> "A male voice"
                source == VoiceSource.ENGINE_DEFAULT -> "The engine's default voice"
                else -> "The best offline voice"
            }
            return if (number != null) "$what · currently voice $number" else what
        }
        override fun voiceLabel(number: Int, gender: VoiceGender?, offline: Boolean) = ListenStrings.details(
            "Voice $number",
            when (gender) {
                VoiceGender.FEMALE -> "female"
                VoiceGender.MALE -> "male"
                null -> null
            },
            if (offline) "offline" else "online",
        )
        override fun voiceDetails(country: String?, quality: Int, slow: Boolean) = ListenStrings.details(
            country?.let { ListenStrings.countryName(it, AppLanguage.EN) },
            listOf("very high quality", "high quality", "normal quality", "basic quality")[ListenStrings.qualityStep(quality)],
            if (slow) "starts a little later" else null,
        )
        override fun previewVoice(label: String) = "Listen to $label"
        override val previewPlaying = "Playing a sample"
        override fun otherRegions(count: Int) = "Voices from other regions ($count)"
        override val fewerRegions = "Show fewer"
        override fun notInstalledVoices(count: Int) =
            if (count == 1) "1 more voice is available to download." else "$count more voices are available to download."
        override val installMoreVoices = "Install more voices"
        override val chosenVoiceMissing = "Your chosen voice isn't available any more – Automatic reads until you pick another one."
        override fun noVoiceFor(language: String) =
            "This phone has no ${languageName(language)} voice installed. Such articles are then read by the voice of your phone's language – install one so they sound right."
        override fun onlineOnlyFor(language: String) =
            "There are only online voices for ${languageName(language)} – without internet, the app can't read such articles aloud."
        override val engineListsNoVoices = "This engine doesn't list its voices – it reads with its default voice."
        override val loadingVoices = "Loading voices …"
        override val voicesFailed = "The text-to-speech engine isn't responding right now."
        override val speedOverline = "Speed"
        override val speedHint = "Applies right away, even mid-article. In the mini player, one tap switches it."
        override val speedDescription = "Reading speed"
        override val pitchOverline = "Pitch"
        override fun pitchLabel(pitch: Float) = listOf("Lower", "Slightly lower", "Normal", "Slightly higher", "Higher")[ListenStrings.pitchStep(pitch)]
        override val pitchDescription = "Pitch of the voice"
        override val neuralOverline = "Natural voices"
        override val neuralHint = "Trained on recordings of real speakers, and they read without internet. Download a voice once and it stays on your phone."
        override val neuralMetered = "You're not on Wi-Fi. A voice will download over mobile data."
        override fun neuralVoiceTitle(name: String, gender: VoiceGender) =
            "$name · " + if (gender == VoiceGender.FEMALE) "female" else "male"
        override fun neuralVoiceDetails(dataset: String, licence: String) =
            "Recordings: $dataset · " + if (licence == NeuralVoice.PUBLIC_DOMAIN) "public domain" else licence
        override fun neuralDownload(megabytes: Int) = "Download · $megabytes MB"
        override fun neuralProgress(doneMegabytes: Int, totalMegabytes: Int) = "$doneMegabytes of $totalMegabytes MB"
        override val neuralUnpacking = "Unpacking …"
        override fun neuralCancel(name: String) = "Stop downloading $name"
        override fun neuralDelete(name: String) = "Delete $name"
        override val neuralFailed = "The download didn't work. Check your connection and tap Download again."
        override val neuralSource = "The voices come from the Piper project, via sherpa-onnx (k2-fsa) on GitHub. Each voice lists its recordings and their licence."
        override val googleInstallHint = "Speech Services by Google comes with free offline voices in high quality."
        override val googleInstallAction = "Get Speech Services by Google"
        override val googleUseHint = "Speech Services by Google is already installed. Its offline voices often sound more natural."
        override val googleUseAction = "Use Google"
    }

    private object Fr : ListenStrings {
        private val names = mapOf(
            "de" to "allemand", "en" to "anglais", "fr" to "français", "es" to "espagnol", "it" to "italien",
            "nl" to "néerlandais", "pl" to "polonais", "pt" to "portugais", "sv" to "suédois", "tr" to "turc", "ja" to "japonais",
        )

        override fun sourceSentence(source: String) = "Source : $source."
        override val articleFallback = "Article"
        override val openArticle = "Ouvrir l'article"
        override fun openReading(title: String) = "Lecture : ouvrir $title"
        override val stop = "Arrêter la lecture"
        override val resume = "Reprendre la lecture"
        override val pause = "Mettre la lecture en pause"
        override val statusPreparing = "PRÉPARATION DE LA VOIX"
        override val statusPaused = "EN PAUSE"
        override val statusReading = "LECTURE"
        override val statusOnlineVoice = "VOIX EN LIGNE"
        override fun remaining(minutes: Int) = "ENCORE ENV. $minutes MIN"
        override val noEngineTitle = "Lecture à voix haute indisponible"
        override val noEngineText = "Configure une synthèse vocale."
        override val missingVoiceText = "Télécharge-la une fois, et c'est bon."
        override val install = "Installer"
        override val setUp = "Configurer"
        override val dismiss = "Fermer l'avis"
        override fun missingVoiceTitle(voice: String) = "Aucune voix en ${languageName(voice)}"
        override fun installOfflineVoice(voice: String) = "Installer une voix hors ligne en ${languageName(voice)}"
        override fun installVoiceHint(voice: String) =
            "Installe une voix en ${languageName(voice)} dans Paramètres › Synthèse vocale."
        override fun languageName(code: String) = names[code.lowercase()] ?: platformName(code, AppLanguage.FR)

        override fun rateLabel(rate: Float) = "${ListenStrings.factor(rate, comma = true)}×"
        override fun speedChip(rate: String) = "Vitesse de lecture $rate"
        override val speedChipAction = "Changer la vitesse"
        override val sampleSentence = "Bonjour ! Voici comment sonne cette voix quand elle te lit les actualités."
        override val settingsOverline = "Comment sonne l'écoute"
        override val settingsTitle = "Lecture à voix haute"
        override val cardHint = "Voix, vitesse et langue quand l'appli te lit des articles."
        override val engineOverline = "Moteur de synthèse vocale"
        override val languageOverline = "Langue"
        override val languageAuto = "Automatique"
        override val languageAutoHint = "Chaque article dans sa propre langue – un résultat de recherche en anglais est lu en anglais."
        override fun languageFixedHint(language: String) = "Toujours en ${languageName(language)} – même les articles dans d'autres langues."
        override val voiceForOverline = "Voix pour"
        override fun voicesOverline(language: String) = "Voix en ${languageName(language)}"
        override val preferenceOverline = "De préférence"
        override val preferenceAny = "Peu importe"
        override val preferenceFemale = "Féminine"
        override val preferenceMale = "Masculine"
        override val voiceAuto = "Automatique"
        override fun voiceAutoText(source: VoiceSource, gender: VoiceGender?, number: Int?): String {
            val what = when {
                source == VoiceSource.PREFERENCE && gender == VoiceGender.FEMALE -> "Une voix féminine"
                source == VoiceSource.PREFERENCE && gender == VoiceGender.MALE -> "Une voix masculine"
                source == VoiceSource.ENGINE_DEFAULT -> "La voix par défaut du moteur"
                else -> "La meilleure voix hors ligne"
            }
            return if (number != null) "$what · en ce moment voix $number" else what
        }
        override fun voiceLabel(number: Int, gender: VoiceGender?, offline: Boolean) = ListenStrings.details(
            "Voix $number",
            when (gender) {
                VoiceGender.FEMALE -> "féminine"
                VoiceGender.MALE -> "masculine"
                null -> null
            },
            if (offline) "hors ligne" else "en ligne",
        )
        override fun voiceDetails(country: String?, quality: Int, slow: Boolean) = ListenStrings.details(
            country?.let { ListenStrings.countryName(it, AppLanguage.FR) },
            listOf("qualité très élevée", "haute qualité", "qualité normale", "qualité basique")[ListenStrings.qualityStep(quality)],
            if (slow) "démarre un peu plus tard" else null,
        )
        override fun previewVoice(label: String) = "Écouter $label"
        override val previewPlaying = "Extrait en cours"
        override fun otherRegions(count: Int) = "Voix d'autres régions ($count)"
        override val fewerRegions = "Afficher moins"
        override fun notInstalledVoices(count: Int) =
            if (count == 1) "Tu peux encore télécharger une autre voix." else "Tu peux encore télécharger $count autres voix."
        override val installMoreVoices = "Installer d'autres voix"
        override val chosenVoiceMissing = "La voix que tu as choisie n'est plus disponible – Automatique lit jusqu'à ce que tu en choisisses une autre."
        override fun noVoiceFor(language: String) =
            "Aucune voix en ${languageName(language)} n'est installée sur ce téléphone. Ces articles sont alors lus par la voix de la langue de ton téléphone – installes-en une pour qu'ils sonnent juste."
        override fun onlineOnlyFor(language: String) =
            "En ${languageName(language)}, il n'y a que des voix en ligne – sans internet, l'appli ne peut pas lire ces articles."
        override val engineListsNoVoices = "Ce moteur n'affiche pas ses voix – il lit avec sa voix par défaut."
        override val loadingVoices = "Chargement des voix …"
        override val voicesFailed = "La synthèse vocale ne répond pas pour le moment."
        override val speedOverline = "Vitesse"
        override val speedHint = "S'applique tout de suite, même en pleine lecture. Dans le mini-lecteur, un tap suffit pour la changer."
        override val speedDescription = "Vitesse de lecture"
        override val pitchOverline = "Hauteur"
        override fun pitchLabel(pitch: Float) =
            listOf("Plus grave", "Un peu plus grave", "Normale", "Un peu plus aiguë", "Plus aiguë")[ListenStrings.pitchStep(pitch)]
        override val pitchDescription = "Hauteur de la voix"
        override val neuralOverline = "Voix naturelles"
        override val neuralHint = "Entraînées sur des enregistrements de vraies personnes, elles lisent sans internet. Télécharge une voix une fois, elle reste sur ton téléphone."
        override val neuralMetered = "Tu n'es pas en Wi-Fi. La voix se téléchargera avec tes données mobiles."
        override fun neuralVoiceTitle(name: String, gender: VoiceGender) =
            "$name · " + if (gender == VoiceGender.FEMALE) "féminine" else "masculine"
        override fun neuralVoiceDetails(dataset: String, licence: String) =
            "Enregistrements : $dataset · " + if (licence == NeuralVoice.PUBLIC_DOMAIN) "domaine public" else licence
        override fun neuralDownload(megabytes: Int) = "Télécharger · $megabytes Mo"
        override fun neuralProgress(doneMegabytes: Int, totalMegabytes: Int) = "$doneMegabytes sur $totalMegabytes Mo"
        override val neuralUnpacking = "Décompression …"
        override fun neuralCancel(name: String) = "Arrêter le téléchargement de $name"
        override fun neuralDelete(name: String) = "Supprimer $name"
        override val neuralFailed = "Le téléchargement n'a pas marché. Vérifie ta connexion et touche à nouveau Télécharger."
        override val neuralSource = "Les voix viennent du projet Piper, via sherpa-onnx (k2-fsa) sur GitHub. Chaque voix indique ses enregistrements et leur licence."
        override val googleInstallHint = "Synthèse vocale Google propose gratuitement des voix hors ligne de haute qualité."
        override val googleInstallAction = "Installer Synthèse vocale Google"
        override val googleUseHint = "Synthèse vocale Google est déjà installée. Ses voix hors ligne sonnent souvent plus naturel."
        override val googleUseAction = "Utiliser Google"
    }

    private object Es : ListenStrings {
        private val names = mapOf(
            "de" to "alemán", "en" to "inglés", "fr" to "francés", "es" to "español", "it" to "italiano",
            "nl" to "neerlandés", "pl" to "polaco", "pt" to "portugués", "sv" to "sueco", "tr" to "turco", "ja" to "japonés",
        )

        override fun sourceSentence(source: String) = "Fuente: $source."
        override val articleFallback = "Artículo"
        override val openArticle = "Abrir artículo"
        override fun openReading(title: String) = "Lectura: abrir $title"
        override val stop = "Dejar de leer"
        override val resume = "Seguir leyendo"
        override val pause = "Pausar la lectura"
        override val statusPreparing = "PREPARANDO LA VOZ"
        override val statusPaused = "EN PAUSA"
        override val statusReading = "LEYENDO"
        override val statusOnlineVoice = "VOZ EN LÍNEA"
        override fun remaining(minutes: Int) = "QUEDAN UNOS $minutes MIN"
        override val noEngineTitle = "Lectura en voz alta no disponible"
        override val noEngineText = "Configura una síntesis de voz."
        override val missingVoiceText = "Descárgala una vez y listo."
        override val install = "Instalar"
        override val setUp = "Configurar"
        override val dismiss = "Cerrar aviso"
        override fun missingVoiceTitle(voice: String) = "No hay voz en ${languageName(voice)}"
        override fun installOfflineVoice(voice: String) = "Instalar una voz sin conexión en ${languageName(voice)}"
        override fun installVoiceHint(voice: String) =
            "Instala una voz en ${languageName(voice)} en Ajustes › Síntesis de voz."
        override fun languageName(code: String) = names[code.lowercase()] ?: platformName(code, AppLanguage.ES)

        override fun rateLabel(rate: Float) = "${ListenStrings.factor(rate, comma = true)}×"
        override fun speedChip(rate: String) = "Velocidad de lectura $rate"
        override val speedChipAction = "Cambiar la velocidad"
        override val sampleSentence = "¡Buenos días! Así suena esta voz cuando te lee las noticias."
        override val settingsOverline = "Cómo suena escuchar"
        override val settingsTitle = "Lectura en voz alta"
        override val cardHint = "Voz, velocidad e idioma cuando la app te lee artículos."
        override val engineOverline = "Motor de voz"
        override val languageOverline = "Idioma"
        override val languageAuto = "Automático"
        override val languageAutoHint = "Cada artículo en su propio idioma: un resultado de búsqueda en inglés se lee en inglés."
        override fun languageFixedHint(language: String) = "Siempre en ${languageName(language)}, también los artículos en otros idiomas."
        override val voiceForOverline = "Voz para"
        override fun voicesOverline(language: String) = "Voz en ${languageName(language)}"
        override val preferenceOverline = "Preferencia"
        override val preferenceAny = "Da igual"
        override val preferenceFemale = "Femenina"
        override val preferenceMale = "Masculina"
        override val voiceAuto = "Automática"
        override fun voiceAutoText(source: VoiceSource, gender: VoiceGender?, number: Int?): String {
            val what = when {
                source == VoiceSource.PREFERENCE && gender == VoiceGender.FEMALE -> "Una voz femenina"
                source == VoiceSource.PREFERENCE && gender == VoiceGender.MALE -> "Una voz masculina"
                source == VoiceSource.ENGINE_DEFAULT -> "La voz predeterminada del motor"
                else -> "La mejor voz sin conexión"
            }
            return if (number != null) "$what · ahora la voz $number" else what
        }
        override fun voiceLabel(number: Int, gender: VoiceGender?, offline: Boolean) = ListenStrings.details(
            "Voz $number",
            when (gender) {
                VoiceGender.FEMALE -> "femenina"
                VoiceGender.MALE -> "masculina"
                null -> null
            },
            if (offline) "sin conexión" else "en línea",
        )
        override fun voiceDetails(country: String?, quality: Int, slow: Boolean) = ListenStrings.details(
            country?.let { ListenStrings.countryName(it, AppLanguage.ES) },
            listOf("calidad muy alta", "alta calidad", "calidad normal", "calidad básica")[ListenStrings.qualityStep(quality)],
            if (slow) "tarda un poco en empezar" else null,
        )
        override fun previewVoice(label: String) = "Escuchar $label"
        override val previewPlaying = "Sonando una muestra"
        override fun otherRegions(count: Int) = "Voces de otras regiones ($count)"
        override val fewerRegions = "Mostrar menos"
        override fun notInstalledVoices(count: Int) =
            if (count == 1) "Aún puedes descargar una voz más." else "Aún puedes descargar $count voces más."
        override val installMoreVoices = "Instalar más voces"
        override val chosenVoiceMissing = "La voz que elegiste ya no está disponible: lee la automática hasta que elijas otra."
        override fun noVoiceFor(language: String) =
            "Este móvil no tiene ninguna voz en ${languageName(language)}. Esos artículos los lee entonces la voz del idioma de tu móvil: instala una para que suenen bien."
        override fun onlineOnlyFor(language: String) =
            "En ${languageName(language)} solo hay voces en línea: sin internet, la app no puede leer esos artículos."
        override val engineListsNoVoices = "Este motor no muestra sus voces: lee con su voz predeterminada."
        override val loadingVoices = "Cargando voces …"
        override val voicesFailed = "La síntesis de voz no responde ahora mismo."
        override val speedOverline = "Velocidad"
        override val speedHint = "Se aplica al momento, incluso a mitad del artículo. En el minirreproductor la cambias con un toque."
        override val speedDescription = "Velocidad de lectura"
        override val pitchOverline = "Tono"
        override fun pitchLabel(pitch: Float) =
            listOf("Más grave", "Algo más grave", "Normal", "Algo más aguda", "Más aguda")[ListenStrings.pitchStep(pitch)]
        override val pitchDescription = "Tono de la voz"
        override val neuralOverline = "Voces naturales"
        override val neuralHint = "Entrenadas con grabaciones de personas reales, leen sin internet. Descarga una voz una vez y se queda en tu móvil."
        override val neuralMetered = "No estás en una red wifi. La voz se descargará con datos móviles."
        override fun neuralVoiceTitle(name: String, gender: VoiceGender) =
            "$name · " + if (gender == VoiceGender.FEMALE) "femenina" else "masculina"
        override fun neuralVoiceDetails(dataset: String, licence: String) =
            "Grabaciones: $dataset · " + if (licence == NeuralVoice.PUBLIC_DOMAIN) "dominio público" else licence
        override fun neuralDownload(megabytes: Int) = "Descargar · $megabytes MB"
        override fun neuralProgress(doneMegabytes: Int, totalMegabytes: Int) = "$doneMegabytes de $totalMegabytes MB"
        override val neuralUnpacking = "Descomprimiendo …"
        override fun neuralCancel(name: String) = "Detener la descarga de $name"
        override fun neuralDelete(name: String) = "Eliminar $name"
        override val neuralFailed = "La descarga no funcionó. Revisa la conexión y toca Descargar otra vez."
        override val neuralSource = "Las voces vienen del proyecto Piper, a través de sherpa-onnx (k2-fsa) en GitHub. Cada voz indica sus grabaciones y su licencia."
        override val googleInstallHint = "Servicios de voz de Google incluye voces sin conexión gratuitas y de alta calidad."
        override val googleInstallAction = "Instalar Servicios de voz de Google"
        override val googleUseHint = "Servicios de voz de Google ya está instalado. Sus voces sin conexión suelen sonar más naturales."
        override val googleUseAction = "Usar Google"
    }

    private object It : ListenStrings {
        private val names = mapOf(
            "de" to "tedesco", "en" to "inglese", "fr" to "francese", "es" to "spagnolo", "it" to "italiano",
            "nl" to "olandese", "pl" to "polacco", "pt" to "portoghese", "sv" to "svedese", "tr" to "turco", "ja" to "giapponese",
        )

        override fun sourceSentence(source: String) = "Fonte: $source."
        override val articleFallback = "Articolo"
        override val openArticle = "Apri articolo"
        override fun openReading(title: String) = "Lettura: apri $title"
        override val stop = "Interrompi la lettura"
        override val resume = "Continua a leggere"
        override val pause = "Metti in pausa la lettura"
        override val statusPreparing = "PREPARAZIONE DELLA VOCE"
        override val statusPaused = "IN PAUSA"
        override val statusReading = "LETTURA"
        override val statusOnlineVoice = "VOCE ONLINE"
        override fun remaining(minutes: Int) = "ANCORA CIRCA $minutes MIN"
        override val noEngineTitle = "Lettura ad alta voce non disponibile"
        override val noEngineText = "Configura una sintesi vocale."
        override val missingVoiceText = "Scaricala una volta e sei a posto."
        override val install = "Installa"
        override val setUp = "Configura"
        override val dismiss = "Chiudi avviso"
        override fun missingVoiceTitle(voice: String) = "Nessuna voce in ${languageName(voice)}"
        override fun installOfflineVoice(voice: String) = "Installa una voce offline in ${languageName(voice)}"
        override fun installVoiceHint(voice: String) =
            "Installa una voce in ${languageName(voice)} in Impostazioni › Sintesi vocale."
        override fun languageName(code: String) = names[code.lowercase()] ?: platformName(code, AppLanguage.IT)

        override fun rateLabel(rate: Float) = "${ListenStrings.factor(rate, comma = true)}×"
        override fun speedChip(rate: String) = "Velocità di lettura $rate"
        override val speedChipAction = "Cambia velocità"
        override val sampleSentence = "Buongiorno! Ecco come suona questa voce quando ti legge le notizie."
        override val settingsOverline = "Come suona l'ascolto"
        override val settingsTitle = "Lettura ad alta voce"
        override val cardHint = "Voce, velocità e lingua quando l'app ti legge gli articoli."
        override val engineOverline = "Motore di sintesi vocale"
        override val languageOverline = "Lingua"
        override val languageAuto = "Automatica"
        override val languageAutoHint = "Ogni articolo nella sua lingua: un risultato di ricerca in inglese viene letto in inglese."
        override fun languageFixedHint(language: String) = "Sempre in ${languageName(language)}, anche gli articoli in altre lingue."
        override val voiceForOverline = "Voce per"
        override fun voicesOverline(language: String) = "Voce in ${languageName(language)}"
        override val preferenceOverline = "Preferenza"
        override val preferenceAny = "Indifferente"
        override val preferenceFemale = "Femminile"
        override val preferenceMale = "Maschile"
        override val voiceAuto = "Automatica"
        override fun voiceAutoText(source: VoiceSource, gender: VoiceGender?, number: Int?): String {
            val what = when {
                source == VoiceSource.PREFERENCE && gender == VoiceGender.FEMALE -> "Una voce femminile"
                source == VoiceSource.PREFERENCE && gender == VoiceGender.MALE -> "Una voce maschile"
                source == VoiceSource.ENGINE_DEFAULT -> "La voce predefinita del motore"
                else -> "La migliore voce offline"
            }
            return if (number != null) "$what · ora la voce $number" else what
        }
        override fun voiceLabel(number: Int, gender: VoiceGender?, offline: Boolean) = ListenStrings.details(
            "Voce $number",
            when (gender) {
                VoiceGender.FEMALE -> "femminile"
                VoiceGender.MALE -> "maschile"
                null -> null
            },
            if (offline) "offline" else "online",
        )
        override fun voiceDetails(country: String?, quality: Int, slow: Boolean) = ListenStrings.details(
            country?.let { ListenStrings.countryName(it, AppLanguage.IT) },
            listOf("qualità molto alta", "alta qualità", "qualità normale", "qualità di base")[ListenStrings.qualityStep(quality)],
            if (slow) "parte un po' più tardi" else null,
        )
        override fun previewVoice(label: String) = "Ascolta $label"
        override val previewPlaying = "Anteprima in riproduzione"
        override fun otherRegions(count: Int) = "Voci di altre regioni ($count)"
        override val fewerRegions = "Mostra meno"
        override fun notInstalledVoices(count: Int) =
            if (count == 1) "Puoi ancora scaricare un'altra voce." else "Puoi ancora scaricare altre $count voci."
        override val installMoreVoices = "Installa altre voci"
        override val chosenVoiceMissing = "La voce che hai scelto non è più disponibile: legge quella automatica finché non ne scegli un'altra."
        override fun noVoiceFor(language: String) =
            "Su questo telefono non c'è nessuna voce in ${languageName(language)}. Questi articoli li legge allora la voce della lingua del telefono: installane una perché suonino bene."
        override fun onlineOnlyFor(language: String) =
            "In ${languageName(language)} ci sono solo voci online: senza internet l'app non può leggere questi articoli."
        override val engineListsNoVoices = "Questo motore non mostra le sue voci: legge con la voce predefinita."
        override val loadingVoices = "Caricamento delle voci …"
        override val voicesFailed = "La sintesi vocale non risponde in questo momento."
        override val speedOverline = "Velocità"
        override val speedHint = "Vale subito, anche a metà articolo. Nel mini player la cambi con un tocco."
        override val speedDescription = "Velocità di lettura"
        override val pitchOverline = "Tono"
        override fun pitchLabel(pitch: Float) =
            listOf("Più grave", "Un po' più grave", "Normale", "Un po' più acuta", "Più acuta")[ListenStrings.pitchStep(pitch)]
        override val pitchDescription = "Tono della voce"
        override val neuralOverline = "Voci naturali"
        override val neuralHint = "Addestrate su registrazioni di persone vere, leggono senza internet. Scarica una voce una volta e resta sul telefono."
        override val neuralMetered = "Non sei in Wi-Fi. La voce verrà scaricata con i dati mobili."
        override fun neuralVoiceTitle(name: String, gender: VoiceGender) =
            "$name · " + if (gender == VoiceGender.FEMALE) "femminile" else "maschile"
        override fun neuralVoiceDetails(dataset: String, licence: String) =
            "Registrazioni: $dataset · " + if (licence == NeuralVoice.PUBLIC_DOMAIN) "pubblico dominio" else licence
        override fun neuralDownload(megabytes: Int) = "Scarica · $megabytes MB"
        override fun neuralProgress(doneMegabytes: Int, totalMegabytes: Int) = "$doneMegabytes di $totalMegabytes MB"
        override val neuralUnpacking = "Estrazione in corso …"
        override fun neuralCancel(name: String) = "Interrompi il download di $name"
        override fun neuralDelete(name: String) = "Elimina $name"
        override val neuralFailed = "Il download non è riuscito. Controlla la connessione e tocca di nuovo Scarica."
        override val neuralSource = "Le voci vengono dal progetto Piper, tramite sherpa-onnx (k2-fsa) su GitHub. Ogni voce indica le sue registrazioni e la loro licenza."
        override val googleInstallHint = "Servizi di sintesi vocale di Google offre voci offline gratuite e di alta qualità."
        override val googleInstallAction = "Installa i Servizi di sintesi vocale di Google"
        override val googleUseHint = "I Servizi di sintesi vocale di Google sono già installati. Le loro voci offline spesso suonano più naturali."
        override val googleUseAction = "Usa Google"
    }
}

@Composable
@ReadOnlyComposable
fun listenStrings(): ListenStrings = ListenStrings.of(LocalAppLanguage.current)

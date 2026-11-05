package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import java.util.Locale

/**
 * Texts of "Zuhören" (read aloud): the mini player, its hints, and the few words the voice
 * itself says ([sourceSentence]). Voice-related texts name the language of the voice by its ISO
 * code (`voice` = "de", "en" … — the language of the news edition).
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
    }
}

@Composable
@ReadOnlyComposable
fun listenStrings(): ListenStrings = ListenStrings.of(LocalAppLanguage.current)

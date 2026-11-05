package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome

/**
 * Texts of the in-app reader: why an article cannot be shown (from
 * [com.cedrickgd.morgenbriefing.data.article.ArticleException]: outcome, site, HTTP status), the
 * notes under an article, the reader's controls and the readability badges of feed cards.
 */
interface ReaderStrings {
    /** "Zuhören" – reads the article aloud. */
    val listen: String
    val pauseListening: String

    /** The menu entry / action that opens an article in the reader. */
    val readInApp: String

    /** Under an article that is probably cut short. */
    val truncatedNote: String

    /** "Quelle: tagesschau". */
    fun source(site: String): String

    /** Headline of the failure state. Null [outcome] = an unexpected error. */
    fun failureTitle(outcome: ReadOutcome?): String

    /**
     * Why the article cannot be shown, in one or two sentences. [site] is the newsroom's name
     * (may be blank), [httpCode] the server's error status, when it sent one.
     */
    fun failureReason(outcome: ReadOutcome?, site: String = "", httpCode: Int? = null): String

    /** Short badge for a feed card; empty for [ArticleStatus.UNKNOWN]. */
    fun status(status: ArticleStatus): String

    companion object {
        fun of(lang: AppLanguage): ReaderStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }

        private fun code(httpCode: Int?): String = if (httpCode != null) " ($httpCode)" else ""
    }

    private object De : ReaderStrings {
        override val listen = "Zuhören"
        override val pauseListening = "Vorlesen pausieren"
        override val readInApp = "Artikel lesen"
        override val truncatedNote = "Das war vermutlich nur der Anfang – den Rest zeigt die Redaktion nur auf ihrer Seite."
        override fun source(site: String) = "Quelle: $site"

        override fun failureTitle(outcome: ReadOutcome?) =
            if (outcome?.isWall == true) "Nur bei der Redaktion lesbar" else "Artikel gerade nicht da"

        override fun failureReason(outcome: ReadOutcome?, site: String, httpCode: Int?): String {
            val who = site.ifBlank { "Die Redaktion" }
            return when (outcome) {
                ReadOutcome.PAYWALL -> "$who zeigt diesen Artikel nur im Abo. Im Browser kannst du dich anmelden."
                ReadOutcome.CONSENT -> "$who zeigt den Text nur nach Cookie-Zustimmung auf der eigenen Seite."
                ReadOutcome.BLOCKED -> "$who lässt keine Lese-Apps auf die eigene Seite${code(httpCode)} – im Browser klappt es."
                ReadOutcome.NOT_ARTICLE -> if (httpCode == 404 || httpCode == 410) {
                    "Den Artikel gibt es nicht mehr${code(httpCode)}."
                } else {
                    "Auf der Seite steht kein lesbarer Text – vielleicht ein Video, ein Podcast oder eine Übersicht."
                }
                ReadOutcome.NETWORK -> when {
                    httpCode == 429 -> "Gerade zu viele Anfragen${code(httpCode)} – versuch es gleich nochmal."
                    httpCode != null -> "Der Server der Redaktion antwortet gerade nicht${code(httpCode)}."
                    else -> "Die Seite ist gerade nicht erreichbar. Bist du online?"
                }
                ReadOutcome.OK, null -> "Der Artikel ließ sich gerade nicht laden."
            }
        }

        override fun status(status: ArticleStatus) = when (status) {
            ArticleStatus.UNKNOWN -> ""
            ArticleStatus.LOADING -> "Wird geladen"
            ArticleStatus.READABLE -> "Offline lesbar"
            ArticleStatus.PAYWALL -> "Abo"
            ArticleStatus.BLOCKED -> "Nur im Browser"
            ArticleStatus.FAILED -> "Nicht lesbar"
        }
    }

    private object En : ReaderStrings {
        override val listen = "Listen"
        override val pauseListening = "Pause reading aloud"
        override val readInApp = "Read article"
        override val truncatedNote = "That was probably just the beginning – the publisher shows the rest only on its own site."
        override fun source(site: String) = "Source: $site"

        override fun failureTitle(outcome: ReadOutcome?) =
            if (outcome?.isWall == true) "Only readable on the publisher's site" else "Article not available right now"

        override fun failureReason(outcome: ReadOutcome?, site: String, httpCode: Int?): String {
            val who = site.ifBlank { "The publisher" }
            return when (outcome) {
                ReadOutcome.PAYWALL -> "$who keeps this article for subscribers. You can sign in in your browser."
                ReadOutcome.CONSENT -> "$who only shows the text after you accept its cookies on its own site."
                ReadOutcome.BLOCKED -> "$who doesn't let reader apps onto its site${code(httpCode)} – it works in the browser."
                ReadOutcome.NOT_ARTICLE -> if (httpCode == 404 || httpCode == 410) {
                    "This article no longer exists${code(httpCode)}."
                } else {
                    "There's no readable text on this page – maybe it's a video, a podcast or an overview."
                }
                ReadOutcome.NETWORK -> when {
                    httpCode == 429 -> "Too many requests right now${code(httpCode)} – try again in a moment."
                    httpCode != null -> "The publisher's server isn't responding${code(httpCode)}."
                    else -> "The page can't be reached right now. Are you online?"
                }
                ReadOutcome.OK, null -> "The article couldn't be loaded just now."
            }
        }

        override fun status(status: ArticleStatus) = when (status) {
            ArticleStatus.UNKNOWN -> ""
            ArticleStatus.LOADING -> "Loading"
            ArticleStatus.READABLE -> "Available offline"
            ArticleStatus.PAYWALL -> "Subscribers"
            ArticleStatus.BLOCKED -> "Browser only"
            ArticleStatus.FAILED -> "Not readable"
        }
    }

    private object Fr : ReaderStrings {
        override val listen = "Écouter"
        override val pauseListening = "Mettre la lecture en pause"
        override val readInApp = "Lire l'article"
        override val truncatedNote = "Ce n'était sans doute que le début : la rédaction ne montre la suite que sur son propre site."
        override fun source(site: String) = "Source : $site"

        override fun failureTitle(outcome: ReadOutcome?) =
            if (outcome?.isWall == true) "Lisible uniquement chez l'éditeur" else "Article indisponible pour le moment"

        override fun failureReason(outcome: ReadOutcome?, site: String, httpCode: Int?): String {
            val who = site.ifBlank { "La rédaction" }
            return when (outcome) {
                ReadOutcome.PAYWALL -> "$who réserve cet article à ses abonnés. Tu peux te connecter dans le navigateur."
                ReadOutcome.CONSENT -> "$who n'affiche le texte qu'après acceptation des cookies sur son propre site."
                ReadOutcome.BLOCKED -> "$who n'accepte pas les applis de lecture sur son site${code(httpCode)} – dans le navigateur, ça marche."
                ReadOutcome.NOT_ARTICLE -> if (httpCode == 404 || httpCode == 410) {
                    "Cet article n'existe plus${code(httpCode)}."
                } else {
                    "Cette page ne contient pas de texte lisible – peut-être une vidéo, un podcast ou une page d'accueil."
                }
                ReadOutcome.NETWORK -> when {
                    httpCode == 429 -> "Trop de requêtes en ce moment${code(httpCode)} – réessaie dans un instant."
                    httpCode != null -> "Le serveur de la rédaction ne répond pas${code(httpCode)}."
                    else -> "La page est injoignable pour le moment. Es-tu connecté ?"
                }
                ReadOutcome.OK, null -> "L'article n'a pas pu être chargé pour le moment."
            }
        }

        override fun status(status: ArticleStatus) = when (status) {
            ArticleStatus.UNKNOWN -> ""
            ArticleStatus.LOADING -> "Chargement"
            ArticleStatus.READABLE -> "Lisible hors ligne"
            ArticleStatus.PAYWALL -> "Abonnés"
            ArticleStatus.BLOCKED -> "Navigateur seulement"
            ArticleStatus.FAILED -> "Illisible"
        }
    }

    private object Es : ReaderStrings {
        override val listen = "Escuchar"
        override val pauseListening = "Pausar la lectura"
        override val readInApp = "Leer artículo"
        override val truncatedNote = "Probablemente esto era solo el comienzo: el medio muestra el resto únicamente en su web."
        override fun source(site: String) = "Fuente: $site"

        override fun failureTitle(outcome: ReadOutcome?) =
            if (outcome?.isWall == true) "Solo se puede leer en la web del medio" else "Artículo no disponible ahora"

        override fun failureReason(outcome: ReadOutcome?, site: String, httpCode: Int?): String {
            val who = site.ifBlank { "El medio" }
            return when (outcome) {
                ReadOutcome.PAYWALL -> "$who reserva este artículo para suscriptores. Puedes iniciar sesión en el navegador."
                ReadOutcome.CONSENT -> "$who solo muestra el texto si aceptas sus cookies en su propia web."
                ReadOutcome.BLOCKED -> "$who no deja entrar a apps de lectura en su web${code(httpCode)}; en el navegador sí funciona."
                ReadOutcome.NOT_ARTICLE -> if (httpCode == 404 || httpCode == 410) {
                    "Este artículo ya no existe${code(httpCode)}."
                } else {
                    "En esta página no hay texto legible: quizá sea un vídeo, un pódcast o una portada."
                }
                ReadOutcome.NETWORK -> when {
                    httpCode == 429 -> "Demasiadas solicitudes ahora mismo${code(httpCode)}; inténtalo de nuevo en un momento."
                    httpCode != null -> "El servidor del medio no responde${code(httpCode)}."
                    else -> "No se puede acceder a la página ahora. ¿Tienes conexión?"
                }
                ReadOutcome.OK, null -> "No se ha podido cargar el artículo."
            }
        }

        override fun status(status: ArticleStatus) = when (status) {
            ArticleStatus.UNKNOWN -> ""
            ArticleStatus.LOADING -> "Cargando"
            ArticleStatus.READABLE -> "Disponible sin conexión"
            ArticleStatus.PAYWALL -> "Suscriptores"
            ArticleStatus.BLOCKED -> "Solo en el navegador"
            ArticleStatus.FAILED -> "No legible"
        }
    }

    private object It : ReaderStrings {
        override val listen = "Ascolta"
        override val pauseListening = "Metti in pausa la lettura"
        override val readInApp = "Leggi l'articolo"
        override val truncatedNote = "Probabilmente era solo l'inizio: la redazione mostra il resto solo sul proprio sito."
        override fun source(site: String) = "Fonte: $site"

        override fun failureTitle(outcome: ReadOutcome?) =
            if (outcome?.isWall == true) "Leggibile solo sul sito della redazione" else "Articolo non disponibile al momento"

        override fun failureReason(outcome: ReadOutcome?, site: String, httpCode: Int?): String {
            val who = site.ifBlank { "La redazione" }
            return when (outcome) {
                ReadOutcome.PAYWALL -> "$who riserva questo articolo agli abbonati. Puoi accedere dal browser."
                ReadOutcome.CONSENT -> "$who mostra il testo solo dopo aver accettato i cookie sul proprio sito."
                ReadOutcome.BLOCKED -> "$who non lascia entrare le app di lettura sul proprio sito${code(httpCode)}: nel browser funziona."
                ReadOutcome.NOT_ARTICLE -> if (httpCode == 404 || httpCode == 410) {
                    "Questo articolo non esiste più${code(httpCode)}."
                } else {
                    "In questa pagina non c'è testo leggibile: forse è un video, un podcast o una pagina di sezione."
                }
                ReadOutcome.NETWORK -> when {
                    httpCode == 429 -> "Troppe richieste in questo momento${code(httpCode)}: riprova tra poco."
                    httpCode != null -> "Il server della redazione non risponde${code(httpCode)}."
                    else -> "La pagina non è raggiungibile al momento. Sei online?"
                }
                ReadOutcome.OK, null -> "Non è stato possibile caricare l'articolo."
            }
        }

        override fun status(status: ArticleStatus) = when (status) {
            ArticleStatus.UNKNOWN -> ""
            ArticleStatus.LOADING -> "Caricamento"
            ArticleStatus.READABLE -> "Leggibile offline"
            ArticleStatus.PAYWALL -> "Abbonati"
            ArticleStatus.BLOCKED -> "Solo nel browser"
            ArticleStatus.FAILED -> "Non leggibile"
        }
    }
}

@Composable
@ReadOnlyComposable
fun readerStrings(): ReaderStrings = ReaderStrings.of(LocalAppLanguage.current)

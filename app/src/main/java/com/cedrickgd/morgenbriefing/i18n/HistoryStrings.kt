package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * "Gelesen & ausgeblendet": the archive of the stories the user read or hid – its head, search,
 * filters, the day headings, the row menu, clearing the history and its empty states.
 */
interface HistoryStrings {
    val overline: String
    val title: String
    fun counts(read: Int, hidden: Int): String
    val searchPlaceholder: String
    val clearSearch: String
    val filterAll: String
    val filterRead: String
    val filterHidden: String
    val today: String
    val yesterday: String
    val statusRead: String
    val statusHidden: String
    val showAgain: String
    val markUnread: String
    val shownAgain: String
    val markedUnread: String
    val clearHistory: String
    val clearTitle: String
    val clearText: String
    val cleared: String
    val emptyTitle: String
    val emptyText: String
    fun noMatch(query: String): String
    val noMatchText: String
    val noneInFilter: String

    companion object {
        fun of(lang: AppLanguage): HistoryStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : HistoryStrings {
        override val overline = "Dein Verlauf"
        override val title = "Gelesen & ausgeblendet"
        override fun counts(read: Int, hidden: Int) = "$read gelesen · $hidden ausgeblendet"
        override val searchPlaceholder = "Titel oder Quelle suchen"
        override val clearSearch = "Suche leeren"
        override val filterAll = "Alle"
        override val filterRead = "Gelesen"
        override val filterHidden = "Ausgeblendet"
        override val today = "Heute"
        override val yesterday = "Gestern"
        override val statusRead = "Gelesen"
        override val statusHidden = "Ausgeblendet"
        override val showAgain = "Wieder einblenden"
        override val markUnread = "Als ungelesen markieren"
        override val shownAgain = "Wieder im Feed"
        override val markedUnread = "Als ungelesen markiert"
        override val clearHistory = "Verlauf löschen"
        override val clearTitle = "Verlauf löschen?"
        override val clearText =
            "Alle gelesenen und ausgeblendeten Artikel werden vergessen, ausgeblendete erscheinen wieder im Feed. Das lässt sich nicht rückgängig machen."
        override val cleared = "Verlauf gelöscht"
        override val emptyTitle = "Noch nichts gelesen"
        override val emptyText = "Was du öffnest, anhörst oder ausblendest, findest du hier wieder – 60 Tage lang."
        override fun noMatch(query: String) = "Nichts zu „$query“"
        override val noMatchText = "Probier ein anderes Wort oder den Namen der Quelle."
        override val noneInFilter = "Hier ist gerade nichts."
    }

    private object En : HistoryStrings {
        override val overline = "Your history"
        override val title = "Read & hidden"
        override fun counts(read: Int, hidden: Int) = "$read read · $hidden hidden"
        override val searchPlaceholder = "Search title or source"
        override val clearSearch = "Clear search"
        override val filterAll = "All"
        override val filterRead = "Read"
        override val filterHidden = "Hidden"
        override val today = "Today"
        override val yesterday = "Yesterday"
        override val statusRead = "Read"
        override val statusHidden = "Hidden"
        override val showAgain = "Show again"
        override val markUnread = "Mark as unread"
        override val shownAgain = "Back in the feed"
        override val markedUnread = "Marked as unread"
        override val clearHistory = "Clear history"
        override val clearTitle = "Clear history?"
        override val clearText = "Every read and hidden story is forgotten, hidden ones show in the feed again. This can't be undone."
        override val cleared = "History cleared"
        override val emptyTitle = "Nothing read yet"
        override val emptyText = "What you open, listen to or hide shows up here again – for 60 days."
        override fun noMatch(query: String) = "Nothing for “$query”"
        override val noMatchText = "Try another word or the source's name."
        override val noneInFilter = "Nothing here right now."
    }

    private object Fr : HistoryStrings {
        override val overline = "Ton historique"
        override val title = "Lus et masqués"
        override fun counts(read: Int, hidden: Int) =
            "$read ${if (read == 1) "lu" else "lus"} · $hidden ${if (hidden == 1) "masqué" else "masqués"}"
        override val searchPlaceholder = "Chercher un titre ou une source"
        override val clearSearch = "Effacer la recherche"
        override val filterAll = "Tous"
        override val filterRead = "Lus"
        override val filterHidden = "Masqués"
        override val today = "Aujourd'hui"
        override val yesterday = "Hier"
        override val statusRead = "Lu"
        override val statusHidden = "Masqué"
        override val showAgain = "Réafficher"
        override val markUnread = "Marquer comme non lu"
        override val shownAgain = "De retour dans le fil"
        override val markedUnread = "Marqué comme non lu"
        override val clearHistory = "Effacer l'historique"
        override val clearTitle = "Effacer l'historique ?"
        override val clearText =
            "Tous les articles lus et masqués seront oubliés, les articles masqués réapparaîtront dans le fil. Impossible d'annuler."
        override val cleared = "Historique effacé"
        override val emptyTitle = "Rien lu pour l'instant"
        override val emptyText = "Ce que tu ouvres, écoutes ou masques se retrouve ici – pendant 60 jours."
        override fun noMatch(query: String) = "Rien pour « $query »"
        override val noMatchText = "Essaie un autre mot ou le nom de la source."
        override val noneInFilter = "Rien ici pour le moment."
    }

    private object Es : HistoryStrings {
        override val overline = "Tu historial"
        override val title = "Leídos y ocultos"
        override fun counts(read: Int, hidden: Int) =
            "$read ${if (read == 1) "leído" else "leídos"} · $hidden ${if (hidden == 1) "oculto" else "ocultos"}"
        override val searchPlaceholder = "Buscar título o fuente"
        override val clearSearch = "Borrar búsqueda"
        override val filterAll = "Todos"
        override val filterRead = "Leídos"
        override val filterHidden = "Ocultos"
        override val today = "Hoy"
        override val yesterday = "Ayer"
        override val statusRead = "Leído"
        override val statusHidden = "Oculto"
        override val showAgain = "Volver a mostrar"
        override val markUnread = "Marcar como no leído"
        override val shownAgain = "De vuelta en el feed"
        override val markedUnread = "Marcado como no leído"
        override val clearHistory = "Borrar historial"
        override val clearTitle = "¿Borrar el historial?"
        override val clearText =
            "Se olvidarán todos los artículos leídos y ocultos, los ocultos volverán a aparecer en el feed. No se puede deshacer."
        override val cleared = "Historial borrado"
        override val emptyTitle = "Aún no has leído nada"
        override val emptyText = "Lo que abras, escuches u ocultes aparece aquí – durante 60 días."
        override fun noMatch(query: String) = "Nada para «$query»"
        override val noMatchText = "Prueba otra palabra o el nombre de la fuente."
        override val noneInFilter = "Aquí no hay nada por ahora."
    }

    private object It : HistoryStrings {
        override val overline = "La tua cronologia"
        override val title = "Letti e nascosti"
        override fun counts(read: Int, hidden: Int) =
            "$read ${if (read == 1) "letto" else "letti"} · $hidden ${if (hidden == 1) "nascosto" else "nascosti"}"
        override val searchPlaceholder = "Cerca titolo o fonte"
        override val clearSearch = "Cancella ricerca"
        override val filterAll = "Tutti"
        override val filterRead = "Letti"
        override val filterHidden = "Nascosti"
        override val today = "Oggi"
        override val yesterday = "Ieri"
        override val statusRead = "Letto"
        override val statusHidden = "Nascosto"
        override val showAgain = "Mostra di nuovo"
        override val markUnread = "Segna come non letto"
        override val shownAgain = "Di nuovo nel feed"
        override val markedUnread = "Segnato come non letto"
        override val clearHistory = "Cancella cronologia"
        override val clearTitle = "Cancellare la cronologia?"
        override val clearText =
            "Tutti gli articoli letti e nascosti verranno dimenticati, quelli nascosti torneranno nel feed. Non si può annullare."
        override val cleared = "Cronologia cancellata"
        override val emptyTitle = "Ancora niente di letto"
        override val emptyText = "Ciò che apri, ascolti o nascondi lo ritrovi qui – per 60 giorni."
        override fun noMatch(query: String) = "Niente per «$query»"
        override val noMatchText = "Prova un'altra parola o il nome della fonte."
        override val noneInFilter = "Qui per ora non c'è niente."
    }
}

@Composable
@ReadOnlyComposable
fun historyStrings(): HistoryStrings = HistoryStrings.of(LocalAppLanguage.current)

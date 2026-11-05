package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Texts of the news search: the field, recent searches, "Gerade wichtig" (the edition's top
 * stories), the results with their states and "Diesem Thema folgen".
 */
interface SearchStrings {
    val fieldHint: String
    val clear: String

    val hintTitle: String
    val hintBody: String

    val recentOverline: String
    val clearRecent: String
    fun removeRecent(query: String): String

    /** Click label of a recent search chip. */
    val searchAgain: String

    val trendingOverline: String
    val trendingTitle: String
    val trendingEmpty: String

    val resultsOverline: String
    fun resultCount(count: Int): String

    /** Result order: as ranked / newest first. */
    val sortRelevance: String
    val sortNewest: String

    /** Language choice: every edition / only the edition's language [name] ("Deutsch"). */
    val allLanguages: String
    fun onlyLanguage(name: String): String

    /** Spoken for the small language tag on a result in another language ([name]: "Englisch"). */
    fun inLanguage(name: String): String

    /** "Only readable articles": [count] results are still being checked. */
    fun checking(count: Int): String
    val checkingTitle: String
    val checkingBody: String

    /** "Only readable articles" left nothing: there were results, none complete in the app. */
    fun noneReadableTitle(query: String): String
    val noneReadableBody: String

    val follow: String
    val following: String
    fun followed(query: String): String
    fun unfollowed(query: String): String

    fun emptyTitle(query: String): String
    val emptyBody: String
    val failedTitle: String
    val failedBody: String

    /** Google News could not be reached; only stories already on the phone are shown. */
    val offline: String
    val moreFailed: String
    fun end(count: Int): String

    /** Live announcement while results load. */
    fun searching(query: String): String

    val discover: String

    val listenUnavailable: String
    val listenFailed: String
    val copyFailed: String
    val linkCopied: String
    val saveFailed: String

    companion object {
        fun of(lang: AppLanguage): SearchStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : SearchStrings {
        override val fieldHint = "Nachrichten durchsuchen"
        override val clear = "Suche leeren"
        override val hintTitle = "Wonach suchst du?"
        override val hintBody = "Ein Name, ein Ort, ein Ereignis – gesucht wird in Nachrichten aus aller Welt, nicht nur in deinen Themen."
        override val recentOverline = "Zuletzt gesucht"
        override val clearRecent = "Alle löschen"
        override fun removeRecent(query: String) = "„$query“ aus dem Verlauf löschen"
        override val searchAgain = "Erneut suchen"
        override val trendingOverline = "Gerade wichtig"
        override val trendingTitle = "Die Schlagzeilen von jetzt"
        override val trendingEmpty = "Die Schlagzeilen laden gleich."
        override val resultsOverline = "Treffer"
        override fun resultCount(count: Int) = if (count == 1) "1 Meldung" else "$count Meldungen"
        override val sortRelevance = "Relevanz"
        override val sortNewest = "Neueste zuerst"
        override val allLanguages = "Alle Sprachen"
        override fun onlyLanguage(name: String) = "Nur $name"
        override fun inLanguage(name: String) = "Auf $name"
        override fun checking(count: Int) = if (count == 1) "1 weiterer Artikel wird geprüft …" else "$count weitere Artikel werden geprüft …"
        override val checkingTitle = "Artikel werden geprüft"
        override val checkingBody = "Du siehst nur Artikel, die sich komplett in der App lesen lassen – sie erscheinen, sobald sie geprüft sind."
        override fun noneReadableTitle(query: String) = "Nichts komplett Lesbares zu „$query“"
        override val noneReadableBody = "Treffer gab es, aber keiner lässt sich ganz in der App lesen. Mehr Wörter oder ein anderer Begriff helfen oft."
        override val follow = "Diesem Thema folgen"
        override val following = "Du folgst diesem Thema"
        override fun followed(query: String) = "„$query“ ist jetzt in deinem Feed"
        override fun unfollowed(query: String) = "Du folgst „$query“ nicht mehr"
        override fun emptyTitle(query: String) = "Nichts gefunden zu „$query“"
        override val emptyBody = "Versuch es mit anderen oder weniger Wörtern."
        override val failedTitle = "Suche gerade nicht möglich"
        override val failedBody = "Die Nachrichtensuche ist nicht erreichbar. Bist du online?"
        override val offline = "Offline – das sind nur Meldungen, die schon auf dem Handy sind."
        override val moreFailed = "Weitere Treffer ließen sich gerade nicht laden."
        override fun end(count: Int) = if (count == 1) "Das war's · 1 Treffer" else "Das war's · $count Treffer"
        override fun searching(query: String) = "Suche nach „$query“ …"
        override val discover = "Themen entdecken"
        override val listenUnavailable = "Vorlesen ist hier gerade nicht verfügbar."
        override val listenFailed = "Vorlesen klappt gerade nicht."
        override val copyFailed = "Kopieren hat nicht geklappt."
        override val linkCopied = "Link kopiert"
        override val saveFailed = "Das ließ sich gerade nicht speichern."
    }

    private object En : SearchStrings {
        override val fieldHint = "Search the news"
        override val clear = "Clear search"
        override val hintTitle = "What are you looking for?"
        override val hintBody = "A name, a place, an event: the search covers news from all over the world, not just your topics."
        override val recentOverline = "Recent searches"
        override val clearRecent = "Clear all"
        override fun removeRecent(query: String) = "Remove “$query” from history"
        override val searchAgain = "Search again"
        override val trendingOverline = "Right now"
        override val trendingTitle = "Today’s top stories"
        override val trendingEmpty = "The top stories are on their way."
        override val resultsOverline = "Results"
        override fun resultCount(count: Int) = if (count == 1) "1 story" else "$count stories"
        override val sortRelevance = "Most relevant"
        override val sortNewest = "Newest first"
        override val allLanguages = "All languages"
        override fun onlyLanguage(name: String) = "Only $name"
        override fun inLanguage(name: String) = "In $name"
        override fun checking(count: Int) = if (count == 1) "Checking 1 more article …" else "Checking $count more articles …"
        override val checkingTitle = "Checking articles"
        override val checkingBody = "You only see articles that can be read in full in the app – they appear as soon as they’re checked."
        override fun noneReadableTitle(query: String) = "Nothing fully readable for “$query”"
        override val noneReadableBody = "There were results, but none can be read in full in the app. More words or a different term often help."
        override val follow = "Follow this topic"
        override val following = "You follow this topic"
        override fun followed(query: String) = "“$query” is now in your feed"
        override fun unfollowed(query: String) = "You no longer follow “$query”"
        override fun emptyTitle(query: String) = "Nothing found for “$query”"
        override val emptyBody = "Try other or fewer words."
        override val failedTitle = "Search isn’t possible right now"
        override val failedBody = "The news search can’t be reached. Are you online?"
        override val offline = "Offline – these are only stories already on your phone."
        override val moreFailed = "More results couldn’t be loaded just now."
        override fun end(count: Int) = if (count == 1) "That’s all · 1 result" else "That’s all · $count results"
        override fun searching(query: String) = "Searching for “$query” …"
        override val discover = "Discover topics"
        override val listenUnavailable = "Reading aloud isn’t available here right now."
        override val listenFailed = "Reading aloud isn’t working right now."
        override val copyFailed = "Copying didn’t work."
        override val linkCopied = "Link copied"
        override val saveFailed = "That couldn’t be saved just now."
    }

    private object Fr : SearchStrings {
        override val fieldHint = "Rechercher dans l’actu"
        override val clear = "Effacer la recherche"
        override val hintTitle = "Que cherches-tu ?"
        override val hintBody = "Un nom, un lieu, un événement : la recherche porte sur l’actualité du monde entier, pas seulement sur tes thèmes."
        override val recentOverline = "Recherches récentes"
        override val clearRecent = "Tout effacer"
        override fun removeRecent(query: String) = "Retirer « $query » de l’historique"
        override val searchAgain = "Relancer la recherche"
        override val trendingOverline = "En ce moment"
        override val trendingTitle = "Les gros titres du moment"
        override val trendingEmpty = "Les gros titres arrivent."
        override val resultsOverline = "Résultats"
        override fun resultCount(count: Int) = if (count == 1) "1 article" else "$count articles"
        override val sortRelevance = "Pertinence"
        override val sortNewest = "Plus récents d’abord"
        override val allLanguages = "Toutes les langues"
        override fun onlyLanguage(name: String) = "Seulement en $name"
        override fun inLanguage(name: String) = "En $name"
        override fun checking(count: Int) = if (count == 1) "Vérification d’un autre article …" else "Vérification de $count autres articles …"
        override val checkingTitle = "Vérification des articles"
        override val checkingBody = "Tu ne vois que les articles lisibles en entier dans l’app – ils s’affichent dès qu’ils sont vérifiés."
        override fun noneReadableTitle(query: String) = "Rien de lisible en entier pour « $query »"
        override val noneReadableBody = "Il y avait des résultats, mais aucun n’est lisible en entier dans l’app. Ajoute des mots ou essaie un autre terme."
        override val follow = "Suivre ce thème"
        override val following = "Tu suis ce thème"
        override fun followed(query: String) = "« $query » est maintenant dans ton fil"
        override fun unfollowed(query: String) = "Tu ne suis plus « $query »"
        override fun emptyTitle(query: String) = "Aucun résultat pour « $query »"
        override val emptyBody = "Essaie d’autres mots, ou moins de mots."
        override val failedTitle = "Recherche impossible pour le moment"
        override val failedBody = "La recherche d’actualités est injoignable. Es-tu en ligne ?"
        override val offline = "Hors ligne – seuls les articles déjà sur ton téléphone s’affichent."
        override val moreFailed = "Impossible de charger plus de résultats pour le moment."
        override fun end(count: Int) = if (count == 1) "C’est tout · 1 résultat" else "C’est tout · $count résultats"
        override fun searching(query: String) = "Recherche de « $query » …"
        override val discover = "Découvrir des thèmes"
        override val listenUnavailable = "La lecture à voix haute n’est pas disponible ici."
        override val listenFailed = "La lecture à voix haute ne fonctionne pas pour le moment."
        override val copyFailed = "La copie n’a pas fonctionné."
        override val linkCopied = "Lien copié"
        override val saveFailed = "Impossible d’enregistrer pour le moment."
    }

    private object Es : SearchStrings {
        override val fieldHint = "Buscar noticias"
        override val clear = "Borrar búsqueda"
        override val hintTitle = "¿Qué buscas?"
        override val hintBody = "Un nombre, un lugar, un acontecimiento: se busca en noticias de todo el mundo, no solo en tus temas."
        override val recentOverline = "Búsquedas recientes"
        override val clearRecent = "Borrar todo"
        override fun removeRecent(query: String) = "Quitar «$query» del historial"
        override val searchAgain = "Buscar de nuevo"
        override val trendingOverline = "Ahora mismo"
        override val trendingTitle = "Los titulares del momento"
        override val trendingEmpty = "Los titulares están en camino."
        override val resultsOverline = "Resultados"
        override fun resultCount(count: Int) = if (count == 1) "1 noticia" else "$count noticias"
        override val sortRelevance = "Relevancia"
        override val sortNewest = "Más recientes primero"
        override val allLanguages = "Todos los idiomas"
        override fun onlyLanguage(name: String) = "Solo en $name"
        override fun inLanguage(name: String) = "En $name"
        override fun checking(count: Int) = if (count == 1) "Comprobando 1 artículo más …" else "Comprobando $count artículos más …"
        override val checkingTitle = "Comprobando artículos"
        override val checkingBody = "Solo ves artículos que se pueden leer completos en la app: aparecen en cuanto se comprueban."
        override fun noneReadableTitle(query: String) = "Nada legible completo para «$query»"
        override val noneReadableBody = "Hubo resultados, pero ninguno se puede leer completo en la app. Más palabras u otro término suelen ayudar."
        override val follow = "Seguir este tema"
        override val following = "Sigues este tema"
        override fun followed(query: String) = "«$query» ya está en tu feed"
        override fun unfollowed(query: String) = "Ya no sigues «$query»"
        override fun emptyTitle(query: String) = "No hay resultados para «$query»"
        override val emptyBody = "Prueba con otras palabras o con menos."
        override val failedTitle = "Ahora no se puede buscar"
        override val failedBody = "La búsqueda de noticias no responde. ¿Tienes conexión?"
        override val offline = "Sin conexión: solo ves noticias que ya están en el móvil."
        override val moreFailed = "No se pudieron cargar más resultados."
        override fun end(count: Int) = if (count == 1) "Eso es todo · 1 resultado" else "Eso es todo · $count resultados"
        override fun searching(query: String) = "Buscando «$query» …"
        override val discover = "Descubrir temas"
        override val listenUnavailable = "La lectura en voz alta no está disponible aquí."
        override val listenFailed = "La lectura en voz alta no funciona ahora mismo."
        override val copyFailed = "No se pudo copiar."
        override val linkCopied = "Enlace copiado"
        override val saveFailed = "No se pudo guardar ahora mismo."
    }

    private object It : SearchStrings {
        override val fieldHint = "Cerca nelle notizie"
        override val clear = "Cancella ricerca"
        override val hintTitle = "Che cosa cerchi?"
        override val hintBody = "Un nome, un luogo, un evento: la ricerca copre le notizie di tutto il mondo, non solo i tuoi temi."
        override val recentOverline = "Ricerche recenti"
        override val clearRecent = "Cancella tutto"
        override fun removeRecent(query: String) = "Rimuovi «$query» dalla cronologia"
        override val searchAgain = "Cerca di nuovo"
        override val trendingOverline = "In questo momento"
        override val trendingTitle = "I titoli del momento"
        override val trendingEmpty = "I titoli stanno arrivando."
        override val resultsOverline = "Risultati"
        override fun resultCount(count: Int) = if (count == 1) "1 notizia" else "$count notizie"
        override val sortRelevance = "Rilevanza"
        override val sortNewest = "Più recenti prima"
        override val allLanguages = "Tutte le lingue"
        override fun onlyLanguage(name: String) = "Solo in $name"
        override fun inLanguage(name: String) = "In $name"
        override fun checking(count: Int) = if (count == 1) "Verifica di un altro articolo …" else "Verifica di altri $count articoli …"
        override val checkingTitle = "Verifica degli articoli"
        override val checkingBody = "Vedi solo articoli leggibili per intero nell’app: compaiono appena sono verificati."
        override fun noneReadableTitle(query: String) = "Niente di leggibile per intero per «$query»"
        override val noneReadableBody = "C’erano risultati, ma nessuno si legge per intero nell’app. Più parole o un altro termine spesso aiutano."
        override val follow = "Segui questo tema"
        override val following = "Stai seguendo questo tema"
        override fun followed(query: String) = "«$query» ora è nel tuo feed"
        override fun unfollowed(query: String) = "Non segui più «$query»"
        override fun emptyTitle(query: String) = "Nessun risultato per «$query»"
        override val emptyBody = "Prova con altre parole o con meno parole."
        override val failedTitle = "Ricerca non possibile al momento"
        override val failedBody = "La ricerca di notizie non risponde. Sei online?"
        override val offline = "Offline: vedi solo le notizie già presenti sul telefono."
        override val moreFailed = "Non è stato possibile caricare altri risultati."
        override fun end(count: Int) = if (count == 1) "È tutto · 1 risultato" else "È tutto · $count risultati"
        override fun searching(query: String) = "Ricerca di «$query» …"
        override val discover = "Scopri i temi"
        override val listenUnavailable = "La lettura ad alta voce qui non è disponibile."
        override val listenFailed = "La lettura ad alta voce al momento non funziona."
        override val copyFailed = "Copia non riuscita."
        override val linkCopied = "Link copiato"
        override val saveFailed = "Non è stato possibile salvare."
    }
}

@Composable
@ReadOnlyComposable
fun searchStrings(): SearchStrings = SearchStrings.of(LocalAppLanguage.current)

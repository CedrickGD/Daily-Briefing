package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Texts of "Themen entdecken": the category grid, a category's detail with its sub-topics and
 * the headline preview, followed places (with the place search) and followed searches.
 * Topic names and descriptions themselves come from [TopicStrings].
 */
interface DiscoverStrings {
    val overline: String
    val title: String

    /** "Du folgst 9 Themen" under the title. */
    fun followingSummary(count: Int): String

    val intro: String

    val categoriesOverline: String
    val categoriesTitle: String

    /** TalkBack text of a category tile. [followed] counts the category and its followed sub-topics. */
    fun tileDescription(title: String, subtitle: String, followed: Int): String

    /** "8 Unterthemen" on a category tile and in its header. */
    fun subtopicCount(count: Int): String

    /** The badge on a tile: how many of its topics are followed. */
    fun followedBadge(count: Int): String

    /** Click label of a tile ("Öffnen"). */
    val openCategory: String

    /** Content description of the back button inside a category. */
    val allCategories: String

    val followCategory: String
    fun followCategoryHint(title: String): String

    /** The follow toggle's label. */
    val follow: String
    val following: String

    /** TalkBack state of the follow toggle. */
    val followedState: String
    val notFollowedState: String

    /** Why the last followed topic cannot be switched off. */
    val lastTopicHint: String

    val subtopicsOverline: String
    val subtopicsTitle: String

    val previewOverline: String

    /** Click label of a sub-topic row: shows its newest headlines in the preview. */
    val showPreview: String
    val previewEmpty: String
    val previewFailed: String

    /** "Alle Meldungen im Feed" hint under the preview once the topic is followed. */
    val previewInFeed: String

    val localOverline: String
    val localTitle: String
    val localEmpty: String
    val addPlace: String
    fun removePlace(name: String): String

    /** Which news a followed place reads: "Nachrichten auf Englisch · Vereinigte Staaten". */
    fun placeNews(language: String, country: String): String

    /** Click label of a place row: shows (or hides) its newest headlines. */
    val togglePlacePreview: String

    val placeSheetOverline: String
    val placeSheetTitle: String
    val placeSearchHint: String
    val clearSearch: String
    val placeSearchFailed: String
    fun placeNothingFound(query: String): String
    val placeSearchStart: String
    fun placeAdded(name: String): String

    val searchesOverline: String
    val searchesTitle: String
    val searchesEmpty: String
    val openSearch: String

    /** Click label of a followed search: opens the search screen with it. */
    val searchAgain: String
    fun removeSearch(title: String): String
    fun searchRemoved(title: String): String

    val customOverline: String
    val customTitle: String
    val customBody: String
    val customAction: String

    /** Shown when a change could not be stored. */
    val saveFailed: String

    companion object {
        fun of(lang: AppLanguage): DiscoverStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : DiscoverStrings {
        override val overline = "Deine Themen"
        override val title = "Themen entdecken"
        override fun followingSummary(count: Int) = if (count == 1) "Du folgst einem Thema" else "Du folgst $count Themen"
        override val intro = "Tippe eine Kategorie an, folge ganzen Bereichen oder nur den Unterthemen, die dich wirklich interessieren."
        override val categoriesOverline = "Kategorien"
        override val categoriesTitle = "Wofür interessierst du dich?"
        override fun tileDescription(title: String, subtitle: String, followed: Int) =
            "$title. $subtitle. " + when (followed) {
                0 -> "Noch nicht gefolgt."
                1 -> "Einem Thema gefolgt."
                else -> "$followed Themen gefolgt."
            }
        override fun subtopicCount(count: Int) = if (count == 1) "1 Unterthema" else "$count Unterthemen"
        override fun followedBadge(count: Int) = "$count"
        override val openCategory = "Öffnen"
        override val allCategories = "Alle Kategorien"
        override val followCategory = "Ganze Kategorie folgen"
        override fun followCategoryHint(title: String) = "Das Wichtigste aus $title in deinem Feed"
        override val follow = "Folgen"
        override val following = "Gefolgt"
        override val followedState = "Gefolgt"
        override val notFollowedState = "Nicht gefolgt"
        override val lastTopicHint = "Ein Thema bleibt immer – dein Feed soll nie leer sein."
        override val subtopicsOverline = "Unterthemen"
        override val subtopicsTitle = "Genauer hinschauen"
        override val previewOverline = "Neueste Meldungen"
        override val showPreview = "Meldungen zeigen"
        override val previewEmpty = "Gerade keine Meldungen zu diesem Thema."
        override val previewFailed = "Die Meldungen ließen sich gerade nicht laden."
        override val previewInFeed = "Mehr davon findest du in deinem Feed."
        override val localOverline = "Lokal"
        override val localTitle = "Nachrichten aus deinen Orten"
        override val localEmpty = "Folge einem Ort, egal wo auf der Welt: Washington, Paris oder dein Heimatort."
        override val addPlace = "Ort hinzufügen"
        override fun removePlace(name: String) = "$name entfernen"
        override fun placeNews(language: String, country: String) = "Nachrichten auf $language · $country"
        override val togglePlacePreview = "Meldungen zeigen oder ausblenden"
        override val placeSheetOverline = "Lokale Nachrichten"
        override val placeSheetTitle = "Welchem Ort willst du folgen?"
        override val placeSearchHint = "Ort suchen, z. B. Washington"
        override val clearSearch = "Suche leeren"
        override val placeSearchFailed = "Suche gerade nicht möglich – bist du online?"
        override fun placeNothingFound(query: String) = "Nichts gefunden für „$query“."
        override val placeSearchStart = "Die Nachrichten kommen aus dem Land des Orts – in dessen Sprache."
        override fun placeAdded(name: String) = "$name ist jetzt in deinem Feed"
        override val searchesOverline = "Deine Suchen"
        override val searchesTitle = "Themen, die du verfolgst"
        override val searchesEmpty = "Suche nach etwas, zum Beispiel „Midterms“, und folge der Suche. Sie erscheint dann hier und in deinem Feed."
        override val openSearch = "Suche öffnen"
        override val searchAgain = "Erneut suchen"
        override fun removeSearch(title: String) = "Suche „$title“ entfernen"
        override fun searchRemoved(title: String) = "„$title“ entfernt"
        override val customOverline = "Eigenes Thema"
        override val customTitle = "Deine Lieblingsquellen"
        override val customBody = "Lege ein Thema mit eigenen Feeds an, etwa dem Blog deines Vereins oder einer Fachseite."
        override val customAction = "In den Einstellungen anlegen"
        override val saveFailed = "Das ließ sich gerade nicht speichern."
    }

    private object En : DiscoverStrings {
        override val overline = "Your topics"
        override val title = "Discover topics"
        override fun followingSummary(count: Int) = if (count == 1) "You follow one topic" else "You follow $count topics"
        override val intro = "Tap a category, then follow all of it or just the sub-topics you really care about."
        override val categoriesOverline = "Categories"
        override val categoriesTitle = "What are you into?"
        override fun tileDescription(title: String, subtitle: String, followed: Int) =
            "$title. $subtitle. " + when (followed) {
                0 -> "Not followed yet."
                1 -> "One topic followed."
                else -> "$followed topics followed."
            }
        override fun subtopicCount(count: Int) = if (count == 1) "1 sub-topic" else "$count sub-topics"
        override fun followedBadge(count: Int) = "$count"
        override val openCategory = "Open"
        override val allCategories = "All categories"
        override val followCategory = "Follow the whole category"
        override fun followCategoryHint(title: String) = "The essentials of $title in your feed"
        override val follow = "Follow"
        override val following = "Following"
        override val followedState = "Following"
        override val notFollowedState = "Not following"
        override val lastTopicHint = "One topic always stays, so your feed is never empty."
        override val subtopicsOverline = "Sub-topics"
        override val subtopicsTitle = "Take a closer look"
        override val previewOverline = "Latest stories"
        override val showPreview = "Show stories"
        override val previewEmpty = "No stories on this topic right now."
        override val previewFailed = "The stories couldn’t be loaded just now."
        override val previewInFeed = "You’ll find more of these in your feed."
        override val localOverline = "Local"
        override val localTitle = "News from your places"
        override val localEmpty = "Follow a place anywhere in the world: Washington, Paris or your home town."
        override val addPlace = "Add a place"
        override fun removePlace(name: String) = "Remove $name"
        override fun placeNews(language: String, country: String) = "News in $language · $country"
        override val togglePlacePreview = "Show or hide stories"
        override val placeSheetOverline = "Local news"
        override val placeSheetTitle = "Which place do you want to follow?"
        override val placeSearchHint = "Search a place, e.g. Washington"
        override val clearSearch = "Clear search"
        override val placeSearchFailed = "Search isn’t possible right now. Are you online?"
        override fun placeNothingFound(query: String) = "Nothing found for “$query”."
        override val placeSearchStart = "The news comes from the place’s own country, in its language."
        override fun placeAdded(name: String) = "$name is now in your feed"
        override val searchesOverline = "Your searches"
        override val searchesTitle = "Topics you keep an eye on"
        override val searchesEmpty = "Search for something, say “Midterms”, and follow the search. It then shows up here and in your feed."
        override val openSearch = "Open search"
        override val searchAgain = "Search again"
        override fun removeSearch(title: String) = "Remove search “$title”"
        override fun searchRemoved(title: String) = "“$title” removed"
        override val customOverline = "Own topic"
        override val customTitle = "Your favourite sources"
        override val customBody = "Create a topic from feeds of your own, like your club’s blog or a specialist site."
        override val customAction = "Create it in Settings"
        override val saveFailed = "That couldn’t be saved just now."
    }

    private object Fr : DiscoverStrings {
        override val overline = "Tes thèmes"
        override val title = "Découvrir des thèmes"
        override fun followingSummary(count: Int) = if (count == 1) "Tu suis un thème" else "Tu suis $count thèmes"
        override val intro = "Touche une catégorie, puis suis-la en entier ou seulement les sous-thèmes qui t’intéressent vraiment."
        override val categoriesOverline = "Catégories"
        override val categoriesTitle = "Qu’est-ce qui t’intéresse ?"
        override fun tileDescription(title: String, subtitle: String, followed: Int) =
            "$title. $subtitle. " + when (followed) {
                0 -> "Pas encore suivi."
                1 -> "Un thème suivi."
                else -> "$followed thèmes suivis."
            }
        override fun subtopicCount(count: Int) = if (count == 1) "1 sous-thème" else "$count sous-thèmes"
        override fun followedBadge(count: Int) = "$count"
        override val openCategory = "Ouvrir"
        override val allCategories = "Toutes les catégories"
        override val followCategory = "Suivre toute la catégorie"
        override fun followCategoryHint(title: String) = "L’essentiel de « $title » dans ton fil"
        override val follow = "Suivre"
        override val following = "Suivi"
        override val followedState = "Suivi"
        override val notFollowedState = "Non suivi"
        override val lastTopicHint = "Un thème reste toujours : ton fil n’est jamais vide."
        override val subtopicsOverline = "Sous-thèmes"
        override val subtopicsTitle = "Regarder de plus près"
        override val previewOverline = "Dernières infos"
        override val showPreview = "Afficher les infos"
        override val previewEmpty = "Aucune info sur ce thème pour le moment."
        override val previewFailed = "Impossible de charger les infos pour le moment."
        override val previewInFeed = "Tu en trouveras davantage dans ton fil."
        override val localOverline = "Local"
        override val localTitle = "L’actu de tes lieux"
        override val localEmpty = "Suis un lieu n’importe où dans le monde : Washington, Paris ou ta ville natale."
        override val addPlace = "Ajouter un lieu"
        override fun removePlace(name: String) = "Retirer $name"
        override fun placeNews(language: String, country: String) = "Infos en $language · $country"
        override val togglePlacePreview = "Afficher ou masquer les infos"
        override val placeSheetOverline = "Actualité locale"
        override val placeSheetTitle = "Quel lieu veux-tu suivre ?"
        override val placeSearchHint = "Chercher un lieu, p. ex. Washington"
        override val clearSearch = "Effacer la recherche"
        override val placeSearchFailed = "Recherche impossible pour le moment. Es-tu en ligne ?"
        override fun placeNothingFound(query: String) = "Aucun résultat pour « $query »."
        override val placeSearchStart = "Les infos viennent du pays du lieu, dans sa langue."
        override fun placeAdded(name: String) = "$name est maintenant dans ton fil"
        override val searchesOverline = "Tes recherches"
        override val searchesTitle = "Les sujets que tu suis"
        override val searchesEmpty = "Cherche quelque chose, par exemple « Midterms », et suis la recherche. Elle apparaîtra ici et dans ton fil."
        override val openSearch = "Ouvrir la recherche"
        override val searchAgain = "Relancer la recherche"
        override fun removeSearch(title: String) = "Retirer la recherche « $title »"
        override fun searchRemoved(title: String) = "« $title » retiré"
        override val customOverline = "Thème perso"
        override val customTitle = "Tes sources préférées"
        override val customBody = "Crée un thème avec tes propres flux, comme le blog de ton club ou un site spécialisé."
        override val customAction = "Le créer dans les réglages"
        override val saveFailed = "Impossible d’enregistrer pour le moment."
    }

    private object Es : DiscoverStrings {
        override val overline = "Tus temas"
        override val title = "Descubrir temas"
        override fun followingSummary(count: Int) = if (count == 1) "Sigues un tema" else "Sigues $count temas"
        override val intro = "Toca una categoría y síguela entera o solo los subtemas que de verdad te interesan."
        override val categoriesOverline = "Categorías"
        override val categoriesTitle = "¿Qué te interesa?"
        override fun tileDescription(title: String, subtitle: String, followed: Int) =
            "$title. $subtitle. " + when (followed) {
                0 -> "Aún no lo sigues."
                1 -> "Sigues un tema."
                else -> "Sigues $followed temas."
            }
        override fun subtopicCount(count: Int) = if (count == 1) "1 subtema" else "$count subtemas"
        override fun followedBadge(count: Int) = "$count"
        override val openCategory = "Abrir"
        override val allCategories = "Todas las categorías"
        override val followCategory = "Seguir toda la categoría"
        override fun followCategoryHint(title: String) = "Lo esencial de $title en tu feed"
        override val follow = "Seguir"
        override val following = "Siguiendo"
        override val followedState = "Siguiendo"
        override val notFollowedState = "Sin seguir"
        override val lastTopicHint = "Siempre queda un tema para que tu feed nunca esté vacío."
        override val subtopicsOverline = "Subtemas"
        override val subtopicsTitle = "Mirar más de cerca"
        override val previewOverline = "Últimas noticias"
        override val showPreview = "Mostrar noticias"
        override val previewEmpty = "Ahora mismo no hay noticias de este tema."
        override val previewFailed = "No se pudieron cargar las noticias."
        override val previewInFeed = "Encontrarás más en tu feed."
        override val localOverline = "Local"
        override val localTitle = "Noticias de tus lugares"
        override val localEmpty = "Sigue un lugar en cualquier parte del mundo: Washington, París o tu pueblo."
        override val addPlace = "Añadir lugar"
        override fun removePlace(name: String) = "Quitar $name"
        override fun placeNews(language: String, country: String) = "Noticias en $language · $country"
        override val togglePlacePreview = "Mostrar u ocultar noticias"
        override val placeSheetOverline = "Noticias locales"
        override val placeSheetTitle = "¿Qué lugar quieres seguir?"
        override val placeSearchHint = "Buscar un lugar, p. ej. Washington"
        override val clearSearch = "Borrar búsqueda"
        override val placeSearchFailed = "Ahora no se puede buscar. ¿Tienes conexión?"
        override fun placeNothingFound(query: String) = "No hay resultados para «$query»."
        override val placeSearchStart = "Las noticias vienen del país del lugar, en su idioma."
        override fun placeAdded(name: String) = "$name ya está en tu feed"
        override val searchesOverline = "Tus búsquedas"
        override val searchesTitle = "Temas que sigues de cerca"
        override val searchesEmpty = "Busca algo, por ejemplo «Midterms», y sigue la búsqueda. Aparecerá aquí y en tu feed."
        override val openSearch = "Abrir búsqueda"
        override val searchAgain = "Buscar de nuevo"
        override fun removeSearch(title: String) = "Quitar la búsqueda «$title»"
        override fun searchRemoved(title: String) = "«$title» eliminado"
        override val customOverline = "Tema propio"
        override val customTitle = "Tus fuentes favoritas"
        override val customBody = "Crea un tema con tus propios feeds, como el blog de tu club o una web especializada."
        override val customAction = "Crearlo en Ajustes"
        override val saveFailed = "No se pudo guardar ahora mismo."
    }

    private object It : DiscoverStrings {
        override val overline = "I tuoi temi"
        override val title = "Scopri i temi"
        override fun followingSummary(count: Int) = if (count == 1) "Segui un tema" else "Segui $count temi"
        override val intro = "Tocca una categoria e seguila tutta oppure solo i sottotemi che ti interessano davvero."
        override val categoriesOverline = "Categorie"
        override val categoriesTitle = "Che cosa ti interessa?"
        override fun tileDescription(title: String, subtitle: String, followed: Int) =
            "$title. $subtitle. " + when (followed) {
                0 -> "Non ancora seguito."
                1 -> "Un tema seguito."
                else -> "$followed temi seguiti."
            }
        override fun subtopicCount(count: Int) = if (count == 1) "1 sottotema" else "$count sottotemi"
        override fun followedBadge(count: Int) = "$count"
        override val openCategory = "Apri"
        override val allCategories = "Tutte le categorie"
        override val followCategory = "Segui l’intera categoria"
        override fun followCategoryHint(title: String) = "L’essenziale di $title nel tuo feed"
        override val follow = "Segui"
        override val following = "Seguito"
        override val followedState = "Seguito"
        override val notFollowedState = "Non seguito"
        override val lastTopicHint = "Un tema resta sempre, così il tuo feed non è mai vuoto."
        override val subtopicsOverline = "Sottotemi"
        override val subtopicsTitle = "Guardare più da vicino"
        override val previewOverline = "Ultime notizie"
        override val showPreview = "Mostra notizie"
        override val previewEmpty = "Al momento nessuna notizia su questo tema."
        override val previewFailed = "Non è stato possibile caricare le notizie."
        override val previewInFeed = "Ne trovi altre nel tuo feed."
        override val localOverline = "Locale"
        override val localTitle = "Notizie dai tuoi luoghi"
        override val localEmpty = "Segui un luogo ovunque nel mondo: Washington, Parigi o il tuo paese."
        override val addPlace = "Aggiungi luogo"
        override fun removePlace(name: String) = "Rimuovi $name"
        override fun placeNews(language: String, country: String) = "Notizie in $language · $country"
        override val togglePlacePreview = "Mostra o nascondi le notizie"
        override val placeSheetOverline = "Notizie locali"
        override val placeSheetTitle = "Quale luogo vuoi seguire?"
        override val placeSearchHint = "Cerca un luogo, ad es. Washington"
        override val clearSearch = "Cancella ricerca"
        override val placeSearchFailed = "Ricerca non possibile al momento. Sei online?"
        override fun placeNothingFound(query: String) = "Nessun risultato per «$query»."
        override val placeSearchStart = "Le notizie arrivano dal paese del luogo, nella sua lingua."
        override fun placeAdded(name: String) = "$name ora è nel tuo feed"
        override val searchesOverline = "Le tue ricerche"
        override val searchesTitle = "Temi che tieni d’occhio"
        override val searchesEmpty = "Cerca qualcosa, per esempio «Midterms», e segui la ricerca. Comparirà qui e nel tuo feed."
        override val openSearch = "Apri la ricerca"
        override val searchAgain = "Cerca di nuovo"
        override fun removeSearch(title: String) = "Rimuovi la ricerca «$title»"
        override fun searchRemoved(title: String) = "«$title» rimosso"
        override val customOverline = "Tema personale"
        override val customTitle = "Le tue fonti preferite"
        override val customBody = "Crea un tema con i tuoi feed, come il blog della tua squadra o un sito specializzato."
        override val customAction = "Crealo nelle impostazioni"
        override val saveFailed = "Non è stato possibile salvare."
    }
}

@Composable
@ReadOnlyComposable
fun discoverStrings(): DiscoverStrings = DiscoverStrings.of(LocalAppLanguage.current)

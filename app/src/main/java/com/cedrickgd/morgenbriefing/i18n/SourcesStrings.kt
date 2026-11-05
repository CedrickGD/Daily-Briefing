package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.cedrickgd.morgenbriefing.data.news.NewsSection
import com.cedrickgd.morgenbriefing.data.remote.FeedError

/**
 * "Deine Themen" (the followed list, own topics) and "Quellen" (feeds per topic, own feeds,
 * Google News, why a source fails) in the settings.
 */
interface SourcesStrings {
    // Followed topics
    fun followingCount(count: Int): String
    val followedHint: String
    val discover: String
    val discoverHint: String
    val customTopic: String
    fun removeTopic(title: String): String
    fun moveUp(title: String): String
    fun moveDown(title: String): String
    val keepOne: String
    val paused: String
    val pausedHint: String
    val follow: String

    // Own topic dialog
    val newTopicTitle: String
    val editTopicTitle: String
    val create: String
    val topicName: String
    val topicNamePlaceholder: String
    val topicIcon: String
    fun iconDescription(index: Int, count: Int): String
    val keepEmoji: String
    val topicColor: String
    fun colorDescription(index: Int, count: Int): String
    val topicContent: String
    val contentFeeds: String
    val contentSearch: String
    val contentFeedsHint: String
    val contentSearchHint: String
    val query: String
    val queryPlaceholder: String
    fun deleteTitle(title: String): String
    val deleteText: String

    // Sources
    fun sourcesFor(edition: String): String
    fun sourceCount(count: Int): String
    fun sourcesOn(on: Int, total: Int): String
    val noSourceYet: String
    val notFollowedSuffix: String
    fun hiddenUnreadable(count: Int): String
    val expanded: String
    val collapsed: String
    val addFeed: String
    val allOn: String
    val edit: String
    val ownSource: String
    val removeSource: String
    val noSourceInTopic: String
    fun articles(count: Int): String
    val googleTopStories: String
    fun googleSection(section: String): String
    fun googleSearch(query: String): String
    fun googlePlace(place: String): String
    fun section(section: NewsSection): String
    /** Why a source failed (also the add-feed dialog); [httpCode] when the server answered with one. */
    fun feedError(error: FeedError, httpCode: Int?): String

    // Add a feed
    fun addFeedTitle(topic: String): String
    val addFeedIntro: String
    val address: String
    val addressPlaceholder: String
    val findFeed: String
    val searching: String
    val lookingUp: String
    fun found(title: String, count: Int): String
    val sourceName: String
    val add: String

    companion object {
        fun of(lang: AppLanguage): SourcesStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : SourcesStrings {
        override fun followingCount(count: Int) = if (count == 1) "Du folgst 1 Thema" else "Du folgst $count Themen"
        override val followedHint = "Am Griff ziehen sortiert · lange drücken geht auch"
        override val discover = "Themen entdecken"
        override val discoverHint = "Kategorien, Unterthemen, Orte und Suchen"
        override val customTopic = "Eigenes Thema"
        override fun removeTopic(title: String) = "$title entfernen"
        override fun moveUp(title: String) = "$title nach oben"
        override fun moveDown(title: String) = "$title nach unten"
        override val keepOne = "Ein Thema bleibt immer in deinem Feed."
        override val paused = "Pausiert"
        override val pausedHint = "Deine eigenen Themen und Orte, die gerade nicht im Feed sind."
        override val follow = "Folgen"

        override val newTopicTitle = "Eigenes Thema"
        override val editTopicTitle = "Thema bearbeiten"
        override val create = "Anlegen"
        override val topicName = "Name"
        override val topicNamePlaceholder = "z. B. Formel 1, Leipzig, Rente"
        override val topicIcon = "Symbol"
        override fun iconDescription(index: Int, count: Int) = "Symbol $index von $count"
        override val keepEmoji = "Bisheriges Emoji"
        override val topicColor = "Farbe"
        override fun colorDescription(index: Int, count: Int) = "Farbe $index von $count"
        override val topicContent = "Woher die Meldungen kommen"
        override val contentFeeds = "Feeds"
        override val contentSearch = "Suche"
        override val contentFeedsHint = "Du fügst danach die Feeds deiner Lieblingsseiten hinzu."
        override val contentSearchHint = "Die App sucht laufend in Google News danach – in deiner Nachrichten-Ausgabe."
        override val query = "Suchbegriff"
        override val queryPlaceholder = "z. B. Midterms, Tesla, Bundesliga"
        override fun deleteTitle(title: String) = "„$title“ löschen?"
        override val deleteText = "Das Thema und seine Quellen werden entfernt. Die eingebauten Themen bleiben."

        override fun sourcesFor(edition: String) = "Für die Ausgabe $edition"
        override fun sourceCount(count: Int) = if (count == 1) "1 Quelle" else "$count Quellen"
        override fun sourcesOn(on: Int, total: Int) = "$on von $total Quellen an"
        override val noSourceYet = "Noch keine Quelle – füge eine hinzu"
        override val notFollowedSuffix = "nicht im Feed"
        override fun hiddenUnreadable(count: Int) =
            if (count == 1) "1 Quelle ausgeblendet – nur bei der Redaktion lesbar" else "$count Quellen ausgeblendet – nur bei der Redaktion lesbar"
        override val expanded = "ausgeklappt"
        override val collapsed = "eingeklappt"
        override val addFeed = "Eigene Quelle"
        override val allOn = "Alle an"
        override val edit = "Bearbeiten"
        override val ownSource = "eigene Quelle"
        override val removeSource = "Quelle entfernen"
        override val noSourceInTopic = "Dieses Thema hat noch keine Quelle."
        override fun articles(count: Int) = if (count == 1) "1 Meldung" else "$count Meldungen"
        override val googleTopStories = "Google News · Schlagzeilen"
        override fun googleSection(section: String) = "Google News · Rubrik $section"
        override fun googleSearch(query: String) = "Google News · Suche: $query"
        override fun googlePlace(place: String) = "Google News · Lokales aus $place"
        override fun section(section: NewsSection) = when (section) {
            NewsSection.WORLD -> "Welt"
            NewsSection.NATION -> "Inland"
            NewsSection.BUSINESS -> "Wirtschaft"
            NewsSection.TECHNOLOGY -> "Technik"
            NewsSection.ENTERTAINMENT -> "Unterhaltung"
            NewsSection.SPORTS -> "Sport"
            NewsSection.SCIENCE -> "Wissenschaft"
            NewsSection.HEALTH -> "Gesundheit"
        }
        override fun feedError(error: FeedError, httpCode: Int?) = when (error) {
            FeedError.NOT_A_URL -> "Das ist keine Web-Adresse"
            FeedError.INVALID_URL -> "Ungültige Adresse"
            FeedError.UNREACHABLE -> "Nicht erreichbar"
            FeedError.BLOCKED -> "Die Seite blockt den Zugriff" + code(httpCode)
            FeedError.NOT_FOUND -> "Feed nicht gefunden" + code(httpCode)
            FeedError.RATE_LIMITED -> "Zu viele Anfragen – später klappt's wieder"
            FeedError.SERVER_ERROR -> "Server antwortet nicht" + code(httpCode)
            FeedError.HTTP_ERROR -> "Fehler vom Server" + code(httpCode)
            FeedError.NOT_A_FEED -> "Das ist kein RSS- oder Atom-Feed"
            FeedError.EMPTY -> "Der Feed enthält keine Meldungen"
            FeedError.NO_FEED_FOUND -> "Hier ist kein Feed zu finden"
            FeedError.UNREADABLE -> "Fehler beim Lesen"
        }

        override fun addFeedTitle(topic: String) = "Quelle für „$topic“"
        override val addFeedIntro = "Die Adresse eines RSS- oder Atom-Feeds – oder einfach die Website der Redaktion, dann sucht die App den Feed selbst."
        override val address = "Adresse"
        override val addressPlaceholder = "z. B. tagesschau.de oder …/feed.xml"
        override val findFeed = "Feed suchen"
        override val searching = "Suche …"
        override val lookingUp = "Suche den Feed …"
        override fun found(title: String, count: Int) = "Gefunden: $title · ${articles(count)}"
        override val sourceName = "Name der Quelle"
        override val add = "Hinzufügen"
    }

    private object En : SourcesStrings {
        override fun followingCount(count: Int) = if (count == 1) "You follow 1 topic" else "You follow $count topics"
        override val followedHint = "Drag the handle to sort · a long press works too"
        override val discover = "Discover topics"
        override val discoverHint = "Categories, sub-topics, places and searches"
        override val customTopic = "Own topic"
        override fun removeTopic(title: String) = "Remove $title"
        override fun moveUp(title: String) = "Move $title up"
        override fun moveDown(title: String) = "Move $title down"
        override val keepOne = "One topic always stays in your feed."
        override val paused = "Paused"
        override val pausedHint = "Your own topics and places that aren't in the feed right now."
        override val follow = "Follow"

        override val newTopicTitle = "Own topic"
        override val editTopicTitle = "Edit topic"
        override val create = "Create"
        override val topicName = "Name"
        override val topicNamePlaceholder = "e.g. Formula 1, Boston, Pensions"
        override val topicIcon = "Icon"
        override fun iconDescription(index: Int, count: Int) = "Icon $index of $count"
        override val keepEmoji = "Previous emoji"
        override val topicColor = "Color"
        override fun colorDescription(index: Int, count: Int) = "Color $index of $count"
        override val topicContent = "Where the stories come from"
        override val contentFeeds = "Feeds"
        override val contentSearch = "Search"
        override val contentFeedsHint = "You'll add the feeds of your favorite sites next."
        override val contentSearchHint = "The app keeps searching Google News for it – in your news edition."
        override val query = "Search term"
        override val queryPlaceholder = "e.g. Midterms, Tesla, Premier League"
        override fun deleteTitle(title: String) = "Delete “$title”?"
        override val deleteText = "The topic and its sources are removed. The built-in topics stay."

        override fun sourcesFor(edition: String) = "For the $edition edition"
        override fun sourceCount(count: Int) = if (count == 1) "1 source" else "$count sources"
        override fun sourcesOn(on: Int, total: Int) = "$on of $total sources on"
        override val noSourceYet = "No source yet – add one"
        override val notFollowedSuffix = "not in the feed"
        override fun hiddenUnreadable(count: Int) =
            if (count == 1) "1 source hidden – only readable on the publisher's site" else "$count sources hidden – only readable on the publisher's site"
        override val expanded = "expanded"
        override val collapsed = "collapsed"
        override val addFeed = "Own source"
        override val allOn = "All on"
        override val edit = "Edit"
        override val ownSource = "own source"
        override val removeSource = "Remove source"
        override val noSourceInTopic = "This topic has no source yet."
        override fun articles(count: Int) = if (count == 1) "1 story" else "$count stories"
        override val googleTopStories = "Google News · Top stories"
        override fun googleSection(section: String) = "Google News · $section section"
        override fun googleSearch(query: String) = "Google News · Search: $query"
        override fun googlePlace(place: String) = "Google News · Local news from $place"
        override fun section(section: NewsSection) = when (section) {
            NewsSection.WORLD -> "World"
            NewsSection.NATION -> "National"
            NewsSection.BUSINESS -> "Business"
            NewsSection.TECHNOLOGY -> "Technology"
            NewsSection.ENTERTAINMENT -> "Entertainment"
            NewsSection.SPORTS -> "Sports"
            NewsSection.SCIENCE -> "Science"
            NewsSection.HEALTH -> "Health"
        }
        override fun feedError(error: FeedError, httpCode: Int?) = when (error) {
            FeedError.NOT_A_URL -> "That's not a web address"
            FeedError.INVALID_URL -> "Invalid address"
            FeedError.UNREACHABLE -> "Can't be reached"
            FeedError.BLOCKED -> "The site blocks access" + code(httpCode)
            FeedError.NOT_FOUND -> "Feed not found" + code(httpCode)
            FeedError.RATE_LIMITED -> "Too many requests – it'll work again later"
            FeedError.SERVER_ERROR -> "Server not responding" + code(httpCode)
            FeedError.HTTP_ERROR -> "Server error" + code(httpCode)
            FeedError.NOT_A_FEED -> "That's not an RSS or Atom feed"
            FeedError.EMPTY -> "The feed has no stories"
            FeedError.NO_FEED_FOUND -> "No feed to be found here"
            FeedError.UNREADABLE -> "Couldn't be read"
        }

        override fun addFeedTitle(topic: String) = "Source for “$topic”"
        override val addFeedIntro = "The address of an RSS or Atom feed – or simply the newsroom's website, and the app finds the feed itself."
        override val address = "Address"
        override val addressPlaceholder = "e.g. npr.org or …/feed.xml"
        override val findFeed = "Find feed"
        override val searching = "Searching …"
        override val lookingUp = "Looking for the feed …"
        override fun found(title: String, count: Int) = "Found: $title · ${articles(count)}"
        override val sourceName = "Source name"
        override val add = "Add"
    }

    private object Fr : SourcesStrings {
        override fun followingCount(count: Int) = if (count == 1) "Tu suis 1 thème" else "Tu suis $count thèmes"
        override val followedHint = "Fais glisser la poignée pour trier · un appui long marche aussi"
        override val discover = "Découvrir des thèmes"
        override val discoverHint = "Catégories, sous-thèmes, lieux et recherches"
        override val customTopic = "Thème perso"
        override fun removeTopic(title: String) = "Retirer $title"
        override fun moveUp(title: String) = "Monter $title"
        override fun moveDown(title: String) = "Descendre $title"
        override val keepOne = "Un thème reste toujours dans ton fil."
        override val paused = "En pause"
        override val pausedHint = "Tes thèmes et lieux à toi qui ne sont pas dans le fil pour l'instant."
        override val follow = "Suivre"

        override val newTopicTitle = "Thème perso"
        override val editTopicTitle = "Modifier le thème"
        override val create = "Créer"
        override val topicName = "Nom"
        override val topicNamePlaceholder = "p. ex. Formule 1, Lyon, Retraites"
        override val topicIcon = "Icône"
        override fun iconDescription(index: Int, count: Int) = "Icône $index sur $count"
        override val keepEmoji = "Emoji actuel"
        override val topicColor = "Couleur"
        override fun colorDescription(index: Int, count: Int) = "Couleur $index sur $count"
        override val topicContent = "D'où viennent les articles"
        override val contentFeeds = "Flux"
        override val contentSearch = "Recherche"
        override val contentFeedsHint = "Tu ajoutes ensuite les flux de tes sites préférés."
        override val contentSearchHint = "L'appli le cherche en continu dans Google News – dans ton édition."
        override val query = "Terme de recherche"
        override val queryPlaceholder = "p. ex. Midterms, Tesla, Ligue 1"
        override fun deleteTitle(title: String) = "Supprimer « $title » ?"
        override val deleteText = "Le thème et ses sources sont supprimés. Les thèmes intégrés restent."

        override fun sourcesFor(edition: String) = "Pour l'édition $edition"
        override fun sourceCount(count: Int) = if (count == 1) "1 source" else "$count sources"
        override fun sourcesOn(on: Int, total: Int) = "$on sources actives sur $total"
        override val noSourceYet = "Pas encore de source – ajoutes-en une"
        override val notFollowedSuffix = "hors du fil"
        override fun hiddenUnreadable(count: Int) =
            if (count == 1) "1 source masquée – lisible seulement chez l'éditeur" else "$count sources masquées – lisibles seulement chez l'éditeur"
        override val expanded = "déplié"
        override val collapsed = "replié"
        override val addFeed = "Source perso"
        override val allOn = "Tout activer"
        override val edit = "Modifier"
        override val ownSource = "source perso"
        override val removeSource = "Supprimer la source"
        override val noSourceInTopic = "Ce thème n'a pas encore de source."
        override fun articles(count: Int) = if (count == 1) "1 article" else "$count articles"
        override val googleTopStories = "Google News · À la une"
        override fun googleSection(section: String) = "Google News · rubrique $section"
        override fun googleSearch(query: String) = "Google News · Recherche : $query"
        override fun googlePlace(place: String) = "Google News · Actualité locale : $place"
        override fun section(section: NewsSection) = when (section) {
            NewsSection.WORLD -> "Monde"
            NewsSection.NATION -> "National"
            NewsSection.BUSINESS -> "Économie"
            NewsSection.TECHNOLOGY -> "Technologie"
            NewsSection.ENTERTAINMENT -> "Divertissement"
            NewsSection.SPORTS -> "Sports"
            NewsSection.SCIENCE -> "Sciences"
            NewsSection.HEALTH -> "Santé"
        }
        override fun feedError(error: FeedError, httpCode: Int?) = when (error) {
            FeedError.NOT_A_URL -> "Ce n'est pas une adresse web"
            FeedError.INVALID_URL -> "Adresse invalide"
            FeedError.UNREACHABLE -> "Injoignable"
            FeedError.BLOCKED -> "Le site bloque l'accès" + code(httpCode)
            FeedError.NOT_FOUND -> "Flux introuvable" + code(httpCode)
            FeedError.RATE_LIMITED -> "Trop de requêtes – ça remarchera plus tard"
            FeedError.SERVER_ERROR -> "Le serveur ne répond pas" + code(httpCode)
            FeedError.HTTP_ERROR -> "Erreur du serveur" + code(httpCode)
            FeedError.NOT_A_FEED -> "Ce n'est pas un flux RSS ou Atom"
            FeedError.EMPTY -> "Le flux ne contient aucun article"
            FeedError.NO_FEED_FOUND -> "Aucun flux trouvé ici"
            FeedError.UNREADABLE -> "Erreur de lecture"
        }

        override fun addFeedTitle(topic: String) = "Source pour « $topic »"
        override val addFeedIntro = "L'adresse d'un flux RSS ou Atom – ou simplement le site de la rédaction, l'appli trouve alors le flux elle-même."
        override val address = "Adresse"
        override val addressPlaceholder = "p. ex. franceinfo.fr ou …/feed.xml"
        override val findFeed = "Chercher le flux"
        override val searching = "Recherche …"
        override val lookingUp = "Je cherche le flux …"
        override fun found(title: String, count: Int) = "Trouvé : $title · ${articles(count)}"
        override val sourceName = "Nom de la source"
        override val add = "Ajouter"
    }

    private object Es : SourcesStrings {
        override fun followingCount(count: Int) = if (count == 1) "Sigues 1 tema" else "Sigues $count temas"
        override val followedHint = "Arrastra el asa para ordenar · también con una pulsación larga"
        override val discover = "Descubrir temas"
        override val discoverHint = "Categorías, subtemas, lugares y búsquedas"
        override val customTopic = "Tema propio"
        override fun removeTopic(title: String) = "Quitar $title"
        override fun moveUp(title: String) = "Subir $title"
        override fun moveDown(title: String) = "Bajar $title"
        override val keepOne = "Siempre queda un tema en tu feed."
        override val paused = "En pausa"
        override val pausedHint = "Tus temas y lugares propios que ahora no están en el feed."
        override val follow = "Seguir"

        override val newTopicTitle = "Tema propio"
        override val editTopicTitle = "Editar tema"
        override val create = "Crear"
        override val topicName = "Nombre"
        override val topicNamePlaceholder = "p. ej. Fórmula 1, Sevilla, Pensiones"
        override val topicIcon = "Icono"
        override fun iconDescription(index: Int, count: Int) = "Icono $index de $count"
        override val keepEmoji = "Emoji actual"
        override val topicColor = "Color"
        override fun colorDescription(index: Int, count: Int) = "Color $index de $count"
        override val topicContent = "De dónde vienen las noticias"
        override val contentFeeds = "Feeds"
        override val contentSearch = "Búsqueda"
        override val contentFeedsHint = "Después añades los feeds de tus webs favoritas."
        override val contentSearchHint = "La app lo busca sin parar en Google News – en tu edición."
        override val query = "Término de búsqueda"
        override val queryPlaceholder = "p. ej. Midterms, Tesla, LaLiga"
        override fun deleteTitle(title: String) = "¿Eliminar «$title»?"
        override val deleteText = "Se eliminan el tema y sus fuentes. Los temas integrados se quedan."

        override fun sourcesFor(edition: String) = "Para la edición $edition"
        override fun sourceCount(count: Int) = if (count == 1) "1 fuente" else "$count fuentes"
        override fun sourcesOn(on: Int, total: Int) = "$on de $total fuentes activas"
        override val noSourceYet = "Aún no hay fuentes – añade una"
        override val notFollowedSuffix = "fuera del feed"
        override fun hiddenUnreadable(count: Int) =
            if (count == 1) "1 fuente oculta – solo se lee en la web del medio" else "$count fuentes ocultas – solo se leen en la web del medio"
        override val expanded = "desplegado"
        override val collapsed = "plegado"
        override val addFeed = "Fuente propia"
        override val allOn = "Activar todas"
        override val edit = "Editar"
        override val ownSource = "fuente propia"
        override val removeSource = "Quitar fuente"
        override val noSourceInTopic = "Este tema aún no tiene fuentes."
        override fun articles(count: Int) = if (count == 1) "1 noticia" else "$count noticias"
        override val googleTopStories = "Google News · Titulares"
        override fun googleSection(section: String) = "Google News · sección $section"
        override fun googleSearch(query: String) = "Google News · Búsqueda: $query"
        override fun googlePlace(place: String) = "Google News · Noticias locales de $place"
        override fun section(section: NewsSection) = when (section) {
            NewsSection.WORLD -> "Internacional"
            NewsSection.NATION -> "Nacional"
            NewsSection.BUSINESS -> "Economía"
            NewsSection.TECHNOLOGY -> "Tecnología"
            NewsSection.ENTERTAINMENT -> "Entretenimiento"
            NewsSection.SPORTS -> "Deportes"
            NewsSection.SCIENCE -> "Ciencia"
            NewsSection.HEALTH -> "Salud"
        }
        override fun feedError(error: FeedError, httpCode: Int?) = when (error) {
            FeedError.NOT_A_URL -> "Eso no es una dirección web"
            FeedError.INVALID_URL -> "Dirección no válida"
            FeedError.UNREACHABLE -> "No responde"
            FeedError.BLOCKED -> "La web bloquea el acceso" + code(httpCode)
            FeedError.NOT_FOUND -> "Feed no encontrado" + code(httpCode)
            FeedError.RATE_LIMITED -> "Demasiadas solicitudes – más tarde volverá a funcionar"
            FeedError.SERVER_ERROR -> "El servidor no responde" + code(httpCode)
            FeedError.HTTP_ERROR -> "Error del servidor" + code(httpCode)
            FeedError.NOT_A_FEED -> "Eso no es un feed RSS ni Atom"
            FeedError.EMPTY -> "El feed no tiene noticias"
            FeedError.NO_FEED_FOUND -> "Aquí no hay ningún feed"
            FeedError.UNREADABLE -> "Error al leer"
        }

        override fun addFeedTitle(topic: String) = "Fuente para «$topic»"
        override val addFeedIntro = "La dirección de un feed RSS o Atom – o simplemente la web de la redacción, y la app busca el feed por sí sola."
        override val address = "Dirección"
        override val addressPlaceholder = "p. ej. elpais.com o …/feed.xml"
        override val findFeed = "Buscar feed"
        override val searching = "Buscando …"
        override val lookingUp = "Buscando el feed …"
        override fun found(title: String, count: Int) = "Encontrado: $title · ${articles(count)}"
        override val sourceName = "Nombre de la fuente"
        override val add = "Añadir"
    }

    private object It : SourcesStrings {
        override fun followingCount(count: Int) = if (count == 1) "Segui 1 tema" else "Segui $count temi"
        override val followedHint = "Trascina la maniglia per ordinare · va bene anche una pressione lunga"
        override val discover = "Scopri temi"
        override val discoverHint = "Categorie, sottotemi, luoghi e ricerche"
        override val customTopic = "Tema personale"
        override fun removeTopic(title: String) = "Rimuovi $title"
        override fun moveUp(title: String) = "Sposta $title in su"
        override fun moveDown(title: String) = "Sposta $title in giù"
        override val keepOne = "Un tema resta sempre nel tuo feed."
        override val paused = "In pausa"
        override val pausedHint = "I tuoi temi e luoghi personali che ora non sono nel feed."
        override val follow = "Segui"

        override val newTopicTitle = "Tema personale"
        override val editTopicTitle = "Modifica tema"
        override val create = "Crea"
        override val topicName = "Nome"
        override val topicNamePlaceholder = "ad es. Formula 1, Bologna, Pensioni"
        override val topicIcon = "Icona"
        override fun iconDescription(index: Int, count: Int) = "Icona $index di $count"
        override val keepEmoji = "Emoji attuale"
        override val topicColor = "Colore"
        override fun colorDescription(index: Int, count: Int) = "Colore $index di $count"
        override val topicContent = "Da dove arrivano le notizie"
        override val contentFeeds = "Feed"
        override val contentSearch = "Ricerca"
        override val contentFeedsHint = "Poi aggiungi i feed dei tuoi siti preferiti."
        override val contentSearchHint = "L'app lo cerca di continuo su Google News – nella tua edizione."
        override val query = "Termine di ricerca"
        override val queryPlaceholder = "ad es. Midterms, Tesla, Serie A"
        override fun deleteTitle(title: String) = "Eliminare «$title»?"
        override val deleteText = "Il tema e le sue fonti vengono rimossi. I temi integrati restano."

        override fun sourcesFor(edition: String) = "Per l'edizione $edition"
        override fun sourceCount(count: Int) = if (count == 1) "1 fonte" else "$count fonti"
        override fun sourcesOn(on: Int, total: Int) = "$on fonti attive su $total"
        override val noSourceYet = "Ancora nessuna fonte – aggiungine una"
        override val notFollowedSuffix = "fuori dal feed"
        override fun hiddenUnreadable(count: Int) =
            if (count == 1) "1 fonte nascosta – leggibile solo sul sito della testata" else "$count fonti nascoste – leggibili solo sul sito della testata"
        override val expanded = "espanso"
        override val collapsed = "compresso"
        override val addFeed = "Fonte personale"
        override val allOn = "Attiva tutte"
        override val edit = "Modifica"
        override val ownSource = "fonte personale"
        override val removeSource = "Rimuovi fonte"
        override val noSourceInTopic = "Questo tema non ha ancora fonti."
        override fun articles(count: Int) = if (count == 1) "1 notizia" else "$count notizie"
        override val googleTopStories = "Google News · Notizie principali"
        override fun googleSection(section: String) = "Google News · sezione $section"
        override fun googleSearch(query: String) = "Google News · Ricerca: $query"
        override fun googlePlace(place: String) = "Google News · Notizie locali da $place"
        override fun section(section: NewsSection) = when (section) {
            NewsSection.WORLD -> "Mondo"
            NewsSection.NATION -> "Italia e interni"
            NewsSection.BUSINESS -> "Economia"
            NewsSection.TECHNOLOGY -> "Tecnologia"
            NewsSection.ENTERTAINMENT -> "Spettacolo"
            NewsSection.SPORTS -> "Sport"
            NewsSection.SCIENCE -> "Scienza"
            NewsSection.HEALTH -> "Salute"
        }
        override fun feedError(error: FeedError, httpCode: Int?) = when (error) {
            FeedError.NOT_A_URL -> "Questo non è un indirizzo web"
            FeedError.INVALID_URL -> "Indirizzo non valido"
            FeedError.UNREACHABLE -> "Non raggiungibile"
            FeedError.BLOCKED -> "Il sito blocca l'accesso" + code(httpCode)
            FeedError.NOT_FOUND -> "Feed non trovato" + code(httpCode)
            FeedError.RATE_LIMITED -> "Troppe richieste – più tardi tornerà a funzionare"
            FeedError.SERVER_ERROR -> "Il server non risponde" + code(httpCode)
            FeedError.HTTP_ERROR -> "Errore del server" + code(httpCode)
            FeedError.NOT_A_FEED -> "Questo non è un feed RSS o Atom"
            FeedError.EMPTY -> "Il feed non contiene notizie"
            FeedError.NO_FEED_FOUND -> "Qui non c'è nessun feed"
            FeedError.UNREADABLE -> "Errore di lettura"
        }

        override fun addFeedTitle(topic: String) = "Fonte per «$topic»"
        override val addFeedIntro = "L'indirizzo di un feed RSS o Atom – o semplicemente il sito della redazione, e l'app trova il feed da sola."
        override val address = "Indirizzo"
        override val addressPlaceholder = "ad es. ansa.it o …/feed.xml"
        override val findFeed = "Cerca feed"
        override val searching = "Cerco …"
        override val lookingUp = "Cerco il feed …"
        override fun found(title: String, count: Int) = "Trovato: $title · ${articles(count)}"
        override val sourceName = "Nome della fonte"
        override val add = "Aggiungi"
    }
}

/** " (404)" after a reason, or nothing. */
private fun code(httpCode: Int?): String = if (httpCode != null) " ($httpCode)" else ""

@Composable
@ReadOnlyComposable
fun sourcesStrings(): SourcesStrings = SourcesStrings.of(LocalAppLanguage.current)

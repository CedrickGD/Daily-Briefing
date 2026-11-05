package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Texts of the news feed ("Heute": chips, sections, entries, the ⋮ menu, the topic sheet, the
 * hints above the feed and the endless lists). Pattern: [CommonStrings].
 */
interface FeedStrings {
    // Chip row
    val forYou: String
    val latest: String
    val filterRow: String
    val chooseTopics: String

    // Header
    fun barTitle(date: String): String
    val search: String
    val searchDescription: String
    val discover: String
    val discoverDescription: String

    // Counts and labels
    fun articleCount(count: Int): String
    fun allArticles(count: Int, topic: String): String
    fun openTopic(topic: String): String
    fun stand(clock: String): String

    // Views
    val latestOverline: String
    val latestTitle: String
    fun subTopicsOf(topic: String): String

    // Loading, empty and end states
    val loadingNews: String
    val loadingArticles: String
    val moreComing: String
    val loadingOlder: String
    val nothingOlderFound: String
    val searchFurtherBack: String
    fun listEnd(count: Int): String
    val noArticlesYet: String
    fun unreachable(sources: String): String
    val nothingLoadedYet: String
    val nothingNewToday: String
    val nothingHereRightNow: String
    val backToTop: String

    // Snackbars
    val lastTopicStays: String
    fun unfollowed(topic: String): String
    fun followed(topic: String): String
    val undo: String
    fun sourceHidden(source: String): String
    val listenUnavailable: String
    val listenFailed: String
    val copyFailed: String
    val linkCopied: String
    val clipLabel: String

    // Entry
    val readMore: String
    val openArticle: String
    val moreOptions: String
    val listen: String
    val stop: String
    val resume: String
    fun listenTo(headline: String): String
    fun resumeListening(headline: String): String
    val stopListening: String

    // ⋮ menu
    val readArticle: String
    fun hideSource(source: String): String
    fun followTopic(topic: String): String
    fun unfollowTopic(topic: String): String

    // Readability hints on a card
    val fullText: String
    val fullTextDescription: String
    val paywall: String
    val paywallDescription: String
    val browserOnly: String
    val browserOnlyDescription: String

    // Topic sheet
    val sheetOverline: String
    val sheetTitle: String
    val sheetHint: String
    val sourcesAndOwnTopics: String
    val discoverMoreTopics: String
    val oneTopicStays: String
    val noArticlesRightNow: String
    fun moveUp(topic: String): String
    fun moveDown(topic: String): String

    // Discover call-to-action and suggestions
    val ctaOverline: String
    val ctaTitle: String
    val ctaText: String
    val suggestionsOverline: String
    val suggestionsTitle: String
    val allTopics: String
    val allTopicsText: String

    // Hints above the feed
    val noTopicsTitle: String
    val noTopicsText: String
    val offlineEmptyTitle: String
    val offlineEmptyText: String
    val offlineCachedTitle: String
    val offlineCachedText: String
    val noArticlesTitle: String
    val sourcesSilent: String
    val sourcesSwitchedOff: String
    val checkSources: String
    fun sourcesFailing(count: Int): String

    // Page footer
    val weatherCredit: String
    val weatherCreditOffline: String

    // Weather part of the page
    val headlineLoading: String
    val headlineFallback: String
    val weatherUnavailableTitle: String
    val weatherUnavailableText: String

    companion object {
        fun of(lang: AppLanguage): FeedStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : FeedStrings {
        override val forYou = "Für dich"
        override val latest = "Neueste"
        override val filterRow = "Nachrichten filtern"
        override val chooseTopics = "Themen auswählen"

        override fun barTitle(date: String) = "Heute · $date"
        override val search = "Suchen"
        override val searchDescription = "Nachrichten durchsuchen"
        override val discover = "Themen"
        override val discoverDescription = "Themen entdecken"

        override fun articleCount(count: Int) = if (count == 1) "1 Meldung" else "$count Meldungen"
        override fun allArticles(count: Int, topic: String) = if (count == 1) "Die Meldung zu $topic" else "Alle $count Meldungen zu $topic"
        override fun openTopic(topic: String) = "Alle Meldungen zu $topic"
        override fun stand(clock: String) = "Stand $clock"

        override val latestOverline = "Alle Themen"
        override val latestTitle = "Das Neueste zuerst"
        override fun subTopicsOf(topic: String) = "Unterthemen von $topic"

        override val loadingNews = "Hole die News …"
        override val loadingArticles = "Hole die Meldungen …"
        override val moreComing = "Mehr kommt …"
        override val loadingOlder = "Suche ältere Meldungen …"
        override val nothingOlderFound = "Gerade nichts Älteres gefunden."
        override val searchFurtherBack = "Weiter zurück suchen"
        override fun listEnd(count: Int) = "Das war's · ${articleCount(count)}"
        override val noArticlesYet = "Noch keine Meldungen geladen."
        override fun unreachable(sources: String) = "Gerade nicht erreichbar: $sources"
        override val nothingLoadedYet = "Noch nichts geladen – zieh nach unten zum Aktualisieren."
        override val nothingNewToday = "Heute noch nichts Neues."
        override val nothingHereRightNow = "Hier ist gerade nichts – schau später nochmal rein."
        override val backToTop = "Nach oben"

        override val lastTopicStays = "Einem Thema folgst du immer."
        override fun unfollowed(topic: String) = "Du folgst „$topic“ nicht mehr"
        override fun followed(topic: String) = "Du folgst jetzt „$topic“"
        override val undo = "Rückgängig"
        override fun sourceHidden(source: String) = "„$source“ ausgeblendet – Einstellungen → Quellen"
        override val listenUnavailable = "Vorlesen ist hier gerade nicht verfügbar."
        override val listenFailed = "Vorlesen klappt gerade nicht."
        override val copyFailed = "Kopieren hat nicht geklappt."
        override val linkCopied = "Link kopiert"
        override val clipLabel = "Link"

        override val readMore = "Weiterlesen"
        override val openArticle = "Artikel öffnen"
        override val moreOptions = "Weitere Optionen"
        override val listen = "Zuhören"
        override val stop = "Stopp"
        override val resume = "Weiter"
        override fun listenTo(headline: String) = "Vorlesen: $headline"
        override fun resumeListening(headline: String) = "Weiter vorlesen: $headline"
        override val stopListening = "Vorlesen beenden"

        override val readArticle = "Artikel lesen"
        override fun hideSource(source: String) = "„$source“ ausblenden"
        override fun followTopic(topic: String) = "„$topic“ folgen"
        override fun unfollowTopic(topic: String) = "„$topic“ nicht mehr folgen"

        override val fullText = "Volltext"
        override val fullTextDescription = "Volltext in der App lesbar"
        override val paywall = "Bezahlinhalt"
        override val paywallDescription = "Bezahlinhalt: nur für Abonnenten"
        override val browserOnly = "Nur im Browser"
        override val browserOnlyDescription = "Diese Seite lässt sich nur im Browser lesen"

        override val sheetOverline = "Dein Feed"
        override val sheetTitle = "Welche Themen willst du sehen?"
        override val sheetHint = "Antippen blendet ein oder aus · am Griff ziehen sortiert"
        override val sourcesAndOwnTopics = "Quellen & eigene Themen"
        override val discoverMoreTopics = "Weitere Themen entdecken"
        override val oneTopicStays = "Ein Thema bleibt immer an"
        override val noArticlesRightNow = "Gerade keine Meldungen"
        override fun moveUp(topic: String) = "$topic nach oben"
        override fun moveDown(topic: String) = "$topic nach unten"

        override val ctaOverline = "Dein Feed"
        override val ctaTitle = "Mach den Feed zu deinem"
        override val ctaText = "Folge Themen wie Börse, Raumfahrt oder deiner Stadt – jedes mit endlos vielen Meldungen."
        override val suggestionsOverline = "Mehr entdecken"
        override val suggestionsTitle = "Themen, die dich interessieren könnten"
        override val allTopics = "Alle Themen"
        override val allTopicsText = "Kategorien, Unterthemen und Orte"

        override val noTopicsTitle = "Du folgst gerade keinem Thema"
        override val noTopicsText = "Such dir ein paar Themen aus – dann füllt sich der Feed."
        override val offlineEmptyTitle = "Offline – noch keine News gespeichert"
        override val offlineEmptyText = "Sobald du wieder Netz hast, holt die App deine Meldungen."
        override val offlineCachedTitle = "Offline – du siehst die zuletzt geladenen Meldungen"
        override val offlineCachedText = "Sobald du wieder Netz hast, zieh einfach zum Aktualisieren nach unten."
        override val noArticlesTitle = "Keine Meldungen"
        override val sourcesSilent = "Die Quellen antworten gerade nicht."
        override val sourcesSwitchedOff = "Sind unter „Quellen“ noch welche eingeschaltet?"
        override val checkSources = "Quellen prüfen"
        override fun sourcesFailing(count: Int) = "$count Quellen antworten gerade nicht"

        override val weatherCredit = "Wetter: Open-Meteo"
        override val weatherCreditOffline = "Wetter: Open-Meteo (offline)"

        override val headlineLoading = "Einen Moment – ich schau kurz nach draußen."
        override val headlineFallback = "Schön, dass du da bist."
        override val weatherUnavailableTitle = "Wetter gerade nicht erreichbar"
        override val weatherUnavailableText = "Check kurz deine Verbindung – dann hol ich das Wetter nochmal."
    }

    private object En : FeedStrings {
        override val forYou = "For you"
        override val latest = "Latest"
        override val filterRow = "Filter the news"
        override val chooseTopics = "Choose topics"

        override fun barTitle(date: String) = "Today · $date"
        override val search = "Search"
        override val searchDescription = "Search the news"
        override val discover = "Topics"
        override val discoverDescription = "Discover topics"

        override fun articleCount(count: Int) = if (count == 1) "1 story" else "$count stories"
        override fun allArticles(count: Int, topic: String) = if (count == 1) "The story on $topic" else "All $count stories on $topic"
        override fun openTopic(topic: String) = "All stories on $topic"
        override fun stand(clock: String) = "Updated $clock"

        override val latestOverline = "All topics"
        override val latestTitle = "Newest first"
        override fun subTopicsOf(topic: String) = "More within $topic"

        override val loadingNews = "Fetching the news …"
        override val loadingArticles = "Fetching the stories …"
        override val moreComing = "More on the way …"
        override val loadingOlder = "Looking for older stories …"
        override val nothingOlderFound = "Nothing older turned up just now."
        override val searchFurtherBack = "Search further back"
        override fun listEnd(count: Int) = "That's all · ${articleCount(count)}"
        override val noArticlesYet = "No stories loaded yet."
        override fun unreachable(sources: String) = "Can't be reached right now: $sources"
        override val nothingLoadedYet = "Nothing loaded yet – pull down to refresh."
        override val nothingNewToday = "Nothing new today yet."
        override val nothingHereRightNow = "Nothing here right now – check back later."
        override val backToTop = "Back to top"

        override val lastTopicStays = "You always follow at least one topic."
        override fun unfollowed(topic: String) = "You no longer follow “$topic”"
        override fun followed(topic: String) = "You're now following “$topic”"
        override val undo = "Undo"
        override fun sourceHidden(source: String) = "“$source” hidden – Settings → Sources"
        override val listenUnavailable = "Reading aloud isn't available here right now."
        override val listenFailed = "Reading aloud isn't working right now."
        override val copyFailed = "Couldn't copy that."
        override val linkCopied = "Link copied"
        override val clipLabel = "Link"

        override val readMore = "Read more"
        override val openArticle = "Open article"
        override val moreOptions = "More options"
        override val listen = "Listen"
        override val stop = "Stop"
        override val resume = "Resume"
        override fun listenTo(headline: String) = "Read aloud: $headline"
        override fun resumeListening(headline: String) = "Resume reading: $headline"
        override val stopListening = "Stop reading aloud"

        override val readArticle = "Read article"
        override fun hideSource(source: String) = "Hide “$source”"
        override fun followTopic(topic: String) = "Follow “$topic”"
        override fun unfollowTopic(topic: String) = "Unfollow “$topic”"

        override val fullText = "Full text"
        override val fullTextDescription = "Full text readable in the app"
        override val paywall = "Paywall"
        override val paywallDescription = "Paywall: for subscribers only"
        override val browserOnly = "Browser only"
        override val browserOnlyDescription = "This site can only be read in the browser"

        override val sheetOverline = "Your feed"
        override val sheetTitle = "Which topics do you want to see?"
        override val sheetHint = "Tap to show or hide · drag the handle to reorder"
        override val sourcesAndOwnTopics = "Sources & your own topics"
        override val discoverMoreTopics = "Discover more topics"
        override val oneTopicStays = "One topic always stays on"
        override val noArticlesRightNow = "No stories right now"
        override fun moveUp(topic: String) = "Move $topic up"
        override fun moveDown(topic: String) = "Move $topic down"

        override val ctaOverline = "Your feed"
        override val ctaTitle = "Make this feed your own"
        override val ctaText = "Follow topics like the stock market, space or your city – each with as many stories as you like."
        override val suggestionsOverline = "Discover more"
        override val suggestionsTitle = "Topics you might like"
        override val allTopics = "All topics"
        override val allTopicsText = "Categories, sub-topics and places"

        override val noTopicsTitle = "You're not following any topics"
        override val noTopicsText = "Pick a few topics and your feed fills up."
        override val offlineEmptyTitle = "Offline – no news saved yet"
        override val offlineEmptyText = "As soon as you're back online, the app fetches your stories."
        override val offlineCachedTitle = "Offline – these are the stories loaded last"
        override val offlineCachedText = "Once you're back online, just pull down to refresh."
        override val noArticlesTitle = "No stories"
        override val sourcesSilent = "The sources aren't responding right now."
        override val sourcesSwitchedOff = "Are any sources still switched on under “Sources”?"
        override val checkSources = "Check sources"
        override fun sourcesFailing(count: Int) = "$count sources aren't responding"

        override val weatherCredit = "Weather: Open-Meteo"
        override val weatherCreditOffline = "Weather: Open-Meteo (offline)"

        override val headlineLoading = "One moment – just taking a look outside."
        override val headlineFallback = "Good to see you."
        override val weatherUnavailableTitle = "Weather can't be reached right now"
        override val weatherUnavailableText = "Check your connection quickly – then I'll fetch the weather again."
    }

    private object Fr : FeedStrings {
        override val forYou = "Pour vous"
        override val latest = "À la une"
        override val filterRow = "Filtrer les actualités"
        override val chooseTopics = "Choisir les thèmes"

        override fun barTitle(date: String) = "Aujourd'hui · $date"
        override val search = "Rechercher"
        override val searchDescription = "Rechercher dans l'actualité"
        override val discover = "Thèmes"
        override val discoverDescription = "Découvrir des thèmes"

        override fun articleCount(count: Int) = if (count <= 1) "$count article" else "$count articles"
        override fun allArticles(count: Int, topic: String) = if (count == 1) "L'article sur $topic" else "Les $count articles sur $topic"
        override fun openTopic(topic: String) = "Tous les articles sur $topic"
        override fun stand(clock: String) = "Mis à jour à $clock"

        override val latestOverline = "Tous les thèmes"
        override val latestTitle = "Les plus récents d'abord"
        override fun subTopicsOf(topic: String) = "Sous-thèmes de $topic"

        override val loadingNews = "Chargement de l'actualité …"
        override val loadingArticles = "Chargement des articles …"
        override val moreComing = "La suite arrive …"
        override val loadingOlder = "Recherche d'articles plus anciens …"
        override val nothingOlderFound = "Rien de plus ancien pour l'instant."
        override val searchFurtherBack = "Remonter plus loin"
        override fun listEnd(count: Int) = "C'est tout · ${articleCount(count)}"
        override val noArticlesYet = "Aucun article chargé pour l'instant."
        override fun unreachable(sources: String) = "Injoignable pour le moment : $sources"
        override val nothingLoadedYet = "Rien de chargé – tirez vers le bas pour actualiser."
        override val nothingNewToday = "Rien de neuf aujourd'hui pour l'instant."
        override val nothingHereRightNow = "Rien ici pour le moment – repassez plus tard."
        override val backToTop = "Revenir en haut"

        override val lastTopicStays = "Vous suivez toujours au moins un thème."
        override fun unfollowed(topic: String) = "Vous ne suivez plus « $topic »"
        override fun followed(topic: String) = "Vous suivez désormais « $topic »"
        override val undo = "Annuler"
        override fun sourceHidden(source: String) = "« $source » masqué – Réglages → Sources"
        override val listenUnavailable = "La lecture à voix haute n'est pas disponible ici pour le moment."
        override val listenFailed = "La lecture à voix haute ne fonctionne pas pour le moment."
        override val copyFailed = "La copie n'a pas fonctionné."
        override val linkCopied = "Lien copié"
        override val clipLabel = "Lien"

        override val readMore = "Lire la suite"
        override val openArticle = "Ouvrir l'article"
        override val moreOptions = "Plus d'options"
        override val listen = "Écouter"
        override val stop = "Arrêter"
        override val resume = "Reprendre"
        override fun listenTo(headline: String) = "Lire à voix haute : $headline"
        override fun resumeListening(headline: String) = "Reprendre la lecture : $headline"
        override val stopListening = "Arrêter la lecture"

        override val readArticle = "Lire l'article"
        override fun hideSource(source: String) = "Masquer « $source »"
        override fun followTopic(topic: String) = "Suivre « $topic »"
        override fun unfollowTopic(topic: String) = "Ne plus suivre « $topic »"

        override val fullText = "Texte intégral"
        override val fullTextDescription = "Texte intégral lisible dans l'app"
        override val paywall = "Payant"
        override val paywallDescription = "Article payant : réservé aux abonnés"
        override val browserOnly = "Navigateur"
        override val browserOnlyDescription = "Ce site ne se lit que dans le navigateur"

        override val sheetOverline = "Votre fil"
        override val sheetTitle = "Quels thèmes voulez-vous voir ?"
        override val sheetHint = "Touchez pour afficher ou masquer · faites glisser la poignée pour trier"
        override val sourcesAndOwnTopics = "Sources et thèmes perso"
        override val discoverMoreTopics = "Découvrir d'autres thèmes"
        override val oneTopicStays = "Un thème reste toujours actif"
        override val noArticlesRightNow = "Aucun article pour le moment"
        override fun moveUp(topic: String) = "Monter $topic"
        override fun moveDown(topic: String) = "Descendre $topic"

        override val ctaOverline = "Votre fil"
        override val ctaTitle = "Faites de ce fil le vôtre"
        override val ctaText = "Suivez la Bourse, l'espace ou votre ville – chaque thème avec autant d'articles que vous voulez."
        override val suggestionsOverline = "À découvrir"
        override val suggestionsTitle = "Des thèmes qui pourraient vous plaire"
        override val allTopics = "Tous les thèmes"
        override val allTopicsText = "Catégories, sous-thèmes et lieux"

        override val noTopicsTitle = "Vous ne suivez aucun thème"
        override val noTopicsText = "Choisissez quelques thèmes et votre fil se remplit."
        override val offlineEmptyTitle = "Hors ligne – aucune actualité enregistrée"
        override val offlineEmptyText = "Dès que vous retrouvez du réseau, l'app charge vos articles."
        override val offlineCachedTitle = "Hors ligne – voici les derniers articles chargés"
        override val offlineCachedText = "Dès que le réseau revient, tirez vers le bas pour actualiser."
        override val noArticlesTitle = "Aucun article"
        override val sourcesSilent = "Les sources ne répondent pas pour le moment."
        override val sourcesSwitchedOff = "Reste-t-il des sources activées dans « Sources » ?"
        override val checkSources = "Vérifier les sources"
        override fun sourcesFailing(count: Int) = "$count sources ne répondent pas"

        override val weatherCredit = "Météo : Open-Meteo"
        override val weatherCreditOffline = "Météo : Open-Meteo (hors ligne)"

        override val headlineLoading = "Un instant – je jette un œil dehors."
        override val headlineFallback = "Ravi de vous voir."
        override val weatherUnavailableTitle = "Météo injoignable pour le moment"
        override val weatherUnavailableText = "Vérifiez votre connexion – je récupère ensuite la météo."
    }

    private object Es : FeedStrings {
        override val forYou = "Para ti"
        override val latest = "Lo último"
        override val filterRow = "Filtrar noticias"
        override val chooseTopics = "Elegir temas"

        override fun barTitle(date: String) = "Hoy · $date"
        override val search = "Buscar"
        override val searchDescription = "Buscar noticias"
        override val discover = "Temas"
        override val discoverDescription = "Descubrir temas"

        override fun articleCount(count: Int) = if (count == 1) "1 noticia" else "$count noticias"
        override fun allArticles(count: Int, topic: String) = if (count == 1) "La noticia sobre $topic" else "Las $count noticias sobre $topic"
        override fun openTopic(topic: String) = "Todas las noticias sobre $topic"
        override fun stand(clock: String) = "Actualizado a las $clock"

        override val latestOverline = "Todos los temas"
        override val latestTitle = "Lo más reciente primero"
        override fun subTopicsOf(topic: String) = "Subtemas de $topic"

        override val loadingNews = "Cargando las noticias …"
        override val loadingArticles = "Cargando las noticias …"
        override val moreComing = "Vienen más …"
        override val loadingOlder = "Buscando noticias más antiguas …"
        override val nothingOlderFound = "Ahora mismo no hay nada más antiguo."
        override val searchFurtherBack = "Buscar más atrás"
        override fun listEnd(count: Int) = "Eso es todo · ${articleCount(count)}"
        override val noArticlesYet = "Todavía no hay noticias cargadas."
        override fun unreachable(sources: String) = "Sin conexión ahora mismo: $sources"
        override val nothingLoadedYet = "Aún no hay nada – desliza hacia abajo para actualizar."
        override val nothingNewToday = "Hoy todavía no hay nada nuevo."
        override val nothingHereRightNow = "Ahora mismo no hay nada aquí – vuelve más tarde."
        override val backToTop = "Volver arriba"

        override val lastTopicStays = "Siempre sigues al menos un tema."
        override fun unfollowed(topic: String) = "Ya no sigues «$topic»"
        override fun followed(topic: String) = "Ahora sigues «$topic»"
        override val undo = "Deshacer"
        override fun sourceHidden(source: String) = "«$source» oculto – Ajustes → Fuentes"
        override val listenUnavailable = "La lectura en voz alta no está disponible aquí ahora mismo."
        override val listenFailed = "La lectura en voz alta no funciona ahora mismo."
        override val copyFailed = "No se ha podido copiar."
        override val linkCopied = "Enlace copiado"
        override val clipLabel = "Enlace"

        override val readMore = "Seguir leyendo"
        override val openArticle = "Abrir artículo"
        override val moreOptions = "Más opciones"
        override val listen = "Escuchar"
        override val stop = "Parar"
        override val resume = "Seguir"
        override fun listenTo(headline: String) = "Leer en voz alta: $headline"
        override fun resumeListening(headline: String) = "Seguir leyendo en voz alta: $headline"
        override val stopListening = "Dejar de leer en voz alta"

        override val readArticle = "Leer artículo"
        override fun hideSource(source: String) = "Ocultar «$source»"
        override fun followTopic(topic: String) = "Seguir «$topic»"
        override fun unfollowTopic(topic: String) = "Dejar de seguir «$topic»"

        override val fullText = "Texto completo"
        override val fullTextDescription = "Texto completo legible en la app"
        override val paywall = "De pago"
        override val paywallDescription = "Contenido de pago: solo para suscriptores"
        override val browserOnly = "Solo navegador"
        override val browserOnlyDescription = "Esta web solo se puede leer en el navegador"

        override val sheetOverline = "Tu feed"
        override val sheetTitle = "¿Qué temas quieres ver?"
        override val sheetHint = "Toca para mostrar u ocultar · arrastra el asa para ordenar"
        override val sourcesAndOwnTopics = "Fuentes y temas propios"
        override val discoverMoreTopics = "Descubrir más temas"
        override val oneTopicStays = "Siempre queda un tema activo"
        override val noArticlesRightNow = "Ahora mismo no hay noticias"
        override fun moveUp(topic: String) = "Subir $topic"
        override fun moveDown(topic: String) = "Bajar $topic"

        override val ctaOverline = "Tu feed"
        override val ctaTitle = "Haz tuyo este feed"
        override val ctaText = "Sigue temas como la Bolsa, el espacio o tu ciudad, cada uno con tantas noticias como quieras."
        override val suggestionsOverline = "Descubre más"
        override val suggestionsTitle = "Temas que podrían gustarte"
        override val allTopics = "Todos los temas"
        override val allTopicsText = "Categorías, subtemas y lugares"

        override val noTopicsTitle = "No sigues ningún tema"
        override val noTopicsText = "Elige algunos temas y tu feed se llenará."
        override val offlineEmptyTitle = "Sin conexión – aún no hay noticias guardadas"
        override val offlineEmptyText = "En cuanto vuelvas a tener red, la app cargará tus noticias."
        override val offlineCachedTitle = "Sin conexión – ves las últimas noticias cargadas"
        override val offlineCachedText = "Cuando vuelvas a tener red, desliza hacia abajo para actualizar."
        override val noArticlesTitle = "No hay noticias"
        override val sourcesSilent = "Las fuentes no responden ahora mismo."
        override val sourcesSwitchedOff = "¿Queda alguna fuente activada en «Fuentes»?"
        override val checkSources = "Revisar fuentes"
        override fun sourcesFailing(count: Int) = "$count fuentes no responden"

        override val weatherCredit = "Tiempo: Open-Meteo"
        override val weatherCreditOffline = "Tiempo: Open-Meteo (sin conexión)"

        override val headlineLoading = "Un momento, echo un vistazo afuera."
        override val headlineFallback = "Qué bien que estés aquí."
        override val weatherUnavailableTitle = "El tiempo no está disponible ahora mismo"
        override val weatherUnavailableText = "Revisa tu conexión y vuelvo a consultar el tiempo."
    }

    private object It : FeedStrings {
        override val forYou = "Per te"
        override val latest = "Ultime"
        override val filterRow = "Filtra le notizie"
        override val chooseTopics = "Scegli i temi"

        override fun barTitle(date: String) = "Oggi · $date"
        override val search = "Cerca"
        override val searchDescription = "Cerca tra le notizie"
        override val discover = "Temi"
        override val discoverDescription = "Scopri i temi"

        override fun articleCount(count: Int) = if (count == 1) "1 notizia" else "$count notizie"
        override fun allArticles(count: Int, topic: String) = if (count == 1) "La notizia su $topic" else "Tutte le $count notizie su $topic"
        override fun openTopic(topic: String) = "Tutte le notizie su $topic"
        override fun stand(clock: String) = "Aggiornato alle $clock"

        override val latestOverline = "Tutti i temi"
        override val latestTitle = "Prima le più recenti"
        override fun subTopicsOf(topic: String) = "Sottotemi di $topic"

        override val loadingNews = "Carico le notizie …"
        override val loadingArticles = "Carico le notizie …"
        override val moreComing = "Ne arrivano altre …"
        override val loadingOlder = "Cerco notizie meno recenti …"
        override val nothingOlderFound = "Per ora niente di meno recente."
        override val searchFurtherBack = "Cerca più indietro"
        override fun listEnd(count: Int) = "È tutto · ${articleCount(count)}"
        override val noArticlesYet = "Nessuna notizia caricata finora."
        override fun unreachable(sources: String) = "Non raggiungibile al momento: $sources"
        override val nothingLoadedYet = "Ancora niente – trascina verso il basso per aggiornare."
        override val nothingNewToday = "Oggi ancora niente di nuovo."
        override val nothingHereRightNow = "Qui per ora non c'è niente – ripassa più tardi."
        override val backToTop = "Torna su"

        override val lastTopicStays = "Segui sempre almeno un tema."
        override fun unfollowed(topic: String) = "Non segui più «$topic»"
        override fun followed(topic: String) = "Ora segui «$topic»"
        override val undo = "Annulla"
        override fun sourceHidden(source: String) = "«$source» nascosta – Impostazioni → Fonti"
        override val listenUnavailable = "La lettura ad alta voce qui non è disponibile al momento."
        override val listenFailed = "La lettura ad alta voce non funziona al momento."
        override val copyFailed = "Copia non riuscita."
        override val linkCopied = "Link copiato"
        override val clipLabel = "Link"

        override val readMore = "Continua a leggere"
        override val openArticle = "Apri l'articolo"
        override val moreOptions = "Altre opzioni"
        override val listen = "Ascolta"
        override val stop = "Stop"
        override val resume = "Riprendi"
        override fun listenTo(headline: String) = "Leggi ad alta voce: $headline"
        override fun resumeListening(headline: String) = "Riprendi la lettura: $headline"
        override val stopListening = "Interrompi la lettura"

        override val readArticle = "Leggi l'articolo"
        override fun hideSource(source: String) = "Nascondi «$source»"
        override fun followTopic(topic: String) = "Segui «$topic»"
        override fun unfollowTopic(topic: String) = "Non seguire più «$topic»"

        override val fullText = "Testo completo"
        override val fullTextDescription = "Testo completo leggibile nell'app"
        override val paywall = "A pagamento"
        override val paywallDescription = "Contenuto a pagamento: solo per abbonati"
        override val browserOnly = "Solo browser"
        override val browserOnlyDescription = "Questo sito si legge solo nel browser"

        override val sheetOverline = "Il tuo feed"
        override val sheetTitle = "Quali temi vuoi vedere?"
        override val sheetHint = "Tocca per mostrare o nascondere · trascina la maniglia per ordinare"
        override val sourcesAndOwnTopics = "Fonti e temi personali"
        override val discoverMoreTopics = "Scopri altri temi"
        override val oneTopicStays = "Un tema resta sempre attivo"
        override val noArticlesRightNow = "Al momento nessuna notizia"
        override fun moveUp(topic: String) = "Sposta su $topic"
        override fun moveDown(topic: String) = "Sposta giù $topic"

        override val ctaOverline = "Il tuo feed"
        override val ctaTitle = "Rendi tuo questo feed"
        override val ctaText = "Segui temi come la Borsa, lo spazio o la tua città, ognuno con tutte le notizie che vuoi."
        override val suggestionsOverline = "Scopri di più"
        override val suggestionsTitle = "Temi che potrebbero piacerti"
        override val allTopics = "Tutti i temi"
        override val allTopicsText = "Categorie, sottotemi e luoghi"

        override val noTopicsTitle = "Non segui nessun tema"
        override val noTopicsText = "Scegli qualche tema e il feed si riempie."
        override val offlineEmptyTitle = "Offline – nessuna notizia salvata"
        override val offlineEmptyText = "Appena torna la rete, l'app carica le tue notizie."
        override val offlineCachedTitle = "Offline – vedi le ultime notizie caricate"
        override val offlineCachedText = "Appena torna la rete, trascina verso il basso per aggiornare."
        override val noArticlesTitle = "Nessuna notizia"
        override val sourcesSilent = "Le fonti al momento non rispondono."
        override val sourcesSwitchedOff = "C'è ancora qualche fonte attiva in «Fonti»?"
        override val checkSources = "Controlla le fonti"
        override fun sourcesFailing(count: Int) = "$count fonti non rispondono"

        override val weatherCredit = "Meteo: Open-Meteo"
        override val weatherCreditOffline = "Meteo: Open-Meteo (offline)"

        override val headlineLoading = "Un attimo, do un'occhiata fuori."
        override val headlineFallback = "Che bello vederti."
        override val weatherUnavailableTitle = "Meteo non raggiungibile al momento"
        override val weatherUnavailableText = "Controlla la connessione, poi riprovo a caricare il meteo."
    }
}

@Composable
@ReadOnlyComposable
fun feedStrings(): FeedStrings = FeedStrings.of(LocalAppLanguage.current)

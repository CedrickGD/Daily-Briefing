package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Texts of the news feed ("Heute": the weather card, the feed bar with search and tabs, the
 * story cards and their ⋮ menu, "Deine Themen", the hints above the feed and the endless
 * lists). Pattern: [CommonStrings].
 */
interface FeedStrings {
    // Feed tabs
    val forYou: String
    val latest: String
    val filterRow: String

    // Search and discover
    val searchDescription: String
    val discoverDescription: String

    // Counts and labels
    fun articleCount(count: Int): String
    fun allArticles(count: Int, topic: String): String
    fun stand(clock: String): String

    // A topic's own list
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

    // "Nur vollständige Artikel": checking candidates before they show
    val verifying: String
    val verifyOffline: String
    val verifyOfflineTitle: String
    val verifyOfflineText: String
    val noneComplete: String
    val noCompleteTitle: String
    val noCompleteText: String
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

    // "Deine Themen" rows
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
    val weatherUnavailableTitle: String
    val weatherUnavailableText: String

    // Feed bar (search field, tabs, "＋") and the "Für dich" cards
    val manageTopics: String
    val searchPlaceholder: String
    val showAll: String
    val continueListening: String

    // "Deine Themen" sheet
    val yourTopicsTitle: String
    val yourTopicsHint: String
    val addPlace: String

    // Compact weather card
    val dry: String
    val weatherExpand: String
    val weatherCollapse: String
    val weatherExpandedState: String
    val weatherCollapsedState: String

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

        override val searchDescription = "Nachrichten durchsuchen"
        override val discoverDescription = "Themen entdecken"

        override fun articleCount(count: Int) = if (count == 1) "1 Meldung" else "$count Meldungen"
        override fun allArticles(count: Int, topic: String) = if (count == 1) "Die Meldung zu $topic" else "Alle $count Meldungen zu $topic"
        override fun stand(clock: String) = "Stand $clock"

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
        override val verifying = "Weitere Artikel werden geprüft …"
        override val verifyOffline = "Offline – hier stehen nur schon geprüfte Artikel. Weitere prüft die App, sobald du wieder online bist."
        override val verifyOfflineTitle = "Offline – nur geprüfte Artikel"
        override val verifyOfflineText = "Weitere Artikel prüft die App, sobald du wieder online bist."
        override val noneComplete = "Gerade ist hier kein Artikel vollständig in der App lesbar."
        override val noCompleteTitle = "Kein vollständiger Artikel"
        override val noCompleteText = "Die neuesten Meldungen lassen sich gerade nur bei den Redaktionen ganz lesen. Alle zeigt die App, wenn du „Nur vollständige Artikel“ in den Einstellungen ausschaltest."
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

        override val weatherUnavailableTitle = "Wetter gerade nicht erreichbar"
        override val weatherUnavailableText = "Check kurz deine Verbindung – dann hol ich das Wetter nochmal."

        override val manageTopics = "Deine Themen verwalten"
        override val searchPlaceholder = "Suchen – z. B. SLV-ETF, Midterms"
        override val showAll = "Alle"
        override val continueListening = "Weiter zuhören"
        override val yourTopicsTitle = "Deine Themen"
        override val yourTopicsHint = "Ziehen zum Sortieren · ✕ entfernt ein Thema"
        override val addPlace = "Ort hinzufügen"
        override val dry = "trocken"
        override val weatherExpand = "Wetter aufklappen"
        override val weatherCollapse = "Wetter zuklappen"
        override val weatherExpandedState = "aufgeklappt"
        override val weatherCollapsedState = "zugeklappt"
    }

    private object En : FeedStrings {
        override val forYou = "For you"
        override val latest = "Latest"
        override val filterRow = "Filter the news"

        override val searchDescription = "Search the news"
        override val discoverDescription = "Discover topics"

        override fun articleCount(count: Int) = if (count == 1) "1 story" else "$count stories"
        override fun allArticles(count: Int, topic: String) = if (count == 1) "The story on $topic" else "All $count stories on $topic"
        override fun stand(clock: String) = "Updated $clock"

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
        override val verifying = "Checking more articles …"
        override val verifyOffline = "Offline – only articles checked before are shown. The app checks more once you're back online."
        override val verifyOfflineTitle = "Offline – checked articles only"
        override val verifyOfflineText = "The app checks more articles once you're back online."
        override val noneComplete = "Right now no article here can be read in full in the app."
        override val noCompleteTitle = "No complete article"
        override val noCompleteText = "The latest stories can only be read in full on the publishers' sites right now. To see them all, switch off “Only complete articles” in the settings."
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

        override val weatherUnavailableTitle = "Weather can't be reached right now"
        override val weatherUnavailableText = "Check your connection quickly – then I'll fetch the weather again."

        override val manageTopics = "Manage your topics"
        override val searchPlaceholder = "Search – e.g. SLV ETF, midterms"
        override val showAll = "See all"
        override val continueListening = "Keep listening"
        override val yourTopicsTitle = "Your topics"
        override val yourTopicsHint = "Drag to reorder · ✕ removes a topic"
        override val addPlace = "Add a place"
        override val dry = "dry"
        override val weatherExpand = "Show more weather"
        override val weatherCollapse = "Show less weather"
        override val weatherExpandedState = "expanded"
        override val weatherCollapsedState = "collapsed"
    }

    private object Fr : FeedStrings {
        override val forYou = "Pour toi"
        override val latest = "À la une"
        override val filterRow = "Filtrer les actualités"

        override val searchDescription = "Rechercher dans l'actualité"
        override val discoverDescription = "Découvrir des thèmes"

        override fun articleCount(count: Int) = if (count <= 1) "$count article" else "$count articles"
        override fun allArticles(count: Int, topic: String) = if (count == 1) "L'article sur $topic" else "Les $count articles sur $topic"
        override fun stand(clock: String) = "Mis à jour à $clock"

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
        override val nothingLoadedYet = "Rien de chargé – tire vers le bas pour actualiser."
        override val nothingNewToday = "Rien de neuf aujourd'hui pour l'instant."
        override val nothingHereRightNow = "Rien ici pour le moment – repasse plus tard."
        override val verifying = "Vérification d'autres articles …"
        override val verifyOffline = "Hors ligne – seuls les articles déjà vérifiés s'affichent. L'appli en vérifie d'autres dès que tu es de nouveau en ligne."
        override val verifyOfflineTitle = "Hors ligne – articles vérifiés seulement"
        override val verifyOfflineText = "L'appli vérifie d'autres articles dès que tu es de nouveau en ligne."
        override val noneComplete = "Pour l'instant, aucun article ici ne se lit en entier dans l'appli."
        override val noCompleteTitle = "Aucun article complet"
        override val noCompleteText = "Les derniers articles ne se lisent en entier que sur les sites des rédactions pour le moment. Pour tout voir, désactive « Articles complets uniquement » dans les réglages."
        override val backToTop = "Revenir en haut"

        override val lastTopicStays = "Tu suis toujours au moins un thème."
        override fun unfollowed(topic: String) = "Tu ne suis plus « $topic »"
        override fun followed(topic: String) = "Tu suis désormais « $topic »"
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

        override val noArticlesRightNow = "Aucun article pour le moment"
        override fun moveUp(topic: String) = "Monter $topic"
        override fun moveDown(topic: String) = "Descendre $topic"

        override val ctaOverline = "Ton fil"
        override val ctaTitle = "Fais de ce fil le tien"
        override val ctaText = "Suis la Bourse, l'espace ou ta ville – chaque thème avec autant d'articles que tu veux."
        override val suggestionsOverline = "À découvrir"
        override val suggestionsTitle = "Des thèmes qui pourraient te plaire"
        override val allTopics = "Tous les thèmes"
        override val allTopicsText = "Catégories, sous-thèmes et lieux"

        override val noTopicsTitle = "Tu ne suis aucun thème"
        override val noTopicsText = "Choisis quelques thèmes et ton fil se remplit."
        override val offlineEmptyTitle = "Hors ligne – aucune actualité enregistrée"
        override val offlineEmptyText = "Dès que tu retrouves du réseau, l'app charge tes articles."
        override val offlineCachedTitle = "Hors ligne – voici les derniers articles chargés"
        override val offlineCachedText = "Dès que le réseau revient, tire vers le bas pour actualiser."
        override val noArticlesTitle = "Aucun article"
        override val sourcesSilent = "Les sources ne répondent pas pour le moment."
        override val sourcesSwitchedOff = "Reste-t-il des sources activées dans « Sources » ?"
        override val checkSources = "Vérifier les sources"
        override fun sourcesFailing(count: Int) = "$count sources ne répondent pas"

        override val weatherCredit = "Météo : Open-Meteo"
        override val weatherCreditOffline = "Météo : Open-Meteo (hors ligne)"

        override val weatherUnavailableTitle = "Météo injoignable pour le moment"
        override val weatherUnavailableText = "Vérifie ta connexion – je récupère ensuite la météo."

        override val manageTopics = "Gérer tes thèmes"
        override val searchPlaceholder = "Rechercher – p. ex. ETF SLV, midterms"
        override val showAll = "Tout voir"
        override val continueListening = "Reprendre l'écoute"
        override val yourTopicsTitle = "Tes thèmes"
        override val yourTopicsHint = "Fais glisser pour trier · ✕ retire un thème"
        override val addPlace = "Ajouter un lieu"
        override val dry = "sec"
        override val weatherExpand = "Déplier la météo"
        override val weatherCollapse = "Replier la météo"
        override val weatherExpandedState = "déplié"
        override val weatherCollapsedState = "replié"
    }

    private object Es : FeedStrings {
        override val forYou = "Para ti"
        override val latest = "Lo último"
        override val filterRow = "Filtrar noticias"

        override val searchDescription = "Buscar noticias"
        override val discoverDescription = "Descubrir temas"

        override fun articleCount(count: Int) = if (count == 1) "1 noticia" else "$count noticias"
        override fun allArticles(count: Int, topic: String) = if (count == 1) "La noticia sobre $topic" else "Las $count noticias sobre $topic"
        override fun stand(clock: String) = "Actualizado a las $clock"

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
        override val verifying = "Comprobando más artículos …"
        override val verifyOffline = "Sin conexión – solo ves artículos ya comprobados. La app comprobará más cuando vuelvas a estar en línea."
        override val verifyOfflineTitle = "Sin conexión – solo artículos comprobados"
        override val verifyOfflineText = "La app comprobará más artículos cuando vuelvas a estar en línea."
        override val noneComplete = "Ahora mismo ningún artículo de aquí se puede leer completo en la app."
        override val noCompleteTitle = "Ningún artículo completo"
        override val noCompleteText = "Las últimas noticias solo se pueden leer completas en las webs de los medios. Para verlas todas, desactiva «Solo artículos completos» en los ajustes."
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

        override val weatherUnavailableTitle = "El tiempo no está disponible ahora mismo"
        override val weatherUnavailableText = "Revisa tu conexión y vuelvo a consultar el tiempo."

        override val manageTopics = "Gestionar tus temas"
        override val searchPlaceholder = "Buscar – p. ej. ETF SLV, elecciones"
        override val showAll = "Ver todas"
        override val continueListening = "Seguir escuchando"
        override val yourTopicsTitle = "Tus temas"
        override val yourTopicsHint = "Arrastra para ordenar · ✕ quita un tema"
        override val addPlace = "Añadir un lugar"
        override val dry = "seco"
        override val weatherExpand = "Mostrar más del tiempo"
        override val weatherCollapse = "Mostrar menos del tiempo"
        override val weatherExpandedState = "desplegado"
        override val weatherCollapsedState = "plegado"
    }

    private object It : FeedStrings {
        override val forYou = "Per te"
        override val latest = "Ultime"
        override val filterRow = "Filtra le notizie"

        override val searchDescription = "Cerca tra le notizie"
        override val discoverDescription = "Scopri i temi"

        override fun articleCount(count: Int) = if (count == 1) "1 notizia" else "$count notizie"
        override fun allArticles(count: Int, topic: String) = if (count == 1) "La notizia su $topic" else "Tutte le $count notizie su $topic"
        override fun stand(clock: String) = "Aggiornato alle $clock"

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
        override val verifying = "Verifico altri articoli …"
        override val verifyOffline = "Offline – vedi solo articoli già verificati. L'app ne verifica altri appena torni online."
        override val verifyOfflineTitle = "Offline – solo articoli verificati"
        override val verifyOfflineText = "L'app verifica altri articoli appena torni online."
        override val noneComplete = "Al momento nessun articolo qui si legge per intero nell'app."
        override val noCompleteTitle = "Nessun articolo completo"
        override val noCompleteText = "Le ultime notizie si leggono per intero solo sui siti delle testate. Per vederle tutte, disattiva «Solo articoli completi» nelle impostazioni."
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

        override val weatherUnavailableTitle = "Meteo non raggiungibile al momento"
        override val weatherUnavailableText = "Controlla la connessione, poi riprovo a caricare il meteo."

        override val manageTopics = "Gestisci i tuoi temi"
        override val searchPlaceholder = "Cerca – ad es. ETF SLV, midterm"
        override val showAll = "Vedi tutte"
        override val continueListening = "Continua ad ascoltare"
        override val yourTopicsTitle = "I tuoi temi"
        override val yourTopicsHint = "Trascina per ordinare · ✕ rimuove un tema"
        override val addPlace = "Aggiungi un luogo"
        override val dry = "asciutto"
        override val weatherExpand = "Mostra più meteo"
        override val weatherCollapse = "Mostra meno meteo"
        override val weatherExpandedState = "espanso"
        override val weatherCollapsedState = "compresso"
    }
}

@Composable
@ReadOnlyComposable
fun feedStrings(): FeedStrings = FeedStrings.of(LocalAppLanguage.current)

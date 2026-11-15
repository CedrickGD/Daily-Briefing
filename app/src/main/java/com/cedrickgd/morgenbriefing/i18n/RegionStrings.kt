package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * "Sprache & Region" (app language, news region, news language, Bundesland, local news), the
 * place cards with the day window and the place picker. Country and language names come from
 * the platform's locale data in the interface language (`ui/settings/EditionLogic`).
 */
interface RegionStrings {
    // App language
    val appLanguage: String
    val appLanguageHint: String
    val followPhone: String
    /** "Wie das Handy · Deutsch" */
    fun followPhoneWith(language: String): String

    // News region and news language: two choices, independent of each other and of the app language
    val newsRegion: String
    val newsLanguage: String
    val newsEditionHint: String
    /**
     * Under the two choices when Google News has no edition of [country] in [language]: the app
     * searches for the country instead. [language] as a sentence writes it ("Deutsch", "allemand").
     */
    fun newsSearchedHint(country: String, language: String): String
    val change: String
    val newsRegionTitle: String
    val newsRegionSearch: String
    val newsRegionSuggested: String
    val newsRegionAll: String
    fun newsRegionNone(query: String): String
    val newsLanguageTitle: String
    /** Heading over the languages Google News has an edition of [country] in. */
    fun newsLanguageOwn(country: String): String
    val newsLanguageOther: String
    val newsLanguageOtherHint: String
    val selected: String
    val clearSearch: String
    /** "Deutschland · Deutsch" read out for a region with its language, "English · Englisch" for a language. */
    fun editionDescription(country: String, language: String): String

    // Bundesland (German editions)
    val region: String
    val regionHint: String
    val regionDialogTitle: String
    val regionDialogText: String

    // Local news
    val localNews: String
    val localNewsHint: String
    val localEmpty: String
    val addPlace: String
    fun removePlace(name: String): String
    /** "Nachrichten: 🇺🇸 Vereinigte Staaten · Deutsch" under a followed place: its country and the news language. */
    fun localEdition(country: String, language: String): String
    val notInFeed: String
    val follow: String

    // Place cards and the day window
    val placeHome: String
    val placeDay: String
    val changePlace: String
    val weatherUnavailable: String
    val lastUpdated: String
    val maxToday: String
    val maxTomorrow: String
    val dayWindow: String
    fun dayWindowFor(place: String): String
    val dayWindowDescription: String

    // Place picker
    val pickerHomeQuestion: String
    val pickerDayQuestion: String
    val pickerLocalQuestion: String
    val pickerLocalHint: String
    val searchPlaceholder: String
    val myLocation: String
    val locating: String
    val currentPlace: String
    val quickPicks: String
    val searchFailed: String
    fun nothingFound(query: String): String
    val select: String
    val locationNoPermission: String
    val locationUnavailable: String
    /** "Mein Standort" for local news, but no town could be named for the position. */
    val locationNoTown: String

    companion object {
        fun of(lang: AppLanguage): RegionStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : RegionStrings {
        override val appLanguage = "App-Sprache"
        override val appLanguageHint = "Menüs und Texte der App – gilt sofort."
        override val followPhone = "Wie das Handy"
        override fun followPhoneWith(language: String) = "Wie das Handy · $language"

        override val newsRegion = "Nachrichten-Region"
        override val newsLanguage = "Nachrichten-Sprache"
        override val newsEditionHint = "Worüber deine Schlagzeilen berichten und in welcher Sprache – beides unabhängig voneinander und von der App-Sprache."
        override fun newsSearchedHint(country: String, language: String) = "$country auf $language: Dafür hat Google News keine eigene Ausgabe. Die App sucht stattdessen Meldungen über das Land in dieser Sprache."
        override val change = "Ändern"
        override val newsRegionTitle = "Aus welchem Land sollen deine Nachrichten kommen?"
        override val newsRegionSearch = "Land suchen"
        override val newsRegionSuggested = "Vorschläge"
        override val newsRegionAll = "Alle Länder"
        override fun newsRegionNone(query: String) = "Kein Land für „$query“."
        override val newsLanguageTitle = "In welcher Sprache möchtest du Nachrichten lesen?"
        override fun newsLanguageOwn(country: String) = "$country · eigene Ausgabe"
        override val newsLanguageOther = "Weitere Sprachen"
        override val newsLanguageOtherHint = "Dafür gibt es keine eigene Ausgabe – die App sucht Meldungen über das Land in der gewählten Sprache."
        override val selected = "Ausgewählt"
        override val clearSearch = "Suche leeren"
        override fun editionDescription(country: String, language: String) = "$country · $language"

        override val region = "Bundesland"
        override val regionHint = "Für das Thema „Region“: die öffentlich-rechtlichen Sender deines Bundeslandes."
        override val regionDialogTitle = "Dein Bundesland"
        override val regionDialogText = "Regionale Nachrichten kommen von den öffentlich-rechtlichen Sendern deines Bundeslandes."

        override val localNews = "Lokale Nachrichten"
        override val localNewsHint = "Folge Orten überall auf der Welt. Ihre Nachrichten liest du in deiner Nachrichten-Sprache, auch im Ausland. Gibt es in ihr zu wenig über einen Ort, kommen Meldungen in der Sprache seines Landes dazu."
        override val localEmpty = "Noch kein Ort – zum Beispiel deine Stadt oder Washington, D.C."
        override val addPlace = "Ort hinzufügen"
        override fun removePlace(name: String) = "$name entfernen"
        override fun localEdition(country: String, language: String) = "Nachrichten: $country · $language"
        override val notInFeed = "Gerade nicht in deinem Feed"
        override val follow = "Folgen"

        override val placeHome = "Vor der Tür"
        override val placeDay = "Tagsüber"
        override val changePlace = "Ort ändern"
        override val weatherUnavailable = "Wetter gerade nicht erreichbar"
        override val lastUpdated = "zuletzt"
        override val maxToday = "max. heute"
        override val maxTomorrow = "max. morgen"
        override val dayWindow = "Zeitfenster"
        override fun dayWindowFor(place: String) = "Für den Tagesverlauf in $place"
        override val dayWindowDescription = "Zeitfenster für den Tagesverlauf"

        override val pickerHomeQuestion = "Wo bist du morgens?"
        override val pickerDayQuestion = "Wo verbringst du den Tag?"
        override val pickerLocalQuestion = "Welchem Ort willst du folgen?"
        override val pickerLocalHint = "Überall auf der Welt – in deiner Nachrichten-Sprache."
        override val searchPlaceholder = "Ort suchen, z. B. Leipzig"
        override val myLocation = "Mein Standort"
        override val locating = "Suche Standort …"
        override val currentPlace = "Aktuell:"
        override val quickPicks = "Schnell ausgewählt"
        override val searchFailed = "Suche gerade nicht möglich – bist du online?"
        override fun nothingFound(query: String) = "Nichts gefunden für „$query“."
        override val select = "Auswählen"
        override val locationNoPermission = "Ohne Standortfreigabe geht das nicht – suche den Ort einfach oben."
        override val locationUnavailable = "Der Standort ließ sich gerade nicht bestimmen. Ist er im System eingeschaltet?"
        override val locationNoTown = "Zu deinem Standort fand sich gerade kein Ortsname – suche den Ort einfach oben."
    }

    private object En : RegionStrings {
        override val appLanguage = "App language"
        override val appLanguageHint = "Menus and texts of the app – applies right away."
        override val followPhone = "Same as phone"
        override fun followPhoneWith(language: String) = "Same as phone · $language"

        override val newsRegion = "News region"
        override val newsLanguage = "News language"
        override val newsEditionHint = "What your headlines cover and in which language – each independent of the other and of the app language."
        override fun newsSearchedHint(country: String, language: String) = "$country in $language: Google News has no edition of its own for this. The app searches for stories about the country in this language instead."
        override val change = "Change"
        override val newsRegionTitle = "Which country should your news come from?"
        override val newsRegionSearch = "Search country"
        override val newsRegionSuggested = "Suggestions"
        override val newsRegionAll = "All countries"
        override fun newsRegionNone(query: String) = "No country for “$query”."
        override val newsLanguageTitle = "Which language do you want to read news in?"
        override fun newsLanguageOwn(country: String) = "$country · own edition"
        override val newsLanguageOther = "More languages"
        override val newsLanguageOtherHint = "There is no edition of its own for these – the app searches for stories about the country in the language you pick."
        override val selected = "Selected"
        override val clearSearch = "Clear search"
        override fun editionDescription(country: String, language: String) = "$country · $language"

        override val region = "German state"
        override val regionHint = "For the “Region” topic: the public broadcasters of your German state."
        override val regionDialogTitle = "Your German state"
        override val regionDialogText = "Regional news comes from the public broadcasters of your German state."

        override val localNews = "Local news"
        override val localNewsHint = "Follow places anywhere in the world. You read their news in your news language, abroad too. If there is too little about a place in it, stories in its country's language are added."
        override val localEmpty = "No place yet – your town, for example, or Washington, D.C."
        override val addPlace = "Add place"
        override fun removePlace(name: String) = "Remove $name"
        override fun localEdition(country: String, language: String) = "News: $country · $language"
        override val notInFeed = "Not in your feed right now"
        override val follow = "Follow"

        override val placeHome = "Outside"
        override val placeDay = "During the day"
        override val changePlace = "Change place"
        override val weatherUnavailable = "Weather unavailable right now"
        override val lastUpdated = "last update"
        override val maxToday = "high today"
        override val maxTomorrow = "high tomorrow"
        override val dayWindow = "Time window"
        override fun dayWindowFor(place: String) = "For the day ahead in $place"
        override val dayWindowDescription = "Time window for the day ahead"

        override val pickerHomeQuestion = "Where are you in the morning?"
        override val pickerDayQuestion = "Where do you spend the day?"
        override val pickerLocalQuestion = "Which place do you want to follow?"
        override val pickerLocalHint = "Anywhere in the world – in your news language."
        override val searchPlaceholder = "Search a place, e.g. Boston"
        override val myLocation = "My location"
        override val locating = "Finding location …"
        override val currentPlace = "Current:"
        override val quickPicks = "Quick picks"
        override val searchFailed = "Search isn't possible right now – are you online?"
        override fun nothingFound(query: String) = "Nothing found for “$query”."
        override val select = "Select"
        override val locationNoPermission = "That needs location access – just search for the place above."
        override val locationUnavailable = "Your location couldn't be determined. Is it switched on in the system?"
        override val locationNoTown = "No town name could be found for your location – just search for the place above."
    }

    private object Fr : RegionStrings {
        override val appLanguage = "Langue de l'appli"
        override val appLanguageHint = "Menus et textes de l'appli – s'applique tout de suite."
        override val followPhone = "Comme le téléphone"
        override fun followPhoneWith(language: String) = "Comme le téléphone · $language"

        override val newsRegion = "Région des actualités"
        override val newsLanguage = "Langue des actualités"
        override val newsEditionHint = "De quel pays parlent tes titres et dans quelle langue – deux choix indépendants l'un de l'autre et de la langue de l'appli."
        override fun newsSearchedHint(country: String, language: String) = "$country en $language : Google Actualités n'a pas d'édition propre pour cela. L'appli cherche à la place des articles sur ce pays dans cette langue."
        override val change = "Changer"
        override val newsRegionTitle = "De quel pays doivent venir tes actualités ?"
        override val newsRegionSearch = "Chercher un pays"
        override val newsRegionSuggested = "Suggestions"
        override val newsRegionAll = "Tous les pays"
        override fun newsRegionNone(query: String) = "Aucun pays pour « $query »."
        override val newsLanguageTitle = "Dans quelle langue veux-tu lire les actualités ?"
        override fun newsLanguageOwn(country: String) = "$country · édition propre"
        override val newsLanguageOther = "Autres langues"
        override val newsLanguageOtherHint = "Il n'existe pas d'édition propre pour celles-ci – l'appli cherche des articles sur le pays dans la langue choisie."
        override val selected = "Sélectionné"
        override val clearSearch = "Effacer la recherche"
        override fun editionDescription(country: String, language: String) = "$country · $language"

        override val region = "Land allemand"
        override val regionHint = "Pour le thème « Région » : les chaînes publiques de ton Land."
        override val regionDialogTitle = "Ton Land"
        override val regionDialogText = "L'actualité régionale vient des chaînes publiques de ton Land allemand."

        override val localNews = "Actualité locale"
        override val localNewsHint = "Suis des lieux partout dans le monde. Tu lis leur actualité dans ta langue des actualités, à l'étranger aussi. S'il y a trop peu d'articles sur un lieu dans cette langue, ceux dans la langue de son pays s'y ajoutent."
        override val localEmpty = "Aucun lieu pour l'instant – ta ville, par exemple, ou Washington, D.C."
        override val addPlace = "Ajouter un lieu"
        override fun removePlace(name: String) = "Retirer $name"
        override fun localEdition(country: String, language: String) = "Actualités : $country · $language"
        override val notInFeed = "Pas dans ton fil pour l'instant"
        override val follow = "Suivre"

        override val placeHome = "Dehors"
        override val placeDay = "En journée"
        override val changePlace = "Changer de lieu"
        override val weatherUnavailable = "Météo indisponible pour le moment"
        override val lastUpdated = "dernière mise à jour"
        override val maxToday = "max. aujourd'hui"
        override val maxTomorrow = "max. demain"
        override val dayWindow = "Plage horaire"
        override fun dayWindowFor(place: String) = "Pour le déroulé de la journée à $place"
        override val dayWindowDescription = "Plage horaire du déroulé de la journée"

        override val pickerHomeQuestion = "Où es-tu le matin ?"
        override val pickerDayQuestion = "Où passes-tu la journée ?"
        override val pickerLocalQuestion = "Quel lieu veux-tu suivre ?"
        override val pickerLocalHint = "Partout dans le monde – dans ta langue des actualités."
        override val searchPlaceholder = "Chercher un lieu, p. ex. Lyon"
        override val myLocation = "Ma position"
        override val locating = "Recherche de la position …"
        override val currentPlace = "Actuel :"
        override val quickPicks = "Choix rapide"
        override val searchFailed = "Recherche impossible pour le moment – es-tu en ligne ?"
        override fun nothingFound(query: String) = "Rien trouvé pour « $query »."
        override val select = "Choisir"
        override val locationNoPermission = "Sans accès à la position, ça ne marche pas – cherche simplement le lieu ci-dessus."
        override val locationUnavailable = "Impossible de déterminer ta position. Est-elle activée dans le système ?"
        override val locationNoTown = "Aucun nom de lieu trouvé pour ta position – cherche simplement le lieu ci-dessus."
    }

    private object Es : RegionStrings {
        override val appLanguage = "Idioma de la app"
        override val appLanguageHint = "Menús y textos de la app – se aplica al instante."
        override val followPhone = "Como el móvil"
        override fun followPhoneWith(language: String) = "Como el móvil · $language"

        override val newsRegion = "Región de las noticias"
        override val newsLanguage = "Idioma de las noticias"
        override val newsEditionHint = "De qué país hablan tus titulares y en qué idioma – dos elecciones independientes entre sí y del idioma de la app."
        override fun newsSearchedHint(country: String, language: String) = "$country en $language: Google Noticias no tiene una edición propia para esto. La app busca en su lugar noticias sobre el país en este idioma."
        override val change = "Cambiar"
        override val newsRegionTitle = "¿De qué país quieres tus noticias?"
        override val newsRegionSearch = "Buscar país"
        override val newsRegionSuggested = "Sugerencias"
        override val newsRegionAll = "Todos los países"
        override fun newsRegionNone(query: String) = "Ningún país para «$query»."
        override val newsLanguageTitle = "¿En qué idioma quieres leer las noticias?"
        override fun newsLanguageOwn(country: String) = "$country · edición propia"
        override val newsLanguageOther = "Más idiomas"
        override val newsLanguageOtherHint = "Para estos no hay edición propia – la app busca noticias sobre el país en el idioma que elijas."
        override val selected = "Seleccionado"
        override val clearSearch = "Borrar búsqueda"
        override fun editionDescription(country: String, language: String) = "$country · $language"

        override val region = "Estado federado alemán"
        override val regionHint = "Para el tema «Región»: las cadenas públicas de tu estado federado."
        override val regionDialogTitle = "Tu estado federado"
        override val regionDialogText = "Las noticias regionales vienen de las cadenas públicas de tu estado federado alemán."

        override val localNews = "Noticias locales"
        override val localNewsHint = "Sigue lugares de todo el mundo. Lees sus noticias en tu idioma de las noticias, también en el extranjero. Si en él hay muy poco sobre un lugar, se añaden noticias en el idioma de su país."
        override val localEmpty = "Aún ningún lugar – tu ciudad, por ejemplo, o Washington, D.C."
        override val addPlace = "Añadir lugar"
        override fun removePlace(name: String) = "Quitar $name"
        override fun localEdition(country: String, language: String) = "Noticias: $country · $language"
        override val notInFeed = "Ahora no está en tu feed"
        override val follow = "Seguir"

        override val placeHome = "Afuera"
        override val placeDay = "Durante el día"
        override val changePlace = "Cambiar lugar"
        override val weatherUnavailable = "El tiempo no está disponible ahora"
        override val lastUpdated = "última actualización"
        override val maxToday = "máx. hoy"
        override val maxTomorrow = "máx. mañana"
        override val dayWindow = "Franja horaria"
        override fun dayWindowFor(place: String) = "Para el día en $place"
        override val dayWindowDescription = "Franja horaria del día"

        override val pickerHomeQuestion = "¿Dónde estás por la mañana?"
        override val pickerDayQuestion = "¿Dónde pasas el día?"
        override val pickerLocalQuestion = "¿Qué lugar quieres seguir?"
        override val pickerLocalHint = "En cualquier parte del mundo – en tu idioma de las noticias."
        override val searchPlaceholder = "Buscar un lugar, p. ej. Sevilla"
        override val myLocation = "Mi ubicación"
        override val locating = "Buscando ubicación …"
        override val currentPlace = "Ahora:"
        override val quickPicks = "Selección rápida"
        override val searchFailed = "La búsqueda no es posible ahora – ¿tienes conexión?"
        override fun nothingFound(query: String) = "No se encontró nada para «$query»."
        override val select = "Elegir"
        override val locationNoPermission = "Sin acceso a la ubicación no funciona – busca el lugar arriba."
        override val locationUnavailable = "No se pudo determinar tu ubicación. ¿Está activada en el sistema?"
        override val locationNoTown = "No se encontró el nombre de ningún lugar para tu ubicación – busca el lugar arriba."
    }

    private object It : RegionStrings {
        override val appLanguage = "Lingua dell'app"
        override val appLanguageHint = "Menu e testi dell'app – vale subito."
        override val followPhone = "Come il telefono"
        override fun followPhoneWith(language: String) = "Come il telefono · $language"

        override val newsRegion = "Regione delle notizie"
        override val newsLanguage = "Lingua delle notizie"
        override val newsEditionHint = "Di quale paese parlano i tuoi titoli e in quale lingua – due scelte indipendenti tra loro e dalla lingua dell'app."
        override fun newsSearchedHint(country: String, language: String) = "$country in $language: Google News non ha un'edizione propria per questa combinazione. L'app cerca invece notizie sul paese in questa lingua."
        override val change = "Cambia"
        override val newsRegionTitle = "Da quale paese vuoi le tue notizie?"
        override val newsRegionSearch = "Cerca un paese"
        override val newsRegionSuggested = "Suggerimenti"
        override val newsRegionAll = "Tutti i paesi"
        override fun newsRegionNone(query: String) = "Nessun paese per «$query»."
        override val newsLanguageTitle = "In quale lingua vuoi leggere le notizie?"
        override fun newsLanguageOwn(country: String) = "$country · edizione propria"
        override val newsLanguageOther = "Altre lingue"
        override val newsLanguageOtherHint = "Per queste non esiste un'edizione propria – l'app cerca notizie sul paese nella lingua che scegli."
        override val selected = "Selezionato"
        override val clearSearch = "Cancella ricerca"
        override fun editionDescription(country: String, language: String) = "$country · $language"

        override val region = "Land tedesco"
        override val regionHint = "Per il tema «Regione»: le emittenti pubbliche del tuo Land."
        override val regionDialogTitle = "Il tuo Land"
        override val regionDialogText = "Le notizie regionali arrivano dalle emittenti pubbliche del tuo Land tedesco."

        override val localNews = "Notizie locali"
        override val localNewsHint = "Segui luoghi in tutto il mondo. Leggi le loro notizie nella tua lingua delle notizie, anche all'estero. Se in quella lingua c'è troppo poco su un luogo, si aggiungono notizie nella lingua del suo paese."
        override val localEmpty = "Ancora nessun luogo – la tua città, per esempio, o Washington, D.C."
        override val addPlace = "Aggiungi luogo"
        override fun removePlace(name: String) = "Rimuovi $name"
        override fun localEdition(country: String, language: String) = "Notizie: $country · $language"
        override val notInFeed = "Al momento non è nel tuo feed"
        override val follow = "Segui"

        override val placeHome = "Fuori"
        override val placeDay = "Di giorno"
        override val changePlace = "Cambia luogo"
        override val weatherUnavailable = "Meteo non disponibile al momento"
        override val lastUpdated = "ultimo aggiornamento"
        override val maxToday = "max. oggi"
        override val maxTomorrow = "max. domani"
        override val dayWindow = "Fascia oraria"
        override fun dayWindowFor(place: String) = "Per l'andamento della giornata a $place"
        override val dayWindowDescription = "Fascia oraria per l'andamento della giornata"

        override val pickerHomeQuestion = "Dove sei al mattino?"
        override val pickerDayQuestion = "Dove passi la giornata?"
        override val pickerLocalQuestion = "Quale luogo vuoi seguire?"
        override val pickerLocalHint = "Ovunque nel mondo – nella tua lingua delle notizie."
        override val searchPlaceholder = "Cerca un luogo, ad es. Bologna"
        override val myLocation = "La mia posizione"
        override val locating = "Cerco la posizione …"
        override val currentPlace = "Attuale:"
        override val quickPicks = "Scelta rapida"
        override val searchFailed = "Ricerca non possibile al momento – sei online?"
        override fun nothingFound(query: String) = "Nessun risultato per «$query»."
        override val select = "Scegli"
        override val locationNoPermission = "Senza accesso alla posizione non funziona – cerca il luogo qui sopra."
        override val locationUnavailable = "Impossibile determinare la posizione. È attiva nel sistema?"
        override val locationNoTown = "Nessun nome di luogo trovato per la tua posizione – cerca il luogo qui sopra."
    }
}

@Composable
@ReadOnlyComposable
fun regionStrings(): RegionStrings = RegionStrings.of(LocalAppLanguage.current)

package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * "Sprache & Region" (app language, news edition, Bundesland, local news), the place cards with
 * the day window and the place picker. Country and language names of the editions come from the
 * platform's locale data in the interface language (`ui/settings/EditionLogic`).
 */
interface RegionStrings {
    // App language
    val appLanguage: String
    val appLanguageHint: String
    val followPhone: String
    /** "Wie das Handy · Deutsch" */
    fun followPhoneWith(language: String): String

    // News edition
    val newsEdition: String
    val newsEditionHint: String
    val change: String
    val editionSheetOverline: String
    val editionSheetTitle: String
    val editionSearch: String
    val editionSuggested: String
    val editionAll: String
    fun editionNone(query: String): String
    val selected: String
    val clearSearch: String
    /** "Deutschland · Deutsch" read out for one edition. */
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
    /** "Nachrichten aus den USA · English" under a followed place. */
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

        override val newsEdition = "Nachrichten aus"
        override val newsEditionHint = "Woher deine Schlagzeilen kommen – unabhängig von der App-Sprache."
        override val change = "Ändern"
        override val editionSheetOverline = "Nachrichten-Ausgabe"
        override val editionSheetTitle = "Woher sollen deine Nachrichten kommen?"
        override val editionSearch = "Land oder Sprache suchen"
        override val editionSuggested = "Vorschläge"
        override val editionAll = "Alle Ausgaben"
        override fun editionNone(query: String) = "Keine Ausgabe für „$query“."
        override val selected = "Ausgewählt"
        override val clearSearch = "Suche leeren"
        override fun editionDescription(country: String, language: String) = "$country · $language"

        override val region = "Bundesland"
        override val regionHint = "Für das Thema „Region“: die öffentlich-rechtlichen Sender deines Bundeslandes."
        override val regionDialogTitle = "Dein Bundesland"
        override val regionDialogText = "Regionale Nachrichten kommen von den öffentlich-rechtlichen Sendern deines Bundeslandes."

        override val localNews = "Lokale Nachrichten"
        override val localNewsHint = "Folge Orten überall auf der Welt. Ein Ort im Ausland bringt die Nachrichten seines Landes – Washington, D.C. etwa aus den USA, auf Englisch."
        override val localEmpty = "Noch kein Ort – zum Beispiel deine Stadt oder Washington, D.C."
        override val addPlace = "Ort hinzufügen"
        override fun removePlace(name: String) = "$name entfernen"
        override fun localEdition(country: String, language: String) = "Ausgabe: $country · $language"
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
        override val pickerLocalHint = "Überall auf der Welt – die Nachrichten kommen aus dem Land des Ortes."
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

        override val newsEdition = "News from"
        override val newsEditionHint = "Where your headlines come from – independent of the app language."
        override val change = "Change"
        override val editionSheetOverline = "News edition"
        override val editionSheetTitle = "Where should your news come from?"
        override val editionSearch = "Search country or language"
        override val editionSuggested = "Suggestions"
        override val editionAll = "All editions"
        override fun editionNone(query: String) = "No edition for “$query”."
        override val selected = "Selected"
        override val clearSearch = "Clear search"
        override fun editionDescription(country: String, language: String) = "$country · $language"

        override val region = "German state"
        override val regionHint = "For the “Region” topic: the public broadcasters of your German state."
        override val regionDialogTitle = "Your German state"
        override val regionDialogText = "Regional news comes from the public broadcasters of your German state."

        override val localNews = "Local news"
        override val localNewsHint = "Follow places anywhere in the world. A place abroad brings that country's news – Washington, D.C. from the US, in English."
        override val localEmpty = "No place yet – your town, for example, or Washington, D.C."
        override val addPlace = "Add place"
        override fun removePlace(name: String) = "Remove $name"
        override fun localEdition(country: String, language: String) = "Edition: $country · $language"
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
        override val pickerLocalHint = "Anywhere in the world – the news comes from the place's country."
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

        override val newsEdition = "Actualités de"
        override val newsEditionHint = "D'où viennent tes titres – indépendamment de la langue de l'appli."
        override val change = "Changer"
        override val editionSheetOverline = "Édition"
        override val editionSheetTitle = "D'où doivent venir tes actualités ?"
        override val editionSearch = "Chercher un pays ou une langue"
        override val editionSuggested = "Suggestions"
        override val editionAll = "Toutes les éditions"
        override fun editionNone(query: String) = "Aucune édition pour « $query »."
        override val selected = "Sélectionné"
        override val clearSearch = "Effacer la recherche"
        override fun editionDescription(country: String, language: String) = "$country · $language"

        override val region = "Land allemand"
        override val regionHint = "Pour le thème « Région » : les chaînes publiques de ton Land."
        override val regionDialogTitle = "Ton Land"
        override val regionDialogText = "L'actualité régionale vient des chaînes publiques de ton Land allemand."

        override val localNews = "Actualité locale"
        override val localNewsHint = "Suis des lieux partout dans le monde. Un lieu à l'étranger apporte l'actualité de son pays – Washington, D.C. depuis les États-Unis, en anglais."
        override val localEmpty = "Aucun lieu pour l'instant – ta ville, par exemple, ou Washington, D.C."
        override val addPlace = "Ajouter un lieu"
        override fun removePlace(name: String) = "Retirer $name"
        override fun localEdition(country: String, language: String) = "Édition : $country · $language"
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
        override val pickerLocalHint = "Partout dans le monde – l'actualité vient du pays du lieu."
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

        override val newsEdition = "Noticias de"
        override val newsEditionHint = "De dónde vienen tus titulares – sin depender del idioma de la app."
        override val change = "Cambiar"
        override val editionSheetOverline = "Edición"
        override val editionSheetTitle = "¿De dónde quieres tus noticias?"
        override val editionSearch = "Buscar país o idioma"
        override val editionSuggested = "Sugerencias"
        override val editionAll = "Todas las ediciones"
        override fun editionNone(query: String) = "Ninguna edición para «$query»."
        override val selected = "Seleccionada"
        override val clearSearch = "Borrar búsqueda"
        override fun editionDescription(country: String, language: String) = "$country · $language"

        override val region = "Estado federado alemán"
        override val regionHint = "Para el tema «Región»: las cadenas públicas de tu estado federado."
        override val regionDialogTitle = "Tu estado federado"
        override val regionDialogText = "Las noticias regionales vienen de las cadenas públicas de tu estado federado alemán."

        override val localNews = "Noticias locales"
        override val localNewsHint = "Sigue lugares de todo el mundo. Un lugar en el extranjero trae las noticias de su país – Washington, D.C. desde EE. UU., en inglés."
        override val localEmpty = "Aún ningún lugar – tu ciudad, por ejemplo, o Washington, D.C."
        override val addPlace = "Añadir lugar"
        override fun removePlace(name: String) = "Quitar $name"
        override fun localEdition(country: String, language: String) = "Edición: $country · $language"
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
        override val pickerLocalHint = "En cualquier parte del mundo – las noticias vienen del país del lugar."
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

        override val newsEdition = "Notizie da"
        override val newsEditionHint = "Da dove arrivano i tuoi titoli – indipendente dalla lingua dell'app."
        override val change = "Cambia"
        override val editionSheetOverline = "Edizione"
        override val editionSheetTitle = "Da dove vuoi le tue notizie?"
        override val editionSearch = "Cerca paese o lingua"
        override val editionSuggested = "Suggerimenti"
        override val editionAll = "Tutte le edizioni"
        override fun editionNone(query: String) = "Nessuna edizione per «$query»."
        override val selected = "Selezionata"
        override val clearSearch = "Cancella ricerca"
        override fun editionDescription(country: String, language: String) = "$country · $language"

        override val region = "Land tedesco"
        override val regionHint = "Per il tema «Regione»: le emittenti pubbliche del tuo Land."
        override val regionDialogTitle = "Il tuo Land"
        override val regionDialogText = "Le notizie regionali arrivano dalle emittenti pubbliche del tuo Land tedesco."

        override val localNews = "Notizie locali"
        override val localNewsHint = "Segui luoghi in tutto il mondo. Un luogo all'estero porta le notizie del suo paese – Washington, D.C. dagli Stati Uniti, in inglese."
        override val localEmpty = "Ancora nessun luogo – la tua città, per esempio, o Washington, D.C."
        override val addPlace = "Aggiungi luogo"
        override fun removePlace(name: String) = "Rimuovi $name"
        override fun localEdition(country: String, language: String) = "Edizione: $country · $language"
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
        override val pickerLocalHint = "Ovunque nel mondo – le notizie arrivano dal paese del luogo."
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

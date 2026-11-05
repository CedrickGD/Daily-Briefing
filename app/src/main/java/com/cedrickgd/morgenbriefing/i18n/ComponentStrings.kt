package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Texts of the shared building blocks in `ui/components` (weather hero, day curve, clothing
 * card, news section header and footer, error details, loading skeleton), of the glass page chrome
 * in `ui/glass` and of the link helpers in `util/Links.kt`.
 */
interface ComponentStrings {
    // Home weather hero ("Vor der Tür")
    /** Overline "VOR DER TÜR · BAD DÜBEN" (the caller upper-cases it). */
    fun outsideAt(place: String): String
    /** Screen-reader summary: "Vor der Tür in Bad Düben: 9°, gefühlt 8°, Nebel". */
    fun outsideDescription(place: String, temp: String, feels: String, condition: String): String
    fun wind(kmh: Int): String
    fun gusts(kmh: Int): String
    fun visibility(km: String): String
    fun sunrise(clock: String): String
    fun sunset(clock: String): String

    // Day curve
    /** "LEIPZIG · 10–18 UHR" (upper-cased by the caller), with tomorrow's prefix. */
    fun dayCurveTitle(place: String, startHour: Int, endHour: Int, tomorrow: Boolean): String
    val noHourlyValues: String
    val now: String
    /** Screen-reader summary of the curve; [points] are the hours already put into words. */
    fun chartDescription(place: String, startHour: Int, endHour: Int, tomorrow: Boolean, points: List<String>): String
    /** One hour of [chartDescription]: "15 Uhr 21°, Regen 60 Prozent". */
    fun chartPoint(hour: Int, temp: String, rainPercent: Int?): String

    // Clothing card
    val clothingTitle: String

    // News section header and page footer
    val yourNews: String
    val latest: String
    fun latestAsOf(clock: String): String
    val farewell: String

    // Loading skeleton
    val loadingBriefing: String

    // Error details
    val details: String
    val showDetails: String
    val hideDetails: String
    val expanded: String
    val collapsed: String
    val errorDetailsClip: String
    val detailsCopied: String
    val copy: String

    // Links
    val linkCannotOpen: String
    val shareChooser: String
    val shareUnavailable: String
    val noBrowser: String

    // Glass page chrome
    /** Screen-reader action of the tappable top bar ("double-tap to …"). */
    val scrollToTop: String

    companion object {
        fun of(lang: AppLanguage): ComponentStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : ComponentStrings {
        override fun outsideAt(place: String) = "Vor der Tür · $place"
        override fun outsideDescription(place: String, temp: String, feels: String, condition: String) =
            "Vor der Tür in $place: $temp, gefühlt $feels, $condition"
        override fun wind(kmh: Int) = "Wind $kmh km/h"
        override fun gusts(kmh: Int) = "Böen $kmh"
        override fun visibility(km: String) = "Sicht $km km"
        override fun sunrise(clock: String) = "Sonnenaufgang $clock"
        override fun sunset(clock: String) = "Sonnenuntergang $clock"
        override fun dayCurveTitle(place: String, startHour: Int, endHour: Int, tomorrow: Boolean) =
            (if (tomorrow) "Morgen · " else "") + "$place · $startHour–$endHour Uhr"
        override val noHourlyValues = "Für dieses Zeitfenster gibt es gerade keine Stundenwerte."
        override val now = "jetzt"
        override fun chartDescription(place: String, startHour: Int, endHour: Int, tomorrow: Boolean, points: List<String>) =
            "Temperaturverlauf ${if (tomorrow) "morgen " else ""}in $place von $startHour bis $endHour Uhr: ${points.joinToString(", ")}."
        override fun chartPoint(hour: Int, temp: String, rainPercent: Int?) =
            "$hour Uhr $temp" + (rainPercent?.let { ", Regen $it Prozent" } ?: "")
        override val clothingTitle = "Anziehen"
        override val yourNews = "Deine Nachrichten"
        override val latest = "Aktuell"
        override fun latestAsOf(clock: String) = "Aktuell · Stand $clock"
        override val farewell = "Bis später"
        override val loadingBriefing = "Dein Briefing wird geladen"
        override val details = "Details"
        override val showDetails = "Details zeigen"
        override val hideDetails = "Details ausblenden"
        override val expanded = "aufgeklappt"
        override val collapsed = "zugeklappt"
        override val errorDetailsClip = "Fehlerdetails"
        override val detailsCopied = "Details kopiert"
        override val copy = "Kopieren"
        override val linkCannotOpen = "Dieser Link lässt sich nicht öffnen."
        override val shareChooser = "Meldung teilen"
        override val shareUnavailable = "Teilen ist auf diesem Gerät gerade nicht möglich."
        override val noBrowser = "Kein Browser gefunden, der den Link öffnen kann."
        override val scrollToTop = "Nach oben scrollen"
    }

    private object En : ComponentStrings {
        private fun at(hour: Int) = FormatStrings.of(AppLanguage.EN).hour(hour)
        override fun outsideAt(place: String) = "Outside · $place"
        override fun outsideDescription(place: String, temp: String, feels: String, condition: String) =
            "Outside in $place: $temp, feels like $feels, $condition"
        override fun wind(kmh: Int) = "Wind $kmh km/h"
        override fun gusts(kmh: Int) = "gusts $kmh"
        override fun visibility(km: String) = "Visibility $km km"
        override fun sunrise(clock: String) = "Sunrise $clock"
        override fun sunset(clock: String) = "Sunset $clock"
        override fun dayCurveTitle(place: String, startHour: Int, endHour: Int, tomorrow: Boolean) =
            (if (tomorrow) "Tomorrow · " else "") + "$place · ${FormatStrings.of(AppLanguage.EN).hourRange(startHour, endHour)}"
        override val noHourlyValues = "No hourly values for this time window right now."
        override val now = "now"
        override fun chartDescription(place: String, startHour: Int, endHour: Int, tomorrow: Boolean, points: List<String>) =
            "Temperatures ${if (tomorrow) "tomorrow " else ""}in $place from ${at(startHour)} to ${at(endHour)}: ${points.joinToString(", ")}."
        override fun chartPoint(hour: Int, temp: String, rainPercent: Int?) =
            "${at(hour)} $temp" + (rainPercent?.let { ", rain $it percent" } ?: "")
        override val clothingTitle = "What to wear"
        override val yourNews = "Your news"
        override val latest = "Latest"
        override fun latestAsOf(clock: String) = "Latest · as of $clock"
        override val farewell = "See you later"
        override val loadingBriefing = "Loading your briefing"
        override val details = "Details"
        override val showDetails = "Show details"
        override val hideDetails = "Hide details"
        override val expanded = "expanded"
        override val collapsed = "collapsed"
        override val errorDetailsClip = "Error details"
        override val detailsCopied = "Details copied"
        override val copy = "Copy"
        override val linkCannotOpen = "This link can't be opened."
        override val shareChooser = "Share story"
        override val shareUnavailable = "Sharing isn't possible on this device right now."
        override val noBrowser = "No browser found that can open the link."
        override val scrollToTop = "Scroll to top"
    }

    private object Fr : ComponentStrings {
        override fun outsideAt(place: String) = "Dehors · $place"
        override fun outsideDescription(place: String, temp: String, feels: String, condition: String) =
            "Dehors à $place : $temp, ressenti $feels, $condition"
        override fun wind(kmh: Int) = "Vent $kmh km/h"
        override fun gusts(kmh: Int) = "rafales $kmh"
        override fun visibility(km: String) = "Visibilité $km km"
        override fun sunrise(clock: String) = "Lever du soleil $clock"
        override fun sunset(clock: String) = "Coucher du soleil $clock"
        override fun dayCurveTitle(place: String, startHour: Int, endHour: Int, tomorrow: Boolean) =
            (if (tomorrow) "Demain · " else "") + "$place · $startHour–$endHour h"
        override val noHourlyValues = "Pas de valeurs horaires pour ce créneau pour le moment."
        override val now = "maint."
        override fun chartDescription(place: String, startHour: Int, endHour: Int, tomorrow: Boolean, points: List<String>) =
            "Températures ${if (tomorrow) "de demain " else ""}à $place de $startHour h à $endHour h : ${points.joinToString(", ")}."
        override fun chartPoint(hour: Int, temp: String, rainPercent: Int?) =
            "$hour h $temp" + (rainPercent?.let { ", pluie $it pour cent" } ?: "")
        override val clothingTitle = "Quoi porter"
        override val yourNews = "Tes infos"
        override val latest = "À la une"
        override fun latestAsOf(clock: String) = "À la une · mis à jour à $clock"
        override val farewell = "À plus tard"
        override val loadingBriefing = "Chargement de ton briefing"
        override val details = "Détails"
        override val showDetails = "Afficher les détails"
        override val hideDetails = "Masquer les détails"
        override val expanded = "déplié"
        override val collapsed = "replié"
        override val errorDetailsClip = "Détails de l'erreur"
        override val detailsCopied = "Détails copiés"
        override val copy = "Copier"
        override val linkCannotOpen = "Impossible d'ouvrir ce lien."
        override val shareChooser = "Partager l'article"
        override val shareUnavailable = "Le partage n'est pas possible sur cet appareil pour le moment."
        override val noBrowser = "Aucun navigateur trouvé pour ouvrir le lien."
        override val scrollToTop = "Revenir en haut"
    }

    private object Es : ComponentStrings {
        override fun outsideAt(place: String) = "Fuera · $place"
        override fun outsideDescription(place: String, temp: String, feels: String, condition: String) =
            "Fuera en $place: $temp, sensación $feels, $condition"
        override fun wind(kmh: Int) = "Viento $kmh km/h"
        override fun gusts(kmh: Int) = "rachas $kmh"
        override fun visibility(km: String) = "Visibilidad $km km"
        override fun sunrise(clock: String) = "Amanecer $clock"
        override fun sunset(clock: String) = "Atardecer $clock"
        override fun dayCurveTitle(place: String, startHour: Int, endHour: Int, tomorrow: Boolean) =
            (if (tomorrow) "Mañana · " else "") + "$place · $startHour–$endHour h"
        override val noHourlyValues = "Ahora mismo no hay valores por horas para esta franja."
        override val now = "ahora"
        override fun chartDescription(place: String, startHour: Int, endHour: Int, tomorrow: Boolean, points: List<String>) =
            "Temperaturas ${if (tomorrow) "de mañana " else ""}en $place de $startHour a $endHour h: ${points.joinToString(", ")}."
        override fun chartPoint(hour: Int, temp: String, rainPercent: Int?) =
            "$hour h $temp" + (rainPercent?.let { ", lluvia $it por ciento" } ?: "")
        override val clothingTitle = "Qué ponerse"
        override val yourNews = "Tus noticias"
        override val latest = "Lo último"
        override fun latestAsOf(clock: String) = "Lo último · a las $clock"
        override val farewell = "Hasta luego"
        override val loadingBriefing = "Cargando tu briefing"
        override val details = "Detalles"
        override val showDetails = "Mostrar detalles"
        override val hideDetails = "Ocultar detalles"
        override val expanded = "desplegado"
        override val collapsed = "plegado"
        override val errorDetailsClip = "Detalles del error"
        override val detailsCopied = "Detalles copiados"
        override val copy = "Copiar"
        override val linkCannotOpen = "Este enlace no se puede abrir."
        override val shareChooser = "Compartir noticia"
        override val shareUnavailable = "Ahora mismo no se puede compartir en este dispositivo."
        override val noBrowser = "No se encontró ningún navegador que pueda abrir el enlace."
        override val scrollToTop = "Volver arriba"
    }

    private object It : ComponentStrings {
        override fun outsideAt(place: String) = "Fuori · $place"
        override fun outsideDescription(place: String, temp: String, feels: String, condition: String) =
            "Fuori a $place: $temp, percepita $feels, $condition"
        override fun wind(kmh: Int) = "Vento $kmh km/h"
        override fun gusts(kmh: Int) = "raffiche $kmh"
        override fun visibility(km: String) = "Visibilità $km km"
        override fun sunrise(clock: String) = "Alba $clock"
        override fun sunset(clock: String) = "Tramonto $clock"
        override fun dayCurveTitle(place: String, startHour: Int, endHour: Int, tomorrow: Boolean) =
            (if (tomorrow) "Domani · " else "") + "$place · $startHour–$endHour"
        override val noHourlyValues = "Al momento non ci sono valori orari per questa fascia."
        override val now = "ora"
        override fun chartDescription(place: String, startHour: Int, endHour: Int, tomorrow: Boolean, points: List<String>) =
            "Temperature ${if (tomorrow) "di domani " else ""}a $place dalle $startHour alle $endHour: ${points.joinToString(", ")}."
        override fun chartPoint(hour: Int, temp: String, rainPercent: Int?) =
            "ore $hour $temp" + (rainPercent?.let { ", pioggia $it per cento" } ?: "")
        override val clothingTitle = "Cosa mettere"
        override val yourNews = "Le tue notizie"
        override val latest = "In primo piano"
        override fun latestAsOf(clock: String) = "In primo piano · alle $clock"
        override val farewell = "A più tardi"
        override val loadingBriefing = "Caricamento del tuo briefing"
        override val details = "Dettagli"
        override val showDetails = "Mostra dettagli"
        override val hideDetails = "Nascondi dettagli"
        override val expanded = "espanso"
        override val collapsed = "compresso"
        override val errorDetailsClip = "Dettagli dell'errore"
        override val detailsCopied = "Dettagli copiati"
        override val copy = "Copia"
        override val linkCannotOpen = "Impossibile aprire questo link."
        override val shareChooser = "Condividi notizia"
        override val shareUnavailable = "Al momento non è possibile condividere su questo dispositivo."
        override val noBrowser = "Nessun browser trovato per aprire il link."
        override val scrollToTop = "Torna su"
    }
}

@Composable
@ReadOnlyComposable
fun componentStrings(): ComponentStrings = ComponentStrings.of(LocalAppLanguage.current)

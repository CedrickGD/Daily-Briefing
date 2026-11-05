package com.cedrickgd.morgenbriefing.i18n

import com.cedrickgd.morgenbriefing.domain.Condition

/*
 * The weather texts of WeatherInsights (headline, home note, day summary, warnings, morning
 * notification) per language. WeatherInsights decides WHAT to say from the weather facts; these
 * objects decide HOW each language says it, so word order, articles, gender and the way hours
 * are written ("ab 15 Uhr", "from 3 pm", "dès 15 h", "desde las 15 h", "dalle 15") stay natural.
 *
 * Conventions:
 *  - Fragments that may stand mid-sentence start lowercase (except German nouns); the caller
 *    capitalises a fragment that starts a sentence.
 *  - `t`, `temp` and `feels` are formatted temperatures ("21°"), `hour` a full hour 0–23.
 *  - An hour that may be unknown (`Int?`) reads as "later" when null; 12 and 13 read as "noon".
 */

/** Links the start of the morning to the rest of the day: "Nebel zum Start, dann …". */
enum class Connector { THEN, LATER, AFTERWARDS, ALSO }

/** The headline's base on a thundery day, chosen by the day's highest temperature. */
enum class ThunderBase { HOT, SULTRY, CHANGEABLE }

/** What the hours before a later rain look like. */
enum class BeforeRain { FRIENDLY, DRY, GREY }

/** The kinds of a dry day's headline. */
enum class DryDay {
    HOT, SUMMER_CLEAR, SUMMER, GOLDEN_OCTOBER, GOLDEN_AUTUMN, SPRING, SUNNY_COLD, SUNNY_COOL, SUNNY,
    PERMAFROST, FOG, FOG_AGAIN, OVERCAST, CHANGEABLE, MIXED,
}

/** The sky of a dry home forecast. */
enum class DrySky { FOG, GREY, SUNNY, PLAIN }

/** Low visibility in running text ("gut 1 km"); [ABOUT_METERS] carries the rounded metres. */
enum class VisibilityBand { UNDER_100_M, ABOUT_METERS, JUST_OVER_1_KM, ABOUT_1_5_KM, NEARLY_2_KM }

/**
 * Headline candidates for a dry day: [after] follow "<start>, <connector> " (lowercase), [alone]
 * stand on their own. The first entry of [alone] is picked from [aloneChoice] by the day.
 */
class DryPhrases(val after: List<String>, val aloneChoice: List<String>, val aloneRest: List<String>)

interface WeatherStrings {
    // ---- Headline ----------------------------------------------------------------------------
    val tomorrowPrefix: String
    val headlineFallback: String
    fun connector(connector: Connector): String
    val iceStart: String
    val fogStarts: List<String>
    val snowStart: String
    val wetStart: String
    val icyStart: String
    val frostyStart: String
    fun thunderBase(base: ThunderBase, t: String): String
    fun thunderFrom(hour: Int): String
    fun thunderUpTo(t: String): String
    fun thunderyDay(t: String): String
    fun thunderyAt(t: String): String
    val freezingRainPhrase: String
    val freezingRainSlippery: String
    val freezingRainAlert: String
    fun blowing(wintry: Boolean): String
    fun blowingHood(wintry: Boolean): String
    fun blowingAt(wintry: Boolean, t: String): String
    fun beforeRain(before: BeforeRain): String
    fun beforeRainAt(before: BeforeRain, t: String): String
    fun precipFrom(kind: Condition, hour: Int): String
    fun precipThenDry(kind: Condition, dryHour: Int): String
    fun dryFrom(hour: Int): String
    fun precipUntilDryHeadline(kind: Condition, dryHour: Int, t: String?): String
    fun precipAt(kind: Condition, t: String): String
    fun wetDay(kind: Condition, t: String): List<String>
    /** The precipitation noun, as it reads mid-sentence ("Regen", "rain"). */
    fun precipName(kind: Condition): String
    fun dry(day: DryDay, t: String): DryPhrases
    fun isolated(snow: Boolean): String

    // ---- Home note ---------------------------------------------------------------------------
    /** "Noch dunkel, Sonnenaufgang 07:18" ([sunrise] = "07:18") or "Noch dunkel" (null). */
    fun stillDark(sunrise: String?): String
    val alreadyDark: String
    /** It is wet right now; [dryHour] = the hour it dries up, null when it keeps going. */
    fun wetNow(kind: Condition, dryHour: Int?): String
    /** Dry now, wet soon: [hour] null = in a moment. */
    fun wetSoon(kind: Condition, hour: Int?): String
    fun dryNow(sky: DrySky): String
    val iceNowHazard: String
    val iceSoonHazard: String
    fun slipperyHazard(temp: String): String
    fun visibilityHazard(distance: String): String
    fun visibility(band: VisibilityBand, meters: Int): String
    val fogHazard: String
    fun gustHazard(kmh: Int): String
    fun frostHazard(temp: String): String

    // ---- Day summary -------------------------------------------------------------------------
    fun between(min: String, max: String): String
    fun rising(start: String, peak: String): String
    fun falling(start: String, end: String): String
    fun around(t: String): String
    val sunPureFirst: String
    val sunMuchFirst: String
    val sunPure: String
    val sunMuch: String
    fun sunFrom(hour: Int?, pure: Boolean): String
    fun sunThenClouds(hour: Int?): String
    val sunAndClouds: String
    val mostlyGrey: String
    val dullFoggy: String
    val changeable: String
    /** "Gewitter (mit Hagel) möglich", with [hour] "ab 16 Uhr …"; lowercase start. */
    fun thunderPossible(hail: Boolean, hour: Int?): String
    fun precipFromPop(kind: Condition, hour: Int, pop: Int): String
    fun precipUntilDry(kind: Condition, dryHour: Int): String
    fun precipMostOfDay(kind: Condition, pop: Int): String
    fun precipOnAndOff(kind: Condition, pop: Int): String
    val flakesPossible: String
    val showersPossible: String
    val dryWord: String
    val noRain: String
    fun gustsUpTo(kmh: Int): String
    val littleWind: String

    // ---- Warnings ----------------------------------------------------------------------------
    val titleBlackIce: String
    val titleSlippery: String
    val titleFog: String
    val titleStorm: String
    val titleGusts: String
    val titleThunder: String
    val titleHeavyRain: String
    val titleExtremeHeat: String
    val titleHeat: String
    fun titleUv(veryHigh: Boolean): String
    val freezingRainNow: String
    fun freezingRainFrom(hour: Int): String
    fun slipperyWet(temp: String, snow: Boolean): String
    fun hoarFrost(temp: String): String
    fun icyFrom(hour: Int, kind: Condition, temp: String): String
    fun fogVisibility(distance: String): String
    val fogPossible: String
    fun stormText(kmh: Int): String
    fun gustText(kmh: Int): String
    val thunderNow: String
    fun thunderWarning(hour: Int, hail: Boolean): String
    fun heavyRainAmount(hour: Int, mm: Int): String
    fun heavyRainBursts(hour: Int): String
    fun extremeHeat(temp: String): String
    fun heat(temp: String): String
    fun uv(index: Int): String

    // ---- Notification and labels -------------------------------------------------------------
    val noWeatherTitle: String
    val noWeatherText: String
    val noWeatherBigText: String
    fun notificationTitle(emoji: String, temp: String, place: String, feels: String): String
    /** "Leipzig 10–18 Uhr", "Leipzig morgen 10–18 Uhr". */
    fun window(place: String, tomorrow: Boolean, startHour: Int, endHour: Int): String
    fun shortAround(t: String): String
    fun thunderFromShort(hour: Int): String
    val thunderShort: String
    fun labelFrom(label: String, hour: Int): String
    fun labelUntil(label: String, hour: Int): String
    val noNews: String
    /** "gefühlt 8°" */
    fun feelsLike(feels: String): String

    /** Short name of a condition ("sonnig", "Nebel"); [isDay] turns "klar" into "sonnig". */
    fun condition(condition: Condition, isDay: Boolean): String
    val rainWord: String
    val snowWord: String
    val sleetWord: String

    companion object {
        fun of(lang: AppLanguage): WeatherStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    // =========================================================================================
    // Deutsch
    // =========================================================================================
    private object De : WeatherStrings {
        private fun from(hour: Int) = "ab $hour Uhr"

        /** "ab Mittag" for 12/13 Uhr, otherwise "ab 15 Uhr"; "später" when the hour is unknown. */
        private fun fromNoon(hour: Int?) = when (hour) {
            null -> "später"
            12, 13 -> "ab Mittag"
            else -> from(hour)
        }

        private fun noun(kind: Condition) = precipName(kind)

        override val tomorrowPrefix = "Morgen: "
        override val headlineFallback = "Dein Wetter für heute"
        override fun connector(connector: Connector) = when (connector) {
            Connector.THEN -> "dann"
            Connector.LATER -> "später"
            Connector.AFTERWARDS -> "danach"
            Connector.ALSO -> "dazu"
        }
        override val iceStart = "Glatteis am Morgen"
        override val fogStarts = listOf("Nebel zum Start", "Erst Nebel")
        override val snowStart = "Schnee zum Start"
        override val wetStart = "Nass zum Start"
        override val icyStart = "Eisiger Start"
        override val frostyStart = "Frostiger Start"
        override fun thunderBase(base: ThunderBase, t: String) = when (base) {
            ThunderBase.HOT -> "heiß bis $t"
            ThunderBase.SULTRY -> "schwül bis $t"
            ThunderBase.CHANGEABLE -> "wechselhaft bei $t"
        }
        override fun thunderFrom(hour: Int) = "${from(hour)} Gewitter"
        override fun thunderUpTo(t: String) = "Gewitter bei bis zu $t"
        override fun thunderyDay(t: String) = "Gewittriger Tag bei bis zu $t"
        override fun thunderyAt(t: String) = "Gewittrig bei $t"
        override val freezingRainPhrase = "gefrierender Regen"
        override val freezingRainSlippery = "Gefrierender Regen – heute wird's glatt"
        override val freezingRainAlert = "Achtung, Eisregen"
        override fun blowing(wintry: Boolean) = if (wintry) "Schneetreiben" else "windig und nass"
        override fun blowingHood(wintry: Boolean) =
            if (wintry) "Schneetreiben – heute lieber Kapuze" else "Windig und nass – heute lieber Kapuze"
        override fun blowingAt(wintry: Boolean, t: String) = if (wintry) "Schneetreiben bei $t" else "Windig und nass bei $t"
        override fun beforeRain(before: BeforeRain) = when (before) {
            BeforeRain.FRIENDLY -> "Erst freundlich"
            BeforeRain.DRY -> "Erst trocken"
            BeforeRain.GREY -> "Grauer Tag"
        }
        override fun beforeRainAt(before: BeforeRain, t: String) = "${beforeRain(before)} bei $t"
        override fun precipFrom(kind: Condition, hour: Int) = "${from(hour)} ${noun(kind)}"
        override fun precipThenDry(kind: Condition, dryHour: Int) = "${noun(kind)}, ${dryFrom(dryHour)}"
        override fun dryFrom(hour: Int) = "${from(hour)} trocken"
        override fun precipUntilDryHeadline(kind: Condition, dryHour: Int, t: String?) =
            "Erst ${noun(kind)}, ${dryFrom(dryHour)}" + (t?.let { " bei $it" } ?: "")
        override fun precipAt(kind: Condition, t: String) = "${noun(kind)} bei $t"
        override fun wetDay(kind: Condition, t: String) = when (kind) {
            Condition.DRIZZLE -> listOf("Nieselgrau bei höchstens $t", "Trüb mit Niesel bei $t")
            Condition.SHOWERS -> listOf("Schauerwetter bei bis zu $t", "Immer wieder Schauer bei $t")
            Condition.HEAVY_RAIN -> listOf("Kräftiger Regen bei höchstens $t", "Sehr nass bei $t")
            Condition.SNOW -> listOf("Schneetag bei höchstens $t", "Winterlich mit Schnee bei $t")
            Condition.SNOW_SHOWERS -> listOf("Immer wieder Schneeschauer bei $t", "Schneeschauer bei $t")
            else -> listOf("Regnerischer Tag bei höchstens $t", "Nass und grau bei höchstens $t")
        }
        override fun precipName(kind: Condition) = when (kind) {
            Condition.DRIZZLE -> "Nieselregen"
            Condition.SHOWERS -> "Schauer"
            Condition.HEAVY_RAIN -> "kräftiger Regen"
            Condition.FREEZING_RAIN -> "Eisregen"
            Condition.SNOW -> "Schnee"
            Condition.SNOW_SHOWERS -> "Schneeschauer"
            Condition.THUNDER -> "Gewitter"
            else -> "Regen"
        }
        override fun dry(day: DryDay, t: String) = when (day) {
            DryDay.HOT -> DryPhrases(listOf("heiß bis $t"), listOf("Hitze bis $t – ab in den Schatten"), listOf("Heiß bei bis zu $t"))
            DryDay.SUMMER_CLEAR -> DryPhrases(
                listOf("Sommer pur mit $t", "sommerlich bei $t"),
                listOf("Sommer pur: bis $t und kaum eine Wolke"), listOf("Sommerlich bei bis zu $t"),
            )
            DryDay.SUMMER -> DryPhrases(
                listOf("Sommer pur mit $t", "sommerlich bei $t"),
                listOf("Sommerlich mit viel Sonne bei $t"), listOf("Sommerlich bei bis zu $t"),
            )
            DryDay.GOLDEN_OCTOBER -> golden("goldener Oktober", t)
            DryDay.GOLDEN_AUTUMN -> golden("goldener Herbst", t)
            DryDay.SPRING -> DryPhrases(listOf("frühlingshaft bei $t"), listOf("Frühling pur: Sonne bei bis zu $t"), listOf("Frühlingshaft bei $t"))
            DryDay.SUNNY_COLD -> DryPhrases(listOf("sonnig, aber kalt bei $t"), listOf("Kalt, aber sonnig bei höchstens $t"), listOf("Sonnig, aber kalt bei $t"))
            DryDay.SUNNY_COOL -> DryPhrases(
                listOf("freundlich bei $t"),
                listOf("Freundlich und sonnig bei bis zu $t", "Viel Sonne bei kühlen $t"), listOf("Freundlich bei $t"),
            )
            DryDay.SUNNY -> DryPhrases(
                listOf("sonnig bei $t", "viel Sonne bei $t"),
                listOf("Viel Sonne bei bis zu $t", "Sonnig und angenehm bei $t"), listOf("Sonnig bei $t"),
            )
            DryDay.PERMAFROST -> DryPhrases(listOf("Dauerfrost bei $t"), listOf("Dauerfrost bei höchstens $t"), listOf("Eisig bei $t"))
            DryDay.FOG -> DryPhrases(listOf("trüb bei $t"), listOf("Trüber Nebeltag bei höchstens $t"), listOf("Neblig-trüb bei $t"))
            DryDay.FOG_AGAIN -> DryPhrases(emptyList(), listOf("Zäher Nebel, kaum mehr als $t"), listOf("Neblig-trüb bei $t"))
            DryDay.OVERCAST -> DryPhrases(
                listOf("grau bei $t"),
                listOf("Grauer Tag bei höchstens $t", "Grau in grau bei bis zu $t"), listOf("Grau bei $t"),
            )
            DryDay.CHANGEABLE -> DryPhrases(listOf("wechselhaft bei $t"), listOf("Wechselhaft bei bis zu $t"), listOf("Wechselhaft bei $t"))
            DryDay.MIXED -> DryPhrases(
                listOf("locker bewölkt bei $t"),
                listOf("Mal Sonne, mal Wolken bei bis zu $t", "Sonne und Wolken im Wechsel, bis $t"), listOf("Wolkig mit Sonne bei $t"),
            )
        }

        private fun golden(name: String, t: String): DryPhrases {
            val title = name.replaceFirstChar { it.uppercase() }
            return DryPhrases(listOf("$name mit $t"), listOf("$title: Sonne satt bei $t"), listOf("$title mit $t"))
        }

        override fun isolated(snow: Boolean) = if (snow) "vereinzelt Flocken" else "vereinzelt Schauer"

        override fun stillDark(sunrise: String?) = if (sunrise != null) "Noch dunkel, Sonnenaufgang $sunrise" else "Noch dunkel"
        override val alreadyDark = "Schon dunkel"
        override fun wetNow(kind: Condition, dryHour: Int?): String {
            val verb = when (kind) {
                Condition.DRIZZLE -> "es nieselt"
                Condition.SNOW, Condition.SNOW_SHOWERS -> "es schneit"
                Condition.FREEZING_RAIN -> "es fällt gefrierender Regen"
                Condition.THUNDER -> "es gewittert"
                Condition.HEAVY_RAIN -> "es schüttet"
                Condition.RAIN, Condition.SHOWERS -> "es regnet"
                else -> "es regnet leicht"
            }
            return if (dryHour != null) "$verb, gegen $dryHour Uhr wird's trocken" else "$verb und das hält erst mal an"
        }
        override fun wetSoon(kind: Condition, hour: Int?): String {
            val time = if (hour == null) "gleich" else from(hour)
            return when (kind) {
                Condition.FREEZING_RAIN -> "noch trocken, $time droht Eisregen"
                Condition.SNOW, Condition.SNOW_SHOWERS -> "noch trocken, $time kann's schneien"
                Condition.THUNDER -> "noch trocken, $time kann's gewittern"
                else -> "noch trocken, $time kann's regnen"
            }
        }
        override fun dryNow(sky: DrySky) = when (sky) {
            DrySky.FOG -> "neblig, aber die nächsten Stunden trocken"
            DrySky.GREY -> "grau, aber die nächsten Stunden trocken"
            DrySky.SUNNY -> "sonnig, die nächsten Stunden bleibt's trocken"
            DrySky.PLAIN -> "die nächsten Stunden bleibt's trocken"
        }
        override val iceNowHazard = "Gehwege und Straßen werden spiegelglatt – langsam machen und Zeit einplanen."
        override val iceSoonHazard = "Gefrierender Regen in Sicht: Gehwege und Straßen werden spiegelglatt – langsam machen."
        override fun slipperyHazard(temp: String) = "Glättegefahr bei $temp – vorsichtig auf Gehwegen und Brücken."
        override fun visibilityHazard(distance: String) = "Sicht stellenweise nur $distance – beim Fahren Licht an."
        override fun visibility(band: VisibilityBand, meters: Int) = when (band) {
            VisibilityBand.UNDER_100_M -> "unter 100 m"
            VisibilityBand.ABOUT_METERS -> "etwa $meters m"
            VisibilityBand.JUST_OVER_1_KM -> "gut 1 km"
            VisibilityBand.ABOUT_1_5_KM -> "rund 1,5 km"
            VisibilityBand.NEARLY_2_KM -> "knapp 2 km"
        }
        override val fogHazard = "Nebel – beim Fahren Licht an und Abstand halten."
        override fun gustHazard(kmh: Int) = "Böen bis $kmh km/h – der Schirm hat's heute schwer."
        override fun frostHazard(temp: String) = "Frost bei $temp – Autoscheibe kratzen einplanen."

        override fun between(min: String, max: String) = "zwischen $min und $max"
        override fun rising(start: String, peak: String) = "von $start auf $peak"
        override fun falling(start: String, end: String) = "erst $start, später nur noch $end"
        override fun around(t: String) = "um die $t"
        override val sunPureFirst = "erst Sonne pur"
        override val sunMuchFirst = "erst viel Sonne"
        override val sunPure = "Sonne pur"
        override val sunMuch = "viel Sonne"
        override fun sunFrom(hour: Int?, pure: Boolean) = "${fromNoon(hour)} ${if (pure) sunPure else sunMuch}"
        override fun sunThenClouds(hour: Int?) = "erst Sonne, ${fromNoon(hour)} Wolken"
        override val sunAndClouds = "mal Sonne, mal Wolken"
        override val mostlyGrey = "meist grau"
        override val dullFoggy = "trüb und neblig"
        override val changeable = "wechselhaft"
        override fun thunderPossible(hail: Boolean, hour: Int?): String {
            val what = if (hail) "Gewitter mit Hagel möglich" else "Gewitter möglich"
            return if (hour == null) what else "${from(hour)} $what"
        }
        override fun precipFromPop(kind: Condition, hour: Int, pop: Int) = "${noun(kind)} ${from(hour)}, bis $pop %"
        override fun precipUntilDry(kind: Condition, dryHour: Int) = "${noun(kind)} bis $dryHour Uhr, danach trocken"
        override fun precipMostOfDay(kind: Condition, pop: Int) = "${noun(kind)} fast den ganzen Tag, bis $pop %"
        override fun precipOnAndOff(kind: Condition, pop: Int) = "immer wieder ${noun(kind)}, bis $pop %"
        override val flakesPossible = "vereinzelt Schneeflocken möglich"
        override val showersPossible = "einzelne Schauer möglich"
        override val dryWord = "trocken"
        override val noRain = "kein Regen"
        override fun gustsUpTo(kmh: Int) = "Böen bis $kmh km/h"
        override val littleWind = "kaum Wind"

        override val titleBlackIce = "Glatteis"
        override val titleSlippery = "Glätte"
        override val titleFog = "Nebel"
        override val titleStorm = "Sturm"
        override val titleGusts = "Starke Böen"
        override val titleThunder = "Gewitter"
        override val titleHeavyRain = "Starkregen"
        override val titleExtremeHeat = "Starke Hitze"
        override val titleHeat = "Hitze"
        override fun titleUv(veryHigh: Boolean) = if (veryHigh) "Sehr hohe UV-Belastung" else "Hohe UV-Belastung"
        override val freezingRainNow = "Gefrierender Regen – Wege und Straßen werden spiegelglatt. Langsam machen und Zeit einplanen."
        override fun freezingRainFrom(hour: Int) = "Ab $hour Uhr gefrierender Regen – Wege und Straßen werden spiegelglatt."
        override fun slipperyWet(temp: String, snow: Boolean) =
            "Bei $temp und ${if (snow) "Schnee" else "Nässe"} können Wege und Straßen glatt sein – langsam machen."
        override fun hoarFrost(temp: String) = "Bei $temp und feuchter Luft ist Reifglätte möglich, vor allem auf Brücken."
        override fun icyFrom(hour: Int, kind: Condition, temp: String) = "Ab $hour Uhr ${noun(kind)} bei $temp – es kann glatt werden."
        override fun fogVisibility(distance: String) = "Sicht teils nur $distance – Abblendlicht an und Abstand halten."
        override val fogPossible = "Nebel möglich – Abblendlicht an und Abstand halten."
        override fun stormText(kmh: Int) = "Sturmböen bis $kmh km/h – Wald und Alleen meiden, lose Sachen sichern."
        override fun gustText(kmh: Int) = "Böen bis $kmh km/h – lose Sachen sichern und auf herabfallende Äste achten."
        override val thunderNow = "Gerade Gewitter – bei Blitz und Donner lieber drinnen bleiben."
        override fun thunderWarning(hour: Int, hail: Boolean) =
            "Ab $hour Uhr ${if (hail) "Gewitter mit Hagel möglich" else "Gewitter möglich"} – bei Blitz und Donner rein ins Haus."
        override fun heavyRainAmount(hour: Int, mm: Int) = "Ab $hour Uhr bis $mm l/m² pro Stunde – Unterführungen und Senken meiden."
        override fun heavyRainBursts(hour: Int) = "Ab $hour Uhr kräftige Güsse – Unterführungen und Senken meiden."
        override fun extremeHeat(temp: String) = "Bis $temp – viel trinken, Anstrengung meiden und im Schatten bleiben."
        override fun heat(temp: String) = "Bis $temp – viel trinken und die Mittagssonne meiden."
        override fun uv(index: Int) = "UV-Index bis $index – Sonnencreme und Kopfbedeckung nicht vergessen."

        override val noWeatherTitle = "☀️ Dein Daily Briefing"
        override val noWeatherText = "Wetter gerade nicht erreichbar – öffne die App für ein Update."
        override val noWeatherBigText = "🌦️ Das Wetter ließ sich gerade nicht laden – in der App gibt's gleich ein Update."
        override fun notificationTitle(emoji: String, temp: String, place: String, feels: String) =
            "$emoji $temp in $place · ${feelsLike(feels)}"
        override fun window(place: String, tomorrow: Boolean, startHour: Int, endHour: Int) =
            "$place ${if (tomorrow) "morgen " else ""}$startHour–$endHour Uhr"
        override fun shortAround(t: String) = "um $t"
        override fun thunderFromShort(hour: Int) = "Gewitter ab $hour Uhr"
        override val thunderShort = "Gewitter"
        override fun labelFrom(label: String, hour: Int) = "$label ab $hour Uhr"
        override fun labelUntil(label: String, hour: Int) = "$label bis $hour Uhr"
        override val noNews = "📰 Die Nachrichten warten in der App."
        override fun feelsLike(feels: String) = "gefühlt $feels"

        override fun condition(condition: Condition, isDay: Boolean) = when (condition) {
            Condition.CLEAR -> if (isDay) "sonnig" else "klar"
            Condition.MOSTLY_CLEAR -> if (isDay) "überwiegend sonnig" else "überwiegend klar"
            Condition.PARTLY_CLOUDY -> "teils bewölkt"
            Condition.OVERCAST -> "bedeckt"
            Condition.FOG -> "Nebel"
            Condition.DRIZZLE -> "Nieselregen"
            Condition.RAIN -> "Regen"
            Condition.HEAVY_RAIN -> "Starkregen"
            Condition.FREEZING_RAIN -> "gefrierender Regen"
            Condition.SNOW -> "Schnee"
            Condition.SHOWERS -> "Schauer"
            Condition.SNOW_SHOWERS -> "Schneeschauer"
            Condition.THUNDER -> "Gewitter"
        }
        override val rainWord = "Regen"
        override val snowWord = "Schnee"
        override val sleetWord = "Schneeregen"
    }

    // =========================================================================================
    // English
    // =========================================================================================
    private object En : WeatherStrings {
        private fun at(hour: Int) = FormatStrings.of(AppLanguage.EN).hour(hour)
        private fun from(hour: Int) = "from ${at(hour)}"
        private fun until(hour: Int) = "until ${at(hour)}"
        private fun fromNoon(hour: Int?) = when (hour) {
            null -> "later"
            12, 13 -> "from midday"
            else -> from(hour)
        }
        private fun noun(kind: Condition) = precipName(kind)

        override val tomorrowPrefix = "Tomorrow: "
        override val headlineFallback = "Your weather for today"
        override fun connector(connector: Connector) = when (connector) {
            Connector.THEN -> "then"
            Connector.LATER -> "later"
            Connector.AFTERWARDS -> "after that"
            Connector.ALSO -> ""
        }
        override val iceStart = "Black ice this morning"
        override val fogStarts = listOf("Foggy start", "Fog at first")
        override val snowStart = "Snowy start"
        override val wetStart = "Wet start"
        override val icyStart = "Icy start"
        override val frostyStart = "Frosty start"
        override fun thunderBase(base: ThunderBase, t: String) = when (base) {
            ThunderBase.HOT -> "hot at $t"
            ThunderBase.SULTRY -> "muggy at $t"
            ThunderBase.CHANGEABLE -> "changeable at $t"
        }
        override fun thunderFrom(hour: Int) = "storms ${from(hour)}"
        override fun thunderUpTo(t: String) = "storms, up to $t"
        override fun thunderyDay(t: String) = "Stormy day, up to $t"
        override fun thunderyAt(t: String) = "Stormy at $t"
        override val freezingRainPhrase = "freezing rain"
        override val freezingRainSlippery = "Freezing rain – slippery out there today"
        override val freezingRainAlert = "Watch out, freezing rain"
        override fun blowing(wintry: Boolean) = if (wintry) "blowing snow" else "windy and wet"
        override fun blowingHood(wintry: Boolean) =
            if (wintry) "Blowing snow – bring a hood today" else "Windy and wet – bring a hood today"
        override fun blowingAt(wintry: Boolean, t: String) = if (wintry) "Blowing snow at $t" else "Windy and wet at $t"
        override fun beforeRain(before: BeforeRain) = when (before) {
            BeforeRain.FRIENDLY -> "Bright at first"
            BeforeRain.DRY -> "Dry at first"
            BeforeRain.GREY -> "Grey day"
        }
        override fun beforeRainAt(before: BeforeRain, t: String) = when (before) {
            BeforeRain.FRIENDLY -> "Bright start at $t"
            BeforeRain.DRY -> "Dry start at $t"
            BeforeRain.GREY -> "Grey day at $t"
        }
        override fun precipFrom(kind: Condition, hour: Int) = "${noun(kind)} ${from(hour)}"
        override fun precipThenDry(kind: Condition, dryHour: Int) = "${noun(kind)}, ${dryFrom(dryHour)}"
        override fun dryFrom(hour: Int) = "dry ${from(hour)}"
        override fun precipUntilDryHeadline(kind: Condition, dryHour: Int, t: String?) =
            "${noun(kind)} ${until(dryHour)}, then dry" + (t?.let { " at $it" } ?: "")
        override fun precipAt(kind: Condition, t: String) = "${noun(kind)} at $t"
        override fun wetDay(kind: Condition, t: String) = when (kind) {
            Condition.DRIZZLE -> listOf("Drizzly and grey, max $t", "Dull with drizzle at $t")
            Condition.SHOWERS -> listOf("Showery, up to $t", "Showers on and off at $t")
            Condition.HEAVY_RAIN -> listOf("Heavy rain, max $t", "Very wet at $t")
            Condition.SNOW -> listOf("Snowy day, max $t", "Wintry with snow at $t")
            Condition.SNOW_SHOWERS -> listOf("Snow showers on and off at $t", "Snow showers at $t")
            else -> listOf("Rainy day, max $t", "Wet and grey, max $t")
        }
        override fun precipName(kind: Condition) = when (kind) {
            Condition.DRIZZLE -> "drizzle"
            Condition.SHOWERS -> "showers"
            Condition.HEAVY_RAIN -> "heavy rain"
            Condition.FREEZING_RAIN -> "freezing rain"
            Condition.SNOW -> "snow"
            Condition.SNOW_SHOWERS -> "snow showers"
            Condition.THUNDER -> "thunderstorms"
            else -> "rain"
        }
        override fun dry(day: DryDay, t: String) = when (day) {
            DryDay.HOT -> DryPhrases(listOf("hot, up to $t"), listOf("Heat up to $t – find some shade"), listOf("Hot, up to $t"))
            DryDay.SUMMER_CLEAR -> DryPhrases(
                listOf("pure summer at $t", "summery at $t"),
                listOf("Pure summer: up to $t, barely a cloud"), listOf("Summery, up to $t"),
            )
            DryDay.SUMMER -> DryPhrases(
                listOf("pure summer at $t", "summery at $t"),
                listOf("Summery with lots of sun at $t"), listOf("Summery, up to $t"),
            )
            DryDay.GOLDEN_OCTOBER -> golden("golden October", t)
            DryDay.GOLDEN_AUTUMN -> golden("golden autumn", t)
            DryDay.SPRING -> DryPhrases(listOf("spring-like at $t"), listOf("Pure spring: sun, up to $t"), listOf("Spring-like at $t"))
            DryDay.SUNNY_COLD -> DryPhrases(listOf("sunny but cold at $t"), listOf("Cold but sunny, max $t"), listOf("Sunny but cold at $t"))
            DryDay.SUNNY_COOL -> DryPhrases(
                listOf("bright at $t"),
                listOf("Bright and sunny, up to $t", "Lots of sun at a cool $t"), listOf("Bright at $t"),
            )
            DryDay.SUNNY -> DryPhrases(
                listOf("sunny at $t", "lots of sun at $t"),
                listOf("Lots of sun, up to $t", "Sunny and pleasant at $t"), listOf("Sunny at $t"),
            )
            DryDay.PERMAFROST -> DryPhrases(listOf("frost all day at $t"), listOf("Frost all day, max $t"), listOf("Icy at $t"))
            DryDay.FOG -> DryPhrases(listOf("dull at $t"), listOf("Dull, foggy day, max $t"), listOf("Foggy and dull at $t"))
            DryDay.FOG_AGAIN -> DryPhrases(emptyList(), listOf("Stubborn fog, barely $t"), listOf("Foggy and dull at $t"))
            DryDay.OVERCAST -> DryPhrases(
                listOf("grey at $t"),
                listOf("Grey day, max $t", "Grey on grey, up to $t"), listOf("Grey at $t"),
            )
            DryDay.CHANGEABLE -> DryPhrases(listOf("changeable at $t"), listOf("Changeable, up to $t"), listOf("Changeable at $t"))
            DryDay.MIXED -> DryPhrases(
                listOf("partly cloudy at $t"),
                listOf("Sun and clouds, up to $t", "Sun and clouds taking turns, up to $t"), listOf("Cloudy with sun at $t"),
            )
        }

        private fun golden(name: String, t: String): DryPhrases {
            val title = name.replaceFirstChar { it.uppercase() }
            return DryPhrases(listOf("$name at $t"), listOf("$title: sunshine galore at $t"), listOf("$title at $t"))
        }

        override fun isolated(snow: Boolean) = if (snow) "a few flurries" else "a stray shower"

        override fun stillDark(sunrise: String?) = if (sunrise != null) "Still dark, sunrise at $sunrise" else "Still dark"
        override val alreadyDark = "Already dark"
        override fun wetNow(kind: Condition, dryHour: Int?): String {
            val verb = when (kind) {
                Condition.DRIZZLE -> "it's drizzling"
                Condition.SNOW, Condition.SNOW_SHOWERS -> "it's snowing"
                Condition.FREEZING_RAIN -> "freezing rain is falling"
                Condition.THUNDER -> "there's a thunderstorm"
                Condition.HEAVY_RAIN -> "it's pouring"
                Condition.RAIN, Condition.SHOWERS -> "it's raining"
                else -> "light rain is falling"
            }
            return if (dryHour != null) "$verb, dry again around ${at(dryHour)}" else "$verb and it'll keep going for a while"
        }
        override fun wetSoon(kind: Condition, hour: Int?): String {
            val time = if (hour == null) "any minute" else from(hour)
            return when (kind) {
                Condition.FREEZING_RAIN -> "still dry, freezing rain likely $time"
                Condition.SNOW, Condition.SNOW_SHOWERS -> "still dry, snow possible $time"
                Condition.THUNDER -> "still dry, storms possible $time"
                else -> "still dry, rain possible $time"
            }
        }
        override fun dryNow(sky: DrySky) = when (sky) {
            DrySky.FOG -> "foggy, but dry for the next few hours"
            DrySky.GREY -> "grey, but dry for the next few hours"
            DrySky.SUNNY -> "sunny and dry for the next few hours"
            DrySky.PLAIN -> "dry for the next few hours"
        }
        override val iceNowHazard = "Paths and roads are turning to glass – take it slow and allow extra time."
        override val iceSoonHazard = "Freezing rain on the way: paths and roads will turn to glass – take it slow."
        override fun slipperyHazard(temp: String) = "Risk of ice at $temp – careful on paths and bridges."
        override fun visibilityHazard(distance: String) = "Visibility down to $distance in places – headlights on when driving."
        override fun visibility(band: VisibilityBand, meters: Int) = when (band) {
            VisibilityBand.UNDER_100_M -> "under 100 m"
            VisibilityBand.ABOUT_METERS -> "about $meters m"
            VisibilityBand.JUST_OVER_1_KM -> "just over 1 km"
            VisibilityBand.ABOUT_1_5_KM -> "about 1.5 km"
            VisibilityBand.NEARLY_2_KM -> "almost 2 km"
        }
        override val fogHazard = "Fog – headlights on and keep your distance when driving."
        override fun gustHazard(kmh: Int) = "Gusts up to $kmh km/h – a tough day for umbrellas."
        override fun frostHazard(temp: String) = "Frost at $temp – allow time to scrape the windscreen."

        override fun between(min: String, max: String) = "between $min and $max"
        override fun rising(start: String, peak: String) = "from $start to $peak"
        override fun falling(start: String, end: String) = "$start at first, only $end later"
        override fun around(t: String) = "around $t"
        override val sunPureFirst = "full sun at first"
        override val sunMuchFirst = "lots of sun at first"
        override val sunPure = "full sun"
        override val sunMuch = "lots of sun"
        override fun sunFrom(hour: Int?, pure: Boolean) = "${if (pure) sunPure else sunMuch} ${fromNoon(hour)}"
        override fun sunThenClouds(hour: Int?) = "sun at first, clouds ${fromNoon(hour)}"
        override val sunAndClouds = "sun and clouds"
        override val mostlyGrey = "mostly grey"
        override val dullFoggy = "dull and foggy"
        override val changeable = "changeable"
        override fun thunderPossible(hail: Boolean, hour: Int?): String {
            val what = if (hail) "thunderstorms with hail possible" else "thunderstorms possible"
            return if (hour == null) what else "$what ${from(hour)}"
        }
        override fun precipFromPop(kind: Condition, hour: Int, pop: Int) = "${noun(kind)} ${from(hour)}, up to $pop%"
        override fun precipUntilDry(kind: Condition, dryHour: Int) = "${noun(kind)} ${until(dryHour)}, dry after that"
        override fun precipMostOfDay(kind: Condition, pop: Int) = "${noun(kind)} most of the day, up to $pop%"
        override fun precipOnAndOff(kind: Condition, pop: Int) = "${noun(kind)} on and off, up to $pop%"
        override val flakesPossible = "a few snowflakes possible"
        override val showersPossible = "a stray shower possible"
        override val dryWord = "dry"
        override val noRain = "no rain"
        override fun gustsUpTo(kmh: Int) = "gusts up to $kmh km/h"
        override val littleWind = "hardly any wind"

        override val titleBlackIce = "Black ice"
        override val titleSlippery = "Slippery roads"
        override val titleFog = "Fog"
        override val titleStorm = "Storm"
        override val titleGusts = "Strong gusts"
        override val titleThunder = "Thunderstorms"
        override val titleHeavyRain = "Heavy rain"
        override val titleExtremeHeat = "Extreme heat"
        override val titleHeat = "Heat"
        override fun titleUv(veryHigh: Boolean) = if (veryHigh) "Very high UV" else "High UV"
        override val freezingRainNow = "Freezing rain – paths and roads are turning to glass. Take it slow and allow extra time."
        override fun freezingRainFrom(hour: Int) = "Freezing rain ${from(hour)} – paths and roads will turn to glass."
        override fun slipperyWet(temp: String, snow: Boolean) =
            "At $temp with ${if (snow) "snow" else "wet ground"}, paths and roads can be slippery – take it slow."
        override fun hoarFrost(temp: String) = "At $temp with damp air, frost may form on the ground, especially on bridges."
        override fun icyFrom(hour: Int, kind: Condition, temp: String) =
            "${noun(kind).replaceFirstChar { it.uppercase() }} ${from(hour)} at $temp – it may get slippery."
        override fun fogVisibility(distance: String) = "Visibility down to $distance in places – dipped headlights on and keep your distance."
        override val fogPossible = "Fog possible – dipped headlights on and keep your distance."
        override fun stormText(kmh: Int) = "Storm gusts up to $kmh km/h – avoid woods and tree-lined roads, secure loose items."
        override fun gustText(kmh: Int) = "Gusts up to $kmh km/h – secure loose items and watch for falling branches."
        override val thunderNow = "Thunderstorm right now – better stay indoors while it lasts."
        override fun thunderWarning(hour: Int, hail: Boolean) =
            "${if (hail) "Thunderstorms with hail possible" else "Thunderstorms possible"} ${from(hour)} – head indoors at the first rumble."
        override fun heavyRainAmount(hour: Int, mm: Int) = "Up to $mm mm an hour ${from(hour)} – avoid underpasses and dips."
        override fun heavyRainBursts(hour: Int) = "Heavy downpours ${from(hour)} – avoid underpasses and dips."
        override fun extremeHeat(temp: String) = "Up to $temp – drink plenty, avoid exertion and stay in the shade."
        override fun heat(temp: String) = "Up to $temp – drink plenty and avoid the midday sun."
        override fun uv(index: Int) = "UV index up to $index – don't forget sunscreen and a hat."

        override val noWeatherTitle = "☀️ Your Daily Briefing"
        override val noWeatherText = "Weather unavailable right now – open the app for an update."
        override val noWeatherBigText = "🌦️ The weather couldn't be loaded just now – the app will have an update shortly."
        override fun notificationTitle(emoji: String, temp: String, place: String, feels: String) =
            "$emoji $temp in $place · ${feelsLike(feels)}"
        override fun window(place: String, tomorrow: Boolean, startHour: Int, endHour: Int) =
            "$place ${if (tomorrow) "tomorrow " else ""}${FormatStrings.of(AppLanguage.EN).hourRange(startHour, endHour)}"
        override fun shortAround(t: String) = "around $t"
        override fun thunderFromShort(hour: Int) = "storms ${from(hour)}"
        override val thunderShort = "storms"
        override fun labelFrom(label: String, hour: Int) = "$label ${from(hour)}"
        override fun labelUntil(label: String, hour: Int) = "$label ${until(hour)}"
        override val noNews = "📰 Your news is waiting in the app."
        override fun feelsLike(feels: String) = "feels like $feels"

        override fun condition(condition: Condition, isDay: Boolean) = when (condition) {
            Condition.CLEAR -> if (isDay) "sunny" else "clear"
            Condition.MOSTLY_CLEAR -> if (isDay) "mostly sunny" else "mostly clear"
            Condition.PARTLY_CLOUDY -> "partly cloudy"
            Condition.OVERCAST -> "overcast"
            Condition.FOG -> "fog"
            Condition.DRIZZLE -> "drizzle"
            Condition.RAIN -> "rain"
            Condition.HEAVY_RAIN -> "heavy rain"
            Condition.FREEZING_RAIN -> "freezing rain"
            Condition.SNOW -> "snow"
            Condition.SHOWERS -> "showers"
            Condition.SNOW_SHOWERS -> "snow showers"
            Condition.THUNDER -> "thunderstorm"
        }
        override val rainWord = "Rain"
        override val snowWord = "Snow"
        override val sleetWord = "Sleet"
    }

    // =========================================================================================
    // Français (tutoiement, comme le « du » allemand)
    // =========================================================================================
    private object Fr : WeatherStrings {
        private fun at(hour: Int) = when (val h = Math.floorMod(hour, 24)) {
            0 -> "minuit"
            12 -> "midi"
            else -> "$h h"
        }
        private fun from(hour: Int) = "dès ${at(hour)}"
        private fun until(hour: Int) = "jusqu'à ${at(hour)}"
        private fun fromNoon(hour: Int?) = when (hour) {
            null -> "plus tard"
            12, 13 -> "dès midi"
            else -> from(hour)
        }
        private fun noun(kind: Condition) = precipName(kind)
        private fun cap(text: String) = text.replaceFirstChar { it.uppercase() }

        override val tomorrowPrefix = "Demain : "
        override val headlineFallback = "Ta météo du jour"
        override fun connector(connector: Connector) = when (connector) {
            Connector.THEN -> "puis"
            Connector.LATER -> "plus tard"
            Connector.AFTERWARDS -> "ensuite"
            Connector.ALSO -> ""
        }
        override val iceStart = "Verglas ce matin"
        override val fogStarts = listOf("Brouillard au réveil", "D'abord du brouillard")
        override val snowStart = "Neige au réveil"
        override val wetStart = "Réveil humide"
        override val icyStart = "Réveil glacial"
        override val frostyStart = "Gelée au réveil"
        override fun thunderBase(base: ThunderBase, t: String) = when (base) {
            ThunderBase.HOT -> "chaleur jusqu'à $t"
            ThunderBase.SULTRY -> "temps lourd jusqu'à $t"
            ThunderBase.CHANGEABLE -> "temps variable et $t"
        }
        override fun thunderFrom(hour: Int) = "orages ${from(hour)}"
        override fun thunderUpTo(t: String) = "orages, jusqu'à $t"
        override fun thunderyDay(t: String) = "Journée orageuse, jusqu'à $t"
        override fun thunderyAt(t: String) = "Temps orageux et $t"
        override val freezingRainPhrase = "pluie verglaçante"
        override val freezingRainSlippery = "Pluie verglaçante – ça glisse aujourd'hui"
        override val freezingRainAlert = "Attention, pluie verglaçante"
        override fun blowing(wintry: Boolean) = if (wintry) "neige et rafales" else "vent et pluie"
        override fun blowingHood(wintry: Boolean) =
            if (wintry) "Neige et rafales – capuche conseillée" else "Vent et pluie – capuche conseillée"
        override fun blowingAt(wintry: Boolean, t: String) = if (wintry) "Neige et rafales, $t" else "Vent et pluie, $t"
        override fun beforeRain(before: BeforeRain) = when (before) {
            BeforeRain.FRIENDLY -> "D'abord ensoleillé"
            BeforeRain.DRY -> "D'abord sec"
            BeforeRain.GREY -> "Journée grise"
        }
        override fun beforeRainAt(before: BeforeRain, t: String) = when (before) {
            BeforeRain.FRIENDLY -> "Soleil et $t"
            BeforeRain.DRY -> "Sec et $t"
            BeforeRain.GREY -> "Gris et $t"
        }
        override fun precipFrom(kind: Condition, hour: Int) = "${noun(kind)} ${from(hour)}"
        override fun precipThenDry(kind: Condition, dryHour: Int) = "${noun(kind)}, ${dryFrom(dryHour)}"
        override fun dryFrom(hour: Int) = "sec ${from(hour)}"
        override fun precipUntilDryHeadline(kind: Condition, dryHour: Int, t: String?) =
            "${noun(kind)} ${until(dryHour)}, puis sec" + (t?.let { " et $it" } ?: "")
        override fun precipAt(kind: Condition, t: String) = "${noun(kind)} et $t"
        override fun wetDay(kind: Condition, t: String) = when (kind) {
            Condition.DRIZZLE -> listOf("Bruine et grisaille, $t max", "Ciel terne et bruine, $t")
            Condition.SHOWERS -> listOf("Averses, jusqu'à $t", "Averses à répétition, $t")
            Condition.HEAVY_RAIN -> listOf("Fortes pluies, $t max", "Très humide, $t")
            Condition.SNOW -> listOf("Journée neigeuse, $t max", "Hivernal avec de la neige, $t")
            Condition.SNOW_SHOWERS -> listOf("Averses de neige à répétition, $t", "Averses de neige, $t")
            else -> listOf("Journée pluvieuse, $t max", "Pluie et grisaille, $t max")
        }
        override fun precipName(kind: Condition) = when (kind) {
            Condition.DRIZZLE -> "bruine"
            Condition.SHOWERS -> "averses"
            Condition.HEAVY_RAIN -> "fortes pluies"
            Condition.FREEZING_RAIN -> "pluie verglaçante"
            Condition.SNOW -> "neige"
            Condition.SNOW_SHOWERS -> "averses de neige"
            Condition.THUNDER -> "orages"
            else -> "pluie"
        }
        override fun dry(day: DryDay, t: String) = when (day) {
            DryDay.HOT -> DryPhrases(listOf("chaleur jusqu'à $t"), listOf("Chaleur jusqu'à $t – vite, à l'ombre"), listOf("Chaud, jusqu'à $t"))
            DryDay.SUMMER_CLEAR -> DryPhrases(
                listOf("plein été et $t", "temps estival et $t"),
                listOf("Plein été : $t et pas un nuage"), listOf("Estival, jusqu'à $t"),
            )
            DryDay.SUMMER -> DryPhrases(
                listOf("plein été et $t", "temps estival et $t"),
                listOf("Estival et très ensoleillé, $t"), listOf("Estival, jusqu'à $t"),
            )
            DryDay.GOLDEN_OCTOBER -> golden("octobre doré", t)
            DryDay.GOLDEN_AUTUMN -> golden("automne doré", t)
            DryDay.SPRING -> DryPhrases(listOf("air de printemps et $t"), listOf("Printemps : soleil et jusqu'à $t"), listOf("Air de printemps et $t"))
            DryDay.SUNNY_COLD -> DryPhrases(listOf("soleil mais froid, $t"), listOf("Froid mais ensoleillé, $t max"), listOf("Soleil mais froid, $t"))
            DryDay.SUNNY_COOL -> DryPhrases(
                listOf("éclaircies et $t"),
                listOf("Agréable et ensoleillé, jusqu'à $t", "Beaucoup de soleil, frais avec $t"), listOf("Agréable et $t"),
            )
            DryDay.SUNNY -> DryPhrases(
                listOf("soleil et $t", "beaucoup de soleil et $t"),
                listOf("Beaucoup de soleil, jusqu'à $t", "Ensoleillé et doux, $t"), listOf("Soleil et $t"),
            )
            DryDay.PERMAFROST -> DryPhrases(listOf("gel toute la journée, $t"), listOf("Gel toute la journée, $t max"), listOf("Glacial, $t"))
            DryDay.FOG -> DryPhrases(listOf("ciel terne et $t"), listOf("Journée brumeuse, $t max"), listOf("Brumeux et terne, $t"))
            DryDay.FOG_AGAIN -> DryPhrases(emptyList(), listOf("Brouillard tenace, à peine $t"), listOf("Brumeux et terne, $t"))
            DryDay.OVERCAST -> DryPhrases(
                listOf("gris et $t"),
                listOf("Journée grise, $t max", "Gris sur gris, jusqu'à $t"), listOf("Gris et $t"),
            )
            DryDay.CHANGEABLE -> DryPhrases(listOf("temps variable et $t"), listOf("Variable, jusqu'à $t"), listOf("Temps variable et $t"))
            DryDay.MIXED -> DryPhrases(
                listOf("passages nuageux et $t"),
                listOf("Soleil et nuages, jusqu'à $t", "Soleil et nuages en alternance, $t"), listOf("Nuageux avec éclaircies, $t"),
            )
        }

        private fun golden(name: String, t: String): DryPhrases =
            DryPhrases(listOf("$name et $t"), listOf("${cap(name)} : grand soleil et $t"), listOf("${cap(name)} et $t"))

        override fun isolated(snow: Boolean) = if (snow) "quelques flocons" else "une averse isolée"

        override fun stillDark(sunrise: String?) = if (sunrise != null) "Encore nuit, lever du soleil à $sunrise" else "Encore nuit"
        override val alreadyDark = "Déjà nuit"
        override fun wetNow(kind: Condition, dryHour: Int?): String {
            val verb = when (kind) {
                Condition.DRIZZLE -> "il bruine"
                Condition.SNOW, Condition.SNOW_SHOWERS -> "il neige"
                Condition.FREEZING_RAIN -> "il tombe de la pluie verglaçante"
                Condition.THUNDER -> "il y a de l'orage"
                Condition.HEAVY_RAIN -> "il pleut à verse"
                Condition.RAIN, Condition.SHOWERS -> "il pleut"
                else -> "il pleut un peu"
            }
            return if (dryHour != null) "$verb, ça se calme vers ${at(dryHour)}" else "$verb et ça va durer un moment"
        }
        override fun wetSoon(kind: Condition, hour: Int?): String {
            val time = if (hour == null) "d'un instant à l'autre" else from(hour)
            return when (kind) {
                Condition.FREEZING_RAIN -> "encore sec, pluie verglaçante possible $time"
                Condition.SNOW, Condition.SNOW_SHOWERS -> "encore sec, neige possible $time"
                Condition.THUNDER -> "encore sec, orages possibles $time"
                else -> "encore sec, pluie possible $time"
            }
        }
        override fun dryNow(sky: DrySky) = when (sky) {
            DrySky.FOG -> "brumeux, mais sec pour les prochaines heures"
            DrySky.GREY -> "gris, mais sec pour les prochaines heures"
            DrySky.SUNNY -> "ensoleillé et sec pour les prochaines heures"
            DrySky.PLAIN -> "sec pour les prochaines heures"
        }
        override val iceNowHazard = "Trottoirs et routes glissent comme une patinoire – vas-y doucement et prévois de la marge."
        override val iceSoonHazard = "Pluie verglaçante en vue : trottoirs et routes vont devenir des patinoires – vas-y doucement."
        override fun slipperyHazard(temp: String) = "Risque de verglas à $temp – prudence sur les trottoirs et les ponts."
        override fun visibilityHazard(distance: String) = "Visibilité réduite à $distance par endroits – allume tes phares au volant."
        override fun visibility(band: VisibilityBand, meters: Int) = when (band) {
            VisibilityBand.UNDER_100_M -> "moins de 100 m"
            VisibilityBand.ABOUT_METERS -> "environ $meters m"
            VisibilityBand.JUST_OVER_1_KM -> "un peu plus de 1 km"
            VisibilityBand.ABOUT_1_5_KM -> "environ 1,5 km"
            VisibilityBand.NEARLY_2_KM -> "presque 2 km"
        }
        override val fogHazard = "Brouillard – phares allumés et distances de sécurité au volant."
        override fun gustHazard(kmh: Int) = "Rafales jusqu'à $kmh km/h – mauvaise journée pour les parapluies."
        override fun frostHazard(temp: String) = "Gel à $temp – prévois de gratter le pare-brise."

        override fun between(min: String, max: String) = "entre $min et $max"
        override fun rising(start: String, peak: String) = "de $start à $peak"
        override fun falling(start: String, end: String) = "d'abord $start, plus que $end ensuite"
        override fun around(t: String) = "autour de $t"
        override val sunPureFirst = "d'abord grand soleil"
        override val sunMuchFirst = "d'abord beaucoup de soleil"
        override val sunPure = "grand soleil"
        override val sunMuch = "beaucoup de soleil"
        override fun sunFrom(hour: Int?, pure: Boolean) = "${if (pure) sunPure else sunMuch} ${fromNoon(hour)}"
        override fun sunThenClouds(hour: Int?) = "d'abord du soleil, nuages ${fromNoon(hour)}"
        override val sunAndClouds = "soleil et nuages"
        override val mostlyGrey = "plutôt gris"
        override val dullFoggy = "terne et brumeux"
        override val changeable = "variable"
        override fun thunderPossible(hail: Boolean, hour: Int?): String {
            val what = if (hail) "orages avec grêle possibles" else "orages possibles"
            return if (hour == null) what else "$what ${from(hour)}"
        }
        override fun precipFromPop(kind: Condition, hour: Int, pop: Int) = "${noun(kind)} ${from(hour)}, jusqu'à $pop %"
        override fun precipUntilDry(kind: Condition, dryHour: Int) = "${noun(kind)} ${until(dryHour)}, puis sec"
        override fun precipMostOfDay(kind: Condition, pop: Int) = "${noun(kind)} presque toute la journée, jusqu'à $pop %"
        override fun precipOnAndOff(kind: Condition, pop: Int) = "${noun(kind)} par intermittence, jusqu'à $pop %"
        override val flakesPossible = "quelques flocons possibles"
        override val showersPossible = "une averse isolée possible"
        override val dryWord = "sec"
        override val noRain = "pas de pluie"
        override fun gustsUpTo(kmh: Int) = "rafales jusqu'à $kmh km/h"
        override val littleWind = "peu de vent"

        override val titleBlackIce = "Verglas"
        override val titleSlippery = "Chaussée glissante"
        override val titleFog = "Brouillard"
        override val titleStorm = "Tempête"
        override val titleGusts = "Fortes rafales"
        override val titleThunder = "Orages"
        override val titleHeavyRain = "Fortes pluies"
        override val titleExtremeHeat = "Canicule"
        override val titleHeat = "Chaleur"
        override fun titleUv(veryHigh: Boolean) = if (veryHigh) "UV très élevés" else "UV élevés"
        override val freezingRainNow =
            "Pluie verglaçante – trottoirs et routes deviennent des patinoires. Vas-y doucement et prévois de la marge."
        override fun freezingRainFrom(hour: Int) = "Pluie verglaçante ${from(hour)} – trottoirs et routes vont devenir des patinoires."
        override fun slipperyWet(temp: String, snow: Boolean) =
            "Avec $temp et ${if (snow) "de la neige" else "un sol mouillé"}, trottoirs et routes peuvent glisser – vas-y doucement."
        override fun hoarFrost(temp: String) = "Avec $temp et un air humide, du givre peut se former, surtout sur les ponts."
        override fun icyFrom(hour: Int, kind: Condition, temp: String) =
            "${cap(noun(kind))} ${from(hour)} avec $temp – ça peut devenir glissant."
        override fun fogVisibility(distance: String) = "Visibilité parfois de $distance seulement – feux de croisement et distances de sécurité."
        override val fogPossible = "Brouillard possible – feux de croisement et distances de sécurité."
        override fun stormText(kmh: Int) =
            "Rafales de tempête jusqu'à $kmh km/h – évite les bois et les allées d'arbres, range ce qui peut s'envoler."
        override fun gustText(kmh: Int) = "Rafales jusqu'à $kmh km/h – range ce qui peut s'envoler et attention aux chutes de branches."
        override val thunderNow = "Orage en cours – mieux vaut rester à l'intérieur."
        override fun thunderWarning(hour: Int, hail: Boolean) =
            "${if (hail) "Orages avec grêle possibles" else "Orages possibles"} ${from(hour)} – à l'abri au premier coup de tonnerre."
        override fun heavyRainAmount(hour: Int, mm: Int) =
            "${cap(from(hour))}, jusqu'à $mm mm par heure – évite les passages souterrains et les cuvettes."
        override fun heavyRainBursts(hour: Int) = "Fortes averses ${from(hour)} – évite les passages souterrains et les cuvettes."
        override fun extremeHeat(temp: String) = "Jusqu'à $temp – bois beaucoup, évite les efforts et reste à l'ombre."
        override fun heat(temp: String) = "Jusqu'à $temp – bois beaucoup et évite le soleil de midi."
        override fun uv(index: Int) = "Indice UV jusqu'à $index – n'oublie pas la crème solaire et un chapeau."

        override val noWeatherTitle = "☀️ Ton Daily Briefing"
        override val noWeatherText = "Météo indisponible pour le moment – ouvre l'appli pour la mettre à jour."
        override val noWeatherBigText = "🌦️ La météo n'a pas pu être chargée – l'appli la mettra à jour dans un instant."
        override fun notificationTitle(emoji: String, temp: String, place: String, feels: String) =
            "$emoji $temp à $place · ${feelsLike(feels)}"
        override fun window(place: String, tomorrow: Boolean, startHour: Int, endHour: Int) =
            "$place ${if (tomorrow) "demain " else ""}$startHour–$endHour h"
        override fun shortAround(t: String) = "autour de $t"
        override fun thunderFromShort(hour: Int) = "orages ${from(hour)}"
        override val thunderShort = "orages"
        override fun labelFrom(label: String, hour: Int) = "$label ${from(hour)}"
        override fun labelUntil(label: String, hour: Int) = "$label ${until(hour)}"
        override val noNews = "📰 Tes infos t'attendent dans l'appli."
        override fun feelsLike(feels: String) = "ressenti $feels"

        override fun condition(condition: Condition, isDay: Boolean) = when (condition) {
            Condition.CLEAR -> if (isDay) "ensoleillé" else "dégagé"
            Condition.MOSTLY_CLEAR -> if (isDay) "plutôt ensoleillé" else "plutôt dégagé"
            Condition.PARTLY_CLOUDY -> "partiellement nuageux"
            Condition.OVERCAST -> "couvert"
            Condition.FOG -> "brouillard"
            Condition.DRIZZLE -> "bruine"
            Condition.RAIN -> "pluie"
            Condition.HEAVY_RAIN -> "fortes pluies"
            Condition.FREEZING_RAIN -> "pluie verglaçante"
            Condition.SNOW -> "neige"
            Condition.SHOWERS -> "averses"
            Condition.SNOW_SHOWERS -> "averses de neige"
            Condition.THUNDER -> "orage"
        }
        override val rainWord = "Pluie"
        override val snowWord = "Neige"
        override val sleetWord = "Neige fondue"
    }

    // =========================================================================================
    // Español (tuteo)
    // =========================================================================================
    private object Es : WeatherStrings {
        /** "las 15 h", "la 1 h", "medianoche" — the hour with its article. */
        private fun at(hour: Int) = when (val h = Math.floorMod(hour, 24)) {
            0 -> "medianoche"
            1 -> "la 1 h"
            else -> "las $h h"
        }
        private fun from(hour: Int) = "desde ${at(hour)}"
        private fun until(hour: Int) = "hasta ${at(hour)}"
        private fun fromNoon(hour: Int?) = when (hour) {
            null -> "más tarde"
            12, 13 -> "desde el mediodía"
            else -> from(hour)
        }
        private fun noun(kind: Condition) = precipName(kind)
        private fun cap(text: String) = text.replaceFirstChar { it.uppercase() }

        override val tomorrowPrefix = "Mañana: "
        override val headlineFallback = "Tu tiempo para hoy"
        override fun connector(connector: Connector) = when (connector) {
            Connector.THEN -> "luego"
            Connector.LATER -> "más tarde"
            Connector.AFTERWARDS -> "después"
            Connector.ALSO -> ""
        }
        override val iceStart = "Hielo por la mañana"
        override val fogStarts = listOf("Niebla al empezar", "Primero niebla")
        override val snowStart = "Nieve al empezar"
        override val wetStart = "Mañana mojada"
        override val icyStart = "Comienzo gélido"
        override val frostyStart = "Comienzo con helada"
        override fun thunderBase(base: ThunderBase, t: String) = when (base) {
            ThunderBase.HOT -> "calor de hasta $t"
            ThunderBase.SULTRY -> "bochorno de hasta $t"
            ThunderBase.CHANGEABLE -> "variable con $t"
        }
        override fun thunderFrom(hour: Int) = "tormentas ${from(hour)}"
        override fun thunderUpTo(t: String) = "tormentas con hasta $t"
        override fun thunderyDay(t: String) = "Día tormentoso con hasta $t"
        override fun thunderyAt(t: String) = "Tormentoso con $t"
        override val freezingRainPhrase = "lluvia helada"
        override val freezingRainSlippery = "Lluvia helada – hoy resbala"
        override val freezingRainAlert = "Atención, lluvia helada"
        override fun blowing(wintry: Boolean) = if (wintry) "ventisca" else "viento y lluvia"
        override fun blowingHood(wintry: Boolean) =
            if (wintry) "Ventisca – hoy mejor con capucha" else "Viento y lluvia – hoy mejor con capucha"
        override fun blowingAt(wintry: Boolean, t: String) = if (wintry) "Ventisca con $t" else "Viento y lluvia con $t"
        override fun beforeRain(before: BeforeRain) = when (before) {
            BeforeRain.FRIENDLY -> "Primero soleado"
            BeforeRain.DRY -> "Primero seco"
            BeforeRain.GREY -> "Día gris"
        }
        override fun beforeRainAt(before: BeforeRain, t: String) = when (before) {
            BeforeRain.FRIENDLY -> "Soleado con $t"
            BeforeRain.DRY -> "Seco con $t"
            BeforeRain.GREY -> "Día gris con $t"
        }
        override fun precipFrom(kind: Condition, hour: Int) = "${noun(kind)} ${from(hour)}"
        override fun precipThenDry(kind: Condition, dryHour: Int) = "${noun(kind)}, ${dryFrom(dryHour)}"
        override fun dryFrom(hour: Int) = "seco ${from(hour)}"
        override fun precipUntilDryHeadline(kind: Condition, dryHour: Int, t: String?) =
            "${noun(kind)} ${until(dryHour)}, luego seco" + (t?.let { " con $it" } ?: "")
        override fun precipAt(kind: Condition, t: String) = "${noun(kind)} con $t"
        override fun wetDay(kind: Condition, t: String) = when (kind) {
            Condition.DRIZZLE -> listOf("Llovizna y cielo gris, máx. $t", "Apagado con llovizna, $t")
            Condition.SHOWERS -> listOf("Chubascos con hasta $t", "Chubascos a ratos con $t")
            Condition.HEAVY_RAIN -> listOf("Lluvia intensa, máx. $t", "Muy húmedo con $t")
            Condition.SNOW -> listOf("Día de nieve, máx. $t", "Invernal, con nieve y $t")
            Condition.SNOW_SHOWERS -> listOf("Chubascos de nieve a ratos, $t", "Chubascos de nieve con $t")
            else -> listOf("Día lluvioso, máx. $t", "Lluvia y cielo gris, máx. $t")
        }
        override fun precipName(kind: Condition) = when (kind) {
            Condition.DRIZZLE -> "llovizna"
            Condition.SHOWERS -> "chubascos"
            Condition.HEAVY_RAIN -> "lluvia intensa"
            Condition.FREEZING_RAIN -> "lluvia helada"
            Condition.SNOW -> "nieve"
            Condition.SNOW_SHOWERS -> "chubascos de nieve"
            Condition.THUNDER -> "tormentas"
            else -> "lluvia"
        }
        override fun dry(day: DryDay, t: String) = when (day) {
            DryDay.HOT -> DryPhrases(listOf("calor de hasta $t"), listOf("Calor de hasta $t – busca la sombra"), listOf("Caluroso, hasta $t"))
            DryDay.SUMMER_CLEAR -> DryPhrases(
                listOf("pleno verano con $t", "veraniego con $t"),
                listOf("Pleno verano: hasta $t y sin nubes"), listOf("Veraniego, hasta $t"),
            )
            DryDay.SUMMER -> DryPhrases(
                listOf("pleno verano con $t", "veraniego con $t"),
                listOf("Veraniego y muy soleado con $t"), listOf("Veraniego, hasta $t"),
            )
            DryDay.GOLDEN_OCTOBER -> golden("octubre dorado", t)
            DryDay.GOLDEN_AUTUMN -> golden("otoño dorado", t)
            DryDay.SPRING -> DryPhrases(listOf("primaveral con $t"), listOf("Primavera pura: sol y hasta $t"), listOf("Primaveral con $t"))
            DryDay.SUNNY_COLD -> DryPhrases(listOf("soleado pero frío, $t"), listOf("Frío pero soleado, máx. $t"), listOf("Soleado pero frío, $t"))
            DryDay.SUNNY_COOL -> DryPhrases(
                listOf("agradable con $t"),
                listOf("Agradable y soleado, hasta $t", "Mucho sol y unos frescos $t"), listOf("Agradable con $t"),
            )
            DryDay.SUNNY -> DryPhrases(
                listOf("soleado con $t", "mucho sol con $t"),
                listOf("Mucho sol, hasta $t", "Soleado y agradable con $t"), listOf("Soleado con $t"),
            )
            DryDay.PERMAFROST -> DryPhrases(listOf("helada todo el día, $t"), listOf("Helada todo el día, máx. $t"), listOf("Gélido con $t"))
            DryDay.FOG -> DryPhrases(listOf("cielo apagado con $t"), listOf("Día de niebla, máx. $t"), listOf("Niebla y cielo apagado, $t"))
            DryDay.FOG_AGAIN -> DryPhrases(emptyList(), listOf("Niebla persistente, apenas $t"), listOf("Niebla y cielo apagado, $t"))
            DryDay.OVERCAST -> DryPhrases(
                listOf("gris con $t"),
                listOf("Día gris, máx. $t", "Gris total, hasta $t"), listOf("Gris con $t"),
            )
            DryDay.CHANGEABLE -> DryPhrases(listOf("variable con $t"), listOf("Variable, hasta $t"), listOf("Variable con $t"))
            DryDay.MIXED -> DryPhrases(
                listOf("nubes y claros con $t"),
                listOf("Sol y nubes, hasta $t", "Sol y nubes alternándose, hasta $t"), listOf("Nubes y claros con $t"),
            )
        }

        private fun golden(name: String, t: String): DryPhrases =
            DryPhrases(listOf("$name con $t"), listOf("${cap(name)}: sol a raudales con $t"), listOf("${cap(name)} con $t"))

        override fun isolated(snow: Boolean) = if (snow) "algún copo" else "algún chubasco"

        override fun stillDark(sunrise: String?) = if (sunrise != null) "Aún de noche, amanece a las $sunrise" else "Aún de noche"
        override val alreadyDark = "Ya es de noche"
        override fun wetNow(kind: Condition, dryHour: Int?): String {
            val verb = when (kind) {
                Condition.DRIZZLE -> "llovizna"
                Condition.SNOW, Condition.SNOW_SHOWERS -> "nieva"
                Condition.FREEZING_RAIN -> "cae lluvia helada"
                Condition.THUNDER -> "hay tormenta"
                Condition.HEAVY_RAIN -> "diluvia"
                Condition.RAIN, Condition.SHOWERS -> "llueve"
                else -> "llueve un poco"
            }
            return if (dryHour != null) "$verb, hacia ${at(dryHour)} escampa" else "$verb y seguirá un buen rato"
        }
        override fun wetSoon(kind: Condition, hour: Int?): String {
            val time = if (hour == null) "en breve" else from(hour)
            return when (kind) {
                Condition.FREEZING_RAIN -> "aún seco, pero $time puede caer lluvia helada"
                Condition.SNOW, Condition.SNOW_SHOWERS -> "aún seco, pero $time puede nevar"
                Condition.THUNDER -> "aún seco, pero $time puede haber tormenta"
                else -> "aún seco, pero $time puede llover"
            }
        }
        override fun dryNow(sky: DrySky) = when (sky) {
            DrySky.FOG -> "niebla, pero seco las próximas horas"
            DrySky.GREY -> "gris, pero seco las próximas horas"
            DrySky.SUNNY -> "soleado y seco las próximas horas"
            DrySky.PLAIN -> "seco las próximas horas"
        }
        override val iceNowHazard = "Aceras y calles se vuelven pistas de hielo – ve con calma y date tiempo."
        override val iceSoonHazard = "Se acerca lluvia helada: aceras y calles se volverán pistas de hielo – ve con calma."
        override fun slipperyHazard(temp: String) = "Riesgo de hielo con $temp – cuidado en aceras y puentes."
        override fun visibilityHazard(distance: String) = "Visibilidad reducida a $distance en algunos puntos – al volante, luces encendidas."
        override fun visibility(band: VisibilityBand, meters: Int) = when (band) {
            VisibilityBand.UNDER_100_M -> "menos de 100 m"
            VisibilityBand.ABOUT_METERS -> "unos $meters m"
            VisibilityBand.JUST_OVER_1_KM -> "algo más de 1 km"
            VisibilityBand.ABOUT_1_5_KM -> "unos 1,5 km"
            VisibilityBand.NEARLY_2_KM -> "casi 2 km"
        }
        override val fogHazard = "Niebla – al volante, luces encendidas y distancia de seguridad."
        override fun gustHazard(kmh: Int) = "Rachas de hasta $kmh km/h – hoy el paraguas lo tiene difícil."
        override fun frostHazard(temp: String) = "Helada con $temp – reserva tiempo para rascar el parabrisas."

        override fun between(min: String, max: String) = "entre $min y $max"
        override fun rising(start: String, peak: String) = "de $start a $peak"
        override fun falling(start: String, end: String) = "primero $start, luego solo $end"
        override fun around(t: String) = "en torno a $t"
        override val sunPureFirst = "primero sol radiante"
        override val sunMuchFirst = "primero mucho sol"
        override val sunPure = "sol radiante"
        override val sunMuch = "mucho sol"
        override fun sunFrom(hour: Int?, pure: Boolean) = "${if (pure) sunPure else sunMuch} ${fromNoon(hour)}"
        override fun sunThenClouds(hour: Int?) = "primero sol, nubes ${fromNoon(hour)}"
        override val sunAndClouds = "sol y nubes"
        override val mostlyGrey = "casi todo gris"
        override val dullFoggy = "apagado y con niebla"
        override val changeable = "variable"
        override fun thunderPossible(hail: Boolean, hour: Int?): String {
            val what = if (hail) "posibles tormentas con granizo" else "posibles tormentas"
            return if (hour == null) what else "$what ${from(hour)}"
        }
        override fun precipFromPop(kind: Condition, hour: Int, pop: Int) = "${noun(kind)} ${from(hour)}, hasta un $pop %"
        override fun precipUntilDry(kind: Condition, dryHour: Int) = "${noun(kind)} ${until(dryHour)}, luego seco"
        override fun precipMostOfDay(kind: Condition, pop: Int) = "${noun(kind)} casi todo el día, hasta un $pop %"
        override fun precipOnAndOff(kind: Condition, pop: Int) = "${noun(kind)} a ratos, hasta un $pop %"
        override val flakesPossible = "posibles copos sueltos"
        override val showersPossible = "posible algún chubasco"
        override val dryWord = "seco"
        override val noRain = "sin lluvia"
        override fun gustsUpTo(kmh: Int) = "rachas de hasta $kmh km/h"
        override val littleWind = "apenas viento"

        override val titleBlackIce = "Hielo"
        override val titleSlippery = "Calzada resbaladiza"
        override val titleFog = "Niebla"
        override val titleStorm = "Temporal"
        override val titleGusts = "Rachas fuertes"
        override val titleThunder = "Tormentas"
        override val titleHeavyRain = "Lluvia intensa"
        override val titleExtremeHeat = "Calor extremo"
        override val titleHeat = "Calor"
        override fun titleUv(veryHigh: Boolean) = if (veryHigh) "UV muy alto" else "UV alto"
        override val freezingRainNow = "Lluvia helada – aceras y calles se vuelven pistas de hielo. Ve con calma y date tiempo."
        override fun freezingRainFrom(hour: Int) = "Lluvia helada ${from(hour)} – aceras y calles se volverán pistas de hielo."
        override fun slipperyWet(temp: String, snow: Boolean) =
            "Con $temp y ${if (snow) "nieve" else "el suelo mojado"}, aceras y calles pueden resbalar – ve con calma."
        override fun hoarFrost(temp: String) = "Con $temp y aire húmedo puede haber escarcha, sobre todo en los puentes."
        override fun icyFrom(hour: Int, kind: Condition, temp: String) =
            "${cap(noun(kind))} ${from(hour)} con $temp – puede resbalar."
        override fun fogVisibility(distance: String) = "Visibilidad de solo $distance a ratos – luces de cruce y distancia de seguridad."
        override val fogPossible = "Posible niebla – luces de cruce y distancia de seguridad."
        override fun stormText(kmh: Int) =
            "Rachas de temporal de hasta $kmh km/h – evita bosques y avenidas con árboles, asegura lo que pueda volar."
        override fun gustText(kmh: Int) = "Rachas de hasta $kmh km/h – asegura lo que pueda volar y ojo con las ramas."
        override val thunderNow = "Tormenta ahora mismo – mejor quédate bajo techo."
        override fun thunderWarning(hour: Int, hail: Boolean) =
            "${if (hail) "Posibles tormentas con granizo" else "Posibles tormentas"} ${from(hour)} – con rayos y truenos, a cubierto."
        override fun heavyRainAmount(hour: Int, mm: Int) =
            "${cap(from(hour))}, hasta $mm l/m² por hora – evita pasos subterráneos y hondonadas."
        override fun heavyRainBursts(hour: Int) = "Chaparrones fuertes ${from(hour)} – evita pasos subterráneos y hondonadas."
        override fun extremeHeat(temp: String) = "Hasta $temp – bebe mucho, evita esfuerzos y quédate a la sombra."
        override fun heat(temp: String) = "Hasta $temp – bebe mucho y evita el sol del mediodía."
        override fun uv(index: Int) = "Índice UV de hasta $index – no olvides la crema solar y algo para la cabeza."

        override val noWeatherTitle = "☀️ Tu Daily Briefing"
        override val noWeatherText = "El tiempo no está disponible ahora – abre la app para actualizar."
        override val noWeatherBigText = "🌦️ No se pudo cargar el tiempo – la app lo actualizará en un momento."
        override fun notificationTitle(emoji: String, temp: String, place: String, feels: String) =
            "$emoji $temp en $place · ${feelsLike(feels)}"
        override fun window(place: String, tomorrow: Boolean, startHour: Int, endHour: Int) =
            "$place ${if (tomorrow) "mañana " else ""}$startHour–$endHour h"
        override fun shortAround(t: String) = "unos $t"
        override fun thunderFromShort(hour: Int) = "tormentas ${from(hour)}"
        override val thunderShort = "tormentas"
        override fun labelFrom(label: String, hour: Int) = "$label ${from(hour)}"
        override fun labelUntil(label: String, hour: Int) = "$label ${until(hour)}"
        override val noNews = "📰 Tus noticias te esperan en la app."
        override fun feelsLike(feels: String) = "sensación $feels"

        override fun condition(condition: Condition, isDay: Boolean) = when (condition) {
            Condition.CLEAR -> if (isDay) "soleado" else "despejado"
            Condition.MOSTLY_CLEAR -> if (isDay) "mayormente soleado" else "mayormente despejado"
            Condition.PARTLY_CLOUDY -> "parcialmente nublado"
            Condition.OVERCAST -> "cubierto"
            Condition.FOG -> "niebla"
            Condition.DRIZZLE -> "llovizna"
            Condition.RAIN -> "lluvia"
            Condition.HEAVY_RAIN -> "lluvia intensa"
            Condition.FREEZING_RAIN -> "lluvia helada"
            Condition.SNOW -> "nieve"
            Condition.SHOWERS -> "chubascos"
            Condition.SNOW_SHOWERS -> "chubascos de nieve"
            Condition.THUNDER -> "tormenta"
        }
        override val rainWord = "Lluvia"
        override val snowWord = "Nieve"
        override val sleetWord = "Aguanieve"
    }

    // =========================================================================================
    // Italiano (del tu)
    // =========================================================================================
    private object It : WeatherStrings {
        /** "dalle 15", "dall'una", "da mezzanotte", "da mezzogiorno". */
        private fun from(hour: Int) = when (val h = Math.floorMod(hour, 24)) {
            0 -> "da mezzanotte"
            1 -> "dall'una"
            12 -> "da mezzogiorno"
            else -> "dalle $h"
        }

        /** "fino alle 15", "fino all'una", "fino a mezzanotte". */
        private fun until(hour: Int) = when (val h = Math.floorMod(hour, 24)) {
            0 -> "fino a mezzanotte"
            1 -> "fino all'una"
            12 -> "fino a mezzogiorno"
            else -> "fino alle $h"
        }

        /** "verso le 15", "verso l'una", "verso mezzanotte". */
        private fun towards(hour: Int) = when (val h = Math.floorMod(hour, 24)) {
            0 -> "verso mezzanotte"
            1 -> "verso l'una"
            12 -> "verso mezzogiorno"
            else -> "verso le $h"
        }
        private fun fromNoon(hour: Int?) = when (hour) {
            null -> "più tardi"
            12, 13 -> "da mezzogiorno"
            else -> from(hour)
        }
        private fun noun(kind: Condition) = precipName(kind)
        private fun cap(text: String) = text.replaceFirstChar { it.uppercase() }

        override val tomorrowPrefix = "Domani: "
        override val headlineFallback = "Il tuo meteo di oggi"
        override fun connector(connector: Connector) = when (connector) {
            Connector.THEN -> "poi"
            Connector.LATER -> "più tardi"
            Connector.AFTERWARDS -> "in seguito"
            Connector.ALSO -> ""
        }
        override val iceStart = "Ghiaccio al mattino"
        override val fogStarts = listOf("Nebbia al risveglio", "Prima nebbia")
        override val snowStart = "Neve al risveglio"
        override val wetStart = "Risveglio bagnato"
        override val icyStart = "Risveglio gelido"
        override val frostyStart = "Gelo al risveglio"
        override fun thunderBase(base: ThunderBase, t: String) = when (base) {
            ThunderBase.HOT -> "caldo fino a $t"
            ThunderBase.SULTRY -> "afa fino a $t"
            ThunderBase.CHANGEABLE -> "variabile con $t"
        }
        override fun thunderFrom(hour: Int) = "temporali ${from(hour)}"
        override fun thunderUpTo(t: String) = "temporali e punte di $t"
        override fun thunderyDay(t: String) = "Giornata temporalesca, fino a $t"
        override fun thunderyAt(t: String) = "Temporalesco con $t"
        override val freezingRainPhrase = "pioggia gelata"
        override val freezingRainSlippery = "Pioggia gelata – oggi si scivola"
        override val freezingRainAlert = "Attenzione, pioggia gelata"
        override fun blowing(wintry: Boolean) = if (wintry) "neve e vento" else "vento e pioggia"
        override fun blowingHood(wintry: Boolean) =
            if (wintry) "Neve e vento – oggi meglio il cappuccio" else "Vento e pioggia – oggi meglio il cappuccio"
        override fun blowingAt(wintry: Boolean, t: String) = if (wintry) "Neve e vento con $t" else "Vento e pioggia con $t"
        override fun beforeRain(before: BeforeRain) = when (before) {
            BeforeRain.FRIENDLY -> "Prima sereno"
            BeforeRain.DRY -> "Prima asciutto"
            BeforeRain.GREY -> "Giornata grigia"
        }
        override fun beforeRainAt(before: BeforeRain, t: String) = when (before) {
            BeforeRain.FRIENDLY -> "Sereno con $t"
            BeforeRain.DRY -> "Asciutto con $t"
            BeforeRain.GREY -> "Giornata grigia con $t"
        }
        override fun precipFrom(kind: Condition, hour: Int) = "${noun(kind)} ${from(hour)}"
        override fun precipThenDry(kind: Condition, dryHour: Int) = "${noun(kind)}, ${dryFrom(dryHour)}"
        override fun dryFrom(hour: Int) = "asciutto ${from(hour)}"
        override fun precipUntilDryHeadline(kind: Condition, dryHour: Int, t: String?) =
            "${noun(kind)} ${until(dryHour)}, poi asciutto" + (t?.let { " con $it" } ?: "")
        override fun precipAt(kind: Condition, t: String) = "${noun(kind)} con $t"
        override fun wetDay(kind: Condition, t: String) = when (kind) {
            Condition.DRIZZLE -> listOf("Pioviggine e grigio, max $t", "Cupo con pioviggine, $t")
            Condition.SHOWERS -> listOf("Rovesci, fino a $t", "Rovesci a tratti con $t")
            Condition.HEAVY_RAIN -> listOf("Pioggia forte, max $t", "Molto bagnato con $t")
            Condition.SNOW -> listOf("Giornata di neve, max $t", "Invernale, neve e $t")
            Condition.SNOW_SHOWERS -> listOf("Rovesci di neve a tratti, $t", "Rovesci di neve con $t")
            else -> listOf("Giornata piovosa, max $t", "Pioggia e grigio, max $t")
        }
        override fun precipName(kind: Condition) = when (kind) {
            Condition.DRIZZLE -> "pioviggine"
            Condition.SHOWERS -> "rovesci"
            Condition.HEAVY_RAIN -> "pioggia forte"
            Condition.FREEZING_RAIN -> "pioggia gelata"
            Condition.SNOW -> "neve"
            Condition.SNOW_SHOWERS -> "rovesci di neve"
            Condition.THUNDER -> "temporali"
            else -> "pioggia"
        }
        override fun dry(day: DryDay, t: String) = when (day) {
            DryDay.HOT -> DryPhrases(listOf("caldo fino a $t"), listOf("Caldo fino a $t – meglio all'ombra"), listOf("Caldo con punte di $t"))
            DryDay.SUMMER_CLEAR -> DryPhrases(
                listOf("piena estate con $t", "estivo con $t"),
                listOf("Piena estate: fino a $t e niente nuvole"), listOf("Estivo, fino a $t"),
            )
            DryDay.SUMMER -> DryPhrases(
                listOf("piena estate con $t", "estivo con $t"),
                listOf("Estivo e molto soleggiato con $t"), listOf("Estivo, fino a $t"),
            )
            DryDay.GOLDEN_OCTOBER -> golden("ottobrata", t)
            DryDay.GOLDEN_AUTUMN -> golden("autunno dorato", t)
            DryDay.SPRING -> DryPhrases(listOf("primaverile con $t"), listOf("Primavera piena: sole e fino a $t"), listOf("Primaverile con $t"))
            DryDay.SUNNY_COLD -> DryPhrases(listOf("soleggiato ma freddo, $t"), listOf("Freddo ma soleggiato, max $t"), listOf("Soleggiato ma freddo, $t"))
            DryDay.SUNNY_COOL -> DryPhrases(
                listOf("bel tempo con $t"),
                listOf("Bello e soleggiato, fino a $t", "Tanto sole e freschi $t"), listOf("Bel tempo con $t"),
            )
            DryDay.SUNNY -> DryPhrases(
                listOf("soleggiato con $t", "tanto sole con $t"),
                listOf("Tanto sole, fino a $t", "Soleggiato e piacevole con $t"), listOf("Soleggiato con $t"),
            )
            DryDay.PERMAFROST -> DryPhrases(listOf("gelo tutto il giorno, $t"), listOf("Gelo tutto il giorno, max $t"), listOf("Gelido con $t"))
            DryDay.FOG -> DryPhrases(listOf("cupo con $t"), listOf("Giornata nebbiosa, max $t"), listOf("Nebbioso e cupo, $t"))
            DryDay.FOG_AGAIN -> DryPhrases(emptyList(), listOf("Nebbia ostinata, appena $t"), listOf("Nebbioso e cupo, $t"))
            DryDay.OVERCAST -> DryPhrases(
                listOf("grigio con $t"),
                listOf("Giornata grigia, max $t", "Grigio su grigio, fino a $t"), listOf("Grigio con $t"),
            )
            DryDay.CHANGEABLE -> DryPhrases(listOf("variabile con $t"), listOf("Variabile, fino a $t"), listOf("Variabile con $t"))
            DryDay.MIXED -> DryPhrases(
                listOf("poco nuvoloso con $t"),
                listOf("Sole e nuvole, fino a $t", "Sole e nuvole a turno, fino a $t"), listOf("Nuvoloso con schiarite, $t"),
            )
        }

        private fun golden(name: String, t: String): DryPhrases =
            DryPhrases(listOf("$name con $t"), listOf("${cap(name)}: sole pieno e $t"), listOf("${cap(name)} con $t"))

        override fun isolated(snow: Boolean) = if (snow) "qualche fiocco" else "qualche rovescio"

        override fun stillDark(sunrise: String?) = if (sunrise != null) "Ancora buio, il sole sorge alle $sunrise" else "Ancora buio"
        override val alreadyDark = "Già buio"
        override fun wetNow(kind: Condition, dryHour: Int?): String {
            val verb = when (kind) {
                Condition.DRIZZLE -> "pioviggina"
                Condition.SNOW, Condition.SNOW_SHOWERS -> "nevica"
                Condition.FREEZING_RAIN -> "cade pioggia gelata"
                Condition.THUNDER -> "c'è un temporale"
                Condition.HEAVY_RAIN -> "diluvia"
                Condition.RAIN, Condition.SHOWERS -> "piove"
                else -> "piove un po'"
            }
            return if (dryHour != null) "$verb, ${towards(dryHour)} smette" else "$verb e durerà ancora un po'"
        }
        override fun wetSoon(kind: Condition, hour: Int?): String {
            val time = if (hour == null) "a breve" else from(hour)
            return when (kind) {
                Condition.FREEZING_RAIN -> "ancora asciutto, $time possibile pioggia gelata"
                Condition.SNOW, Condition.SNOW_SHOWERS -> "ancora asciutto, $time può nevicare"
                Condition.THUNDER -> "ancora asciutto, $time possibili temporali"
                else -> "ancora asciutto, $time può piovere"
            }
        }
        override fun dryNow(sky: DrySky) = when (sky) {
            DrySky.FOG -> "nebbia, ma asciutto per le prossime ore"
            DrySky.GREY -> "grigio, ma asciutto per le prossime ore"
            DrySky.SUNNY -> "soleggiato e asciutto per le prossime ore"
            DrySky.PLAIN -> "asciutto per le prossime ore"
        }
        override val iceNowHazard = "Marciapiedi e strade diventano lastre di ghiaccio – vai piano e prenditi tempo."
        override val iceSoonHazard = "Pioggia gelata in arrivo: marciapiedi e strade diventeranno lastre di ghiaccio – vai piano."
        override fun slipperyHazard(temp: String) = "Rischio ghiaccio con $temp – attenzione su marciapiedi e ponti."
        override fun visibilityHazard(distance: String) = "Visibilità ridotta a $distance in alcuni punti – alla guida, luci accese."
        override fun visibility(band: VisibilityBand, meters: Int) = when (band) {
            VisibilityBand.UNDER_100_M -> "meno di 100 m"
            VisibilityBand.ABOUT_METERS -> "circa $meters m"
            VisibilityBand.JUST_OVER_1_KM -> "poco più di 1 km"
            VisibilityBand.ABOUT_1_5_KM -> "circa 1,5 km"
            VisibilityBand.NEARLY_2_KM -> "quasi 2 km"
        }
        override val fogHazard = "Nebbia – alla guida luci accese e distanza di sicurezza."
        override fun gustHazard(kmh: Int) = "Raffiche fino a $kmh km/h – oggi l'ombrello avrà vita dura."
        override fun frostHazard(temp: String) = "Gelo con $temp – metti in conto di grattare il parabrezza."

        override fun between(min: String, max: String) = "tra $min e $max"
        override fun rising(start: String, peak: String) = "da $start a $peak"
        override fun falling(start: String, end: String) = "prima $start, poi solo $end"
        override fun around(t: String) = "circa $t"
        override val sunPureFirst = "prima sole pieno"
        override val sunMuchFirst = "prima tanto sole"
        override val sunPure = "sole pieno"
        override val sunMuch = "tanto sole"
        override fun sunFrom(hour: Int?, pure: Boolean) = "${if (pure) sunPure else sunMuch} ${fromNoon(hour)}"
        override fun sunThenClouds(hour: Int?) = "prima sole, nuvole ${fromNoon(hour)}"
        override val sunAndClouds = "sole e nuvole"
        override val mostlyGrey = "per lo più grigio"
        override val dullFoggy = "cupo e nebbioso"
        override val changeable = "variabile"
        override fun thunderPossible(hail: Boolean, hour: Int?): String {
            val what = if (hail) "possibili temporali con grandine" else "possibili temporali"
            return if (hour == null) what else "$what ${from(hour)}"
        }
        override fun precipFromPop(kind: Condition, hour: Int, pop: Int) = "${noun(kind)} ${from(hour)}, probabilità $pop%"
        override fun precipUntilDry(kind: Condition, dryHour: Int) = "${noun(kind)} ${until(dryHour)}, poi asciutto"
        override fun precipMostOfDay(kind: Condition, pop: Int) = "${noun(kind)} quasi tutto il giorno, probabilità $pop%"
        override fun precipOnAndOff(kind: Condition, pop: Int) = "${noun(kind)} a tratti, probabilità $pop%"
        override val flakesPossible = "possibile qualche fiocco"
        override val showersPossible = "possibile qualche rovescio"
        override val dryWord = "asciutto"
        override val noRain = "niente pioggia"
        override fun gustsUpTo(kmh: Int) = "raffiche fino a $kmh km/h"
        override val littleWind = "quasi niente vento"

        override val titleBlackIce = "Ghiaccio"
        override val titleSlippery = "Strade scivolose"
        override val titleFog = "Nebbia"
        override val titleStorm = "Tempesta"
        override val titleGusts = "Raffiche forti"
        override val titleThunder = "Temporali"
        override val titleHeavyRain = "Pioggia forte"
        override val titleExtremeHeat = "Caldo estremo"
        override val titleHeat = "Caldo"
        override fun titleUv(veryHigh: Boolean) = if (veryHigh) "UV molto alti" else "UV alti"
        override val freezingRainNow = "Pioggia gelata – marciapiedi e strade diventano lastre di ghiaccio. Vai piano e prenditi tempo."
        override fun freezingRainFrom(hour: Int) = "Pioggia gelata ${from(hour)} – marciapiedi e strade diventeranno lastre di ghiaccio."
        override fun slipperyWet(temp: String, snow: Boolean) =
            "Con $temp e ${if (snow) "la neve" else "il bagnato"} marciapiedi e strade possono essere scivolosi – vai piano."
        override fun hoarFrost(temp: String) = "Con $temp e aria umida può formarsi brina, soprattutto sui ponti."
        override fun icyFrom(hour: Int, kind: Condition, temp: String) =
            "${cap(noun(kind))} ${from(hour)} con $temp – può diventare scivoloso."
        override fun fogVisibility(distance: String) = "Visibilità a tratti di soli $distance – anabbaglianti accesi e distanza di sicurezza."
        override val fogPossible = "Possibile nebbia – anabbaglianti accesi e distanza di sicurezza."
        override fun stormText(kmh: Int) =
            "Raffiche di tempesta fino a $kmh km/h – evita boschi e viali alberati, fissa gli oggetti sciolti."
        override fun gustText(kmh: Int) = "Raffiche fino a $kmh km/h – fissa gli oggetti sciolti e attenzione ai rami che cadono."
        override val thunderNow = "Temporale in corso – meglio restare al chiuso."
        override fun thunderWarning(hour: Int, hail: Boolean) =
            "${if (hail) "Possibili temporali con grandine" else "Possibili temporali"} ${from(hour)} – con tuoni e fulmini, al riparo."
        override fun heavyRainAmount(hour: Int, mm: Int) =
            "${cap(from(hour))} fino a $mm mm all'ora – evita sottopassi e avvallamenti."
        override fun heavyRainBursts(hour: Int) = "${cap(from(hour))} acquazzoni forti – evita sottopassi e avvallamenti."
        override fun extremeHeat(temp: String) = "Fino a $temp – bevi molto, evita gli sforzi e resta all'ombra."
        override fun heat(temp: String) = "Fino a $temp – bevi molto ed evita il sole di mezzogiorno."
        override fun uv(index: Int) = "Indice UV fino a $index – non dimenticare crema solare e cappello."

        override val noWeatherTitle = "☀️ Il tuo Daily Briefing"
        override val noWeatherText = "Meteo non disponibile al momento – apri l'app per aggiornare."
        override val noWeatherBigText = "🌦️ Non è stato possibile caricare il meteo – l'app lo aggiornerà a breve."
        override fun notificationTitle(emoji: String, temp: String, place: String, feels: String) =
            "$emoji $temp a $place · ${feelsLike(feels)}"
        override fun window(place: String, tomorrow: Boolean, startHour: Int, endHour: Int) =
            "$place ${if (tomorrow) "domani " else ""}$startHour–$endHour"
        override fun shortAround(t: String) = "circa $t"
        override fun thunderFromShort(hour: Int) = "temporali ${from(hour)}"
        override val thunderShort = "temporali"
        override fun labelFrom(label: String, hour: Int) = "$label ${from(hour)}"
        override fun labelUntil(label: String, hour: Int) = "$label ${until(hour)}"
        override val noNews = "📰 Le tue notizie ti aspettano nell'app."
        override fun feelsLike(feels: String) = "percepita $feels"

        override fun condition(condition: Condition, isDay: Boolean) = when (condition) {
            Condition.CLEAR -> if (isDay) "soleggiato" else "sereno"
            Condition.MOSTLY_CLEAR -> if (isDay) "prevalentemente soleggiato" else "prevalentemente sereno"
            Condition.PARTLY_CLOUDY -> "parzialmente nuvoloso"
            Condition.OVERCAST -> "coperto"
            Condition.FOG -> "nebbia"
            Condition.DRIZZLE -> "pioviggine"
            Condition.RAIN -> "pioggia"
            Condition.HEAVY_RAIN -> "pioggia forte"
            Condition.FREEZING_RAIN -> "pioggia gelata"
            Condition.SNOW -> "neve"
            Condition.SHOWERS -> "rovesci"
            Condition.SNOW_SHOWERS -> "rovesci di neve"
            Condition.THUNDER -> "temporale"
        }
        override val rainWord = "Pioggia"
        override val snowWord = "Neve"
        override val sleetWord = "Nevischio"
    }
}

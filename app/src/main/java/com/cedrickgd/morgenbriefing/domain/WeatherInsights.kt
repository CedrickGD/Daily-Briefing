package com.cedrickgd.morgenbriefing.domain

import com.cedrickgd.morgenbriefing.data.model.DayWeather
import com.cedrickgd.morgenbriefing.data.model.WeatherSnapshot
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.BeforeRain
import com.cedrickgd.morgenbriefing.i18n.Connector
import com.cedrickgd.morgenbriefing.i18n.DryDay
import com.cedrickgd.morgenbriefing.i18n.DrySky
import com.cedrickgd.morgenbriefing.i18n.FormatStrings
import com.cedrickgd.morgenbriefing.i18n.ThunderBase
import com.cedrickgd.morgenbriefing.i18n.VisibilityBand
import com.cedrickgd.morgenbriefing.i18n.WeatherStrings
import kotlin.math.max

/**
 * Pure, deterministic texts derived from a [WeatherSnapshot] in the interface language. No
 * Android APIs, fully unit-tested in WeatherInsightsTest.
 *
 * This object decides WHAT to say (thresholds, order, which facts matter); [WeatherStrings]
 * decides how each language says it.
 *
 * Wording varies a little from day to day (chosen by a hash of the window's date), but the
 * same snapshot always produces the same texts.
 */
object WeatherInsights {

    private const val HEADLINE_MAX = 60
    private const val CLOTHING_MARKER = "👕"

    /** Friendly one-liner for the serif headline, e.g. "Nebel zum Start, dann goldener Oktober mit 21°." */
    fun headline(s: WeatherSnapshot, lang: AppLanguage): String {
        val w = WeatherStrings.of(lang)
        val day = DayFacts(s.day)
        val seed = s.day.date
        val prefix = if (s.day.isTomorrow) w.tomorrowPrefix else ""
        val start = if (s.day.isTomorrow) tomorrowStart(w, day, seed) else todayStart(w, HomeFacts(s.home), day, seed)

        val all = dayCandidates(w, day, start, seed).mapNotNull { candidate ->
            when (candidate) {
                is Candidate.Alone -> candidate.text
                is Candidate.After -> start?.let { "${it.text}, ${candidate.text}" }
            }
        }
        // "Morgen: Goldener Oktober: …" reads clumsy — avoid a second colon after the prefix.
        val texts = if (prefix.isEmpty()) all else all.filter { ':' !in it }.ifEmpty { all }
        val chosen = texts.firstOrNull { prefix.length + it.length + 1 <= HEADLINE_MAX }
            ?: texts.minByOrNull { it.length }
            ?: w.headlineFallback
        return "$prefix$chosen."
    }

    /** One or two sentences about stepping outside at home right now. */
    fun homeNote(s: WeatherSnapshot, lang: AppLanguage): String {
        val w = WeatherStrings.of(lang)
        val home = HomeFacts(s.home)
        val light = when {
            home.beforeSunrise -> w.stillDark(if (home.sunrise != null) Fmt.clock(s.home.sunrise) else null)
            home.afterSunset -> w.alreadyDark
            else -> null
        }
        val weather = weatherClause(w, home)
        val first = (if (light != null) "$light – $weather" else weather.capitalizeFirst()) + "."
        return listOfNotNull(first, hazardSentence(w, home)).joinToString(" ")
    }

    /** One sentence about the day window, e.g. "Von 13° auf 21°, ab Mittag Sonne pur, trocken." */
    fun daySummary(s: WeatherSnapshot, lang: AppLanguage): String {
        val w = WeatherStrings.of(lang)
        val day = DayFacts(s.day)
        val parts = mutableListOf(temperatureTrend(w, day))
        skyPhrase(w, day)?.let(parts::add)
        parts += precipitationPhrase(w, day)
        windPhrase(w, day)?.let(parts::add)
        return parts.joinToString(", ").capitalizeFirst() + "."
    }

    fun clothing(s: WeatherSnapshot, lang: AppLanguage): ClothingAdvice = ClothingAdvisor.advise(s, lang)

    /** Fog/visibility, ice, storm, thunder, heavy rain, heat, strong UV — empty on a calm day. */
    fun warnings(s: WeatherSnapshot, lang: AppLanguage): List<WeatherWarning> {
        val w = WeatherStrings.of(lang)
        val home = HomeFacts(s.home)
        val day = DayFacts(s.day)
        val out = mutableListOf<WeatherWarning>()

        iceWarning(w, home, day)?.let(out::add)
        fogWarning(w, lang, home)?.let(out::add)
        gustWarning(w, home, day)?.let(out::add)
        thunderWarning(w, home, day)?.let(out::add)
        heavyRainWarning(w, home, day)?.let(out::add)
        heatWarning(w, day)?.let(out::add)
        uvWarning(w, s.day)?.let(out::add)

        // Stable sort: within a level the order above (most pressing for the morning) is kept.
        return out.distinctBy { it.title }.sortedByDescending { it.level.ordinal }
    }

    /**
     * Text for the morning notification. [headlines] are the top stories of the morning (may be
     * empty when nothing could be fetched).
     */
    fun notification(s: WeatherSnapshot?, headlines: List<String>, lang: AppLanguage): NotificationText {
        val w = WeatherStrings.of(lang)
        val news = newsLine(w, headlines)
        if (s == null) {
            val text = headlines.firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: w.noWeatherText
            val bigText = listOf(w.noWeatherBigText, news)
            return NotificationText(title = w.noWeatherTitle, text = text, bigText = bigText.joinToString("\n"))
        }

        val current = s.home.current
        val condition = Condition.fromCode(current.code)
        val title = w.notificationTitle(
            emoji = condition.emoji(current.isDay),
            temp = Fmt.temp(current.temp),
            place = s.home.place.name,
            feels = Fmt.temp(current.feels),
        )

        val day = DayFacts(s.day)
        val window = w.window(s.day.place.name, s.day.isTomorrow, s.day.startHour, s.day.endHour)
        val clothing = clothing(s, lang)
        val text = "$window: ${shortTrend(w, day)}, ${conditionLabel(w, day)} · ${clothing.short.removeSuffix(".")}"
        val bigText = listOf(
            homeNote(s, lang),
            "$window: ${daySummary(s, lang)}",
            "${clothingMarker(clothing)} ${clothing.short}",
            news,
        ).joinToString("\n")
        return NotificationText(title = title, text = text, bigText = bigText)
    }

    /**
     * Emoji in front of the clothing line of the notification: the first advised item (🧥 on a
     * jacket day, 👕 on a T-shirt day), 👕 without any. Notifications.kt swaps it like every
     * emoji of the text where the device's font cannot draw it (Android 7).
     */
    internal fun clothingMarker(advice: ClothingAdvice): String =
        advice.items.firstOrNull()?.emoji?.takeIf { it.isNotBlank() } ?: CLOTHING_MARKER

    // ---------------------------------------------------------------------------------------
    // Headline
    // ---------------------------------------------------------------------------------------

    /** How the morning starts; [connector] links it to the rest of the day ("dann", "später"). */
    private data class Start(val kind: StartKind, val text: String, val connector: Connector)

    private enum class StartKind { ICE, FOG, WET, FROST }

    /** A headline candidate without the final period. */
    private sealed interface Candidate {
        /** A complete sentence on its own. */
        data class Alone(val text: String) : Candidate

        /** Follows "<start>, " and already contains its connector. */
        data class After(val text: String) : Candidate
    }

    /** "<connector> <phrase>"; a language may leave a connector empty ("Frosty start, snow at -1°"). */
    private fun joined(w: WeatherStrings, connector: Connector, phrase: String): String {
        val word = w.connector(connector)
        return if (word.isEmpty()) phrase else "$word $phrase"
    }

    private fun todayStart(w: WeatherStrings, home: HomeFacts, day: DayFacts, seed: String): Start? {
        // Snowing (or raining) already on a wet day: the wet day is no "later" — "Frostiger Start,
        // dazu Schnee bei -1°", not "…, später Schnee …" while it snows.
        val frostConnector = if (home.wetNow && day.isWetDay) Connector.ALSO else Connector.LATER
        return when {
            home.freezingRain -> Start(StartKind.ICE, w.iceStart, Connector.LATER)
            home.condition == Condition.FOG -> Start(StartKind.FOG, pick(seed, 1, w.fogStarts), Connector.THEN)
            home.wetNow && !day.isWetDay && day.thunder == null ->
                Start(StartKind.WET, if (home.condition.isWintry) w.snowStart else w.wetStart, Connector.AFTERWARDS)
            home.now.temp <= -6 -> Start(StartKind.FROST, w.icyStart, frostConnector)
            home.now.temp <= 0 -> Start(StartKind.FROST, w.frostyStart, frostConnector)
            else -> null
        }
    }

    /** For tomorrow's window the current evening says nothing; look at the window's first hour. */
    private fun tomorrowStart(w: WeatherStrings, day: DayFacts, seed: String): Start? {
        val first = day.hours.firstOrNull() ?: return null
        return when {
            first.condition == Condition.FOG -> Start(StartKind.FOG, pick(seed, 1, w.fogStarts), Connector.THEN)
            first.temp <= 0 -> Start(StartKind.FROST, w.frostyStart, Connector.LATER)
            else -> null
        }
    }

    /** Candidates ordered by preference; the first one that fits [HEADLINE_MAX] wins. */
    private fun dayCandidates(w: WeatherStrings, d: DayFacts, start: Start?, seed: String): List<Candidate> {
        val t = Fmt.temp(d.maxTemp)
        val conn = start?.connector ?: Connector.THEN
        val out = mutableListOf<Candidate>()
        val thunder = d.thunder
        when {
            thunder != null -> {
                val base = w.thunderBase(
                    when {
                        d.maxTemp >= 30 -> ThunderBase.HOT
                        d.maxTemp >= 25 -> ThunderBase.SULTRY
                        else -> ThunderBase.CHANGEABLE
                    },
                    t,
                )
                if (thunder !== d.hours.first()) {
                    val from = w.thunderFrom(thunder.hour)
                    out += Candidate.After(joined(w, conn, "$base, $from"))
                    out += Candidate.After(from)
                    out += Candidate.Alone("$base, $from".capitalizeFirst())
                    out += Candidate.Alone(from.capitalizeFirst())
                } else {
                    out += Candidate.After(joined(w, conn, w.thunderUpTo(t)))
                    out += Candidate.Alone(w.thunderyDay(t))
                    out += Candidate.Alone(w.thunderyAt(t))
                }
            }
            d.freezing != null -> {
                out += Candidate.After(joined(w, conn, w.freezingRainPhrase))
                out += Candidate.Alone(w.freezingRainSlippery)
                out += Candidate.Alone(w.freezingRainAlert)
            }
            d.isWetDay && d.maxGust >= 50 -> {
                val wintry = d.wetKind.isWintry
                out += Candidate.After(joined(w, conn, w.blowing(wintry)))
                out += Candidate.Alone(w.blowingHood(wintry))
                out += Candidate.Alone(w.blowingAt(wintry, t))
            }
            d.isWetDay -> wetCandidates(w, d, conn, t, seed, out)
            else -> dryCandidates(w, d, start, conn, t, seed, out)
        }
        return out
    }

    private fun wetCandidates(
        w: WeatherStrings,
        d: DayFacts,
        conn: Connector,
        t: String,
        seed: String,
        out: MutableList<Candidate>,
    ) {
        val kind = d.wetKind
        val firstWet = d.firstWet
        val dryFrom = d.dryFrom
        when {
            d.rainStartsLater && firstWet != null -> {
                val before = dominantCondition(d.hours.takeWhile { it !== firstWet }.map { it.condition })
                val base = when {
                    before?.isSunny == true -> BeforeRain.FRIENDLY
                    before == Condition.PARTLY_CLOUDY -> BeforeRain.DRY
                    else -> BeforeRain.GREY
                }
                val from = w.precipFrom(kind, firstWet.hour)
                out += Candidate.After(from)
                out += Candidate.Alone("${w.beforeRainAt(base, t)}, $from")
                out += Candidate.Alone("${w.beforeRain(base)}, $from")
                out += Candidate.Alone(from.capitalizeFirst())
            }
            dryFrom != null -> {
                out += Candidate.After(joined(w, conn, w.precipThenDry(kind, dryFrom)))
                out += Candidate.After(w.dryFrom(dryFrom))
                out += Candidate.Alone(w.precipUntilDryHeadline(kind, dryFrom, t).capitalizeFirst())
                out += Candidate.Alone(w.precipUntilDryHeadline(kind, dryFrom, null).capitalizeFirst())
            }
            else -> {
                out += Candidate.After(joined(w, conn, w.precipAt(kind, t)))
                val alone = w.wetDay(kind, t)
                val first = pick(seed, 2, alone)
                out += Candidate.Alone(first)
                alone.filter { it != first }.forEach { out += Candidate.Alone(it) }
                out += Candidate.Alone(w.precipName(kind).capitalizeFirst())
            }
        }
    }

    private fun dryCandidates(
        w: WeatherStrings,
        d: DayFacts,
        start: Start?,
        conn: Connector,
        t: String,
        seed: String,
        out: MutableList<Candidate>,
    ) {
        val month = d.month
        val kind = when {
            d.maxTemp >= 30 -> DryDay.HOT
            d.mostlySunny && d.maxTemp >= 25 -> if (d.allSunny && !d.showersPossible) DryDay.SUMMER_CLEAR else DryDay.SUMMER
            month != null && month in 9..11 && d.mostlySunny && d.maxTemp >= 15 ->
                if (month == 10) DryDay.GOLDEN_OCTOBER else DryDay.GOLDEN_AUTUMN
            month != null && month in 3..5 && d.mostlySunny && d.maxTemp >= 15 -> DryDay.SPRING
            d.mostlySunny && d.maxTemp <= 2 -> DryDay.SUNNY_COLD
            d.mostlySunny && d.maxTemp < 15 -> DryDay.SUNNY_COOL
            d.mostlySunny -> DryDay.SUNNY
            d.maxTemp < 0 -> DryDay.PERMAFROST
            d.main == Condition.FOG -> if (start?.kind == StartKind.FOG) DryDay.FOG_AGAIN else DryDay.FOG
            d.main == Condition.OVERCAST -> DryDay.OVERCAST
            d.main.isWet -> DryDay.CHANGEABLE
            else -> DryDay.MIXED
        }
        val phrases = w.dry(kind, t)
        // A frosty start already says "Frost"; "…, später Dauerfrost" adds nothing.
        val after = if (kind == DryDay.PERMAFROST && start?.kind == StartKind.FROST) {
            emptyList()
        } else {
            phrases.after.map { joined(w, conn, it) }
        }
        val alone = listOf(pick(seed, PICK_SALT[kind] ?: 0, phrases.aloneChoice)) + phrases.aloneRest

        val tail = when {
            d.showersPossible || d.main.isWet -> w.isolated(snow = d.maxTemp <= 2)
            else -> null
        }
        if (tail != null) {
            // Keeping the shower hint beats keeping the start of the morning.
            after.forEach { out += Candidate.After("$it, $tail") }
            out += Candidate.Alone("${alone.last()}, $tail")
        }
        after.forEach { out += Candidate.After(it) }
        alone.forEach { out += Candidate.Alone(it) }
    }

    /** Salt of the daily wording choice per dry kind (kept stable so the German texts never change). */
    private val PICK_SALT = mapOf(DryDay.SUNNY_COOL to 3, DryDay.SUNNY to 4, DryDay.OVERCAST to 5, DryDay.MIXED to 6)

    // ---------------------------------------------------------------------------------------
    // Home note
    // ---------------------------------------------------------------------------------------

    private fun weatherClause(w: WeatherStrings, home: HomeFacts): String {
        val soon = home.wetSoon
        return when {
            home.wetNow -> w.wetNow(home.condition, home.dryAgain?.hour)
            soon != null -> w.wetSoon(soon.condition, if (soon === home.upcoming.firstOrNull()) null else soon.hour)
            home.condition == Condition.FOG -> w.dryNow(DrySky.FOG)
            home.condition == Condition.OVERCAST -> w.dryNow(DrySky.GREY)
            home.condition.isSunny && home.now.isDay -> w.dryNow(DrySky.SUNNY)
            else -> w.dryNow(DrySky.PLAIN)
        }
    }

    private fun hazardSentence(w: WeatherStrings, home: HomeFacts): String? {
        val temp = Fmt.temp(home.now.temp)
        val visibility = home.visibilityNow
        return when {
            // The first sentence already says that freezing rain is falling right now.
            home.freezingRain && home.condition == Condition.FREEZING_RAIN -> w.iceNowHazard
            home.freezingRain -> w.iceSoonHazard
            home.slippery -> w.slipperyHazard(temp)
            visibility != null && visibility < 2000 -> w.visibilityHazard(visibilityPhrase(w, visibility))
            home.foggy -> w.fogHazard
            home.maxGust >= 45 -> w.gustHazard(roundHalfUp(home.maxGust))
            home.now.temp <= 0 -> w.frostHazard(temp)
            else -> null
        }
    }

    /** "gut 1 km", "etwa 400 m" — for running text. */
    private fun visibilityPhrase(w: WeatherStrings, meters: Double): String = when {
        meters < 100 -> w.visibility(VisibilityBand.UNDER_100_M, 0)
        meters < 1000 -> w.visibility(VisibilityBand.ABOUT_METERS, roundHalfUp(meters / 100) * 100)
        meters < 1250 -> w.visibility(VisibilityBand.JUST_OVER_1_KM, 0)
        meters < 1750 -> w.visibility(VisibilityBand.ABOUT_1_5_KM, 0)
        else -> w.visibility(VisibilityBand.NEARLY_2_KM, 0)
    }

    /** "~1 km", "~400 m", "~1,5 km" ("~1.5 km") — for the compact warning text. */
    private fun approxDistance(lang: AppLanguage, meters: Double): String {
        if (meters < 950) return "~${max(100, roundHalfUp(meters / 100) * 100)} m"
        val halfKm = roundHalfUp(meters / 500)
        val separator = FormatStrings.of(lang).decimalSeparator
        return if (halfKm % 2 == 0) "~${halfKm / 2} km" else "~${halfKm / 2}${separator}5 km"
    }

    // ---------------------------------------------------------------------------------------
    // Day summary
    // ---------------------------------------------------------------------------------------

    private fun temperatureTrend(w: WeatherStrings, d: DayFacts): String {
        if (!d.hasHours) return w.between(Fmt.temp(d.minTemp), Fmt.temp(d.maxTemp))
        val start = roundHalfUp(d.startTemp)
        val peak = roundHalfUp(d.maxTemp)
        val end = roundHalfUp(d.endTemp)
        val peakIndex = d.hours.indexOf(d.peak)
        return when {
            peak - start >= 2 && peakIndex > 0 -> w.rising(Fmt.temp(d.startTemp), Fmt.temp(d.maxTemp))
            start - end >= 2 -> w.falling(Fmt.temp(d.startTemp), Fmt.temp(d.endTemp))
            else -> w.around(Fmt.temp((d.minTemp + d.maxTemp) / 2))
        }
    }

    /**
     * Sky development. On wet or thundery days only the dry stretch before the first event is
     * described, and only when it is sunny.
     */
    private fun skyPhrase(w: WeatherStrings, d: DayFacts): String? {
        if (!d.hasHours) return null
        val firstEvent = listOfNotNull(
            d.thunder?.let(d.hours::indexOf),
            if (d.isWetDay) d.firstWet?.let(d.hours::indexOf) else null,
        ).minOrNull()
        if (firstEvent != null) {
            val before = d.conditions.take(firstEvent)
            return when {
                before.size < 2 -> null
                before.all { it == Condition.CLEAR } -> w.sunPureFirst
                before.count { it.isSunny } * 3 >= before.size * 2 -> w.sunMuchFirst
                else -> null
            }
        }

        val conditions = d.conditions
        if (conditions.all { it == Condition.CLEAR }) return w.sunPure
        if (conditions.all { it.isSunny }) return w.sunMuch

        // Turning sunny: everything from index i on is sunny (at least two hours of it).
        val sunnyFrom = (1..conditions.size - 2).firstOrNull { i -> conditions.drop(i).all { it.isSunny } }
        if (sunnyFrom != null) {
            val pure = conditions.drop(sunnyFrom).all { it == Condition.CLEAR }
            return w.sunFrom(d.hours[sunnyFrom].hour, pure)
        }
        // Clouding over: a sunny start of at least two hours, then at least two hours without sun.
        val cloudsFrom = (2..conditions.size - 2).firstOrNull { j ->
            conditions.take(j).all { it.isSunny } && conditions.drop(j).none { it.isSunny }
        }
        if (cloudsFrom != null) return w.sunThenClouds(d.hours[cloudsFrom].hour)
        if (conditions.count { it.isSunny } * 2 >= conditions.size) return w.sunMuch
        return when (dominantCondition(conditions)) {
            Condition.PARTLY_CLOUDY -> w.sunAndClouds
            Condition.OVERCAST -> w.mostlyGrey
            Condition.FOG -> w.dullFoggy
            else -> w.changeable
        }
    }

    private fun precipitationPhrase(w: WeatherStrings, d: DayFacts): String {
        val thunder = d.thunder
        if (thunder != null) {
            val hail = d.hours.any { isHailCode(it.code) }
            return w.thunderPossible(hail, if (thunder === d.hours.first()) null else thunder.hour)
        }
        val firstWet = d.firstWet
        val dryFrom = d.dryFrom
        if (d.isWetDay && firstWet != null) {
            val kind = d.wetKind
            return when {
                d.rainStartsLater -> w.precipFromPop(kind, firstWet.hour, d.maxPop)
                dryFrom != null -> w.precipUntilDry(kind, dryFrom)
                d.wetHours.size * 10 >= d.hours.size * 7 -> w.precipMostOfDay(kind, d.maxPop)
                else -> w.precipOnAndOff(kind, d.maxPop)
            }
        }
        return when {
            d.showersPossible -> if (d.maxTemp <= 1) w.flakesPossible else w.showersPossible
            d.maxTemp <= 2 -> w.dryWord
            else -> w.noRain
        }
    }

    private fun windPhrase(w: WeatherStrings, d: DayFacts): String? = when {
        d.maxGust >= 50 -> w.gustsUpTo(roundHalfUp(d.maxGust))
        d.hasHours && d.maxGust < 20 && d.maxWind < 10 -> w.littleWind
        else -> null
    }

    // ---------------------------------------------------------------------------------------
    // Warnings
    // ---------------------------------------------------------------------------------------

    private fun iceWarning(w: WeatherStrings, home: HomeFacts, day: DayFacts): WeatherWarning? {
        val dayFreezing = day.freezing
        val dayIcy = day.hours.firstOrNull { it.temp <= 1.0 && (it.isWetPoint() || (it.condition.isWet && it.pop >= 30)) }
        return when {
            home.freezingRain -> WeatherWarning(WarningLevel.SEVERE, "🧊", w.titleBlackIce, w.freezingRainNow)
            dayFreezing != null -> WeatherWarning(WarningLevel.SEVERE, "🧊", w.titleBlackIce, w.freezingRainFrom(dayFreezing.hour))
            home.slippery -> {
                val temp = Fmt.temp(home.now.temp)
                val wet = home.wetNow || home.upcoming.any { it.temp <= 1.0 && it.isWetPoint() }
                val text = if (wet) {
                    w.slipperyWet(temp, snow = home.condition.isWintry || home.wintryPrecip)
                } else {
                    w.hoarFrost(temp)
                }
                WeatherWarning(WarningLevel.CAUTION, "🧊", w.titleSlippery, text)
            }
            dayIcy != null -> WeatherWarning(
                WarningLevel.CAUTION, "🧊", w.titleSlippery,
                w.icyFrom(dayIcy.hour, dayIcy.condition.takeIf { it.isWet } ?: Condition.RAIN, Fmt.temp(dayIcy.temp)),
            )
            else -> null
        }
    }

    private fun fogWarning(w: WeatherStrings, lang: AppLanguage, home: HomeFacts): WeatherWarning? {
        val fogCode = home.condition == Condition.FOG || home.upcoming.any { it.condition == Condition.FOG }
        val visibility = home.minVisibility
        if (!fogCode && (visibility == null || visibility >= 1000)) return null
        val text = if (visibility != null && visibility < 2000) {
            w.fogVisibility(approxDistance(lang, visibility))
        } else {
            w.fogPossible
        }
        return WeatherWarning(WarningLevel.CAUTION, "🌫️", w.titleFog, text)
    }

    private fun gustWarning(w: WeatherStrings, home: HomeFacts, day: DayFacts): WeatherWarning? {
        val gust = max(home.maxGust, day.maxGust)
        val kmh = roundHalfUp(gust)
        return when {
            gust >= 80 -> WeatherWarning(WarningLevel.SEVERE, "💨", w.titleStorm, w.stormText(kmh))
            gust >= 60 -> WeatherWarning(WarningLevel.CAUTION, "💨", w.titleGusts, w.gustText(kmh))
            else -> null
        }
    }

    private fun thunderWarning(w: WeatherStrings, home: HomeFacts, day: DayFacts): WeatherWarning? {
        val homeHour = home.upcoming.firstOrNull { it.condition == Condition.THUNDER }
        val dayHour = day.thunder
        val hail = (home.upcoming + day.hours).any { it.condition == Condition.THUNDER && isHailCode(it.code) } ||
            isHailCode(home.now.code)
        val text = when {
            home.condition == Condition.THUNDER -> w.thunderNow
            homeHour != null -> w.thunderWarning(homeHour.hour, hail)
            dayHour != null -> w.thunderWarning(dayHour.hour, hail)
            else -> return null
        }
        return WeatherWarning(WarningLevel.CAUTION, "⛈️", w.titleThunder, text)
    }

    private fun heavyRainWarning(w: WeatherStrings, home: HomeFacts, day: DayFacts): WeatherWarning? {
        val heavy = (home.upcoming + day.hours).filter { isHeavyRainCode(it.code) || it.precipMm >= 5 }
        val first = heavy.firstOrNull() ?: return null
        val maxMm = heavy.maxOf { it.precipMm }
        val text = if (maxMm >= 5) w.heavyRainAmount(first.hour, roundHalfUp(maxMm)) else w.heavyRainBursts(first.hour)
        return WeatherWarning(WarningLevel.CAUTION, "🌧️", w.titleHeavyRain, text)
    }

    private fun heatWarning(w: WeatherStrings, day: DayFacts): WeatherWarning? {
        val temp = Fmt.temp(day.maxTemp)
        return when {
            day.maxTemp >= 35 -> WeatherWarning(WarningLevel.SEVERE, "🌡️", w.titleExtremeHeat, w.extremeHeat(temp))
            day.maxTemp >= 30 -> WeatherWarning(WarningLevel.CAUTION, "🌡️", w.titleHeat, w.heat(temp))
            else -> null
        }
    }

    private fun uvWarning(w: WeatherStrings, day: DayWeather): WeatherWarning? {
        if (day.uvMax < 6) return null
        val index = roundHalfUp(day.uvMax)
        // WHO scale on the rounded index the text shows: 6–7 high, 8+ very high.
        return WeatherWarning(WarningLevel.INFO, "🔆", w.titleUv(veryHigh = index >= 8), w.uv(index))
    }

    // ---------------------------------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------------------------------

    /** "13→21°" (start to peak), start to end when it only gets colder, "um 8°" when flat. */
    private fun shortTrend(w: WeatherStrings, d: DayFacts): String {
        val from = if (d.hasHours) d.startTemp else d.minTemp
        val to = when {
            !d.hasHours -> d.maxTemp
            roundHalfUp(d.maxTemp) > roundHalfUp(d.startTemp) -> d.maxTemp
            else -> d.endTemp
        }
        if (roundHalfUp(from) == roundHalfUp(to)) return w.shortAround(Fmt.temp(to))
        return "${Fmt.temp(from).removeSuffix("°")}→${Fmt.temp(to)}"
    }

    private fun conditionLabel(w: WeatherStrings, d: DayFacts): String {
        val thunder = d.thunder
        val firstWet = d.firstWet
        val dryFrom = d.dryFrom
        return when {
            thunder != null && thunder !== d.hours.first() -> w.thunderFromShort(thunder.hour)
            thunder != null -> w.thunderShort
            d.rainStartsLater && firstWet != null -> w.labelFrom(w.condition(d.wetKind, true), firstWet.hour)
            dryFrom != null -> w.labelUntil(w.condition(d.wetKind, true), dryFrom)
            else -> w.condition(d.main, true)
        }
    }

    private fun newsLine(w: WeatherStrings, headlines: List<String>): String {
        val top = headlines.map { it.trim() }.filter { it.isNotEmpty() }.take(3)
        return if (top.isEmpty()) w.noNews else "📰 " + top.joinToString(" · ")
    }
}

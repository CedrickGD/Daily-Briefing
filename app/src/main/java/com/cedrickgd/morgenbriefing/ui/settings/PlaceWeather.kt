package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.model.DayWeather
import com.cedrickgd.morgenbriefing.data.model.HourPoint
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.WeatherSnapshot
import com.cedrickgd.morgenbriefing.domain.Condition
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.RegionStrings
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.abs

/** Live mini weather on a place card: "☀️" + "14°" + "vor 12 Min.". */
@Immutable
internal data class PlaceWeather(val emoji: String, val temp: String, val caption: String)

/**
 * Mini weather for the two place cards from the last stored snapshot, captions in [lang]. A
 * snapshot of a place the user has since changed is not shown (null) — better no number than a
 * wrong one. Home shows the current temperature ("vor 12 Min."), the day place the warmest point
 * of its window ("max. heute"). Pure, unit-tested.
 */
internal fun placeWeather(
    snapshot: WeatherSnapshot?,
    home: Place,
    day: Place,
    now: ZonedDateTime,
    lang: AppLanguage,
): Pair<PlaceWeather?, PlaceWeather?> {
    if (snapshot == null) return null to null
    val strings = RegionStrings.of(lang)
    val homeWeather = if (snapshot.home.place.sameSpotAs(home)) {
        val current = snapshot.home.current
        PlaceWeather(
            emoji = Condition.fromCode(current.code).emoji(current.isDay),
            temp = Fmt.temp(current.temp),
            caption = Fmt.relative(snapshot.fetchedAt, now, lang) ?: strings.lastUpdated,
        )
    } else {
        null
    }
    val dayWeather = if (snapshot.day.place.sameSpotAs(day) && snapshot.day.hours.isNotEmpty()) {
        val hour = representativeHour(snapshot.day)
        val windowDate = runCatching { LocalDate.parse(snapshot.day.date) }.getOrNull()
        val today = now.toLocalDate()
        PlaceWeather(
            emoji = Condition.fromCode(hour?.code ?: OVERCAST_CODE).emoji(hour?.isDay ?: true),
            temp = Fmt.temp(snapshot.day.maxTemp),
            caption = when (windowDate) {
                today -> strings.maxToday
                today.plusDays(1) -> strings.maxTomorrow
                else -> strings.lastUpdated
            },
        )
    } else {
        null
    }
    return homeWeather to dayWeather
}

private const val OVERCAST_CODE = 3

/** Hour of the day window that best represents the day (closest to early afternoon). */
internal fun representativeHour(day: DayWeather): HourPoint? = day.hours.minByOrNull { abs(it.hour - 14) }

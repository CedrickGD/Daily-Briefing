package com.cedrickgd.morgenbriefing.domain

import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.FormatStrings
import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Display formatting in the interface language. Pure Kotlin + java.time (desugared on API 24/25).
 *
 * Month and weekday names come from [FormatStrings] instead of the platform's locale data, so
 * the output is identical on every Android version and in JVM unit tests.
 */
object Fmt {
    /** The app's reference time zone: local ISO strings without offset are read as Berlin time. */
    private val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

    /**
     * 9.4 -> "9°", -0.4 -> "0°" (never "-0°"). Rounds half away from zero ("kaufmännisch"),
     * so 2.5 -> "3°" and -2.5 -> "-3°". Non-finite values render as "–°". The same in every language.
     */
    fun temp(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "–°"
        return "${roundHalfUp(value)}°"
    }

    /** "DIENSTAG · 6. OKTOBER", "TUESDAY · OCTOBER 6" */
    fun dateLine(date: LocalDate, lang: AppLanguage): String {
        val f = FormatStrings.of(lang)
        return f.dateLine(f.weekdays[date.dayOfWeek.value - 1], date.dayOfMonth, f.months[date.monthValue - 1])
            .uppercase(lang.locale)
    }

    /** "Di, 6. Okt.", "Tue, Oct 6" */
    fun shortDate(date: LocalDate, lang: AppLanguage): String {
        val f = FormatStrings.of(lang)
        return f.shortDate(f.weekdaysShort[date.dayOfWeek.value - 1], date.dayOfMonth, f.monthsShort[date.monthValue - 1])
    }

    /** "Oktober 2026", "October 2026" */
    fun monthHeader(date: LocalDate, lang: AppLanguage): String {
        val f = FormatStrings.of(lang)
        return f.monthHeader(f.months[date.monthValue - 1], date.year)
    }

    /**
     * "Guten Morgen" (4–10), "Guten Tag" (11–17), "Guten Abend" (18–22), "Gute Nacht" otherwise;
     * every language with its own natural boundaries. Uses the wall-clock hour of [now] as
     * given; callers pass Berlin time.
     */
    fun greeting(now: ZonedDateTime, lang: AppLanguage): String = FormatStrings.of(lang).greeting(now.hour)

    /** A full hour as a label: "15 Uhr", "3 pm", "15 h". */
    fun hour(hour: Int, lang: AppLanguage): String = FormatStrings.of(lang).hour(hour)

    /** "10–18 Uhr", "10 am–6 pm". */
    fun hourRange(from: Int, to: Int, lang: AppLanguage): String = FormatStrings.of(lang).hourRange(from, to)

    /** "80 %", "80%". */
    fun percent(value: Int, lang: AppLanguage): String = FormatStrings.of(lang).percent(value)

    /** [value] with [digits] decimals and the language's separator: "1,5" / "1.5". */
    fun decimal(value: Double, digits: Int, lang: AppLanguage): String {
        val text = String.format(Locale.ROOT, "%.${digits.coerceAtLeast(0)}f", value)
        val separator = FormatStrings.of(lang).decimalSeparator
        return if (separator == '.') text else text.replace('.', separator)
    }

    /**
     * ISO with offset or local ISO -> "05:07". Returns "" if unparsable. 24-hour clock in every
     * language.
     *
     * Accepted: `2026-10-06T05:07:12+02:00` / `…Z` (converted to Berlin time),
     * `2026-10-06T05:00` (already Berlin time) and a bare `07:18` as Open-Meteo uses for sunrise.
     */
    fun clock(iso: String): String {
        val text = iso.trim()
        val time: LocalTime = parseBerlin(text)?.toLocalTime()
            ?: attempt { LocalTime.parse(text) }
            ?: return ""
        return String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)
    }

    /**
     * "gerade eben", "vor 12 Min.", "vor 3 Std.", "gestern", "vor 4 Tagen" ("3 min ago" …)
     * relative to [now]. Null/unparsable -> null. Times slightly in the future (clock skew)
     * count as "just now". Below 24 hours the distance is shown in minutes/hours; beyond that
     * in Berlin calendar days.
     */
    fun relative(iso: String?, now: ZonedDateTime, lang: AppLanguage): String? {
        val then = iso?.trim()?.let(::parseBerlin) ?: return null
        val reference = now.withZoneSameInstant(ZONE)
        val minutes = Duration.between(then, reference).toMinutes()
        val f = FormatStrings.of(lang)
        return when {
            minutes < 1 -> f.justNow
            minutes < 60 -> f.minutesAgo(minutes)
            minutes < 24 * 60 -> f.hoursAgo(minutes / 60)
            else -> {
                val days = ChronoUnit.DAYS.between(then.toLocalDate(), reference.toLocalDate())
                if (days <= 1) f.yesterday else f.daysAgo(days)
            }
        }
    }

    /**
     * Parses an ISO date-time with offset/zone, a local ISO date-time (Berlin) or a plain ISO
     * date (start of day, Berlin) into Berlin time. Null when the text is none of these.
     */
    internal fun parseBerlin(text: String): ZonedDateTime? {
        if (text.isEmpty()) return null
        // Tolerate "2026-10-06 05:00" (space instead of 'T').
        val iso = if (text.length > 10 && text[10] == ' ') text.replaceRange(10, 11, "T") else text
        return attempt { ZonedDateTime.parse(iso).withZoneSameInstant(ZONE) }
            ?: attempt { LocalDateTime.parse(iso).atZone(ZONE) }
            ?: attempt { LocalDate.parse(iso).atStartOfDay(ZONE) }
    }

    private inline fun <T> attempt(block: () -> T): T? =
        try {
            block()
        } catch (_: DateTimeException) {
            null
        }
}

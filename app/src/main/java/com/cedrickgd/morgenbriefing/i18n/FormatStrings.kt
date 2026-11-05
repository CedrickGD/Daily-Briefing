package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Words and patterns for dates, times, greetings and numbers ([com.cedrickgd.morgenbriefing.domain.Fmt]).
 *
 * Weekday and month names are spelled out here instead of coming from the platform's locale
 * data, so every text is identical on every Android version and in JVM unit tests.
 */
interface FormatStrings {
    /** Monday … Sunday. */
    val weekdays: List<String>

    /** Mon … Sun, as used in [shortDate]. */
    val weekdaysShort: List<String>

    /** January … December. */
    val months: List<String>

    /** Jan … Dec, as used in [shortDate]. */
    val monthsShort: List<String>

    /** Decimal separator for numbers with a fraction ("1,5 km" / "1.5 km"). */
    val decimalSeparator: Char

    /** "Dienstag · 6. Oktober" (the caller upper-cases it for the overline). */
    fun dateLine(weekday: String, day: Int, month: String): String

    /** "Di, 6. Okt." */
    fun shortDate(weekdayShort: String, day: Int, monthShort: String): String

    /** "Oktober 2026" */
    fun monthHeader(month: String, year: Int): String

    /** "Guten Morgen" … for the wall-clock [hour] (0–23). */
    fun greeting(hour: Int): String

    val justNow: String
    fun minutesAgo(minutes: Long): String
    fun hoursAgo(hours: Long): String
    val yesterday: String
    fun daysAgo(days: Long): String

    val today: String
    val tomorrow: String

    /**
     * A full hour as a standalone label: "15 Uhr", "3 pm", "15 h", "ore 15". Texts that need a
     * preposition ("ab 15 Uhr", "dalle 15") build it in their own strings file.
     */
    fun hour(hour: Int): String

    /** A window of full hours: "10–18 Uhr", "10 am–6 pm". */
    fun hourRange(from: Int, to: Int): String

    /** "80 %" (with the language's spacing). */
    fun percent(value: Int): String

    companion object {
        fun of(lang: AppLanguage): FormatStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : FormatStrings {
        override val weekdays = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
        override val weekdaysShort = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
        override val months = listOf(
            "Januar", "Februar", "März", "April", "Mai", "Juni",
            "Juli", "August", "September", "Oktober", "November", "Dezember",
        )
        override val monthsShort = listOf(
            "Jan.", "Feb.", "März", "Apr.", "Mai", "Juni",
            "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez.",
        )
        override val decimalSeparator = ','
        override fun dateLine(weekday: String, day: Int, month: String) = "$weekday · $day. $month"
        override fun shortDate(weekdayShort: String, day: Int, monthShort: String) = "$weekdayShort, $day. $monthShort"
        override fun monthHeader(month: String, year: Int) = "$month $year"
        override fun greeting(hour: Int) = when (hour) {
            in 4..10 -> "Guten Morgen"
            in 11..17 -> "Guten Tag"
            in 18..22 -> "Guten Abend"
            else -> "Gute Nacht"
        }
        override val justNow = "gerade eben"
        override fun minutesAgo(minutes: Long) = "vor $minutes Min."
        override fun hoursAgo(hours: Long) = "vor $hours Std."
        override val yesterday = "gestern"
        override fun daysAgo(days: Long) = if (days == 1L) "vor 1 Tag" else "vor $days Tagen"
        override val today = "heute"
        override val tomorrow = "morgen"
        override fun hour(hour: Int) = "$hour Uhr"
        override fun hourRange(from: Int, to: Int) = "$from–$to Uhr"
        override fun percent(value: Int) = "$value %"
    }

    private object En : FormatStrings {
        override val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        override val weekdaysShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        override val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
        override val monthsShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        override val decimalSeparator = '.'
        override fun dateLine(weekday: String, day: Int, month: String) = "$weekday · $month $day"
        override fun shortDate(weekdayShort: String, day: Int, monthShort: String) = "$weekdayShort, $monthShort $day"
        override fun monthHeader(month: String, year: Int) = "$month $year"
        override fun greeting(hour: Int) = when (hour) {
            in 4..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            in 18..23 -> "Good evening"
            else -> "Good night"
        }
        override val justNow = "just now"
        override fun minutesAgo(minutes: Long) = "$minutes min ago"
        override fun hoursAgo(hours: Long) = "$hours hr ago"
        override val yesterday = "yesterday"
        override fun daysAgo(days: Long) = if (days == 1L) "1 day ago" else "$days days ago"
        override val today = "today"
        override val tomorrow = "tomorrow"
        override fun hour(hour: Int) = when (val h = Math.floorMod(hour, 24)) {
            0 -> "midnight"
            12 -> "noon"
            in 1..11 -> "$h am"
            else -> "${h - 12} pm"
        }

        /** "10 am–6 pm", "1–4 pm" when both halves of the day agree. */
        override fun hourRange(from: Int, to: Int): String {
            val a = Math.floorMod(from, 24)
            val b = Math.floorMod(to, 24)
            val sameHalf = a in 1..11 && b in 1..11 || a in 13..23 && b in 13..23
            return if (sameHalf) "${twelve(a)}–${hour(b)}" else "${hour(a)}–${hour(b)}"
        }

        private fun twelve(h: Int) = if (h > 12) h - 12 else h
        override fun percent(value: Int) = "$value%"
    }

    private object Fr : FormatStrings {
        override val weekdays = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
        override val weekdaysShort = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
        override val months = listOf(
            "janvier", "février", "mars", "avril", "mai", "juin",
            "juillet", "août", "septembre", "octobre", "novembre", "décembre",
        )
        override val monthsShort = listOf(
            "janv.", "févr.", "mars", "avr.", "mai", "juin",
            "juil.", "août", "sept.", "oct.", "nov.", "déc.",
        )
        override val decimalSeparator = ','
        override fun dateLine(weekday: String, day: Int, month: String) = "$weekday · ${if (day == 1) "1er" else "$day"} $month"
        override fun shortDate(weekdayShort: String, day: Int, monthShort: String) = "$weekdayShort ${if (day == 1) "1er" else "$day"} $monthShort"
        override fun monthHeader(month: String, year: Int) = "${month.replaceFirstChar { it.uppercase() }} $year"
        override fun greeting(hour: Int) = when (hour) {
            in 4..17 -> "Bonjour"
            in 18..23 -> "Bonsoir"
            else -> "Bonne nuit"
        }
        override val justNow = "à l'instant"
        override fun minutesAgo(minutes: Long) = "il y a $minutes min"
        override fun hoursAgo(hours: Long) = "il y a $hours h"
        override val yesterday = "hier"
        override fun daysAgo(days: Long) = if (days == 1L) "il y a 1 jour" else "il y a $days jours"
        override val today = "aujourd'hui"
        override val tomorrow = "demain"
        override fun hour(hour: Int) = "${Math.floorMod(hour, 24)} h"
        override fun hourRange(from: Int, to: Int) = "$from–$to h"
        override fun percent(value: Int) = "$value %"
    }

    private object Es : FormatStrings {
        override val weekdays = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
        override val weekdaysShort = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
        override val months = listOf(
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
        )
        override val monthsShort = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")
        override val decimalSeparator = ','
        override fun dateLine(weekday: String, day: Int, month: String) = "$weekday · $day de $month"
        override fun shortDate(weekdayShort: String, day: Int, monthShort: String) = "$weekdayShort, $day $monthShort"
        override fun monthHeader(month: String, year: Int) = "${month.replaceFirstChar { it.uppercase() }} de $year"
        override fun greeting(hour: Int) = when (hour) {
            in 4..11 -> "Buenos días"
            in 12..19 -> "Buenas tardes"
            else -> "Buenas noches"
        }
        override val justNow = "ahora mismo"
        override fun minutesAgo(minutes: Long) = "hace $minutes min"
        override fun hoursAgo(hours: Long) = "hace $hours h"
        override val yesterday = "ayer"
        override fun daysAgo(days: Long) = if (days == 1L) "hace 1 día" else "hace $days días"
        override val today = "hoy"
        override val tomorrow = "mañana"
        override fun hour(hour: Int) = "${Math.floorMod(hour, 24)} h"
        override fun hourRange(from: Int, to: Int) = "$from–$to h"
        override fun percent(value: Int) = "$value %"
    }

    private object It : FormatStrings {
        override val weekdays = listOf("lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica")
        override val weekdaysShort = listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom")
        override val months = listOf(
            "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
            "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
        )
        override val monthsShort = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
        override val decimalSeparator = ','
        override fun dateLine(weekday: String, day: Int, month: String) = "$weekday · $day $month"
        override fun shortDate(weekdayShort: String, day: Int, monthShort: String) = "$weekdayShort $day $monthShort"
        override fun monthHeader(month: String, year: Int) = "${month.replaceFirstChar { it.uppercase() }} $year"
        override fun greeting(hour: Int) = when (hour) {
            in 4..11 -> "Buongiorno"
            in 12..17 -> "Buon pomeriggio"
            in 18..22 -> "Buonasera"
            else -> "Buonanotte"
        }
        override val justNow = "adesso"
        override fun minutesAgo(minutes: Long) = "$minutes min fa"
        override fun hoursAgo(hours: Long) = if (hours == 1L) "1 ora fa" else "$hours ore fa"
        override val yesterday = "ieri"
        override fun daysAgo(days: Long) = if (days == 1L) "1 giorno fa" else "$days giorni fa"
        override val today = "oggi"
        override val tomorrow = "domani"
        override fun hour(hour: Int) = "ore ${Math.floorMod(hour, 24)}"
        override fun hourRange(from: Int, to: Int) = "$from–$to"
        override fun percent(value: Int) = "$value%"
    }
}

@Composable
@ReadOnlyComposable
fun formatStrings(): FormatStrings = FormatStrings.of(LocalAppLanguage.current)

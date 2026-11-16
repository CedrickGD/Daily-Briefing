package com.cedrickgd.morgenbriefing.ui.history

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.local.HistoryEntry
import com.cedrickgd.morgenbriefing.data.news.SearchQuery
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.HistoryStrings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/*
 * "Gelesen & ausgeblendet" as pure functions (unit-tested): which entries a search and a filter
 * leave, how they group by day and what each day is called.
 */

/** Which entries the archive lists. */
enum class HistoryFilter { ALL, READ, HIDDEN }

/** The entries of one day, newest first. */
@Immutable
data class HistoryDay(val date: LocalDate, val entries: List<HistoryEntry>)

/** True when [entry] passes [filter]. */
internal fun HistoryFilter.passes(entry: HistoryEntry): Boolean = when (this) {
    HistoryFilter.ALL -> entry.read || entry.hidden
    HistoryFilter.READ -> entry.read
    HistoryFilter.HIDDEN -> entry.hidden
}

/**
 * True when every word of [query] appears in [entry]'s title, source or site – folded like the
 * search (case, accents and punctuation do not count), as the start of a word or inside it. A
 * blank query matches everything.
 */
internal fun matchesQuery(entry: HistoryEntry, query: String): Boolean {
    val words = SearchQuery.fold(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val text = SearchQuery.fold("${entry.title} ${entry.source} ${entry.domain}")
    return words.all { it in text }
}

/**
 * The archive: the [entries] that pass [filter] and [query], newest first
 * ([HistoryEntry.touchedAt]), grouped by the day they were read or hidden in [zone].
 */
internal fun historyDays(entries: List<HistoryEntry>, query: String, filter: HistoryFilter, zone: ZoneId): List<HistoryDay> {
    val shown = entries.filter { filter.passes(it) && matchesQuery(it, query) }.sortedByDescending { it.touchedAt }
    val days = LinkedHashMap<LocalDate, MutableList<HistoryEntry>>()
    for (entry in shown) days.getOrPut(dayOf(entry, zone)) { ArrayList() } += entry
    return days.map { (date, list) -> HistoryDay(date, list) }
}

/** The day [entry] was last read or hidden, in [zone]. */
internal fun dayOf(entry: HistoryEntry, zone: ZoneId): LocalDate = Instant.ofEpochMilli(entry.touchedAt).atZone(zone).toLocalDate()

/** "Heute", "Gestern", else "Dienstag · 6. Oktober" (as the date line of "Heute" writes it). */
internal fun dayLabel(date: LocalDate, today: LocalDate, lang: AppLanguage): String {
    val strings = HistoryStrings.of(lang)
    return when (date) {
        today -> strings.today
        today.minusDays(1) -> strings.yesterday
        else -> Fmt.dateLine(date, lang)
    }
}

/** "14:32": when [entry] was last read or hidden, on the clock of [zone]. */
internal fun timeOf(entry: HistoryEntry, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(entry.touchedAt).atZone(zone)
    return String.format(Locale.ROOT, "%02d:%02d", at.hour, at.minute)
}

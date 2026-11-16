package com.cedrickgd.morgenbriefing.ui.history

import com.cedrickgd.morgenbriefing.data.local.HistoryEntry
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.HistoryStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class HistoryLogicTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val today: LocalDate = LocalDate.of(2026, 11, 16)

    private fun at(day: LocalDate, hour: Int, minute: Int = 0): Long = ZonedDateTime.of(day.atTime(hour, minute), zone).toInstant().toEpochMilli()

    private fun entry(key: String, title: String, source: String, readAt: Long? = null, hiddenAt: Long? = null) =
        HistoryEntry(key = key, title = title, url = "https://example.org/$key", source = source, domain = "$source.de", readAt = readAt, hiddenAt = hiddenAt)

    private val entries = listOf(
        entry("a", "Rentenreform beschlossen", "tagesschau", readAt = at(today, 8)),
        entry("b", "DAX auf Rekordhoch", "Handelsblatt", hiddenAt = at(today, 10)),
        entry("c", "Unwetter im Süden", "BR24", readAt = at(today.minusDays(1), 22, 30)),
        entry("d", "Wahl in Österreich", "ORF", readAt = at(today.minusDays(3), 7), hiddenAt = at(today.minusDays(3), 7, 5)),
    )

    @Test
    fun `entries group by day, newest first`() {
        val days = historyDays(entries, query = "", filter = HistoryFilter.ALL, zone = zone)
        assertEquals(listOf(today, today.minusDays(1), today.minusDays(3)), days.map { it.date })
        assertEquals(listOf("b", "a"), days[0].entries.map { it.key })
        assertEquals(listOf("c"), days[1].entries.map { it.key })
    }

    @Test
    fun `the filter picks read or hidden stories`() {
        assertEquals(listOf("a", "c", "d"), historyDays(entries, "", HistoryFilter.READ, zone).flatMap { d -> d.entries.map { it.key } })
        assertEquals(listOf("b", "d"), historyDays(entries, "", HistoryFilter.HIDDEN, zone).flatMap { d -> d.entries.map { it.key } })
    }

    @Test
    fun `the search finds title or source, without case and accents`() {
        fun keys(query: String) = historyDays(entries, query, HistoryFilter.ALL, zone).flatMap { d -> d.entries.map { it.key } }
        assertEquals(listOf("c"), keys("sud"))
        assertEquals(listOf("d"), keys("ÖSTERREICH"))
        assertEquals(listOf("b"), keys("handels"))
        assertEquals(listOf("a"), keys("tagesschau rente"))
        assertEquals(emptyList<String>(), keys("tagesschau dax"))
        assertEquals(4, keys("   ").size)
    }

    @Test
    fun `days are called today, yesterday, else by their date`() {
        val de = HistoryStrings.of(AppLanguage.DE)
        assertEquals(de.today, dayLabel(today, today, AppLanguage.DE))
        assertEquals(de.yesterday, dayLabel(today.minusDays(1), today, AppLanguage.DE))
        assertEquals(Fmt.dateLine(today.minusDays(3), AppLanguage.DE), dayLabel(today.minusDays(3), today, AppLanguage.DE))
        assertEquals("22:30", timeOf(entries[2], zone))
    }

    @Test
    fun `every language has every text`() {
        for (lang in AppLanguage.entries) {
            val s = HistoryStrings.of(lang)
            val texts = listOf(
                s.overline, s.title, s.counts(2, 1), s.searchPlaceholder, s.clearSearch, s.filterAll, s.filterRead, s.filterHidden,
                s.today, s.yesterday, s.statusRead, s.statusHidden, s.showAgain, s.markUnread, s.shownAgain, s.markedUnread,
                s.clearHistory, s.clearTitle, s.clearText, s.cleared, s.emptyTitle, s.emptyText, s.noMatch("x"), s.noMatchText, s.noneInFilter,
            )
            texts.forEach { assertTrue("$lang has a blank text", it.isNotBlank() && it == it.trim()) }
            assertTrue(s.counts(12, 3).contains("12") && s.counts(12, 3).contains("3"))
            assertTrue(s.noMatch("Rente").contains("Rente"))
            if (lang != AppLanguage.DE) assertTrue("$lang translated", s.title != HistoryStrings.of(AppLanguage.DE).title)
        }
    }
}

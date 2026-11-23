package com.cedrickgd.morgenbriefing.ui.favorites

import com.cedrickgd.morgenbriefing.data.local.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class FavoritesLogicTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2027, 1, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun entry(key: String, title: String, starredAt: Long?, source: String = "tagesschau") =
        HistoryEntry(key = key, title = title, url = "https://x.de/$key", source = source, starredAt = starredAt)

    @Test
    fun `starred stories group by the day of the star, newest first, and the search narrows them`() {
        val entries = listOf(
            entry("a", "Rente steigt", at(14, 9)),
            entry("b", "Wetter wird mild", at(15, 8)),
            entry("c", "Börse im Plus", at(15, 18), source = "Handelsblatt"),
            entry("read-only", "Nur gelesen", null),
        )
        val days = favoriteDays(entries, query = "", zone = zone)
        assertEquals(listOf(LocalDate.of(2027, 1, 15), LocalDate.of(2027, 1, 14)), days.map { it.date })
        assertEquals(listOf("c", "b"), days.first().entries.map { it.key })
        assertEquals(listOf("c"), favoriteDays(entries, query = "handelsblatt", zone = zone).flatMap { it.entries }.map { it.key })
        assertEquals(emptyList<Any>(), favoriteDays(entries, query = "Fußball", zone = zone))
    }
}

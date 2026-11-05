package com.cedrickgd.morgenbriefing.update

import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.UpdateStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UpdateTextTest {

    @Test
    fun `release notes become a plain excerpt`() {
        val notes = """
            ## Neu in 1.2.0

            - **Themen** sortieren und ausblenden
            * Artikel komplett in der App lesen ([Details](https://example.com))
            ---
            `Kleinigkeiten` behoben

            **Full Changelog**: https://github.com/CedrickGD/Morgenbriefing/compare/v1.1.0...v1.2.0
        """.trimIndent()

        assertEquals(
            "Neu in 1.2.0\n• Themen sortieren und ausblenden\n• Artikel komplett in der App lesen (Details)\nKleinigkeiten behoben",
            UpdateText.notesExcerpt(notes),
        )
    }

    @Test
    fun `long notes are cut with an ellipsis`() {
        val manyLines = (1..10).joinToString("\n") { "- Punkt $it" }
        val excerpt = UpdateText.notesExcerpt(manyLines, maxLines = 3)
        assertEquals("• Punkt 1\n• Punkt 2\n• Punkt 3 …", excerpt)

        val longLine = "Wort ".repeat(100).trim()
        val cut = UpdateText.notesExcerpt(longLine, maxChars = 60)
        assertTrue(cut.endsWith(" …"))
        assertTrue(cut.length <= 62)
        assertTrue("cuts at a word boundary", cut.removeSuffix(" …").split(" ").all { it == "Wort" })
    }

    @Test
    fun `empty notes stay empty`() {
        assertEquals("", UpdateText.notesExcerpt(""))
        assertEquals("", UpdateText.notesExcerpt("\n\n**Full Changelog**: https://x\n"))
    }

    @Test
    fun `sizes and percentages are german`() {
        assertEquals("12,3 MB", UpdateText.megabytes(12_345_678, AppLanguage.DE))
        assertEquals("0,1 MB", UpdateText.megabytes(2_000, AppLanguage.DE))
        assertEquals("0,0 MB", UpdateText.megabytes(0, AppLanguage.DE))
        assertEquals("0 %", UpdateText.percent(0f, AppLanguage.DE))
        assertEquals("42 %", UpdateText.percent(0.429f, AppLanguage.DE))
        assertEquals("99 %", UpdateText.percent(0.999f, AppLanguage.DE))
        assertEquals("100 %", UpdateText.percent(1f, AppLanguage.DE))
    }

    @Test
    fun `last check label`() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 10, 6, 14, 40, 0, 0, zone)
        assertEquals("Noch nicht geprüft", UpdateText.lastCheck(now, null, AppLanguage.DE))
        assertEquals("Zuletzt geprüft: gerade eben", UpdateText.lastCheck(now, now.minusSeconds(20), AppLanguage.DE))
        assertEquals("Zuletzt geprüft: heute um 14:32", UpdateText.lastCheck(now, now.withMinute(32), AppLanguage.DE))
        assertEquals("Zuletzt geprüft: gestern um 08:05", UpdateText.lastCheck(now, now.minusDays(1).withHour(8).withMinute(5), AppLanguage.DE))
        assertEquals(
            "Zuletzt geprüft: Fr, 2. Okt. um 07:00",
            UpdateText.lastCheck(now, now.minusDays(4).withHour(7).withMinute(0), AppLanguage.DE),
        )
        // Stored instants are shown in the reader's zone.
        val utc = now.withZoneSameInstant(ZoneId.of("UTC")).minusHours(1)
        assertEquals("Zuletzt geprüft: heute um 13:40", UpdateText.lastCheck(now, utc, AppLanguage.DE))
    }

    @Test
    fun `updater texts in English and Italian`() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 10, 6, 14, 40, 0, 0, zone)
        assertEquals("Not checked yet", UpdateText.lastCheck(now, null, AppLanguage.EN))
        assertEquals("Last checked: today at 14:32", UpdateText.lastCheck(now, now.withMinute(32), AppLanguage.EN))
        assertEquals("Last checked: Fri, Oct 2 at 07:00", UpdateText.lastCheck(now, now.minusDays(4).withHour(7).withMinute(0), AppLanguage.EN))
        assertEquals("Ultimo controllo: ieri alle 08:05", UpdateText.lastCheck(now, now.minusDays(1).withHour(8).withMinute(5), AppLanguage.IT))
        assertEquals("12.3 MB", UpdateText.megabytes(12_345_678, AppLanguage.EN))
        assertEquals("42%", UpdateText.percent(0.429f, AppLanguage.EN))
        assertEquals("Can't reach GitHub – are you online?", UpdateText.errorMessage(UpdateError.NETWORK, AppLanguage.EN))
        val strings = UpdateStrings.of(AppLanguage.EN)
        assertEquals("Update available: Daily Briefing 2.1.0", strings.availableTitle("2.1.0"))
        assertEquals("Update verfügbar: Daily Briefing 2.1.0", UpdateStrings.of(AppLanguage.DE).availableTitle("2.1.0"))
    }

    @Test
    fun `every error has its own friendly message`() {
        for (lang in AppLanguage.entries) {
            val all = UpdateError.entries.map { UpdateText.errorMessage(it, lang) }
            assertEquals("$lang: one message per error", all.size, all.toSet().size)
        }
        val messages = UpdateError.entries.map { UpdateText.errorMessage(it, AppLanguage.DE) }
        messages.forEach { assertTrue(it.isNotBlank() && it.length < 140) }
        assertEquals(messages.size, messages.toSet().size)
        assertNotEquals(
            UpdateText.errorMessage(UpdateError.WRONG_PACKAGE, AppLanguage.DE, debugBuild = true),
            UpdateText.errorMessage(UpdateError.WRONG_PACKAGE, AppLanguage.DE, debugBuild = false),
        )
    }
}

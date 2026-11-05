package com.cedrickgd.morgenbriefing.domain

import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.FormatStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class FmtLanguagesTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val now: ZonedDateTime = ZonedDateTime.of(2026, 10, 6, 5, 30, 0, 0, berlin)
    private val tuesday = LocalDate.of(2026, 10, 6)

    @Test
    fun `every language names all weekdays and months`() {
        for (lang in AppLanguage.entries) {
            val f = FormatStrings.of(lang)
            assertEquals(lang.name, 7, f.weekdays.size)
            assertEquals(lang.name, 7, f.weekdaysShort.size)
            assertEquals(lang.name, 12, f.months.size)
            assertEquals(lang.name, 12, f.monthsShort.size)
            assertEquals(lang.name, 7, f.weekdays.toSet().size)
            assertEquals(lang.name, 12, f.months.toSet().size)
        }
    }

    @Test
    fun `date lines`() {
        assertEquals("TUESDAY · OCTOBER 6", Fmt.dateLine(tuesday, AppLanguage.EN))
        assertEquals("MARDI · 6 OCTOBRE", Fmt.dateLine(tuesday, AppLanguage.FR))
        assertEquals("MARTES · 6 DE OCTUBRE", Fmt.dateLine(tuesday, AppLanguage.ES))
        assertEquals("MARTEDÌ · 6 OTTOBRE", Fmt.dateLine(tuesday, AppLanguage.IT))
        // French writes the first of the month as an ordinal.
        assertEquals("LUNDI · 1ER MARS", Fmt.dateLine(LocalDate.of(2027, 3, 1), AppLanguage.FR))
    }

    @Test
    fun `short dates and month headers`() {
        assertEquals("Tue, Oct 6", Fmt.shortDate(tuesday, AppLanguage.EN))
        assertEquals("mar. 6 oct.", Fmt.shortDate(tuesday, AppLanguage.FR))
        assertEquals("mar, 6 oct", Fmt.shortDate(tuesday, AppLanguage.ES))
        assertEquals("mar 6 ott", Fmt.shortDate(tuesday, AppLanguage.IT))
        assertEquals("October 2026", Fmt.monthHeader(tuesday, AppLanguage.EN))
        assertEquals("Octobre 2026", Fmt.monthHeader(tuesday, AppLanguage.FR))
        assertEquals("Octubre de 2026", Fmt.monthHeader(tuesday, AppLanguage.ES))
        assertEquals("Ottobre 2026", Fmt.monthHeader(tuesday, AppLanguage.IT))
    }

    @Test
    fun `greetings follow each language's day`() {
        fun at(lang: AppLanguage, h: Int) = Fmt.greeting(now.withHour(h), lang)
        assertEquals("Good morning", at(AppLanguage.EN, 7))
        assertEquals("Good afternoon", at(AppLanguage.EN, 14))
        assertEquals("Good evening", at(AppLanguage.EN, 20))
        assertEquals("Good night", at(AppLanguage.EN, 2))
        assertEquals("Bonjour", at(AppLanguage.FR, 14))
        assertEquals("Bonsoir", at(AppLanguage.FR, 19))
        assertEquals("Buenos días", at(AppLanguage.ES, 9))
        assertEquals("Buenas tardes", at(AppLanguage.ES, 17))
        assertEquals("Buenas noches", at(AppLanguage.ES, 21))
        assertEquals("Buongiorno", at(AppLanguage.IT, 8))
        assertEquals("Buonasera", at(AppLanguage.IT, 19))
        for (lang in AppLanguage.entries) {
            (0..23).forEach { h -> assertTrue("$lang $h", at(lang, h).isNotBlank()) }
        }
    }

    @Test
    fun `relative times`() {
        fun rel(lang: AppLanguage, iso: String) = Fmt.relative(iso, now, lang)
        assertEquals("just now", rel(AppLanguage.EN, "2026-10-06T05:29:40+02:00"))
        assertEquals("12 min ago", rel(AppLanguage.EN, "2026-10-06T05:18:00+02:00"))
        assertEquals("3 hr ago", rel(AppLanguage.EN, "2026-10-06T02:30"))
        assertEquals("yesterday", rel(AppLanguage.EN, "2026-10-05T03:00:00+02:00"))
        assertEquals("4 days ago", rel(AppLanguage.EN, "2026-10-02T12:00"))
        assertEquals("il y a 12 min", rel(AppLanguage.FR, "2026-10-06T05:18:00+02:00"))
        assertEquals("il y a 2 jours", rel(AppLanguage.FR, "2026-10-04T03:00"))
        assertEquals("hace 3 h", rel(AppLanguage.ES, "2026-10-06T02:30"))
        assertEquals("ayer", rel(AppLanguage.ES, "2026-10-05T03:00:00+02:00"))
        assertEquals("1 ora fa", rel(AppLanguage.IT, "2026-10-06T04:30"))
        assertEquals("3 ore fa", rel(AppLanguage.IT, "2026-10-06T02:30"))
        assertEquals("adesso", rel(AppLanguage.IT, "2026-10-06T05:35:00+02:00"))
    }

    @Test
    fun `hours, ranges, percent and decimals`() {
        assertEquals("15 Uhr", Fmt.hour(15, AppLanguage.DE))
        assertEquals("3 pm", Fmt.hour(15, AppLanguage.EN))
        assertEquals("10 am", Fmt.hour(10, AppLanguage.EN))
        assertEquals("noon", Fmt.hour(12, AppLanguage.EN))
        assertEquals("midnight", Fmt.hour(0, AppLanguage.EN))
        assertEquals("15 h", Fmt.hour(15, AppLanguage.FR))
        assertEquals("ore 15", Fmt.hour(15, AppLanguage.IT))

        assertEquals("10–18 Uhr", Fmt.hourRange(10, 18, AppLanguage.DE))
        assertEquals("10 am–6 pm", Fmt.hourRange(10, 18, AppLanguage.EN))
        assertEquals("1–4 pm", Fmt.hourRange(13, 16, AppLanguage.EN))
        assertEquals("8–11 am", Fmt.hourRange(8, 11, AppLanguage.EN))
        assertEquals("10–18 h", Fmt.hourRange(10, 18, AppLanguage.FR))

        assertEquals("80 %", Fmt.percent(80, AppLanguage.DE))
        assertEquals("80%", Fmt.percent(80, AppLanguage.EN))
        assertEquals("80 %", Fmt.percent(80, AppLanguage.ES))

        assertEquals("1,5", Fmt.decimal(1.5, 1, AppLanguage.DE))
        assertEquals("1.5", Fmt.decimal(1.5, 1, AppLanguage.EN))
        assertEquals("12,3", Fmt.decimal(12.345, 1, AppLanguage.IT))
    }
}

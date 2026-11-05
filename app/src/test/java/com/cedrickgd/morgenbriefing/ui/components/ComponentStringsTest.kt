package com.cedrickgd.morgenbriefing.ui.components

import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ComponentStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentStringsTest {

    private val de = ComponentStrings.of(AppLanguage.DE)
    private val en = ComponentStrings.of(AppLanguage.EN)

    @Test
    fun `German overlines read as before once upper-cased`() {
        val locale = AppLanguage.DE.locale
        assertEquals("VOR DER TÜR · BAD DÜBEN", de.outsideAt("Bad Düben").uppercase(locale))
        assertEquals("LEIPZIG · 10–18 UHR", de.dayCurveTitle("Leipzig", 10, 18, tomorrow = false).uppercase(locale))
        assertEquals("MORGEN · LEIPZIG · 10–18 UHR", de.dayCurveTitle("Leipzig", 10, 18, tomorrow = true).uppercase(locale))
        assertEquals("AKTUELL · STAND 07:12", de.latestAsOf("07:12").uppercase(locale))
        assertEquals("ANZIEHEN", de.clothingTitle.uppercase(locale))
    }

    @Test
    fun `chart descriptions for screen readers`() {
        val dePoints = listOf(de.chartPoint(10, "13°", null), de.chartPoint(11, "15°", 60))
        assertEquals(
            "Temperaturverlauf in Leipzig von 10 bis 18 Uhr: 10 Uhr 13°, 11 Uhr 15°, Regen 60 Prozent.",
            de.chartDescription("Leipzig", 10, 18, tomorrow = false, points = dePoints),
        )
        val enPoints = listOf(en.chartPoint(10, "13°", null), en.chartPoint(15, "15°", 60))
        assertEquals(
            "Temperatures tomorrow in Leipzig from 10 am to 6 pm: 10 am 13°, 3 pm 15°, rain 60 percent.",
            en.chartDescription("Leipzig", 10, 18, tomorrow = true, points = enPoints),
        )
        assertEquals("TOMORROW · LEIPZIG · 10 AM–6 PM", en.dayCurveTitle("Leipzig", 10, 18, tomorrow = true).uppercase())
    }

    @Test
    fun `hero chips`() {
        assertEquals("Outside in Bad Düben: 9°, feels like 8°, fog", en.outsideDescription("Bad Düben", "9°", "8°", "fog"))
        assertEquals("Vor der Tür in Bad Düben: 9°, gefühlt 8°, Nebel", de.outsideDescription("Bad Düben", "9°", "8°", "Nebel"))
        assertEquals("Sonnenaufgang 07:18", de.sunrise("07:18"))
        assertEquals("Sunset 16:40", en.sunset("16:40"))
        assertEquals("Visibilità 1,5 km", ComponentStrings.of(AppLanguage.IT).visibility("1,5"))
    }

    @Test
    fun `every language has every text`() {
        for (lang in AppLanguage.entries) {
            val s = ComponentStrings.of(lang)
            val texts = listOf(
                s.noHourlyValues, s.now, s.clothingTitle, s.yourNews, s.latest, s.farewell, s.loadingBriefing,
                s.details, s.showDetails, s.hideDetails, s.expanded, s.collapsed, s.errorDetailsClip, s.detailsCopied,
                s.copy, s.linkCannotOpen, s.shareChooser, s.shareUnavailable, s.noBrowser,
            )
            texts.forEach { assertTrue("$lang: \"$it\"", it.isNotBlank() && it == it.trim()) }
        }
        // German keeps today's wording.
        assertEquals("Dein Briefing wird geladen", de.loadingBriefing)
        assertEquals("Bis später", de.farewell)
        assertEquals("Meldung teilen", de.shareChooser)
    }
}

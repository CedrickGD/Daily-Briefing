package com.cedrickgd.morgenbriefing.ui.components

import com.cedrickgd.morgenbriefing.data.model.CurrentConditions
import com.cedrickgd.morgenbriefing.data.model.HomeWeather
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ComponentStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunChipTest {

    private fun home(now: String, sunrise: String = "07:18", sunset: String = "16:42", tomorrow: String = "07:20") =
        HomeWeather(
            place = Place.BAD_DUEBEN,
            current = CurrentConditions(
                time = "2026-11-05T$now",
                temp = 8.0,
                feels = 6.0,
                code = 3,
                isDay = true,
                windKmh = 10.0,
                gustKmh = 15.0,
                precipMm = 0.0,
                humidity = 80,
            ),
            nextHours = emptyList(),
            sunrise = sunrise,
            sunset = sunset,
            sunriseTomorrow = tomorrow,
        )

    private fun chip(now: String, lang: AppLanguage, sunrise: String = "07:18") =
        sunChip(home(now, sunrise = sunrise), ComponentStrings.of(lang))

    @Test
    fun `before sunrise the chip shows today's sunrise in the interface language`() {
        assertEquals(HeroChip("🌅", "Sonnenaufgang 07:18"), chip("05:30", AppLanguage.DE))
        assertEquals(HeroChip("🌅", "Sunrise 07:18"), chip("05:30", AppLanguage.EN))
        assertEquals(HeroChip("🌅", "Lever du soleil 07:18"), chip("05:30", AppLanguage.FR))
    }

    @Test
    fun `during the day the chip shows the sunset`() {
        assertEquals(HeroChip("🌇", "Sonnenuntergang 16:42"), chip("12:00", AppLanguage.DE))
        assertEquals(HeroChip("🌇", "Atardecer 16:42"), chip("12:00", AppLanguage.ES))
        assertEquals(HeroChip("🌇", "Tramonto 16:42"), chip("12:00", AppLanguage.IT))
    }

    @Test
    fun `after sunset the chip shows tomorrow's sunrise`() {
        assertEquals(HeroChip("🌅", "Sunrise 07:20"), chip("19:00", AppLanguage.EN))
        assertEquals(HeroChip("🌅", "Sonnenaufgang 07:20"), chip("19:00", AppLanguage.DE))
    }

    @Test
    fun `no chip without sun times`() {
        assertNull(sunChip(home("12:00", sunrise = "", sunset = "", tomorrow = ""), ComponentStrings.of(AppLanguage.EN)))
    }

    @Test
    fun `no German word leaks into another language`() {
        for (lang in AppLanguage.entries - AppLanguage.DE) {
            for (now in listOf("05:30", "12:00", "19:00")) {
                val text = chip(now, lang)!!.text
                assertTrue("$lang $now: $text", !text.contains("Sonnen"))
            }
        }
    }
}

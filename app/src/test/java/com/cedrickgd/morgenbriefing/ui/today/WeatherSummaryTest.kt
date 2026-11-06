package com.cedrickgd.morgenbriefing.ui.today

import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.domain.Condition
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.domain.WeatherFixtures
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import com.cedrickgd.morgenbriefing.i18n.WeatherStrings
import com.cedrickgd.morgenbriefing.ui.brief.toWeatherUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherSummaryTest {

    private val dryDay = WeatherFixtures.foggyAutumn.toWeatherUi(AppLanguage.DE)
    private val rainyDay = WeatherFixtures.rainyWindy.toWeatherUi(AppLanguage.DE)

    @Test
    fun `the collapsed card names the range, dry and the first clothing piece`() {
        val clothing = dryDay.clothing!!.items.first().label
        assertEquals("13° – 21° · trocken · $clothing", weatherSummaryLine(dryDay, WeatherCard.ALL, AppLanguage.DE))
    }

    @Test
    fun `rain shows with its probability`() {
        val line = weatherSummaryLine(rainyDay, WeatherCard.ALL, AppLanguage.DE)
        val rain = "${WeatherStrings.of(AppLanguage.DE).rainWord} ${Fmt.percent(80, AppLanguage.DE)}"
        assertTrue(line, line.startsWith("12° – 14° · $rain"))
        assertEquals(rain, rainNote(WeatherFixtures.rainyWindy.day, AppLanguage.DE))
        assertEquals(FeedStrings.of(AppLanguage.DE).dry, rainNote(WeatherFixtures.foggyAutumn.day, AppLanguage.DE))
    }

    @Test
    fun `the clothing tip follows the clothing switch`() {
        val clothing = dryDay.clothing!!.items.first().label
        val line = weatherSummaryLine(dryDay, WeatherCard.ALL - WeatherCard.CLOTHING, AppLanguage.DE)
        assertEquals("13° – 21° · trocken", line)
        assertFalse(line.contains(clothing))
    }

    @Test
    fun `the line is written in the interface language`() {
        val english = WeatherFixtures.foggyAutumn.toWeatherUi(AppLanguage.EN)
        val line = weatherSummaryLine(english, WeatherCard.ALL, AppLanguage.EN)
        assertTrue(line, line.startsWith("13° – 21° · dry · "))
        for (lang in AppLanguage.entries) {
            assertTrue(weatherSummaryLine(WeatherFixtures.foggyAutumn.toWeatherUi(lang), WeatherCard.ALL, lang).isNotBlank())
        }
    }

    @Test
    fun `the place line names the home place and its condition`() {
        val home = WeatherFixtures.foggyAutumn.home
        val condition = Condition.fromCode(home.current.code).labelFor(home.current.isDay, AppLanguage.DE)
        assertEquals("${home.place.name} · $condition", weatherPlaceLine(dryDay, AppLanguage.DE))
    }

    @Test
    fun `the feed bar comes after the header, the weather and the warnings`() {
        assertEquals(2, feedBarIndex(warningsShown = false))
        assertEquals(3, feedBarIndex(warningsShown = true))
    }
}

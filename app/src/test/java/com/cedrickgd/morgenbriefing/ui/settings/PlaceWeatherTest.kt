package com.cedrickgd.morgenbriefing.ui.settings

import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.domain.WeatherFixtures
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PlaceWeatherTest {

    @Test
    fun `place weather comes from the snapshot of the same places`() {
        val snapshot = WeatherFixtures.foggyAutumn
        val now = ZonedDateTime.of(2026, 10, 6, 5, 42, 0, 0, ZoneId.of("Europe/Berlin"))
        val (home, day) = placeWeather(snapshot, Place.BAD_DUEBEN, Place.LEIPZIG, now, AppLanguage.DE)
        assertEquals("9°", home?.temp)
        assertEquals("🌫️", home?.emoji)
        assertEquals("vor 12 Min.", home?.caption)
        assertEquals("21°", day?.temp)
        assertEquals("☀️", day?.emoji)
        assertEquals("max. heute", day?.caption)
    }

    @Test
    fun `captions follow the interface language`() {
        val snapshot = WeatherFixtures.foggyAutumn
        val now = ZonedDateTime.of(2026, 10, 6, 5, 42, 0, 0, ZoneId.of("Europe/Berlin"))
        val (home, day) = placeWeather(snapshot, Place.BAD_DUEBEN, Place.LEIPZIG, now, AppLanguage.EN)
        assertEquals("12 min ago", home?.caption)
        assertEquals("high today", day?.caption)
        val (_, dayFr) = placeWeather(snapshot, Place.BAD_DUEBEN, Place.LEIPZIG, now, AppLanguage.FR)
        assertEquals("max. aujourd'hui", dayFr?.caption)
    }

    @Test
    fun `changed places show no stale weather`() {
        val snapshot = WeatherFixtures.foggyAutumn
        val now = ZonedDateTime.of(2026, 10, 5, 22, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        val berlin = Place("Berlin", 52.52, 13.405)
        val (home, day) = placeWeather(snapshot, berlin, Place.LEIPZIG, now, AppLanguage.DE)
        assertNull(home)
        assertEquals("max. morgen", day?.caption)
        assertEquals("high tomorrow", placeWeather(snapshot, berlin, Place.LEIPZIG, now, AppLanguage.EN).second?.caption)
        assertEquals(null to null, placeWeather(null, Place.BAD_DUEBEN, Place.LEIPZIG, now, AppLanguage.DE))
    }

    @Test
    fun `nearby coordinates count as the same spot`() {
        val a = Place("Leipzig", 51.3397, 12.3731)
        assertEquals(true, a.sameSpotAs(Place("Leipzig", 51.34, 12.37)))
        assertEquals(false, a.sameSpotAs(Place("Halle", 51.48, 11.97)))
    }
}

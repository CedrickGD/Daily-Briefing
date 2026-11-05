package com.cedrickgd.morgenbriefing.data

import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.WeatherSnapshot
import com.cedrickgd.morgenbriefing.data.remote.OpenMeteoClient
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

class OpenMeteoClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OpenMeteoClient
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        // A comma-decimal locale must not leak into the coordinates ("51,5928").
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString()
        client = OpenMeteoClient(OkHttpClient(), AppJson, baseUrl = base, geocodingUrl = base)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Locale.setDefault(originalLocale)
    }

    private fun serveForecast() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().startsWith("/v1/forecast")) {
                    MockResponse().setBody(OpenMeteoFixture.json()).setHeader("Content-Type", "application/json")
                } else {
                    MockResponse().setResponseCode(404)
                }
        }
    }

    private fun berlin(iso: String): ZonedDateTime = ZonedDateTime.parse("$iso+02:00[Europe/Berlin]")

    private suspend fun snapshotAt(now: ZonedDateTime, home: Place = Place.BAD_DUEBEN): WeatherSnapshot =
        client.snapshot(home, Place.LEIPZIG, dayStart = 10, dayEnd = 18, now = now)

    @Test
    fun `home block uses current values, next four hours and today's sun times`() = runTest {
        serveForecast()
        val s = snapshotAt(berlin("2026-10-06T07:20:00"))

        with(s.home) {
            assertEquals(Place.BAD_DUEBEN, place)
            assertEquals("2026-10-06T07:15", current.time)
            assertEquals(6.3, current.temp, 0.0)
            assertEquals(4.1, current.feels, 0.0)
            assertEquals(45, current.code)
            assertFalse(current.isDay)
            assertEquals(7.2, current.windKmh, 0.0)
            assertEquals(14.4, current.gustKmh, 0.0)
            assertEquals(96, current.humidity)
            assertEquals(300.0, current.visibilityM!!, 0.0)
            assertEquals(
                listOf("2026-10-06T07:00", "2026-10-06T08:00", "2026-10-06T09:00", "2026-10-06T10:00"),
                nextHours.map { it.time },
            )
            assertEquals(listOf(7, 8, 9, 10), nextHours.map { it.hour })
            assertEquals(listOf(6.4, 7.8, 9.9, 12.1), nextHours.map { it.temp })
            assertEquals("07:14", sunrise)
            assertEquals("18:32", sunset)
        }
        assertEquals("2026-10-06T07:20:00+02:00", s.fetchedAt)
    }

    @Test
    fun `day window aggregates today's hours from start to end inclusive`() = runTest {
        serveForecast()
        val day = snapshotAt(berlin("2026-10-06T07:20:00")).day

        assertEquals(Place.LEIPZIG, day.place)
        assertEquals("2026-10-06", day.date)
        assertFalse(day.isTomorrow)
        assertEquals(10, day.startHour)
        assertEquals(18, day.endHour)
        assertEquals((10..18).toList(), day.hours.map { it.hour })
        assertEquals(12.1, day.minTemp, 0.0)
        assertEquals(18.3, day.maxTemp, 0.0)
        assertEquals(40, day.maxPop)
        assertEquals(0.7, day.precipSum, 1e-9)
        assertEquals(38.0, day.maxGustKmh, 0.0)
        assertEquals(3.45, day.uvMax, 0.0)
        assertEquals("18:32", day.sunset)
        // null precipitation probability falls back to 0
        assertEquals(0, day.hours.first { it.hour == 12 }.pop)
        assertTrue(day.hours.first { it.hour == 15 }.isDay)
        assertEquals(24_000.0, day.hours.first().visibilityM!!, 0.0)
    }

    @Test
    fun `after the window ended the day block switches to tomorrow and skips missing hours`() = runTest {
        serveForecast()
        val s = snapshotAt(berlin("2026-10-06T19:05:00"))

        with(s.day) {
            assertEquals("2026-10-07", date)
            assertTrue(isTomorrow)
            // 13:00 has no temperature on day 2 → skipped
            assertEquals(listOf(10, 11, 12, 14, 15, 16, 17, 18), hours.map { it.hour })
            assertEquals(11.8, minTemp, 0.0)
            assertEquals(14.9, maxTemp, 0.0)
            assertEquals(85, maxPop)
            assertEquals(6.7, precipSum, 1e-9)
            assertEquals(65.0, maxGustKmh, 0.0)
            assertEquals(1.2, uvMax, 0.0)
            assertEquals("18:30", sunset)
            assertTrue(hours.all { it.visibilityM == null })
        }
        assertEquals(listOf(19, 20, 21, 22), s.home.nextHours.map { it.hour })
    }

    @Test
    fun `window switches exactly at the end hour`() = runTest {
        serveForecast()
        assertFalse(snapshotAt(berlin("2026-10-06T17:59:00")).day.isTomorrow)
        assertTrue(snapshotAt(berlin("2026-10-06T18:00:00")).day.isTomorrow)
    }

    @Test
    fun `next hours continue past midnight`() = runTest {
        serveForecast()
        val next = snapshotAt(berlin("2026-10-06T22:30:00")).home.nextHours
        assertEquals(
            listOf("2026-10-06T22:00", "2026-10-06T23:00", "2026-10-07T00:00", "2026-10-07T01:00"),
            next.map { it.time },
        )
    }

    @Test
    fun `now in another zone is converted to Berlin time`() = runTest {
        serveForecast()
        val utc = ZonedDateTime.of(2026, 10, 6, 5, 20, 0, 0, ZoneOffset.UTC)
        val s = snapshotAt(utc)
        assertEquals(7, s.home.nextHours.first().hour)
        assertEquals("2026-10-06T07:20:00+02:00", s.fetchedAt)
    }

    @Test
    fun `requests carry all parameters with US formatted coordinates`() = runTest {
        serveForecast()
        snapshotAt(berlin("2026-10-06T07:20:00"))

        assertEquals(2, server.requestCount)
        val requests = listOf(server.takeRequest(), server.takeRequest())
        val latitudes = requests.map { it.requestUrl!!.queryParameter("latitude") }.toSet()
        assertEquals(setOf("51.5928", "51.3397"), latitudes)
        val path = requests.first().path!!
        assertTrue(path, path.startsWith("/v1/forecast?"))
        assertTrue(path, path.contains("timezone=Europe%2FBerlin"))
        assertTrue(path, path.contains("forecast_days=2"))
        assertTrue(path, path.contains("wind_speed_unit=kmh"))
        assertTrue(
            path,
            path.contains(
                "current=temperature_2m,apparent_temperature,precipitation,weather_code,is_day," +
                    "wind_speed_10m,wind_gusts_10m,relative_humidity_2m,visibility",
            ),
        )
        assertTrue(path, path.contains("daily=sunrise,sunset,uv_index_max,temperature_2m_max,temperature_2m_min"))
    }

    @Test
    fun `same coordinates for home and day need only one request`() = runTest {
        serveForecast()
        val s = snapshotAt(berlin("2026-10-06T07:20:00"), home = Place.LEIPZIG)
        assertEquals(1, server.requestCount)
        assertEquals(Place.LEIPZIG, s.home.place)
        assertEquals(Place.LEIPZIG, s.day.place)
    }

    @Test
    fun `http error becomes IOException`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            snapshotAt(berlin("2026-10-06T07:20:00"))
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("503"))
        }
    }

    @Test
    fun `malformed json becomes IOException`() = runTest {
        server.enqueue(MockResponse().setBody("{\"hourly\": [not json"))
        try {
            snapshotAt(berlin("2026-10-06T07:20:00"), home = Place.LEIPZIG)
            fail("expected IOException")
        } catch (_: IOException) {
            // expected
        }
    }

    @Test
    fun `search maps results and joins region parts`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results":[
                  {"id":1,"name":"Bad Düben","latitude":51.5928,"longitude":12.5853,"admin1":"Sachsen","country":"Deutschland"},
                  {"id":2,"name":"Düben","latitude":51.8,"longitude":12.4,"admin1":"","country":"Deutschland"},
                  {"id":3,"name":"Ohne Koordinaten","country":"Deutschland"}
                ],"generationtime_ms":0.5}
                """.trimIndent(),
            ),
        )
        val places = client.searchPlaces("  Bad Düben ", AppLanguage.DE)

        assertEquals(
            listOf(
                Place("Bad Düben", 51.5928, 12.5853, "Sachsen, Deutschland"),
                Place("Düben", 51.8, 12.4, "Deutschland"),
            ),
            places,
        )
        val request = server.takeRequest()
        val url = request.requestUrl!!
        assertEquals("/v1/search", url.encodedPath)
        assertEquals("Bad Düben", url.queryParameter("name"))
        assertEquals("8", url.queryParameter("count"))
        assertEquals("de", url.queryParameter("language"))
        assertEquals("json", url.queryParameter("format"))
    }

    @Test
    fun `search speaks the interface language and names foreign places in their own language`() = runTest {
        val requested = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                synchronized(requested) { requested += url.encodedPath + "?" + url.query }
                return when {
                    url.encodedPath == "/v1/search" -> MockResponse().setBody(
                        """
                        {"results":[
                          {"id":3173435,"name":"Milan","latitude":45.46427,"longitude":9.18951,"country_code":"IT","admin1":"Lombardy","country":"Italy"},
                          {"id":5056280,"name":"Milan","latitude":39.4,"longitude":-82.6,"country_code":"US","admin1":"Ohio","country":"United States"},
                          {"id":2867714,"name":"Munich","latitude":48.13743,"longitude":11.57549,"country_code":"DE","admin1":"Bavaria","country":"Germany"}
                        ]}
                        """.trimIndent(),
                    )
                    url.encodedPath == "/v1/get" && url.queryParameter("id") == "3173435" ->
                        MockResponse().setBody("""{"id":3173435,"name":"Milano","latitude":45.46427,"longitude":9.18951,"country_code":"IT"}""")
                    // Munich's lookup fails: the place still comes, only without a local name.
                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        val places = client.searchPlaces("Milan", AppLanguage.EN)

        assertEquals(
            listOf(
                Place("Milan", 45.46427, 9.18951, "Lombardy, Italy", "IT", localName = "Milano"),
                Place("Milan", 39.4, -82.6, "Ohio, United States", "US"),
                Place("Munich", 48.13743, 11.57549, "Bavaria, Germany", "DE"),
            ),
            places,
        )
        val calls = synchronized(requested) { requested.toList() }
        assertTrue(calls.toString(), "/v1/search?name=Milan&count=8&language=en&format=json" in calls)
        assertTrue(calls.toString(), "/v1/get?id=3173435&language=it&format=json" in calls)
        assertTrue(calls.toString(), "/v1/get?id=2867714&language=de&format=json" in calls)
        // A place in the interface's own language (Milan, Ohio for an English user): no extra lookup.
        assertFalse(calls.toString(), calls.any { "5056280" in it })
        // The news search pages with the local name in the place's own edition.
        assertEquals("Milano", places[0].newsName("it"))
        assertEquals("Milan", places[0].newsName("en"))
    }

    @Test
    fun `search without results key returns empty list`() = runTest {
        server.enqueue(MockResponse().setBody("""{"generationtime_ms":0.3}"""))
        assertEquals(emptyList<Place>(), client.searchPlaces("Xyzzy", AppLanguage.DE))
    }

    @Test
    fun `blank search does not hit the network`() = runTest {
        assertEquals(emptyList<Place>(), client.searchPlaces("   ", AppLanguage.EN))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing current block falls back to the current hour`() = runTest {
        val withoutCurrent = OpenMeteoFixture.json().replace(Regex("\"current\":\\{[^}]*\\},"), "")
        server.enqueue(MockResponse().setBody(withoutCurrent))
        val home = snapshotAt(berlin("2026-10-06T08:40:00"), home = Place.LEIPZIG).home

        assertEquals("2026-10-06T08:40", home.current.time)
        assertEquals(7.8, home.current.temp, 0.0)
        assertEquals(45, home.current.code)
        assertTrue(home.current.isDay)
        assertEquals(800.0, home.current.visibilityM!!, 0.0)
    }
}

package com.cedrickgd.morgenbriefing.domain

import com.cedrickgd.morgenbriefing.data.model.CurrentConditions
import com.cedrickgd.morgenbriefing.data.model.HomeWeather
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.WeatherSnapshot
import com.cedrickgd.morgenbriefing.domain.WeatherFixtures.foggyAutumn
import com.cedrickgd.morgenbriefing.domain.WeatherFixtures.frostyWinter
import com.cedrickgd.morgenbriefing.domain.WeatherFixtures.rainyWindy
import com.cedrickgd.morgenbriefing.domain.WeatherFixtures.summerThunder
import com.cedrickgd.morgenbriefing.domain.WeatherFixtures.tomorrowEvening
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The weather texts in the other interface languages: exact English, spot checks, invariants. */
class WeatherInsightsLanguagesTest {

    private val en = AppLanguage.EN

    private fun assertContains(haystack: String, needle: String) =
        assertTrue("Expected \"$needle\" in \"$haystack\"", haystack.contains(needle))

    // --- English, exact -------------------------------------------------------------------------

    @Test
    fun `foggy autumn in English`() {
        assertEquals("Foggy start, then golden October at 21°.", WeatherInsights.headline(foggyAutumn, en))
        assertEquals(
            "Still dark, sunrise at 07:18 – foggy, but dry for the next few hours. " +
                "Visibility down to just over 1 km in places – headlights on when driving.",
            WeatherInsights.homeNote(foggyAutumn, en),
        )
        assertEquals(
            "From 13° to 21°, full sun from midday, no rain, hardly any wind.",
            WeatherInsights.daySummary(foggyAutumn, en),
        )
        val advice = WeatherInsights.clothing(foggyAutumn, en)
        assertEquals(listOf("Jacket", "T-shirt", "Sunglasses"), advice.items.map { it.label })
        assertEquals(
            "In the morning a jacket or fleece over your shirt – the fog makes 8° feel damp. " +
                "From midday a T-shirt will do, sunglasses are worth it. No umbrella needed.",
            advice.text,
        )
        assertEquals("Jacket first, T-shirt from midday, no umbrella.", advice.short)

        val fog = WeatherInsights.warnings(foggyAutumn, en).single()
        assertEquals("Fog", fog.title)
        assertEquals("Visibility down to ~1 km in places – dipped headlights on and keep your distance.", fog.text)
    }

    @Test
    fun `English notification uses 12-hour times and the English news line`() {
        val n = WeatherInsights.notification(foggyAutumn, listOf("Euro slides", "AI task force starts"), en)
        assertEquals("🌫️ 9° in Bad Düben · feels like 8°", n.title)
        assertEquals("Leipzig 10 am–6 pm: 13→21°, sunny · Jacket first, T-shirt from midday, no umbrella", n.text)
        assertEquals("📰 Euro slides · AI task force starts", n.bigText.lines().last())
        assertEquals("🧥 Jacket first, T-shirt from midday, no umbrella.", n.bigText.lines()[2])

        val noWeather = WeatherInsights.notification(null, emptyList(), en)
        assertEquals("☀️ Your Daily Briefing", noWeather.title)
        assertEquals("Weather unavailable right now – open the app for an update.", noWeather.text)
        assertEquals("📰 Your news is waiting in the app.", noWeather.bigText.lines().last())
    }

    @Test
    fun `English thunder, rain and tomorrow`() {
        assertEquals("Hot at 31°, storms from 4 pm.", WeatherInsights.headline(summerThunder, en))
        assertEquals(
            "From 26° to 31°, lots of sun at first, thunderstorms possible from 4 pm.",
            WeatherInsights.daySummary(summerThunder, en),
        )
        val titles = WeatherInsights.warnings(summerThunder, en).map { it.title }
        assertEquals(listOf("Thunderstorms", "Heat", "High UV"), titles)

        assertEquals("Windy and wet – bring a hood today.", WeatherInsights.headline(rainyWindy, en))
        assertEquals(
            "From 12° to 14°, rain most of the day, up to 80%, gusts up to 50 km/h.",
            WeatherInsights.daySummary(rainyWindy, en),
        )
        assertContains(WeatherInsights.clothing(rainyWindy, en).text, "Rain jacket instead of an umbrella – too windy.")

        assertEquals("Tomorrow: Grey day at 15°, rain from 2 pm.", WeatherInsights.headline(tomorrowEvening, en))
        val text = WeatherInsights.notification(tomorrowEvening, emptyList(), en).text
        assertTrue(text, text.startsWith("Leipzig tomorrow 10 am–6 pm: 12→15°, rain from 2 pm · "))
    }

    @Test
    fun `English black ice morning`() {
        val ice = WeatherInsights.warnings(frostyWinter, en).first()
        assertEquals(WarningLevel.SEVERE, ice.level)
        assertEquals("Black ice", ice.title)
        assertEquals("From 0° to 2°, rain until noon, dry after that.", WeatherInsights.daySummary(frostyWinter, en))
        val advice = WeatherInsights.clothing(frostyWinter, en)
        assertTrue(advice.items.map { it.label }.containsAll(listOf("Winter coat", "Gloves", "Sturdy shoes", "Umbrella")))
        assertContains(advice.text, "sturdy shoes")
    }

    // --- French, Spanish, Italian: spot checks ------------------------------------------------

    @Test
    fun `French texts`() {
        val fr = AppLanguage.FR
        assertEquals("Brouillard au réveil, puis octobre doré et 21°.", WeatherInsights.headline(foggyAutumn, fr))
        assertEquals("De 13° à 21°, grand soleil dès midi, pas de pluie, peu de vent.", WeatherInsights.daySummary(foggyAutumn, fr))
        assertEquals("Chaleur jusqu'à 31°, orages dès 16 h.", WeatherInsights.headline(summerThunder, fr))
        assertEquals("Demain : Gris et 15°, pluie dès 14 h.", WeatherInsights.headline(tomorrowEvening, fr))
        val advice = WeatherInsights.clothing(summerThunder, fr)
        // Plural agreement: "des vêtements légers et un short suffisent".
        assertContains(advice.text, "Dès 10 h, des vêtements légers et un short suffisent")
        assertEquals("🌫️ 9° à Bad Düben · ressenti 8°", WeatherInsights.notification(foggyAutumn, emptyList(), fr).title)
    }

    @Test
    fun `Spanish texts`() {
        val es = AppLanguage.ES
        assertEquals("Niebla al empezar, luego octubre dorado con 21°.", WeatherInsights.headline(foggyAutumn, es))
        assertEquals("Calor de hasta 31°, tormentas desde las 16 h.", WeatherInsights.headline(summerThunder, es))
        assertEquals("De 0° a 2°, lluvia hasta las 12 h, luego seco.", WeatherInsights.daySummary(frostyWinter, es))
        assertEquals(
            "De 12° a 14°, lluvia casi todo el día, hasta un 80 %, rachas de hasta 50 km/h.",
            WeatherInsights.daySummary(rainyWindy, es),
        )
        assertEquals("Primero chaqueta, camiseta desde el mediodía, sin paraguas.", WeatherInsights.clothing(foggyAutumn, es).short)
    }

    @Test
    fun `Italian texts`() {
        val it = AppLanguage.IT
        assertEquals("Nebbia al risveglio, poi ottobrata con 21°.", WeatherInsights.headline(foggyAutumn, it))
        assertEquals("Caldo fino a 31°, temporali dalle 16.", WeatherInsights.headline(summerThunder, it))
        assertEquals("Da 0° a 2°, pioggia fino a mezzogiorno, poi asciutto.", WeatherInsights.daySummary(frostyWinter, it))
        // Plural agreement: "bastano vestiti leggeri e pantaloncini".
        assertContains(WeatherInsights.clothing(summerThunder, it).text, "Dalle 10 bastano vestiti leggeri e pantaloncini")
        assertEquals("Domani: Giornata grigia con 15°, pioggia dalle 14.", WeatherInsights.headline(tomorrowEvening, it))
    }

    @Test
    fun `condition and precipitation words follow the language`() {
        assertEquals("sonnig", Condition.CLEAR.labelFor(true, AppLanguage.DE))
        assertEquals("klar", Condition.CLEAR.labelFor(false, AppLanguage.DE))
        assertEquals("sunny", Condition.CLEAR.labelFor(true, en))
        assertEquals("mostly clear", Condition.MOSTLY_CLEAR.labelFor(false, en))
        assertEquals("brouillard", Condition.FOG.labelFor(true, AppLanguage.FR))
        assertEquals("tormenta", Condition.THUNDER.labelFor(true, AppLanguage.ES))
        assertEquals("parzialmente nuvoloso", Condition.PARTLY_CLOUDY.labelFor(true, AppLanguage.IT))
        assertEquals("Sleet", PrecipKind.SLEET.word(en))
        assertEquals("Neige fondue", PrecipKind.SLEET.word(AppLanguage.FR))
        for (lang in AppLanguage.entries) {
            val labels = Condition.entries.map { it.labelFor(true, lang) }
            assertTrue("$lang: every condition is named", labels.all { it.isNotBlank() })
        }
    }

    // --- Invariants in every language ---------------------------------------------------------

    @Test
    fun `invariants hold in every language`() {
        val germanOnly = Regex("""\b(Uhr|und|bei|gefühlt|Regen|Schirm|morgen)\b""")
        for (lang in AppLanguage.entries) {
            for (s in sweep()) {
                val ctx = "$lang ${s.day.date} ${s.home.current} ${s.day.hours.map { it.code }}"
                val headline = WeatherInsights.headline(s, lang)
                assertTrue("headline too long: $headline | $ctx", headline.length <= 60)
                assertTrue(headline, headline.endsWith("."))
                assertTrue(headline, headline.first().isUpperCase())

                val clothing = WeatherInsights.clothing(s, lang)
                assertTrue("short too long: ${clothing.short} | $ctx", clothing.short.wordCount() <= 8)
                assertTrue(clothing.short, clothing.short.first().isUpperCase())
                assertTrue("items: ${clothing.items} | $ctx", clothing.items.size in 2..5)
                assertEquals(clothing.items.map { it.label }.distinct(), clothing.items.map { it.label })
                val sentences = Regex("[.!?](\\s|$)").findAll(clothing.text).count()
                assertTrue("sentences: ${clothing.text} | $ctx", sentences in 1..3)

                val note = WeatherInsights.homeNote(s, lang)
                assertTrue("home note: $note", Regex("[.!?](\\s|$)").findAll(note).count() in 1..2)
                assertTrue(note, note.first().isUpperCase())

                val warnings = WeatherInsights.warnings(s, lang)
                assertEquals(warnings.map { it.title }.distinct(), warnings.map { it.title })

                val summary = WeatherInsights.daySummary(s, lang)
                assertTrue(summary, summary.first().isUpperCase())

                val n = WeatherInsights.notification(s, emptyList(), lang)
                val texts = listOf(headline, note, summary, clothing.text, clothing.short, n.title, n.text, n.bigText) +
                    warnings.flatMap { listOf(it.title, it.text) }
                for (text in texts) {
                    assertFalse("-0° in $text | $ctx", text.contains("-0°"))
                    assertFalse("null in $text | $ctx", text.contains("null"))
                    assertFalse("double space in \"$text\" | $ctx", text.contains("  "))
                    assertFalse(".. in $text | $ctx", text.contains(".."))
                    assertFalse(" , in $text | $ctx", text.contains(" ,"))
                    if (lang != AppLanguage.DE) {
                        assertFalse("German word in $lang text: $text", germanOnly.containsMatchIn(text.replace("Bad Düben", "")))
                    }
                }
            }
        }
    }

    @Test
    fun `the morning wording is the same for the same snapshot`() {
        for (lang in AppLanguage.entries) {
            assertEquals(WeatherInsights.headline(foggyAutumn, lang), WeatherInsights.headline(foggyAutumn.copy(), lang))
        }
    }

    /** The named fixtures plus a sweep of synthetic days (the same grid as WeatherInsightsTest). */
    private fun sweep(): List<WeatherSnapshot> {
        val codes = listOf(0, 1, 2, 3, 45, 51, 61, 65, 66, 71, 80, 85, 95)
        val all = WeatherFixtures.allNamed.values.toMutableList()
        var seed = 0
        for (base in listOf(-14.0, -7.0, -2.0, 1.0, 5.0, 9.0, 14.0, 18.0, 23.0, 28.0, 33.0)) {
            for (pop in listOf(0, 30, 60, 90)) {
                for (gust in listOf(10.0, 45.0, 85.0)) {
                    seed++
                    val code = codes[seed % codes.size]
                    val month = 1 + seed % 12
                    val date = String.format(java.util.Locale.ROOT, "2026-%02d-15", month)
                    val temps = (0..8).map { base + it * 0.9 - (if (it > 6) 2.0 else 0.0) }
                    val hours = WeatherFixtures.window(
                        date = date, startHour = 10, temps = temps,
                        feels = temps.map { it - gust / 25 },
                        codes = (0..8).map { if (it % 3 == 0) code else codes[(seed + it) % codes.size] },
                        pops = (0..8).map { (pop + it * 5).coerceAtMost(100) },
                        precip = (0..8).map { if (pop >= 60) 0.8 else 0.0 },
                        gusts = (0..8).map { gust },
                    )
                    all += WeatherSnapshot(
                        fetchedAt = "${date}T05:30:00+02:00",
                        home = HomeWeather(
                            place = Place.BAD_DUEBEN,
                            current = CurrentConditions(
                                time = "${date}T05:30", temp = base - 3, feels = base - 5, code = code,
                                isDay = false, windKmh = gust / 2, gustKmh = gust, precipMm = if (pop >= 60) 0.5 else 0.0,
                                humidity = 70 + seed % 30, visibilityM = 400.0 + seed * 97 % 20_000,
                            ),
                            nextHours = WeatherFixtures.window(date, 5, temps.take(4).map { it - 4 }, codes = List(4) { code }, pops = List(4) { pop }),
                            sunrise = "06:40",
                            sunset = "19:10",
                        ),
                        day = WeatherFixtures.day(date, hours, isTomorrow = seed % 7 == 0, uvMax = (seed % 10).toDouble()),
                    )
                }
            }
        }
        return all
    }
}

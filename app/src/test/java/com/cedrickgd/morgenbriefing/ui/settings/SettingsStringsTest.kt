package com.cedrickgd.morgenbriefing.ui.settings

import com.cedrickgd.morgenbriefing.data.model.AccentId
import com.cedrickgd.morgenbriefing.data.model.EffectsMode
import com.cedrickgd.morgenbriefing.data.model.NewsAge
import com.cedrickgd.morgenbriefing.data.model.PrefetchMode
import com.cedrickgd.morgenbriefing.data.model.ThemeMode
import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.OnboardingStrings
import com.cedrickgd.morgenbriefing.i18n.RegionStrings
import com.cedrickgd.morgenbriefing.i18n.SettingsStrings
import com.cedrickgd.morgenbriefing.i18n.SourcesStrings
import com.cedrickgd.morgenbriefing.perf.DeviceTier
import com.cedrickgd.morgenbriefing.perf.TierReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStringsTest {

    /** Every fixed text (`val`) of a strings interface, per language. */
    private fun <T : Any> fixedTexts(type: Class<T>, of: (AppLanguage) -> T): Map<AppLanguage, Map<String, String>> =
        AppLanguage.entries.associateWith { lang ->
            val strings = of(lang)
            type.methods
                .filter { it.parameterCount == 0 && it.returnType == String::class.java && it.name.startsWith("get") }
                .associate { it.name to it.invoke(strings) as String }
        }

    private fun <T : Any> checkFixed(type: Class<T>, mayBeEmpty: Set<String> = emptySet(), of: (AppLanguage) -> T) {
        val texts = fixedTexts(type, of)
        val german = texts.getValue(AppLanguage.DE)
        assertTrue("${type.simpleName} has texts", german.size > 5)
        texts.forEach { (lang, byName) ->
            byName.forEach { (name, text) ->
                if (name !in mayBeEmpty) assertTrue("${type.simpleName}.$name in $lang is blank", text.isNotBlank())
                assertEquals("${type.simpleName}.$name in $lang has stray spaces", text.trim(), text)
            }
            if (lang != AppLanguage.DE) {
                // A text equal to the German one is a missed translation – except names ("Material You").
                val same = byName.filter { (name, text) -> text.isNotEmpty() && german[name] == text }
                assertTrue("${type.simpleName} in $lang copies German: ${same.keys}", same.size <= byName.size / 10)
            }
        }
    }

    @Test
    fun `every fixed text exists in every language and is translated`() {
        checkFixed(SettingsStrings::class.java, mayBeEmpty = setOf("getClockSuffix")) { SettingsStrings.of(it) }
        checkFixed(RegionStrings::class.java) { RegionStrings.of(it) }
        checkFixed(SourcesStrings::class.java) { SourcesStrings.of(it) }
        checkFixed(OnboardingStrings::class.java) { OnboardingStrings.of(it) }
    }

    @Test
    fun `every enum label exists in every language`() {
        for (lang in AppLanguage.entries) {
            val s = SettingsStrings.of(lang)
            val labels = AccentId.entries.map(s::accentName) + NewsAge.entries.map(s::newsAge) +
                WeatherCard.entries.flatMap { listOf(s.weatherCardTitle(it), s.weatherCardText(it)) } +
                ThemeMode.entries.map(s::themeMode) + PrefetchMode.entries.flatMap { listOf(s.prefetchMode(it), s.prefetchHint(it)) } +
                EffectsMode.entries.map(s::effectsMode)
            labels.forEach { assertTrue("$lang: \"$it\"", it.isNotBlank()) }
            assertEquals("$lang effects modes are distinct", EffectsMode.entries.size, EffectsMode.entries.map(s::effectsMode).distinct().size)
            assertEquals("$lang accent names are distinct", AccentId.entries.size, AccentId.entries.map(s::accentName).distinct().size)
        }
    }

    @Test
    fun `German keeps today's wording`() {
        val de = SettingsStrings.of(AppLanguage.DE)
        assertEquals("Morgenrot", de.accentName(AccentId.MORGENROT))
        assertEquals("Heute", de.newsAge(NewsAge.DAY))
        assertEquals("Alles, was die Quellen liefern", de.newsAge(NewsAge.ALL))
        assertEquals("Glätte, Sturm, Hitze – wenn etwas ansteht", de.weatherCardText(WeatherCard.WARNINGS))
        assertEquals("Artikel in der App lesen", de.readInApp)
        assertEquals("Nur vollständige Artikel", de.readableOnly)
        assertEquals("Nur im WLAN", de.prefetchMode(PrefetchMode.WIFI_ONLY))
        assertEquals("Themen entdecken", SourcesStrings.of(AppLanguage.DE).discover)
        assertEquals("Sprache & Region", de.languageTitle)
        assertEquals("Wie das Handy", RegionStrings.of(AppLanguage.DE).followPhone)
    }

    @Test
    fun `the readable-only setting says what it does, in every language`() {
        for (lang in AppLanguage.entries) {
            val s = SettingsStrings.of(lang)
            listOf(s.readableOnly, s.readableOnlyOn, s.readableOnlyOff, s.prefetchWhileReadableOnly).forEach { assertTrue("$lang: \"$it\"", it.isNotBlank()) }
            // The hint names the price: articles are loaded ahead, on mobile data too.
            assertTrue("$lang mentions the data use", s.readableOnlyOn.length > 120)
        }
        val de = SettingsStrings.of(AppLanguage.DE)
        assertTrue(de.readableOnlyOn.contains("mobile Daten"))
        assertTrue(de.readableOnlyOn.contains("Anriss"))
        assertTrue(SettingsStrings.of(AppLanguage.EN).readableOnlyOn.contains("mobile data"))
        assertTrue(SettingsStrings.of(AppLanguage.FR).readableOnlyOn.contains("tu ne vois"))
    }

    @Test
    fun `the effects hint says what automatic picks and why, in every language`() {
        for (lang in AppLanguage.entries) {
            val s = SettingsStrings.of(lang)
            val hints = TierReason.entries.flatMap { reason ->
                val tier = if (reason == TierReason.CAPABLE) DeviceTier.FULL else DeviceTier.LOW
                listOf(s.effectsHint(tier, reason, chosen = true), s.effectsHint(tier, reason, chosen = false))
            }
            hints.forEach { hint ->
                assertTrue("$lang: \"$hint\"", hint.isNotBlank())
                assertEquals("$lang: \"$hint\" has stray spaces", hint.trim(), hint)
            }
            // Chosen or not, the line reads differently; full and light read differently.
            assertTrue(lang.name, s.effectsHint(DeviceTier.LOW, TierReason.SLOW_CPU, true) != s.effectsHint(DeviceTier.LOW, TierReason.SLOW_CPU, false))
            assertTrue(lang.name, s.effectsHint(DeviceTier.LOW, TierReason.SLOW_CPU, true) != s.effectsHint(DeviceTier.FULL, TierReason.CAPABLE, true))
        }
        val de = SettingsStrings.of(AppLanguage.DE)
        assertEquals("Effekte", de.effectsOverline)
        assertEquals(listOf("Automatisch", "Volle Effekte", "Sparsam"), EffectsMode.entries.map(de::effectsMode))
        assertEquals("Für dieses Handy: sparsam – flüssiger auf einfacher Hardware", de.effectsHint(DeviceTier.LOW, TierReason.LITTLE_MEMORY, chosen = true))
        assertEquals("Gerade: sparsam – der Energiesparmodus ist an", de.effectsHint(DeviceTier.LOW, TierReason.POWER_SAVER, chosen = true))
        assertTrue(de.effectsHint(DeviceTier.FULL, TierReason.CAPABLE, chosen = false).startsWith("Automatisch wäre hier: volle Effekte"))
    }

    @Test
    fun `snackbars are worded in the interface language`() {
        assertEquals("Dein Briefing kommt jetzt um 05:30 Uhr.", SettingsMessage.BriefingTime(5, 30).text(AppLanguage.DE))
        assertEquals("Your briefing now arrives at 05:30.", SettingsMessage.BriefingTime(5, 30).text(AppLanguage.EN))
        assertEquals("„Blogs“ angelegt – füge jetzt eine Quelle hinzu.", SettingsMessage.TopicCreated("Blogs", search = false).text(AppLanguage.DE))
        assertEquals("“Midterms” is now in your feed.", SettingsMessage.TopicCreated("Midterms", search = true).text(AppLanguage.EN))
        assertEquals("Du folgst jetzt den Nachrichten aus Washington.", SettingsMessage.PlaceAdded("Washington").text(AppLanguage.DE))
        val all = listOf(
            SettingsMessage.BriefingTime(6, 0), SettingsMessage.KeepOneTopic, SettingsMessage.TopicCreated("x", true),
            SettingsMessage.TopicDeleted, SettingsMessage.FeedAdded("x"), SettingsMessage.FeedRemoved,
            SettingsMessage.PlaceAdded("x"), SettingsMessage.PlaceRemoved("x"), SettingsMessage.SaveFailed,
        )
        for (lang in AppLanguage.entries) all.forEach { assertTrue("$lang $it", it.text(lang).isNotBlank()) }
    }

    @Test
    fun `counts read naturally`() {
        val de = SourcesStrings.of(AppLanguage.DE)
        assertEquals("Du folgst 1 Thema", de.followingCount(1))
        assertEquals("Du folgst 9 Themen", de.followingCount(9))
        assertEquals("You follow 9 topics", SourcesStrings.of(AppLanguage.EN).followingCount(9))
        assertEquals("1 Quelle ausgeblendet – nur bei der Redaktion lesbar", de.hiddenUnreadable(1))
        assertEquals("Gefunden: heise online · 30 Meldungen", de.found("heise online", 30))
        assertEquals("Wie das Handy · Deutsch", RegionStrings.of(AppLanguage.DE).followPhoneWith("Deutsch"))
        assertEquals("Schritt 2 von 4", OnboardingStrings.of(AppLanguage.DE).stepOf(2, 4))
    }
}

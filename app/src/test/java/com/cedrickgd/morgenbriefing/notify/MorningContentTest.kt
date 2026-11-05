package com.cedrickgd.morgenbriefing.notify

import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.domain.WeatherFixtures
import com.cedrickgd.morgenbriefing.i18n.NotifyStrings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningContentTest {

    private fun news(topic: String, vararg headlines: String) = TopicNews(
        topicId = topic,
        fetchedAt = "2026-10-29T06:00:00+01:00",
        items = headlines.mapIndexed { i, h -> NewsItem("$topic-$i", h, url = "https://x/$topic/$i", source = "S", sourceId = "s", topicId = topic) },
    )

    @Test
    fun `one headline per visible topic in the user's order`() {
        val settings = AppSettings(topicOrder = listOf("sport", "politik", "ki"), hiddenTopics = setOf("politik"))
        val repo = mapOf(
            "ki" to news("ki", "KI eins", "KI zwei"),
            "politik" to news("politik", "Politik eins"),
            "sport" to news("sport", "Sport eins"),
            "welt" to news("welt", "Welt eins"),
        )
        assertEquals(listOf("Sport eins", "KI eins", "Welt eins"), topHeadlines(repo, settings))
        assertEquals(listOf("Sport eins"), topHeadlines(repo, settings, count = 1))
        assertTrue(topHeadlines(emptyMap(), settings).isEmpty())
    }

    @Test
    fun `with nur vollstaendige Artikel only verified headlines go out, the next article stands in`() = runTest {
        val settings = AppSettings(topicOrder = listOf("sport", "politik", "ki"), readableOnly = true)
        val repo = mapOf(
            "ki" to news("ki", "KI eins", "KI zwei", "KI drei", "KI vier"),
            "politik" to news("politik", "Politik eins", "Politik zwei"),
            "sport" to news("sport", "Sport eins"),
            "welt" to news("welt", "Welt eins", "Welt zwei"),
        )
        val candidates = headlineCandidates(repo, settings, count = 3)
        assertEquals("the first count + 2 topics, three articles each", listOf(listOf("sport-0"), listOf("politik-0", "politik-1"), listOf("ki-0", "ki-1", "ki-2"), listOf("welt-0", "welt-1")), candidates.map { list -> list.map { it.id } })

        // sport-0 is a teaser, politik-0 is complete, the KI ones are walled until ki-2.
        val verdicts = mapOf("sport-0" to ArticleStatus.PAYWALL, "politik-0" to ArticleStatus.READABLE, "ki-0" to ArticleStatus.BLOCKED, "ki-1" to ArticleStatus.PAYWALL, "ki-2" to ArticleStatus.READABLE, "welt-0" to ArticleStatus.READABLE)
        val checked = ArrayList<String>()
        verifyHeadlineCandidates(candidates) { item ->
            checked += item.id
            verdicts[item.id] ?: ArticleStatus.FAILED
        }
        assertEquals("a topic stops at its first complete article", setOf("sport-0", "politik-0", "ki-0", "ki-1", "ki-2", "welt-0"), checked.toSet())
        val status = { id: String -> verdicts[id] ?: ArticleStatus.UNKNOWN }
        assertEquals(listOf("Politik eins", "KI drei", "Welt eins"), verifiedHeadlines(candidates, status))
        // Nothing verified (offline, time up): no unverified headline sneaks in.
        assertTrue(verifiedHeadlines(candidates, { ArticleStatus.UNKNOWN }).isEmpty())
    }

    @Test
    fun `notification text carries the headlines and survives without weather`() {
        val text = morningText(null, listOf("Eins", "Zwei", "Drei", "Vier"), AppLanguage.DE)
        assertEquals("Eins", text.text)
        assertTrue(text.bigText.contains("📰 Eins · Zwei · Drei"))
        val none = morningText(null, emptyList(), AppLanguage.DE)
        assertTrue(none.bigText.contains("Die Nachrichten warten in der App."))
    }

    @Test
    fun `the morning notification speaks the interface language`() {
        val en = morningText(WeatherFixtures.foggyAutumn, listOf("Markets rally"), AppLanguage.EN)
        assertEquals("🌫️ 9° in Bad Düben · feels like 8°", en.title)
        assertTrue(en.text, en.text.startsWith("Leipzig 10 am–6 pm: 13→21°, sunny · "))
        assertEquals("📰 Markets rally", en.bigText.lines().last())

        val fr = morningText(null, emptyList(), AppLanguage.FR)
        assertEquals("☀️ Ton Daily Briefing", fr.title)
        assertTrue(fr.bigText.contains("📰 Tes infos t'attendent dans l'appli."))

        val es = morningText(WeatherFixtures.summerThunder, emptyList(), AppLanguage.ES)
        assertTrue(es.text, es.text.startsWith("Leipzig 10–18 h: 26→31°, tormentas desde las 16 h · "))

        val it = morningText(WeatherFixtures.foggyAutumn, emptyList(), AppLanguage.IT)
        assertEquals("🌫️ 9° a Bad Düben · percepita 8°", it.title)
    }

    @Test
    fun `notify strings exist in every language`() {
        for (lang in AppLanguage.entries) {
            val s = NotifyStrings.of(lang)
            listOf(s.fallbackTitle, s.fallbackText, s.syncTitle, s.syncText).forEach { assertTrue("$lang", it.isNotBlank()) }
        }
        assertEquals("Briefing wird geladen…", NotifyStrings.of(AppLanguage.DE).syncTitle)
        assertEquals("Loading your briefing…", NotifyStrings.of(AppLanguage.EN).syncTitle)
    }
}

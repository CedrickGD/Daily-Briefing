package com.cedrickgd.morgenbriefing.ui.settings

import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.util.SystemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusLogicTest {

    private fun status(
        notify: Boolean = true,
        exactRelevant: Boolean = true,
        exact: Boolean = true,
        battery: Boolean = true,
    ) = SystemStatus(
        permissionGranted = notify,
        appNotificationsEnabled = notify,
        morningChannelBlocked = false,
        exactAlarmRelevant = exactRelevant,
        exactAlarmsAllowed = exact,
        batteryUnrestricted = battery,
    )

    @Test
    fun `three pills, all green when everything is allowed`() {
        val items = statusItems(lang = AppLanguage.DE, system = status())
        assertEquals(listOf(StatusKind.NOTIFICATIONS, StatusKind.EXACT_ALARM, StatusKind.BATTERY), items.map { it.kind })
        assertTrue(items.all { it.level == StatusLevel.OK })
        val hero = heroText(enabled = true, items = items, day = "morgen", time = "06:30", lang = AppLanguage.DE)
        assertTrue(hero.ready)
        assertEquals("Alles bereit für morgen, 06:30", hero.title)
    }

    @Test
    fun `exact alarms do not matter below android 12`() {
        val items = statusItems(lang = AppLanguage.DE, system = status(exactRelevant = false, exact = false))
        assertEquals(StatusLevel.OK, items.single { it.kind == StatusKind.EXACT_ALARM }.level)
    }

    @Test
    fun `blocked notifications are the headline problem`() {
        val items = statusItems(lang = AppLanguage.DE, system = status(notify = false, battery = false))
        val hero = heroText(enabled = true, items = items, day = "morgen", time = "06:30", lang = AppLanguage.DE)
        assertFalse(hero.ready)
        assertEquals("Noch kommt morgens nichts an", hero.title)
        assertEquals("2 Kleinigkeiten fehlen noch – tippe auf die gelben Punkte.", hero.subtitle)
    }

    @Test
    fun `one problem gets its own hint and off is off`() {
        val items = statusItems(lang = AppLanguage.DE, system = status(battery = false))
        assertEquals("Fast bereit für heute, 06:30", heroText(true, items, "heute", "06:30", AppLanguage.DE).title)
        assertTrue(heroText(true, items, "heute", "06:30", AppLanguage.DE).subtitle.contains("Akku"))
        assertEquals("Dein Morgenbriefing ist aus", heroText(false, items, "heute", "06:30", AppLanguage.DE).title)
    }

    @Test
    fun `pills and hero speak the interface language`() {
        val items = statusItems(status(notify = false), AppLanguage.EN)
        assertEquals(listOf("Notifications", "Exact time", "Battery"), items.map { it.label })
        assertEquals("not allowed", items.first().state)
        val hero = heroText(true, items, "tomorrow", "06:30", AppLanguage.EN)
        assertEquals("Nothing reaches you in the morning yet", hero.title)
        assertEquals("Allow notifications – tap the yellow dot below.", hero.subtitle)
        val ready = heroText(true, statusItems(status(), AppLanguage.FR), "demain", "06:30", AppLanguage.FR)
        assertEquals("Tout est prêt pour demain, 06:30", ready.title)
        for (lang in AppLanguage.entries) {
            val all = statusItems(status(notify = false, exact = false, battery = false), lang)
            assertTrue(all.all { it.label.isNotBlank() && it.state.isNotBlank() })
            val text = heroText(true, all, "x", "06:30", lang)
            assertTrue("$lang", text.subtitle.contains("3"))
            assertFalse(heroText(false, all, "x", "06:30", lang).ready)
        }
    }
}

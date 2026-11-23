package com.cedrickgd.morgenbriefing.ui.discover

import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.DiscoverStrings
import com.cedrickgd.morgenbriefing.i18n.SearchStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The texts of discover and search exist in every language and carry their values. */
class DiscoverTextsTest {

    @Test
    fun `texts with values name them in every language`() {
        AppLanguage.entries.forEach { lang ->
            val d = DiscoverStrings.of(lang)
            val s = SearchStrings.of(lang)
            listOf(
                d.placeAdded("Washington"), d.removePlace("Washington"), d.placeNothingFound("Washington"),
                d.removeSearch("Washington"), d.searchRemoved("Washington"), d.followCategoryHint("Washington"),
                s.followed("Washington"), s.unfollowed("Washington"), s.emptyTitle("Washington"), s.removeRecent("Washington"),
                s.searching("Washington"), s.searchFor("Washington"),
            ).forEach { text -> assertTrue("$lang: $text", "Washington" in text) }
            listOf(s.trendTermsOverline, s.headlineTermsOverline).forEach { text -> assertTrue("$lang", text.isNotBlank()) }
            assertTrue("$lang", "Englisch" in d.placeNews("Englisch", "USA") && "USA" in d.placeNews("Englisch", "USA"))
            assertTrue("$lang", d.tileDescription("Sport", "Fußball", 2).startsWith("Sport. Fußball."))
            listOf(d.title, d.follow, d.following, d.lastTopicHint, s.fieldHint, s.follow, s.following, s.offline)
                .forEach { assertTrue("$lang: blank text", it.isNotBlank()) }
            assertNotEquals("$lang: follow and following look alike", d.follow, d.following)
            assertNotEquals("$lang: follow and following look alike", s.follow, s.following)
        }
    }

    @Test
    fun `counts use singular and plural`() {
        val de = DiscoverStrings.of(AppLanguage.DE)
        assertEquals("Du folgst einem Thema", de.followingSummary(1))
        assertEquals("Du folgst 9 Themen", de.followingSummary(9))
        assertEquals("1 Unterthema", de.subtopicCount(1))
        assertEquals("8 Unterthemen", de.subtopicCount(8))
        assertEquals("You follow 9 topics", DiscoverStrings.of(AppLanguage.EN).followingSummary(9))
        val search = SearchStrings.of(AppLanguage.DE)
        assertEquals("1 Meldung", search.resultCount(1))
        assertEquals("23 Meldungen", search.resultCount(23))
        assertEquals("Das war's · 1 Treffer", search.end(1))
        AppLanguage.entries.forEach { lang ->
            val s = SearchStrings.of(lang)
            assertNotEquals("$lang", s.resultCount(1), s.resultCount(2).replace("2", "1"))
        }
    }
}

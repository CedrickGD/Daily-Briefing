package com.cedrickgd.morgenbriefing.ui.favorites

import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.FavoritesStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesStringsTest {

    private val EM_DASH = 0x2014.toChar()

    @Test
    fun `every language names the list, the star both ways and the empty state`() {
        for (lang in AppLanguage.entries) {
            val s = FavoritesStrings.of(lang)
            listOf(s.overline, s.title, s.openFavorites, s.addFavorite, s.removeFavorite, s.added, s.removed, s.emptyTitle, s.emptyText)
                .forEach { assertTrue("$lang", it.isNotBlank()) }
            assertTrue("$lang", s.saved(1).isNotBlank() && s.saved(7).isNotBlank())
            assertNotEquals("$lang", s.addFavorite, s.removeFavorite)
            assertNotEquals("$lang", s.added, s.removed)
            // No em dash in the copy.
            listOf(s.emptyText, s.emptyTitle, s.addFavorite, s.removeFavorite).forEach { assertFalse("$lang", EM_DASH in it) }
        }
    }

    @Test
    fun `the languages really differ – no silent German fallback`() {
        val titles = AppLanguage.entries.map { FavoritesStrings.of(it).addFavorite }
        assertEquals(titles.size, titles.toSet().size)
        assertEquals("Favoriten", FavoritesStrings.of(AppLanguage.DE).title)
        assertEquals("Favorites", FavoritesStrings.of(AppLanguage.EN).title)
    }
}

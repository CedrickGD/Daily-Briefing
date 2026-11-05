package com.cedrickgd.morgenbriefing.ui.shell

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ShellStrings
import com.cedrickgd.morgenbriefing.ui.nav.ArticleHandoff
import com.cedrickgd.morgenbriefing.ui.nav.ArticleRoute
import com.cedrickgd.morgenbriefing.ui.nav.DiscoverRoute
import com.cedrickgd.morgenbriefing.ui.nav.MainRoute
import com.cedrickgd.morgenbriefing.ui.nav.SearchRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavPlanTest {

    @Test
    fun `opening a screen from the pager pushes it`() {
        assertEquals(NavPlan.PUSH, NavPlan.of(top = MainRoute, sameKindBelow = null, target = DiscoverRoute()))
        assertEquals(NavPlan.PUSH, NavPlan.of(top = MainRoute, sameKindBelow = null, target = SearchRoute("Midterms")))
    }

    @Test
    fun `a double tap does not stack the same screen twice`() {
        assertEquals(NavPlan.IGNORE, NavPlan.of(top = DiscoverRoute(), sameKindBelow = DiscoverRoute(), target = DiscoverRoute()))
        assertEquals(NavPlan.IGNORE, NavPlan.of(top = SearchRoute(null), sameKindBelow = SearchRoute(null), target = SearchRoute(null)))
    }

    @Test
    fun `the same screen further down is returned to`() {
        // Discover → search → "Themen entdecken" again: back to the discover screen below.
        assertEquals(NavPlan.RETURN, NavPlan.of(top = SearchRoute(null), sameKindBelow = DiscoverRoute(), target = DiscoverRoute()))
        // From the reader opened in a search, back to that search.
        assertEquals(NavPlan.RETURN, NavPlan.of(top = ArticleRoute("https://x"), sameKindBelow = SearchRoute("EU"), target = SearchRoute("EU")))
    }

    @Test
    fun `another category or query replaces the screen of its kind`() {
        assertEquals(NavPlan.PUSH, NavPlan.of(top = DiscoverRoute(), sameKindBelow = DiscoverRoute(), target = DiscoverRoute("sport")))
        assertEquals(NavPlan.PUSH, NavPlan.of(top = DiscoverRoute(), sameKindBelow = SearchRoute("EU"), target = SearchRoute("Midterms")))
    }

    @Test
    fun `page names follow the interface language`() {
        assertEquals(listOf("Heute", "Einstellungen"), MainPage.specs(ShellStrings.of(AppLanguage.DE)).map { it.label })
        assertEquals(listOf("Today", "Settings"), MainPage.specs(ShellStrings.of(AppLanguage.EN)).map { it.label })
        AppLanguage.entries.forEach { lang ->
            MainPage.specs(ShellStrings.of(lang)).forEach { spec -> assert(spec.label.isNotBlank()) { "$lang: blank page name" } }
        }
    }

    @Test
    fun `the reader finds handed-over items by id or link`() {
        ArticleHandoff.clear()
        val item = NewsItem(id = "a1", headline = "Midterms: results", url = "https://news.google.com/rss/articles/X", source = "BBC", sourceId = "gn", topicId = "search")
        ArticleHandoff.put(item)
        assertEquals(item, ArticleHandoff.find("a1", "other"))
        assertEquals(item, ArticleHandoff.find(null, item.url))
        assertNull(ArticleHandoff.find("nope", "https://elsewhere"))
        // Only the most recent few are kept.
        repeat(60) { ArticleHandoff.put(item.copy(id = "n$it", url = "https://x/$it")) }
        assertNull(ArticleHandoff.find("a1", item.url))
        assertEquals("n59", ArticleHandoff.find("n59", "")?.id)
        ArticleHandoff.clear()
    }
}

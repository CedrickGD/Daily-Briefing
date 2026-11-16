package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedStringsTest {

    /** Every text of [strings], the ones with values filled in. */
    private fun texts(strings: FeedStrings): Map<String, String> = with(strings) {
        mapOf(
            "forYou" to forYou, "latest" to latest, "filterRow" to filterRow,
            "searchDescription" to searchDescription, "discoverDescription" to discoverDescription,
            "articleCount1" to articleCount(1), "articleCount5" to articleCount(5),
            "allArticles1" to allArticles(1, "X"), "allArticles5" to allArticles(5, "X"),
            "stand" to stand("07:12"),
            "subTopicsOf" to subTopicsOf("X"), "loadingNews" to loadingNews, "loadingArticles" to loadingArticles,
            "moreComing" to moreComing, "loadingOlder" to loadingOlder, "nothingOlderFound" to nothingOlderFound,
            "searchFurtherBack" to searchFurtherBack, "listEnd" to listEnd(3), "noArticlesYet" to noArticlesYet,
            "unreachable" to unreachable("A"), "nothingLoadedYet" to nothingLoadedYet, "nothingNewToday" to nothingNewToday,
            "nothingHereRightNow" to nothingHereRightNow, "backToTop" to backToTop, "lastTopicStays" to lastTopicStays,
            "unfollowed" to unfollowed("X"), "followed" to followed("X"), "undo" to undo, "sourceHidden" to sourceHidden("X"),
            "listenUnavailable" to listenUnavailable, "listenFailed" to listenFailed, "copyFailed" to copyFailed,
            "linkCopied" to linkCopied, "clipLabel" to clipLabel, "readMore" to readMore, "openArticle" to openArticle,
            "moreOptions" to moreOptions, "listen" to listen, "stop" to stop, "resume" to resume,
            "listenTo" to listenTo("X"), "resumeListening" to resumeListening("X"), "stopListening" to stopListening,
            "readArticle" to readArticle, "hideSource" to hideSource("X"), "followTopic" to followTopic("X"),
            "unfollowTopic" to unfollowTopic("X"),
            "paywall" to paywall, "paywallDescription" to paywallDescription, "browserOnly" to browserOnly,
            "browserOnlyDescription" to browserOnlyDescription,
            "noArticlesRightNow" to noArticlesRightNow, "moveUp" to moveUp("X"),
            "moveDown" to moveDown("X"), "ctaOverline" to ctaOverline, "ctaTitle" to ctaTitle, "ctaText" to ctaText,
            "suggestionsOverline" to suggestionsOverline, "suggestionsTitle" to suggestionsTitle, "allTopics" to allTopics,
            "allTopicsText" to allTopicsText, "noTopicsTitle" to noTopicsTitle, "noTopicsText" to noTopicsText,
            "offlineEmptyTitle" to offlineEmptyTitle, "offlineEmptyText" to offlineEmptyText,
            "offlineCachedTitle" to offlineCachedTitle, "offlineCachedText" to offlineCachedText,
            "noArticlesTitle" to noArticlesTitle, "sourcesSilent" to sourcesSilent, "sourcesSwitchedOff" to sourcesSwitchedOff,
            "checkSources" to checkSources, "sourcesFailing" to sourcesFailing(3), "weatherCredit" to weatherCredit,
            "weatherCreditOffline" to weatherCreditOffline, "weatherUnavailableTitle" to weatherUnavailableTitle,
            "weatherUnavailableText" to weatherUnavailableText,
            "verifying" to verifying, "verifyOffline" to verifyOffline, "noneComplete" to noneComplete,
            "noCompleteTitle" to noCompleteTitle, "noCompleteText" to noCompleteText,
            "verifyOfflineTitle" to verifyOfflineTitle, "verifyOfflineText" to verifyOfflineText,
            "manageTopics" to manageTopics, "searchPlaceholder" to searchPlaceholder, "showAll" to showAll,
            "continueListening" to continueListening, "yourTopicsTitle" to yourTopicsTitle,
            "yourTopicsHint" to yourTopicsHint, "addPlace" to addPlace, "dry" to dry,
            "weatherExpand" to weatherExpand, "weatherCollapse" to weatherCollapse,
            "weatherExpandedState" to weatherExpandedState, "weatherCollapsedState" to weatherCollapsedState,
        )
    }

    @Test
    fun `every language has every text`() {
        for (lang in AppLanguage.entries) {
            texts(FeedStrings.of(lang)).forEach { (name, text) -> assertTrue("$lang.$name is blank", text.isNotBlank()) }
        }
    }

    @Test
    fun `texts with values contain them`() {
        for (lang in AppLanguage.entries) {
            val s = FeedStrings.of(lang)
            assertTrue(s.allArticles(37, "Sport").contains("37") && s.allArticles(37, "Sport").contains("Sport"))
            assertTrue(s.unfollowed("Krypto").contains("Krypto"))
            assertTrue(s.sourcesFailing(4).contains("4"))
            assertTrue(s.listEnd(12).contains("12"))
            // The hint names the ✕ the rows show.
            assertTrue(s.yourTopicsHint.contains("✕"))
        }
    }

    @Test
    fun `counts use the plural of each language`() {
        assertEquals("1 Meldung", FeedStrings.of(AppLanguage.DE).articleCount(1))
        assertEquals("2 Meldungen", FeedStrings.of(AppLanguage.DE).articleCount(2))
        assertEquals("1 story", FeedStrings.of(AppLanguage.EN).articleCount(1))
        assertEquals("2 stories", FeedStrings.of(AppLanguage.EN).articleCount(2))
        assertEquals("2 articles", FeedStrings.of(AppLanguage.FR).articleCount(2))
        assertEquals("2 noticias", FeedStrings.of(AppLanguage.ES).articleCount(2))
        assertEquals("1 notizia", FeedStrings.of(AppLanguage.IT).articleCount(1))
        assertEquals("2 notizie", FeedStrings.of(AppLanguage.IT).articleCount(2))
    }

    @Test
    fun `languages are really translated`() {
        val german = texts(FeedStrings.of(AppLanguage.DE))
        for (lang in AppLanguage.entries - AppLanguage.DE) {
            val other = texts(FeedStrings.of(lang))
            val same = german.filter { (name, text) -> other[name] == text }.keys - SHARED
            assertEquals("$lang copies German texts: $same", emptySet<String>(), same)
            assertNotEquals(german["ctaTitle"], other["ctaTitle"])
        }
    }

    private companion object {
        /** Texts that may read the same in several languages. */
        val SHARED = setOf("clipLabel", "weatherCredit", "weatherCreditOffline", "stop", "offlineEmptyTitle", "offlineCachedTitle", "noTopicsTitle", "sourcesFailing")
    }
}

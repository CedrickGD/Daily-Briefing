package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.TopicStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedLogicTest {

    private fun item(
        id: String,
        url: String = "https://www.tagesschau.de/$id",
        domain: String = "",
        aggregator: String? = null,
        topic: String = "wirtschaft",
    ) = NewsItem(
        id = id, headline = "H$id", url = url, source = "S", sourceId = "s", topicId = topic,
        publisherDomain = domain, aggregator = aggregator,
    )

    @Test
    fun `readable only hides walled sites and walled articles, not the rest`() {
        val hides = ReadabilityHides.of(
            blocked = listOf("nytimes.com", "zeit.de"),
            statuses = mapOf("p" to ArticleStatus.PAYWALL, "b" to ArticleStatus.BLOCKED, "r" to ArticleStatus.READABLE, "f" to ArticleStatus.FAILED),
        )
        assertEquals(setOf("p", "b"), hides.itemIds)
        val items = listOf(
            item("ok"),
            item("nyt", url = "https://news.google.com/rss/articles/abc", domain = "nytimes.com", aggregator = "google"),
            item("zeit", url = "https://www.zeit.de/politik/x"),
            item("sub", url = "https://epaper.zeit.de/x"),
            item("p"),
            item("b"),
            item("r"),
            item("f"),
            // A Google News link names no publisher: kept.
            item("gn", url = "https://news.google.com/rss/articles/xyz", aggregator = "google"),
        )
        assertEquals(listOf("ok", "r", "f", "gn"), readableItems(items, hides, readableOnly = true).map { it.id })
        assertEquals(items, readableItems(items, hides, readableOnly = false))
        assertEquals(items, readableItems(items, ReadabilityHides.NONE, readableOnly = true))
    }

    @Test
    fun `publisher host prefers the source domain and ignores aggregator links`() {
        assertEquals("bbc.com", publisherHost(item("1", url = "https://news.google.com/x", domain = "www.BBC.com", aggregator = "google")))
        assertEquals("tagesschau.de", publisherHost(item("2")))
        assertEquals("", publisherHost(item("3", url = "https://news.google.com/x", aggregator = "google")))
    }

    @Test
    fun `card badges follow the prefetch status`() {
        // A complete article carries no mark: "Nur vollständige Artikel" promises that once.
        assertNull(cardBadge(ArticleStatus.READABLE))
        assertEquals(CardBadge.PAYWALL, cardBadge(ArticleStatus.PAYWALL))
        assertEquals(CardBadge.BROWSER_ONLY, cardBadge(ArticleStatus.BLOCKED))
        assertNull(cardBadge(ArticleStatus.UNKNOWN))
        assertNull(cardBadge(ArticleStatus.LOADING))
        assertNull(cardBadge(ArticleStatus.FAILED))
    }

    @Test
    fun `prefetch window is the visible articles plus a few ahead`() {
        val order = (1..10).map { "a$it" }
        assertEquals(listOf("a3", "a4", "a5", "a6", "a7", "a8"), prefetchWindow(order, listOf("a4", "a3"), ahead = 4))
        assertEquals(listOf("a9", "a10"), prefetchWindow(order, listOf("a9"), ahead = 4))
        assertEquals(emptyList<String>(), prefetchWindow(order, emptyList()))
        assertEquals(emptyList<String>(), prefetchWindow(order, listOf("elsewhere")))
        // The same story twice in the view counts once.
        assertEquals(listOf("x", "y", "z", "w"), prefetchWindow(listOf("x", "y", "x", "z", "w", "v"), listOf("y", "x"), ahead = 2))
    }

    @Test
    fun `entry keys carry the article id`() {
        val key = entryKey("foryou-s0-wirtschaft/boerse", 3, "id|with|bars")
        assertEquals("id|with|bars", entryItemId(key))
        assertNull(entryItemId("header"))
        assertNull(entryItemId(42))
        assertNull(entryItemId(null))
        assertTrue(entryKey("latest", 0, "a") != entryKey("latest", 1, "a"))
    }

    @Test
    fun `endless lists reveal first, then load older pages once`() {
        assertEquals(EndAction.REVEAL, endAction(revealed = 20, total = 50, canLoadMore = true, loadingMore = false, stalled = false))
        assertEquals(EndAction.LOAD_MORE, endAction(revealed = 50, total = 50, canLoadMore = true, loadingMore = false, stalled = false))
        assertEquals(EndAction.NONE, endAction(revealed = 50, total = 50, canLoadMore = true, loadingMore = true, stalled = false))
        assertEquals(EndAction.NONE, endAction(revealed = 50, total = 50, canLoadMore = true, loadingMore = false, stalled = true))
        assertEquals(EndAction.NONE, endAction(revealed = 50, total = 50, canLoadMore = false, loadingMore = false, stalled = false))
        assertEquals(EndAction.NONE, endAction(revealed = 0, total = 0, canLoadMore = false, loadingMore = false, stalled = false))
        // An empty list waits for its refresh.
        assertEquals(EndAction.NONE, endAction(revealed = 0, total = 0, canLoadMore = true, loadingMore = false, stalled = false))
    }

    @Test
    fun `list tail shows loading, retry or the end`() {
        assertEquals(ListTail.REVEALING, listTail(20, 50, canLoadMore = false, loadingMore = false, stalled = false))
        assertEquals(ListTail.LOADING_OLDER, listTail(50, 50, canLoadMore = true, loadingMore = true, stalled = false))
        assertEquals(ListTail.LOADING_OLDER, listTail(50, 50, canLoadMore = true, loadingMore = false, stalled = false))
        assertEquals(ListTail.STALLED, listTail(50, 50, canLoadMore = true, loadingMore = false, stalled = true))
        assertEquals(ListTail.END, listTail(50, 50, canLoadMore = false, loadingMore = false, stalled = false))
    }

    @Test
    fun `the next stories are revealed and older ones asked for well before the end`() {
        // A list of 200 items: revealing starts a screen and a half before its end …
        assertFalse(nearEnd(EndAction.REVEAL, lastVisible = 200 - REVEAL_AHEAD - 1, count = 200))
        assertTrue(nearEnd(EndAction.REVEAL, lastVisible = 200 - REVEAL_AHEAD, count = 200))
        // … and older articles are asked for several screens ahead: a fling does not reach the "loading" row.
        assertFalse(nearEnd(EndAction.LOAD_MORE, lastVisible = 200 - LOAD_AHEAD - 1, count = 200))
        assertTrue(nearEnd(EndAction.LOAD_MORE, lastVisible = 200 - LOAD_AHEAD, count = 200))
        assertTrue(LOAD_AHEAD > REVEAL_AHEAD && REVEAL_AHEAD >= 8)
        // With "nur vollständige Artikel" the list keeps VERIFY_AHEAD verified stories below the screen:
        // asking only closer to the end than that would mean asking when they are used up.
        assertTrue(LOAD_AHEAD > VERIFY_AHEAD)
        // The candidates run low while a screenful of checks is still to do: time to ask.
        assertEquals(EndAction.LOAD_MORE, endAction(50, 50, canLoadMore = true, loadingMore = false, stalled = false, waiting = VERIFY_LOW_WATER - 1, candidates = 120))
        assertEquals(EndAction.NONE, endAction(50, 50, canLoadMore = true, loadingMore = false, stalled = false, waiting = VERIFY_LOW_WATER, candidates = 120))
        // A short list is near its end from the start; nothing to do is never near.
        assertTrue(nearEnd(EndAction.LOAD_MORE, lastVisible = 3, count = 12))
        assertFalse(nearEnd(EndAction.NONE, lastVisible = 199, count = 200))
        assertFalse(nearEnd(EndAction.REVEAL, lastVisible = 0, count = 0))
    }

    @Test
    fun `a list stops asking by itself when page after page brings nothing it can show`() {
        val loads = BarrenLoads(limit = 100)
        // The first load of a list always goes out.
        assertTrue(loads.mayLoad("top", shown = 94, judged = 110))
        // The page is checked, nothing of it is complete; the next one may still be asked for …
        assertTrue(loads.mayLoad("top", shown = 94, judged = 190))
        // … but after a hundred candidates without one story to show the list stops: no endless "wird geprüft".
        assertFalse(loads.mayLoad("top", shown = 94, judged = 215))
        assertFalse(loads.mayLoad("top", shown = 94, judged = 300))
        // Another list counts for itself.
        assertTrue(loads.mayLoad(TIMELINE_LIST, shown = 150, judged = 300))
        // The user asks from "Weiter zurück suchen": that always loads, and the count starts afresh.
        assertTrue(loads.mayLoad("top", shown = 94, judged = 300, byUser = true))
        assertTrue(loads.mayLoad("top", shown = 94, judged = 380))
        assertFalse(loads.mayLoad("top", shown = 94, judged = 400))
        // A list that grows never stops: every story shown starts the count afresh.
        val growing = BarrenLoads(limit = 100)
        var shown = 20
        var judged = 20
        repeat(10) {
            assertTrue(growing.mayLoad("politik", shown, judged))
            shown += 1
            judged += 99
        }
        // Without "nur vollständige Artikel" every article loaded is shown.
        val plain = BarrenLoads()
        assertTrue((1..20).all { plain.mayLoad("welt", shown = it * 100, judged = it * 100) })
        // A refresh built the list anew (fewer judged than before), a pull-to-refresh forgets everything.
        assertTrue(loads.mayLoad("top", shown = 60, judged = 70))
        loads.reset()
        assertTrue(loads.mayLoad("top", shown = 60, judged = 500))
    }

    @Test
    fun `the checking row never stays - a list ends in loading, a retry or its end`() {
        // Candidates are being verified: the row says so.
        assertEquals(ListTail.VERIFYING, listTail(94, 94, canLoadMore = true, loadingMore = false, stalled = false, checking = true))
        assertEquals(ListTail.VERIFY_OFFLINE, listTail(94, 94, canLoadMore = true, loadingMore = false, stalled = false, checking = true, offline = true))
        // All of them judged and none complete, the list stopped asking (BarrenLoads): it offers to search on.
        assertEquals(ListTail.STALLED, listTail(94, 94, canLoadMore = true, loadingMore = false, stalled = true, checking = false))
        assertEquals(EndAction.NONE, endAction(94, 94, canLoadMore = true, loadingMore = false, stalled = true, waiting = 0, candidates = 400))
        // Paging is exhausted (the end of the topic's history) and nothing is left to check: the calm end.
        assertEquals(ListTail.END, listTail(94, 94, canLoadMore = false, loadingMore = false, stalled = false, checking = false))
        assertEquals(ListTail.END, listTail(94, 94, canLoadMore = false, loadingMore = false, stalled = true, checking = false))
        assertEquals(EndAction.NONE, endAction(94, 94, canLoadMore = false, loadingMore = false, stalled = false, waiting = 0, candidates = 400))
    }

    @Test
    fun `only a publisher's own feed can be hidden from the menu`() {
        assertTrue(canHideSource(item("1")))
        assertFalse(canHideSource(item("2", aggregator = "google")))
        assertFalse(canHideSource(item("3").copy(sourceId = "gn:wirtschaft@DE:de")))
        assertFalse(canHideSource(item("4").copy(sourceId = "")))
    }

    @Test
    fun `the last followed topic cannot be unfollowed`() {
        assertTrue(canUnfollow(listOf("a", "b"), "a"))
        assertFalse(canUnfollow(listOf("a"), "a"))
        assertTrue(canUnfollow(listOf("a"), "not-followed"))
    }

    @Test
    fun `the menu offers the sub-topic of a category article`() {
        val wirtschaft = Topics.builtIn("wirtschaft", AppLanguage.DE)!!
        val article = FeedArticle(item("1", topic = "wirtschaft/boerse"), wirtschaft)
        val offered = followSuggestion(article, followedIds = listOf("wirtschaft"), lang = AppLanguage.DE)
        assertEquals("wirtschaft/boerse", offered?.id)
        assertEquals("Börse & Märkte", offered?.title)
        assertEquals(TopicStrings.of(AppLanguage.EN).title("wirtschaft/boerse"), followSuggestion(article, listOf("wirtschaft"), AppLanguage.EN)?.title)
        // Already followed, the section's own topic, another category's sub-topic, unknown ids: nothing.
        assertNull(followSuggestion(article, listOf("wirtschaft", "wirtschaft/boerse"), AppLanguage.DE))
        assertNull(followSuggestion(FeedArticle(item("2", topic = "wirtschaft"), wirtschaft), listOf("wirtschaft"), AppLanguage.DE))
        assertNull(followSuggestion(FeedArticle(item("3", topic = "sport/fussball"), wirtschaft), listOf("wirtschaft"), AppLanguage.DE))
        assertNull(followSuggestion(FeedArticle(item("4", topic = "custom:x"), wirtschaft), listOf("wirtschaft"), AppLanguage.DE))
    }

    @Test
    fun `suggestions skip followed categories and those followed through a sub-topic`() {
        val categories = Topics.categories(AppLanguage.DE)
        val suggested = discoverSuggestions(categories, followedIds = listOf("top", "sport/fussball", "local:x"), max = 50).map { it.id }
        assertFalse("top" in suggested)
        assertFalse("sport" in suggested)
        assertTrue("wirtschaft" in suggested)
        assertEquals(categories.map { it.id }.filter { it != "top" && it != "sport" }, suggested)
        assertEquals(3, discoverSuggestions(categories, emptyList(), max = 3).size)
    }

    @Test
    fun `a thin feed invites to discover`() {
        assertTrue(invitesToDiscover(0))
        assertTrue(invitesToDiscover(FEW_TOPICS - 1))
        assertFalse(invitesToDiscover(FEW_TOPICS))
    }

    @Test
    fun `own topics never get a follow offer`() {
        val custom = Topic("custom:1", "Midterms", "🔎", 0xFF2F6FDE, custom = true, icon = "SEARCH", query = "Midterms")
        assertNull(followSuggestion(FeedArticle(item("5", topic = "custom:1"), custom), listOf("custom:1"), AppLanguage.DE))
    }

    @Test
    fun `a teaser that only repeats the headline is not shown`() {
        val headline = "Nach der herben Niederlage der Republikaner bei den Midterms"
        // tagesschau's feed (seen on the phone): the description is the title again.
        assertEquals("", cardTeaser(headline, headline))
        assertEquals("", cardTeaser(headline, "  nach der herben Niederlage der Republikaner bei den Midterms… "))
        // Cut off, or only the publisher added: still no teaser.
        assertEquals("", cardTeaser(headline, "Nach der herben Niederlage der"))
        assertEquals("", cardTeaser(headline, "$headline – tagesschau.de"))
    }

    @Test
    fun `a teaser that starts with the headline keeps only what follows`() {
        val headline = "Midterms: Demokraten holen das Repräsentantenhaus"
        assertEquals(
            "Die Partei von Präsident Trump verliert die Mehrheit in der Kammer.",
            cardTeaser(headline, "Midterms – Demokraten holen das Repräsentantenhaus. Die Partei von Präsident Trump verliert die Mehrheit in der Kammer."),
        )
        assertEquals(
            "„Ein klares Signal“, sagt die Fraktionschefin am Abend in Washington.",
            cardTeaser(headline, "$headline: „Ein klares Signal“, sagt die Fraktionschefin am Abend in Washington."),
        )
    }

    @Test
    fun `a real teaser stays as it is`() {
        val teaser = "Die Republikaner verlieren die Mehrheit im Repräsentantenhaus. Was das für Trump bedeutet."
        assertEquals(teaser, cardTeaser("Midterms: Was die Niederlage für Trump bedeutet", "  $teaser "))
        assertEquals("", cardTeaser("Headline", "   "))
        assertEquals("Nur ein Teaser", cardTeaser("", "Nur ein Teaser"))
        // The headline ends inside a word of the teaser: no clean cut, the teaser stays whole.
        assertEquals("Midterms-Wahl entschieden: die Demokraten jubeln", cardTeaser("Midterms", "Midterms-Wahl entschieden: die Demokraten jubeln"))
    }
}

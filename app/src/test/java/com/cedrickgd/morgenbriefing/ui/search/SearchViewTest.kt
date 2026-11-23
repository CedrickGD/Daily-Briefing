package com.cedrickgd.morgenbriefing.ui.search

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What of the results shows: "only readable articles", the order, the language tags. */
class SearchViewTest {

    private fun item(id: String, at: String? = null, edition: String = "DE:de", domain: String = "example.de") = NewsItem(
        id = id, headline = "Story $id", url = "https://news.example/$id", source = "Example", sourceId = "gn:search@$edition",
        topicId = Topics.SEARCH_ID, publishedAt = at, publisherDomain = domain,
    )

    private fun ready(vararg items: NewsItem, hasMore: Boolean = true) =
        SearchResults(query = "SLV ETF", items = items.toList(), nextPage = 1, hasMore = hasMore, phase = SearchPhase.READY)

    private val readableOnly = AppSettings(edition = NewsEdition.GERMANY, readableOnly = true)

    @Test
    fun `only verified complete articles show, the unchecked ones are verified in list order`() {
        val items = (1..30).map { item("i$it") }
        val statuses = mapOf(
            "i1" to ArticleStatus.READABLE,
            "i2" to ArticleStatus.PAYWALL,
            "i3" to ArticleStatus.LOADING,
            "i4" to ArticleStatus.BLOCKED,
            "i5" to ArticleStatus.READABLE,
            "i6" to ArticleStatus.FAILED,
        )
        val view = SearchView.readable(items, statuses, readableOnly = true)
        assertEquals(listOf("i1", "i5"), view.shown.map { it.id })
        // i3 is loading, i7…i30 unchecked: 25 wait, the first 24 of them are asked for, in order.
        assertEquals(25, view.checking)
        assertEquals(SearchView.VERIFY_WINDOW, view.candidates.size)
        assertEquals(listOf("i3", "i7", "i8"), view.candidates.take(3).map { it.id })
        assertEquals(3, view.unreadable)
        // Off: everything shows, nothing to verify.
        val all = SearchView.readable(items, statuses, readableOnly = false)
        assertEquals(30, all.shown.size)
        assertEquals(0, all.checking)
        assertTrue(all.candidates.isEmpty())
    }

    @Test
    fun `the screen state hides unverified results and says what is going on`() {
        val results = ready(item("a"), item("b"), item("c"))
        val checking = buildSearchUi(readableOnly, AppLanguage.DE, results, emptyMap(), null, false, mapOf("b" to ArticleStatus.READABLE))
        assertEquals(listOf("b"), checking.articles.map { it.item.id })
        assertEquals(2, checking.checking)
        assertEquals(listOf("a", "c"), checking.candidates.map { it.id })
        assertNull(SearchView.empty(checking))

        val none = buildSearchUi(readableOnly, AppLanguage.DE, results, emptyMap(), null, false, emptyMap())
        assertEquals(SearchView.Empty.CHECKING, SearchView.empty(none))

        val walled = mapOf("a" to ArticleStatus.PAYWALL, "b" to ArticleStatus.BLOCKED, "c" to ArticleStatus.PAYWALL)
        // More pages may still bring readable ones: still "checking"; at the end: nothing readable.
        assertEquals(SearchView.Empty.CHECKING, SearchView.empty(buildSearchUi(readableOnly, AppLanguage.DE, results, emptyMap(), null, false, walled)))
        val end = ready(item("a"), item("b"), item("c"), hasMore = false)
        assertEquals(SearchView.Empty.NONE_READABLE, SearchView.empty(buildSearchUi(readableOnly, AppLanguage.DE, end, emptyMap(), null, false, walled)))
        val nothing = buildSearchUi(readableOnly, AppLanguage.DE, ready(hasMore = false), emptyMap(), null, false, emptyMap())
        assertEquals(SearchView.Empty.NOTHING, SearchView.empty(nothing))
    }

    @Test
    fun `no more pages while many results still wait for their check`() {
        val many = ready(*(1..20).map { item("i$it") }.toTypedArray())
        val waiting = buildSearchUi(readableOnly, AppLanguage.DE, many, emptyMap(), null, false, emptyMap())
        assertFalse(SearchView.shouldLoadMore(waiting, nearEnd = true))
        val verified = buildSearchUi(readableOnly, AppLanguage.DE, many, emptyMap(), null, false, (1..20).associate { "i$it" to ArticleStatus.READABLE })
        assertTrue(SearchView.shouldLoadMore(verified, nearEnd = true))
        assertFalse(SearchView.shouldLoadMore(verified, nearEnd = false))
    }

    @Test
    fun `newest first is a choice, relevance the default`() {
        val results = ready(item("old", "2026-10-01T10:00:00Z"), item("undated"), item("new", "2026-11-05T10:00:00Z"))
        val open = readableOnly.copy(readableOnly = false)
        assertEquals(listOf("old", "undated", "new"), buildSearchUi(open, AppLanguage.DE, results, emptyMap(), null, false, emptyMap()).articles.map { it.item.id })
        val newest = buildSearchUi(open, AppLanguage.DE, results, emptyMap(), null, false, emptyMap(), SearchOptions(sort = SearchSort.NEWEST))
        assertEquals(listOf("new", "old", "undated"), newest.articles.map { it.item.id })
        assertEquals(SearchSort.NEWEST, newest.options.sort)
    }

    @Test
    fun `results in another language than the interface carry a tag`() {
        val english = item("en", edition = "US:en", domain = "benzinga.com")
        val germanInUs = item("de-in-us", edition = "US:en", domain = "zeit.de")
        val german = item("de", edition = "DE:de", domain = "boerse-online.de")
        assertEquals("EN", SearchView.languageTag(english, AppLanguage.DE))
        assertNull("a German site in the US edition is German", SearchView.languageTag(germanInUs, AppLanguage.DE))
        assertNull(SearchView.languageTag(german, AppLanguage.DE))
        assertEquals("DE", SearchView.languageTag(german, AppLanguage.EN))
        assertNull(SearchView.languageTag(english, AppLanguage.EN))
        val ui = buildSearchUi(readableOnly.copy(readableOnly = false), AppLanguage.DE, ready(english, german), emptyMap(), null, false, emptyMap())
        assertEquals(mapOf("en" to "EN"), ui.languageTags)
        // A German edition can add the English ones; an English one has nothing to add.
        assertTrue(ui.offerLanguages)
        assertEquals("de", ui.editionLanguage)
        assertFalse(buildSearchUi(AppSettings(edition = NewsEdition.USA), AppLanguage.EN, ready(), emptyMap(), null, false, emptyMap()).offerLanguages)
    }

    @Test
    fun `the results go to the top once per query and order`() {
        val listing = SearchView.listingKey("SLV ETF", SearchSort.RELEVANCE.name)
        // A new query (or none before): to the top.
        assertTrue(SearchView.startsAtTop(listing, null))
        assertTrue(SearchView.startsAtTop(listing, SearchView.listingKey("Gold", SearchSort.RELEVANCE.name)))
        // Another order of the same query: to the top.
        assertTrue(SearchView.startsAtTop(SearchView.listingKey("SLV ETF", SearchSort.NEWEST.name), listing))
        // Back from an article: the same listing, the list stays where it was.
        assertFalse(SearchView.startsAtTop(listing, listing))
    }
}

package com.cedrickgd.morgenbriefing.ui.search

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SearchPage
import com.cedrickgd.morgenbriefing.data.model.Topics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchPagingTest {

    private fun item(id: String, headline: String = "Story $id") = NewsItem(
        id = id, headline = headline, url = "https://news.example/$id", source = "Example", sourceId = "gn", topicId = Topics.SEARCH_ID,
    )

    private fun page(query: String, number: Int, vararg ids: String, hasMore: Boolean = true, failed: Boolean = false) =
        SearchPage(query = query, page = number, items = ids.map { item(it) }, hasMore = hasMore, failed = failed)

    @Test
    fun `queries are normalized like the repository does and need two letters`() {
        assertEquals("us midterms", SearchPaging.normalize("  us   midterms "))
        assertFalse(SearchPaging.isSearchable(" a "))
        assertTrue(SearchPaging.isSearchable("EU"))
        val started = SearchPaging.start("  Midterms ")
        assertEquals("Midterms", started.query)
        assertEquals(SearchPhase.LOADING, started.phase)
        assertFalse(SearchPaging.canLoadMore(started))
    }

    @Test
    fun `the first page fills the results and enables paging`() {
        val ready = SearchPaging.onPage(SearchPaging.start("Midterms"), page("Midterms", 0, "a", "b"))
        assertEquals(SearchPhase.READY, ready.phase)
        assertEquals(listOf("a", "b"), ready.items.map { it.id })
        assertEquals(1, ready.nextPage)
        assertTrue(SearchPaging.canLoadMore(ready))
        assertFalse(ready.offline)
        assertFalse(ready.isEnd)
    }

    @Test
    fun `pages of an older query or an unexpected page are ignored`() {
        val loading = SearchPaging.start("Midterms")
        assertSame(loading, SearchPaging.onPage(loading, page("Midter", 0, "x")))
        assertSame(loading, SearchPaging.onPage(loading, page("Midterms", 2, "x")))
        val ready = SearchPaging.onPage(loading, page("Midterms", 0, "a"))
        // A late duplicate of page 0 changes nothing.
        assertSame(ready, SearchPaging.onPage(ready, page("Midterms", 0, "z")))
        // Page 1 without a request for it changes nothing either.
        assertSame(ready, SearchPaging.onPage(ready, page("Midterms", 1, "z")))
    }

    @Test
    fun `more pages append only stories not shown yet`() {
        val ready = SearchPaging.onPage(SearchPaging.start("Midterms"), page("Midterms", 0, "a", "b"))
        val asking = SearchPaging.beginMore(ready)!!
        assertTrue(asking.loadingMore)
        assertNull("no second request while one runs", SearchPaging.beginMore(asking))
        val more = SearchPage("Midterms", 1, listOf(item("b"), item("c"), item("d", headline = "Story a!")), hasMore = true)
        val after = SearchPaging.onPage(asking, more)
        // "b" again by id, "d" again by its headline ("Story a" ≈ "Story a!").
        assertEquals(listOf("a", "b", "c"), after.items.map { it.id })
        assertEquals(2, after.nextPage)
        assertFalse(after.loadingMore)
        assertEquals(0, after.emptyStreak)
        assertFalse(SearchPaging.wantsMore(after))
    }

    @Test
    fun `quiet weeks are skipped and paging stops after a few in a row`() {
        var state = SearchPaging.onPage(SearchPaging.start("Midterms"), page("Midterms", 0, "a"))
        repeat(SearchPaging.MAX_EMPTY_STREAK - 1) { index ->
            state = SearchPaging.onPage(SearchPaging.beginMore(state)!!, page("Midterms", index + 1, "a"))
            assertTrue("an empty week asks for the next one at once", SearchPaging.wantsMore(state))
        }
        state = SearchPaging.onPage(SearchPaging.beginMore(state)!!, page("Midterms", SearchPaging.MAX_EMPTY_STREAK, "a"))
        assertFalse(state.hasMore)
        assertTrue(state.isEnd)
        assertFalse(SearchPaging.wantsMore(state))
        assertNull(SearchPaging.beginMore(state))
    }

    @Test
    fun `the end comes when the repository has no more`() {
        val ready = SearchPaging.onPage(SearchPaging.start("EU"), page("EU", 0, "a", hasMore = false))
        assertTrue(ready.isEnd)
        assertNull(SearchPaging.beginMore(ready))
    }

    @Test
    fun `an unreachable first page fails, unless cached stories match`() {
        val failed = SearchPaging.onPage(SearchPaging.start("EU"), page("EU", 0, failed = true, hasMore = false))
        assertEquals(SearchPhase.FAILED, failed.phase)
        assertFalse(SearchPaging.canLoadMore(failed))
        val offline = SearchPaging.onPage(SearchPaging.start("EU"), page("EU", 0, "cached", failed = true, hasMore = false))
        assertEquals(SearchPhase.READY, offline.phase)
        assertTrue(offline.offline)
        assertEquals(listOf("cached"), offline.items.map { it.id })
    }

    @Test
    fun `a failed later page keeps the results and can be retried`() {
        val ready = SearchPaging.onPage(SearchPaging.start("EU"), page("EU", 0, "a"))
        val failed = SearchPaging.onPage(SearchPaging.beginMore(ready)!!, page("EU", 1, failed = true, hasMore = false))
        assertTrue(failed.moreFailed)
        assertFalse(failed.loadingMore)
        assertEquals(listOf("a"), failed.items.map { it.id })
        assertTrue(failed.hasMore)
        val retry = SearchPaging.beginMore(failed)
        assertNotNull(retry)
        assertFalse(retry!!.moreFailed)
    }

    @Test
    fun `unexpected errors end the first page or the request for more`() {
        val loading = SearchPaging.start("EU")
        assertEquals(SearchPhase.FAILED, SearchPaging.onError(loading, " EU ", 0).phase)
        assertSame(loading, SearchPaging.onError(loading, "other", 0))
        val ready = SearchPaging.onPage(loading, page("EU", 0, "a"))
        val asking = SearchPaging.beginMore(ready)!!
        val afterError = SearchPaging.onError(asking, "EU", 1)
        assertTrue(afterError.moreFailed)
        assertFalse(afterError.loadingMore)
        assertSame(asking, SearchPaging.onError(asking, "EU", 5))
    }

    @Test
    fun `the first page loses repeats too`() {
        val page = SearchPage("EU", 0, listOf(item("a"), item("a"), item("b", headline = "Story A")), hasMore = false)
        assertEquals(listOf("a"), SearchPaging.onPage(SearchPaging.start("EU"), page).items.map { it.id })
    }
}

package com.cedrickgd.morgenbriefing.ui.today

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchSessionTest {

    private fun item(id: String) = NewsItem(id, "H$id", url = "https://x/$id", source = "S", sourceId = "s", topicId = "ki")

    private val a = item("a")
    private val b = item("b")
    private val c = item("c")

    @Test
    fun `leaving cancels everything requested, coming back warms the last window again`() {
        val session = PrefetchSession()
        session.request(listOf(a, b))
        session.request(listOf(b, c))
        assertEquals(listOf("a", "b", "c"), session.stop())
        // Screen off and on again: the list on screen does not ask again by itself.
        assertEquals(listOf(b, c), session.resume())
        // The renewed window counts as requested: the next pause cancels it.
        assertEquals(listOf("b", "c"), session.stop())
    }

    @Test
    fun `nothing is sent again when nothing was cancelled`() {
        val session = PrefetchSession()
        assertTrue(session.resume().isEmpty())
        session.request(listOf(a))
        // A resume without a pause before it (the first one) sends nothing twice.
        assertTrue(session.resume().isEmpty())
        assertTrue(PrefetchSession().stop().isEmpty())
    }

    @Test
    fun `a window the list sent after coming back is not sent twice`() {
        val session = PrefetchSession()
        session.request(listOf(a))
        session.stop()
        session.request(listOf(c))
        assertTrue(session.resume().isEmpty())
        assertEquals(listOf("c"), session.stop())
    }

    @Test
    fun `only the newest requests are tracked`() {
        val session = PrefetchSession(maxTracked = 2)
        session.request(listOf(a, b))
        session.request(listOf(a))
        session.request(listOf(c))
        assertEquals(listOf("a", "c"), session.stop())
    }
}

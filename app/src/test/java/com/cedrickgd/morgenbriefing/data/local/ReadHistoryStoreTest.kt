package com.cedrickgd.morgenbriefing.data.local

import com.cedrickgd.morgenbriefing.data.AppJson
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class ReadHistoryStoreTest {

    private lateinit var dir: File
    private var clock = 1_800_000_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("history").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val file get() = File(dir, "read-history.json")

    private fun TestScope.store(max: Int = ReadHistoryStore.MAX_ENTRIES, resolved: (String) -> String? = { null }) =
        ReadHistoryStore(file, AppJson, backgroundScope, clock = { clock }, maxEntries = max, resolved = resolved)

    private fun item(
        id: String,
        headline: String = "Bundestag beschließt neues Gesetz zur Rente $id",
        url: String = "https://www.tagesschau.de/inland/$id.html",
        domain: String = "tagesschau.de",
        source: String = "tagesschau",
        aggregator: String? = null,
        publishedAt: String = "2027-01-15T08:00:00+01:00",
    ) = NewsItem(
        id = id, headline = headline, url = url, source = source, sourceId = "src", topicId = "politik",
        publishedAt = publishedAt, publisherDomain = domain, aggregator = aggregator, image = ArticleImage("https://img.example/$id.jpg"),
    )

    @Test
    fun `a read story is kept across restarts with what the archive shows`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.markRead(item("a"))
        s.save()
        assertTrue(file.isFile)
        val again = store()
        again.warm()
        val entry = again.state.value.entries.single()
        assertEquals("a", entry.key)
        assertEquals("Bundestag beschließt neues Gesetz zur Rente a", entry.title)
        assertEquals("https://www.tagesschau.de/inland/a.html", entry.url)
        assertEquals("tagesschau", entry.source)
        assertEquals("https://img.example/a.jpg", entry.image)
        assertEquals("politik", entry.topicId)
        assertEquals(clock, entry.readAt)
        assertFalse(entry.hidden)
        assertTrue(again.state.value.isRead(item("a")))
    }

    @Test
    fun `state publishes every change at once, newest first`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.markRead(item("a"))
        clock += 1_000
        s.hide(item("b"))
        clock += 1_000
        s.markRead(item("c"))
        assertEquals(listOf("c", "b", "a"), s.state.value.entries.map { it.key })
        assertEquals(2, s.state.value.readCount)
        assertEquals(1, s.state.value.hiddenCount)
        // Read again: to the top.
        clock += 1_000
        s.markRead(item("a"))
        assertEquals(listOf("a", "c", "b"), s.state.value.entries.map { it.key })
    }

    @Test
    fun `entries untouched for 60 days are pruned, and the store is bounded`() = runTest(StandardTestDispatcher()) {
        val s = store(max = 3)
        s.markRead(item("old"))
        clock += Duration.ofDays(59).toMillis()
        s.markRead(item("b"))
        assertEquals(setOf("old", "b"), s.state.value.entries.map { it.key }.toSet())
        clock += Duration.ofDays(2).toMillis()
        s.markRead(item("c"))
        assertEquals("61 days old", setOf("b", "c"), s.state.value.entries.map { it.key }.toSet())
        listOf("d", "e").forEach {
            clock += 1_000
            s.markRead(item(it))
        }
        assertEquals("the oldest go first", listOf("e", "d", "c"), s.state.value.entries.map { it.key })
        s.save()
        // Pruned when read, too.
        clock += Duration.ofDays(61).toMillis()
        val again = store(max = 3)
        again.warm()
        assertTrue(again.state.value.isEmpty)
    }

    @Test
    fun `the same story from another feed counts as read - one entry per story`() = runTest(StandardTestDispatcher()) {
        val s = store(resolved = { url -> if (url == "https://news.google.com/rss/articles/CBMiXYZ") "https://www.tagesschau.de/inland/a.html?utm_source=x" else null })
        s.markRead(item("a"))
        val history = s.state.value
        // Google News with the publisher's link behind it (another id, another link).
        val viaGoogle = item("g", url = "https://news.google.com/rss/articles/CBMiXYZ", headline = "Ganz anders formuliert", aggregator = "google")
        assertTrue(history.isRead(viaGoogle))
        // Another feed of the publisher, the same headline (case and punctuation aside).
        val sameHeadline = item("h", url = "https://www.tagesschau.de/x/other.html", headline = "BUNDESTAG beschließt neues Gesetz zur Rente: a!")
        assertTrue(history.isRead(sameHeadline))
        // Google News' wording of the publisher's own story: a twin (same site, nearly the same headline, same time).
        val twin = item(
            "t",
            url = "https://news.google.com/rss/articles/OTHER",
            headline = "Bundestag beschließt neues Gesetz zur Rente a - tagesschau.de wichtig",
            aggregator = "google",
        )
        assertTrue(history.isRead(twin))
        // Another story is not.
        assertFalse(history.isRead(item("z", headline = "Ein ganz anderes Thema heute im Land")))
        // Marking a twin read updates the story's entry instead of adding one.
        clock += 1_000
        s.markRead(sameHeadline)
        assertEquals(listOf("a"), s.state.value.entries.map { it.key })
        assertEquals(clock, s.state.value.entries.single().readAt)
    }

    @Test
    fun `hide and unhide - the undo leaves things as they were`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.hide(item("a"))
        assertTrue(s.state.value.isHidden(item("a")))
        assertFalse(s.state.value.isRead(item("a")))
        s.unhide(item("a"))
        assertTrue("hidden only: gone again", s.state.value.isEmpty)

        s.markRead(item("b"))
        s.hide(item("b"))
        s.unhide(item("b"))
        val entry = s.state.value.entries.single()
        assertTrue(entry.read)
        assertFalse(entry.hidden)
    }

    @Test
    fun `mark unread drops a story that is not hidden, keeps a hidden one`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.markRead(item("a"))
        s.markRead(item("b"))
        s.hide(item("b"))
        s.markUnread(item("a"))
        s.markUnread(item("b"))
        val entry = s.state.value.entries.single()
        assertEquals("b", entry.key)
        assertNull(entry.readAt)
        assertTrue(entry.hidden)
        assertFalse(s.state.value.isRead(item("a")))
    }

    @Test
    fun `archive rows change by key and restore puts them back as they were`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.markRead(item("a"))
        s.hide(item("a"))
        val before = s.state.value.entries.single()
        s.setHidden("a", false)
        assertFalse(s.state.value.entries.single().hidden)
        s.setRead("a", false)
        assertTrue(s.state.value.isEmpty)
        s.restore(before)
        assertEquals(before, s.state.value.entries.single())
    }

    @Test
    fun `clear forgets everything, hidden stories show again`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.markRead(item("a"))
        s.hide(item("b"))
        s.save()
        s.clear()
        assertTrue(s.state.value.isEmpty)
        assertFalse(s.state.value.isHidden(item("b")))
        s.save()
        val again = store()
        again.warm()
        assertTrue(again.state.value.isEmpty)
    }

    @Test
    fun `an unreadable file starts over`() = runTest(StandardTestDispatcher()) {
        file.writeText("{ not json")
        val s = store()
        s.warm()
        assertTrue(s.state.value.isEmpty)
        s.markRead(item("a"))
        assertEquals(1, s.state.value.entries.size)
    }

    @Test
    fun `an index is equal to another with the same stories`() {
        val a = HistoryEntry.of(item("a")).copy(readAt = 1)
        val b = HistoryEntry.of(item("b")).copy(readAt = 2)
        assertEquals(StoryIndex(listOf(a, b)), StoryIndex(listOf(b, a.copy(readAt = 5))))
        assertFalse(StoryIndex(listOf(a)) == StoryIndex(listOf(a, b)))
        assertTrue(StoryIndex.EMPTY.isEmpty)
        assertNull(StoryIndex.EMPTY.find(item("a")))
    }
}

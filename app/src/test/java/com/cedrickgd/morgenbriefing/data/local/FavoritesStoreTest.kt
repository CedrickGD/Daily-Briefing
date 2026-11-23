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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesStoreTest {

    private lateinit var dir: File
    private var clock = 1_800_000_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("favorites").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val file get() = File(dir, "favorites.json")

    private fun TestScope.store() = FavoritesStore(file, AppJson, backgroundScope, clock = { clock })

    private fun item(
        id: String,
        headline: String = "Bundestag beschließt neues Gesetz zur Rente $id",
        url: String = "https://www.tagesschau.de/inland/$id.html",
    ) = NewsItem(
        id = id, headline = headline, url = url, source = "tagesschau", sourceId = "src", topicId = "politik",
        publishedAt = "2027-01-15T08:00:00+01:00", publisherDomain = "tagesschau.de", image = ArticleImage("https://img.example/$id.jpg"),
    )

    @Test
    fun `a starred story is kept across restarts with what the list shows`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.star(item("a"))
        s.save()
        assertTrue(file.isFile)
        val again = store()
        again.warm()
        val entry = again.state.value.entries.single()
        assertEquals("a", entry.key)
        assertEquals("Bundestag beschließt neues Gesetz zur Rente a", entry.title)
        assertEquals("https://img.example/a.jpg", entry.image)
        assertEquals(clock, entry.starredAt)
        assertFalse(entry.read)
        assertTrue(again.state.value.isStarred(item("a")))
    }

    @Test
    fun `newest star first, one entry per story even under another link`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.star(item("a"))
        clock += 1_000
        s.star(item("b"))
        clock += 1_000
        // The same story again, from another feed with another id: it keeps its first star.
        s.star(item("a2", headline = "Bundestag beschließt neues Gesetz zur Rente a", url = "https://www.tagesschau.de/inland/a.html"))
        assertEquals(listOf("b", "a"), s.state.value.entries.map { it.key })
        assertTrue(s.state.value.isStarred(item("a2", headline = "Bundestag beschließt neues Gesetz zur Rente a")))
    }

    @Test
    fun `unstarring takes every entry of the story, restore brings it back as it was`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.star(item("a"))
        val entry = s.state.value.entries.single()
        s.setStarred(item("a"), false)
        assertTrue(s.state.value.isEmpty)
        s.restore(entry)
        assertEquals(listOf(entry), s.state.value.entries)
        s.remove("a")
        assertFalse(s.state.value.isStarred(item("a")))
    }

    @Test
    fun `favorites are never pruned – more than the history keeps, and years old`() = runTest(StandardTestDispatcher()) {
        val s = store()
        repeat(1_050) { i ->
            s.star(item("n$i", headline = "Meldung Nummer $i über etwas ganz anderes"))
            clock += 1_000
        }
        // Two years later, still all there – also after a restart.
        clock += 2L * 365 * 24 * 60 * 60 * 1000
        s.star(item("late", headline = "Die letzte Meldung des Tages"))
        s.save()
        val again = store()
        again.warm()
        assertEquals(1_051, again.state.value.size)
        assertEquals("late", again.state.value.entries.first().key)
    }

    @Test
    fun `a broken file starts empty, a story without a link is not starred`() = runTest(StandardTestDispatcher()) {
        file.writeText("{ not json")
        val s = store()
        s.warm()
        assertTrue(s.state.value.isEmpty)
        s.star(item("x", url = ""))
        assertTrue(s.state.value.isEmpty)
    }
}

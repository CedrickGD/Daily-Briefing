package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.AppJson
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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

class ReadabilityStoreTest {

    private lateinit var dir: File
    private var clock = 1_800_000_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("readability").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun store(seed: Set<String> = linkedSetOf("youtube.com", "nytimes.com", "faz.net")) =
        ReadabilityStore(File(dir, "readability.json"), AppJson, seed) { clock }

    @Test
    fun `seeded walls are blocked, subdomains included, other sites are not`() {
        val s = store()
        assertTrue(s.isBlocked("www.nytimes.com"))
        assertTrue(s.isBlocked("cooking.nytimes.com"))
        assertFalse(s.isBlocked("tagesschau.de"))
        assertEquals(ReadOutcome.BLOCKED, s.blockReason("faz.net"))
        assertNull(s.blockReason("bbc.co.uk"))
    }

    @Test
    fun `two walls in a row teach a new blocked site, a success in between does not`() = runBlocking {
        val s = store()
        s.record("www.example-paper.com", ReadOutcome.PAYWALL)
        assertFalse(s.isBlocked("example-paper.com"))
        s.record("edition.example-paper.com", ReadOutcome.PAYWALL)
        assertTrue(s.isBlocked("www.example-paper.com"))
        assertEquals(ReadOutcome.PAYWALL, s.blockReason("example-paper.com"))
        assertEquals("learned walls come first", "example-paper.com", s.blockedDomains().first())

        s.record("other.de", ReadOutcome.BLOCKED)
        s.record("other.de", ReadOutcome.OK)
        s.record("other.de", ReadOutcome.BLOCKED)
        assertFalse(s.isBlocked("other.de"))
    }

    @Test
    fun `a newsroom with some paid articles stays readable, network errors teach nothing`() = runBlocking {
        val s = store()
        repeat(6) { s.record("www.spiegel.de", ReadOutcome.OK) }
        s.record("www.spiegel.de", ReadOutcome.PAYWALL)
        s.record("www.spiegel.de", ReadOutcome.PAYWALL)
        assertFalse(s.isBlocked("spiegel.de"))
        repeat(5) { s.record("slow.example.org", ReadOutcome.NETWORK) }
        assertNull(s.statsFor("slow.example.org"))
        // A couple of video pages do not block a site, a whole run of them on a site that never had text does.
        repeat(3) { s.record("clips.example.net", ReadOutcome.NOT_ARTICLE) }
        assertFalse(s.isBlocked("clips.example.net"))
        s.record("clips.example.net", ReadOutcome.NOT_ARTICLE)
        assertTrue(s.isBlocked("clips.example.net"))
    }

    @Test
    fun `a seeded site that reads fine twice is let back in, a learned block expires`() = runBlocking {
        val s = store()
        s.record("www.faz.net", ReadOutcome.OK)
        assertTrue(s.isBlocked("faz.net"))
        s.record("www.faz.net", ReadOutcome.OK)
        assertFalse(s.isBlocked("faz.net"))

        s.record("wall.example.com", ReadOutcome.CONSENT)
        s.record("wall.example.com", ReadOutcome.CONSENT)
        assertTrue(s.isBlocked("wall.example.com"))
        clock += ReadabilityStore.LEARNED_FOR.plus(Duration.ofDays(1)).toMillis()
        s.record("unrelated.org", ReadOutcome.OK) // any record recomputes the list
        assertFalse(s.isBlocked("wall.example.com"))
    }

    @Test
    fun `notes survive a restart, seeds seen failing come before the rest, aggregator hosts are ignored`() = runBlocking {
        val s = store()
        s.record("www.nytimes.com", ReadOutcome.PAYWALL)
        s.record("learned-site.com", ReadOutcome.BLOCKED)
        s.record("learned-site.com", ReadOutcome.BLOCKED)
        s.record("news.google.com", ReadOutcome.BLOCKED)
        val again = store()
        assertEquals(listOf("learned-site.com", "nytimes.com", "youtube.com", "faz.net"), again.blockedDomains().toList())
        assertEquals(1, again.statsFor("nytimes.com")?.failed)
        assertNull(again.statsFor("news.google.com"))
        assertEquals(2, again.snapshot().size)
    }

    @Test
    fun `parallel records are all counted`() = runBlocking {
        val s = store()
        (1..50).map { async(Dispatchers.Default) { s.record("busy.example.com", ReadOutcome.OK) } }.awaitAll()
        assertEquals(50, s.statsFor("busy.example.com")?.ok)
        assertEquals(50, store().statsFor("busy.example.com")?.ok)
    }

    @Test
    fun `registrable domains`() {
        assertEquals("bbc.co.uk", ReadabilityStore.domainOf("www.bbc.co.uk"))
        assertEquals("cnn.com", ReadabilityStore.domainOf("edition.cnn.com"))
        assertEquals("abc.net.au", ReadabilityStore.domainOf("www.abc.net.au"))
        assertEquals("wdr.de", ReadabilityStore.domainOf("www1.wdr.de"))
        assertEquals("spiegel.de", ReadabilityStore.domainOf("https://www.spiegel.de/politik/a-1"))
        assertEquals("localhost", ReadabilityStore.domainOf("localhost"))
        assertNull(ReadabilityStore.domainOf(" "))
    }
}

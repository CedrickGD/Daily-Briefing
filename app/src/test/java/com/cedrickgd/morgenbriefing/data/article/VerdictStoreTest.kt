package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.AppJson
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class VerdictStoreTest {

    private lateinit var dir: File
    private var clock = 1_800_000_000_000L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("verdicts").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val file get() = File(dir, "verdicts.json")

    private fun TestScope.store(max: Int = VerdictStore.MAX_ENTRIES) =
        VerdictStore(file, AppJson, backgroundScope, clock = { clock }, maxEntries = max)

    @Test
    fun `final verdicts are kept across restarts, passing states are not`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.record("a", ArticleStatus.READABLE)
        s.record("b", ArticleStatus.PAYWALL)
        s.record("c", ArticleStatus.BLOCKED)
        s.record("d", ArticleStatus.FAILED)
        s.record("e", ArticleStatus.LOADING)
        s.record("f", ArticleStatus.UNKNOWN)
        s.save()
        assertTrue(file.isFile)
        val again = store().restore()
        assertEquals(
            mapOf("a" to ArticleStatus.READABLE, "b" to ArticleStatus.PAYWALL, "c" to ArticleStatus.BLOCKED, "d" to ArticleStatus.FAILED),
            again,
        )
    }

    @Test
    fun `verdicts expire after a week, like the article cache`() = runTest(StandardTestDispatcher()) {
        val s = store()
        s.record("old", ArticleStatus.READABLE)
        clock += Duration.ofDays(6).toMillis()
        s.record("new", ArticleStatus.READABLE)
        s.save()
        assertEquals(ArticleStatus.READABLE, s.verdictOf("old"))
        clock += Duration.ofDays(2).toMillis()
        assertNull("8 days old", s.verdictOf("old"))
        assertEquals(mapOf("new" to ArticleStatus.READABLE), store().restore())
    }

    @Test
    fun `the store is bounded, the oldest verdicts go first, a new verdict replaces the old one`() = runTest(StandardTestDispatcher()) {
        val s = store(max = 3)
        listOf("a", "b", "c").forEach {
            s.record(it, ArticleStatus.READABLE)
            clock += 1_000
        }
        s.record("a", ArticleStatus.PAYWALL) // a changes: now the newest
        clock += 1_000
        s.record("d", ArticleStatus.READABLE)
        s.save()
        assertEquals(mapOf("c" to ArticleStatus.READABLE, "a" to ArticleStatus.PAYWALL, "d" to ArticleStatus.READABLE), store(max = 3).restore())
    }

    @Test
    fun `a broken file or one of an older judgement starts over`() = runTest(StandardTestDispatcher()) {
        file.writeText("{ not json")
        assertTrue(store().restore().isEmpty())
        file.writeText("""{"version":0,"entries":[{"id":"a","status":"READABLE","at":$clock}]}""")
        assertTrue(store().restore().isEmpty())
        file.writeText("""{"version":${VerdictStore.VERSION},"entries":[{"id":"a","status":"READABLE","at":$clock}]}""")
        assertEquals(mapOf("a" to ArticleStatus.READABLE), store().restore())
    }
}

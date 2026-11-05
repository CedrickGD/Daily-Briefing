package com.cedrickgd.morgenbriefing.ui.discover

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.DiscoverStrings
import com.cedrickgd.morgenbriefing.ui.news.Verification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Discover previews with "nur vollständige Artikel": verified headlines only, the rest checked first. */
class DiscoverVerifiedTest {

    private fun item(id: String, topic: String) = NewsItem(
        id = id, headline = "H$id", url = "https://example.org/$id", source = "S", sourceId = "s", topicId = topic,
    )

    private val news = mapOf(
        "wirtschaft" to TopicNews(
            "wirtschaft", "2026-11-05T09:00:00+01:00", (1..6).map { item("w$it", "wirtschaft") }, listOf(SourceStatus("s", "S", ok = true)),
        ),
    )

    private fun state(readableOnly: Boolean, statuses: Map<String, ArticleStatus>, offline: Boolean = false) = buildDiscoverState(
        AppSettings(uiLanguage = AppLanguage.DE, edition = NewsEdition.GERMANY, followed = listOf("welt"), readableOnly = readableOnly),
        AppLanguage.DE, news, emptySet(), emptySet(), DiscoverSelection(categoryId = "wirtschaft"),
        Verification(enabled = true, statuses = statuses), offline,
    )

    @Test
    fun `a preview shows verified headlines only and names what to check next`() {
        val s = state(true, mapOf("w1" to ArticleStatus.READABLE, "w2" to ArticleStatus.PAYWALL))
        val preview = s.detail!!.preview
        assertEquals(listOf("w1"), preview.items.map { it.id })
        assertTrue(preview.checking)
        assertEquals(listOf("w3", "w4", "w5"), preview.toVerify.map { it.id })
        assertEquals(listOf("w3", "w4", "w5"), s.previewsToVerify().map { it.id })

        // Three verified: nothing more to check.
        val full = state(true, (1..3).associate { "w$it" to ArticleStatus.READABLE })
        assertEquals(listOf("w1", "w2", "w3"), full.detail!!.preview.items.map { it.id })
        assertTrue(full.previewsToVerify().isEmpty())
    }

    @Test
    fun `nothing complete, offline and the setting switched off`() {
        val none = state(true, (1..6).associate { "w$it" to ArticleStatus.BLOCKED }).detail!!.preview
        assertTrue(none.noneComplete)
        assertTrue(none.empty)

        val offline = state(true, emptyMap(), offline = true).detail!!.preview
        assertTrue(offline.offline)
        assertFalse("no endless skeleton offline", offline.waiting)

        val off = state(false, emptyMap()).detail!!.preview
        assertEquals(listOf("w1", "w2", "w3"), off.items.map { it.id })
        assertTrue(off.toVerify.isEmpty())
    }

    @Test
    fun `the preview texts exist in every language`() {
        for (lang in AppLanguage.entries) {
            val s = DiscoverStrings.of(lang)
            listOf(s.previewChecking, s.previewNoneComplete, s.previewOffline).forEach { assertTrue("$lang", it.isNotBlank()) }
        }
    }
}

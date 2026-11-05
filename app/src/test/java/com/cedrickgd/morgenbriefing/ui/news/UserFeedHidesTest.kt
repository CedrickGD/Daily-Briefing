package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Only readable articles" leaves the feeds the user added alone: the user chose those sites. */
class UserFeedHidesTest {

    private fun item(id: String, sourceId: String) =
        NewsItem(id = id, headline = "Schlagzeile $id", url = "https://www.kicker.de/$id", source = "kicker", sourceId = sourceId, topicId = "custom:f")

    @Test
    fun `articles of a user's feed show even from a walled site`() {
        val hides = ReadabilityHides.of(listOf("kicker.de"), mapOf("paid" to ArticleStatus.PAYWALL))
        val own = item("own", "custom:k1")
        val paid = item("paid", "custom:k1")
        val builtIn = item("builtIn", "kicker")
        assertEquals(listOf(own, paid), readableItems(listOf(own, paid, builtIn), hides, readableOnly = true))
    }
}

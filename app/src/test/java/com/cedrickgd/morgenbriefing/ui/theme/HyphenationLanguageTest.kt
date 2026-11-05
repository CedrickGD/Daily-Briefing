package com.cedrickgd.morgenbriefing.ui.theme

import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.domain.ItemEdition
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class HyphenationLanguageTest {

    private fun item(sourceId: String) = NewsItem(
        id = sourceId, headline = "Headline", url = "https://example.com/a", source = "Source", sourceId = sourceId, topicId = "top",
    )

    @Test
    fun `feed text is hyphenated in the article's language, not always German`() {
        assertEquals("en", readingLanguageTag(ItemEdition.languageOf(item("gn:local:washington-us-1a2b@US:en")), AppLanguage.DE))
        assertEquals("en", readingLanguageTag(ItemEdition.languageOf(item("bbc-top")), AppLanguage.DE))
        assertEquals("de", readingLanguageTag(ItemEdition.languageOf(item("tagesschau-top")), AppLanguage.EN))
        assertEquals("fr", readingLanguageTag(ItemEdition.languageOf(item("gn:top@FR:fr#page")), AppLanguage.IT))
    }

    @Test
    fun `unknown languages follow the interface`() {
        assertEquals("it", readingLanguageTag(ItemEdition.languageOf(item("custom:abcd1234")), AppLanguage.IT))
        assertEquals("es", readingLanguageTag(null, AppLanguage.ES))
        assertEquals("es", readingLanguageTag("  ", AppLanguage.ES))
        assertEquals("ja", readingLanguageTag("JA", AppLanguage.DE))
    }
}

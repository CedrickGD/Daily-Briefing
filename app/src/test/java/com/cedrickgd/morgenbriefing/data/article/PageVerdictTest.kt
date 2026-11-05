package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/** Paywall, consent and bot-check verdicts, the JSON-LD fallback and picture URLs. */
class PageVerdictTest {

    private val now: ZonedDateTime = ZonedDateTime.parse("2026-11-05T12:00:00+01:00")

    private fun html(name: String): String = javaClass.getResource("/article/$name")!!.readText()

    private fun paragraphs(n: Int, prefix: String = "Absatz") = (1..n).joinToString("") { i ->
        "<p>$prefix $i erzählt in aller Ruhe, was an diesem Tag in der Stadt passiert ist, wer dabei war und warum das " +
            "für die Menschen vor Ort eine Rolle spielt, mit $i Stimmen aus der Nachbarschaft.</p>"
    }

    @Test
    fun `a page marked as paid in its JSON-LD is a paywall, and its hidden articleBody is never used`() {
        val page = ArticleExtractor.read(html("paid-jsonld.html"), "https://www.sueddeutsche.de/politik/zuege-li.3573071", now)
        assertEquals(ReadOutcome.PAYWALL, page.outcome)
        assertFalse(page.content?.text.orEmpty().contains("GEHEIMER BEZAHLTEXT"))
    }

    @Test
    fun `a login wall after the free part is a paywall`() {
        val page = ArticleExtractor.read(html("regwall.html"), "https://www.aerzteblatt.de/news/zervixkarzinom-05c2c6e3", now)
        assertEquals(ReadOutcome.PAYWALL, page.outcome)
    }

    @Test
    fun `a short free article on a page full of SPIEGEL+ and subscribe links is readable`() {
        val page = ArticleExtractor.read(html("free-short.html"), "https://www.spiegel.de/wirtschaft/erbschaftsteuer-a-f63369a2", now)
        assertEquals(ReadOutcome.OK, page.outcome)
        assertEquals(3, page.content!!.blocks.size)
        assertTrue(page.content!!.looksTruncated) // short, but complete: the reader shows a hint only
    }

    @Test
    fun `the publisher's open JSON-LD articleBody is the text when the page renders it with scripts`() {
        val page = ArticleExtractor.read(html("jsonld-body.html"), "https://www.example-daily.com/news/tram", now)
        assertEquals(ReadOutcome.OK, page.outcome)
        val blocks = page.content!!.blocks.filter { it.type == ArticleBlock.PARAGRAPH }
        assertEquals(4, blocks.size)
        assertTrue(blocks[1].text.startsWith("Construction is set to begin"))
    }

    @Test
    fun `paywall wording at the end of a short text, and brand marks inside a paywall box`() {
        val short = "<html><body><article><h1>T</h1>" + paragraphs(2) +
            "<p>Jetzt weiterlesen mit SPIEGEL+. Sie haben bereits ein Digital-Abo? Hier anmelden.</p></article></body></html>"
        assertEquals(ReadOutcome.PAYWALL, ArticleExtractor.read(short, "https://www.spiegel.de/a", now).outcome)

        val box = "<html><body><article><h1>T</h1>" + paragraphs(2) + "</article><div class=\"paywall-offer\"><p>Z+</p><p>Exklusiv</p></div></body></html>"
        assertEquals(ReadOutcome.PAYWALL, ArticleExtractor.read(box, "https://www.zeit.de/a", now).outcome)

        // A long article that merely mentions the paid brand, with a "Jetzt abonnieren" newsletter box, is free.
        val long = "<html><body><article><h1>T</h1>" + paragraphs(12) + "<p>Mehr dazu bei SPIEGEL+ und im Newsletter.</p></article>" +
            "<div class=\"newsletter\"><p>Jetzt abonnieren</p></div></body></html>"
        assertEquals(ReadOutcome.OK, ArticleExtractor.read(long, "https://www.spiegel.de/b", now).outcome)
    }

    @Test
    fun `bot checks are blocked, consent pages are consent`() {
        val cloudflare = "<!DOCTYPE html><html lang=\"en-US\"><head><title>Just a moment...</title></head><body>" +
            "<script src=\"https://challenges.cloudflare.com/turnstile/v0/api.js\"></script></body></html>"
        assertEquals(ReadOutcome.BLOCKED, ArticleExtractor.read(cloudflare, "https://www.chathamhouse.org/a", now).outcome)
        val waf = "<html><head><script>window.awsWafCookieDomainList = []; window.gokuProps = {\"key\":\"x\"};</script></head><body></body></html>"
        assertEquals(ReadOutcome.BLOCKED, ArticleExtractor.read(waf, "https://www.espn.com/a", now).outcome)
        val datadome = "<html><head><title>reuters.com</title></head><body><p id=\"cmsg\">Please enable JS and disable any ad blocker</p>" +
            "<script>var dd={'rt':'i','host':'geo.captcha-delivery.com'}</script></body></html>"
        assertEquals(ReadOutcome.BLOCKED, ArticleExtractor.read(datadome, "https://www.reuters.com/a", now).outcome)

        val golem = "<html><body><article><h1>Cookies zustimmen</h1><p>Besuchen Sie Golem.de wie gewohnt mit Werbung und Tracking, " +
            "indem Sie der Nutzung aller Cookies zustimmen. Details zum Tracking finden Sie im Privacy Center.</p>" +
            "<p>Oder lesen Sie Golem.de ohne Werbung und Tracking: mit dem Pur-Abo für wenige Euro im Monat.</p></article></body></html>"
        assertEquals(ReadOutcome.CONSENT, ArticleExtractor.read(golem, "https://www.golem.de/sonstiges/zustimmung/auswahl.html", now).outcome)
        assertTrue(PageSignals.isConsentRedirect("https://www.golem.de/news/a-2611-1.html", "https://www.golem.de/sonstiges/zustimmung/auswahl.html?from=x"))
        assertFalse(PageSignals.isConsentRedirect("https://www.golem.de/news/a.html", "https://www.golem.de/news/a.html?page=2"))
    }

    @Test
    fun `an audio or video page without text is no article`() {
        val page = ArticleExtractor.read("<html><body><main><h1>Podcast</h1><p>Hören Sie die Folge.</p><audio src=\"x.mp3\"></audio></main></body></html>", "https://www.deutschlandfunk.de/a", now)
        assertEquals(ReadOutcome.NOT_ARTICLE, page.outcome)
        assertNull(page.content)
    }

    @Test
    fun `lazy pictures - widest srcset candidate, data-src over a placeholder, picture sources, noscript twin`() {
        val html = "<html><body><article><h1>T</h1>" + paragraphs(2) +
            "<figure><img src=\"data:image/gif;base64,R0lGOD\" srcset=\"https://cdn.example.org/a-480.jpg 480w, https://cdn.example.org/a-1600.jpg 1600w, https://cdn.example.org/a-3200.jpg 3200w\" alt=\"A\"></figure>" +
            paragraphs(1, "Zwischen") +
            "<p><img src=\"https://cdn.example.org/placeholder.gif\" data-src=\"https://cdn.example.org/b.jpg\" alt=\"B\"></p>" +
            paragraphs(1, "Weiter") +
            "<picture><source srcset=\"https://cdn.example.org/c-800.webp 800w, https://cdn.example.org/c-1200.webp 1200w\"><img alt=\"C\"></picture>" +
            paragraphs(1, "Danach") +
            "<div><img class=\"lazy\" src=\"https://cdn.example.org/spacer.gif\" alt=\"D\"><noscript><img src=\"https://cdn.example.org/d.jpg\" alt=\"D\"></noscript></div>" +
            paragraphs(2, "Ende") + "</article></body></html>"
        val images = ArticleExtractor.read(html, "https://www.example.org/x", now).content!!.blocks.filter { it.type == ArticleBlock.IMAGE }.map { it.url }
        assertEquals(
            listOf("https://cdn.example.org/a-1600.jpg", "https://cdn.example.org/b.jpg", "https://cdn.example.org/c-1200.webp", "https://cdn.example.org/d.jpg"),
            images,
        )
    }
}

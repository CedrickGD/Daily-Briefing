package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The teaser / lead / hero arrangement and the matchers, with texts as the live pages had them. */
class ArticleCleanerTest {

    private fun content(vararg blocks: ArticleBlock, title: String = "T", image: String? = null) =
        ArticleContent(url = "https://x.de/a", title = title, blocks = blocks.toList(), image = image, fetchedAt = "2026-11-05T12:00:00+01:00")

    @Test
    fun `a feed teaser that is standfirst plus the first paragraphs is cut back to the standfirst (The Guardian)`() {
        val p0 = "A British healthcare worker with Ebola is being evacuated from the Democratic Republic of the Congo to the UK for medical treatment, it has been confirmed."
        val p1 = "“A British citizen currently in the DR Congo has tested positive for Ebola,” a spokesperson for the UK government said in a statement on Wednesday."
        val p2 = "The patient, whose identity is unknown, is in a stable condition and is expected to arrive in the UK on Wednesday or Thursday."
        val teaser = "Patient is ⁠in ​stable condition and will be transferred to a specialist ‘high consequence’ infectious disease unit\n$p0\n“A British citizen currently in the DR Congo has tested positive for Ebola,” a spokesperson … Continue reading..."
        val arranged = ArticleCleaner.arrange(content(ArticleBlock.paragraph(p0), ArticleBlock.paragraph(p1), ArticleBlock.paragraph(p2)), teaser)
        assertEquals("Patient is ⁠in ​stable condition and will be transferred to a specialist ‘high consequence’ infectious disease unit", arranged.teaser)
        assertEquals(3, arranged.content.blocks.size)
    }

    @Test
    fun `a teaser the feed cut without an ellipsis is replaced by the full lead paragraph (Ärzteblatt)`() {
        val lead = "Seattle – Obwohl die altersstandardisierten Inzidenz- und Mortalitätsraten des Zervixkarzinoms weltweit bis 2050 voraussichtlich sinken werden, könnte die absolute Zahl der Neuerkrankungen und Todesfälle deutlich steigen."
        val teaser = "Seattle – Obwohl die altersstandardisierten Inzidenz- und Mortalitätsraten des Zervixkarzinoms weltweit bis 2050 voraussichtlich sinken werden, könnte die absolute Zahl der Neuerkrankungen und Todesfälle d"
        val arranged = ArticleCleaner.arrange(content(ArticleBlock.paragraph(lead), ArticleBlock.paragraph("Das Autorenteam analysierte 204 Länder.")), teaser)
        assertEquals(lead, arranged.teaser)
        assertEquals(listOf("Das Autorenteam analysierte 204 Länder."), arranged.content.blocks.map { it.text })
    }

    @Test
    fun `a long lead paragraph that contains the teaser hides the teaser instead (CNBC)`() {
        val lead = "The Democratic midterm election sweep could result in changes in economic policies related to trade, spending priorities and artificial intelligence. " +
            "But the biggest forces shaping the economy aren't going to change just because the party in power did. " + "Economists point to affordability, spending and the AI boom. ".repeat(6)
        val arranged = ArticleCleaner.arrange(content(ArticleBlock.paragraph(lead.trim())), "The biggest forces shaping the economy aren't going to change just because the party in power did.")
        assertEquals("", arranged.teaser)
        assertEquals(1, arranged.content.blocks.size)
    }

    @Test
    fun `a header box with headline and teaser is dropped and the feed teaser stays (Handelsblatt)`() {
        val teaser = "Die Bundeswehr wächst und übernimmt mehr Verantwortung in der Nato. Verteidigungsminister Pistorius setzt für den weiteren Aufbau auf Heeresinspekteur Christian Freuding als neuen Generalinspekteur."
        val box = "Bundeswehr : Heereschef Freuding tritt an Spitze der Bundeswehr $teaser"
        val arranged = ArticleCleaner.arrange(
            content(ArticleBlock.paragraph(box), ArticleBlock.paragraph("Berlin. Heeresinspekteur Christian Freuding wird im Februar neuer Generalinspekteur.")),
            teaser,
            headline = "Bundeswehr: Heereschef Freuding tritt an Spitze der Bundeswehr",
        )
        assertEquals(teaser, arranged.teaser)
        assertEquals(1, arranged.content.blocks.size)
    }

    @Test
    fun `the feed headline repeated as a heading and a hero rendition as the first picture go`() {
        val hero = "https://cdn.prod.www.spiegel.de/images/327d5f02-2fac-494d-a61c-f8bb672195b9_w520_r2.08_fpx38_fpy55.01.jpg"
        val arranged = ArticleCleaner.arrange(
            content(
                ArticleBlock.image("https://cdn.prod.www.spiegel.de/images/327d5f02-2fac-494d-a61c-f8bb672195b9_w1200_r1.33_fpx51_fpy50.jpg", "Foto"),
                ArticleBlock.caption("Erben in Deutschland (Symbolbild)"),
                ArticleBlock.heading("Erbschaftsteuer: Großerben zahlen auf Firmenvermögen kaum Steuern"),
                ArticleBlock.paragraph("Aktuelle Zahlen über geringe Steuereinnahmen des Staates bei Großerben könnten die Debatte befeuern."),
            ),
            teaser = "",
            headline = "Erbschaftsteuer: Großerben zahlen auf Firmenvermögen kaum Steuern",
            hero = hero,
        )
        assertEquals(listOf(ArticleBlock.PARAGRAPH), arranged.content.blocks.map { it.type })
    }

    @Test
    fun `a different picture and a different first paragraph stay`() {
        val c = content(
            ArticleBlock.image("https://images.tagesschau.de/image/11111111-2222-3333-4444-555555555555/AAA/BBB/16x9/anderes-bild-100.jpg"),
            ArticleBlock.paragraph("Ganz anderer Einstieg in den Text, der nichts mit dem Teaser zu tun hat und weitergeht."),
        )
        val arranged = ArticleCleaner.arrange(c, "Der Teaser der Redaktion ist ein anderer Satz über etwas anderes.", hero = "https://images.tagesschau.de/image/8e6be664-a867-424d-82e0-a1f8ce29c500/AAA/BBB/16x9/demokraten-124.jpg")
        assertEquals(2, arranged.content.blocks.size)
        assertEquals("Der Teaser der Redaktion ist ein anderer Satz über etwas anderes.", arranged.teaser)
    }

    @Test
    fun `templated paragraphs that differ in their numbers are not duplicates`() {
        val blocks = (1..4).map { ArticleBlock.paragraph("Im Wahlkreis $it gewann die Kandidatin der Regierungspartei mit ${40 + it} Prozent der Stimmen deutlich vor dem Herausforderer.") }
        assertEquals(4, ArticleCleaner.clean(blocks, "Wahlergebnisse").size)
    }

    @Test
    fun `noise and credit recognition`() {
        assertTrue(ArticleCleaner.isNoise("Stand: 05.11.2026 • 09:03 Uhr"))
        assertTrue(ArticleCleaner.isNoise("Published 5 November 2026 — 3 minute READ"))
        assertTrue(ArticleCleaner.isNoise("Nov 5, 2026 9:00 AM UTC"))
        assertTrue(ArticleCleaner.isNoise("Von Caspar Busse und Roland Muschel, Stuttgart"))
        assertFalse(ArticleCleaner.isNoise("Von der Leyen fordert mehr Tempo bei der Aufrüstung."))
        assertTrue(ArticleCleaner.isCredit("Mike Campbell | Nurphoto | Getty Images"))
        assertTrue(ArticleCleaner.isCredit("Heeresinspekteur Christian Freuding wird im Februar neuer Generalinspekteur. (Archivbild) Foto: Kay Nietfeld/dpa"))
        assertFalse(ArticleCleaner.isCredit("Das teilte die Polizei am Abend mit. Quelle ist ein Bericht der Stadt."))
        assertTrue(ArticleCleaner.isRelatedLink("Read More: Inside the Democrats’ Plans to Take On Trump"))
        assertTrue(ArticleCleaner.isMediaNoise("Audio starten, abbrechen mit Escape"))
        assertTrue(ArticleCleaner.isMediaNoise("AUDIO: Pläne zur Umgestaltung des Alstervorlands (1 Min)"))
        assertEquals("Party expected to get less than a third of the vote", ArticleCleaner.cleanTeaser("Party expected to get less than a third of the vote Continue reading..."))
        assertEquals("Die Preise steigen immer mehr", ArticleCleaner.cleanTeaser("Die Preise steigen immer mehr"))
    }

    @Test
    fun `title recognition copes with kickers and separators`() {
        assertTrue(ArticleCleaner.isTitle("US-Zwischenwahl Demokraten erobern Mehrheit im Senat", "US-Zwischenwahl: Demokraten erobern Mehrheit im Senat"))
        assertTrue(ArticleCleaner.isTitle("Kongresswahlen in den USA Die Ergebnisse der Midterms in Zahlen und Karten", "Die Ergebnisse der Midterms in den USA in Zahlen und Karten"))
        assertTrue(ArticleCleaner.isTitle("Rechtsextremismus Bundestag debattiert über Aufarbeitung der NSU-Verbrechen", "Rechtsextremismus - Bundestag debattiert über Aufarbeitung der NSU-Verbrechen"))
        assertFalse(ArticleCleaner.isTitle("Wer gewann wo?", "US-Zwischenwahl: Demokraten erobern Mehrheit im Senat"))
    }
}

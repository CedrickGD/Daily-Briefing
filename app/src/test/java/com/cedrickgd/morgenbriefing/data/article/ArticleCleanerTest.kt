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
    fun `date lines are meta, sentences that open with the same word are text`() {
        listOf(
            "Stand: 05.11.2026 • 09:03 Uhr", "Veröffentlicht am 5. November 2026", "Aktualisiert vor 2 Stunden",
            "Zuletzt aktualisiert am Mittwoch, 05.11.2026, 18:03 Uhr", "Updated 2 hours ago", "Posted on November 5, 2026",
            "Published 5 November 2026 — 3 minute READ", "Erschienen: Die Zeit Nr. 46",
        ).forEach { assertTrue(it, ArticleCleaner.isNoise(it)) }
        listOf(
            "Stand Mittwochabend wurden 23 Verletzte gezählt, teilte die Polizei mit.",
            "Stand heute sind 23 Menschen gestorben.",
            "Stand 5. November wurden 23 Verletzte gezählt.",
            "Veröffentlicht wurde die Studie im Fachblatt Nature.",
            "Published in Nature, the study found that sleep matters.",
            "Updated rules apply from 2027 on.",
        ).forEach { assertFalse(it, ArticleCleaner.isNoise(it)) }
        // As the lead before the first long paragraph, such a sentence stays.
        val lead = "Stand heute sind 23 Menschen gestorben."
        val long = "Die Feuerwehr war mit mehr als hundert Einsatzkräften vor Ort, die Bergungsarbeiten dauerten bis in die Nacht und sollen am Morgen fortgesetzt werden, teilte ein Sprecher mit."
        val cleaned = ArticleCleaner.clean(listOf(ArticleBlock.paragraph("Stand: 05.11.2026, 09:03 Uhr"), ArticleBlock.paragraph(lead), ArticleBlock.paragraph(long)), "Unglück")
        assertEquals(listOf(lead, long), cleaned.map { it.text })
    }

    @Test
    fun `a consent dialog at the end goes, a closing sentence that opens with Zustimmen stays`() {
        val text = (1..5).map { ArticleBlock.paragraph("Absatz $it erzählt in aller Ruhe, was im Bundestag beschlossen wurde und warum das für viele Menschen eine Rolle spielt.") }
        val closing = ArticleBlock.paragraph("Zustimmen muss nun noch der Bundesrat, der am 21. November tagt.")
        assertEquals(6, ArticleCleaner.clean(text + closing, "Gesetz").size)
        val dialog = listOf(ArticleBlock.heading("Weiter mit Werbung"), ArticleBlock.paragraph("Zustimmen"))
        assertEquals((text + closing).map { it.text }, ArticleCleaner.clean(text + closing + dialog, "Gesetz").map { it.text })
        assertTrue(ArticleCleaner.isConsentLine("Accept all"))
        assertTrue(ArticleCleaner.isConsentLine("Datenschutzoptionen anpassen"))
        assertFalse(ArticleCleaner.isConsentLine("Accept the challenge, says the coach"))
        assertFalse(ArticleCleaner.isConsentLine("Zustimmen muss nun noch der Bundesrat."))
    }

    @Test
    fun `an author's bio box at the end goes, a picture of the story near the end stays with the text after it`() {
        val text = (1..5).map { ArticleBlock.paragraph("Absatz $it erzählt in aller Ruhe, was an der Unfallstelle passiert ist und wie die Helfer vorgegangen sind.") }
        val story = listOf(
            ArticleBlock.image("https://img.example.com/unfall.jpg", alt = "Foto von der Unfallstelle"),
            ArticleBlock.caption("Feuerwehr am Unfallort (Foto: dpa)"),
            ArticleBlock.paragraph("Die Straße bleibt bis zum Abend gesperrt."),
            ArticleBlock.paragraph("Die Polizei sucht Zeugen."),
        )
        assertEquals(9, ArticleCleaner.clean(text + story, "Unfall").size)
        val crowd = listOf(ArticleBlock.image("https://img.example.com/demo.jpg", alt = "Bild von Demonstranten vor dem Reichstag"), ArticleBlock.paragraph("Die Demonstranten zogen am Abend weiter."))
        assertEquals(7, ArticleCleaner.clean(text + crowd, "Demo").size)

        val bio = listOf(
            ArticleBlock.image("https://img.example.com/k.orland.jpg", alt = "Photo of Kyle Orland"),
            ArticleBlock.paragraph("Kyle Orland Senior Gaming Editor"),
            ArticleBlock.paragraph("Kyle Orland has been the Senior Gaming Editor since 2012, writing primarily about the business, tech, and culture behind video games."),
        )
        assertEquals(text.map { it.text }, ArticleCleaner.clean(text + bio, "Spiele").map { it.text })
        // A picture of a person in the story, followed by text about them, is no bio.
        val person = listOf(
            ArticleBlock.image("https://img.example.com/merkel.jpg", alt = "Bild von Angela Merkel"),
            ArticleBlock.paragraph("Angela Merkel sagte am Abend, sie habe mit diesem Ergebnis gerechnet und sei nicht überrascht."),
        )
        assertEquals(7, ArticleCleaner.clean(text + person, "Wahl").size)
    }

    @Test
    fun `short paragraphs may repeat, long ones may not`() {
        val q1 = ArticleBlock.paragraph("Haben Sie mit diesem Ergebnis gerechnet?")
        val q2 = ArticleBlock.paragraph("Werden Sie jetzt zurücktreten?")
        val no = ArticleBlock.paragraph("Nein.")
        val long = ArticleBlock.paragraph("Das Interview führte unsere Korrespondentin am Mittwoch in Berlin, kurz nach der Bekanntgabe.")
        val cleaned = ArticleCleaner.clean(listOf(long, q1, no, q2, no, long), "Interview")
        assertEquals(listOf(long, q1, no, q2, no).map { it.text }, cleaned.map { it.text })
    }

    @Test
    fun `title recognition copes with kickers and separators`() {
        assertTrue(ArticleCleaner.isTitle("US-Zwischenwahl Demokraten erobern Mehrheit im Senat", "US-Zwischenwahl: Demokraten erobern Mehrheit im Senat"))
        assertTrue(ArticleCleaner.isTitle("Kongresswahlen in den USA Die Ergebnisse der Midterms in Zahlen und Karten", "Die Ergebnisse der Midterms in den USA in Zahlen und Karten"))
        assertTrue(ArticleCleaner.isTitle("Rechtsextremismus Bundestag debattiert über Aufarbeitung der NSU-Verbrechen", "Rechtsextremismus - Bundestag debattiert über Aufarbeitung der NSU-Verbrechen"))
        assertFalse(ArticleCleaner.isTitle("Wer gewann wo?", "US-Zwischenwahl: Demokraten erobern Mehrheit im Senat"))
    }
}

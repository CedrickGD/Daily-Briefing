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
    fun `a lead paragraph that rewords the teaser goes (ABC News, seen on the emulator)`() {
        val teaser = "President Donald Trump said he intends to keep Defense Secretary Pete Hegseth in his job, as Rep. Massie, prepares to introduce an impeachment resolution against him."
        val lead = "President Donald Trump said Thursday he intends to keep Defense Secretary Pete Hegseth in his job, as GOP Rep. Thomas Massie prepares to reintroduce an impeachment resolution against Hegseth."
        val next = "\"I have every intention to keep him,\" Trump said."
        val arranged = ArticleCleaner.arrange(content(ArticleBlock.paragraph(lead), ArticleBlock.paragraph(next)), teaser)
        assertEquals(listOf(next), arranged.content.blocks.map { it.text })
        assertEquals(lead, arranged.teaser)
    }

    @Test
    fun `a second paragraph sharing the teaser's words but saying more stays`() {
        val teaser = "The central bank kept interest rates unchanged on Thursday and signalled that further cuts are unlikely before the spring."
        val lead = "Inflation has eased for three months in a row, the bank said, but wage growth and energy prices remain a worry for its policymakers."
        val arranged = ArticleCleaner.arrange(content(ArticleBlock.paragraph(lead)), teaser)
        assertEquals(teaser, arranged.teaser)
        assertEquals(1, arranged.content.blocks.size)
        assertFalse(TextMatch.paraphrase(lead, teaser))
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
    fun `a different first paragraph stays, a different picture does not show in the text (one picture, on top)`() {
        val c = content(
            ArticleBlock.image("https://images.tagesschau.de/image/11111111-2222-3333-4444-555555555555/AAA/BBB/16x9/anderes-bild-100.jpg"),
            ArticleBlock.paragraph("Ganz anderer Einstieg in den Text, der nichts mit dem Teaser zu tun hat und weitergeht."),
        )
        val arranged = ArticleCleaner.arrange(c, "Der Teaser der Redaktion ist ein anderer Satz über etwas anderes.", hero = "https://images.tagesschau.de/image/8e6be664-a867-424d-82e0-a1f8ce29c500/AAA/BBB/16x9/demokraten-124.jpg")
        assertEquals(listOf(ArticleBlock.PARAGRAPH), arranged.content.blocks.map { it.type })
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

    @Test
    fun `the consent placeholder of an embed goes with its provider's name, the text around it stays (t3n, T-Online)`() {
        val before = ArticleBlock.paragraph("„Sie entstammen der Physik des frühen Universums noch vor den ersten Sternen“, erklärt Studienautor Valerio De Luca laut einer Mitteilung der Johns Hopkins University in Baltimore vom Montag.")
        val after = ArticleBlock.paragraph("In ihren Berechnungen schlossen die Forscher:innen zunächst aus, dass solche Urzeit-Löcher schon bei ihrer Geburt Millionen Sonnenmassen wogen.")
        val t3n = listOf(
            ArticleBlock.paragraph("Empfohlene redaktionelle Inhalte"),
            ArticleBlock.paragraph("Hier findest du externe Inhalte von"),
            ArticleBlock.paragraph("TargetVideo GmbH"),
            ArticleBlock.paragraph(", die unser redaktionelles Angebot auf t3n.de ergänzen. Mit dem Klick auf \"Inhalte anzeigen\" erklärst du dich einverstanden, dass wir dir jetzt und in Zukunft Inhalte von"),
            ArticleBlock.paragraph("TargetVideo GmbH"),
            ArticleBlock.paragraph("auf unseren Seiten anzeigen dürfen. Dabei können personenbezogene Daten an Plattformen von Drittanbietern übermittelt werden."),
            ArticleBlock.paragraph("Hinweis zum Datenschutz"),
            ArticleBlock.paragraph("Leider ist etwas schief gelaufen..."),
            ArticleBlock.paragraph("An dieser Stelle findest du normalerweise externe Inhalte von"),
            ArticleBlock.paragraph("TargetVideo GmbH"),
            ArticleBlock.paragraph(", jedoch konnten wir deine Consent-Einstellungen nicht abrufen."),
            ArticleBlock.paragraph("Lade die Seite neu oder passe deine Consent-Einstellungen manuell an."),
        )
        assertEquals(listOf(before, after).map { it.text }, ArticleCleaner.clean(listOf(before) + t3n + after, "Little Red Dots").map { it.text })

        val tonline = listOf(
            ArticleBlock.paragraph(
                "Wir benötigen Ihre Einwilligung, um den von unserer Redaktion eingebundenen Instagram-Inhalt anzuzeigen. Sie können diesen (und damit " +
                    "auch alle weiteren Instagram-Inhalte auf t-online.de) mit einem Klick anzeigen lassen und auch wieder deaktivieren.",
            ),
            ArticleBlock.paragraph("Instagram-Inhalte immer anzeigen"),
        )
        assertEquals(listOf(before, after).map { it.text }, ArticleCleaner.clean(listOf(before) + tonline + after, "Sterban").map { it.text })

        // A company's name between two paragraphs of the story is no leftover, nor is a sentence about consent.
        val story = listOf(before, ArticleBlock.paragraph("TargetVideo GmbH"), after)
        assertEquals(3, ArticleCleaner.clean(story, "Little Red Dots").size)
        assertFalse(ArticleCleaner.isEmbedConsent("Die Datenschützer kritisieren, dass externe Inhalte von YouTube ohne Einwilligung der Nutzer geladen werden."))
        assertFalse(ArticleCleaner.isEmbedConsent("Empfohlene Inhalte wählt bei Netflix ein Algorithmus aus."))
    }

    @Test
    fun `a newsroom asking to be preferred on Google goes, with its whole box at the end (t3n, WELT, Sportschau)`() {
        val text = (1..5).map { ArticleBlock.paragraph("Absatz $it erzählt in aller Ruhe, wie die Adler Mannheim das Spiel gegen Ingolstadt gedreht haben und was das für die Tabelle heißt.") }
        // Under the teaser: only the line goes.
        val welt = ArticleBlock.paragraph("Klicken Sie hier, um sich Artikel von WELT in der Google-Suche bevorzugt anzeigen zu lassen.")
        val t3n = ArticleBlock.paragraph("Als bevorzugte Quelle auf Google hinzufügen")
        assertEquals(text.map { it.text }, ArticleCleaner.clean(listOf(welt) + text, "Eishockey").map { it.text })
        assertEquals(text.map { it.text }, ArticleCleaner.clean(text.take(1) + t3n + text.drop(1), "Eishockey").map { it.text })
        // At the end: the box explains itself and trails off in link fragments.
        val box = listOf(
            ArticleBlock.heading("Unsere Artikel gezielt bei Google anzeigen lassen"),
            ArticleBlock.paragraph("Wenn Sie bei Google nach Informationen suchen, werden Ihnen bestimmte Nachrichten in der sogenannten Schlagzeilen-Box angezeigt. Wie das genau geht, erklären wir Ihnen"),
            ArticleBlock.paragraph("hier"),
            ArticleBlock.paragraph(". → Hier können Sie"),
            ArticleBlock.paragraph("SWR Sport als bevorzugte Quelle einstellen"),
        )
        assertEquals(text.map { it.text }, ArticleCleaner.clean(text + box, "Eishockey").map { it.text })

        assertFalse(ArticleCleaner.isSourcePromo("Wasserkraft ist in Norwegen die bevorzugte Quelle für Strom."))
        assertFalse(ArticleCleaner.isSourcePromo("Nutzer können sich bei Google Maps die Staus auf ihrer Strecke anzeigen lassen."))
    }

    @Test
    fun `what taz hangs under its text goes - author box, error link, teasers and the subscription offer`() {
        val text = (1..5).map { ArticleBlock.paragraph("Absatz $it erzählt in aller Ruhe, was die Unterhändler in Abu Dhabi besprochen haben und warum daraus so schnell nichts folgen wird.") }
        val tail = listOf(
            ArticleBlock.image("https://img.example.com/johnson.jpg", alt = "Dominic Johnson"),
            ArticleBlock.paragraph("Dominic Johnson Ressortleiter Ausland"),
            ArticleBlock.paragraph("Seit 2011 Co-Leiter des taz-Auslandsressorts und seit 1990 Afrikaredakteur der taz."),
            ArticleBlock.paragraph("Inhaltlichen Fehler melden"),
            ArticleBlock.paragraph("Stadtgespräch aus Moskau Von Lügen und Siegesfantasien Kolumne Stadtgespräch von Vera Bessonova Die Ohren spitzen genügt."),
            ArticleBlock.image("https://img.example.com/rekruten.jpg", alt = "Junge Rekruten stehen mit ernstem Gesichtsausdruck und Gewehr in der Hand in Formation"),
            ArticleBlock.paragraph("3 Monate wochentaz für nur 15 Euro Die Wochenzeitung mit taz-Blick Unsere wochentaz bietet jeden Samstag Journalismus, der es nicht allen recht macht."),
            ArticleBlock.image("https://img.example.com/packshot.jpg", alt = "Ein Packshot der Wochentaz-Zeitung mit einer neutralen Illustration auf der Titelseite."),
        )
        assertEquals(text.map { it.text }, ArticleCleaner.clean(text + tail, "Die Nebelkerze vom Donbass").map { it.text })

        // The link on top of a page closes nothing: only the line itself goes.
        val top = ArticleCleaner.clean(listOf(ArticleBlock.paragraph("Fehler melden")) + text, "Die Nebelkerze vom Donbass")
        assertEquals(text.map { it.text }, top.map { it.text })
        assertFalse(ArticleCleaner.isErrorReport("Fehler melden die Prüfer in jedem dritten Bericht."))

        // A person of the story near the end, named in the alt text and written about in sentences, is no author box.
        val person = listOf(
            ArticleBlock.image("https://img.example.com/selenskyj.jpg", alt = "Wolodymyr Selenskyj"),
            ArticleBlock.paragraph("Wolodymyr Selenskyj will die Antwort seiner Gesellschaft überlassen."),
        )
        assertEquals(7, ArticleCleaner.clean(text + person, "Die Nebelkerze vom Donbass").size)
    }
}

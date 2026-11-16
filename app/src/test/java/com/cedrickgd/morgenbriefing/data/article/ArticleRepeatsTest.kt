package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

/**
 * "Pictures or texts appear twice": every cause found in the survey of 162 live pages on
 * 15.11.2026 (ArticleDuplicateSurvey), each with the blocks or markup of the page it was seen on.
 */
class ArticleRepeatsTest {

    private fun p(text: String) = ArticleBlock.paragraph(text)
    private fun h(text: String) = ArticleBlock.heading(text)
    private fun img(url: String, alt: String = "") = ArticleBlock.image(url, alt)
    private fun caption(text: String) = ArticleBlock.caption(text)

    private fun content(blocks: List<ArticleBlock>, title: String = "", image: String? = null, description: String = "") =
        ArticleContent(url = "https://example.org/a", title = title, blocks = blocks, image = image, fetchedAt = "2026-11-15T12:00:00+01:00", description = description)

    /** A paragraph of the text proper: long, and different for every [n]. */
    private fun text(n: Int) = "Absatz $n erzählt in einem ganzen Satz, was an dieser Stelle der Geschichte geschieht, wer daran beteiligt war, " +
        "wie es dazu kam und warum es für die Menschen in der Stadt Nummer $n von Bedeutung ist."

    private fun texts(blocks: List<ArticleBlock>) = blocks.filter { it.type == ArticleBlock.PARAGRAPH }.map { it.text }

    // ------------------------------------------------------------------------------ pictures

    @Test
    fun `the text keeps no picture - the hero again, other pictures and their captions all go (BBC, Fox, ANSA)`() {
        val feed = "https://ichef.bbci.co.uk/ace/standard/240/cpsprodpb/3c47/live/464f7b70-e0e6-11f1-a701-fddedf244c16.png"
        val blocks = (1..10).map { p(text(it)) } + listOf(
            img("https://ichef.bbci.co.uk/ace/standard/976/cpsprodpb/3c47/live/464f7b70-e0e6-11f1-a701-fddedf244c16.png", "A police car in the middle of a road in Leeds"),
            caption("Officers are appealing for anyone who witnessed the crash to get in touch"),
            p(text(11)),
            img("https://ichef.bbci.co.uk/ace/standard/976/cpsprodpb/90c1/live/77aa0b20-e0e6-11f1-a701-fddedf244c16.png", "A white car which has been hit"),
            caption("The crash happened in the early hours of Sunday morning"),
            p(text(12)),
        )
        val arranged = ArticleCleaner.arrange(content(blocks), teaser = "", headline = "Man dies after crash in Leeds", hero = feed)
        assertEquals(feed, arranged.hero)
        val shown = arranged.content.blocks
        // One picture per article: the one on top.
        assertTrue(shown.none { it.type == ArticleBlock.IMAGE || it.type == ArticleBlock.CAPTION })
        assertEquals("no paragraph is lost", 12, texts(shown).size)
    }

    @Test
    fun `without a feed or lead picture the first picture of the text moves up - and every copy of it goes`() {
        val blocks = listOf(
            p(text(1)), img("https://cdn.example.org/2026/11/rathaus.jpg?w=1200", "Das Rathaus"), p(text(2)), p(text(3)),
            img("https://cdn.example.org/2026/11/rathaus.jpg?w=640"), img("https://cdn.example.org/2026/11/markt.jpg"),
        )
        val arranged = ArticleCleaner.arrange(content(blocks), teaser = "", headline = "Eine Überschrift mit Wörtern", hero = null, pictureFromText = true)
        assertEquals("https://cdn.example.org/2026/11/rathaus.jpg?w=1200", arranged.hero)
        assertTrue(arranged.content.blocks.none { it.type == ArticleBlock.IMAGE })
        // Not asked for (the cleaner's default): no hero – and still no picture in the text.
        val plain = ArticleCleaner.arrange(content(blocks), teaser = "")
        assertNull(plain.hero)
        assertTrue(plain.content.blocks.none { it.type == ArticleBlock.IMAGE })
    }

    @Test
    fun `renditions named after a tilde are one picture, numbered pictures of one shoot are not (WDR)`() {
        assertTrue(
            ImageMatch.same(
                "https://www1.wdr.de/sport/eishockey/moritz-mueller-148~_v-gseagaleriexl.jpg",
                "https://www1.wdr.de/sport/eishockey/moritz-mueller-148~_v-fullhd.jpg",
            ),
        )
        assertTrue(ImageMatch.same("https://www.zdfheute.de/assets/bedrohte-koralle-100~384x216?cb=1", "https://www.zdfheute.de/assets/bedrohte-koralle-100~1920x1080?cb=2"))
        // Two photos of one evening, two charts of one poll: different pictures.
        assertFalse(
            ImageMatch.same(
                "https://www1.wdr.de/landesausschuss-kleiner-parteitag-wuest-102~_v-gseagaleriexl.jpg",
                "https://www1.wdr.de/landesausschuss-kleiner-parteitag-wuest-100~_v-fullhd.jpg",
            ),
        )
        assertFalse(
            ImageMatch.same(
                "https://www1.wdr.de/politik/politik-in-nrw/nrw-trend-november-2026-104~_v-fullhd.jpg",
                "https://www1.wdr.de/politik/politik-in-nrw/nrw-trend-november-2026-118~_v-fullhd.jpg",
            ),
        )
    }

    @Test
    fun `other renditions seen live - the same id is the same picture, the same generic file name is not`() {
        // ANSA: og:image and the picture under the byline.
        assertTrue(
            ImageMatch.same(
                "https://www.ansa.it/webimages/img_1280x720/2026/11/15/22dce1e14af98564c8501d9e6b96d187.jpg",
                "https://www.ansa.it/webimages/news_base/2026/11/15/22dce1e14af98564c8501d9e6b96d187.jpg",
            ),
        )
        // Fox News: the feed's original and the page's 686x384 rendition.
        assertTrue(
            ImageMatch.same(
                "https://static.foxnews.com/foxnews.com/content/uploads/2025/11/scott-bessent-mamdani.jpg",
                "https://a57.foxnews.com/static.foxnews.com/foxnews.com/content/uploads/2025/11/686/384/scott-bessent-mamdani.jpg?ve=1&tl=1",
            ),
        )
        // Stills of two videos, two agency photos of one scene (NPR).
        assertFalse(ImageMatch.same("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", "https://i.ytimg.com/vi/9bZkp7q19f0/maxresdefault.jpg"))
        assertFalse(
            ImageMatch.same(
                "https://npr.brightspotcdn.com/dims3/default/strip/false/crop/2882x1746+0+0/resize/2882x1746!/quality/85/format/webp/?url=https%3A%2F%2Fnpr-production-npr.s3.us-east-1.amazonaws.com%2Fbrightspot%2F83%2F46%2F2a8840234f4db5747471e0da5f83%2Fap26317811767503.jpg",
                "https://npr.brightspotcdn.com/dims3/default/strip/false/crop/4096x3072+0+0/resize/1800/quality/85/format/webp/?url=https%3A%2F%2Fnpr-production-npr.s3.us-east-1.amazonaws.com%2Fbrightspot%2Fbc%2F06%2F74467ed1483ca5a8783fe377c5c7%2Fap26317811759411.jpg",
            ),
        )
    }

    // ------------------------------------------------------------- headline above the text

    @Test
    fun `a box above the page's own headline goes, and so do headline, standfirst, dateline and lead picture (ANSA)`() {
        val headline = "Trovata morta sulle strisce pedonali, rintracciato un automobilista coinvolto"
        val standfirst = "E' un 56enne del posto, indagini per chiarire la dinamica dell'incidente"
        val first = "Una 68enne è stata trovata morta stamani in strada lungo l'Aurelia a Donoratico (Livorno), dal personale del 118, intervenuto con " +
            "un'automedica e due ambulanze. La donna era riversa a terra sulle strisce pedonali con varie ferite sul corpo."
        val second = "E' stato rintracciato l'automobilista che sarebbe rimasto coinvolto nel presunto incidente stradale. Si tratta di un 56enne del posto. " +
            "Ora le indagini dei carabinieri di Donoratico dovranno chiarire la precisa dinamica dell'incidente."
        val page = listOf(
            p("Se hai scelto di non accettare i cookie di profilazione e tracciamento, puoi aderire all’abbonamento \"Consentless\" a un costo molto accessibile, oppure scegliere un altro abbonamento per accedere ad ANSA.it."),
            p("Ti invitiamo a leggere le Condizioni Generali di Servizio, la Cookie Policy e l'Informativa Privacy."),
            ArticleBlock.list(listOf("Durata annuale (senza rinnovo automatico)", "Pubblicità presente ma non profilata o gestibile mediante il pannello delle preferenze")),
            p("ALTRI ABBONAMENTI"),
            p("Mostra meno"),
            h(headline),
            p(standfirst),
            p("LIVORNO, 15 novembre 2026, 15:32"),
            p("Redazione ANSA"),
            img("https://www.ansa.it/webimages/news_base/2026/8/5/4a5c69106a8c0f7d5ef2fbfefe42a2d0.jpg", "Foto d 'archivio - RIPRODUZIONE RISERVATA"),
            p("Foto d 'archivio - RIPRODUZIONE RISERVATA"),
            p(first),
            p(second),
        )
        val description = "$standfirst (ANSA)"
        val cleaned = ArticleCleaner.clean(page, "$headline - Notizie - Ansa.it", description)
        val og = "https://www.ansa.it/webimages/img_1280x720/2026/8/5/4a5c69106a8c0f7d5ef2fbfefe42a2d0.jpg"
        val arranged = ArticleCleaner.arrange(content(cleaned, title = "$headline - Notizie - Ansa.it", image = og, description = description), teaser = description, headline = headline, hero = og)
        assertEquals(listOf(first, second), arranged.content.blocks.map { it.text })
        assertTrue(arranged.content.blocks.all { it.type == ArticleBlock.PARAGRAPH })
        assertEquals(description, arranged.teaser)
        assertEquals(og, arranged.hero)
    }

    @Test
    fun `only the whole headline marks the beginning - a subheading quoting it does not cut the text`() {
        val headline = "Wetter in Berlin und Brandenburg: \"Schnee ist derzeit kein Thema\""
        val blocks = listOf(p(text(1)), p(text(2)), h("Schnee ist derzeit kein Thema"), p(text(3)))
        val arranged = ArticleCleaner.arrange(content(blocks), teaser = "", headline = headline)
        assertEquals(4, arranged.content.blocks.size)
    }

    @Test
    fun `another version of the headline before the text goes, a subheading stays (Bild, n-tv)`() {
        val headline = "JP Kraemer spricht nach 11 Wochen Schweigen: 231 Mal „Ich“, aber kein Wort über sein Opfer"
        val blocks = listOf(
            h("Ehefrau misshandelt: JP Kraemer sagt 231 Mal „Ich“, aber kein Wort über sein Opfer"),
            h("18-Minuten-Jammervideo nach 11 Wochen Funkstille"),
            p(text(1)),
            // After the text began, a heading may take up the headline's words.
            h("Kein Wort über sein Opfer und die Tat"),
            p(text(2)),
        )
        val shown = ArticleCleaner.arrange(content(blocks), teaser = "", headline = headline).content.blocks
        assertEquals(listOf("18-Minuten-Jammervideo nach 11 Wochen Funkstille", "Kein Wort über sein Opfer und die Tat"), shown.filter { it.type == ArticleBlock.HEADING }.map { it.text })
        val ntv = ArticleCleaner.arrange(content(listOf(h("Exil-Belarusse in WarschauIn Polen kippt die Stimmung gegen Ukrainer"), p(text(1)))), "", "In Polen kippt die Stimmung gegen Ukrainer")
        assertEquals(listOf(text(1)), ntv.content.blocks.map { it.text })
    }

    @Test
    fun `a breadcrumb that ends in the headline goes, the summary list stays and the teaser does not repeat its points (rbb24)`() {
        val headline = "Wetter in Berlin und Brandenburg: \"Schnee ist derzeit kein Thema\""
        val points = listOf(
            "Die neue Woche startet in Berlin und Brandenburg mit vielen Wolken, Regen und Wind",
            "Mit Schnee ist derzeit nicht zu rechnen",
            "Die Prognosen zum Winter in der Region fallen noch vage aus",
        )
        val page = listOf(ArticleBlock.list(listOf("rbb24 >", "Panorama >", headline)), ArticleBlock.list(points), p(text(1)), p(text(2)))
        val cleaned = ArticleCleaner.clean(page, headline)
        assertEquals(listOf(ArticleBlock.LIST, ArticleBlock.PARAGRAPH, ArticleBlock.PARAGRAPH), cleaned.map { it.type })
        assertEquals(points, cleaned.first().items)

        val teaser = "Die neue Woche startet in Berlin und Brandenburg mit vielen Wolken, Regen und Wind. Schnee wie Anfang der Woche im Landkreis Oder-Spree ist " +
            "\"derzeit kein Thema\", so der ARD-Wetterexperte Vögtlin. Auch die Prognosen zum Winter in der Region fallen noch vage aus."
        val arranged = ArticleCleaner.arrange(content(cleaned, title = headline), teaser, headline)
        assertEquals("Schnee wie Anfang der Woche im Landkreis Oder-Spree ist \"derzeit kein Thema\", so der ARD-Wetterexperte Vögtlin.", arranged.teaser)
        assertEquals(3, arranged.content.blocks.size)
    }

    // -------------------------------------------------------------------------------- teaser

    @Test
    fun `a teaser that only repeats the headline is not shown (Al Jazeera video pages)`() {
        val headline = "Nearly 1,500 migrants reach Spain’s Canary Islands in one day"
        val arranged = ArticleCleaner.arrange(content(listOf(p(text(1)))), teaser = headline, headline = headline)
        assertEquals("", arranged.teaser)
        assertEquals(1, arranged.content.blocks.size)
    }

    @Test
    fun `what WordPress appends to a feed teaser goes - it is the headline again (The Decoder)`() {
        val teaser = "Anthropics Sprachmodell erzeugt Bilder auf ungewöhnlichem Weg: Es schreibt Python-Code, der Pinselstriche auf eine Leinwand setzt."
        assertEquals(teaser, ArticleCleaner.cleanTeaser("$teaser\nDer Artikel Claude malt fotorealistische Gemälde Strich für Strich erschien zuerst auf The Decoder."))
        assertEquals("A short summary of the story.", ArticleCleaner.cleanTeaser("A short summary of the story. The post Claude paints stroke by stroke appeared first on The Decoder."))
        // A sentence that merely uses the words stays.
        val plain = "Der Artikel 5 des Grundgesetzes schützt die Pressefreiheit, sagte die Ministerin am Montag in Berlin."
        assertEquals(plain, ArticleCleaner.cleanTeaser(plain))
    }

    @Test
    fun `a heading before the text that is the teaser shows once (SZ am Abend)`() {
        val blocks = listOf(h("Was heute wichtig war"), p(text(1)), h("Was morgen wichtig wird"), p(text(2)))
        val arranged = ArticleCleaner.arrange(content(blocks), teaser = "Was heute wichtig war.", headline = "SZ am Abend: Nachrichten vom 15. November 2026")
        assertEquals("Was heute wichtig war.", arranged.teaser)
        assertEquals(listOf(text(1), "Was morgen wichtig wird", text(2)), arranged.content.blocks.map { it.text })
    }

    @Test
    fun `a teaser made of the first sentences of the lead paragraphs is hidden, the text stays whole (USA Today, WDR)`() {
        val blocks = listOf(
            p("The surging El Niño continues to make news."),
            p("The current El Niño climate pattern has reached the top of the scale for intensity, according to a report released Nov. 12 from NOAA's Climate Prediction Center (CPC). The pattern is now considered to be \"very strong\"."),
            p("Only three other El Niños on record have attained this level: 1982-83, 1997-98 and 2015-16. Modern El Niño records started in 1950."),
        )
        val teaser = "The current El Niño climate pattern has reached the top of the scale for intensity. Only three other El Niños on record have attained this level."
        val arranged = ArticleCleaner.arrange(content(blocks), teaser, "El Niño still growing stronger. Here's what could happen next")
        assertEquals("", arranged.teaser)
        assertEquals(3, arranged.content.blocks.size)

        // The first sentence opens the lead paragraph, what is left ("Platz zwei …") is too little to stand alone.
        val wdr = listOf(p("Gut fünf Monate vor der Landtagswahl verteidigt Schwarz-Grün die Mehrheit in der Sonntagsfrage. Die CDU trotzt mit 30 Prozent dem Abwärtstrend der Partei auf Bundesebene. Zweitstärkste Kraft im NRW-Trend wird erstmals die AfD."))
        val wdrTeaser = "Gut fünf Monate vor der Landtagswahl verteidigt Schwarz-Grün die Mehrheit in der Sonntagsfrage. Platz Zwei holt erstmals die AfD."
        assertEquals("", ArticleCleaner.arrange(content(wdr), wdrTeaser, "NRW-Trend: Wüst und die CDU führen deutlich").teaser)
    }

    @Test
    fun `a teaser the text does not repeat stays as it is`() {
        val teaser = "Bürgermeister Peter Tschentscher warnte in seiner Rede auf dem Ohlsdorfer Friedhof vor Populismus und antidemokratischen Anfeindungen."
        val arranged = ArticleCleaner.arrange(content(listOf(p(text(1)), p(text(2)))), teaser, "Volkstrauertag: Tschentscher fordert Einsatz für Demokratie")
        assertEquals(teaser, arranged.teaser)
        assertEquals(teaser, ArticleCleaner.withoutRepeatedSentences(teaser, listOf(text(1), text(2))))
    }

    // ------------------------------------------------------------------- alt text and caption

    @Test
    fun `an alt text that repeats the headline, a neighbour or an earlier alt text is not printed, nor one that says nothing`() {
        val headline = "Il maltempo non dà tregua alla Toscana. Nuova allerta gialla per lunedì 16 novembre"
        val soldier = "Ein französischer Soldat legt am Internationalen Mahnmal der KZ-Gedenkstätte Neuengamme zum Volkstrauertag einen Kranz nieder."
        val page = listOf(
            p(text(1)),
            img("https://img.example.org/video-still.jpg", headline),
            p(text(2)),
            p(soldier),
            img("https://img.example.org/kranz.jpg", soldier),
            p(text(3)),
            img("https://img.example.org/player.jpg", "Videoplayer"),
            p(text(4)),
            img("https://img.example.org/kreuz-1.jpg", "Ein großes hölzernes Kreuz steht am Denkmal"),
            p(text(5)),
            img("https://img.example.org/kreuz-2.jpg", "Ein großes hölzernes Kreuz steht am Denkmal"),
            p(text(6)),
            img("https://img.example.org/schueler.jpg", "Schüler stehen auf dem Gelände der Gedenkstätte."),
        )
        val alts = ArticleCleaner.clean(page, headline).filter { it.type == ArticleBlock.IMAGE }.map { it.alt }
        assertEquals(listOf("", "", "", "Ein großes hölzernes Kreuz steht am Denkmal", "", "Schüler stehen auf dem Gelände der Gedenkstätte."), alts)
        // What the reader arranges keeps no picture at all, and so no alt text either.
        val late = ArticleCleaner.arrange(content(listOf(p(text(1)), img("https://img.example.org/video-still.jpg", headline), p(text(2)))), teaser = "", headline = headline)
        assertEquals(listOf(ArticleBlock.PARAGRAPH, ArticleBlock.PARAGRAPH), late.content.blocks.map { it.type })
    }

    @Test
    fun `a short paragraph under a picture that repeats its alt text is the caption (Vatican News, BFM)`() {
        val page = listOf(
            p(text(1)),
            img("https://www.vaticannews.va/content/dam/2026/11/14/1794669896842.JPG", "Leone XIV in volo verso Santa Cruz de Succhabamba"),
            p("Leone XIV in volo verso Santa Cruz de Succhabamba (VATICAN MEDIA)"),
            p(text(2)),
        )
        val cleaned = ArticleCleaner.clean(page, "Leone XIV: dalle Ande una fede schietta")
        assertEquals(listOf(ArticleBlock.PARAGRAPH, ArticleBlock.IMAGE, ArticleBlock.CAPTION, ArticleBlock.PARAGRAPH), cleaned.map { it.type })
        assertEquals("", cleaned[1].alt)
        assertEquals("Leone XIV in volo verso Santa Cruz de Succhabamba (VATICAN MEDIA)", cleaned[2].text)
    }

    // ---------------------------------------------------------------------------------- text

    @Test
    fun `a pull quote or caption set as a paragraph goes when a longer paragraph says the same words (NPR)`() {
        val quote = "Nobody questions a wealthy family sending their kids to public school and it should be the same for early childhood"
        val full = "\"$quote,\" said Catron Allred, who oversees Kids Campus and the early childhood program at Santa Fe Community College."
        // Far into the text (the lead's own repeats were caught before), with paragraphs between the two.
        val before = (1..14).map { p(text(it)) }
        val cleaned = ArticleCleaner.clean(before + listOf(p(quote), p(text(15)), p(text(16)), p(full), p("Nein."), p(text(17)), p("Nein.")), "A headline of the story")
        assertEquals((1..14).map(::text) + listOf(text(15), text(16), full, "Nein.", text(17), "Nein."), texts(cleaned))
    }

    @Test
    fun `the heading again as a line below it goes (live blog anchors)`() {
        val heading = "L'Estonia chiuderà il confine con la Russia dal 1° dicembre"
        val entry = "Lo ha annunciato il ministro degli Esteri Margus Tsahkna. \"L'Estonia chiuderà tutti i valichi di frontiera con la Russia a partire dall'1 dicembre\", ha scritto sui social media."
        val cleaned = ArticleCleaner.clean(listOf(p(text(1)), h(heading), p(entry), p("$heading Vai al contenuto"), p(text(2))), "Guerra Ucraina, le ultime notizie")
        assertEquals(listOf(text(1), heading, entry, text(2)), cleaned.map { it.text })
    }

    @Test
    fun `captions with their own switch and credits without a picture (NPR, USA Today)`() {
        val page = listOf(
            p(text(1)),
            img("https://npr.brightspotcdn.com/kids-campus.jpg"),
            p("Kids Campus is a sought-after childcare center, with a waitlist nearly 700 children long. Nina Alexandria Riggio for NPR hide caption"),
            p("Nina Alexandria Riggio for NPR"),
            p(text(2)),
            p("Show Caption Hide Caption How winter weather could cut back your home heating prices this year"),
            p("Bree Lamb for NPR"),
            p(text(3)),
            p("Gerald Herbert/AP"),
            p("Tips for Parents"),
            p(text(4)),
        )
        val cleaned = ArticleCleaner.clean(page, "New Mexico wanted free childcare for all")
        assertEquals(
            listOf(
                text(1), "", "Kids Campus is a sought-after childcare center, with a waitlist nearly 700 children long. Nina Alexandria Riggio for NPR",
                text(2), text(3), "Tips for Parents", text(4),
            ),
            cleaned.map { it.text },
        )
        assertEquals(ArticleBlock.CAPTION, cleaned[2].type)
    }

    @Test
    fun `follow boxes and the author box with its avatar go (The Verge)`() {
        val page = listOf(
            p("Sheena Vasani Sheena Vasani Posts from this author will be added to your daily email digest and your homepage feed. See All by Sheena Vasani writes about tech news, reviews gadgets, and helps readers save money by highlighting deals."),
            img("https://platform.theverge.com/wp-content/uploads/sites/2/2025/01/SHEENA_BLURPLE.jpg", "Sheena Vasani"),
            p(text(1)),
            p(text(2)),
            p("Follow topics and authors from this story to see more like this in your personalized homepage feed and to receive email updates."),
            ArticleBlock.list(
                listOf(
                    "Black Friday Posts from this topic will be added to your daily email digest and your homepage feed. See All Black Friday",
                    "Deals Posts from this topic will be added to your daily email digest and your homepage feed. See All Deals",
                ),
            ),
        )
        assertEquals(listOf(p(text(1)), p(text(2))), ArticleCleaner.clean(page, "Fanttik’s S1 Pro electric screwdriver is \$40 off"))
    }

    @Test
    fun `ad markers between paragraphs and the BBC's caption label are not text`() {
        val html = """
            <html><head><title>USA: Alle Flughäfen stundenlang gesperrt</title></head><body><article>
            <h1>USA: Alle Flughäfen stundenlang gesperrt</h1>
            <p>${text(1)}</p>
            <p>Weiterlesen nach der Anzeige</p>
            <p>${text(2)}</p>
            <figure><img src="https://ichef.bbci.co.uk/ace/standard/976/cpsprodpb/90c1/live/77aa0b20.png" alt="A white car" width="976">
            <figcaption>Image caption, The crash happened in the early hours of Sunday morning</figcaption></figure>
            <p>Weiterlesen nach der Anzeige</p>
            <p>${text(3)}</p>
            <p>${text(4)}</p>
            </article></body></html>
        """.trimIndent()
        val page = ArticleExtractor.read(html, "https://www.heise.de/news/USA-Alle-Flughaefen-gesperrt-11525181.html", ZonedDateTime.parse("2026-11-15T12:00:00+01:00"))
        assertEquals(ReadOutcome.OK, page.outcome)
        val blocks = page.content!!.blocks
        assertEquals(listOf(text(1), text(2), text(3), text(4)), texts(blocks))
        assertEquals(listOf("The crash happened in the early hours of Sunday morning"), blocks.filter { it.type == ArticleBlock.CAPTION }.map { it.text })
    }
}

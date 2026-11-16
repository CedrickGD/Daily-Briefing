package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sources, credits, link boxes and prompts leave the reader's text (the owner, 16.11.2026: "no
 * source clutter") – in German, English and French, with tails as newsrooms set them. A sentence
 * of the story that names its source stays.
 */
class ArticleSourcesTest {

    private fun p(text: String) = ArticleBlock.paragraph(text)
    private fun h(text: String) = ArticleBlock.heading(text)
    private fun list(vararg items: String) = ArticleBlock.list(items.toList())
    private fun texts(blocks: List<ArticleBlock>) = blocks.map { if (it.type == ArticleBlock.LIST) it.items.joinToString(" | ") else it.text }

    private val mietenLead = "Der Bundestag hat am Donnerstag mit breiter Mehrheit ein Gesetz beschlossen, das die Mietpreisbremse bis 2029 verlängert. " +
        "Die Opposition kritisierte, die Regelung komme zu spät und greife zu kurz."
    private val mietenQuote = "Bundesjustizministerin Hubig sagte der Deutschen Presse-Agentur, man werde die Wirkung der Bremse genau beobachten und im " +
        "kommenden Jahr nachsteuern, falls das nötig sei."
    private val mietenVote = "Nach Angaben der dpa stimmten 412 Abgeordnete für das Gesetz, 198 dagegen. Der Bundesrat muss noch zustimmen, was als sicher gilt."

    @Test
    fun `a German agency story loses its link box, agency credits, prompt and copyright line`() {
        val page = listOf(
            p(mietenLead),
            p(mietenQuote),
            h("Mehr zum Thema"),
            list("Mietpreisbremse: Was Mieter jetzt wissen müssen", "Wohnungsmarkt: Neubau bricht ein"),
            p(mietenVote),
            p("Mit Material von dpa und AFP"),
            p("jdl/dpa"),
            p("Abonnieren Sie unseren kostenlosen Newsletter."),
            p("© dpa-infocom, dpa:261116-930-123456/1"),
        )
        assertEquals(listOf(mietenLead, mietenQuote, mietenVote), texts(ArticleCleaner.clean(page, "Mietpreisbremse bis 2029 verlängert")))
    }

    @Test
    fun `an English wire story loses Reuters and AP credits, related links, photo credit and newsletter prompt`() {
        val lead = "Boeing on Monday said it would cut production of its 737 MAX jets for the rest of the year after regulators ordered " +
            "additional inspections of fuselage panels supplied by Spirit AeroSystems."
        val quote = "\"We are taking the time to get this right,\" the company told Reuters in a statement, adding that deliveries to airlines would resume in March."
        val page = listOf(
            p(lead),
            p("Photo: Getty Images"),
            p(quote),
            h("Related coverage"),
            p("Airlines brace for delays as Boeing slows output"),
            p("FAA widens probe into 737 MAX panels"),
            p("Reporting by David Shepardson in Washington; Editing by Chizu Nomiyama and Bill Berkrot"),
            p("The Associated Press contributed to this report."),
            p("Sign up for our daily business newsletter."),
            p("Copyright 2026 The Associated Press. All rights reserved. This material may not be published, broadcast, rewritten or redistributed without permission."),
        )
        assertEquals(listOf(lead, quote), texts(ArticleCleaner.clean(page, "Boeing cuts 737 MAX output")))
    }

    @Test
    fun `a French story loses its À lire aussi box, avec AFP, photo credit, first-published note and social prompt`() {
        val lead = "Le gouvernement a présenté mercredi un projet de loi visant à réduire le déficit public de 20 milliards d'euros dès l'an prochain, " +
            "une mesure jugée insuffisante par plusieurs économistes."
        val quote = "« Nous allons continuer à réformer », a déclaré le ministre de l'Économie à l'AFP."
        val page = listOf(
            p(lead),
            h("À lire aussi"),
            list("Budget 2027 : ce qui va changer pour les ménages", "Déficit : Bruxelles s'inquiète"),
            p(quote),
            p("Le Figaro avec AFP"),
            p("Crédit photo : Ludovic Marin / AFP"),
            p("Cet article a été publié initialement dans Le Monde."),
            p("Suivez-nous sur les réseaux sociaux."),
        )
        assertEquals(listOf(lead, quote), texts(ArticleCleaner.clean(page, "Le gouvernement veut réduire le déficit")))
    }

    @Test
    fun `a list of sources goes up to the next real section, a box in the middle stops before the text`() {
        val long = "Die Zahl der Grippefälle ist in der vergangenen Woche deutlich gestiegen, wie aus dem aktuellen Wochenbericht hervorgeht. " +
            "Besonders betroffen sind Kinder unter fünf Jahren und ältere Menschen."
        val conclusion = "Fachleute raten deshalb allen über 60-Jährigen, sich jetzt impfen zu lassen, und Eltern kleiner Kinder, bei Fieber früh " +
            "die Kinderarztpraxis anzurufen, statt in die Notaufnahme zu fahren."
        val sources = listOf(
            p(long),
            h("Quellen"),
            p("Robert Koch-Institut: Epidemiologisches Bulletin 45/2026."),
            p("Statistisches Bundesamt, Pressemitteilung Nr. 412 vom 12. November 2026."),
            h("Was jetzt hilft"),
            p(conclusion),
        )
        assertEquals(listOf(long, "Was jetzt hilft", conclusion), texts(ArticleCleaner.clean(sources, "Grippewelle rollt an")))

        val box = listOf(
            p(long),
            h("Mehr zum Thema"),
            p("Grippe: Wann die Impfung sinnvoll ist"),
            p("Die Praxen sind voll."),
            p(conclusion),
        )
        assertEquals(listOf(long, "Die Praxen sind voll.", conclusion), texts(ArticleCleaner.clean(box, "Grippewelle rollt an")))
    }

    @Test
    fun `source and credit lines in three languages`() {
        listOf(
            "Quelle: dpa", "Quellen: Statistisches Bundesamt, Eurostat, OECD", "Source: Reuters", "Source : AFP", "Sources: BBC, CNN",
            "(dpa)", "AFP", "dpa/AFP/Reuters", "mit dpa", "jdl/dpa", "© dpa", "Reuters, AP",
            "Mit Material von dpa", "Mit Agenturmaterial", "Unter Verwendung von Material der Nachrichtenagenturen dpa und AFP", "With reporting from Reuters",
            "Mitarbeit: Anna Muster, Paul Beispiel", "Text: Max Muster", "Recherche: Lena Schmidt", "Übersetzung: Max Muster",
            "Foto: Kay Nietfeld/dpa", "Bild: IMAGO/Chris Emil Janßen", "Titelbild: picture alliance", "© picture alliance / dpa",
            "AP Photo/Evan Vucci", "picture alliance / dpa | Kay Nietfeld", "Photo: Getty Images", "Crédit photo : Ludovic Marin / AFP",
            "Reporting by David Shepardson; Editing by Chizu Nomiyama", "Additional reporting by Jane Doe in Paris.", "(Reporting by Jane Doe)",
            "The Associated Press contributed to this report.", "AP writer Jane Doe in Paris contributed.",
            "Copyright 2026 The Associated Press. All rights reserved.", "Alle Rechte vorbehalten.", "Tous droits réservés",
            "Text von Max Muster", "Ein Beitrag von Anna Muster", "Propos recueillis par Jean Dupont", "Rédaction : Jean Dupont",
            "Avec AFP", "Le Figaro avec AFP", "La rédaction avec AFP", "Par Jean Dupont avec l'AFP",
            "Dieser Artikel erschien zuerst bei Zeit Online.", "Der Text ist zuerst in der Süddeutschen Zeitung erschienen.",
            "This article was originally published on The Conversation.", "This story first appeared in ProPublica's weekly edition.",
            "Cet article a été publié initialement dans Le Monde.", "Article initialement paru sur Le Figaro.",
            "Abonnieren Sie unseren kostenlosen Newsletter.", "Folgen Sie uns auf Instagram und Facebook.", "Hier geht's zum WhatsApp-Kanal.",
            "Sign up for our daily newsletter.", "Suivez-nous sur les réseaux sociaux.", "Abonnez-vous à notre newsletter.",
        ).forEach { assertTrue(it, ArticleCleaner.isSourceLine(it)) }
    }

    @Test
    fun `sentences of the story that name a source are text`() {
        listOf(
            "Das sagte ein Sprecher der Polizei am Montag der dpa.",
            "Nach Angaben der dpa wurden drei Menschen verletzt.",
            "Il a parlé avec l'AFP.",
            "Reporting by The Guardian revealed that the minister had misled parliament.",
            "Fotos von Demonstranten kursieren im Netz.",
            "He contributed to the report on climate change.",
            "Research by Harvard scientists shows that sleep matters.",
            "Die Quelle des Flusses liegt in den Alpen.",
            "Folgen Sie dem Geld, sagt der Ermittler.",
            "BILD: Herr Merz, warum haben Sie das gemacht?",
            "Laut dpa",
            "Mit Informationen aus Ermittlerkreisen konnte die Polizei den Täter fassen.",
            "Die Studie erschien im Fachblatt Nature und wurde zuerst von Kollegen geprüft.",
        ).forEach { assertFalse(it, ArticleCleaner.isSourceLine(it)) }
    }

    @Test
    fun `the byline on its own line is a credit, a sentence that starts with Von is not`() {
        listOf("Von Max Mustermann, Berlin", "By The Associated Press", "By Jane Doe and John Roe", "Par Jean Dupont").forEach {
            assertTrue(it, ArticleCleaner.isAuthorLine(it))
        }
        listOf("Von Berlin nach Paris", "Von Montag an gilt die neue Regel.", "By the numbers", "Von Amts wegen").forEach {
            assertFalse(it, ArticleCleaner.isAuthorLine(it))
        }
        // As a paragraph it goes; a heading in title case ("By The Numbers") is not read as a byline.
        val long = "Die Landesregierung will die Grundsteuer im kommenden Jahr nicht erhöhen, obwohl mehrere Kommunen das gefordert hatten, um ihre " +
            "Haushalte auszugleichen, wie der Finanzminister am Dienstag sagte."
        val more = "Insgesamt fehlen den Städten und Gemeinden nach eigenen Angaben rund 800 Millionen Euro."
        val page = listOf(p(long), h("By The Numbers"), p(more), p("Von Max Mustermann, Berlin"))
        assertEquals(listOf(long, "By The Numbers", more), texts(ArticleCleaner.clean(page, "Grundsteuer bleibt")))
    }

    @Test
    fun `agency lines are nothing but agencies and initials`() {
        assertTrue(ArticleCleaner.isAgencyLine("dpa"))
        assertTrue(ArticleCleaner.isAgencyLine("(AFP)"))
        assertTrue(ArticleCleaner.isAgencyLine("sd/dpa/AFP"))
        assertTrue(ArticleCleaner.isAgencyLine("dpa, Reuters und AP"))
        assertFalse(ArticleCleaner.isAgencyLine("USA"))
        assertFalse(ArticleCleaner.isAgencyLine("SPD/AfD"))
        assertFalse(ArticleCleaner.isAgencyLine("Laut dpa"))
    }

    @Test
    fun `labels of link and source boxes`() {
        listOf("Mehr zum Thema", "Mehr zum Thema:", "Quellen", "Das könnte Sie auch interessieren", "Related coverage", "Sources", "À lire aussi", "Sur le même sujet")
            .forEach { assertTrue(it, ArticleCleaner.isSectionLabel(it)) }
        listOf("Empfehlungen", "Weitere Informationen", "Mehr zum Thema Grippe lesen Sie in unserem Dossier, das wir laufend aktualisieren.")
            .forEach { assertFalse(it, ArticleCleaner.isSectionLabel(it)) }
        assertTrue(ArticleCleaner.isRelatedLink("À lire aussi : Le gouvernement prépare un nouveau budget"))
        assertTrue(ArticleCleaner.isRelatedLink("Das könnte Sie auch interessieren: Neubau bricht ein"))
    }

    @Test
    fun `credits inside a list go, the list keeps its real entries`() {
        val long = "Die Stadt hat für den Winter einen Notfallplan vorgelegt, der Wärmestuben, zusätzliche Busse und längere Öffnungszeiten der Bibliotheken vorsieht."
        val cleaned = ArticleCleaner.clean(listOf(p(long), list("Wärmestuben in allen Bezirken", "Busse alle zehn Minuten", "Quelle: Stadtverwaltung")), "Notfallplan für den Winter")
        assertEquals(listOf(long, "Wärmestuben in allen Bezirken | Busse alle zehn Minuten"), texts(cleaned))
    }

    @Test
    fun `what is left of a gallery goes`() {
        listOf("Bild 3 von 12", "3 / 12", "Alle Bilder anzeigen", "Zur Galerie", "Fotostrecke: Die Bilder des Tages", "Voir le diaporama", "View gallery")
            .forEach { assertTrue(it, ArticleCleaner.isMediaNoise(it)) }
        assertFalse(ArticleCleaner.isMediaNoise("Galerie Neue Meister zeigt ab Freitag Werke von Caspar David Friedrich."))
    }
}

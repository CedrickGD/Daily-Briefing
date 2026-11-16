package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent

/**
 * Removes what a reader would see twice or does not want to see at all. Found on live pages
 * (see `ArticleLiveEvaluation`):
 *
 * - the headline again as the first block, often with its kicker ("US-Zwischenwahl Demokraten
 *   erobern …" under "US-Zwischenwahl: Demokraten erobern …"),
 * - meta lines before the text: "Stand: 05.11.2026 • 09:03 Uhr", a bare date, "Von …", "Lesezeit:
 *   5 Minuten", author boxes with their avatar, breadcrumbs,
 * - the teaser twice: once above the text (from the feed or `og:description`) and once as the
 *   lead paragraph – also when the feed cut it with "…", when it carries a dateline, or when the
 *   feed teaser is the standfirst plus the first paragraphs (The Guardian),
 * - the lead picture twice: as the reader's hero and as the first picture in the text,
 * - pull quotes that repeat a sentence of the text, with their attribution line,
 * - the same paragraph twice, the same picture twice, a caption that repeats the alt text.
 *
 * And in the survey of 15.11.2026 (`ArticleDuplicateSurvey`, 132 readable pages, 35 with a repeat):
 *
 * - the hero picture again far below the lead (after the byline, as a video still), in a
 *   rendition the URL rules did not know,
 * - the headline and standfirst again behind a box the page keeps above its own headline
 *   (ANSA's subscription offer), in a breadcrumb, as another version of the headline,
 * - a teaser that is the headline, that ends in the feed's "The post … appeared first on …",
 *   that is a heading of the text or the first sentences of its paragraphs,
 * - alt texts printed although the caption, a paragraph next to the picture or the headline
 *   says the same; captions and credits set as paragraphs; pull quotes set as paragraphs.
 *
 * And on the emulator on 15.11.2026 (19 articles read in the app):
 *
 * - the placeholder a page shows instead of an embed until the reader consents (t3n's video
 *   player with its provider's name three times, T-Online's Instagram post),
 * - "show our articles first on Google" (t3n, WELT; Sportschau with a whole box at the end),
 * - what taz hangs under its text after "Inhaltlichen Fehler melden": teasers for other articles
 *   and a subscription offer – and above it the author's box with a bare name as alt text.
 *
 * And what the owner does not want to read at all (16.11.2026: "short, clean, one picture, no
 * source clutter", like Google News or Perplexity):
 *
 * - sources and credits, in German, English and French: lists of sources ("Quellen", "Sources"),
 *   "Quelle: dpa", "Mit Material von dpa", "jdl/dpa", "Avec AFP", "Mitarbeit: …", "Reporting by
 *   …; Editing by …", "… contributed to this report", photo credits ("Foto: …", "Bild: …", "© …",
 *   "AP Photo/…"), the byline as a line of its own,
 * - boxes of other articles ("Mehr zum Thema", "Related", "À lire aussi") with their links,
 *   "Dieser Artikel erschien zuerst …", newsletter and social prompts, what is left of a gallery,
 * - every picture but one ([arrange]) – and with them their captions and alt texts.
 *
 * [clean] works on what the page itself says (stored in the cache); [arrange] on what the
 * reader shows around it (the feed's headline, teaser and picture).
 */
internal object ArticleCleaner {

    /** Blocks before the text proper: at most this many, and only up to the first long paragraph. */
    private const val LEAD_BLOCKS = 12
    private const val LONG_PARAGRAPH_WORDS = 25
    private const val NOISE_MAX_WORDS = 30
    private const val PROMOTE_MAX_WORDS = 80
    private const val TEASER_CANDIDATES = 5
    private const val TAIL_MAX_WORDS = 20
    private const val SNIPPET_WORDS = 4
    private const val CAPTION_LINE_WORDS = 30
    private const val AUTHOR_TAIL_BLOCKS = 5
    private const val CONSENT_TAIL_SHARE = 0.35
    private const val HEADLINE_SEARCH = 30
    private const val ALT_NEIGHBOURS = 2
    private const val ECHO_MIN_WORDS = 8
    private const val ECHO_GAIN_WORDS = 4
    private const val HEADING_ECHO_BLOCKS = 3
    private const val HEADING_ECHO_EXTRA = 4
    private const val BARE_CREDIT_WORDS = 8
    private const val TEASER_REST_WORDS = 8
    private const val DATELINE_WORDS = 7
    private val YEAR = Regex("\\b(19|20)\\d{2}\\b")

    /** "Follow" boxes of a newsroom's account system (The Verge): one per author and topic of the story. */
    private val FOLLOW_BOX = Regex("(?i)posts from this (author|topic) will be added to your|follow topics and authors from this story")

    /** A caption that carries its own switch: "… Nina Riggio for NPR hide caption", "Show Caption Hide Caption …" (a video's). */
    private val CAPTION_TOGGLE = Regex("(?is)^show caption hide caption\\b.*$|\\s*\\bhide caption\\b.{0,60}$")

    /**
     * A photo credit and nothing else: a name and the newsroom or agency it worked for ("Bree Lamb
     * for NPR", "Gerald Herbert/AP"). Only with a known one – "Tips for Parents" is a subheading.
     */
    private val BARE_CREDIT = Regex(
        "^\\p{Lu}[\\p{L}.'’-]*(\\s\\p{Lu}[\\p{L}.'’-]*){0,3}(\\sfor\\s|\\s?/\\s?)(The\\s)?(AP|AFP|dpa|Reuters|Getty Images|EPA|NPR|ANSA|IMAGO|imago|picture alliance|Bloomberg|" +
            "KFF Health News|ProPublica|New York Times|Washington Post|Wall Street Journal|Guardian|Texas Tribune|NBC News|ABC News|CBS News|CNN|TIME|WIRED)( via .{1,30})?$",
    )

    /** Alt texts that describe nothing: a player's label, a file name, a browser notice. */
    private val GENERIC_ALT = Regex(
        "(?i)^(video ?player|video|audio|player|bild|image|img|foto|photo|picture|grafik|logo|icon|thumbnail|placeholder|teaser(bild)?|" +
            "artikelbild|titelbild|beitragsbild|symbolbild|cover|untitled|null|undefined)$|browser does not support|\\.(jpe?g|png|webp|gif|avif)$",
    )
    private const val TITLE_RUN_WORDS = 5

    /**
     * The word that opens a date line: "Stand: …", "Veröffentlicht am 5. November 2026",
     * "Updated 2 hours ago". Followed by a colon, or by nothing but the date (see [isMetaLine]) –
     * "Stand heute sind 23 Menschen gestorben." is the text's first sentence.
     */
    private val META_WORD = Regex("(?i)^(zuletzt aktualisiert|aktualisiert|stand|veröffentlicht|erschienen|erstellt|last updated|updated|first published|published|posted|modified)\\b")

    /** After the date a separator may lead on to other meta ("— 3 minute read", "| Von …"). */
    private val META_SEPARATOR = Regex("\\s[•|—–·]\\s|\\s-\\s")
    private val META_TOKEN_SPLIT = Regex("[\\s,]+")

    /** A token of a date or time: "05.11.2026", "09:03", "5.", "2026", "11/05/2026". */
    private val DATE_TOKEN = Regex("\\d{1,4}([./:-]\\d{1,4}){0,2}[.,]?")
    private val MONTHS = Regex(
        "(?i)(jan|januar|january|feb|februar|february|mär|märz|mar|march|apr|april|mai|may|jun|juni|june|jul|juli|july|aug|august|" +
            "sep|sept|september|okt|oktober|oct|october|nov|november|dez|dezember|dec|december)\\.?",
    )
    private val WEEKDAYS = Regex(
        "(?i)(mo|di|mi|do|fr|sa|so|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|" +
            "mon|tue|tues|wed|thu|thurs|fri|sat|sun|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\.?",
    )

    /** Small words of a date line: "am", "um … Uhr", "vor 2 Stunden", "2 hours ago", time zones. */
    private val META_FILLER = setOf(
        "am", "um", "uhr", "vor", "seit", "on", "at", "ago", "heute", "gestern", "today", "yesterday", "und", "and",
        "minute", "minuten", "minutes", "min", "stunde", "stunden", "hour", "hours", "h", "tag", "tagen", "day", "days",
        "am", "pm", "a.m.", "p.m.", "mez", "mesz", "cet", "cest", "utc", "gmt", "et", "est", "edt", "pt", "pst", "pdt", "bst",
    )

    /** Links of an author box: "Posts from this author", "Mehr Artikel von …". */
    private val AUTHOR_LINKS = Regex("(?i)(posts from this author|see all by|alle artikel von|mehr artikel von|mehr von (diesem|dieser) autor|folgen sie (dem autor|der autorin)|follow (the )?author)")

    private val NOISE = listOf(
        Regex("(?i)\\b(lesezeit|lesedauer|reading time|\\d+\\s*min(\\.|ute|uten|s)?\\s+(read|lesen|lesezeit)|minute read|minutes read)\\b"),
        Regex("^\\d{1,2}\\.\\s?\\d{1,2}\\.\\s?(\\d{2}|\\d{4})\\b"),
        Regex("(?i)^\\d{1,2}\\.\\s+(januar|februar|märz|april|mai|juni|juli|august|september|oktober|november|dezember)\\s+\\d{4}"),
        Regex("(?i)^(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?\\s+\\d{1,2},\\s+\\d{4}"),
        Regex("(?i)^\\d{1,2}\\s+(january|february|march|april|may|june|july|august|september|october|november|december)\\s+\\d{4}"),
        AUTHOR_LINKS,
        Regex("(?i)^(artikel anhören|anhören|audio|hören sie|listen|listen to (this )?(article|story)|vorlesen|teilen|share|drucken|print|kommentare|comments|merken|save)\\b.{0,40}$"),
        // Audio players: "00:00 1.0x 08:58", "Narrated by …", "Gelesen von …".
        Regex("(?i)^(\\d{1,2}:\\d{2}(\\s|$)|narrated by|read by|gelesen von|vorgelesen von|audio version)"),
    )

    /**
     * "Read More: Inside the …", "Lesen Sie auch: …", "À lire aussi : …", "Watch: …": links to
     * other articles (or videos) between paragraphs.
     */
    private val RELATED_LINE = Regex(
        "(?i)^(read more|more|related|related coverage|see also|also read|further reading|recommended|watch|" +
            "lesen sie auch|lesen sie mehr|mehr zum thema|mehr dazu|auch interessant|das könnte sie auch interessieren|passend dazu|weiterlesen|zum weiterlesen|" +
            "[aàÀ] lire aussi|lire aussi|lire [ée]galement|voir aussi|[aàÀ] voir aussi|[aàÀ] voir|[aàÀ] [ée]couter|sur le m[êe]me sujet)\\s*[:|»›]\\s*\\S.{2,200}$",
    )
    private const val RELATED_MAX_WORDS = 20
    private val BYLINE = Regex("^(?i:von|by|text|interview|ein kommentar von|eine analyse von|ein gastbeitrag von|aus)[:\\s]+\\p{Lu}")
    private val SENTENCE_END = Regex("[.!?:\"“”»«]$")
    private val BREADCRUMB = Regex("(?i)^(startseite|home|homepage|aktuelle seite|news|nachrichten)\\b|^(›|»|>|/)")
    private const val CREDIT_MAX_WORDS = 45
    private const val CAPTION_PARTS = 3
    private const val CAPTION_PART_WORDS = 25
    private const val MEDIA_MAX_WORDS = 15
    private val MEDIA_NOISE = Regex(
        "(?i)((audio|video|podcast) (starten|abspielen|anhören)|abbrechen mit escape|verfügbar bis \\d{1,2}\\.\\d{1,2}\\.\\d{2,4}|" +
            "^\\d+\\s*(sek|min)\\.?$|^(play|watch) (video|now)|video duration|^(video|audio):?\\s*\\d+:\\d{2}|bildergalerie mit \\d+ bildern|" +
            "^\\d+ bilder$|zur bildergalerie|^(galerie|gallery)\\s*\\(\\d+|^(audio|video|podcast)\\s*:\\s*\\S.*\\(\\d+\\s*(min|sek|sec)\\.?\\)$|" +
            // The reader shows one picture: what is left of a gallery goes too ("Bild 3 von 12", "Alle Bilder anzeigen", "Voir le diaporama").
            "^(bild|foto|image|photo)\\s+\\d+\\s*(von|of|sur|/)\\s*\\d+$|^\\d+\\s*/\\s*\\d+$|^(zur|zu den|alle) (galerie|fotos|bilder)\\b|^(alle )?(bilder|fotos) anzeigen$|" +
            "^(weitere|mehr) (bilder|fotos)$|^(bildergalerie|fotostrecke|fotogalerie|photo gallery|galerie photos?|diaporama)\\b.{0,30}$|^(view|see|open) (the )?(gallery|all \\d* ?photos)\\b|" +
            "^(voir|ouvrir) (la galerie|le diaporama|les photos)\\b)",
    )
    private val MORE_WORD = Regex("(?i)(mehr|more|weiterlesen|read more|alle artikel|zum artikel|zur übersicht|›|»)")
    private val CREDIT_TAIL = Regex("(?i)(\\(?(foto|fotos|bild|bilder|photo|image|bildquelle|credit)\\s*:\\s*[^:]{2,80}|photo by [^:]{3,80}|\\(foto: [^)]{2,60}\\))\\.?$")
    private const val AGENCY_CREDIT_WORDS = 12
    private val AGENCY_TAIL = Regex("(?i)(getty images|reuters|afp|dpa|ap photo|associated press|imago( images)?|picture alliance|picture-alliance|shutterstock|istock(photo)?|epa(-efe)?|keystone|nurphoto|bloomberg|anadolu)\\)?\\.?$")
    private val CREDIT_MARK = Regex("[|/©]|\\bvia\\b")
    private val CREDIT_HEAD = Regex("(?i)^(image|bild|foto|photo)\\s*[—–:-]\\s+\\S")
    private val TAIL_NOISE = Regex(
        "(?i)^(stand:\\s*\\d|veröffentlicht nach den redaktionellen standards|mehr informationen finden sie in unseren richtlinien|" +
            "dieser (artikel|beitrag|text) erschien zuerst|this (article|story) (was )?(originally|first) (appeared|published)|" +
            "mehr zum thema|lesen sie (auch|mehr)|more on this story|related:|diesen artikel teilen|artikel teilen|" +
            "teilen sie diesen artikel|share this (article|story)|(teilen|share)$|\\d+\\s+(comments?|kommentare?)$|" +
            "(vorherige|nächste) (meldung|artikel|seite)|(previous|next) (article|story|post)|" +
            "(keine meldungen mehr verpassen|newsletter abonnieren|sign up for (our|the) newsletter|subscribe to (our|the) newsletter))",
    )

    /**
     * The author's portrait opens the bio box at the end: "Photo of Kyle Orland", "Porträt von …".
     * Group 2 is the name; "Autorenbild"/"headshot" say "author" by themselves.
     */
    private val AUTHOR_PHOTO = Regex("(?i)^(photo|picture|portrait|headshot|foto|bild|porträt|autorenbild|autorenfoto) (of|von|:)\\s+(.+)$")
    private val AUTHOR_WORD = Regex("(?i)^(headshot|autorenbild|autorenfoto)\\b")
    private const val NAME_MAX_WORDS = 4
    private const val ROLE_LINE_WORDS = 8

    /** Lower-case parts of names: "Ursula von der Leyen", "Ludwig van Beethoven". */
    private val NAME_PARTICLES = setOf("von", "van", "der", "den", "de", "del", "della", "di", "da", "du", "la", "le", "zu", "ten", "ter", "al", "bin", "ibn", "dos", "das")

    /**
     * The consent dialog some pages carry in their article container: button lines ("Weiter
     * mit Werbung", "Datenschutzoptionen …") and bare buttons ("Zustimmen", "Accept all") – a
     * line of at most [CONSENT_LINE_WORDS] words without a sentence ending, never a sentence that
     * opens with the word ("Zustimmen muss nun noch der Bundesrat.").
     */
    private val CONSENT_LINE = Regex("(?i)^(weiter mit werbung|alle (cookies )?akzeptieren|accept all cookies|werbefrei (mit|lesen)|datenschutzoptionen|cookie[- ]einstellungen|cookie settings|privacy settings|manage (cookies|consent))\\b")
    private val CONSENT_BUTTON = Regex("(?i)^(zustimmen|akzeptieren|einverstanden|accept|accept all|agree|i agree)[.!]?$")
    private const val CONSENT_LINE_WORDS = 6
    private val SENTENCE_STOP = Regex("[.!?]$")

    /**
     * The placeholder a page shows where an embed (a video player, a post) waits for the reader's
     * consent – lines that speak to the reader about "external content", never about the story:
     * "Empfohlene redaktionelle Inhalte", "Hier findest du externe Inhalte von …", "Mit dem Klick
     * auf "Inhalte anzeigen" erklärst du dich einverstanden …", "Dabei können personenbezogene
     * Daten an … übermittelt werden", "… deine Consent-Einstellungen …" (t3n); "Wir benötigen Ihre
     * Einwilligung, um den von unserer Redaktion eingebundenen Instagram-Inhalt anzuzeigen",
     * "Instagram-Inhalte immer anzeigen" (T-Online).
     */
    private val EMBED_CONSENT = Regex(
        "(?i)^empfohlene[rn]? (redaktionelle[rn]? |externe[rn]? )?inhalte?$|" +
            "\\b(hier|an dieser stelle) (findest du|finden sie)\\b.{0,30}\\bexterne[nr]? inhalte?\\b|" +
            "\\bklick auf .{0,3}inhalte anzeigen\\b|" +
            "\\b(dabei|damit) können personenbezogene daten an\\b|" +
            "\\b(deine|ihre) consent-einstellungen\\b|" +
            "^hinweis zum datenschutz$|" +
            "^leider ist etwas schief ?gelaufen\\W{0,5}$|" +
            "\\bbenötigen ihre einwilligung, um den von unserer redaktion eingebundenen\\b|" +
            "^\\S+-inhalte? immer anzeigen$",
    )
    private const val EMBED_MAX_WORDS = 60

    /**
     * "Show our articles first on Google": "Als bevorzugte Quelle auf Google hinzufügen" (t3n),
     * "Klicken Sie hier, um sich Artikel von WELT in der Google-Suche bevorzugt anzeigen zu
     * lassen." (WELT), "Unsere Artikel gezielt bei Google anzeigen lassen", "SWR Sport als
     * bevorzugte Quelle einstellen" (Sportschau). Not a sentence about a preferred source of
     * energy, nor one about what Google Maps can show.
     */
    private val SOURCE_PROMO = Regex(
        "(?i)\\bals bevorzugte quelle\\b.{0,40}\\b(hinzufügen|einstellen|festlegen|auswählen)\\b|" +
            "\\b(artikel|inhalte|beiträge|nachrichten)\\b.{0,40}\\bgoogle(-suche)?\\b.{0,20}\\banzeigen (zu )?lassen\\b",
    )
    private const val SOURCE_PROMO_WORDS = 20

    /** "Inhaltlichen Fehler melden" (taz), "Fehler gefunden?": the link under an article's last line. */
    private val ERROR_REPORT = Regex("(?i)^(einen )?(inhaltlichen )?fehler (im (text|artikel|beitrag) )?(melden|gefunden)\\W{0,2}$")
    private const val ERROR_REPORT_WORDS = 6

    /** An error-report link closes the article only once this share of the words stands before it. */
    private const val END_MARK_MIN_SHARE = 0.3
    private val TEASER_TAIL = Regex("(?i)\\s*(continue reading|read more|weiterlesen|mehr lesen|lesen sie mehr|\\[…]|\\[\\.\\.\\.])\\s*(\\.{3}|…)?\\s*$")

    /**
     * What WordPress appends to every feed teaser: "The post <headline> appeared first on <site>.",
     * "Der Artikel <headline> erschien zuerst auf <site>." – the headline a second time.
     */
    private val FEED_TAIL = Regex(
        "(?is)\\s*(the post\\b.{1,300}?\\b(appeared first on|first appeared on)\\b|der (artikel|beitrag)\\b.{1,300}?\\berschien zuerst (auf|bei)\\b|" +
            "l[’']article\\b.{1,300}?\\best apparu en premier sur\\b|la entrada\\b.{1,300}?\\bse publicó primero en\\b|" +
            "l[’']articolo\\b.{1,300}?\\b(proviene da|sembra essere il primo su)\\b).{0,80}$",
    )

    // ------------------------------------------------------------------------------- sources

    /**
     * The label of a box that lists the sources or other articles, alone on its line (a heading or
     * a short paragraph): "Quellen", "Mehr zum Thema", "Das könnte Sie auch interessieren",
     * "Sources", "Related coverage", "À lire aussi", "Sur le même sujet". Not "Empfehlungen" or
     * "Weitere Informationen" alone – an article may well have a section of that name.
     */
    private val SECTION_LABEL = Regex(
        "(?i)^(quellen(angaben|verzeichnis)?|quellen und (links|literatur)|literatur(hinweise|verzeichnis)?|weiterführende links|links zum (thema|artikel)|" +
            "mehr zu(m| diesem) thema|mehr dazu|zum thema|passend (zum thema|dazu)|weitere artikel( zum thema)?|weitere (meldungen|nachrichten|beiträge) (zum thema|dazu)|" +
            "das könnte (sie|dich) auch interessieren|auch interessant|ebenfalls interessant|lesen sie auch|lesen sie mehr|lese-?tipps?|unsere empfehlungen|" +
            "sources?|sources and (references|further reading)|references|further reading|related|related (articles?|stories|coverage|content|links|reading|topics)|" +
            "more on this (story|topic)|read more|recommended( (reading|stories|for you))?|you (may|might) also like|" +
            "sources et (liens|r[ée]f[ée]rences)|r[éÉ]f[ée]rences|[aàÀ] lire aussi|lire [ée]galement|[aàÀ] lire [ée]galement|voir aussi|[aàÀ] voir aussi|sur le m[êe]me sujet|" +
            "dans la même rubrique|pour aller plus loin|nos articles sur le (sujet|même thème)|ces articles peuvent vous intéresser|vous aimerez aussi)\\s*[:.…]?$",
    )

    /** Labels of a list of sources (every short line after them is a source, sentence or not). */
    private val SOURCE_LABEL = Regex("(?i)^(quellen|literatur|weiterführende links|sources?|references|r[éÉ]f[ée]rences)")

    /** A source section's line: a link title or a source. Longer paragraphs are text again. */
    private const val SECTION_LINE_WORDS = 25
    private const val SECTION_LABEL_WORDS = 8

    /** A newsroom's name on a line of its own ("tagesschau.de", "Süddeutsche Zeitung") is at most this long. */
    private const val SOURCE_NAME_WORDS = 6

    /** "Quelle: dpa", "Quellen: Destatis, Eurostat", "Sources: …", "Source : AFP", "Bildquelle: …". */
    private val SOURCE_LINE = Regex("(?i)^\\(?(quellen?|quellenangaben?|bildquellen?|textquelle|datenquelle|sources?|source\\(s\\)|data sources?)\\s*:")
    private const val SOURCE_LINE_WORDS = 40

    /**
     * A photo credit: "Foto: Kay Nietfeld/dpa", "Bild: IMAGO/…", "Titelbild: …", "Photo: Getty
     * Images", "Pictured: …", "Crédit photo : AFP". Short – an interview question that opens with
     * the newspaper's name ("BILD: Herr Merz, warum …?") is longer and asks.
     */
    private val PHOTO_CREDIT = Regex(
        "(?i)^\\(?(fotos?|bilder?|bildquellen?|bildrechte|bildnachweis|fotonachweis|titelbild|titelfoto|aufmacher(bild|foto)|symbolbild|foto oben|bild oben|" +
            "fotograf(in)?|photos?|photograph(y)?|images?|pictures?|pictured|(photo|image|picture) credits?|credits?|(top|header|lead|main|featured|cover) (image|photo)|" +
            "cr[ée]dits?|cr[ée]dits? photos?|cr[ée]dit image|photo d['’]illustration|illustrations?|grafik|infografik|infographie)\\s*:\\s*\\S",
    )
    private const val PHOTO_CREDIT_WORDS = 15

    /**
     * Who wrote, reported, translated or filmed it: "Mitarbeit: …", "Text: …", "Redaktion: …",
     * "Recherche: …", "Übersetzung: …", "Editing: …", "Rédaction : …", "Traduction : …". Never
     * "Research:" or "Production:", which open explanatory paragraphs in English.
     */
    private val STAFF_CREDIT = Regex(
        "(?i)^\\(?(mitarbeit|redaktionelle mitarbeit|recherche|redaktion|text|texte|text und fotos?|texte et photos?|autor(in|en|innen)?|protokoll|bearbeitung|" +
            "[üÜ]bersetzung|layout|kamera|schnitt|editing|reporting|writing|contributors?|contributing (writers?|reporters?|editors?)|" +
            "r[ée]daction|contribution|traduction|[éÉ]dition|montage|vid[ée]o)\\s*:\\s*\\S",
    )
    private const val STAFF_CREDIT_WORDS = 30

    /** Reuters and friends, unmistakable: "Additional reporting by Jane Doe", "With reporting by …". */
    private val REPORTING_CREDIT = Regex("(?i)^\\(?(additional reporting|with (additional )?reporting|reporting and writing) by\\s+\\p{Lu}")

    /**
     * "Reporting by Jane Doe in Washington; Editing by Bill Smith" – a credit with its semicolon,
     * its editor, its brackets or without a full stop; "Reporting by The Guardian revealed …." is
     * a sentence.
     */
    private val REPORTING_BY = Regex("(?i)^\\(?(reporting|writing|editing) by\\s+\\p{Lu}")
    private val REPORTING_MARKS = Regex("(?i);|\\bediting by\\b|^\\(")
    private const val REPORTING_BY_WORDS = 60

    /**
     * A short credit in words, without a full stop: "Text von Max Muster", "Interview by …", "Ein
     * Beitrag von …", "Propos recueillis par …", "Un article de …". German capitalises its nouns:
     * "Fotos von Demonstranten kursieren im Netz." is a sentence, and it ends like one.
     */
    private val CREDIT_PHRASE = Regex(
        "(?i)^\\(?((text|words|interview|photos?|fotos?|illustrations?) (by|von)\\s+\\p{Lu}|propos recueillis par\\s+\\p{Lu}|" +
            "(un |une )?(article|reportage|enquête|chronique) (de|d['’])\\s*\\p{Lu}|" +
            "(ein|eine) (beitrag|artikel|text|kommentar|gastbeitrag|analyse|reportage|recherche|interview) (von|der|des)\\s+\\p{Lu})",
    )
    private const val CREDIT_PHRASE_WORDS = 12

    /** The byline as a line of its own, anywhere: "Von Max Muster, Berlin", "By The Associated Press", "Par Jean Dupont". */
    private val AUTHOR_PREFIX = Regex("(?i)^(von|by|par)\\s+")
    private val NAME_SPLIT = Regex("\\s*(,|&|\\bund\\b|\\band\\b|\\bet\\b)\\s*")
    private const val AUTHOR_LINE_WORDS = 10

    /** A newsroom by its web address in a byline ("Von index-radar.de", "www.futurezone.de") – not "z.B." or "2.5". */
    private val WEB_NAME = Regex("^(www\\.)?[a-z0-9][a-z0-9-]*(\\.[a-z0-9-]+)*\\.[a-z]{2,6}$")

    /**
     * "Mit Material von dpa", "Mit Agenturmaterial", "Unter Verwendung von Material der
     * Nachrichtenagenturen", "With reporting from Reuters" – never "Nach Angaben der dpa …", which
     * opens a sentence of the story.
     */
    private val AGENCY_MATERIAL = Regex(
        "(?i)^\\(?((mit|unter verwendung von) (material|agenturmaterial)\\b|with (material|reporting|additional reporting|information) from\\b)",
    )

    /**
     * The agency closes a French credit: "Avec AFP", "Le Figaro avec AFP", "Par Jean Dupont avec
     * l'AFP", "La rédaction avec AFP". What stands before "avec" must be a name, not a sentence:
     * "Il a parlé avec l'AFP." is one.
     */
    private val AVEC_AGENCY = Regex("(?i)\\s*\\bavec (l['’])?(afp|reuters|ap|agences|belga|ats|apa)\\)?\\.?$")
    private val NEWSROOM_LEAD = Regex("(?i)^(la )?r[ée]daction\\b.*$")
    private const val AGENCY_LINE_WORDS = 15
    private const val AVEC_AGENCY_WORDS = 10

    /** An agency named in a credit (lower case). */
    private val AGENCY_WORD = Regex(
        "(?i)\\b(dpa|afp|ap|reuters|kna|epd|sid|apa|ddp|dapd|ansa|efe|belga|ats|sda|anp|bloomberg|associated press|agence france-presse|" +
            "nachrichtenagentur(en)?|agentur(en)?|agenturmaterial|agencies|agences|wire services?)\\b",
    )

    /** News and photo agencies a credit line may consist of ("jdl/dpa", "(AFP)", "dpa, Reuters"), lower case. */
    private val AGENCIES = setOf(
        "dpa", "dpa-afx", "dpa-infocom", "afx", "afp", "ap", "reuters", "kna", "epd", "sid", "apa", "ddp", "dapd", "ots", "ansa", "efe", "belga", "ats",
        "sda", "keystone-sda", "keystone", "anp", "pap", "tass", "interfax", "xinhua", "kyodo", "yonhap", "pa", "pa media", "bloomberg", "upi",
        "associated press", "agence france-presse", "agence france presse", "nachrichtenagenturen", "agenturen", "agencies", "agences", "wire services",
        "getty images", "imago", "imago images", "picture alliance", "picture-alliance", "shutterstock", "istock", "epa", "epa-efe",
    )

    /** An editor's or author's initials next to the agency: "jdl", "mau", "AFP". */
    private val INITIALS = Regex("^[a-zäöü]{2,4}$")
    private val AGENCY_SPLIT = Regex("(?i)\\s*(/|,|;|&|\\+|\\||\\bund\\b|\\band\\b|\\bet\\b)\\s*")
    private val AGENCY_LEAD = Regex("(?i)^(mit|with|avec)\\s+")
    private const val AGENCY_PARTS = 6
    private const val AGENCY_LINE_CHARS = 80

    /** A picture credit made of a photographer and an agency: "AP Photo/Evan Vucci", "picture alliance / dpa | Kay Nietfeld". */
    private val PHOTO_AGENCY = Regex(
        "(?i)\\b(getty images|reuters|afp|dpa|ap photo|ap|associated press|imago( images)?|picture[ -]alliance|shutterstock|istock(photo)?|epa(-efe)?|keystone|" +
            "nurphoto|bloomberg|anadolu|sipa|abaca|hans lucas|maxppp|belga|ansa|efe|zuma( press)?|alamy|xinhua|sopa images|lightrocket|ddp|fotofinder|photothek|" +
            "ullstein ?bild|action press|eibner|sven simon|kyodo|yonhap)\\b",
    )
    private val CREDIT_SEPARATOR = Regex("[|/]")
    private const val PHOTO_AGENCY_WORDS = 10

    /**
     * A note that someone else contributed: "The Associated Press contributed to this report.",
     * "AP writer Jane Doe in Paris contributed.", "An diesem Artikel hat … mitgewirkt.", "… a
     * contribué à cet article."
     */
    private val CONTRIBUTED = Regex(
        "(?i)\\bcontributed( reporting)? to this (report|story|article)\\b|" +
            "^an diesem (artikel|text|beitrag) ha(t|ben)\\b.{1,80}\\bmitgewirkt\\b|^mitgearbeitet ha(t|ben)\\b|" +
            "\\b(a|ont) (contribué|participé) à (cet|cette) (article|enquête|reportage)\\b",
    )

    /** "… contributed." or "… contributed to the report." at the end – a credit only when it names writers or an agency. */
    private val CONTRIBUTED_END = Regex("(?i)\\bcontributed( reporting)?( to (the|this) (report|story|article))?\\.?\\)?$")
    private val CONTRIBUTOR = Regex("(?i)\\b(writers?|reporters?|correspondents?|staff|associated press|reuters|bloomberg|afp)\\b")
    private const val CONTRIBUTED_WORDS = 30

    /** "© dpa-infocom, dpa:261115-…", "Copyright 2026 The Associated Press. All rights reserved.", "Alle Rechte vorbehalten." */
    private val COPYRIGHT = Regex(
        "(?i)^(©|\\(c\\)|copyright\\s*(©|\\(c\\)|\\d{4}))|^(all rights reserved|alle rechte vorbehalten|tous droits réservés|todos los derechos reservados|tutti i diritti riservati)\\b|" +
            "^this (material|article|story) may not be (published|reproduced|redistributed)\\b",
    )
    private const val COPYRIGHT_WORDS = 40

    /**
     * Where the text appeared first: "Dieser Artikel erschien zuerst bei …", "Der Text ist zuerst in
     * … erschienen", "Zuerst erschienen auf …", "Das Original zu diesem Beitrag "…" stammt von
     * Index Radar." (FOCUS, BUNTE), "This article was originally published on The Conversation",
     * "This story first appeared in …", "A version of this story appeared in …", "Cet article a
     * été publié initialement dans …", "Article initialement paru sur …".
     */
    private val FIRST_APPEARED = Regex(
        "(?i)^(dieser|diese|der) (artikel|beitrag|text|kommentar|gastbeitrag|essay|interview)\\b(?=.{0,80}\\b(zuerst|erstmals|ursprünglich|zunächst)\\b)" +
            "(?=.{0,100}\\b(erschien|erschienen|veröffentlicht|publiziert)\\b)|^(zuerst|erstmals|ursprünglich|zunächst) (erschienen|veröffentlicht)\\b|" +
            "^das original (zu |von )?(diesem|dieses|dem) (beitrag|artikel|text)\\b.{0,200}\\bstammt (von|aus)\\b|" +
            "^this (article|story|piece|post|essay|column|op-ed|interview|report)\\b.{0,40}\\b((originally|first) (published|appeared|ran|posted)|republished|reprinted)\\b|" +
            "^(originally|first) (published|appeared|ran|posted) (on|in|at|by)\\b|^a version of this (article|story|piece)\\b|" +
            // After "é" a word boundary is not one on every JVM: "no letter follows" instead.
            "^(cet|cette|ce) (article|entretien|tribune|texte|reportage|[ée]ditorial)\\b.{0,60}\\b(publi[ée]e?|parue?)(?!\\p{L})|" +
            "^(article |texte )?(initialement|d['’]abord|originellement|pr[ée]c[ée]demment) (publi[ée]e?|parue?)(?!\\p{L})|" +
            "^publi[ée]e? (initialement|d['’]abord|[àa] l['’]origine) (sur|dans|par)\\b",
    )
    private const val FIRST_APPEARED_WORDS = 45

    /**
     * Asking the reader to subscribe, follow or install: an imperative at the start ("Abonnieren
     * Sie …", "Folgen Sie …", "Melde dich …", "Sign up …", "Abonnez-vous …", "Suivez …") with a
     * channel ([CHANNEL]), "Folgen Sie uns" / "follow us" / "suivez-nous" with a channel, or a
     * phrase that means only that ("unseren Newsletter", "WhatsApp-Kanal", "notre newsletter").
     */
    private val PROMPT_START = Regex(
        "(?i)^(jetzt |hier |gleich |kostenlos |einfach )?(abonnieren sie|abonniere|bestellen sie|melden sie sich|melde dich|registrieren sie sich|folgen sie|folge uns|" +
            "holen sie sich|laden sie|lade dir|sign up|subscribe|abonnez-vous|inscrivez-vous|suivez|retrouvez|téléchargez)\\b",
    )
    private val FOLLOW_US = Regex("(?i)\\b(folgen sie uns|folgt uns|folge uns|follow us|suivez-nous|retrouvez-nous)\\b")
    private val CHANNEL = Regex(
        "(?i)\\b(newsletters?|instagram|facebook|twitter|tiktok|youtube|whatsapp|telegram|threads|bluesky|linkedin|mastodon|google news|podcasts?|" +
            "push-?(mitteilung|nachricht|benachrichtigung)(en)?|app|e-?mail|postfach|kanal|channel|réseaux sociaux|notifications?)\\b",
    )
    private val PROMPT_PHRASE = Regex(
        "(?i)\\b(newsletter (abonnieren|bestellen)|zum newsletter anmelden|für (unseren|den) newsletter|unseren? (\\S+-)?newsletter|our newsletter|notre newsletter|" +
            "notre lettre d['’]information|whatsapp-kanal|whatsapp channel|sign up for our|download our app|(abonnez|inscrivez)-vous [àa] (notre|la|nos))\\b",
    )
    private const val PROMPT_WORDS = 40

    // ---------------------------------------------------------------------------------- page

    /** Cleans the blocks of a page whose headline is [title] and whose own teaser is [description]. */
    fun clean(blocks: List<ArticleBlock>, title: String, description: String = ""): List<ArticleBlock> {
        var out = dropLeadNoise(blocks, listOf(title), description)
        out = cutClosingBoxes(out)
        out = dropEmbedPlaceholders(out)
        out = out.filterNot { block ->
            val text = block.type == ArticleBlock.PARAGRAPH || block.type == ArticleBlock.HEADING
            (text && (isRelatedLink(block.text) || isMediaNoise(block.text) || FOLLOW_BOX.containsMatchIn(block.text) || isSourcePromo(block.text) || isErrorReport(block.text))) ||
                isLinkList(block) || (block.type == ArticleBlock.LIST && block.items.any { FOLLOW_BOX.containsMatchIn(it) })
        }
        out = dropPullQuotes(out)
        out = dropRepeatedText(out)
        out = dropRepeatedImages(out)
        out = creditsToCaptions(out)
        out = fixCaptions(out)
        out = settleAlts(out, listOf(title, description))
        // After the captions are put together: "Erneut nominiert: …" + "Bildquelle: IMAGO/…" under a picture is its caption, not text.
        out = dropSources(out)
        out = dropTailNoise(out)
        return dropOrphanCaptions(out)
    }

    /** The headline, meta lines, author boxes and breadcrumbs before the text proper. */
    private fun dropLeadNoise(all: List<ArticleBlock>, titles: List<String>, description: String): List<ArticleBlock> {
        val blocks = dropBeforeHeadline(all, titles)
        val long = firstLong(blocks, description)
        val lead = minOf(if (long < 0) blocks.size else long + 1, LEAD_BLOCKS, blocks.size)
        val dropped = BooleanArray(blocks.size)
        val noiseTexts = ArrayList<String>()
        for (i in 0 until lead) {
            val block = blocks[i]
            when (block.type) {
                ArticleBlock.HEADING, ArticleBlock.PARAGRAPH -> {
                    val text = block.text
                    when {
                        titles.any { isTitle(text, it) } -> dropped[i] = true
                        // Another version of the headline (the page's own, the feed's): they share most of their words in a row.
                        block.type == ArticleBlock.HEADING && (long < 0 || i < long) &&
                            titles.any { TextMatch.sharedRun(text, it) >= TITLE_RUN_WORDS } -> dropped[i] = true
                        // The first long paragraph is text, even when it opens with a dateline.
                        (i != long && isNoise(text)) || AUTHOR_LINKS.containsMatchIn(text) -> {
                            dropped[i] = true
                            noiseTexts += text
                        }
                        // The lead picture's caption, written as a paragraph, without its picture.
                        i != long && block.type == ArticleBlock.PARAGRAPH && isCredit(text) && blocks.getOrNull(i - 1)?.type != ArticleBlock.IMAGE -> dropped[i] = true
                        // "Podcasts 05.11.2026 Lesedauer ca. 1 Minute Drucken <title> <teaser>": a meta box.
                        description.isNotBlank() && titles.any { it.isNotBlank() && TextMatch.covers(text, it) } &&
                            TextMatch.covers(text, description) -> dropped[i] = true
                        // Before the text: "Expert comment", "Size", "Small Standard Large", an author's name – widget and kicker lines.
                        long >= 0 && i < long && block.type == ArticleBlock.PARAGRAPH && TextMatch.wordCount(text) <= SNIPPET_WORDS &&
                            !SENTENCE_END.containsMatchIn(text.trim()) -> dropped[i] = true
                        // A dateline in any language: "ROMA, 15 novembre 2026, 11:34", "Publié le 15/11/2026 à 12:30".
                        long >= 0 && i < long && block.type == ArticleBlock.PARAGRAPH && TextMatch.wordCount(text) <= DATELINE_WORDS &&
                            !SENTENCE_END.containsMatchIn(text.trim()) && YEAR.containsMatchIn(text) -> dropped[i] = true
                    }
                }
                ArticleBlock.LIST -> if (isBreadcrumb(block) || endsInTitle(block, titles) || (block.items.size == 1 && TextMatch.wordCount(block.items[0]) <= SNIPPET_WORDS)) dropped[i] = true
            }
        }
        // The caption line above a picture credit ("A Wendy's restaurant sign …" + "… | Getty Images") goes with it.
        for (i in 1 until lead) {
            val before = blocks[i - 1]
            if (dropped[i] && !dropped[i - 1] && i - 1 != long && before.type == ArticleBlock.PARAGRAPH && isCredit(blocks[i].text) &&
                TextMatch.wordCount(before.text) <= CAPTION_LINE_WORDS
            ) {
                dropped[i - 1] = true
            }
        }
        // An author's avatar sits next to the byline or date line: a picture with a name as alt text.
        for (i in 0 until lead) {
            val block = blocks[i]
            if (block.type != ArticleBlock.IMAGE || dropped[i]) continue
            val nextToNoise = (i > 0 && dropped[i - 1] && blocks[i - 1].type == ArticleBlock.PARAGRAPH) ||
                (i + 1 < blocks.size && dropped[i + 1] && blocks[i + 1].type == ArticleBlock.PARAGRAPH)
            val altWords = TextMatch.words(block.alt)
            val nameLike = altWords.size <= 3 || noiseTexts.any { TextMatch.normalize(it).contains(TextMatch.normalize(block.alt)) }
            if (nextToNoise && nameLike) dropped[i] = true
        }
        return blocks.filterIndexed { i, _ -> !dropped[i] }
    }

    /**
     * Some pages keep a box above their own headline inside the article's container – ANSA its
     * subscription offer ("Se hai scelto di non accettare i cookie …", a dozen blocks), others
     * a breadcrumb or a newsletter note. The headline as a heading among the first
     * [HEADLINE_SEARCH] blocks says where the article begins: the text before it goes (pictures
     * stay – a lead picture often sits above the headline), and so does the heading, which the
     * reader prints itself. Only the whole headline counts, not a part of it: a subheading may
     * well quote some of the headline's words further down.
     */
    private fun dropBeforeHeadline(blocks: List<ArticleBlock>, titles: List<String>): List<ArticleBlock> {
        val at = blocks.take(HEADLINE_SEARCH).indexOfFirst { block ->
            block.type == ArticleBlock.HEADING && titles.any { isWholeTitle(block.text, it) }
        }
        if (at <= 0) return blocks
        return blocks.filterIndexed { i, block -> i > at || (i < at && (block.type == ArticleBlock.IMAGE || block.type == ArticleBlock.CAPTION)) }
    }

    /** The headline itself, or the headline with its kicker – never just a part of it. */
    private fun isWholeTitle(text: String, title: String): Boolean {
        if (title.isBlank() || text.isBlank()) return false
        if (TextMatch.same(text, title)) return true
        val hw = TextMatch.words(title).size
        return hw >= 3 && TextMatch.covers(text, title) && TextMatch.words(text).size <= hw + 6
    }

    /** A breadcrumb that ends in the page's own headline: "rbb24 > Panorama > Wetter in Berlin …". */
    private fun endsInTitle(block: ArticleBlock, titles: List<String>): Boolean =
        block.items.size in 2..8 && block.items.dropLast(1).all { TextMatch.wordCount(it) <= SNIPPET_WORDS } &&
            titles.any { isWholeTitle(block.items.last(), it) }

    /**
     * The first long paragraph: where the text proper begins. The page's own teaser
     * ([description]) printed above the byline does not count – the text starts after it.
     */
    private fun firstLong(blocks: List<ArticleBlock>, description: String = ""): Int =
        blocks.indices.firstOrNull { i ->
            val block = blocks[i]
            block.type == ArticleBlock.PARAGRAPH && TextMatch.wordCount(block.text) >= LONG_PARAGRAPH_WORDS &&
                // An author box with the author's bio is long, but not the text.
                !AUTHOR_LINKS.containsMatchIn(block.text) &&
                !(description.isNotBlank() && i < LEAD_BLOCKS && (TextMatch.same(block.text, description) || TextMatch.covers(block.text, TextMatch.withoutEllipsis(description))))
        } ?: -1

    /** The headline itself, the headline with its kicker, or the kicker alone. */
    internal fun isTitle(text: String, title: String): Boolean {
        if (title.isBlank() || text.isBlank()) return false
        if (TextMatch.same(text, title)) return true
        val tw = TextMatch.words(text).size
        val hw = TextMatch.words(title).size
        if (hw >= 3 && TextMatch.covers(text, title) && tw <= hw + 6) return true
        return tw >= 3 && TextMatch.covers(title, text) && tw >= hw - 6
    }

    /** "Read More: …" between paragraphs. */
    internal fun isRelatedLink(text: String): Boolean =
        TextMatch.wordCount(text) <= RELATED_MAX_WORDS && RELATED_LINE.matches(text.trim())

    /** A picture caption: "… (Archivbild) Foto: Kay Nietfeld/dpa", "Image — … Photo by … / AFP". */
    internal fun isCredit(text: String): Boolean {
        val words = TextMatch.wordCount(text)
        if (words == 0 || words > CREDIT_MAX_WORDS) return false
        val t = text.trim()
        if (CREDIT_TAIL.containsMatchIn(t) || CREDIT_HEAD.containsMatchIn(t) || isBareCredit(t)) return true
        // "Mike Campbell | Nurphoto | Getty Images", "© Max Muster / dpa"
        return words <= AGENCY_CREDIT_WORDS && AGENCY_TAIL.containsMatchIn(t) && CREDIT_MARK.containsMatchIn(t)
    }

    /** A photographer's credit standing alone: no caption, no sentence. */
    internal fun isBareCredit(text: String): Boolean {
        val t = text.trim()
        return TextMatch.wordCount(t) <= BARE_CREDIT_WORDS && BARE_CREDIT.matches(t)
    }

    /** "Stand: …", a date, "Von …", "Lesezeit: …", an author box. */
    internal fun isNoise(text: String): Boolean {
        val words = TextMatch.wordCount(text)
        if (words == 0 || words > NOISE_MAX_WORDS) return false
        if (isMetaLine(text) || NOISE.any { it.containsMatchIn(text) }) return true
        return words <= 10 && BYLINE.containsMatchIn(text) && !SENTENCE_END.containsMatchIn(text.trim())
    }

    /**
     * A date line: "Stand: 05.11.2026 • 09:03 Uhr", "Veröffentlicht am 5. November 2026",
     * "Updated 2 hours ago", "Published 5 November 2026 — 3 minute read". The opening word with
     * a colon, or with only a date or time after it (up to a separator) – not a sentence that
     * happens to start with the word ("Stand Mittwochabend wurden 23 Verletzte gezählt.",
     * "Veröffentlicht wurde die Studie im Fachblatt Nature.").
     */
    internal fun isMetaLine(text: String): Boolean {
        val t = text.trim()
        val word = META_WORD.find(t) ?: return false
        val rest = t.substring(word.range.last + 1)
        if (rest.trimStart().startsWith(":")) return true
        val head = rest.split(META_SEPARATOR, limit = 2).first()
        val tokens = head.split(META_TOKEN_SPLIT).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        var dated = false
        for (token in tokens) {
            when {
                DATE_TOKEN.matches(token) || MONTHS.matches(token) -> dated = true
                WEEKDAYS.matches(token) || token.lowercase() in META_FILLER -> Unit
                else -> return false
            }
        }
        return dated
    }

    private fun isBreadcrumb(block: ArticleBlock): Boolean =
        block.items.size in 2..8 && block.items.all { TextMatch.wordCount(it) <= 12 } && block.items.any { BREADCRUMB.containsMatchIn(it.trim()) }

    /** A line of an embed's consent placeholder (see [EMBED_CONSENT]). */
    internal fun isEmbedConsent(text: String): Boolean =
        TextMatch.wordCount(text) <= EMBED_MAX_WORDS && EMBED_CONSENT.containsMatchIn(text.trim())

    /** The newsroom asking to be shown first on Google (see [SOURCE_PROMO]). */
    internal fun isSourcePromo(text: String): Boolean =
        TextMatch.wordCount(text) <= SOURCE_PROMO_WORDS && SOURCE_PROMO.containsMatchIn(text.trim())

    /** "Inhaltlichen Fehler melden": a link, not a sentence. */
    internal fun isErrorReport(text: String): Boolean =
        TextMatch.wordCount(text) <= ERROR_REPORT_WORDS && ERROR_REPORT.matches(text.trim())

    /**
     * The placeholder of an embed that waits for consent goes, line by line – and with it the
     * provider's name, which pages set as a line of its own between two of them ("Hier findest du
     * externe Inhalte von" · "TargetVideo GmbH" · ", die unser redaktionelles Angebot …").
     */
    private fun dropEmbedPlaceholders(blocks: List<ArticleBlock>): List<ArticleBlock> {
        val box = BooleanArray(blocks.size) { i ->
            val block = blocks[i]
            (block.type == ArticleBlock.PARAGRAPH || block.type == ArticleBlock.HEADING) && isEmbedConsent(block.text)
        }
        if (box.none { it }) return blocks
        return blocks.filterIndexed { i, block ->
            val between = i in 1 until blocks.lastIndex && box[i - 1] && box[i + 1] && block.type == ArticleBlock.PARAGRAPH &&
                TextMatch.wordCount(block.text) <= SNIPPET_WORDS && !SENTENCE_STOP.containsMatchIn(block.text.trim())
            !box[i] && !between
        }
    }

    /**
     * Sources and credits are not the story (the owner's rule: always out, docs/DESIGN.md §7):
     *
     * - a box of sources or other articles – its label ([isSectionLabel]: "Quellen", "Mehr zum
     *   Thema", "Related", "À lire aussi") and what follows it as long as it looks like the box:
     *   lists, pictures, captions, short lines that are no sentence (link titles), short lines of
     *   any kind in a list of sources, headings of teaser cards; a long paragraph – or a short
     *   line or heading right before one – is the text again,
     * - a line of its own that says where the text or its pictures come from or asks the reader
     *   to subscribe ([isSourceLine]); in a list, such entries (and a list of nothing else).
     *
     * A sentence of the story that names a source ("…, sagte er der dpa.") is never such a line.
     */
    private fun dropSources(blocks: List<ArticleBlock>): List<ArticleBlock> {
        val dropped = BooleanArray(blocks.size)
        var i = 0
        while (i < blocks.size) {
            val block = blocks[i]
            if ((block.type == ArticleBlock.HEADING || block.type == ArticleBlock.PARAGRAPH) && isSectionLabel(block.text)) {
                val sources = SOURCE_LABEL.containsMatchIn(block.text.trim())
                dropped[i] = true
                var k = i + 1
                while (k < blocks.size && inSourceBox(blocks, k, sources)) dropped[k++] = true
                i = k
                continue
            }
            val line = when (block.type) {
                ArticleBlock.PARAGRAPH, ArticleBlock.CAPTION -> isSourceLine(block.text) || isAuthorLine(block.text)
                // A heading in title case may look like a name ("By The Numbers") or carry an agency's letters ("AP-Analyse").
                ArticleBlock.HEADING -> isSourceLine(block.text, heading = true)
                else -> false
            }
            if (line) dropped[i] = true
            i++
        }
        return blocks.mapIndexedNotNull { k, block ->
            when {
                dropped[k] -> null
                block.type == ArticleBlock.LIST -> block.items.filterNot(::isSourceLine).takeIf { it.isNotEmpty() }?.let { block.copy(items = it) }
                else -> block
            }
        }
    }

    /** True when the block at [k] still belongs to a box of sources or links that opened above it. */
    private fun inSourceBox(blocks: List<ArticleBlock>, k: Int, sources: Boolean): Boolean {
        val block = blocks[k]
        val words = TextMatch.wordCount(block.text)
        val beforeText = blocks.getOrNull(k + 1)?.let { it.type == ArticleBlock.PARAGRAPH && TextMatch.wordCount(it.text) > SECTION_LINE_WORDS } == true
        return when (block.type) {
            ArticleBlock.LIST, ArticleBlock.IMAGE, ArticleBlock.CAPTION -> true
            ArticleBlock.HEADING -> words <= SECTION_LINE_WORDS && !beforeText && !isSectionLabel(block.text)
            ArticleBlock.PARAGRAPH -> words <= SECTION_LINE_WORDS && !beforeText && !isSectionLabel(block.text) &&
                (sources || isSourceLine(block.text) || !SENTENCE_STOP.containsMatchIn(block.text.trim()))
            else -> false
        }
    }

    /** The label of a box of sources or other articles, alone on its line (see [SECTION_LABEL]). */
    internal fun isSectionLabel(text: String): Boolean =
        TextMatch.wordCount(text) <= SECTION_LABEL_WORDS && SECTION_LABEL.matches(text.trim())

    /**
     * A line that names where the text or its pictures come from, who wrote or photographed it, or
     * asks the reader to subscribe or follow – never a sentence of the story:
     *
     * - sources: "Quelle: dpa", "Sources: …", "Source : AFP",
     * - agencies and initials: "(dpa)", "jdl/dpa", "dpa, AFP", "Mit Material von dpa", "Avec AFP",
     *   "Le Figaro avec AFP", "With reporting from Reuters",
     * - credits: "Mitarbeit: …", "Text: …", "Foto: …", "Bild: IMAGO/…", "© …", "AP Photo/Evan
     *   Vucci", "Reporting by …; Editing by …", "Propos recueillis par …", "Crédit photo : …", the
     *   byline as its own line ("Von Max Muster, Berlin", "By The Associated Press"),
     *   "The Associated Press contributed to this report.",
     * - where it appeared first: "Dieser Artikel erschien zuerst …", "This article was originally
     *   published on …", "Cet article a été publié initialement …",
     * - prompts: "Abonnieren Sie unseren Newsletter", "Folgen Sie uns auf Instagram", "Hier geht's
     *   zum WhatsApp-Kanal", "Sign up for our newsletter", "Suivez-nous sur les réseaux sociaux".
     *
     * A question is never one ("BILD: Herr Merz, warum …?" is an interview). The byline on its own
     * line is [isAuthorLine] (for paragraphs only); a [heading] is spared the photo-agency rule.
     */
    internal fun isSourceLine(text: String, heading: Boolean = false): Boolean {
        val t = text.trim()
        val words = TextMatch.wordCount(t)
        if (words == 0 || t.endsWith("?")) return false
        val stop = SENTENCE_STOP.containsMatchIn(t)
        return (words <= SOURCE_LINE_WORDS && SOURCE_LINE.containsMatchIn(t)) ||
            (words <= PHOTO_CREDIT_WORDS && PHOTO_CREDIT.containsMatchIn(t)) ||
            (words <= STAFF_CREDIT_WORDS && STAFF_CREDIT.containsMatchIn(t)) ||
            (words <= REPORTING_BY_WORDS && (REPORTING_CREDIT.containsMatchIn(t) || (REPORTING_BY.containsMatchIn(t) && (!stop || REPORTING_MARKS.containsMatchIn(t))))) ||
            (words <= CREDIT_PHRASE_WORDS && CREDIT_PHRASE.containsMatchIn(t) && !stop) ||
            (words <= AGENCY_LINE_WORDS && AGENCY_MATERIAL.containsMatchIn(t) && AGENCY_WORD.containsMatchIn(t)) ||
            (words <= AVEC_AGENCY_WORDS && isAvecAgency(t)) ||
            (words <= COPYRIGHT_WORDS && COPYRIGHT.containsMatchIn(t)) ||
            (words <= CONTRIBUTED_WORDS && (CONTRIBUTED.containsMatchIn(t) || (CONTRIBUTED_END.containsMatchIn(t) && CONTRIBUTOR.containsMatchIn(t)))) ||
            (words <= FIRST_APPEARED_WORDS && FIRST_APPEARED.containsMatchIn(t)) ||
            (words <= PROMPT_WORDS && isPrompt(t)) ||
            (!heading && words <= PHOTO_AGENCY_WORDS && isPhotoAgencyCredit(t)) ||
            isAgencyLine(t) || isBareCredit(t)
    }

    /** "Avec AFP", "Le Figaro avec AFP", "Par Jean Dupont avec l'AFP", "La rédaction avec AFP" – not "Il a parlé avec l'AFP." */
    private fun isAvecAgency(text: String): Boolean {
        val match = AVEC_AGENCY.find(text) ?: return false
        val before = text.substring(0, match.range.first).trim().removePrefix("(").trim()
        if (before.isEmpty() || NEWSROOM_LEAD.matches(before)) return true
        val name = before.removePrefix("Par ").removePrefix("par ").trim()
        return name.isNotEmpty() && isPersonName(name)
    }

    /** "Abonnieren Sie unseren Newsletter", "Folgen Sie uns auf Instagram", "WhatsApp-Kanal" (see [PROMPT_START]). */
    private fun isPrompt(text: String): Boolean =
        PROMPT_PHRASE.containsMatchIn(text) || ((PROMPT_START.containsMatchIn(text) || FOLLOW_US.containsMatchIn(text)) && CHANNEL.containsMatchIn(text))

    /**
     * Nothing but agencies and initials: "dpa", "(AFP)", "jdl/dpa", "dpa/AFP/Reuters", "mit dpa",
     * "© dpa", "Reuters, AP" – at least one agency, every part an agency or a short lower-case
     * mark. "Laut dpa" or "USA" are not.
     */
    internal fun isAgencyLine(text: String): Boolean {
        var t = text.trim().trimEnd('.').trim()
        if (t.isEmpty() || t.length > AGENCY_LINE_CHARS) return false
        t = t.removePrefix("(").removeSuffix(")").removePrefix("[").removeSuffix("]").trim().removePrefix("©").trim()
        t = AGENCY_LEAD.replace(t, "")
        val parts = t.split(AGENCY_SPLIT).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > AGENCY_PARTS) return false
        return parts.any { it in AGENCIES } && parts.all { it in AGENCIES || INITIALS.matches(it) }
    }

    /** "AP Photo/Evan Vucci", "picture alliance / dpa | Kay Nietfeld": a photographer and an agency, no sentence. */
    private fun isPhotoAgencyCredit(text: String): Boolean =
        CREDIT_SEPARATOR.containsMatchIn(text) && PHOTO_AGENCY.containsMatchIn(text) && !SENTENCE_STOP.containsMatchIn(text)

    /**
     * The byline on a line of its own: "Von Max Muster", "By Jane Doe and John Roe, Washington",
     * "Par Jean Dupont", "Von index-radar.de" – every part after the word a name (or a place, or
     * the web address of the newsroom that wrote it). "Von Montag an" or "Von Berlin nach Paris"
     * are not.
     */
    internal fun isAuthorLine(text: String): Boolean {
        val t = text.trim().trimEnd('.')
        if (TextMatch.wordCount(t) > AUTHOR_LINE_WORDS) return false
        val prefix = AUTHOR_PREFIX.find(t) ?: return false
        val names = t.substring(prefix.range.last + 1).split(NAME_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        return names.isNotEmpty() && names.size <= NAME_MAX_WORDS && names.all { isPersonName(it) || WEB_NAME.matches(it) }
    }

    /**
     * What a page hangs under its text, from its first line on:
     *
     * - "Inhaltlichen Fehler melden" (taz) closes the article – after it come teasers for other
     *   articles with their pictures and a subscription offer. Only once a good part of the words
     *   stands before it ([END_MARK_MIN_SHARE]): a page that puts the link on top keeps its text.
     * - "Unsere Artikel gezielt bei Google anzeigen lassen" (Sportschau) opens a box that explains
     *   itself in a paragraph and ends in link fragments ("hier", ". → Hier können Sie"). Only in
     *   the closing part of the text; further up the line goes alone (t3n and WELT set it under
     *   the teaser).
     */
    private fun cutClosingBoxes(blocks: List<ArticleBlock>): List<ArticleBlock> {
        val sizes = blocks.map { TextMatch.wordCount(it.text) + it.items.sumOf(TextMatch::wordCount) }
        val total = sizes.sum()
        var before = 0
        for ((k, block) in blocks.withIndex()) {
            if (block.type == ArticleBlock.PARAGRAPH || block.type == ArticleBlock.HEADING) {
                val closes = (isErrorReport(block.text) && before >= total * END_MARK_MIN_SHARE) ||
                    (isSourcePromo(block.text) && total - before <= total * CONSENT_TAIL_SHARE)
                if (closes) return blocks.subList(0, k)
            }
            before += sizes[k]
        }
        return blocks
    }

    /** A pull quote repeats a sentence of the text; its attribution line goes with it. */
    private fun dropPullQuotes(blocks: List<ArticleBlock>): List<ArticleBlock> {
        val paragraphs = blocks.filter { it.type == ArticleBlock.PARAGRAPH }
        val dropped = BooleanArray(blocks.size)
        blocks.forEachIndexed { i, block ->
            if (block.type != ArticleBlock.QUOTE) return@forEachIndexed
            if (TextMatch.words(block.text).size < TextMatch.MIN_FUZZY_WORDS) return@forEachIndexed
            if (paragraphs.none { TextMatch.covers(it.text, block.text) }) return@forEachIndexed
            dropped[i] = true
            val next = blocks.getOrNull(i + 1)
            if (next != null && next.type == ArticleBlock.PARAGRAPH && TextMatch.wordCount(next.text) <= 8 && !SENTENCE_END.containsMatchIn(next.text.trim())) {
                dropped[i + 1] = true
            }
        }
        return blocks.filterIndexed { i, _ -> !dropped[i] }
    }

    /**
     * The same text twice: anywhere when (nearly) identical; a shorter block that a neighbour (or,
     * before the text proper, any lead block) repeats in full is the duplicate.
     */
    private fun dropRepeatedText(blocks: List<ArticleBlock>): List<ArticleBlock> {
        val texts = blocks.withIndex().filter { it.value.type in TEXT && it.value.text.isNotBlank() }
        val dropped = BooleanArray(blocks.size)
        val seen = HashSet<String>()
        for ((x, entry) in texts.withIndex()) {
            val (i, block) = entry
            val key = TextMatch.normalize(block.text)
            if (repeatsCount(block) && !seen.add(key)) {
                dropped[i] = true
                continue
            }
            if (block.type == ArticleBlock.HEADING || !repeatsCount(block)) continue
            for (y in 0 until x) {
                val (j, earlier) = texts[y]
                if (dropped[j] || earlier.type == ArticleBlock.HEADING) continue
                if (TextMatch.same(earlier.text, block.text)) {
                    dropped[i] = true
                    break
                }
                val near = x - y == 1 || y < LEAD_BLOCKS
                if (!near) continue
                val ew = TextMatch.words(earlier.text).size
                val bw = TextMatch.words(block.text).size
                if (bw <= ew && bw >= 8 && TextMatch.covers(earlier.text, block.text)) {
                    dropped[i] = true
                    break
                }
                if (ew < bw && ew >= 8 && TextMatch.covers(block.text, earlier.text)) {
                    dropped[j] = true
                }
            }
        }
        // A paragraph whose words all stand, in a row, inside a longer paragraph somewhere else: a
        // pull quote or a picture's caption set as a paragraph. The longer one says it anyway.
        val paragraphs = texts.filter { (i, block) -> block.type == ArticleBlock.PARAGRAPH && !dropped[i] }.map { (i, block) -> i to TextMatch.words(block.text) }
        for ((i, words) in paragraphs) {
            if (words.size < ECHO_MIN_WORDS) continue
            if (paragraphs.any { (j, other) -> j != i && !dropped[j] && other.size >= words.size + ECHO_GAIN_WORDS && TextMatch.containsRun(other, words) }) dropped[i] = true
        }
        // The heading again as a short line right below it ("<heading> Vai al contenuto": the anchor link of a live blog's entry).
        for ((x, entry) in texts.withIndex()) {
            val (i, block) = entry
            if (block.type != ArticleBlock.PARAGRAPH || dropped[i]) continue
            val words = TextMatch.words(block.text)
            val echo = (maxOf(0, x - HEADING_ECHO_BLOCKS) until x).any { y ->
                val (_, earlier) = texts[y]
                val heading = TextMatch.words(earlier.text)
                earlier.type == ArticleBlock.HEADING && heading.size >= 4 && words.size <= heading.size + HEADING_ECHO_EXTRA &&
                    words.size >= heading.size && words.subList(0, heading.size) == heading
            }
            if (echo) dropped[i] = true
        }
        return blocks.filterIndexed { i, _ -> !dropped[i] }
    }

    /**
     * True when [block] seen a second time word for word is a duplicate: headings (a repeated
     * short heading is page chrome) and texts of at least [TextMatch.MIN_FUZZY_WORDS] words. A
     * short paragraph may repeat on purpose: "Nein." as the answer to two questions of an
     * interview, a refrain.
     */
    internal fun repeatsCount(block: ArticleBlock): Boolean =
        block.type == ArticleBlock.HEADING || TextMatch.words(block.text).size >= TextMatch.MIN_FUZZY_WORDS

    /** A picture that was shown before (another rendition of it) goes, with its caption. */
    private fun dropRepeatedImages(blocks: List<ArticleBlock>): List<ArticleBlock> {
        val shown = ArrayList<String>()
        val out = ArrayList<ArticleBlock>(blocks.size)
        var skipCaption = false
        for (block in blocks) {
            if (block.type == ArticleBlock.IMAGE) {
                if (shown.any { ImageMatch.same(it, block.url) }) {
                    skipCaption = true
                    continue
                }
                shown += block.url
            } else if (block.type == ArticleBlock.CAPTION && skipCaption) {
                skipCaption = false
                continue
            }
            skipCaption = false
            out += block
        }
        return out
    }

    /**
     * Short paragraphs right under a picture that end in a photo credit ("Erneut nominiert: Said
     * El Mala" + "Bildquelle: IMAGO/…") are the picture's caption.
     */
    private fun creditsToCaptions(blocks: List<ArticleBlock>): List<ArticleBlock> {
        val out = ArrayList<ArticleBlock>(blocks.size)
        var i = 0
        while (i < blocks.size) {
            val block = blocks[i]
            if (block.type == ArticleBlock.PARAGRAPH && CAPTION_TOGGLE.containsMatchIn(block.text)) {
                // "<caption> <credit> hide caption" under a picture, then the credit once more.
                val text = CAPTION_TOGGLE.replace(block.text, "").trim()
                if (text.isNotEmpty() && out.lastOrNull()?.type == ArticleBlock.IMAGE) out += ArticleBlock.caption(text)
                i++
                val next = blocks.getOrNull(i)
                if (next != null && next.type == ArticleBlock.PARAGRAPH && TextMatch.wordCount(next.text) <= BARE_CREDIT_WORDS &&
                    TextMatch.containsRun(TextMatch.words(text), TextMatch.words(next.text))
                ) {
                    i++
                }
                continue
            }
            if (block.type == ArticleBlock.CAPTION && CAPTION_TOGGLE.containsMatchIn(block.text)) {
                // In a <figcaption>: "<caption> <credit> hide caption <credit>".
                val text = CAPTION_TOGGLE.replace(block.text, "").trim()
                if (text.isNotEmpty()) out += ArticleBlock.caption(text)
                i++
                continue
            }
            out += block
            i++
            if (block.type != ArticleBlock.IMAGE) continue
            // A caption with its own switch is taken apart above, on the next round.
            if (blocks.getOrNull(i)?.let { it.type == ArticleBlock.PARAGRAPH && CAPTION_TOGGLE.containsMatchIn(it.text) } == true) continue
            for (span in 1..CAPTION_PARTS) {
                val parts = blocks.subList(i, minOf(blocks.size, i + span))
                if (parts.size < span || parts.any { it.type != ArticleBlock.PARAGRAPH }) break
                val credit = isCredit(parts.last().text)
                val short = parts.dropLast(1).all { TextMatch.wordCount(it.text) <= CAPTION_PART_WORDS }
                if (credit && short) {
                    out += ArticleBlock.caption(parts.joinToString(" ") { it.text })
                    i += span
                    break
                }
            }
        }
        // A credit that found no picture (the picture went, or never was in the text) describes nothing.
        return out.filterNot { it.type == ArticleBlock.PARAGRAPH && isBareCredit(it.text) }
    }

    /** Player and gallery leftovers between paragraphs: "Audio starten, abbrechen mit Escape". */
    internal fun isMediaNoise(text: String): Boolean =
        TextMatch.wordCount(text) <= MEDIA_MAX_WORDS && MEDIA_NOISE.containsMatchIn(text)

    /** A list of links to other articles: "Erstmals in der DFB-Elf: … | Mehr". */
    private fun isLinkList(block: ArticleBlock): Boolean =
        block.type == ArticleBlock.LIST && block.items.size in 2..12 && MORE_WORD.matches(block.items.last().trim())

    /**
     * The reader prints an image's alt text under it and a caption block below that: when one
     * repeats the other, only one stays (the caption, which usually adds the credit).
     */
    private fun fixCaptions(blocks: List<ArticleBlock>): List<ArticleBlock> {
        val out = ArrayList<ArticleBlock>(blocks.size)
        for (block in blocks) {
            val previous = out.lastOrNull()
            if (block.type == ArticleBlock.CAPTION && previous?.type == ArticleBlock.IMAGE && previous.alt.isNotBlank()) {
                val alt = previous.alt
                when {
                    TextMatch.same(alt, block.text) || TextMatch.covers(block.text, alt) -> {
                        out[out.lastIndex] = previous.copy(alt = "")
                        out += block
                    }
                    TextMatch.covers(alt, block.text) -> Unit
                    else -> out += block
                }
                continue
            }
            if (block.type == ArticleBlock.CAPTION && out.any { it.type == ArticleBlock.CAPTION && TextMatch.same(it.text, block.text) }) continue
            out += block
        }
        return out
    }

    /**
     * The reader prints a picture's alt text under it when the picture has no caption. An alt
     * text that only says again what stands on the screen anyway goes: the headline or the teaser
     * ([above]) – a video still or the lead picture is often described by the headline –, a
     * paragraph next to the picture, the alt text of an earlier picture, a word that describes
     * nothing ("Videoplayer"). A short paragraph right under the picture that repeats the alt
     * text (often with the credit: "Leone XIV in volo … (VATICAN MEDIA)") is the picture's caption.
     */
    internal fun settleAlts(blocks: List<ArticleBlock>, above: List<String>): List<ArticleBlock> {
        val out = blocks.toMutableList()
        val seen = HashSet<String>()
        for (i in out.indices) {
            val block = out[i]
            if (block.type != ArticleBlock.IMAGE || block.alt.isBlank()) continue
            val alt = block.alt
            val next = out.getOrNull(i + 1)
            if (next != null && next.type == ArticleBlock.PARAGRAPH && TextMatch.wordCount(next.text) <= CREDIT_MAX_WORDS && saysAgain(next.text, alt)) {
                out[i + 1] = ArticleBlock.caption(next.text)
                out[i] = block.copy(alt = "")
                continue
            }
            val repeated = !seen.add(TextMatch.normalize(alt))
            val near = (i - ALT_NEIGHBOURS..i + ALT_NEIGHBOURS).any { k ->
                val other = out.getOrNull(k)
                k != i && other != null && other.type in TEXT && saysAgain(other.text, alt)
            }
            if (repeated || near || GENERIC_ALT.containsMatchIn(alt.trim()) || above.any { it.isNotBlank() && saysAgain(it, alt) }) {
                out[i] = block.copy(alt = "")
            }
        }
        return out
    }

    /** True when [text] says what [alt] says (and maybe more). */
    private fun saysAgain(text: String, alt: String): Boolean = TextMatch.same(text, alt) || TextMatch.covers(text, alt)

    private fun dropTailNoise(blocks: List<ArticleBlock>): List<ArticleBlock> {
        var end = blocks.size
        // A consent dialog in the last part of the text: everything from it on goes.
        val total = blocks.sumOf { TextMatch.wordCount(it.text) + it.items.sumOf(TextMatch::wordCount) }
        var after = 0
        for (k in blocks.indices.reversed()) {
            val block = blocks[k]
            after += TextMatch.wordCount(block.text) + block.items.sumOf(TextMatch::wordCount)
            if (after > total * CONSENT_TAIL_SHARE) break
            if ((block.type == ArticleBlock.HEADING || block.type == ArticleBlock.PARAGRAPH) && isConsentLine(block.text)) end = k
        }
        // The author's bio box: portrait, name, bio.
        val portrait = (maxOf(0, end - AUTHOR_TAIL_BLOCKS) until end).firstOrNull { opensAuthorBox(blocks, it, end) }
        if (portrait != null) end = portrait
        while (end > 0) {
            val block = blocks[end - 1]
            val tail = (block.type == ArticleBlock.PARAGRAPH || block.type == ArticleBlock.HEADING) &&
                TextMatch.wordCount(block.text) <= TAIL_MAX_WORDS && TAIL_NOISE.containsMatchIn(block.text.trim())
            // A heading with nothing after it, or a closing line ("Stand: …", "37 Comments", "Artikel teilen").
            if (tail || block.type == ArticleBlock.HEADING) end-- else break
        }
        return if (end == blocks.size) blocks else blocks.subList(0, end)
    }

    /** A line of a consent dialog: a button, not a sentence. */
    internal fun isConsentLine(text: String): Boolean {
        val t = text.trim()
        if (CONSENT_BUTTON.matches(t)) return true
        return TextMatch.wordCount(t) <= CONSENT_LINE_WORDS && !SENTENCE_STOP.containsMatchIn(t) && CONSENT_LINE.containsMatchIn(t)
    }

    /**
     * True when the picture at [index] opens the author's bio box (before [end]): its alt text
     * names a person ("Photo of Kyle Orland") and what follows is about that person – a line
     * that starts with the name ("Kyle Orland Senior Gaming Editor", "Kyle Orland has been …")
     * or an author link ("Posts from this author"). "Foto von der Unfallstelle" or "Bild von
     * Demonstranten vor dem Reichstag" are pictures of the story.
     *
     * An alt text that is just a name ("Dominic Johnson", taz) is a portrait only with the name
     * and the role as the line right under it ("Dominic Johnson Ressortleiter Ausland") – a
     * picture of a person in the story is followed by sentences.
     */
    internal fun opensAuthorBox(blocks: List<ArticleBlock>, index: Int, end: Int = blocks.size): Boolean {
        val image = blocks[index]
        if (image.type != ArticleBlock.IMAGE) return false
        val alt = image.alt.trim()
        val labelled = AUTHOR_PHOTO.find(alt)?.groupValues?.get(3)?.trim()?.trimEnd('.')
        val name = labelled ?: alt.takeIf { TextMatch.wordCount(it) >= 2 } ?: return false
        if (!isPersonName(name)) return false
        val after = blocks.subList(index + 1, end).filter { it.type == ArticleBlock.PARAGRAPH || it.type == ArticleBlock.HEADING }
        val nameKey = TextMatch.normalize(name)
        if (labelled == null) {
            val role = after.firstOrNull() ?: return false
            return blocks[index + 1] === role && TextMatch.normalize(role.text).startsWith("$nameKey ") &&
                TextMatch.wordCount(role.text) <= ROLE_LINE_WORDS && !SENTENCE_STOP.containsMatchIn(role.text.trim())
        }
        if (after.any { AUTHOR_LINKS.containsMatchIn(it.text) }) return true
        val next = after.take(2)
        val aboutTheName = next.filter { TextMatch.normalize(it.text).startsWith(nameKey) }
        if (aboutTheName.isEmpty()) return false
        // "Autorenbild von …" says so itself; else the name with the role ("Kyle Orland Senior Gaming Editor").
        return AUTHOR_WORD.containsMatchIn(alt) ||
            aboutTheName.any { TextMatch.wordCount(it.text) <= ROLE_LINE_WORDS && !SENTENCE_STOP.containsMatchIn(it.text.trim()) }
    }

    /** "Kyle Orland", "Ursula von der Leyen": a few words, each a capitalised name or a name particle. */
    private fun isPersonName(text: String): Boolean {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > NAME_MAX_WORDS) return false
        if (!words.first().first().isUpperCase()) return false
        return words.all { w -> w.first().isUpperCase() || w.lowercase() in NAME_PARTICLES }
    }

    /** A caption whose picture went (or that opens the article) has nothing to describe. */
    private fun dropOrphanCaptions(blocks: List<ArticleBlock>): List<ArticleBlock> =
        blocks.filterIndexed { i, block -> block.type != ArticleBlock.CAPTION || (i > 0 && blocks[i - 1].type == ArticleBlock.IMAGE) }

    // -------------------------------------------------------------------------------- reader

    /**
     * What the reader shows: the teaser above the text (maybe shortened, maybe empty), the text
     * and the picture on top ([hero]; null = none, the reader draws the topic's cover).
     */
    data class Arranged(val teaser: String, val content: ArticleContent, val hero: String? = null)

    /**
     * Fits [content] under the reader's head: drops the [headline] when the text repeats it, every
     * copy of the picture the reader shows on top, and settles the [teaser] against the lead
     * paragraphs so the same words never appear twice:
     *
     * - the teaser only repeats the headline → no teaser,
     * - the lead paragraph (or a heading before the text) says what the teaser says → it goes,
     * - the teaser holds the paragraph and more (standfirst + first paragraphs) → the teaser is
     *   cut before it (or hidden when nothing else is left),
     * - the paragraph holds the teaser and more (the feed cut it) → the full paragraph becomes
     *   the teaser when it is short, else the teaser is hidden,
     * - the teaser is put together from sentences of the first paragraphs → the teaser is hidden.
     *
     * **One picture per article**, the one on top: [hero] (the feed's picture, else the card's
     * cover, else the page's announced lead picture); without one and with [pictureFromText], the
     * first picture of the text moves up. It is decided here, once – and every picture of the text
     * goes, with its caption: the lead picture again in another size, inline pictures, galleries,
     * video stills. Nothing is left to show a picture, a caption or an alt text below the head.
     *
     * [sources] are names of the newsroom (the feed's source, `og:site_name`, its domain): a line
     * that is nothing but one of them ("tagesschau.de" under the last paragraph) goes as well.
     */
    fun arrange(
        content: ArticleContent,
        teaser: String,
        headline: String = "",
        hero: String? = null,
        pictureFromText: Boolean = false,
        sources: List<String> = emptyList(),
    ): Arranged {
        var blocks = dropLeadNoise(content.blocks, listOf(headline, content.title), "")
        val titles = listOf(headline, content.title).filter { TextMatch.words(it).size >= 3 }
        var shown = cleanTeaser(teaser)
        // "Nearly 1,500 migrants reach …" under the headline "Nearly 1,500 migrants reach …": a video page's description.
        if (listOf(headline, content.title).any { it.isNotBlank() && (TextMatch.same(shown, it) || TextMatch.covers(it, shown)) }) shown = ""
        if (TextMatch.words(shown).size >= 4) {
            // A heading before the text that is the teaser ("Was heute wichtig war"): once is enough.
            val firstText = blocks.indexOfFirst { it.type == ArticleBlock.PARAGRAPH || it.type == ArticleBlock.QUOTE }.let { if (it < 0) blocks.size else it }
            val heading = blocks.take(firstText).indexOfFirst { it.type == ArticleBlock.HEADING && TextMatch.same(it.text, TextMatch.withoutEllipsis(shown)) }
            if (heading >= 0) blocks = blocks.filterIndexed { k, _ -> k != heading }
            val candidates = blocks.withIndex().filter { it.value.type == ArticleBlock.PARAGRAPH || it.value.type == ArticleBlock.QUOTE }.take(TEASER_CANDIDATES)
            var settled = false
            for ((i, block) in candidates) {
                val text = block.text
                val bare = TextMatch.withoutEllipsis(shown)
                when {
                    TextMatch.same(text, shown) || TextMatch.same(text, bare) -> {
                        // The same words: show them once, complete (the feed may have cut its copy).
                        if (TextMatch.wordCount(text) > TextMatch.wordCount(shown) && TextMatch.wordCount(text) <= PROMOTE_MAX_WORDS) shown = text
                        blocks = blocks.filterIndexed { k, _ -> k != i }
                    }
                    TextMatch.words(text).size >= 4 && TextMatch.covers(shown, text) -> {
                        shown = cutBefore(shown, text)
                    }
                    TextMatch.covers(text, bare) || TextMatch.startsWithCut(text, shown) -> {
                        if (titles.any { TextMatch.covers(text, it) }) {
                            // Headline + teaser in one block: the page's own header box, not text.
                            blocks = blocks.filterIndexed { k, _ -> k != i }
                        } else if (TextMatch.wordCount(text) <= PROMOTE_MAX_WORDS) {
                            shown = text
                            blocks = blocks.filterIndexed { k, _ -> k != i }
                        } else {
                            shown = ""
                        }
                    }
                    i == candidates.first().index && TextMatch.paraphrase(text, bare) && titles.none { TextMatch.covers(text, it) } -> {
                        // The lead paragraph rewords the teaser: say it once, in the page's own words.
                        if (TextMatch.wordCount(text) <= PROMOTE_MAX_WORDS) shown = text
                        blocks = blocks.filterIndexed { k, _ -> k != i }
                    }
                    else -> continue
                }
                settled = true
                break
            }
            if (settled && shown.isNotBlank()) {
                // The standfirst a second time among the lead paragraphs (once in the page's header box, once in the text).
                val again = blocks.withIndex().filter { it.value.type == ArticleBlock.PARAGRAPH || it.value.type == ArticleBlock.QUOTE }.take(TEASER_CANDIDATES)
                    .filter { TextMatch.same(it.value.text, shown) }.map { it.index }.toSet()
                if (again.isNotEmpty()) blocks = blocks.filterIndexed { k, _ -> k !in again }
            } else if (!settled) {
                // The teaser's sentences that the lead prints again, word for word, go ("The pattern has reached the
                // top of the scale. Only three others have." – the first sentences of two paragraphs; the first
                // point of the page's summary list); what is left stays when it still says something.
                val firstLong = blocks.indexOfFirst { it.type == ArticleBlock.PARAGRAPH && TextMatch.wordCount(it.text) >= LONG_PARAGRAPH_WORDS }
                val points = blocks.take(if (firstLong < 0) LEAD_BLOCKS else minOf(firstLong, LEAD_BLOCKS)).filter { it.type == ArticleBlock.LIST }.flatMap { it.items }
                shown = withoutRepeatedSentences(shown, candidates.map { it.value.text } + points)
            }
        } else {
            shown = shown.takeIf { TextMatch.words(it).isNotEmpty() }.orEmpty()
        }
        val top = hero?.takeIf { it.isNotBlank() }
            ?: blocks.firstOrNull { pictureFromText && it.type == ArticleBlock.IMAGE && it.url.startsWith("https://") }?.url
        // A short paragraph that says what a picture's alt text says is that picture's caption: it goes with it.
        blocks = settleAlts(blocks, listOf(headline, content.title, shown))
        blocks = blocks.filterNot { it.type == ArticleBlock.IMAGE || it.type == ArticleBlock.CAPTION }
        blocks = dropSourceNames(blocks, sources)
        return Arranged(shown.trim(), content.copy(blocks = blocks), top)
    }

    /** A line that is just the newsroom's name ([sources]): "tagesschau.de", "ZEIT ONLINE", "© SPIEGEL". */
    private fun dropSourceNames(blocks: List<ArticleBlock>, sources: List<String>): List<ArticleBlock> {
        val names = sources.map { TextMatch.normalize(it) }.filter { it.isNotBlank() }.toSet()
        if (names.isEmpty()) return blocks
        return blocks.filterNot { block ->
            val text = block.text.trim().removePrefix("©").trim()
            (block.type == ArticleBlock.PARAGRAPH || block.type == ArticleBlock.HEADING) &&
                TextMatch.wordCount(text) <= SOURCE_NAME_WORDS && TextMatch.normalize(text) in names
        }
    }

    /**
     * [teaser] without the sentences (of at least [TextMatch.MIN_FUZZY_WORDS] words) that stand
     * word for word in one of the [lead] texts (or that one of them makes up but for a word or
     * two); empty when no real sentence is left (a tail like "(ANSA)" is none).
     */
    internal fun withoutRepeatedSentences(teaser: String, lead: List<String>): String {
        val texts = lead.map(TextMatch::words)
        val sentences = TextMatch.sentences(TextMatch.withoutEllipsis(teaser))
        val kept = sentences.filterNot { sentence ->
            val words = TextMatch.words(sentence)
            words.size >= TextMatch.MIN_FUZZY_WORDS && texts.any { text ->
                // The sentence stands in the text – or the text (a point of a summary list) is the sentence but for a word or two.
                TextMatch.containsRun(text, words) ||
                    (text.size >= TextMatch.MIN_FUZZY_WORDS && text.size * 10 >= words.size * 7 && TextMatch.containsRun(words, text))
            }
        }
        if (kept.size == sentences.size) return teaser
        // What is left must still say something by itself ("Platz zwei holt erstmals die AfD." alone does not).
        return if (kept.sumOf { TextMatch.words(it).size } >= TEASER_REST_WORDS) kept.joinToString(" ").trim() else ""
    }

    /** The feed teaser without its "Continue reading…" / "[…]" tail. */
    internal fun cleanTeaser(teaser: String): String {
        var t = FEED_TAIL.replace(teaser.trim(), "").trim()
        while (true) {
            val next = TEASER_TAIL.replace(t, "").trim()
            if (next == t) break
            t = next
        }
        return t
    }

    /** [teaser] up to where [paragraph] begins inside it; empty when that leaves (almost) nothing. */
    private fun cutBefore(teaser: String, paragraph: String): String {
        val words = teaser.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val flat = ArrayList<Pair<String, Int>>()
        words.forEachIndexed { index, word -> TextMatch.words(word).forEach { flat += it to index } }
        val target = TextMatch.words(paragraph).take(6)
        if (target.isEmpty()) return ""
        for (k in 0..flat.size - target.size) {
            if ((target.indices).all { flat[k + it].first == target[it] }) {
                val kept = words.take(flat[k].second).joinToString(" ").trim()
                return if (TextMatch.words(kept).size >= 4) kept else ""
            }
        }
        return ""
    }

    private val TEXT = setOf(ArticleBlock.PARAGRAPH, ArticleBlock.HEADING, ArticleBlock.QUOTE)
}

package com.cedrickgd.morgenbriefing.data.news

import kotlin.random.Random

/**
 * What people search for right now, as suggestions for the search (pure, unit-tested): the
 * search bar names two of them ("Suchen – z. B. …"), the search screen shows them as chips
 * before anything is typed.
 *
 * - [fromFeed]: the terms of Google Trends' daily RSS of the edition's country, in Google's order.
 * - [fromHeadlines]: without them (offline, nothing cached), the subjects the top stories name
 *   before their colon ("Champions League: …").
 * - Without either the bar keeps its fixed examples (the strings).
 */
object TrendingTerms {
    /** The search screen shows at most this many. */
    const val MAX_TERMS = 10

    /** The search bar names this many. */
    const val EXAMPLES = 2

    /** Longer terms do not fit the search bar's single line next to another one. */
    const val MAX_EXAMPLE_LENGTH = 22

    /** Longer "terms" are sentences, not something to tap. */
    private const val MAX_TERM_LENGTH = 40

    /** Editions whose language is written in another script than Latin: their terms are not filtered by script. */
    private val OTHER_SCRIPTS = setOf("ja")

    /**
     * The trend terms of [feed] (Google Trends' `<item><title>`), each once, at most [MAX_TERMS]. For
     * a [language] written in Latin script, terms in other scripts are left out: the French feed,
     * for one, carries Arabic searches the edition's readers cannot use as examples.
     */
    fun fromFeed(feed: ParsedFeed, language: String): List<String> = clean(feed.entries.map { it.title }, language)

    /** [raw] trimmed, without blanks, sentences, foreign scripts ([fromFeed]) and repeats. */
    fun clean(raw: List<String>, language: String): List<String> {
        val seen = HashSet<String>()
        return raw.asSequence()
            .map { it.replace(WHITESPACE, " ").trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_TERM_LENGTH }
            .filter { language in OTHER_SCRIPTS || isLatin(it) }
            .filter { seen.add(it.lowercase()) }
            .take(MAX_TERMS)
            .toList()
    }

    /**
     * The subjects the [headlines] name before a colon – "Champions League: Bayern siegt in Paris"
     * gives "Champions League" – one to three words, without labels like "Liveblog" or
     * "Kommentar", each once, at most [MAX_TERMS]. Headlines without such a subject give nothing:
     * better no example than half a sentence.
     */
    fun fromHeadlines(headlines: List<String>): List<String> {
        val seen = HashSet<String>()
        return headlines.asSequence()
            .mapNotNull(::subjectOf)
            .filter { seen.add(it.lowercase()) }
            .take(MAX_TERMS)
            .toList()
    }

    private fun subjectOf(headline: String): String? {
        val colon = headline.indexOf(':')
        if (colon <= 0) return null
        val subject = headline.substring(0, colon).replace(WHITESPACE, " ").trim().trim('„', '“', '"', '«', '»', '\'')
        val words = subject.split(' ')
        if (words.size > MAX_SUBJECT_WORDS || subject.length < 3 || subject.length > MAX_EXAMPLE_LENGTH) return null
        if (subject.none(Char::isLetter) || subject.lowercase() in LABELS) return null
        // "+++ Liveblog +++", "Update 3": labels and counters, not subjects.
        if (subject.any { it == '+' || it == '|' }) return null
        return subject
    }

    /**
     * [count] terms of [terms] for the search bar, picked at random ([random]: one per app start,
     * so the bar shows other ones next time) among the ones short enough for it.
     */
    fun examples(terms: List<String>, random: Random, count: Int = EXAMPLES): List<String> =
        terms.filter { it.length <= MAX_EXAMPLE_LENGTH }.shuffled(random).take(count)

    private fun isLatin(term: String): Boolean = term.all { ch ->
        !ch.isLetter() || Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.LATIN
    }

    private const val MAX_SUBJECT_WORDS = 3
    private val WHITESPACE = Regex("\\s+")

    /** Headline labels in the app's languages that stand where a subject would. */
    private val LABELS = setOf(
        "live", "liveblog", "liveticker", "ticker", "newsblog", "news", "eilmeldung", "breaking", "breaking news",
        "kommentar", "analyse", "analysis", "interview", "meinung", "opinion", "video", "podcast", "exklusiv",
        "exclusive", "update", "watch", "en direct", "direct", "directo", "en directo", "en vivo", "diretta",
        "commento", "opinione", "opinión", "editorial", "éditorial", "faktencheck", "fact check", "quiz",
    )
}

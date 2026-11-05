package com.cedrickgd.morgenbriefing.data.news

import java.text.Normalizer
import java.util.Locale

/**
 * A search as the user typed it, understood: the Google News query to send ([googleQuery]) and
 * how an article's own text matches it ([match], [matchesAll]). Pure, unit-tested.
 *
 * - Words are ANDed, as Google News does: "Rheinmetall Aktie" wants both.
 * - What the user wrote stays: `"Champions League"` is a phrase, `Midterms OR Senat` wants one
 *   of the two, `-Trump` leaves Trump out, operators (`site:`, `intitle:`, `when:` …) go to
 *   Google as they are.
 * - Ticker-like tokens are exact ([Term.Ticker]): 2–5 capitals or digits (`SLV`, `QQQ`, `NVDA`,
 *   `$BTC`), ISINs and WKNs. Google gets them quoted – unquoted `SLV ETF` also finds Indian
 *   gold-and-silver-ETF stories that never name SLV, quoted only SLV stories (checked
 *   05.11.2026) – and they match only as a whole word in capitals ("SLV" is not in "SLVP").
 *   A short word next to a finance word ("slv etf", "Slv Aktie") is taken for a ticker too.
 *   Common abbreviations ([GENERIC_CAPS]: ETF, EU, KI …) stay plain words: Google's synonyms
 *   ("ETF" ↔ "Exchange Traded Fund") find more for them.
 * - Words match as whole words, case and accents folded, with the usual endings tolerated
 *   ("Aktie" finds "Aktien", "Midterms" finds "midterm", "ETF" finds "ETFs") – never inside
 *   another word.
 */
class SearchQuery private constructor(
    /** The text as typed (trimmed, single spaces). */
    val text: String,
    /** The query for Google News (before date windows and `-site:` exclusions, see [GoogleNews.buildQuery]). */
    val googleQuery: String,
    /** What an article must contain: every group, each by one of its alternatives. */
    val groups: List<List<Term>>,
    /** What an article must not contain. */
    val excluded: List<Term>,
    /** `site:` targets (lower case, without `www.`): a cached article must come from one of them. */
    val sites: List<String>,
) {
    /** One thing a query looks for. */
    sealed interface Term {
        /** A word, folded ([fold]): whole word, common endings tolerated. */
        data class Word(val token: String) : Term

        /**
         * A ticker, ISIN or WKN: these [parts] (split at `.`) as whole words in a row; with
         * [strictCase] (typed in capitals) only in capitals, otherwise in any case.
         */
        data class Ticker(val parts: List<String>, val strictCase: Boolean) : Term

        /** Folded words in a row: a quoted phrase ([exact]) or a hyphenated word ("Rheinmetall-Aktie", endings tolerated). */
        data class Phrase(val tokens: List<String>, val exact: Boolean) : Term
    }

    /** How well an article's text meets the query (see [match]). */
    data class Match(
        /** Groups found in the headline. */
        val inHeadline: Int,
        /** Groups found in the headline or the teaser. */
        val inText: Int,
        /** Groups found in the publisher's name or domain. */
        val inPublisher: Int,
        /** All query words in a row in the headline (two words or more), or a quoted phrase there. */
        val phraseInHeadline: Boolean,
        /** Ticker groups found in the headline. */
        val tickersInHeadline: Int,
        /** An excluded term is in the headline or teaser. */
        val excluded: Boolean,
        /** Groups of the query. */
        val groups: Int,
        /**
         * The same as weights: a group with a ticker weighs [TICKER_WEIGHT] – "SLV" tells an
         * SLV story from any silver-ETF story, "ETF" does not.
         */
        val weightInHeadline: Int = inHeadline,
        val weightInText: Int = inText,
        val weight: Int = groups,
    ) {
        /** Every group is in the headline or teaser. */
        val complete: Boolean get() = inText == groups
        val sharesAnyTerm: Boolean get() = inText > 0 || inPublisher > 0
    }

    /** Whether the query has anything to look for in an article's text (not only operators). */
    val hasTerms: Boolean get() = groups.isNotEmpty()

    /** True when the query looks for a ticker, ISIN or WKN. */
    val hasTicker: Boolean get() = groups.any { group -> group.any { it is Term.Ticker } }

    /** How [headline], [summary] and [publisher] (name and domain) meet the query. */
    fun match(headline: String, summary: String = "", publisher: String = ""): Match {
        val head = Tokens.of(headline)
        val body = Tokens.of(summary)
        val from = Tokens.of(publisher)
        var inHeadline = 0
        var inText = 0
        var inPublisher = 0
        var tickers = 0
        var weightInHeadline = 0
        var weightInText = 0
        var weight = 0
        for (group in groups) {
            val w = if (group.any { it is Term.Ticker }) TICKER_WEIGHT else 1
            weight += w
            val h = group.any { head.has(it) }
            if (h) {
                inHeadline++
                weightInHeadline += w
            }
            if (h || group.any { body.has(it) }) {
                inText++
                weightInText += w
            }
            if (group.any { from.has(it) }) inPublisher++
            if (h && group.any { it is Term.Ticker && head.has(it) }) tickers++
        }
        val excluded = excluded.any { head.has(it) || body.has(it) }
        return Match(inHeadline, inText, inPublisher, phraseIn(head), tickers, excluded, groups.size, weightInHeadline, weightInText, weight)
    }

    /**
     * True when [headline] plus [summary] hold every group and no excluded term and, for a
     * query with `site:`, [domain] is one of its sites – what a cached article must meet to be
     * a search result.
     */
    fun matchesAll(headline: String, summary: String, domain: String = ""): Boolean {
        if (groups.isEmpty()) return false
        if (sites.isNotEmpty() && sites.none { site -> domain == site || domain.endsWith(".$site") }) return false
        val text = Tokens.of("$headline \n $summary")
        return groups.all { group -> group.any { text.has(it) } } && excluded.none { text.has(it) }
    }

    /** All plain words of a multi-word query in a row, or any quoted phrase, in [head]. */
    private fun phraseIn(head: Tokens): Boolean {
        if (groups.any { group -> group.any { it is Term.Phrase && it.exact && head.has(it) } }) return true
        val single = groups.filter { it.size == 1 }.map { it.single() }
        if (single.size < 2 || single.size != groups.size) return false
        val words = single.flatMap { term ->
            when (term) {
                is Term.Word -> listOf(term.token)
                is Term.Ticker -> term.parts.map { fold(it) }
                is Term.Phrase -> term.tokens
            }
        }
        return head.hasRun(words, exact = false)
    }

    override fun toString(): String = "SearchQuery($text → $googleQuery)"

    /** The words of a text, as typed and folded, for matching. */
    private class Tokens(val raw: List<String>, val folded: List<String>) {
        fun has(term: Term): Boolean = when (term) {
            is Term.Word -> folded.any { wordMatches(term.token, it) }
            is Term.Phrase -> hasRun(term.tokens, term.exact)
            is Term.Ticker -> tickerAt(term)
        }

        fun hasRun(words: List<String>, exact: Boolean): Boolean {
            if (words.isEmpty() || words.size > folded.size) return false
            for (start in 0..folded.size - words.size) {
                if (words.indices.all { i -> if (exact) folded[start + i] == words[i] else wordMatches(words[i], folded[start + i]) }) return true
            }
            return false
        }

        private fun tickerAt(term: Term.Ticker): Boolean {
            val parts = term.parts
            if (parts.size > raw.size) return false
            for (start in 0..raw.size - parts.size) {
                if (parts.indices.all { i -> tickerPart(parts[i], raw[start + i], term.strictCase, last = i == parts.lastIndex) }) return true
            }
            return false
        }

        companion object {
            fun of(text: String): Tokens {
                val raw = text.split(SPLIT).filter { it.isNotEmpty() }
                return Tokens(raw, raw.map { fold(it) })
            }
        }
    }

    companion object {
        /** A ticker group counts this many times in [Match.weight]. */
        const val TICKER_WEIGHT = 2

        /** Parses [query] (see the class description). */
        fun parse(query: String): SearchQuery {
            val text = query.trim().replace(WHITESPACE, " ")
            val pieces = split(text)
            val google = ArrayList<String>()
            val groups = ArrayList<MutableList<Term>>()
            val excluded = ArrayList<Term>()
            val sites = ArrayList<String>()
            var or = false
            val positives = pieces.filter { !it.negated && it.operator == null && !it.isOr }
            for (piece in pieces) {
                when {
                    piece.isOr -> {
                        // "OR" joins the terms around it; at the start, twice or at the end it means nothing.
                        if (groups.isNotEmpty() && google.lastOrNull() != "OR") {
                            or = true
                            google += "OR"
                        }
                    }
                    piece.operator != null -> {
                        google += piece.text
                        if (piece.operator == "site" && !piece.negated) {
                            piece.text.substringAfter(':').trim().lowercase(Locale.ROOT).removePrefix("www.").takeIf { it.isNotEmpty() }?.let { sites += it }
                        }
                    }
                    else -> {
                        val ticker = piece.ticker ?: softTicker(piece, positives)
                        val term = ticker ?: termOf(piece)
                        google += when {
                            term == null -> piece.text
                            piece.negated -> piece.text
                            ticker != null -> "\"" + ticker.parts.joinToString(".") + "\""
                            else -> piece.text
                        }
                        if (term != null) {
                            if (piece.negated) {
                                excluded += term
                            } else if (or && groups.isNotEmpty()) {
                                groups.last() += term
                            } else {
                                groups += mutableListOf(term)
                            }
                        }
                        or = false
                    }
                }
            }
            // A dangling "OR" at the end is no operator for Google either.
            while (google.lastOrNull() == "OR") google.removeAt(google.lastIndex)
            return SearchQuery(text, google.joinToString(" "), groups, excluded, sites)
        }

        /** Lower case, accents and punctuation removed, single spaces: "„Börse: DAX-Rekord!“" → "borse dax rekord". */
        fun fold(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(MARKS, "")
                .lowercase(Locale.ROOT)
                .replace("ß", "ss")
                .replace(NON_WORD, " ")
                .trim()

        /**
         * Whether the text word [token] is the query word [word] (both folded): the same, or one
         * of them with a common ending more ([ENDINGS]; words of three letters only an "s",
         * shorter ones exactly).
         */
        internal fun wordMatches(word: String, token: String): Boolean {
            if (token == word) return true
            if (word.length <= 2) return false
            if (token.length > word.length && token.startsWith(word)) {
                val rest = token.substring(word.length)
                return if (word.length == 3) rest == "s" else rest in ENDINGS
            }
            if (word.length > token.length && token.length >= 4 && word.startsWith(token)) {
                return word.substring(token.length) in ENDINGS
            }
            return false
        }

        /** A ticker part against a text word: whole word, in capitals when [strict]; the last part may take a plural "s" ("ETFs"). */
        private fun tickerPart(part: String, raw: String, strict: Boolean, last: Boolean): Boolean {
            val word = if (last && raw.length == part.length + 1 && raw.endsWith('s')) raw.dropLast(1) else raw
            return if (strict) word == part else word.equals(part, ignoreCase = true)
        }

        private fun termOf(piece: Piece): Term? {
            val words = fold(piece.body).split(' ').filter { it.isNotEmpty() }
            return when {
                words.isEmpty() -> null
                piece.quoted -> Term.Phrase(words, exact = true)
                words.size == 1 -> Term.Word(words.single())
                else -> Term.Phrase(words, exact = false)
            }
        }

        /**
         * A ticker typed in any case next to a finance word ("slv etf", "Slv Aktie"): a word of
         * 2–5 letters that is no common word itself.
         */
        private fun softTicker(piece: Piece, positives: List<Piece>): Term.Ticker? {
            if (piece.quoted || piece.negated) return null
            val body = piece.body.removePrefix("$")
            if (!SOFT_TICKER.matches(body)) return null
            val folded = fold(body)
            if (folded in FINANCE_WORDS || folded in COMMON_WORDS || body.uppercase(Locale.ROOT) in GENERIC_CAPS) return null
            val index = positives.indexOf(piece)
            if (index < 0) return null
            val near = listOfNotNull(positives.getOrNull(index - 1), positives.getOrNull(index + 1))
            if (near.none { fold(it.body) in FINANCE_WORDS }) return null
            return Term.Ticker(listOf(body), strictCase = false)
        }

        /** The query split into pieces: quoted phrases, words, `OR`, `-terms` and operators. */
        private fun split(text: String): List<Piece> {
            val pieces = ArrayList<Piece>()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == ' ') {
                    i++
                    continue
                }
                val negated = c == '-' && i + 1 < text.length && text[i + 1] != ' '
                val start = if (negated) i + 1 else i
                if (start < text.length && text[start] in OPEN_QUOTES) {
                    val close = CLOSE_QUOTES.indices.asSequence()
                        .map { text.indexOf(CLOSE_QUOTES[it], start + 1) }
                        .filter { it > 0 }
                        .minOrNull()
                    val end = close ?: text.length
                    val body = text.substring(start + 1, end).trim()
                    if (body.isNotEmpty()) pieces += Piece(text = (if (negated) "-" else "") + "\"" + body + "\"", body = body, quoted = true, negated = negated)
                    i = if (close == null) text.length else end + 1
                    continue
                }
                val end = text.indexOf(' ', start).let { if (it < 0) text.length else it }
                val word = text.substring(start, end)
                val raw = text.substring(i, end)
                i = end
                if (word.isEmpty()) continue
                val operator = OPERATOR.find(word)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
                pieces += when {
                    !negated && (word == "OR" || word == "|") -> Piece(text = "OR", body = "", isOr = true)
                    operator != null -> Piece(text = raw, body = word, negated = negated, operator = operator)
                    else -> Piece(text = raw, body = word, negated = negated, ticker = if (negated) null else tickerOf(word))
                }
            }
            return pieces
        }

        /** [word] as a ticker, ISIN or WKN typed in capitals; null when it is none. */
        private fun tickerOf(word: String): Term.Ticker? {
            val body = word.removePrefix("$").trimEnd(',', ';', '!', '?')
            if (body.isEmpty()) return null
            val exact = when {
                ISIN.matches(body) -> true
                WKN.matches(body) -> true
                TICKER.matches(body) -> body.substringBefore('.') !in GENERIC_CAPS
                else -> false
            }
            return if (exact) Term.Ticker(body.split('.'), strictCase = true) else null
        }

        private class Piece(
            /** As it goes to Google News. */
            val text: String,
            /** Without the minus and the quotes. */
            val body: String,
            val quoted: Boolean = false,
            val negated: Boolean = false,
            val isOr: Boolean = false,
            /** `site`, `intitle` … for an operator. */
            val operator: String? = null,
            val ticker: Term.Ticker? = null,
        )

        private val WHITESPACE = Regex("\\s+")
        private val MARKS = Regex("\\p{M}+")
        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

        private const val OPEN_QUOTES = "\"„“”«»‚‘'"
        private val CLOSE_QUOTES = listOf('"', '“', '”', '«', '»', '‘', '’', '\'')

        /** Google News operators the app passes through untouched. */
        private val OPERATOR = Regex("^(site|intitle|allintitle|inurl|intext|when|after|before|source|location):\\S", RegexOption.IGNORE_CASE)

        /** `SLV`, `QQQ`, `NVDA`, `BRK.B`, `2025` is no ticker (no letter). */
        private val TICKER = Regex("(?=[A-Z0-9]*[A-Z])[A-Z0-9]{2,5}(\\.[A-Z]{1,2})?")

        /** ISIN: country, nine characters, check digit (`US46428Q1094`). */
        private val ISIN = Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]")

        /** WKN: six characters with a digit (`A0S9GB`, `870747`). */
        private val WKN = Regex("(?=[A-Z0-9]*[0-9])[A-Z0-9]{6}")

        /** A short word that can be a ticker typed in lower case. */
        private val SOFT_TICKER = Regex("\\p{L}{2,5}")

        /** Endings a word may take or lose and still be the same word ("Aktie"/"Aktien", "midterm"/"Midterms"). */
        private val ENDINGS = setOf("s", "es", "n", "en", "e", "er", "ern", "ens", "ns")

        /**
         * Capitals that are common abbreviations, not tickers: quoting them would cost Google's
         * synonyms ("ETF", "EU", "KI" …).
         */
        internal val GENERIC_CAPS = setOf(
            "ETF", "ETFS", "ETP", "ETC", "ETN", "EU", "US", "USA", "UK", "UN", "UNO", "KI", "AI", "CEO", "CFO", "CTO",
            "IPO", "NATO", "EZB", "ECB", "FED", "IWF", "IMF", "GDP", "BIP", "TV", "PC", "IT", "AG", "SE", "SA", "INC",
            "PLC", "LTD", "GMBH", "AND", "NOT", "OR",
        )

        /** Words next to which a short word is a ticker ("SLV ETF", "Rheinmetall Aktie" is no ticker: too long). */
        private val FINANCE_WORDS = setOf(
            "etf", "etfs", "etp", "etc", "etn", "aktie", "aktien", "stock", "stocks", "share", "shares", "fonds", "fund",
            "funds", "kurs", "ticker", "earnings", "dividende", "dividend", "optionen", "options", "anleihe", "bond",
            "trust", "zertifikat", "action", "actions", "accion", "acciones", "azioni", "azione",
        )

        /** Short common words that are never tickers ("die Aktie", "the stock"). */
        private val COMMON_WORDS = setOf(
            "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "und", "oder", "von", "vom", "zur", "zum",
            "mit", "auf", "aus", "bei", "nach", "fur", "uber", "ist", "sind", "wie", "was", "neue", "neuer", "the", "a",
            "an", "and", "or", "of", "for", "to", "in", "on", "with", "new", "best", "top", "is", "are", "how", "why",
            "what", "buy", "sell", "le", "la", "les", "des", "du", "un", "une", "et", "el", "los", "las", "il", "lo",
            "gli", "di", "da", "gold", "oil", "ol", "silber", "silver", "euro", "dollar", "world", "welt", "msci",
        )
    }
}

package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import kotlin.math.roundToInt

/*
 * The reader's short version (unit-tested): the beginning of a long article, about as long as an
 * answer in Perplexity, with "Ganzen Artikel lesen" below it. Pure functions on the arranged
 * blocks – the extractor, the cache and the prefetcher keep the whole text.
 */

/** Only an article longer than this many words gets a short version. */
internal const val SHORT_FROM_WORDS = 350

/** The short version stops once it has this many words … */
internal const val SHORT_TARGET_WORDS = 220

/** … and never takes a whole paragraph that would carry it past this many. */
internal const val SHORT_MAX_WORDS = 300

/** A short version that leaves less than this unread is not worth the extra tap. */
internal const val SHORT_MIN_REST_WORDS = 80

/** Below this the short version cuts into the next paragraph instead of stopping (it would be a stub). */
private const val SHORT_MIN_WORDS = 150

/** A short version shorter than this is a stub: the article stays whole. */
private const val SHORT_MIN_KEEP_WORDS = 100

/** Reading speed for "noch X Min.". */
private const val WORDS_PER_MINUTE = 200

/**
 * A long article split for the reader: [short] is shown first, [full] after "Ganzen Artikel
 * lesen". [short] is a prefix of [full] block by block – a paragraph cut at a sentence is two
 * blocks in [full] – so opening the rest only adds blocks below and the text never moves.
 *
 * @property restWords the words [full] has beyond [short].
 */
internal data class ShortVersion(val short: List<ArticleBlock>, val full: List<ArticleBlock>, val restWords: Int) {
    /** "noch X Min.": the rest at [WORDS_PER_MINUTE], at least one minute. */
    val restMinutes: Int get() = (restWords / WORDS_PER_MINUTE.toFloat()).roundToInt().coerceAtLeast(1)
}

/** The words of [block] that are read (pictures and captions have none). */
internal fun wordsOf(block: ArticleBlock): Int = when (block.type) {
    ArticleBlock.PARAGRAPH, ArticleBlock.HEADING, ArticleBlock.QUOTE -> countWords(block.text)
    ArticleBlock.LIST -> block.items.sumOf(::countWords)
    else -> 0
}

private fun countWords(text: String): Int = text.split(' ', '\n', '\t').count { it.isNotBlank() }

/**
 * The short version of [blocks], or null when the article stays whole: it has at most
 * [SHORT_FROM_WORDS] words, the short version would be a stub, or the cut would leave less than
 * [SHORT_MIN_REST_WORDS] unread.
 *
 * Whole blocks are taken until [SHORT_TARGET_WORDS] are reached; a block that would carry the
 * short version past [SHORT_MAX_WORDS] stops it. A paragraph that is too long to take whole
 * while the short version is still a stub (the first one, typically) is cut at the last sentence
 * end that fits. A heading never ends the short version.
 */
internal fun shorten(blocks: List<ArticleBlock>): ShortVersion? {
    val total = blocks.sumOf(::wordsOf)
    if (total <= SHORT_FROM_WORDS) return null
    val short = ArrayList<ArticleBlock>()
    val full = ArrayList<ArticleBlock>(blocks.size + 1)
    var words = 0
    var index = 0
    while (index < blocks.size && words < SHORT_TARGET_WORDS) {
        val block = blocks[index]
        val w = wordsOf(block)
        if (words + w <= SHORT_MAX_WORDS) {
            short += block
            full += block
            words += w
            index++
            continue
        }
        if (words < SHORT_MIN_WORDS && block.type == ArticleBlock.PARAGRAPH) {
            val (head, tail) = cutAtSentence(block.text, target = SHORT_TARGET_WORDS - words, max = SHORT_MAX_WORDS - words)
            if (head.isNotEmpty() && tail.isNotEmpty()) {
                short += ArticleBlock.paragraph(head)
                full += ArticleBlock.paragraph(head)
                full += ArticleBlock.paragraph(tail)
                words += countWords(head)
                index++
            }
        }
        break
    }
    // A heading at the end would announce text that only comes after the tap.
    while (short.isNotEmpty() && short.last().type == ArticleBlock.HEADING) {
        words -= wordsOf(short.removeAt(short.lastIndex))
    }
    if (words < SHORT_MIN_KEEP_WORDS) return null
    val rest = total - words
    if (rest < SHORT_MIN_REST_WORDS) return null
    full += blocks.subList(index, blocks.size)
    return ShortVersion(short = short, full = full, restWords = rest)
}

/**
 * [text] split after the last sentence that keeps the head within [max] words, stopping as soon
 * as it reaches [target]: (head, tail). The head is empty when not even the first sentence fits;
 * the tail is empty when the text has no sentence end to cut at before its end.
 */
internal fun cutAtSentence(text: String, target: Int, max: Int): Pair<String, String> {
    val ends = sentenceEnds(text)
    var cut = -1
    for (end in ends) {
        val words = countWords(text.substring(0, end))
        if (words > max) break
        cut = end
        if (words >= target) break
    }
    if (cut <= 0) return "" to text.trim()
    return text.substring(0, cut).trim() to text.substring(cut).trim()
}

/**
 * The positions right after each sentence end in [text]: ".", "!", "?" or "…" (with closing
 * quotes or brackets), followed by a space and an upper-case letter, a digit or an opening quote.
 * A period after a common abbreviation ("z. B.", "Dr.", "Nr.") or a single letter is no end.
 */
private fun sentenceEnds(text: String): List<Int> {
    val out = ArrayList<Int>()
    for (match in SENTENCE_END.findAll(text)) {
        val end = match.range.first + match.groupValues[1].length
        val before = text.substring(0, match.range.first + 1)
        val word = LAST_WORD.find(before)?.groupValues?.get(1).orEmpty()
        if (match.groupValues[1].startsWith(".") && (word.length <= 1 || word.lowercase() in ABBREVIATIONS)) continue
        out += end
    }
    return out
}

private val SENTENCE_END = Regex("""([.!?…]+["“”»«'’)\]]*)\s+(?=[\p{Lu}\d„"“«‚'])""")

/** The word right before a period at the end of the text given. */
private val LAST_WORD = Regex("""([\p{L}]+)\.$""")

private val ABBREVIATIONS = setOf(
    "dr", "prof", "nr", "st", "bzw", "ca", "vgl", "inkl", "usw", "sog", "evtl", "ggf", "mio", "mrd", "abs", "art",
    "mr", "mrs", "ms", "jr", "sr", "vs", "etc", "no", "jan", "feb", "aug", "sept", "okt", "nov", "dez", "dec", "oct",
    "mme", "mlle", "sig", "dott", "sra", "dña",
)

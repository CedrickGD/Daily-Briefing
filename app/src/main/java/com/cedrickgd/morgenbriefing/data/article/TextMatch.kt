package com.cedrickgd.morgenbriefing.data.article

import java.util.Locale

/**
 * Fuzzy text comparison for the reader's duplicate checks: is this paragraph the headline again,
 * the teaser again (cut with "…", with a dateline or a different quote style), or the same
 * paragraph a second time? Texts are compared as word pairs (shingles) after folding case,
 * punctuation and typographic quotes away, so small edits do not hide a repeat and a shared
 * vocabulary alone does not make two different sentences "the same".
 */
internal object TextMatch {

    /** Fewer words than this are compared exactly (after normalising), never fuzzily. */
    const val MIN_FUZZY_WORDS = 6

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val ELLIPSIS = Regex("(\\s*(…|\\.\\.\\.|\\[…]|\\[\\.\\.\\.]))+\\s*$")

    /** Lower case, letters and digits only, single spaces. */
    fun normalize(text: String): String = NON_WORD.replace(text.lowercase(Locale.ROOT), " ").trim()

    fun words(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    /** The text without a trailing ellipsis ("Der Kanzler sagte…" → "Der Kanzler sagte"). */
    fun withoutEllipsis(text: String): String = ELLIPSIS.replace(text, "")

    fun endsWithEllipsis(text: String): Boolean = ELLIPSIS.containsMatchIn(text)

    private fun shingles(words: List<String>): Set<String> =
        if (words.size < 2) words.toSet() else words.zipWithNext { a, b -> "$a $b" }.toSet()

    /** Share of [part]'s word pairs that also occur in [whole] (0..1). */
    fun containment(part: String, whole: String): Double = containment(words(part), words(whole))

    private fun containment(part: List<String>, whole: List<String>): Double {
        val p = shingles(part)
        if (p.isEmpty()) return 0.0
        val w = shingles(whole)
        return p.count { it in w }.toDouble() / p.size
    }

    /**
     * True when [a] and [b] say the same: equal after normalising, or (both long enough) at least
     * [threshold] of each one's word pairs occur in the other.
     */
    fun same(a: String, b: String, threshold: Double = 0.85): Boolean {
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return false
        if (wa == wb) return true
        if (wa.size < MIN_FUZZY_WORDS || wb.size < MIN_FUZZY_WORDS) return false
        // Templated paragraphs ("Wahlkreis 12: 48 Prozent", "Wahlkreis 13: 51 Prozent") differ in their numbers only.
        if (numbers(wa) != numbers(wb)) return false
        return containment(wa, wb) >= threshold && containment(wb, wa) >= threshold
    }

    /**
     * True when [a] and [b] say the same in slightly different words – the teaser the page's
     * own lead paragraph rewords ("Trump said he intends to keep Hegseth … as Rep. Massie
     * prepares to introduce …" vs "Trump said Thursday he intends to keep Hegseth … as GOP Rep.
     * Thomas Massie prepares to reintroduce …": 73 % of the word pairs shared, below [same]'s
     * threshold). Only for the teaser against the lead: both at least [PARAPHRASE_MIN_WORDS]
     * long, of similar length, the same numbers, and at least [threshold] of each one's word
     * pairs in the other.
     */
    fun paraphrase(a: String, b: String, threshold: Double = 0.6): Boolean {
        val wa = words(a)
        val wb = words(b)
        if (wa.size < PARAPHRASE_MIN_WORDS || wb.size < PARAPHRASE_MIN_WORDS) return false
        val ratio = wa.size.toDouble() / wb.size
        if (ratio < 0.6 || ratio > 1.0 / 0.6) return false
        if (numbers(wa) != numbers(wb)) return false
        return containment(wa, wb) >= threshold && containment(wb, wa) >= threshold
    }

    /** Shorter texts are too easy to confuse to call a rewording the same. */
    const val PARAPHRASE_MIN_WORDS = 12

    private fun numbers(words: List<String>): Set<String> = words.filter { w -> w.any(Char::isDigit) }.toSet()

    /**
     * True when [whole] repeats all of [part] (and maybe more): [part] occurs literally (after
     * normalising, at least three words) or, fuzzily, at least [threshold] of its word pairs.
     */
    fun covers(whole: String, part: String, threshold: Double = 0.85): Boolean {
        val wp = words(part)
        if (wp.isEmpty()) return false
        val ww = words(whole)
        if (wp.size > ww.size + 2) return false
        if (wp.size >= 3 && " ${ww.joinToString(" ")} ".contains(" ${wp.joinToString(" ")} ")) return true
        if (wp.size < MIN_FUZZY_WORDS) return wp == ww
        if (!numbers(ww).containsAll(numbers(wp.dropLast(1)))) return false
        return containment(wp, ww) >= threshold
    }

    /**
     * True when [text] begins with [lead] cut short (a feed teaser that ends with "…"): every
     * word of the lead but the last, possibly cut one, opens [text].
     */
    fun startsWithCut(text: String, lead: String): Boolean {
        val wl = words(withoutEllipsis(lead))
        if (wl.size < MIN_FUZZY_WORDS) return false
        val wt = words(text)
        val full = wl.dropLast(1)
        if (wt.size < full.size || wt.subList(0, full.size) != full) return false
        val cut = wl.last()
        return wt.size > full.size && wt[full.size].startsWith(cut)
    }

    /**
     * The longest run of words [a] and [b] have in common, in words: two versions of one headline
     * ("JP Kraemer spricht nach 11 Wochen: 231 Mal „Ich“, aber kein Wort über sein Opfer" and
     * "Ehefrau misshandelt: JP Kraemer sagt 231 Mal „Ich“, aber kein Wort über sein Opfer") share
     * a long one, two different sentences hardly more than a name.
     */
    fun sharedRun(a: String, b: String): Int {
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return 0
        var best = 0
        var previous = IntArray(wb.size + 1)
        for (x in wa.indices) {
            val current = IntArray(wb.size + 1)
            for (y in wb.indices) {
                if (wa[x] == wb[y]) {
                    current[y + 1] = previous[y] + 1
                    if (current[y + 1] > best) best = current[y + 1]
                }
            }
            previous = current
        }
        return best
    }

    private val SENTENCE_BREAK = Regex("(?<=[.!?…])[\"“”»«')\\]]*\\s+")

    /** [text] cut into its sentences (at ". ", "! ", "? ", "… ", closing quotes kept with their sentence). */
    fun sentences(text: String): List<String> = SENTENCE_BREAK.split(text.trim()).filter { it.isNotBlank() }

    /** True when the words [part] stand in [whole] in this order, one after the other. */
    fun containsRun(whole: List<String>, part: List<String>): Boolean {
        if (part.isEmpty() || part.size > whole.size) return false
        return " ${whole.joinToString(" ")} ".contains(" ${part.joinToString(" ")} ")
    }

    /** Number of words, as the reader counts them. */
    fun wordCount(text: String): Int = text.split(' ').count { it.isNotBlank() }
}

package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Is the text the reader extracted the whole article, or only the part a newsroom gives away?
 * The strict judgement behind "nur vollständige Artikel": only a complete article counts as
 * verified ([com.cedrickgd.morgenbriefing.data.model.ArticleStatus.READABLE]); the reader's
 * "das war vermutlich nur der Anfang" note follows it too. Pure, unit-tested on real pages
 * (`app/src/test/resources/article/`).
 *
 * The extraction itself must have succeeded (no paywall, consent or bot wall – those never come
 * here). The page must hold an article ([Gap.NO_ARTICLE] otherwise: a site's front page the link
 * landed on, a video, audio or podcast page with a few lines of description, a page of figures
 * and labels like a stock quote, or a page that has nothing to do with the feed's headline). On
 * top of that a text is a teaser when
 *
 * - the page shows subscriber marks ([ArticleContent.paywallHints]: "Weiterlesen mit …" at the
 *   end, a paid brand mark, a paywall box),
 * - the publisher declares a clearly longer article (JSON-LD `wordCount`) than was found,
 * - a short text ends cut off ("…", "Weiterlesen", "Read more"),
 * - nothing is left besides the lead: without the paragraphs that only repeat the page's or the
 *   feed's teaser, fewer than [MIN_BODY_WORDS] words,
 * - it is short ([ArticleContent.SHORT_WORDS]) and has fewer than [MIN_SHORT_PARAGRAPHS] text
 *   paragraphs or fewer than [MIN_SHORT_WORDS] words. A page the publisher marks as free
 *   (`isAccessibleForFree: true`) needs only [MIN_FREE_WORDS].
 *
 * A genuine short news item of a public broadcaster (Deutschlandfunk: three paragraphs, no
 * marks, no declared length) is complete.
 */
object ArticleCompleteness {

    /** Why a text is not the whole article. */
    enum class Gap { NO_ARTICLE, PAYWALL_MARKS, DECLARED_LONGER, CUT_OFF, ONLY_LEAD, TOO_SHORT }

    /** Below this many words a text needs several paragraphs to count as complete. */
    const val MIN_SHORT_WORDS = 50

    /** Text paragraphs a short text needs. */
    const val MIN_SHORT_PARAGRAPHS = 2

    /** Words a text the publisher marks as free needs. */
    const val MIN_FREE_WORDS = 40

    /** Words beyond the teaser a text needs. */
    const val MIN_BODY_WORDS = 30

    /** Found / declared words below this share: only part of the article. */
    const val DECLARED_SHARE = 0.6

    /** Declared lengths below this are not trusted (some sites declare the teaser's length). */
    const val MIN_DECLARED_WORDS = 80

    /** From this length a trailing "…" is a teaser of something else, not a cut. */
    const val LONG_WORDS = 400

    /** A video, audio or podcast page with fewer words is the media's description, not an article. */
    const val MEDIA_MAX_WORDS = 300

    /** A paragraph with this many words is prose (a page of labels and figures has hardly any). */
    const val PROSE_WORDS = 15

    /**
     * Why [content] is not the whole article, or null when it is. [teaser] is the feed's summary
     * of it, [headline] the feed's headline (both may be empty).
     */
    fun gap(content: ArticleContent, teaser: String = "", headline: String = ""): Gap? {
        val words = content.wordCount
        val paragraphs = content.blocks.filter { it.isText() }
        if (paragraphs.isEmpty() || isNoArticle(content, paragraphs, headline)) return Gap.NO_ARTICLE
        if (content.paywallHints) return Gap.PAYWALL_MARKS
        if (content.declaredWords >= MIN_DECLARED_WORDS && words < content.declaredWords * DECLARED_SHARE) return Gap.DECLARED_LONGER
        if (words < LONG_WORDS && paragraphs.last().endsCutOff()) return Gap.CUT_OFF
        val leads = listOf(content.description, teaser).map { it.trim() }.filter { it.isNotEmpty() }
        val body = paragraphs.filterNot { block -> leads.any { lead -> repeats(lead, block.plain()) } }
        if (body.sumOf { TextMatch.wordCount(it.plain()) } < MIN_BODY_WORDS) return Gap.ONLY_LEAD
        if (words < ArticleContent.SHORT_WORDS) {
            val enough = if (content.free == true) words >= MIN_FREE_WORDS else words >= MIN_SHORT_WORDS && paragraphs.size >= MIN_SHORT_PARAGRAPHS
            if (!enough) return Gap.TOO_SHORT
        }
        return null
    }

    /** True when [content] is the whole article ([gap] finds nothing). */
    fun isComplete(content: ArticleContent, teaser: String = "", headline: String = ""): Boolean = gap(content, teaser, headline) == null

    /** True when [content] is the whole article behind the feed's [item]. */
    fun isComplete(content: ArticleContent, item: NewsItem): Boolean = isComplete(content, item.summary, item.headline)

    /** A front page, a media page, a page of labels and figures, or a page about something else. */
    private fun isNoArticle(content: ArticleContent, paragraphs: List<ArticleBlock>, headline: String): Boolean {
        val url = content.url.toHttpUrlOrNull()
        if (url != null) {
            val segments = url.pathSegments.filter { it.isNotBlank() }
            if (segments.isEmpty()) return true
            if (content.wordCount < MEDIA_MAX_WORDS && segments.dropLast(1).any { it.lowercase() in MEDIA_SEGMENTS }) return true
        }
        // A short page of labels and figures (a stock quote); a long listing (a fixture list) is an article's content.
        val prose = paragraphs.count { it.type != ArticleBlock.LIST && TextMatch.wordCount(it.plain()) >= PROSE_WORDS }
        if (content.wordCount < ArticleContent.SHORT_WORDS && paragraphs.size >= FRAGMENT_BLOCKS && prose < 3 && prose * 4 < paragraphs.size) return true
        val wanted = TextMatch.words(headline).filter { it.length >= SIGNIFICANT_LETTERS }.toSet()
        if (wanted.isNotEmpty()) {
            val page = (listOf(content.title, content.description) + paragraphs.take(3).map { it.plain() }).flatMap(TextMatch::words).toHashSet()
            if (wanted.none { it in page }) return true
        }
        return false
    }

    /** Text blocks from which a page with hardly any prose is a page of labels and figures. */
    private const val FRAGMENT_BLOCKS = 6

    /** Headline words this long (or longer) say what the article is about ("der", "und" do not). */
    private const val SIGNIFICANT_LETTERS = 5

    /** Path segments (before the last one) of pages whose content is a video, a recording or a podcast. */
    private val MEDIA_SEGMENTS = setOf("video", "videos", "audio", "audios", "podcast", "podcasts", "av", "mediathek", "sendung", "sendungen")

    private fun ArticleBlock.isText(): Boolean = when (type) {
        ArticleBlock.PARAGRAPH, ArticleBlock.QUOTE -> text.isNotBlank()
        ArticleBlock.LIST -> items.isNotEmpty()
        else -> false
    }

    private fun ArticleBlock.plain(): String = if (type == ArticleBlock.LIST) items.joinToString(" ") else text

    private fun ArticleBlock.endsCutOff(): Boolean {
        val text = plain().trim()
        return TextMatch.endsWithEllipsis(text) || READ_ON.containsMatchIn(text.takeLast(80))
    }

    /** [paragraph] says nothing the [lead] does not: the lead again, or a part of it. */
    private fun repeats(lead: String, paragraph: String): Boolean =
        TextMatch.same(lead, paragraph) || TextMatch.covers(lead, paragraph) || TextMatch.startsWithCut(lead, paragraph)

    /** A last line that sends the reader elsewhere for the rest. */
    private val READ_ON = Regex(
        "(?i)(weiterlesen|weiter lesen|mehr lesen|den (ganzen|vollständigen) (artikel|beitrag|text)|zum (ganzen|vollständigen) (artikel|beitrag)|" +
            "read (the )?(full|more|rest)|continue reading|full (story|article)|lire la suite|leer más|continua a leggere|leggi (tutto|l'articolo))\\W*$",
    )
}

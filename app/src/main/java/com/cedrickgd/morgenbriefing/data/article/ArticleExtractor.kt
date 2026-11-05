package com.cedrickgd.morgenbriefing.data.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import com.cedrickgd.morgenbriefing.data.news.HtmlText
import net.dankito.readability4j.Readability4J
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns a newsroom's web page into the reader's blocks: Mozilla's Readability algorithm (via
 * Readability4J) finds the article, this walks the result into paragraphs, headings, quotes,
 * lists and pictures and [ArticleCleaner] takes out what would show twice. Pure Kotlin (jsoup),
 * unit-tested; no JavaScript, no ads, no cookie banners.
 *
 * When Readability picks too little (it sometimes settles on the header of a page whose text
 * sits in many sibling containers), the container holding most of the page's paragraphs is
 * read instead; when the page publishes its full text openly in its JSON-LD (`articleBody` of
 * an article not marked as paid), that is the last resort. A page the publisher marks as paid
 * (`isAccessibleForFree: false`) or that ends in a paywall notice is a [ReadOutcome.PAYWALL].
 */
object ArticleExtractor {

    /** What a page turned out to be. [content] is set whenever enough text was found, also for walls. */
    data class Page(
        val content: ArticleContent?,
        val outcome: ReadOutcome,
        /** The page's AMP version (`<link rel="amphtml">`), offered openly by the newsroom. */
        val ampUrl: String? = null,
    )

    /**
     * The readable article of [html] (loaded from [url]), or null when the page holds no article
     * worth showing (a landing page, a video page, an empty paywall shell). Walls (consent,
     * paywall) still come back – see [read] for the verdict.
     *
     * @param now stamp for [ArticleContent.fetchedAt].
     */
    fun extract(html: String, url: String, now: ZonedDateTime = ZonedDateTime.now()): ArticleContent? = read(html, url, now).content

    /** Reads [html] (loaded from [url]): the content and what kind of page it is. */
    fun read(html: String, url: String, now: ZonedDateTime = ZonedDateTime.now()): Page {
        val document = Jsoup.parse(html, url)
        val meta = Meta.of(document)
        val structured = PageSignals.structuredArticle(document)
        val title = meta.title.ifEmpty { structured?.headline.orEmpty() }
        val description = meta.description.ifEmpty { structured?.description.orEmpty() }.let(::clean).takeUnless { TextMatch.same(it, title) }.orEmpty()

        unwrapNoscriptImages(document)
        val readability = try {
            Readability4J(url, document.outerHtml()).parse()
        } catch (e: Exception) {
            null
        } catch (e: StackOverflowError) {
            null
        }
        val headline = title.ifEmpty { readability?.title.orEmpty().trim() }
        var blocks = readability?.articleContent?.let { walk(it, url, headline) }.orEmpty()

        // Readability settled on too little – nothing much, or one of several sibling sections
        // (WDR puts every paragraph group into its own module): the container that holds most
        // of the page's paragraphs, when it says clearly more and includes what Readability found.
        densestContainer(document)?.let { root ->
            val dense = walk(root, url, headline, strict = true)
            val found = words(blocks)
            val more = words(dense)
            val short = found < FALLBACK_WORDS && more >= maxOf(FALLBACK_WORDS, found * 3 / 2)
            val superset = more >= found * 13 / 10 + SUPERSET_MIN_GAIN && contains(dense, blocks)
            if (short || superset) blocks = dense
        }
        // The full text the publisher openly publishes in its structured data (never for paid articles).
        val body = structured?.body.orEmpty()
        if (structured?.free != false && words(blocks) < ArticleContent.SHORT_WORDS && TextMatch.wordCount(body) >= maxOf(ArticleContent.SHORT_WORDS / 2, words(blocks) * 2)) {
            blocks = blocks.filter { it.type == ArticleBlock.IMAGE }.take(1) + paragraphsOf(body)
        }

        blocks = ArticleCleaner.clean(blocks, headline, description).take(MAX_BLOCKS)
        val wordCount = words(blocks)
        val content = if (wordCount < MIN_WORDS) {
            null
        } else {
            ArticleContent(
                url = url,
                title = headline,
                blocks = blocks,
                image = meta.image?.takeIf { it.isUsableImage() },
                site = meta.site,
                fetchedAt = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                description = description,
                language = meta.language,
                format = ArticleContent.FORMAT,
            )
        }
        val text = content?.text.orEmpty()
        val outcome = when {
            structured?.free == false -> ReadOutcome.PAYWALL
            content != null && looksLikeConsentWall(content) -> ReadOutcome.CONSENT
            content == null && PageSignals.isBotCheck(html) -> ReadOutcome.BLOCKED
            wordCount < PAYWALL_CHECK_WORDS && structured?.free != true && PageSignals.hasPaywallMarks(document, text) -> ReadOutcome.PAYWALL
            content == null -> ReadOutcome.NOT_ARTICLE
            else -> ReadOutcome.OK
        }
        return Page(content, outcome, meta.amp)
    }

    /**
     * True when [content] is a consent page, not the article: some newsrooms (golem.de) serve
     * "Cookies zustimmen" to every client without their consent cookie. Short, and about cookies.
     */
    fun looksLikeConsentWall(content: ArticleContent): Boolean {
        if (content.wordCount > WALL_MAX_WORDS) return false
        val head = content.text.take(2500)
        return CONSENT_WALL.containsMatchIn(head)
    }

    private const val WALL_MAX_WORDS = 450
    private val CONSENT_WALL = Regex(
        "(?i)(cookies? zustimmen|nutzung aller cookies|zustimmen und weiter|mit werbung und tracking|" +
            "pur-abo|werbefrei lesen|zustimmen oder abo|alle cookies akzeptieren|accept all cookies|" +
            "privacy manager|consent management|einwilligung in die (datenverarbeitung|nutzung))",
    )

    private const val MAX_BLOCKS = 400
    private const val MIN_WORDS = 20
    private const val FALLBACK_WORDS = 150
    private const val PAYWALL_CHECK_WORDS = 250
    private const val MIN_PARAGRAPH_CHARS = 2
    private const val SHORT_PARAGRAPH = 90
    private const val MIN_IMAGE_WIDTH = 200
    private const val MAX_SRCSET_WIDTH = 2000
    private const val DENSE_MIN_WORDS = 12
    private const val DENSE_SHARE = 0.8
    private const val SUPERSET_MIN_GAIN = 40
    private const val PARAGRAPH_LINK_SHARE = 0.3
    private const val LINKY_SHARE = 0.5
    private const val LINKY_MIN_CHARS = 20
    private const val TEASER_DEPTH = 3
    private const val TEASER_MAX_CHARS = 700
    private val CHROME_ROLES = setOf("dialog", "alertdialog", "navigation", "banner", "contentinfo", "complementary", "search")
    private val INLINE_OK = setOf("img", "picture", "figure", "br", "hr")

    /** Short lines newsrooms sprinkle between paragraphs: teasers, share bars, notices. */
    private val JUNK = Regex(
        "(?i)^(anzeige|werbung|advertisement|lesen sie auch|lese-tipp|lesetipp|mehr zum thema|auch interessant|" +
            "zur startseite|artikel teilen|teilen|drucken|jetzt abonnieren|newsletter|alle rechte vorbehalten|" +
            "folgen sie uns|zum thema|weitere artikel|mehr dazu|das könnte sie auch interessieren|" +
            "empfehlungen|bildergalerie|video|quelle|read more|related|recommended|advertisement - scroll to continue)[:\\s.…!-]*$",
    )

    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val SKIPPED = setOf("script", "style", "iframe", "form", "nav", "aside", "footer", "header", "button", "svg", "noscript", "template", "video", "audio")
    private val BLOCKS = setOf("p", "div", "section", "article", "main", "ul", "ol", "blockquote", "figure", "table", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "picture", "img", "hr", "br", "li", "dl")

    /** Containers that are not the article (for the paragraph-density fallback). */
    private val NOT_CONTENT = Regex("(?i)^(nav|navigation|menu|footer|header|sidebar|related|recommend\\w*|teaser|promo|comments?|newsletter|social|share|cookie|consent|banner|breadcrumbs?|outbrain|taboola)([-_].*)?$")

    /** True when one of [element]'s class names (or its id) starts with a [NOT_CONTENT] word: `related-articles`, `nav`. */
    private fun isNotContent(element: Element): Boolean =
        (element.classNames() + element.id()).any { it.isNotEmpty() && NOT_CONTENT.matches(it) }
    private val DENSE_SKIPPED = setOf("nav", "aside", "footer", "header", "form", "noscript", "template")

    private fun words(blocks: List<ArticleBlock>): Int = blocks.sumOf { block ->
        when (block.type) {
            ArticleBlock.PARAGRAPH, ArticleBlock.HEADING, ArticleBlock.QUOTE -> TextMatch.wordCount(block.text)
            ArticleBlock.LIST -> block.items.sumOf(TextMatch::wordCount)
            else -> 0
        }
    }

    /**
     * Lazy-loading pages show a placeholder `<img>` and put the real one into a `<noscript>`
     * right after it (for clients without scripts – like this reader). The real one takes the
     * placeholder's place before Readability sees the page.
     */
    private fun unwrapNoscriptImages(document: Document) {
        for (noscript in document.select("noscript")) {
            val placeholder = noscript.previousElementSibling()?.takeIf { it.normalName() == "img" }
                ?: noscript.parent()?.children()?.filter { it.normalName() == "img" }?.singleOrNull()
                ?: continue
            if (bestUrl(placeholder, document.location()) != null) continue
            val real = noscript.selectFirst("img")
                ?: Jsoup.parseBodyFragment(noscript.data().ifBlank { noscript.html() }, document.location()).selectFirst("img")
                ?: continue
            if (bestUrl(real, document.location()) == null) continue
            placeholder.replaceWith(real.clone())
            noscript.remove()
        }
    }

    /**
     * The best usable URL of [img]: the widest `srcset` candidate (at most 2000 w), then a
     * `<picture>`'s `<source>`s ([sources]), then lazy-loading attributes before a placeholder
     * `src`; null when none is a picture the app can show.
     */
    private fun bestUrl(img: Element, base: String, sources: Element? = null): String? {
        val candidates = ArrayList<String>()
        listOf("srcset", "data-srcset", "data-lazy-srcset").mapNotNullTo(candidates) { bestOf(srcset(img.attr(it))) }
        sources?.select("source")?.flatMap { srcset(it.attr("srcset")) + srcset(it.attr("data-srcset")) }?.let(::bestOf)?.let(candidates::add)
        listOf("data-src", "data-lazy-src", "data-original", "data-full-src", "data-hi-res-src", "data-lazy", "data-url", "src")
            .mapNotNullTo(candidates) { img.attr(it).trim().takeIf { v -> v.isNotEmpty() }?.substringBefore(' ')?.substringBefore(',') }
        return candidates.asSequence().mapNotNull { resolve(base, it) }.firstOrNull { it.isUsableImage() }
    }

    /** True when every long paragraph of [part] also appears in [whole]. */
    private fun contains(whole: List<ArticleBlock>, part: List<ArticleBlock>): Boolean {
        val keys = whole.filter { it.type == ArticleBlock.PARAGRAPH }.mapTo(HashSet()) { TextMatch.normalize(it.text) }
        val long = part.filter { it.type == ArticleBlock.PARAGRAPH && TextMatch.wordCount(it.text) >= DENSE_MIN_WORDS }
        return long.isNotEmpty() && long.all { TextMatch.normalize(it.text) in keys }
    }

    private fun walk(root: Element, url: String, title: String, strict: Boolean = false): List<ArticleBlock> {
        val out = ArrayList<ArticleBlock>()
        Walker(url, title, out, strict).walk(root)
        return out
    }

    /** Share of [element]'s text that sits in links (0..1). */
    private fun linkDensity(element: Element): Double {
        val text = element.text().length
        if (text == 0) return 0.0
        return element.select("a").sumOf { it.text().length }.toDouble() / text
    }

    /** A teaser card, a link list, a dialog: not the article, even inside its container. */
    private fun isChromeBox(element: Element): Boolean =
        isNotContent(element) || element.attr("role") in CHROME_ROLES || element.hasAttr("aria-modal") ||
            (element.normalName() != "p" && element.text().length >= LINKY_MIN_CHARS && linkDensity(element) > LINKY_SHARE)

    /**
     * The deepest element that holds most ([DENSE_SHARE]) of the page's real paragraphs – long
     * ones, outside navigation, teasers and comments, not mostly links.
     */
    private fun densestContainer(document: Document): Element? {
        val body = document.body() ?: return null
        val scores = HashMap<Element, Int>()
        var total = 0
        var count = 0
        for (p in body.select("p")) {
            val ancestors = p.parents()
            if (ancestors.any { it.normalName() in DENSE_SKIPPED || isNotContent(it) || it.attr("role") in CHROME_ROLES }) continue
            if (isNotContent(p)) continue
            val text = p.text()
            if (TextMatch.wordCount(text) < DENSE_MIN_WORDS) continue
            if (linkDensity(p) > PARAGRAPH_LINK_SHARE) continue
            // A paragraph inside a teaser card (a box that is mostly a link) is a teaser.
            if (ancestors.take(TEASER_DEPTH).any { it.text().length < TEASER_MAX_CHARS && linkDensity(it) > LINKY_SHARE }) continue
            if (ancestors.take(TEASER_DEPTH).any { it.normalName() == "a" }) continue
            total += text.length
            count++
            ancestors.forEach { scores[it] = (scores[it] ?: 0) + text.length }
        }
        if (count < 3) return null
        return scores.filter { it.value >= total * DENSE_SHARE }.keys.maxByOrNull { it.parents().size }
    }

    /** `articleBody` as paragraphs: its line breaks, else groups of three sentences. */
    private fun paragraphsOf(body: String): List<ArticleBlock> {
        val lines = body.split(Regex("\\n+")).map(::clean).filter { it.length >= MIN_PARAGRAPH_CHARS }
        val chunks = if (lines.size > 1) lines else {
            SENTENCE.split(clean(body)).chunked(3).map { it.joinToString(" ") }
        }
        return chunks.filter { it.isNotBlank() }.map(ArticleBlock::paragraph)
    }

    private val SENTENCE = Regex("(?<=[.!?…])\\s+(?=[\\p{Lu}„\"“‚'])")

    private class Meta(val title: String, val image: String?, val site: String, val description: String, val language: String, val amp: String?) {
        companion object {
            fun of(document: Document): Meta {
                fun meta(selector: String): String = document.selectFirst(selector)?.attr("content")?.trim().orEmpty()
                val title = meta("meta[property=og:title]").ifEmpty { meta("meta[name=twitter:title]") }.ifEmpty { document.title().trim() }
                val image = meta("meta[property=og:image]").ifEmpty { meta("meta[name=twitter:image]") }
                    .let { resolve(document.location(), it) }
                val site = meta("meta[property=og:site_name]")
                val description = meta("meta[property=og:description]").ifEmpty { meta("meta[name=description]") }.ifEmpty { meta("meta[name=twitter:description]") }
                val language = document.selectFirst("html")?.attr("lang")?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase(Locale.ROOT)
                    ?.takeIf { it.length == 2 }.orEmpty()
                // As announced (the repository only follows https anyway).
                val amp = document.selectFirst("link[rel=amphtml]")?.attr("href")?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { document.location().toHttpUrlOrNull()?.resolve(it)?.toString() }
                return Meta(clean(title), image, clean(site), description, language, amp)
            }
        }
    }

    /**
     * Walks an article root into blocks. [strict] (for the paragraph-density fallback, whose root
     * is a plain page container that Readability did not clean) also skips teaser cards, link
     * lists, dialogs and headings that are links.
     */
    private class Walker(private val baseUrl: String, private val title: String, private val out: MutableList<ArticleBlock>, private val strict: Boolean = false) {
        private val seenImages = HashSet<String>()
        private val seenText = HashSet<String>()

        fun walk(element: Element) {
            for (child in element.children()) {
                if (out.size >= MAX_BLOCKS) return
                val name = child.normalName()
                when {
                    name in SKIPPED -> Unit
                    strict && name !in INLINE_OK && isChromeBox(child) -> Unit
                    strict && name in HEADINGS && child.text().isNotBlank() && linkDensity(child) > LINKY_SHARE -> Unit
                    name == "p" -> paragraph(child)
                    name in HEADINGS -> heading(child.text())
                    name == "blockquote" -> quote(child.text())
                    name == "ul" || name == "ol" -> list(child)
                    name == "figure" -> figure(child)
                    name == "picture" -> picture(child)
                    name == "img" -> image(child)
                    name == "pre" -> addText(child.wholeText().trim(), ArticleBlock::paragraph)
                    name == "table" -> Unit
                    name == "br" || name == "hr" -> Unit
                    child.children().any { it.normalName() in BLOCKS } -> walk(child)
                    else -> paragraph(child)
                }
            }
        }

        private fun paragraph(element: Element) {
            addText(element.text(), ArticleBlock::paragraph)
            // Pictures sitting inside a paragraph (common in WordPress newsrooms).
            element.select("img").forEach { image(it) }
        }

        private fun heading(text: String) = addText(text, ArticleBlock::heading)

        private fun quote(text: String) = addText(text, ArticleBlock::quote)

        private fun list(element: Element) {
            val entries = element.children().filter { it.normalName() == "li" }
            // Every entry a link: "more on this" teasers, tags, share buttons – not the article's text.
            val links = entries.isNotEmpty() && entries.all { li ->
                val text = li.text().length
                text > 0 && li.select("a").sumOf { it.text().length } * 10 >= text * 9
            }
            if (links) return
            val items = entries.map { clean(it.text()) }.filter { it.length >= MIN_PARAGRAPH_CHARS }
            if (items.isNotEmpty()) out += ArticleBlock.list(items)
        }

        private fun figure(element: Element) {
            val caption = element.selectFirst("figcaption")?.text()
            val picture = element.selectFirst("picture")
            val img = element.selectFirst("img")
            when {
                img != null -> image(img, caption = caption, sources = picture)
                picture != null -> picture(picture, caption)
            }
        }

        private fun picture(element: Element, caption: String? = null) {
            val img = element.selectFirst("img")
            if (img != null) {
                image(img, caption, sources = element)
            } else {
                bestOf(element.select("source").map { it.attr("srcset") }.flatMap(::srcset))?.let { add(it, "", caption) }
            }
        }

        private fun image(img: Element, caption: String? = null, sources: Element? = null) {
            val width = img.attr("width").trim().toIntOrNull()
            if (width != null && width < MIN_IMAGE_WIDTH) return
            val url = imageUrl(img, sources) ?: return
            add(url, clean(img.attr("alt")), caption)
        }

        private fun add(url: String, alt: String, caption: String?) {
            if (!seenImages.add(url)) return
            out += ArticleBlock.image(url, alt)
            val text = clean(caption.orEmpty())
            if (text.isNotEmpty() && text != alt) out += ArticleBlock.caption(text)
        }

        /**
         * The picture's best URL: the widest `srcset` candidate (≤ 2000 w), lazy-loading
         * attributes before a placeholder `src`, `<picture><source>` and a `<noscript>` twin.
         */
        private fun imageUrl(img: Element, sources: Element?): String? = bestUrl(img, baseUrl, sources) ?: noscriptTwin(img)

        /** Lazy pages put the real `<img>` into a `<noscript>` right next to the placeholder. */
        private fun noscriptTwin(img: Element): String? {
            val noscript = sequenceOf(img.nextElementSibling(), img.parent()?.selectFirst("noscript")).filterNotNull().firstOrNull { it.normalName() == "noscript" }
                ?: return null
            val inner = noscript.selectFirst("img") ?: Jsoup.parseBodyFragment(noscript.data().ifBlank { noscript.html() }, baseUrl).selectFirst("img") ?: return null
            if (inner === img) return null
            return listOf(bestOf(srcset(inner.attr("srcset"))), inner.attr("src")).asSequence()
                .filterNotNull().mapNotNull { resolve(baseUrl, it) }.firstOrNull { it.isUsableImage() }
        }

        private fun addText(raw: String, block: (String) -> ArticleBlock) {
            val text = clean(raw)
            if (text.length < MIN_PARAGRAPH_CHARS) return
            if (text.equals(title, ignoreCase = true)) return
            if (text.length <= SHORT_PARAGRAPH && JUNK.matches(text)) return
            if (!seenText.add(text)) return
            out += block(text)
        }
    }

    private data class Candidate(val url: String, val width: Int?)

    private val SRCSET_SPLIT = Regex("(?<=\\d[wx])\\s*,\\s*|,\\s+")

    /** "a.jpg 400w, b.jpg 1200w" / "a.jpg 1x, b.jpg 2x" → candidates. */
    private fun srcset(value: String): List<Candidate> {
        if (value.isBlank()) return emptyList()
        // URLs may hold commas (Cloudinary "w_400,h_300"): split on ", " followed by a new URL.
        return value.split(SRCSET_SPLIT).mapNotNull { part ->
            val bits = part.trim().split(Regex("\\s+"))
            val url = bits.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val descriptor = bits.getOrNull(1).orEmpty()
            val width = when {
                descriptor.endsWith("w") -> descriptor.dropLast(1).toIntOrNull()
                descriptor.endsWith("x") -> descriptor.dropLast(1).toDoubleOrNull()?.let { (it * 1000).toInt() }
                else -> null
            }
            Candidate(url, width)
        }
    }

    /** The widest candidate not wider than [MAX_SRCSET_WIDTH] (else the narrowest); the last one without widths. */
    private fun bestOf(candidates: List<Candidate>): String? {
        if (candidates.isEmpty()) return null
        val sized = candidates.filter { it.width != null }
        if (sized.isEmpty()) return candidates.last().url
        return (sized.filter { it.width!! <= MAX_SRCSET_WIDTH }.maxByOrNull { it.width!! } ?: sized.minByOrNull { it.width!! })?.url
    }

    private fun clean(text: String): String = HtmlText.decodeEntities(text).replace(' ', ' ').replace(Regex("[\\u200B-\\u200D\\u2060\\uFEFF]"), "").replace(Regex("\\s+"), " ").trim()

    /** Absolute https URL for [raw] against [base]; http is upgraded, anything else is dropped. */
    private fun resolve(base: String, raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("data:", ignoreCase = true)) return null
        val resolved = base.toHttpUrlOrNull()?.resolve(trimmed) ?: trimmed.toHttpUrlOrNull() ?: return null
        return resolved.newBuilder().scheme("https").build().toString()
    }

    private fun String.isUsableImage(): Boolean = with(HtmlText) { isUsableImageUrl() }
}

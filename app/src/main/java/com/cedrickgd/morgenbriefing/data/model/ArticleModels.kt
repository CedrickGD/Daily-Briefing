package com.cedrickgd.morgenbriefing.data.model

import kotlinx.serialization.Serializable

/**
 * One block of an article as the reader shows it. [type] is one of [ArticleBlock.PARAGRAPH],
 * [ArticleBlock.HEADING], [ArticleBlock.QUOTE], [ArticleBlock.LIST], [ArticleBlock.IMAGE] or
 * [ArticleBlock.CAPTION]; a flat class keeps the JSON cache simple.
 */
@Serializable
data class ArticleBlock(
    val type: String,
    val text: String = "",
    /** [IMAGE]: the https URL of the picture. */
    val url: String = "",
    /** [IMAGE]: the picture's alt text / caption. */
    val alt: String = "",
    /** [LIST]: the bullet items. */
    val items: List<String> = emptyList(),
) {
    companion object {
        const val PARAGRAPH = "p"
        const val HEADING = "h"
        const val QUOTE = "quote"
        const val LIST = "list"
        const val IMAGE = "img"
        const val CAPTION = "caption"

        fun paragraph(text: String) = ArticleBlock(PARAGRAPH, text = text)
        fun heading(text: String) = ArticleBlock(HEADING, text = text)
        fun quote(text: String) = ArticleBlock(QUOTE, text = text)
        fun list(items: List<String>) = ArticleBlock(LIST, items = items)
        fun image(url: String, alt: String = "") = ArticleBlock(IMAGE, url = url, alt = alt)
        fun caption(text: String) = ArticleBlock(CAPTION, text = text)
    }
}

/**
 * The readable part of a web article: what the reader extracted from the page (text, pictures),
 * without navigation, ads, cookie banners or comments. Cached per article.
 */
@Serializable
data class ArticleContent(
    val url: String,
    /** The page's own title (`<h1>` / `og:title`); the feed's headline is shown instead when known. */
    val title: String,
    /** Blocks in reading order. */
    val blocks: List<ArticleBlock>,
    /** A lead picture the page announces (`og:image`), when the feed had none. */
    val image: String? = null,
    /** The site's name from `og:site_name`, when announced. */
    val site: String = "",
    /** ISO-8601 with offset: when the page was read. */
    val fetchedAt: String,
    /** The page's own teaser (`og:description` / `description`), plain text; may be empty. */
    val description: String = "",
    /** The page's language (`<html lang>`, ISO 639-1), when it says. */
    val language: String = "",
    /**
     * Version of the extractor that produced this; older cache entries are read again so they
     * get the current cleanup (duplicates, noise) instead of the old one.
     */
    val format: Int = 0,
    /** The publisher's own `isAccessibleForFree` (schema.org JSON-LD); null = not stated. */
    val free: Boolean? = null,
    /** The article's length as the publisher declares it (JSON-LD `wordCount`); 0 = not declared. */
    val declaredWords: Int = 0,
    /**
     * The page shows subscriber marks (paywall wording at the end of the text, a paid brand mark,
     * a paywall box) although the extractor still found a readable text: probably only the free
     * part. Judged by [com.cedrickgd.morgenbriefing.data.article.ArticleCompleteness].
     */
    val paywallHints: Boolean = false,
) {
    /** The plain text of the article (paragraphs separated by blank lines), for reading aloud. */
    val text: String
        get() = blocks.mapNotNull { block ->
            when (block.type) {
                ArticleBlock.PARAGRAPH, ArticleBlock.HEADING, ArticleBlock.QUOTE -> block.text
                ArticleBlock.LIST -> block.items.joinToString("\n")
                else -> null
            }
        }.joinToString("\n\n")

    val wordCount: Int
        get() = blocks.sumOf { block ->
            when (block.type) {
                ArticleBlock.PARAGRAPH, ArticleBlock.HEADING, ArticleBlock.QUOTE -> block.text.split(' ').count { it.isNotBlank() }
                ArticleBlock.LIST -> block.items.sumOf { item -> item.split(' ').count { it.isNotBlank() } }
                else -> 0
            }
        }

    /** Probably only a teaser: a paywall or a page the extractor could not read fully. */
    val looksTruncated: Boolean get() = wordCount < SHORT_WORDS

    /** The first picture of the article: the announced lead picture, else the first one in the text. */
    val leadImage: String?
        get() = image ?: blocks.firstOrNull { it.type == ArticleBlock.IMAGE }?.url

    companion object {
        /** Below this many words an article is most likely cut off (paywall, teaser page). */
        const val SHORT_WORDS = 120

        /** Current [format]; bump it whenever the extraction or cleanup changes what is stored. */
        const val FORMAT = 4
    }
}

/**
 * How reading an article went — the reader's typed result, also what the readability
 * bookkeeping learns per site.
 */
enum class ReadOutcome {
    /** The article's text is here. */
    OK,

    /** The text is for subscribers (or registered users) only. */
    PAYWALL,

    /** The site shows the text only after cookie / tracking consent on its own page. */
    CONSENT,

    /** The site refuses automated readers (403, bot check, redirect loop). */
    BLOCKED,

    /** The page holds no article: a video, audio or overview page, or it is gone (404). */
    NOT_ARTICLE,

    /** No connection, timeout, server error: worth trying again later. */
    NETWORK,
    ;

    /** The site keeps its text away from the reader (paywall, consent wall, bot block). */
    val isWall: Boolean get() = this == PAYWALL || this == CONSENT || this == BLOCKED
}

/** What the feed knows about an article's readability in the app (for badges, filters, prefetch). */
enum class ArticleStatus {
    /** Not checked yet. */
    UNKNOWN,

    /** Being loaded ahead of time right now. */
    LOADING,

    /**
     * Verified: loaded, extracted and judged a complete article (not a wall, not a teaser), and
     * cached – opens instantly and offline. With "nur vollständige Artikel" only these show.
     */
    READABLE,

    /** Only for subscribers – also a page that shows just the free lead of the article (a teaser). */
    PAYWALL,

    /** The site keeps automated readers out (bot block, consent wall). */
    BLOCKED,

    /** Could not be read (no article on the page, network); may work later. */
    FAILED,
    ;

    companion object {
        fun of(outcome: ReadOutcome): ArticleStatus = when (outcome) {
            ReadOutcome.OK -> READABLE
            ReadOutcome.PAYWALL -> PAYWALL
            ReadOutcome.CONSENT, ReadOutcome.BLOCKED -> BLOCKED
            ReadOutcome.NOT_ARTICLE, ReadOutcome.NETWORK -> FAILED
        }
    }
}

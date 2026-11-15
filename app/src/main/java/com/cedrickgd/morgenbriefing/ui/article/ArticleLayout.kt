package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.article.ArticleCleaner
import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome

/**
 * How early in the text a picture may stand to become the hero (pages without `og:image`
 * often open with their lead picture as the first or second block).
 */
internal const val HERO_FROM_BLOCKS = 3

/**
 * The reader's picture under the status bar is chosen once, where the text is arranged
 * ([com.cedrickgd.morgenbriefing.data.article.ArticleCleaner.arrange]): the feed's picture, else
 * the page's announced lead picture, else a picture among the first [HERO_FROM_BLOCKS] blocks –
 * and every copy of it is taken out of the text there, so it never shows twice.
 *
 * Under a picture the reader prints its alt text – unless the picture has a caption: then the
 * caption is what the newsroom wants read, and the alt text (a description for screen readers,
 * often the caption in other words) stays the picture's content description only. A caption
 * that is just the photo credit ("Bildquelle: IMAGO/…") says nothing about the picture: the alt
 * text is printed above it.
 */
internal fun showsAlt(blocks: List<ArticleBlock>, index: Int): Boolean {
    val block = blocks.getOrNull(index) ?: return false
    if (block.type != ArticleBlock.IMAGE || block.alt.isBlank()) return false
    val next = blocks.getOrNull(index + 1)
    return next?.type != ArticleBlock.CAPTION || ArticleCleaner.isCreditOnly(next.text)
}

/** What a failed article offers, in order: the first is the main action of the panel. */
internal enum class FailureAction { RETRY, OPEN_IN_BROWSER }

/** The pictogram of a failure panel (mapped to a Material symbol by the screen). */
internal enum class FailureIcon { PAYWALL, CONSENT, BLOCKED, GONE, NOT_ARTICLE, OFFLINE, ERROR }

/**
 * The failure panel for [failure]: walls (paywall, consent, bot block) and pages without an
 * article cannot get better by trying again – only "Im Browser öffnen" is offered, as a quiet
 * secondary button; network trouble and unexpected errors offer "Nochmal versuchen" first.
 */
internal fun failureActions(failure: ArticleFailure?): List<FailureAction> = when (failure?.outcome) {
    ReadOutcome.PAYWALL, ReadOutcome.CONSENT, ReadOutcome.BLOCKED, ReadOutcome.NOT_ARTICLE -> listOf(FailureAction.OPEN_IN_BROWSER)
    ReadOutcome.NETWORK, ReadOutcome.OK, null -> listOf(FailureAction.RETRY, FailureAction.OPEN_IN_BROWSER)
}

internal fun failureIcon(failure: ArticleFailure?): FailureIcon = when (failure?.outcome) {
    ReadOutcome.PAYWALL -> FailureIcon.PAYWALL
    ReadOutcome.CONSENT -> FailureIcon.CONSENT
    ReadOutcome.BLOCKED -> FailureIcon.BLOCKED
    ReadOutcome.NOT_ARTICLE -> if (failure.httpCode == 404 || failure.httpCode == 410) FailureIcon.GONE else FailureIcon.NOT_ARTICLE
    ReadOutcome.NETWORK -> FailureIcon.OFFLINE
    ReadOutcome.OK, null -> FailureIcon.ERROR
}

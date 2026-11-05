package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome

/**
 * The reader's picture under the status bar: [url] (null = the topic's generated cover) and,
 * when it was taken from the article's own text, the block that must not show a second time.
 */
internal data class HeroPick(val url: String?, val skipBlock: Int? = null)

/**
 * How early in the text a picture may stand to become the hero (pages without `og:image`
 * often open with their lead picture as the first or second block).
 */
internal const val HERO_FROM_BLOCKS = 3

/**
 * Picks the hero: the feed's picture, else the page's announced lead picture (the view model
 * already removed its copy from the text), else a picture among the first [HERO_FROM_BLOCKS]
 * blocks – then that block is skipped below, so it never shows twice. Pure.
 */
internal fun pickHero(itemImage: String?, content: ArticleContent?): HeroPick {
    if (!itemImage.isNullOrBlank()) return HeroPick(itemImage)
    if (content == null) return HeroPick(null)
    content.image?.takeIf { it.isNotBlank() }?.let { return HeroPick(it) }
    val index = content.blocks.take(HERO_FROM_BLOCKS).indexOfFirst { it.type == ArticleBlock.IMAGE && it.url.startsWith("https://") }
    return if (index >= 0) HeroPick(content.blocks[index].url, skipBlock = index) else HeroPick(null)
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

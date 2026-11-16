package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.model.ReadOutcome

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

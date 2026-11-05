package com.cedrickgd.morgenbriefing.ui.today

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.i18n.CommonStrings
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import com.cedrickgd.morgenbriefing.ui.news.NewsFeed
import com.cedrickgd.morgenbriefing.ui.news.fetchedLabel

/** What a notice's button does. */
enum class NoticeAction { RETRY, OPEN_TOPICS, OPEN_SOURCES, DISCOVER }

/** Copy for a [com.cedrickgd.morgenbriefing.ui.components.NoticeBanner]. */
@Immutable
data class Notice(
    val emoji: String,
    val title: String,
    val text: String,
    val actionLabel: String? = null,
    val action: NoticeAction? = null,
)

/**
 * The hint above the feed, if any: offline, no topic followed, every source off, or some
 * newsrooms not answering. Null while loading or when all is well. Pure, unit-tested.
 *
 * @param feed the feed on screen; null until settings are known.
 * @param loading a refresh is running.
 * @param warmed the cache was read.
 */
internal fun newsNotice(
    feed: NewsFeed?,
    offline: Boolean,
    loading: Boolean,
    warmed: Boolean,
    attempted: Boolean,
    strings: FeedStrings,
    common: CommonStrings,
): Notice? {
    if (feed == null || !warmed) return null
    if (feed.sections.isEmpty()) {
        return Notice(
            emoji = "🧭",
            title = strings.noTopicsTitle,
            text = strings.noTopicsText,
            actionLabel = strings.discoverDescription,
            action = NoticeAction.DISCOVER,
        )
    }
    if (offline) {
        return if (feed.isEmpty) {
            Notice(
                emoji = "📡",
                title = strings.offlineEmptyTitle,
                text = strings.offlineEmptyText,
                actionLabel = common.retry,
                action = NoticeAction.RETRY,
            )
        } else {
            Notice(
                emoji = "📡",
                title = strings.offlineCachedTitle,
                text = strings.offlineCachedText,
                actionLabel = common.retry,
                action = NoticeAction.RETRY,
            )
        }
    }
    if (loading) return null
    val failed = feed.sections.flatMap { it.failedSources }.distinct()
    if (feed.isEmpty && attempted) {
        return Notice(
            emoji = "📭",
            title = strings.noArticlesTitle,
            text = if (failed.isNotEmpty()) strings.sourcesSilent else strings.sourcesSwitchedOff,
            actionLabel = if (failed.isNotEmpty()) common.retry else strings.checkSources,
            action = if (failed.isNotEmpty()) NoticeAction.RETRY else NoticeAction.OPEN_SOURCES,
        )
    }
    if (failed.size >= FAILED_SOURCES_NOTICE) {
        return Notice(
            emoji = "🔌",
            title = strings.sourcesFailing(failed.size),
            text = failed.take(4).joinToString(", ") + if (failed.size > 4) " …" else "",
            actionLabel = common.retry,
            action = NoticeAction.RETRY,
        )
    }
    return null
}

/** How many sources must fail before the feed says so (one quiet newsroom is normal). */
private const val FAILED_SOURCES_NOTICE = 3

/** "Stand 07:12 · 128 Meldungen · Wetter: Open-Meteo" for the end of the page. */
internal fun footerLine(feed: NewsFeed?, hasWeather: Boolean, weatherOffline: Boolean, strings: FeedStrings): String = buildList {
    val stand = fetchedLabel(feed?.fetchedAt)
    if (stand.isNotBlank()) add(strings.stand(stand))
    feed?.articleCount?.takeIf { it > 0 }?.let { add(strings.articleCount(it)) }
    if (hasWeather) add(if (weatherOffline) strings.weatherCreditOffline else strings.weatherCredit)
}.joinToString(" · ")

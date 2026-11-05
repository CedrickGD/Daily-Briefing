package com.cedrickgd.morgenbriefing.notify

import android.util.Log
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.model.WeatherSnapshot
import com.cedrickgd.morgenbriefing.domain.NotificationText
import com.cedrickgd.morgenbriefing.domain.WeatherInsights
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.NotifyStrings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val TAG = "MorningContent"

/**
 * The headlines the notification shows: the newest story of each visible topic, in the user's
 * order, up to [count] — one story per topic, so the morning reads varied, not three takes on
 * the same event. Pure, unit-tested.
 */
internal fun topHeadlines(news: Map<String, TopicNews>, settings: AppSettings, count: Int = 3): List<String> =
    Topics.visible(settings)
        .mapNotNull { topic -> news[topic.id]?.items?.firstOrNull()?.headline?.trim()?.takeIf { it.isNotEmpty() } }
        .distinct()
        .take(count)

/** Candidates checked per topic for a verified headline (the newest, else the next ones). */
internal const val HEADLINE_CANDIDATES = 3

/**
 * "Nur vollständige Artikel": the articles to verify for the morning headlines – per visible
 * topic (in the user's order; the first [count] + 2 topics, so a topic without a complete
 * article can be skipped) its newest [perTopic] articles, newest first. Switched-off sources
 * are left out. Pure.
 */
internal fun headlineCandidates(
    news: Map<String, TopicNews>,
    settings: AppSettings,
    count: Int = 3,
    perTopic: Int = HEADLINE_CANDIDATES,
): List<List<NewsItem>> =
    Topics.visible(settings)
        .asSequence()
        .mapNotNull { topic ->
            news[topic.id]?.items.orEmpty()
                .filter { it.feedSourceId !in settings.disabledFeeds && it.headline.isNotBlank() }
                .take(perTopic)
                .takeIf { it.isNotEmpty() }
        }
        .take(count + 2)
        .toList()

/**
 * Verifies the morning [candidates] with [verify]: a topic whose newest article is not complete
 * falls back to its next one; the caller bounds the time (the worker must stay fast).
 */
internal suspend fun verifyHeadlineCandidates(candidates: List<List<NewsItem>>, verify: suspend (NewsItem) -> ArticleStatus) {
    coroutineScope {
        // Topics side by side (the prefetcher limits the loads at a time); within a topic one
        // article after the other, until one is complete.
        candidates.forEach { items ->
            launch {
                for (item in items) if (verify(item) == ArticleStatus.READABLE) break
            }
        }
    }
}

/**
 * The headlines the notification shows with "nur vollständige Artikel" (see [headlineCandidates]
 * and [verifyHeadlineCandidates]): per topic the newest article verified complete
 * ([ArticleStatus.READABLE] in [status]) – one per topic, in the user's order, up to [count].
 */
internal fun verifiedHeadlines(candidates: List<List<NewsItem>>, status: (String) -> ArticleStatus, count: Int = 3): List<String> =
    candidates
        .mapNotNull { items -> items.firstOrNull { status(it.id) == ArticleStatus.READABLE }?.headline?.trim()?.takeIf { it.isNotEmpty() } }
        .distinct()
        .take(count)

/**
 * Notification text from [WeatherInsights] in [lang]; falls back to a plain greeting if building
 * the text fails, so a formatting problem never costs the user the whole morning notification.
 */
internal fun morningText(snapshot: WeatherSnapshot?, headlines: List<String>, lang: AppLanguage): NotificationText =
    try {
        WeatherInsights.notification(snapshot, headlines, lang)
    } catch (e: Exception) {
        Log.e(TAG, "Building the notification text failed, using the fallback", e)
        fallbackText(headlines, lang)
    }

private fun fallbackText(headlines: List<String>, lang: AppLanguage): NotificationText {
    val strings = NotifyStrings.of(lang)
    val lines = headlines.map { "• ${it.trim()}" }.filter { it.length > 2 }
    val text = strings.fallbackText
    val bigText = if (lines.isEmpty()) text else text + "\n\n" + lines.joinToString("\n")
    return NotificationText(title = strings.fallbackTitle, text = text, bigText = bigText)
}

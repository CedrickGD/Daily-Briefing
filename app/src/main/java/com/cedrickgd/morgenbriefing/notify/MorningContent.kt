package com.cedrickgd.morgenbriefing.notify

import android.util.Log
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.model.WeatherSnapshot
import com.cedrickgd.morgenbriefing.domain.NotificationText
import com.cedrickgd.morgenbriefing.domain.WeatherInsights
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.NotifyStrings

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

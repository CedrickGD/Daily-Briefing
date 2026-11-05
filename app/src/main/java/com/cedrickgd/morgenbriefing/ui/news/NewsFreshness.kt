package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.news.TopicSources

/**
 * Whether the cached [news] of [topicId] were fetched from the sources [settings] read now. Pure
 * (unit-tested).
 *
 * A cache that is only minutes old can still be wrong: after a new edition its Google News
 * source names the old edition (`gn:top@DE:de`), the curated feeds belong to the old country,
 * a place's region may have moved. Such a topic must be fetched again, however fresh it is.
 *
 * What does *not* count as a change: publisher feeds that "nur lesbare Artikel" left out or the
 * user switched off (both still belong to the topic), and walls the phone learned since the
 * fetch – otherwise every learned wall would make every topic look outdated.
 *
 * - every source the cache was fetched from must still belong to the topic (switched-off and
 *   walled feeds included), and
 * - every source that is always read (Google News, the user's own feeds) must be in the cache.
 */
internal fun readsCurrentSources(topicId: String, news: TopicNews?, settings: AppSettings): Boolean {
    if (news == null) return false
    val fetched = news.sources.mapTo(HashSet()) { it.sourceId }
    val belonging = TopicSources.resolve(topicId, settings, blocked = emptyList(), includeDisabled = true)
        .mapTo(HashSet()) { it.id }
    if (!belonging.containsAll(fetched)) return false
    val alwaysRead = TopicSources.resolve(topicId, settings)
        .filter { it.isGoogleNews || !it.builtIn }
        .map { it.id }
    return fetched.containsAll(alwaysRead)
}

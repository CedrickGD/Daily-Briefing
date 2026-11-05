package com.cedrickgd.morgenbriefing.data

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicIcon
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import java.util.Locale

/**
 * How settings stored by older versions become today's: the news edition and the followed
 * topics did not exist before version 3. Pure, unit-tested; [SettingsRepository] applies it on
 * every read until the new fields are written.
 */
object SettingsMigration {

    /**
     * The stored edition; without one, installs that finished onboarding read the German news
     * they always had, fresh installs start with the edition of the phone's [locale].
     */
    fun edition(storedKey: String?, onboardingDone: Boolean, locale: Locale): NewsEdition =
        NewsEdition.fromKey(storedKey) ?: if (onboardingDone) NewsEdition.GERMANY else NewsEdition.default(locale)

    /**
     * The followed topics: the stored list (unknown ids dropped); without one, what an old
     * install showed (its stored order of the version 2 topics, missing ones appended, minus
     * the hidden ones); a fresh install gets [Taxonomy.defaultFollowed].
     *
     * @param legacyOrder the stored `topic_order`, null when there is none.
     * @param known whether an id still exists (built-in, own topic, followed place).
     */
    fun followed(
        stored: List<String>?,
        legacyOrder: List<String>?,
        legacyHidden: Set<String>,
        onboardingDone: Boolean,
        edition: NewsEdition,
        customIds: List<String>,
        known: (String) -> Boolean,
    ): List<String> {
        if (stored != null) return stored.filter(known).distinct()
        if (legacyOrder == null && !onboardingDone) return Taxonomy.defaultFollowed(edition)
        val migrated = Topics.legacyFollowed(legacyOrder.orEmpty(), legacyHidden, customIds, known = Topics.LEGACY_ORDER)
        return migrated.filter(known).ifEmpty { Taxonomy.defaultFollowed(edition) }
    }
}

/**
 * The settings changes the feed, the discover screen and the search need, as pure functions
 * on [AppSettings] ([SettingsRepository] has a suspending wrapper for each). Unit-tested.
 */
object SettingsOps {
    /** Recent searches kept, newest first. */
    const val MAX_RECENT_SEARCHES = 8

    /** Longest title of a followed search. */
    const val MAX_SEARCH_TITLE = 40

    /** Colors for followed searches, picked by the query so the same search keeps its color. */
    private val SEARCH_SEEDS = listOf(0xFF2F6FDE, 0xFF0F9D9A, 0xFF2E8B57, 0xFF7B61FF, 0xFFD81B60, 0xFF8E24AA, 0xFF3949AB, 0xFF00838F)

    /** Follows [id] (at [index], default: at the end); following twice changes nothing. */
    fun follow(settings: AppSettings, id: String, index: Int? = null): AppSettings {
        val current = Topics.followedIds(settings)
        if (id in current) return settings.copy(followed = current)
        val at = (index ?: current.size).coerceIn(0, current.size)
        return settings.copy(followed = current.toMutableList().apply { add(at, id) })
    }

    /** False only for the last followed topic: the feed never ends up empty. */
    fun canUnfollow(settings: AppSettings, id: String): Boolean {
        val current = Topics.followedIds(settings)
        return id !in current || current.size > 1
    }

    /** Stops following [id]; the last followed topic stays (see [canUnfollow]). */
    fun unfollow(settings: AppSettings, id: String): AppSettings {
        if (!canUnfollow(settings, id)) return settings
        return settings.copy(followed = Topics.followedIds(settings) - id)
    }

    /** [follow] or [unfollow]. */
    fun setFollowed(settings: AppSettings, id: String, followed: Boolean): AppSettings =
        if (followed) follow(settings, id) else unfollow(settings, id)

    /**
     * Puts the followed topics in [order]: ids that are not followed are ignored, followed ones
     * missing from [order] keep their place at the end.
     */
    fun reorder(settings: AppSettings, order: List<String>): AppSettings {
        val current = Topics.followedIds(settings)
        val ordered = order.filter { it in current }.distinct()
        return settings.copy(followed = ordered + current.filterNot { it in ordered })
    }

    /** Every category, own topic and place followed again, in catalogue order. */
    fun followAll(settings: AppSettings): AppSettings =
        settings.copy(followed = (Topics.DEFAULT_ORDER + settings.customTopics.map { it.id } + settings.localPlaces.map(Topics::localId)).distinct())

    /** Adds a place to follow (once) and follows its topic; returns the settings and the topic id. */
    fun addLocalPlace(settings: AppSettings, place: Place): Pair<AppSettings, String> {
        val id = Topics.localId(place)
        val places = if (settings.localPlaces.any { Topics.localId(it) == id }) settings.localPlaces else settings.localPlaces + place
        return follow(settings.copy(localPlaces = places), id) to id
    }

    /** Removes a followed place ([topicId] = its `local:` id) and stops following it. */
    fun removeLocalPlace(settings: AppSettings, topicId: String): AppSettings {
        val places = settings.localPlaces.filterNot { Topics.localId(it) == topicId }
        val followed = (Topics.followedIds(settings) - topicId).ifEmpty { Taxonomy.defaultFollowed(settings.edition) }
        return settings.copy(localPlaces = places, followed = followed)
    }

    /**
     * Follows a search ("Midterms") as an own topic with a search icon; an own topic with the
     * same query is followed instead of a second one. Returns the settings and the topic id.
     */
    fun followSearch(settings: AppSettings, query: String): Pair<AppSettings, String> {
        val clean = query.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotEmpty()) { "empty search" }
        settings.customTopics.firstOrNull { it.query.equals(clean, ignoreCase = true) }?.let { existing ->
            return follow(settings, existing.id) to existing.id
        }
        val topic = Topic(
            id = Topics.newCustomId(),
            title = clean.take(MAX_SEARCH_TITLE),
            emoji = "🔎",
            seed = SEARCH_SEEDS[Math.floorMod(clean.lowercase(Locale.ROOT).hashCode(), SEARCH_SEEDS.size)],
            custom = true,
            icon = TopicIcon.SEARCH.name,
            query = clean,
        )
        return follow(settings.copy(customTopics = settings.customTopics + topic), topic.id) to topic.id
    }

    /** Adds an own topic and follows it at the end. */
    fun addCustomTopic(settings: AppSettings, topic: Topic): AppSettings =
        follow(settings.copy(customTopics = settings.customTopics + topic.copy(custom = true)), topic.id)

    /** Deletes an own topic with its feeds and stops following it. */
    fun deleteCustomTopic(settings: AppSettings, id: String): AppSettings {
        val followed = (Topics.followedIds(settings) - id).ifEmpty { Taxonomy.defaultFollowed(settings.edition) }
        return settings.copy(
            customTopics = settings.customTopics.filterNot { it.id == id },
            customFeeds = settings.customFeeds.filterNot { it.topicId == id },
            followed = followed,
            topicOrder = settings.topicOrder - id,
            hiddenTopics = settings.hiddenTopics - id,
        )
    }

    /** Puts [query] first in the recent searches (once, case-insensitive), keeping [MAX_RECENT_SEARCHES]. */
    fun addRecentSearch(settings: AppSettings, query: String): AppSettings {
        val clean = query.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return settings
        val rest = settings.recentSearches.filterNot { it.equals(clean, ignoreCase = true) }
        return settings.copy(recentSearches = (listOf(clean) + rest).take(MAX_RECENT_SEARCHES))
    }

    fun removeRecentSearch(settings: AppSettings, query: String): AppSettings =
        settings.copy(recentSearches = settings.recentSearches.filterNot { it.equals(query.trim(), ignoreCase = true) })

    fun clearRecentSearches(settings: AppSettings): AppSettings = settings.copy(recentSearches = emptyList())
}

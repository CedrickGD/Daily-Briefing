package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicIcon
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.AppLanguage

/*
 * Pure logic of Settings → "Deine Themen": dragging a row through the followed list, the own
 * topics and places that are paused, and the draft of an own topic. Unit-tested.
 */

/** Drag-to-sort of rows with different heights (a two-line title, a large font). */
internal object RowReorder {

    /** Where the dragged row is after [step]: the new order and how far it still sits from its new slot. */
    @Immutable
    data class Result(val order: List<String>, val offset: Float)

    /**
     * Moves [id], dragged [offset] px away from its slot, through [order]: it swaps with a
     * neighbour once it has travelled half of that neighbour's pitch (its height plus [gap]),
     * as often as the offset allows. The remaining offset keeps the row under the finger.
     */
    fun step(order: List<String>, id: String, offset: Float, heightOf: (String) -> Float, gap: Float): Result {
        var current = order
        var rest = offset
        var index = current.indexOf(id)
        if (index < 0) return Result(order, offset)
        fun pitch(neighbour: Int) = heightOf(current[neighbour]) + gap
        while (index < current.lastIndex && rest > pitch(index + 1) / 2f) {
            rest -= pitch(index + 1)
            current = TopicList.move(current, index, index + 1)
            index++
        }
        while (index > 0 && rest < -pitch(index - 1) / 2f) {
            rest += pitch(index - 1)
            current = TopicList.move(current, index, index - 1)
            index--
        }
        return Result(current, rest)
    }
}

internal object FollowedTopics {

    /** The followed topics in feed order, titled in [lang]. */
    fun followed(settings: AppSettings, lang: AppLanguage): List<Topic> = Topics.visible(settings, lang)

    /**
     * Own topics and followed places that are not in the feed right now (switched off in the old
     * topic sheet or from the discover screen): the settings offer to follow them again. Built-in
     * topics are found on the discover screen instead.
     */
    fun paused(settings: AppSettings): List<Topic> {
        val followed = Topics.followedIds(settings).toSet()
        val own = settings.customTopics.filter { it.id !in followed }.map { it.copy(custom = true) }
        val places = settings.localPlaces.map(Topics::localTopic).filter { it.id !in followed }
        return own + places
    }
}

/** What an own topic reads: feeds the user adds, or a Google News search. */
internal enum class TopicContent { FEEDS, SEARCH }

/** The "Eigenes Thema" dialog's fields. */
@Immutable
internal data class TopicDraft(
    val title: String = "",
    /** Null keeps the [emoji] of an old own topic as its pictogram. */
    val icon: TopicIcon? = TopicIcon.NEWSPAPER,
    val emoji: String = DEFAULT_EMOJI,
    val seed: Long = TopicSeeds.first(),
    val content: TopicContent = TopicContent.FEEDS,
    val query: String = "",
) {
    val cleanTitle: String get() = title.trim().replace(Regex("\\s+"), " ").take(MAX_TITLE)
    val cleanQuery: String get() = query.trim().replace(Regex("\\s+"), " ")

    /** A name is needed; a search topic also needs its search words. */
    val canSave: Boolean get() = cleanTitle.isNotEmpty() && (content == TopicContent.FEEDS || cleanQuery.isNotEmpty())

    /** The topic with [id]; feeds of an edited topic stay attached by id. */
    fun toTopic(id: String): Topic = Topic(
        id = id,
        title = cleanTitle,
        emoji = emoji.ifBlank { if (content == TopicContent.SEARCH) SEARCH_EMOJI else DEFAULT_EMOJI },
        seed = seed,
        custom = true,
        icon = icon?.name,
        query = cleanQuery.takeIf { content == TopicContent.SEARCH && it.isNotEmpty() },
    )

    companion object {
        const val MAX_TITLE = 40
        const val DEFAULT_EMOJI = "📰"
        const val SEARCH_EMOJI = "🔎"

        /** The draft of an existing own topic (an old emoji-only topic keeps its emoji). */
        fun of(topic: Topic): TopicDraft = TopicDraft(
            title = topic.title,
            icon = TopicIcon.fromKey(topic.icon),
            emoji = topic.emoji,
            seed = topic.seed,
            content = if (topic.query.isNullOrBlank()) TopicContent.FEEDS else TopicContent.SEARCH,
            query = topic.query.orEmpty(),
        )

        /** A new search topic starts with the search icon and its words as the name. */
        fun forContent(draft: TopicDraft, content: TopicContent): TopicDraft = when {
            content == draft.content -> draft
            content == TopicContent.SEARCH && draft.icon == TopicIcon.NEWSPAPER -> draft.copy(content = content, icon = TopicIcon.SEARCH)
            content == TopicContent.FEEDS && draft.icon == TopicIcon.SEARCH -> draft.copy(content = content, icon = TopicIcon.NEWSPAPER)
            else -> draft.copy(content = content)
        }
    }
}

/** Colors offered for an own topic (ARGB seeds), a calm palette that works on every sky. */
internal val TopicSeeds: List<Long> = listOf(
    0xFF7B61FF, 0xFF2E8B57, 0xFF2F6FDE, 0xFF0F9D9A, 0xFFC6613F, 0xFFE0A100,
    0xFF3949AB, 0xFFD81B60, 0xFF43A047, 0xFF8E24AA, 0xFF5F6B7A, 0xFF00897B,
)

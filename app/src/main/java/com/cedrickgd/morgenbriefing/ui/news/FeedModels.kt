package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.i18n.FeedStrings

/** What the tabs of the feed bar show. */
@Immutable
sealed interface FeedFilter {
    /** Stable id for saving the selection (rememberSaveable). */
    val key: String

    /** Every visible topic as its own section, in the user's order. */
    data object ForYou : FeedFilter {
        override val key: String = "foryou"
    }

    /** Everything, newest first. */
    data object Latest : FeedFilter {
        override val key: String = "latest"
    }

    /** All articles of one topic, endless. */
    data class OneTopic(val topicId: String) : FeedFilter {
        override val key: String get() = TOPIC_PREFIX + topicId
    }

    companion object {
        private const val TOPIC_PREFIX = "topic:"

        fun fromKey(key: String?): FeedFilter = when {
            key == Latest.key -> Latest
            key != null && key.startsWith(TOPIC_PREFIX) && key.length > TOPIC_PREFIX.length ->
                OneTopic(key.removePrefix(TOPIC_PREFIX))
            else -> ForYou
        }
    }
}

/** What a tab of the feed bar is (docs/DESIGN.md §7). */
enum class TabKind { FOR_YOU, LATEST, TOPIC, ADD }

/**
 * One tab of the feed bar: a view of the feed ([filter]), or the round "＋" at the end that
 * opens "Deine Themen" ([TabKind.ADD], no filter). [topic] is set for topic tabs (their color
 * and icon).
 */
@Immutable
data class FeedTab(val kind: TabKind, val label: String, val filter: FeedFilter? = null, val topic: Topic? = null) {
    /** Stable lazy-row key. */
    val key: String get() = filter?.key ?: ADD_TAB_KEY
}

private const val ADD_TAB_KEY = "~add"

/** "Für dich", "Neueste", one tab per followed topic of [feed] (user order), then "＋". Pure. */
internal fun feedTabs(feed: NewsFeed?, strings: FeedStrings): List<FeedTab> = buildList {
    add(FeedTab(TabKind.FOR_YOU, strings.forYou, FeedFilter.ForYou))
    add(FeedTab(TabKind.LATEST, strings.latest, FeedFilter.Latest))
    feed?.sections
        ?.distinctBy { it.id }
        ?.forEach { section -> add(FeedTab(TabKind.TOPIC, section.topic.title, FeedFilter.OneTopic(section.id), section.topic)) }
    add(FeedTab(TabKind.ADD, strings.manageTopics))
}

/** [selected] when a tab still offers it, else "Für dich" (e.g. after its topic was unfollowed). */
internal fun resolveFilter(selected: FeedFilter, tabs: List<FeedTab>): FeedFilter =
    if (tabs.any { it.filter == selected }) selected else FeedFilter.ForYou

/**
 * What switching the view does: the view to show and whether the list scrolls so that the
 * feed bar sits pinned at the top with the new view starting right below it.
 */
@Immutable
internal data class TabSwitch(val filter: FeedFilter, val scrollToBar: Boolean)

/**
 * A tab was tapped: while the bar is pinned ([parked]) a new view starts right below it; a tap
 * on the view already shown, or while the bar still sits in the page, scrolls nothing.
 */
internal fun tabSwitch(tapped: FeedFilter, current: FeedFilter, parked: Boolean): TabSwitch =
    TabSwitch(tapped, scrollToBar = parked && tapped != current)

/**
 * "Alle →" of a "Für dich" card: the topic's own tab is selected – visibly, in the tab row –
 * and the list goes back to the top of the bar, where the topic's list starts.
 */
internal fun showAllSwitch(section: SectionUi): TabSwitch = TabSwitch(FeedFilter.OneTopic(section.id), scrollToBar = true)

/** True when a "Für dich" card offers "Alle →": the topic has articles, or some are still being checked. */
internal fun canShowAll(section: SectionUi): Boolean = !section.isEmpty || section.waiting.isNotEmpty()

/** The section a topic tab shows; null when it is not part of the feed (anymore). */
internal fun NewsFeed.sectionFor(topicId: String): SectionUi? = sections.firstOrNull { it.id == topicId }

/**
 * How many articles an endless list reveals after the user reached its end [pages] times:
 * [pageSize] per step, never more than [total].
 */
internal fun revealedCount(pages: Int, total: Int, pageSize: Int = PAGE_SIZE): Int =
    (pages.coerceAtLeast(1) * pageSize).coerceAtMost(total)

/** Articles revealed per step of an endless list. */
internal const val PAGE_SIZE = 20

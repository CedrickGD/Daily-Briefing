package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic

/*
 * The feed's glass cards as pure decisions (unit-tested): which part of a card a list item is,
 * how a "Für dich" card is laid out (header, the first story large, the next ones as compact
 * rows), which picture a story shows and what its ⋮ menu offers.
 */

/**
 * Which part of a glass card a lazy-list item draws. A card that spans several items (a
 * "Für dich" topic, the endless lists) is drawn piece by piece: the first piece has the
 * rounded top, the last one the rounded bottom, a single item is the [WHOLE] card.
 */
enum class CardPart {
    TOP,
    MIDDLE,
    BOTTOM,
    WHOLE,
    ;

    val roundTop: Boolean get() = this == TOP || this == WHOLE
    val roundBottom: Boolean get() = this == BOTTOM || this == WHOLE
}

/** The part of a card the item at [index] of [count] draws. */
internal fun cardPart(index: Int, count: Int): CardPart = when {
    count <= 1 -> CardPart.WHOLE
    index <= 0 -> CardPart.TOP
    index >= count - 1 -> CardPart.BOTTOM
    else -> CardPart.MIDDLE
}

/** How a story sits in a card: the first one [LEAD] (big picture, larger headline), the others as compact [ROW]s. */
enum class StoryStyle { LEAD, ROW }

/** What a row of a "Für dich" card shows. */
enum class SectionRowKind {
    /** Icon tile, topic name, "Alle →". */
    HEADER,
    STORY,

    /** "Weitere Artikel werden geprüft …" ("Nur vollständige Artikel"). */
    CHECKING,

    /** The topic has nothing to show right now (loading, offline, nothing new …). */
    EMPTY,
}

/**
 * One lazy-list item of a "Für dich" card.
 *
 * @property storyIndex position of [item] among the section's stories (keeps keys stable).
 * @property divider a hairline below this row (between stories, not after the last one).
 */
@Immutable
internal data class SectionRow(
    val kind: SectionRowKind,
    val part: CardPart,
    val item: NewsItem? = null,
    val style: StoryStyle = StoryStyle.ROW,
    val storyIndex: Int = -1,
    val divider: Boolean = false,
)

/**
 * The rows of [section]'s card in "Für dich": the header, then its first story large and the
 * next ones (as many as the section size allows, [SectionUi.top]) as compact rows; while
 * candidates are still being checked a quiet "Weitere Artikel werden geprüft …" row closes the
 * card (not offline: the note above the feed says that once, [verifyOffline]); a topic without
 * stories shows one note row instead.
 */
internal fun sectionRows(section: SectionUi, verifyOffline: Boolean): List<SectionRow> {
    val kinds = buildList {
        add(SectionRowKind.HEADER)
        if (section.isEmpty) {
            add(SectionRowKind.EMPTY)
        } else {
            repeat(section.top.size) { add(SectionRowKind.STORY) }
            if (section.checking && !verifyOffline) add(SectionRowKind.CHECKING)
        }
    }
    var story = 0
    return kinds.mapIndexed { index, kind ->
        val part = cardPart(index, kinds.size)
        if (kind == SectionRowKind.STORY) {
            val next = kinds.getOrNull(index + 1)
            SectionRow(
                kind = kind,
                part = part,
                item = section.top[story],
                style = if (story == 0) StoryStyle.LEAD else StoryStyle.ROW,
                storyIndex = story++,
                divider = next == SectionRowKind.STORY || next == SectionRowKind.CHECKING,
            )
        } else {
            SectionRow(kind = kind, part = part)
        }
    }
}

/**
 * The style of the story at [index] of an endless list: a topic's list opens with a large
 * story ([leadFirst]), "Neueste" is compact rows only.
 */
internal fun listStoryStyle(index: Int, leadFirst: Boolean): StoryStyle =
    if (leadFirst && index == 0) StoryStyle.LEAD else StoryStyle.ROW

/** What fills a story's picture slot. */
enum class StoryVisual {
    /** The article's picture (its own, or the lead picture the prefetcher found). */
    PICTURE,

    /** No (allowed) picture: a small tile in the topic's color with its icon – never a big cover. */
    TOPIC_TILE,
}

/** The picture a story can show: its own, else the lead picture the prefetcher found ([hero]). */
internal fun storyImageUrl(item: NewsItem, hero: String?): String? =
    item.image?.url?.takeIf { it.isNotBlank() } ?: hero?.takeIf { it.isNotBlank() }

/**
 * Picture or topic tile: a picture only with "Bilder anzeigen" on and an address the app may
 * load ([safeImageUrl]). A lead story without one is laid out like a row (headline beside a
 * small tile), so an image-less story never gets a big generated cover.
 */
internal fun storyVisual(showImages: Boolean, imageUrl: String?): StoryVisual =
    if (showImages && safeImageUrl(imageUrl) != null) StoryVisual.PICTURE else StoryVisual.TOPIC_TILE

/** An entry of a story's ⋮ menu (also opened by a long press). */
enum class StoryAction {
    LISTEN,

    /** This story is loaded in the player but paused. */
    RESUME_LISTENING,

    /** This story is being read aloud. */
    STOP_LISTENING,
    SHARE,
    COPY_LINK,
    OPEN_IN_BROWSER,

    /** Follow the sub-topic the story belongs to ([StoryMenuItem.topic]). */
    FOLLOW_TOPIC,

    /** Stop following the topic the story is shown under ([StoryMenuItem.topic]). */
    UNFOLLOW_TOPIC,
    HIDE_SOURCE,
}

/** One entry of a story's ⋮ menu; [topic] for following / unfollowing. */
@Immutable
internal data class StoryMenuItem(val action: StoryAction, val topic: Topic? = null)

/**
 * The ⋮ menu of [article], in this order: listening (start, resume or stop), share, copy the
 * link, open in the browser, then – where they apply – follow its sub-topic ([followSuggestion]),
 * stop following the topic it is shown under ([canUnfollow]: the screen offers it and it is not
 * the last followed topic) and hide its source ([canHideSource]: the screen offers it, the
 * story names a source and that source can be switched off, see [canHideSource]).
 */
internal fun storyMenu(
    article: FeedArticle,
    listening: Boolean,
    paused: Boolean,
    followSuggestion: Topic?,
    canUnfollow: Boolean,
    canHideSource: Boolean,
    sourceName: String,
): List<StoryMenuItem> = buildList {
    add(
        StoryMenuItem(
            when {
                listening -> StoryAction.STOP_LISTENING
                paused -> StoryAction.RESUME_LISTENING
                else -> StoryAction.LISTEN
            },
        ),
    )
    add(StoryMenuItem(StoryAction.SHARE))
    add(StoryMenuItem(StoryAction.COPY_LINK))
    add(StoryMenuItem(StoryAction.OPEN_IN_BROWSER))
    if (followSuggestion != null && followSuggestion.id != article.topic.id) add(StoryMenuItem(StoryAction.FOLLOW_TOPIC, followSuggestion))
    if (canUnfollow) add(StoryMenuItem(StoryAction.UNFOLLOW_TOPIC, article.topic))
    if (canHideSource && sourceName.isNotBlank() && canHideSource(article.item)) add(StoryMenuItem(StoryAction.HIDE_SOURCE))
}

/** The source a story names (publisher, else its site), for the meta line and "„…“ ausblenden". */
internal fun storySource(item: NewsItem): String =
    item.source.trim().ifBlank { publisherHost(item) }.ifBlank { hostOf(item.url) }

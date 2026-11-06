package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.glass.GlassLevel
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.glass
import com.cedrickgd.morgenbriefing.ui.theme.OverlineLetterSpacing
import java.time.ZonedDateTime

/** Side margin of feed content (docs/DESIGN.md §5: 16 dp). */
internal val FeedGutter: Dp = 16.dp

/**
 * A small overline + serif title introducing a feed view ("Neueste", a topic), with an optional
 * [topic] tile, a [subtitle] and an [action] below (e.g. "Unterthemen von Wirtschaft").
 */
@Composable
fun FeedViewIntro(
    overline: String,
    title: String,
    modifier: Modifier = Modifier,
    topic: Topic? = null,
    subtitle: String = "",
    action: (@Composable () -> Unit)? = null,
) {
    val language = LocalAppLanguage.current
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (topic != null) {
                TopicTile(topic, diameter = 48.dp)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = overline.uppercase(language.locale),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = OverlineLetterSpacing),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}

/** A quiet line inside a section that has nothing to show right now. */
@Composable
fun EmptySectionNote(text: String, loading: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The calm end of an endless list: a short rule, "Das war's · 128 Meldungen" and, when
 * [onBackToTop] is set, a glass pill back to the top.
 */
@Composable
fun ListEndNote(text: String, modifier: Modifier = Modifier, onBackToTop: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 28.dp, height = 3.dp)
                .background(colors.onSurfaceVariant.copy(alpha = 0.28f), CircleShape),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onBackToTop != null) {
            Spacer(Modifier.height(12.dp))
            GlassPillButton(text = feedStrings().backToTop, onClick = onBackToTop, icon = Icons.Rounded.KeyboardArrowUp)
        }
    }
}

/**
 * The row at the end of an endless list while more is on its way: a small spinner in a frosted
 * pill with [text] ("Suche ältere Meldungen …"); announced politely to TalkBack.
 */
@Composable
fun LoadingMoreRow(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .glass(shape = GlassShapes.pill, level = GlassLevel.Thin)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The last load of older articles came back empty: say so and let the user look further back. */
@Composable
fun LoadOlderRow(onLoadOlder: () -> Unit, modifier: Modifier = Modifier) {
    val strings = feedStrings()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings.nothingOlderFound,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        GlassPillButton(text = strings.searchFurtherBack, onClick = onLoadOlder, icon = Icons.Rounded.History)
    }
}

/**
 * "Für dich": one glass card per followed topic – its header (icon tile, name, "Alle →"), the
 * first story large, the next ones as compact rows (as many as the section size, [SectionUi.top]).
 * Each row is its own lazy item, drawn as a piece of the card ([sectionRows]).
 *
 * With "nur vollständige Artikel" a card shows its verified articles only; while candidates for
 * it are still being checked a quiet "Weitere Artikel werden geprüft …" row closes it
 * ([verifyOffline]: offline, only articles checked before, the note above the feed says so), and
 * a topic whose candidates all turned out incomplete says so.
 *
 * @param loadingTopics topics whose refresh is running (empty cards say "lade …").
 * @param onShowAll "Alle →": the topic's own tab.
 * @param keyPrefix keeps keys unique when several feeds share a list.
 */
internal fun LazyListScope.forYouItems(
    sections: List<SectionUi>,
    loadingTopics: Set<String>,
    now: ZonedDateTime,
    listening: NowListening?,
    actions: FeedActions,
    hints: FeedHints,
    showImages: Boolean,
    onShowAll: (SectionUi) -> Unit,
    keyPrefix: String,
    verifyOffline: Boolean = false,
) {
    sections.forEach { section ->
        val sectionKey = "$keyPrefix-s${section.index}-${section.id}"
        for (row in sectionRows(section, verifyOffline)) {
            when (row.kind) {
                SectionRowKind.HEADER -> item(key = "$sectionKey-header", contentType = "card-header") {
                    CardPiece(row.part, Modifier.animateItem().padding(horizontal = FeedGutter)) {
                        SectionCardHeader(
                            section = section,
                            onShowAll = if (canShowAll(section)) ({ onShowAll(section) }) else null,
                        )
                    }
                }

                SectionRowKind.STORY -> {
                    val story = row.item ?: continue
                    item(key = entryKey(sectionKey, row.storyIndex, story.key), contentType = "story-${row.style}") {
                        val article = remember(story, section.topic) { FeedArticle(story, section.topic) }
                        CardPiece(row.part, Modifier.animateItem().padding(horizontal = FeedGutter)) {
                            StoryCardItem(
                                article = article,
                                style = row.style,
                                now = now,
                                listening = listening.reading(story.key),
                                paused = listening.pausedOn(story.key),
                                actions = actions,
                                hints = hints,
                                showImage = showImages,
                                showTopic = false,
                                divider = row.divider,
                            )
                        }
                    }
                }

                SectionRowKind.CHECKING -> item(key = "$sectionKey-checking", contentType = "card-note") {
                    CardPiece(row.part, Modifier.animateItem().padding(horizontal = FeedGutter)) {
                        EmptySectionNote(text = feedStrings().verifying, loading = true)
                    }
                }

                SectionRowKind.EMPTY -> item(key = "$sectionKey-empty", contentType = "card-note") {
                    val strings = feedStrings()
                    val loading = section.id in loadingTopics
                    val checking = section.checking || section.waiting.isNotEmpty()
                    CardPiece(row.part, Modifier.animateItem().padding(horizontal = FeedGutter)) {
                        EmptySectionNote(
                            text = when {
                                loading -> strings.loadingArticles
                                checking && verifyOffline -> strings.verifyOffline
                                checking -> strings.verifying
                                section.noneComplete -> strings.noneComplete
                                section.failedSources.isNotEmpty() -> strings.unreachable(section.failedSources.joinToString(", "))
                                section.fetchedAt == null -> strings.nothingLoadedYet
                                else -> strings.nothingNewToday
                            },
                            loading = loading || (checking && !verifyOffline),
                        )
                    }
                }
            }
        }
    }
}

/**
 * An endless list as one glass card ("Neueste", a topic's tab): every story a piece of the card,
 * the first one large when [leadFirst] (a topic), else compact rows only.
 *
 * @param showTopic a small colored topic label above each headline (mixed views).
 */
internal fun LazyListScope.storyListItems(
    articles: List<FeedArticle>,
    now: ZonedDateTime,
    listening: NowListening?,
    actions: FeedActions,
    hints: FeedHints,
    showImages: Boolean,
    keyPrefix: String,
    showTopic: Boolean,
    leadFirst: Boolean,
) {
    val count = articles.size
    articles.forEachIndexed { index, article ->
        val style = listStoryStyle(index, leadFirst)
        item(key = entryKey(keyPrefix, index, article.feedKey), contentType = "story-$style") {
            CardPiece(cardPart(index, count), Modifier.animateItem().padding(horizontal = FeedGutter)) {
                StoryCardItem(
                    article = article,
                    style = style,
                    now = now,
                    listening = listening.reading(article.item.key),
                    paused = listening.pausedOn(article.item.key),
                    actions = actions,
                    hints = hints,
                    showImage = showImages,
                    showTopic = showTopic,
                    divider = index < count - 1,
                )
            }
        }
    }
}

/** A short note in a card of its own (an empty list that is loading or waiting for its check). */
internal fun LazyListScope.noteCardItem(key: String, text: String, loading: Boolean) {
    item(key = key, contentType = "card-note") {
        CardPiece(CardPart.WHOLE, Modifier.animateItem().padding(horizontal = FeedGutter)) {
            EmptySectionNote(text = text, loading = loading)
        }
    }
}

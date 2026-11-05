package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.components.entrance
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.components.rememberEntrance
import com.cedrickgd.morgenbriefing.ui.glass.GlassLevel
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.glass
import com.cedrickgd.morgenbriefing.ui.theme.OverlineLetterSpacing
import java.time.ZonedDateTime

/** Side margin of feed content (docs/DESIGN.md §5: 16 dp). */
internal val FeedGutter: Dp = 16.dp

/** Extra space below a feed entry on top of the list spacing (16 + 14 = 30 dp between entries). */
private val EntryGap = 14.dp

/**
 * Opens a topic in the feed like a newspaper section: a hairline, then the topic's icon on its
 * gradient tile, the title in the topic's color with a one-line description below, and the
 * article count with a chevron on the right — straight on the sky. Tapping it opens the topic's
 * own endless list ([onOpen]; null while the topic has nothing to show).
 */
@Composable
fun FeedSectionHeader(
    topic: Topic,
    count: Int,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onOpen: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val strings = feedStrings()
    val language = LocalAppLanguage.current
    val topicColors = rememberTopicColors(topic)
    val tint = if (isDarkTheme()) topicColors.onContainer else topicColors.strong
    val interaction = remember { MutableInteractionSource() }
    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.onSurface.copy(alpha = 0.10f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(
                    if (onOpen != null) {
                        Modifier
                            .pressScale(interaction, pressedScale = 0.98f)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Button,
                                onClickLabel = strings.openTopic(topic.title),
                                onClick = onOpen,
                            )
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) { heading() }
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopicTile(topic, diameter = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = topic.title.uppercase(language.locale),
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = OverlineLetterSpacing, fontWeight = FontWeight.SemiBold),
                    color = tint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = strings.articleCount(count),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
            if (onOpen != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Glass pill "Alle 37 Meldungen zu KI & Tech →" that opens the topic's own, endless view. */
@Composable
fun AllArticlesButton(section: SectionUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val topicColors = rememberTopicColors(section.topic)
    val labelColor = if (isDarkTheme()) topicColors.onContainer else topicColors.strong
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .pressScale(interaction, pressedScale = 0.98f)
            .glass(shape = GlassShapes.pill, level = GlassLevel.Thin)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = feedStrings().allArticles(section.count, section.topic.title),
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = labelColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

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
 * "Für dich": every followed topic with its header, its first articles as editorial entries
 * and the "Alle … Meldungen" pill that opens the topic's own chip.
 *
 * @param loadingTopics topics whose refresh is running (empty sections say "lade …").
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
) {
    sections.forEach { section ->
        val sectionKey = "$keyPrefix-s${section.index}-${section.id}"
        item(key = "$sectionKey-header", contentType = "feed-section-header") {
            FeedSectionHeader(
                topic = section.topic,
                count = section.count,
                subtitle = section.subtitle,
                onOpen = if (section.isEmpty) null else ({ onShowAll(section) }),
                modifier = Modifier
                    .animateItem()
                    .padding(horizontal = FeedGutter)
                    .padding(top = 12.dp),
            )
        }
        if (section.isEmpty) {
            item(key = "$sectionKey-empty", contentType = "feed-empty") {
                val strings = feedStrings()
                val loading = section.id in loadingTopics
                EmptySectionNote(
                    text = when {
                        loading -> strings.loadingArticles
                        section.failedSources.isNotEmpty() -> strings.unreachable(section.failedSources.joinToString(", "))
                        section.fetchedAt == null -> strings.nothingLoadedYet
                        else -> strings.nothingNewToday
                    },
                    loading = loading,
                    modifier = Modifier.animateItem().padding(horizontal = FeedGutter),
                )
            }
            return@forEach
        }
        section.top.forEachIndexed { index, item ->
            item(key = entryKey(sectionKey, index, item.key), contentType = "feed-entry") {
                val article = remember(item, section.topic) { FeedArticle(item, section.topic) }
                AnimatedFeedEntry(article, now, listening.reading(item.key), listening.pausedOn(item.key), actions, hints, showImages)
            }
        }
        if (section.hasMore) {
            item(key = "$sectionKey-more", contentType = "feed-more") {
                AllArticlesButton(
                    section = section,
                    onClick = { onShowAll(section) },
                    modifier = Modifier
                        .animateItem()
                        .padding(horizontal = FeedGutter)
                        .padding(bottom = EntryGap),
                )
            }
        }
    }
}

/**
 * A plain list of editorial entries ("Neueste", a topic).
 *
 * @param showTopic topic overline above each headline (mixed views).
 */
internal fun LazyListScope.articleItems(
    articles: List<FeedArticle>,
    now: ZonedDateTime,
    listening: NowListening?,
    actions: FeedActions,
    hints: FeedHints,
    showImages: Boolean,
    keyPrefix: String,
    showTopic: Boolean,
) {
    articles.forEachIndexed { index, article ->
        item(key = entryKey(keyPrefix, index, article.feedKey), contentType = "feed-entry") {
            AnimatedFeedEntry(article, now, listening.reading(article.item.key), listening.pausedOn(article.item.key), actions, hints, showImages, showTopic)
        }
    }
}

/** A [FeedEntry] that rises in once when it first scrolls into view. */
@Composable
private fun LazyItemScope.AnimatedFeedEntry(
    article: FeedArticle,
    now: ZonedDateTime,
    listening: Boolean,
    paused: Boolean,
    actions: FeedActions,
    hints: FeedHints,
    showImage: Boolean,
    showTopic: Boolean = false,
) {
    val entrance = rememberEntrance(durationMillis = 460)
    FeedEntry(
        article = article,
        now = now,
        listening = listening,
        paused = paused,
        actions = actions,
        hints = hints,
        showTopic = showTopic,
        showImage = showImage,
        modifier = Modifier
            .animateItem()
            .padding(horizontal = FeedGutter)
            .padding(bottom = EntryGap)
            .entrance(entrance, distance = 18.dp),
    )
}

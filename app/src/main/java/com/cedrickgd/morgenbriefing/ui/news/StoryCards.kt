package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.domain.ItemEdition
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.ui.components.entrance
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.components.rememberEntrance
import com.cedrickgd.morgenbriefing.ui.glass.glassCardPart
import com.cedrickgd.morgenbriefing.ui.theme.Reading
import java.time.ZonedDateTime

/** Space between two glass cards of the feed (docs/DESIGN.md §5). */
internal val CardGap: Dp = 12.dp

/** Inner side margin of a feed card (docs/DESIGN.md §7). */
internal val CardInset: Dp = 16.dp

/** Height of the lead story's picture. */
internal val LeadPictureHeight = 168.dp

/** Size of a compact row's thumbnail. */
internal val ThumbnailSize = 80.dp

/**
 * One piece of a glass card in a lazy list ([part]: rounded top, bottom, both or neither): the
 * frosted surface without seams to its neighbours, 16 dp inner margins, and the gap to the next
 * card below a closing piece.
 */
@Composable
internal fun CardPiece(part: CardPart, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = if (part.roundBottom) CardGap else 0.dp)
            .glassCardPart(top = part.roundTop, bottom = part.roundBottom)
            .padding(
                start = CardInset,
                end = CardInset,
                top = if (part.roundTop) 6.dp else 0.dp,
                bottom = if (part.roundBottom) 4.dp else 0.dp,
            ),
        content = content,
    )
}

/** The fine line between two stories of a card. */
@Composable
internal fun CardHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

/**
 * The head of a "Für dich" card: the topic's icon tile, its name and "Alle →", which opens the
 * topic's own tab ([onShowAll]; null while the topic has nothing to show).
 */
@Composable
internal fun SectionCardHeader(section: SectionUi, onShowAll: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val strings = feedStrings()
    val language = LocalAppLanguage.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopicBadge(section.topic, diameter = 34.dp, size = 16.sp, shape = RoundedCornerShape(11.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = section.topic.title.uppercase(language.locale),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold),
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onShowAll != null) {
            val label = strings.allArticles(section.count, section.topic.title)
            Row(
                modifier = Modifier
                    .offset(x = 8.dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClickLabel = label, onClick = onShowAll)
                    .semantics(mergeDescendants = true) { }
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = strings.showAll, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.primary)
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * One story in a feed card: [StoryStyle.LEAD] with a 168 dp picture above a larger serif
 * headline, or a compact [StoryStyle.ROW] (serif headline beside an 80 dp thumbnail). Under the
 * headline the meta line "Quelle · vor 3 Std." with a small mark for a paywall or browser-only
 * page, and the ⋮ menu. Tapping opens the article,
 * a long press opens the same menu. No buttons under the story.
 *
 * A story the user read already ([FeedHints.isRead]) sits back: its headline in the quieter
 * text color, its picture faded ([ReadPictureAlpha]) and a small "👁 Gelesen" at the start of the
 * meta line – no check mark, which once meant "full text".
 *
 * With "Bilder anzeigen" on every story has its picture slot from the start ([storyVisual]): the
 * generated cover in the topic's colors, and over it the article's picture – its own, or the one
 * found for it (Google News items carry none; it is looked up ahead of the list and fades in).
 * The slot keeps its size, so nothing jumps when a picture arrives, and a story without one
 * keeps its cover. With the setting off the slot is a small tile in the topic's color
 * ([StoryVisual.TOPIC_TILE]) and a lead story sits like a row with its larger headline.
 *
 * @param showTopic a small colored topic label above the headline (mixed lists: "Neueste").
 * @param divider a hairline below the story (not after the card's last one).
 */
@Composable
internal fun StoryCardItem(
    article: FeedArticle,
    style: StoryStyle,
    now: ZonedDateTime,
    listening: Boolean,
    paused: Boolean,
    actions: FeedActions,
    hints: FeedHints,
    showImage: Boolean,
    showTopic: Boolean,
    divider: Boolean,
    modifier: Modifier = Modifier,
) {
    val item = article.item
    val strings = feedStrings()
    val language = LocalAppLanguage.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    var menuOpen by remember { mutableStateOf(false) }
    val hero by remember(hints, item.id, item.image) {
        derivedStateOf { if (item.image == null) hints.heroImage(item.id) else null }
    }
    val badge by remember(hints, item.id) { derivedStateOf { cardBadge(hints.status(item.id)) } }
    val read by remember(hints, item) { derivedStateOf { hints.isRead(item) } }
    val pictureAlpha by animateFloatAsState(if (read) ReadPictureAlpha else 1f, label = "readPicture")
    val headlineColor = if (read) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
    val visualItem = remember(item, hero) { hero?.let { item.copy(image = ArticleImage(it)) } ?: item }
    val visual = storyVisual(showImage)
    val leadWithPicture = style == StoryStyle.LEAD && visual == StoryVisual.PICTURE
    val meta = remember(item, now, language) { feedMetaParts(item, now, language) }
    // Hyphenate in the article's own language (English local news under a German edition).
    val textLanguage = remember(item.sourceId) { ItemEdition.languageOf(item) }
    val entrance = rememberEntrance(durationMillis = 420)

    val click = Modifier
        .pressScale(interaction, pressedScale = 0.985f)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClickLabel = strings.openArticle,
            onLongClickLabel = strings.moreOptions,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            },
            onClick = { actions.onOpen(article) },
        )
    val metaRow: @Composable (Dp) -> Unit = { menuShift ->
        StoryMetaRow(
            article = article,
            meta = meta,
            badge = badge,
            listening = listening,
            paused = paused,
            read = read,
            menuOpen = menuOpen,
            onMenu = { menuOpen = it },
            actions = actions,
            menuShift = menuShift,
        )
    }

    Column(modifier.fillMaxWidth().entrance(entrance, distance = 10.dp)) {
        if (leadWithPicture) {
            Column(click.padding(top = 8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(LeadPictureHeight)
                        .graphicsLayer { alpha = pictureAlpha }
                        .clip(RoundedCornerShape(18.dp)),
                ) {
                    ArticleVisual(
                        item = visualItem,
                        topic = article.topic,
                        modifier = Modifier.matchParentSize(),
                        showImage = showImage,
                        contentDescription = item.image?.alt?.takeIf { it.isNotBlank() },
                        showCoverBadge = false,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (showTopic) {
                    TopicLabel(article.topic)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = item.headline,
                    style = Reading.storyLead(language = textLanguage),
                    color = headlineColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                metaRow(12.dp)
            }
        } else {
            Row(click.padding(top = 12.dp, bottom = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    if (showTopic) {
                        TopicLabel(article.topic)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = item.headline,
                        style = if (style == StoryStyle.LEAD) Reading.storyLead(language = textLanguage) else Reading.storyRow(language = textLanguage),
                        color = headlineColor,
                        maxLines = if (style == StoryStyle.LEAD) 4 else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    metaRow(0.dp)
                }
                Spacer(Modifier.width(14.dp))
                StoryThumbnail(visualItem, article.topic, visual, showImage, Modifier.graphicsLayer { alpha = pictureAlpha })
            }
        }
        if (divider) CardHairline()
    }
}

/** How far the picture of a story the user read already fades (on glass, light and dark). */
internal const val ReadPictureAlpha = 0.5f

/** The 80 dp picture of a row: the article's picture over its generated cover, or (pictures off) a tile in the topic's color. */
@Composable
private fun StoryThumbnail(item: NewsItem, topic: Topic, visual: StoryVisual, showImage: Boolean, modifier: Modifier = Modifier) {
    if (visual == StoryVisual.PICTURE) {
        Box(
            modifier
                .size(ThumbnailSize)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            ArticleVisual(
                item = item,
                topic = topic,
                modifier = Modifier.matchParentSize(),
                showImage = showImage,
                // A cover that stands for the picture carries the topic's icon; under a picture it would only cost.
                showCoverBadge = item.image == null,
            )
        }
    } else {
        TopicThumbnail(topic, size = ThumbnailSize, modifier = modifier)
    }
}

/** "KI & TECH" in the topic's color above a headline (mixed lists). */
@Composable
internal fun TopicLabel(topic: Topic, modifier: Modifier = Modifier) {
    val colors = rememberTopicColors(topic)
    Text(
        text = topic.title.uppercase(LocalAppLanguage.current.locale),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
        color = if (isDarkTheme()) colors.onContainer else colors.strong,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * "Quelle · vor 3 Std." with the readability mark, a headphones mark while the story is read
 * aloud, "👁 Gelesen" in front once the user read it ([read]), and the ⋮ button with the story's
 * menu. The time stays whole, only the source shortens.
 *
 * @param menuShift moves the ⋮ towards the card's edge (the lead's full-width meta line).
 */
@Composable
private fun StoryMetaRow(
    article: FeedArticle,
    meta: FeedMetaParts,
    badge: CardBadge?,
    listening: Boolean,
    paused: Boolean,
    read: Boolean,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    actions: FeedActions,
    menuShift: Dp,
) {
    val colors = MaterialTheme.colorScheme
    val strings = feedStrings()
    val metaStyle = Reading.storyMeta()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (listening || paused) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            if (read) ReadMark(metaStyle, separator = meta.source != null || meta.time != null)
            meta.source?.let { source ->
                Text(
                    text = source,
                    style = metaStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            meta.time?.let { time ->
                Text(text = if (meta.source != null) " · $time" else time, style = metaStyle, maxLines = 1, softWrap = false)
            }
            if (badge != null) {
                Spacer(Modifier.width(6.dp))
                BadgeMark(badge, strings)
            }
        }
        Box(Modifier.offset(x = menuShift)) {
            IconButton(onClick = { onMenu(true) }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = strings.moreOptions, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            StoryMenu(
                article = article,
                open = menuOpen,
                listening = listening,
                paused = paused,
                read = read,
                onDismiss = { onMenu(false) },
                actions = actions,
            )
        }
    }
}

/**
 * "👁 Gelesen" at the start of a meta line: a muted eye and the word, in the meta line's own
 * style (quiet in light and dark), then " · " before the rest when [separator].
 */
@Composable
internal fun ReadMark(style: TextStyle, separator: Boolean, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Visibility, contentDescription = null, tint = muted.copy(alpha = 0.8f), modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (separator) "${feedStrings().readMark} · " else feedStrings().readMark,
            style = style.copy(fontWeight = FontWeight.Medium),
            color = muted,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** 🔒 (paywall) or 🌐 (browser only), small and muted, with its spoken meaning. */
@Composable
private fun BadgeMark(badge: CardBadge, strings: FeedStrings) {
    val (icon, description) = when (badge) {
        CardBadge.PAYWALL -> Icons.Rounded.Lock to strings.paywallDescription
        CardBadge.BROWSER_ONLY -> Icons.Rounded.Public to strings.browserOnlyDescription
    }
    Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
}

/**
 * The ⋮ menu of a story ([storyMenu]): Zuhören (or Weiter / Beenden while it is loaded in the
 * player), Teilen, Link kopieren, Im Browser öffnen, then following or unfollowing a topic and
 * hiding the source where they apply.
 */
@Composable
private fun StoryMenu(
    article: FeedArticle,
    open: Boolean,
    listening: Boolean,
    paused: Boolean,
    read: Boolean,
    onDismiss: () -> Unit,
    actions: FeedActions,
) {
    val colors = MaterialTheme.colorScheme
    val strings = feedStrings()
    val common = commonStrings()
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = colors.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        val source = storySource(article.item)
        val items = remember(article, listening, paused, read, actions, source) {
            storyMenu(
                article = article,
                listening = listening,
                paused = paused,
                followSuggestion = if (actions.onFollowTopic != null) actions.followSuggestion(article) else null,
                canUnfollow = actions.onUnfollowTopic != null && actions.canUnfollowTopic(article.topic),
                canHideSource = actions.onHideSource != null,
                sourceName = source,
                read = if (actions.onSetRead != null) read else null,
                canHide = actions.onHide != null,
            )
        }
        for (entry in items) {
            val (label, icon) = when (entry.action) {
                StoryAction.LISTEN -> strings.listen to Icons.Rounded.Headphones
                StoryAction.RESUME_LISTENING -> strings.continueListening to Icons.Rounded.PlayArrow
                StoryAction.STOP_LISTENING -> strings.stopListening to Icons.Rounded.Stop
                StoryAction.SHARE -> common.share to Icons.Rounded.Share
                StoryAction.COPY_LINK -> common.copyLink to Icons.Rounded.ContentCopy
                StoryAction.OPEN_IN_BROWSER -> common.openInBrowser to Icons.AutoMirrored.Rounded.OpenInNew
                StoryAction.MARK_READ -> strings.markRead to Icons.Rounded.Visibility
                StoryAction.MARK_UNREAD -> strings.markUnread to Icons.Rounded.Restore
                StoryAction.HIDE -> strings.hideStory to Icons.Rounded.VisibilityOff
                StoryAction.FOLLOW_TOPIC -> strings.followTopic(entry.topic?.title.orEmpty()) to Icons.Rounded.AddCircleOutline
                StoryAction.UNFOLLOW_TOPIC -> strings.unfollowTopic(entry.topic?.title.orEmpty()) to Icons.Rounded.RemoveCircleOutline
                StoryAction.HIDE_SOURCE -> strings.hideSource(source) to Icons.AutoMirrored.Rounded.VolumeOff
            }
            val accent = entry.action == StoryAction.FOLLOW_TOPIC || entry.action == StoryAction.STOP_LISTENING ||
                entry.action == StoryAction.RESUME_LISTENING
            StoryMenuEntry(label, icon, tint = if (accent) colors.primary else colors.onSurfaceVariant) {
                onDismiss()
                runStoryAction(entry, article, actions)
            }
        }
    }
}

/** Does what a menu entry says. */
private fun runStoryAction(entry: StoryMenuItem, article: FeedArticle, actions: FeedActions) {
    when (entry.action) {
        StoryAction.LISTEN, StoryAction.RESUME_LISTENING -> actions.onListen(article)
        StoryAction.STOP_LISTENING -> actions.onStopListening()
        StoryAction.SHARE -> actions.onShare(article)
        StoryAction.COPY_LINK -> actions.onCopyLink(article)
        StoryAction.OPEN_IN_BROWSER -> actions.onOpenExternal(article)
        StoryAction.MARK_READ -> actions.onSetRead?.invoke(article, true)
        StoryAction.MARK_UNREAD -> actions.onSetRead?.invoke(article, false)
        StoryAction.HIDE -> actions.onHide?.invoke(article)
        StoryAction.FOLLOW_TOPIC -> entry.topic?.let { topic -> actions.onFollowTopic?.invoke(topic) }
        StoryAction.UNFOLLOW_TOPIC -> entry.topic?.let { topic -> actions.onUnfollowTopic?.invoke(topic) }
        StoryAction.HIDE_SOURCE -> actions.onHideSource?.invoke(article)
    }
}

@Composable
private fun StoryMenuEntry(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

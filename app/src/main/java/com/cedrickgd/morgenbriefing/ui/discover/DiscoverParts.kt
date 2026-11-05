package com.cedrickgd.morgenbriefing.ui.discover

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.discoverStrings
import com.cedrickgd.morgenbriefing.ui.components.LoadingSkeleton
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import com.cedrickgd.morgenbriefing.ui.news.TopicColors
import com.cedrickgd.morgenbriefing.ui.news.TopicGlyph
import com.cedrickgd.morgenbriefing.ui.news.hostOf
import com.cedrickgd.morgenbriefing.ui.news.rememberTopicColors
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/*
 * Building blocks shared by "Themen entdecken" and the search: the topic's icon in a tile of its
 * own gradient, the follow pill, and the compact headline rows of a preview.
 */

/** "Now" that ticks once per minute (relative times like "vor 3 Min."), no per-frame work. */
@Composable
internal fun rememberNow(): ZonedDateTime {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = ZonedDateTime.now()
            now = current
            val next = current.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(ChronoUnit.MILLIS.between(current, next).coerceAtLeast(250))
        }
    }
    return now
}

/** The text color for the topic on glass: deep in light mode, light in dark mode (≥ 4.5:1). */
@Composable
internal fun TopicColors.onGlass(): Color = if (isGlassDark()) onContainer else strong

/**
 * The topic's pictogram in a rounded tile painted with the topic's own gradient – the
 * "tinted tile" every topic row and header uses. Decorative (the row says the name).
 */
@Composable
internal fun TopicIconTile(topic: Topic, modifier: Modifier = Modifier, size: Dp = 44.dp, corner: Dp = 14.dp) {
    val colors = rememberTopicColors(topic)
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.coverStart, colors.coverEnd)))
            .drawBehind {
                // A soft highlight in the top-left corner, like light on enamel.
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(colors.coverGlow.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(this.size.width * 0.2f, this.size.height * 0.15f),
                        radius = this.size.minDimension * 0.9f,
                    ),
                    radius = this.size.minDimension * 0.9f,
                    center = Offset(this.size.width * 0.2f, this.size.height * 0.15f),
                )
            }
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        TopicGlyph(topic, tint = Color.White, size = size * 0.52f)
    }
}

/** A plain Material icon in the same kind of tile, tinted with the accent (sections without a topic). */
@Composable
internal fun AccentIconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = if (isGlassDark()) 0.22f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * The follow pill of a topic: "+ Folgen" on a light pane, or filled in the topic's deep color
 * with "✓ Gefolgt". Toggles with a haptic tick and reads as a switch to TalkBack ([title] is
 * its label). When [canToggle] is false (the last followed topic) a tap calls [onBlocked].
 */
@Composable
internal fun FollowPill(
    topic: Topic,
    title: String,
    followed: Boolean,
    canToggle: Boolean,
    onToggle: (Boolean) -> Unit,
    onBlocked: () -> Unit,
    modifier: Modifier = Modifier,
    onCover: Boolean = false,
) {
    val strings = discoverStrings()
    val colors = rememberTopicColors(topic)
    val dark = isGlassDark()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val fillTarget = when {
        followed && onCover -> Color.White
        followed -> colors.strong
        onCover -> Color.White.copy(alpha = 0.18f)
        dark -> Color.White.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.62f)
    }
    val contentTarget = when {
        followed && onCover -> colors.coverEnd
        followed -> Color.White
        onCover -> Color.White
        else -> colors.onGlass()
    }
    val fill by animateColorAsState(fillTarget, spring(stiffness = Spring.StiffnessMediumLow), label = "followFill")
    val content by animateColorAsState(contentTarget, spring(stiffness = Spring.StiffnessMediumLow), label = "followContent")
    val edge = if (followed) Color.Transparent else Color.White.copy(alpha = if (dark || onCover) 0.16f else 0.7f)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, edge, CircleShape)
            .toggleable(
                value = followed,
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Switch,
                onValueChange = { value ->
                    if (!canToggle && !value) {
                        onBlocked()
                    } else {
                        haptics.performHapticFeedback(if (value) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        onToggle(value)
                    }
                },
            )
            .semantics {
                contentDescription = title
                stateDescription = if (followed) strings.followedState else strings.notFollowedState
            }
            .heightIn(min = 36.dp)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = followed,
            transitionSpec = {
                (scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) + fadeIn()) togetherWith
                    (scaleOut(targetScale = 0.4f) + fadeOut())
            },
            label = "followIcon",
        ) { on ->
            Icon(if (on) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (followed) strings.following else strings.follow,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** "vor 3 Std. · BBC" for a headline row: [relative] time and the publisher (unknown parts left out). */
internal fun headlineMeta(item: NewsItem, relative: String?): String {
    val source = item.source.trim().ifEmpty { item.publisherDomain.ifBlank { hostOf(item.url) } }
    return listOfNotNull(relative, source.ifBlank { null }).joinToString(" · ")
}

/**
 * A compact headline: a dot in the topic's color, the headline in serif (three lines at most)
 * and "vor 3 Std. · Quelle". The whole row opens the article.
 */
@Composable
internal fun HeadlineRow(
    item: NewsItem,
    accent: Color,
    now: ZonedDateTime,
    onOpen: (NewsItem) -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
) {
    val lang = LocalAppLanguage.current
    val meta = remember(item, now, lang) { headlineMeta(item, Fmt.relative(item.publishedAt, now, lang)) }
    val interaction = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = { onOpen(item) })
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (rank != null) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
                color = accent,
                modifier = Modifier
                    .widthIn(min = 28.dp)
                    .clearAndSetSemantics { },
            )
            Spacer(Modifier.width(8.dp))
        } else {
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .size(8.dp)
                    .background(accent, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = item.headline,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
                color = colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The newest headlines of a topic: skeleton lines while they come, the rows, or a calm note
 * (nothing there / could not load, with "Nochmal versuchen").
 */
@Composable
internal fun PreviewBlock(
    preview: PreviewUi,
    accent: Color,
    now: ZonedDateTime,
    onOpen: (NewsItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = discoverStrings()
    val colors = MaterialTheme.colorScheme
    AnimatedContent(
        targetState = preview,
        contentKey = { p ->
            when {
                p.items.isNotEmpty() -> "items:" + p.topicId + p.items.joinToString { it.id }
                p.waiting -> "wait:" + p.topicId
                p.failed -> "failed"
                else -> "empty"
            }
        },
        transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessMedium)) },
        modifier = modifier.fillMaxWidth(),
        label = "preview",
    ) { p ->
        Column(Modifier.fillMaxWidth()) {
            when {
                p.items.isNotEmpty() -> {
                    p.items.forEach { item ->
                        HeadlineRow(item = item, accent = accent, now = now, onOpen = onOpen)
                    }
                    // "Nur vollständige Artikel": fewer than three verified yet.
                    if (p.checking || (p.offline && p.toVerify.isNotEmpty())) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (p.checking) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = accent)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                text = if (p.checking) strings.previewChecking else strings.previewOffline,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }

                p.waiting -> Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    repeat(PREVIEW_SIZE) { index ->
                        LoadingSkeleton(
                            Modifier
                                .fillMaxWidth(if (index == 1) 0.78f else 0.94f)
                                .height(18.dp),
                        )
                    }
                }

                p.failed -> Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Text(strings.previewFailed, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    QuietAction(text = commonStrings().retry, icon = Icons.Rounded.Refresh, tint = accent, onClick = onRetry)
                }

                else -> Text(
                    text = when {
                        p.offline && p.toVerify.isNotEmpty() -> strings.previewOffline
                        p.noneComplete -> strings.previewNoneComplete
                        else -> strings.previewEmpty
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** A small text action with an icon, at least 48 dp tall. */
@Composable
internal fun QuietAction(text: String, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

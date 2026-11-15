package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.Topic

/** Corner radius of article cards (reader). Feed pictures use 22 dp (GlassShapes.tile). */
internal val CardShape = RoundedCornerShape(24.dp)

/**
 * The topic's icon in white on a small tile with the topic's own gradient (the colors of its
 * cover): the lively mark that opens a section, a suggestion or the discover invitation.
 * The corners follow [diameter] (≈ 30 %).
 */
@Composable
internal fun TopicTile(topic: Topic, modifier: Modifier = Modifier, diameter: Dp = 40.dp) {
    val colors = rememberTopicColors(topic)
    val shape = RoundedCornerShape(diameter * 0.3f)
    Box(
        modifier = modifier
            .size(diameter)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.coverStart, colors.coverEnd)))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center,
    ) {
        TopicGlyph(topic, tint = Color.White, size = diameter * 0.55f)
    }
}

/** Small rounded chip with the topic's icon and title, tinted with the topic color. */
@Composable
fun TopicChip(topic: Topic, modifier: Modifier = Modifier, compact: Boolean = false) {
    val colors = rememberTopicColors(topic)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (compact) 8.dp else 10.dp,
                end = if (compact) 10.dp else 12.dp,
                top = if (compact) 3.dp else 5.dp,
                bottom = if (compact) 3.dp else 5.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (topic.icon != null || topic.emoji.isNotBlank()) {
                TopicGlyph(topic, tint = colors.onContainer, size = if (compact) 14.dp else 16.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = topic.title,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The topic's icon (or, for old custom topics, emoji) on a tile tinted with the topic color: a
 * circle by default, a rounded square with [shape] (the header of a feed card).
 */
@Composable
internal fun TopicBadge(
    topic: Topic,
    modifier: Modifier = Modifier,
    size: TextUnit = 20.sp,
    diameter: Dp = 40.dp,
    shape: Shape = CircleShape,
) {
    val colors = rememberTopicColors(topic)
    Box(
        modifier = modifier
            .size(diameter)
            .background(colors.container, shape),
        contentAlignment = Alignment.Center,
    ) {
        TopicGlyph(topic, tint = colors.onContainer, size = (size.value * 1.1f).dp)
    }
}

/**
 * The picture slot of a story while "Bilder anzeigen" is off: a small tile in the topic's own
 * gradient with its icon in white – color in the list without a download. Decorative.
 */
@Composable
internal fun TopicThumbnail(topic: Topic, modifier: Modifier = Modifier, size: Dp = 80.dp, corner: Dp = 14.dp) {
    val colors = rememberTopicColors(topic)
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.coverStart, colors.coverEnd)))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape),
        contentAlignment = Alignment.Center,
    ) {
        TopicGlyph(topic, tint = Color.White.copy(alpha = 0.92f), size = size * 0.4f)
    }
}

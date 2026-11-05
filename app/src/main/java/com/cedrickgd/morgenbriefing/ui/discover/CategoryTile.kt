package com.cedrickgd.morgenbriefing.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.discoverStrings
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.news.TopicColors
import com.cedrickgd.morgenbriefing.ui.news.TopicGlyph
import com.cedrickgd.morgenbriefing.ui.news.iconVector
import com.cedrickgd.morgenbriefing.ui.news.rememberTopicColors

/** Corner radius of the category tiles. */
private val TileShape = RoundedCornerShape(26.dp)

/**
 * The topic's cover painting: its gradient from top-left to bottom-right with two soft glows —
 * a bright one in the top-right corner and a deeper one rising from the bottom-left. Draw phase
 * only. The cover tones are always dark enough for white text (see [TopicColors]).
 */
internal fun DrawScope.drawTopicCover(colors: TopicColors, glowStrength: Float = 1f) {
    drawRect(Brush.linearGradient(listOf(colors.coverStart, colors.coverEnd), start = Offset.Zero, end = Offset(size.width, size.height)))
    val glowCenter = Offset(size.width * 0.92f, size.height * 0.04f)
    val glowRadius = size.maxDimension * 0.72f
    drawCircle(
        brush = Brush.radialGradient(listOf(colors.coverGlow.copy(alpha = 0.62f * glowStrength), Color.Transparent), center = glowCenter, radius = glowRadius),
        radius = glowRadius,
        center = glowCenter,
    )
    val deepCenter = Offset(size.width * 0.05f, size.height * 1.05f)
    val deepRadius = size.maxDimension * 0.6f
    drawCircle(
        brush = Brush.radialGradient(listOf(colors.coverStart.copy(alpha = 0.55f * glowStrength), Color.Transparent), center = deepCenter, radius = deepRadius),
        radius = deepRadius,
        center = deepCenter,
    )
}

/**
 * A large category tile: the topic's gradient with a soft glow, its big pictogram as a tilted
 * watermark, a white icon tile, the title, a one-line description and – top right – how many of
 * its topics are followed (a check with the count) or a "+".
 */
@Composable
internal fun CategoryTile(tile: CategoryTileUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = discoverStrings()
    val colors = rememberTopicColors(tile.topic)
    val interaction = remember { MutableInteractionSource() }
    val description = strings.tileDescription(tile.topic.title, tile.subtitle, tile.followedCount)
    val watermark = tile.topic.iconVector()
    Box(
        modifier = modifier
            .heightIn(min = 172.dp)
            .pressScale(interaction, pressedScale = 0.96f)
            .shadow(14.dp, TileShape, ambientColor = colors.coverEnd.copy(alpha = 0.35f), spotColor = colors.coverEnd.copy(alpha = 0.35f))
            .clip(TileShape)
            .drawBehind { drawTopicCover(colors) }
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))), TileShape)
            .clickable(interactionSource = interaction, indication = ripple(color = Color.White), onClickLabel = strings.openCategory, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
    ) {
        if (watermark != null) {
            Icon(
                imageVector = watermark,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.13f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 22.dp, y = 20.dp)
                    .size(118.dp)
                    .graphicsLayer { rotationZ = -14f },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clearAndSetSemantics { },
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    TopicGlyph(tile.topic, tint = Color.White, size = 26.dp)
                }
                Spacer(Modifier.weight(1f))
                FollowedBadge(count = tile.followedCount, colors = colors, label = strings.followedBadge(tile.followedCount))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = tile.topic.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (tile.subCount > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = strings.subtopicCount(tile.subCount).uppercase(LocalAppLanguage.current.locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.74f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** ✓ 3 on a white pill when something is followed, else a quiet "+" ring. */
@Composable
private fun FollowedBadge(count: Int, colors: TopicColors, label: String) {
    if (count > 0) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.94f))
                .padding(start = 7.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.coverEnd, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(3.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = colors.coverEnd)
        }
    } else {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** One row of the category grid: [tiles] side by side with equal heights, empty slots kept. */
@Composable
internal fun CategoryRow(tiles: List<CategoryTileUi>, columns: Int, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        tiles.forEach { tile ->
            CategoryTile(
                tile = tile,
                onClick = { onOpen(tile.topic.id) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        repeat(columns - tiles.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Space between tiles (both directions). */
internal val TileGap = 12.dp

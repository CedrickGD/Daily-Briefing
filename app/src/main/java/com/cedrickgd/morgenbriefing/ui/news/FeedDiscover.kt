package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.components.entrance
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.components.rememberEntrance
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import com.cedrickgd.morgenbriefing.ui.glass.GlassLevel
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.glass
import com.cedrickgd.morgenbriefing.ui.theme.OverlineLetterSpacing

/** Width of a suggestion tile in the "Mehr entdecken" row. */
private val SuggestionWidth = 168.dp

/**
 * The invitation at the top of a thin feed (few followed topics): a glass card with a fan of
 * topic tiles, "Mach den Feed zu deinem" and a filled "Themen entdecken" pill ([onDiscover]).
 *
 * @param preview a few unfollowed topics whose tiles make the fan.
 */
@Composable
fun DiscoverInvite(preview: List<Topic>, onDiscover: () -> Unit, modifier: Modifier = Modifier) {
    val strings = feedStrings()
    val language = LocalAppLanguage.current
    val entrance = rememberEntrance(durationMillis = 560)
    GlassCard(modifier = modifier.entrance(entrance, distance = 16.dp)) {
        if (preview.isNotEmpty()) {
            Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                preview.take(4).forEachIndexed { index, topic ->
                    TopicTile(
                        topic = topic,
                        diameter = 44.dp,
                        modifier = Modifier
                            .zIndex((10 - index).toFloat())
                            .offset(x = (-10 * index).dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        Text(
            text = strings.ctaOverline.uppercase(language.locale),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = OverlineLetterSpacing),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = strings.ctaTitle,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = strings.ctaText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        GlassPillButton(text = strings.discoverDescription, onClick = onDiscover, icon = Icons.Rounded.Explore, filled = true)
    }
}

/**
 * "Mehr entdecken" at the end of "Für dich": an overline and title, then a row of gradient tiles
 * of categories the user does not follow yet ([onOpenDiscover] with the category) and a last
 * glass tile for every topic ([onOpenDiscover] with null).
 */
internal fun LazyListScope.suggestionItems(
    suggestions: List<Topic>,
    subtitles: Map<String, String>,
    onOpenDiscover: (String?) -> Unit,
) {
    if (suggestions.isEmpty()) return
    item(key = "suggestions-title", contentType = "feed-intro") {
        val strings = feedStrings()
        FeedViewIntro(
            overline = strings.suggestionsOverline,
            title = strings.suggestionsTitle,
            modifier = Modifier
                .animateItem()
                .padding(horizontal = FeedGutter)
                .padding(top = 20.dp),
        )
    }
    item(key = "suggestions-row", contentType = "suggestions") {
        LazyRow(
            modifier = Modifier
                .animateItem()
                .fillMaxWidth()
                .nestedScroll(KeepHorizontalScroll),
            contentPadding = PaddingValues(horizontal = FeedGutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(suggestions, key = { it.id }) { topic ->
                SuggestionTile(topic, subtitles[topic.id].orEmpty(), onClick = { onOpenDiscover(topic.id) })
            }
            item(key = "all-topics") {
                AllTopicsTile(onClick = { onOpenDiscover(null) })
            }
        }
    }
}

/** A category to discover: its gradient, its icon in a frosted circle, title and one line. */
@Composable
private fun SuggestionTile(topic: Topic, subtitle: String, onClick: () -> Unit) {
    val colors = rememberTopicColors(topic)
    val interaction = remember { MutableInteractionSource() }
    val strings = feedStrings()
    Column(
        modifier = Modifier
            .width(SuggestionWidth)
            .heightIn(min = 156.dp)
            .pressScale(interaction, pressedScale = 0.96f)
            .clip(GlassShapes.tile)
            .drawWithCache {
                val base = Brush.linearGradient(listOf(colors.coverStart, colors.coverEnd), start = Offset.Zero, end = Offset(size.width, size.height))
                val glow = Brush.radialGradient(
                    listOf(colors.coverGlow.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.05f),
                    radius = size.maxDimension * 0.7f,
                )
                onDrawBehind {
                    drawRect(base)
                    drawRect(glow)
                }
            }
            .border(1.dp, Color.White.copy(alpha = 0.18f), GlassShapes.tile)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                role = Role.Button,
                onClickLabel = strings.discoverDescription,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { }
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            TopicGlyph(topic, tint = Color.White, size = 22.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = topic.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The last tile of the suggestions: every topic, on glass. */
@Composable
private fun AllTopicsTile(onClick: () -> Unit) {
    val strings = feedStrings()
    val interaction = remember { MutableInteractionSource() }
    val ink = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .width(SuggestionWidth)
            .heightIn(min = 156.dp)
            .pressScale(interaction, pressedScale = 0.96f)
            .glass(shape = GlassShapes.tile, level = GlassLevel.Regular)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) { }
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Explore, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = strings.allTopics,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = strings.allTopicsText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

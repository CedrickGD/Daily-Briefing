package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsAge
import com.cedrickgd.morgenbriefing.data.model.PrefetchMode
import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.i18n.settingsStrings
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import kotlin.math.roundToInt

/** Callbacks of the feed card. */
internal class FeedCardActions(
    val onSectionSize: (Int) -> Unit,
    val onNewsAge: (NewsAge) -> Unit,
    val onShowImages: (Boolean) -> Unit,
    val onReadInApp: (Boolean) -> Unit,
    val onReadableOnly: (Boolean) -> Unit,
    val onPrefetch: (PrefetchMode) -> Unit,
    val onHideRead: (Boolean) -> Unit,
    /** Opens "Gelesen & ausgeblendet". */
    val onOpenHistory: () -> Unit,
)

/**
 * Settings section "Feed": in the feed – how many articles a topic shows in "Für dich", how far
 * back the feed reaches, pictures, hiding read stories and the way to the read and hidden ones
 * ([readCount], [hiddenCount]); while reading – the in-app reader, readable-only articles and
 * preloading.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FeedCard(settings: AppSettings, actions: FeedCardActions, modifier: Modifier = Modifier, readCount: Int = 0, hiddenCount: Int = 0) {
    val strings = settingsStrings()
    val colors = MaterialTheme.colorScheme
    // Local while dragging; follows the stored value whenever it changes.
    var size by remember(settings.sectionSize) { mutableIntStateOf(settings.sectionSize) }
    val haptics = LocalHapticFeedback.current
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Overline(strings.inTheFeed, color = colors.primary)
        Spacer(Modifier.height(10.dp))
        Overline(strings.perTopic)
        AnimatedContent(
            targetState = size,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "sectionSize",
        ) { value ->
            Text(text = value.toString(), style = MaterialTheme.typography.displaySmall, color = colors.onSurface)
        }
        Text(text = strings.perTopicHint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Slider(
            value = size.toFloat(),
            onValueChange = {
                val next = it.roundToInt().coerceIn(AppSettings.MIN_SECTION_SIZE, AppSettings.MAX_SECTION_SIZE)
                if (next != size) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                size = next
            },
            onValueChangeFinished = { actions.onSectionSize(size) },
            valueRange = AppSettings.MIN_SECTION_SIZE.toFloat()..AppSettings.MAX_SECTION_SIZE.toFloat(),
            steps = AppSettings.MAX_SECTION_SIZE - AppSettings.MIN_SECTION_SIZE - 1,
            thumb = { CalmSliderThumb() },
            track = { state -> CalmSliderTrack(state) },
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics {
                    contentDescription = strings.perTopicDescription
                    stateDescription = size.toString()
                },
        )
        Spacer(Modifier.height(16.dp))
        Overline(strings.newsAgeOverline)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            NewsAge.entries.forEach { age ->
                ChoicePill(label = strings.newsAge(age), selected = age == settings.newsAge, onClick = { actions.onNewsAge(age) })
            }
        }
        Text(
            text = strings.newsAgeHint,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            title = strings.showImages,
            subtitle = if (settings.showImages) strings.showImagesOn else strings.showImagesOff,
            checked = settings.showImages,
            onCheckedChange = actions.onShowImages,
            leading = { IconTile(Icons.Rounded.Image) },
        )
        ToggleRow(
            title = strings.hideRead,
            subtitle = if (settings.hideRead) strings.hideReadOn else strings.hideReadOff,
            checked = settings.hideRead,
            onCheckedChange = actions.onHideRead,
            leading = { IconTile(Icons.Rounded.VisibilityOff, tint = colors.tertiary) },
        )
        HistoryRow(
            title = strings.historyTitle,
            summary = strings.historySummary(readCount, hiddenCount),
            onClick = actions.onOpenHistory,
        )

        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = colors.onSurface.copy(alpha = 0.08f))
        Overline(strings.whileReading, color = colors.primary)
        Spacer(Modifier.height(4.dp))
        ToggleRow(
            title = strings.readInApp,
            subtitle = if (settings.readInApp) strings.readInAppOn else strings.readInAppOff,
            checked = settings.readInApp,
            onCheckedChange = actions.onReadInApp,
            leading = { IconTile(Icons.AutoMirrored.Rounded.Article) },
        )
        ToggleRow(
            title = strings.readableOnly,
            subtitle = if (settings.readableOnly) strings.readableOnlyOn else strings.readableOnlyOff,
            checked = settings.readableOnly,
            onCheckedChange = actions.onReadableOnly,
            leading = { IconTile(Icons.Rounded.VerifiedUser, tint = StatusPalette.ok()) },
        )
        AnimatedVisibility(
            visible = settings.readInApp,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.CloudDownload, tint = colors.tertiary)
                    Spacer(Modifier.width(14.dp))
                    Text(strings.prefetch, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrefetchMode.entries.forEach { mode ->
                        ChoicePill(label = strings.prefetchMode(mode), selected = mode == settings.prefetch, onClick = { actions.onPrefetch(mode) })
                    }
                }
                AnimatedContent(
                    targetState = settings.prefetch to settings.readableOnly,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "prefetchHint",
                ) { (mode, verified) ->
                    // "Nur vollständige Artikel" verifies (and so loads) ahead on any connection: say so.
                    Text(
                        text = if (verified) strings.prefetchWhileReadableOnly else strings.prefetchHint(mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** The row to "Gelesen & ausgeblendet": an icon tile, the title, how many stories it holds and a chevron. */
@Composable
private fun HistoryRow(title: String, summary: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(Icons.Rounded.History, tint = colors.secondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/** Settings section "Wetter auf Heute": which blocks the page shows besides the hero. */
@Composable
internal fun WeatherCardsCard(settings: AppSettings, onToggle: (WeatherCard, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val strings = settingsStrings()
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.WbCloudy)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Overline(strings.weatherCardsOverline)
                Text(
                    text = strings.weatherCardsHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        WeatherCard.entries.forEach { card ->
            ToggleRow(
                title = strings.weatherCardTitle(card),
                subtitle = strings.weatherCardText(card),
                checked = card in settings.weatherCards,
                onCheckedChange = { onToggle(card, it) },
            )
        }
    }
}

/**
 * A selectable pill for one of a few options (segmented choice on a glass card), optionally with
 * an icon; [description] replaces the label for screen readers when the icon means something.
 */
@Composable
internal fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null, description: String? = null) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimary else colors.onSurface
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(colors.primary) else Modifier.lamina(CircleShape, strong = true))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier)
            .padding(start = if (icon != null) 12.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

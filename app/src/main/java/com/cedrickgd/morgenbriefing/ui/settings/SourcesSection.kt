package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.sourcesStrings
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import com.cedrickgd.morgenbriefing.ui.news.TopicBadge
import com.cedrickgd.morgenbriefing.ui.news.hostOf

/** Callbacks of the sources section. */
internal class SourcesActions(
    val onSourceEnabled: (sourceId: String, enabled: Boolean) -> Unit,
    val onEnableAll: (topicId: String) -> Unit,
    val onAddFeed: (Topic) -> Unit,
    val onRemoveFeed: (sourceId: String) -> Unit,
    val onEditTopic: (Topic) -> Unit,
    val onDeleteTopic: (Topic) -> Unit,
    /** "Internationale Quellen auf Deutsch" ([AppSettings.internationalSources]). */
    val onInternationalSources: (Boolean) -> Unit,
    /** "Englischsprachige Quellen" ([AppSettings.englishSources]). */
    val onEnglishSources: (Boolean) -> Unit,
)

/**
 * Settings section "Quellen": for the current news edition, every followed topic (and the own
 * topics that are paused) with each source it reads – publisher feeds, the Google News page as
 * one understandable line ("Google News · Suche: Börse, DAX, Aktien …"), own feeds – a switch
 * per source and how its last fetch went. Above the topics, where the edition can use them, the
 * switches for newsrooms from abroad and for English feeds ([TopicSources.mixes]). Own feeds can be removed, own topics edited or
 * deleted, "Eigene Quelle" adds a feed to any topic.
 *
 * @param blocked the sites the reader cannot show (their feeds are left out with "readable only").
 * @param status the last fetch of each source by id.
 * @param expanded ids of the topics whose sources are shown.
 */
@Composable
internal fun SourcesSection(
    settings: AppSettings,
    blocked: Set<String>,
    status: Map<String, SourceStatus>,
    expanded: Set<String>,
    onToggleExpanded: (String) -> Unit,
    actions: SourcesActions,
    modifier: Modifier = Modifier,
) {
    val lang = LocalAppLanguage.current
    val strings = sourcesStrings()
    val groups = remember(settings, blocked, lang) { SourcesLogic.groups(settings, blocked, lang) }
    val edition = remember(settings.edition, lang) { EditionLabels.option(settings.edition, lang) }
    GlassCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            FlagTile(edition.flag, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = strings.sourcesFor("${edition.country} · ${edition.language}"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        )
        // The two switches widen every topic below, so they sit above the topics; each only
        // where it can add something for this edition.
        val offers = remember(settings.edition) { TopicSources.readsAbroad(settings.edition) to TopicSources.readsEnglish(settings.edition) }
        if (offers.first) {
            ToggleRow(
                title = strings.internationalSources,
                subtitle = if (settings.internationalSources) strings.internationalSourcesOn else strings.internationalSourcesOff,
                checked = settings.internationalSources,
                onCheckedChange = actions.onInternationalSources,
                leading = { IconTile(Icons.Rounded.Public) },
            )
        }
        if (offers.second) {
            ToggleRow(
                title = strings.englishSources,
                subtitle = if (settings.englishSources) strings.englishSourcesOn else strings.englishSourcesOff,
                checked = settings.englishSources,
                onCheckedChange = actions.onEnglishSources,
                leading = { IconTile(Icons.Rounded.Translate, tint = MaterialTheme.colorScheme.secondary) },
            )
        }
        if (offers.first || offers.second) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            )
        }
        groups.forEachIndexed { index, group ->
            key(group.topic.id) {
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    )
                }
                TopicSourcesRow(
                    group = group,
                    settings = settings,
                    status = status,
                    lang = lang,
                    expanded = group.topic.id in expanded,
                    onToggleExpanded = { onToggleExpanded(group.topic.id) },
                    actions = actions,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicSourcesRow(
    group: TopicSourcesGroup,
    settings: AppSettings,
    status: Map<String, SourceStatus>,
    lang: AppLanguage,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    actions: SourcesActions,
) {
    val strings = sourcesStrings()
    val common = commonStrings()
    val colors = MaterialTheme.colorScheme
    val topic = group.topic
    val disabled = settings.disabledFeeds
    val enabledCount = group.sources.count { it.id !in disabled }
    val failing = group.sources.count { it.id !in disabled && SourcesLogic.health(status[it.id]) is SourceHealth.Failed }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "sourcesChevron")
    val summary = remember(group, disabled, lang) { SourcesLogic.summary(group, disabled, lang) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onToggleExpanded)
                .semantics { stateDescription = if (expanded) strings.expanded else strings.collapsed }
                .heightIn(min = 60.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopicBadge(topic, diameter = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(topic.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (failing > 0) {
                Icon(
                    Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = StatusPalette.attention(),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(18.dp),
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(start = 4.dp, bottom = 10.dp)) {
                group.sources.forEach { source ->
                    key(source.id) {
                        SourceRow(
                            source = source,
                            title = if (source.isGoogleNews) SourcesLogic.describe(SourcesLogic.googleLine(source, settings), lang) else source.name,
                            health = SourcesLogic.health(status[source.id]),
                            lang = lang,
                            enabled = source.id !in disabled,
                            onEnabled = { actions.onSourceEnabled(source.id, it) },
                            onRemove = if (source.isCustom) ({ actions.onRemoveFeed(source.id) }) else null,
                        )
                    }
                }
                if (group.sources.isEmpty()) {
                    Text(
                        text = strings.noSourceInTopic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                if (group.hiddenUnreadable > 0) {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.hiddenUnreadable(group.hiddenUnreadable), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    GlassPillButton(strings.addFeed, onClick = { actions.onAddFeed(topic) }, icon = Icons.Rounded.Add, style = PillStyle.Tonal)
                    if (enabledCount < group.sources.size) {
                        GlassPillButton(strings.allOn, onClick = { actions.onEnableAll(topic.id) }, style = PillStyle.Quiet)
                    }
                    if (topic.custom) {
                        GlassPillButton(strings.edit, onClick = { actions.onEditTopic(topic) }, icon = Icons.Rounded.Edit, style = PillStyle.Quiet)
                        GlassPillButton(common.delete, onClick = { actions.onDeleteTopic(topic) }, icon = Icons.Rounded.DeleteOutline, style = PillStyle.Quiet)
                    }
                }
            }
        }
    }
}

/** One source: a small tile (feed or Google News), its name, host and last fetch, a switch – and a bin for own feeds. */
@Composable
private fun SourceRow(
    source: FeedSource,
    title: String,
    health: SourceHealth?,
    lang: AppLanguage,
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val strings = sourcesStrings()
    val colors = MaterialTheme.colorScheme
    val haptic = rememberToggleHaptics()
    val failed = health is SourceHealth.Failed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = { value ->
                    haptic(value)
                    onEnabled(value)
                },
            )
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = if (source.isGoogleNews) Icons.Rounded.TravelExplore else Icons.Rounded.RssFeed,
            tint = if (source.isGoogleNews) colors.tertiary else colors.primary,
            size = 32.dp,
            modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.55f },
        )
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .graphicsLayer { alpha = if (enabled) 1f else 0.55f },
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = hostOf(source.url) + if (source.isCustom) " · ${strings.ownSource}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (health != null && enabled) {
                Text(
                    text = SourcesLogic.healthLabel(health, lang),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (failed) StatusPalette.attention() else StatusPalette.ok(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = strings.removeSource, tint = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(6.dp))
        GlassSwitch(checked = enabled)
    }
}

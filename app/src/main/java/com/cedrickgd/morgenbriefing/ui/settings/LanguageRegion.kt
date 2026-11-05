package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.Regions
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.regionStrings
import com.cedrickgd.morgenbriefing.ui.components.Emoji
import com.cedrickgd.morgenbriefing.ui.components.GlassSheet
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import java.util.Locale

/**
 * Settings "Sprache & Region", first card: the app language (applies at once), the news
 * edition (flag, country in the interface language, the edition's own language; tap → picker
 * sheet) and, for the German edition, the Bundesland of the "Region" topic.
 */
@Composable
internal fun LanguageEditionCard(
    settings: AppSettings,
    onLanguage: (AppLanguage?) -> Unit,
    onOpenEditions: () -> Unit,
    onPickRegion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = regionStrings()
    val lang = LocalAppLanguage.current
    val colors = MaterialTheme.colorScheme
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Translate)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Overline(strings.appLanguage)
                Text(strings.appLanguageHint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        LanguagePills(selected = settings.uiLanguage, onSelect = onLanguage)

        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = colors.onSurface.copy(alpha = 0.08f))

        val option = remember(settings.edition, lang) { EditionLabels.option(settings.edition, lang) }
        EditionRow(option = option, onClick = onOpenEditions)
        Text(
            text = strings.newsEditionHint,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        AnimatedVisibility(
            visible = EditionChange.showsRegionPresets(settings.edition),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val region = remember(settings.region) { Regions.byKey(settings.region) }
            Column {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = colors.onSurface.copy(alpha = 0.08f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Map, tint = colors.tertiary)
                    Spacer(Modifier.width(14.dp))
                    ValueRow(
                        label = strings.region,
                        value = "${region.name} · ${region.feeds.joinToString(", ") { it.name }}",
                        actionLabel = strings.change,
                        onClick = onPickRegion,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** "Wie das Handy" and the five languages by their own names, as choice pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LanguagePills(selected: AppLanguage?, onSelect: (AppLanguage?) -> Unit, modifier: Modifier = Modifier) {
    val strings = regionStrings()
    val haptic = rememberToggleHaptics()
    FlowRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguageChoice.OPTIONS.forEach { option ->
            ChoicePill(
                label = option?.nativeName ?: strings.followPhoneWith(AppLanguage.system().nativeName),
                selected = option == selected,
                onClick = {
                    if (option != selected) haptic(true)
                    onSelect(option)
                },
            )
        }
    }
}

/** The current edition: a big flag, the country in serif and the edition's language; tap to change. */
@Composable
internal fun EditionRow(option: EditionOption, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = regionStrings()
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = strings.change, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${strings.newsEdition}: ${strings.editionDescription(option.country, option.language)}"
            }
            .heightIn(min = 64.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagTile(option.flag, size = 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Overline(strings.newsEdition)
            Text(
                text = option.country,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.headlineSmall.fontFamily),
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(option.language, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(strings.change, style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** A flag on a soft accent tile (a neutral newspaper symbol when there is no flag). */
@Composable
internal fun FlagTile(flag: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = isGlassDark()
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.primaryContainer.copy(alpha = if (dark) 0.55f else 0.85f),
                        colors.tertiaryContainer.copy(alpha = if (dark) 0.35f else 0.6f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.10f else 0.6f), shape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (flag.isNotEmpty()) {
            Emoji(flag, size = (size.value * 0.46f).sp)
        } else {
            Icon(Icons.Rounded.Newspaper, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/**
 * Settings "Sprache & Region", second card: the places the user follows the local news of,
 * each with the edition its news come in (Washington, D.C. → USA · English), remove, follow
 * again, and "Ort hinzufügen" (the place picker).
 */
@Composable
internal fun LocalNewsCard(
    settings: AppSettings,
    onAddPlace: () -> Unit,
    onRemovePlace: (topicId: String) -> Unit,
    onFollowPlace: (topicId: String) -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = regionStrings()
    val lang = LocalAppLanguage.current
    val colors = MaterialTheme.colorScheme
    val rows = remember(settings.localPlaces, settings.followed, settings.edition) { LocalPlaces.rows(settings) }
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.PinDrop, tint = Color(Topics.LOCAL_SEED))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Overline(strings.localNews)
                Text(strings.localNewsHint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .then(if (reduceMotion) Modifier else Modifier.animateContentSize()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (rows.isEmpty()) {
                Text(
                    text = strings.localEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lamina()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            rows.forEach { row ->
                key(row.topicId) {
                    val option = remember(row.edition, lang) { EditionLabels.option(row.edition, lang) }
                    LocalPlaceRow(
                        name = row.place.name,
                        flag = flagEmoji(row.place.countryCode).ifEmpty { option.flag },
                        detail = if (row.followed) strings.localEdition("${option.flag} ${option.country}", option.language) else strings.notInFeed,
                        followed = row.followed,
                        onRemove = { onRemovePlace(row.topicId) },
                        onFollow = { onFollowPlace(row.topicId) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassPillButton(text = strings.addPlace, onClick = onAddPlace, icon = Icons.Rounded.Add, style = PillStyle.Tonal)
    }
}

@Composable
private fun LocalPlaceRow(name: String, flag: String, detail: String, followed: Boolean, onRemove: () -> Unit, onFollow: () -> Unit) {
    val strings = regionStrings()
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lamina(RoundedCornerShape(18.dp), strong = followed)
            .heightIn(min = 64.dp)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagTile(flag, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { },
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!followed) {
            GlassPillButton(text = strings.follow, onClick = onFollow, style = PillStyle.Quiet)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = strings.removePlace(name), tint = colors.onSurfaceVariant)
        }
    }
}

/**
 * The news edition picker as a glass sheet: a search over country and language names
 * (interface language, English, the edition's own language), suggestions for this phone while
 * nothing is typed, then every edition. Picking one closes the sheet.
 */
@Composable
internal fun EditionPickerSheet(current: NewsEdition, onPick: (NewsEdition) -> Unit, onDismiss: () -> Unit) {
    val strings = regionStrings()
    val lang = LocalAppLanguage.current
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    val options = remember(lang) { EditionLabels.options(lang) }
    val byEdition = remember(options) { options.associateBy { it.edition } }
    val suggestions = remember(current, lang) {
        EditionSearch.suggestions(current, Locale.getDefault(), lang).mapNotNull { byEdition[it] }
    }
    val results = remember(options, query, lang) { EditionSearch.filter(options, query, lang) }
    val searching = query.isNotBlank()

    GlassSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp)) {
            Overline(strings.editionSheetOverline)
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.editionSheetTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = GlassShapes.chip,
                placeholder = { Text(strings.editionSearch) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, contentDescription = strings.clearSearch) }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface.copy(alpha = if (isGlassDark()) 0.18f else 0.55f),
                    unfocusedContainerColor = colors.surface.copy(alpha = if (isGlassDark()) 0.12f else 0.4f),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .imePadding()
                .selectableGroup(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!searching) {
                item(key = "h-suggested", contentType = "heading") { ListHeading(strings.editionSuggested) }
                items(suggestions, key = { "s:" + it.edition.key }, contentType = { "edition" }) { option ->
                    EditionOptionRow(option, selected = option.edition == current, onClick = { onPick(option.edition) })
                }
                item(key = "h-all", contentType = "heading") { ListHeading(strings.editionAll) }
            }
            items(results, key = { "a:" + it.edition.key }, contentType = { "edition" }) { option ->
                EditionOptionRow(option, selected = option.edition == current, onClick = { onPick(option.edition) })
            }
            if (searching && results.isEmpty()) {
                item(key = "none", contentType = "empty") {
                    Text(
                        text = strings.editionNone(query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListHeading(text: String) {
    Overline(text, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 6.dp))
}

@Composable
private fun EditionOptionRow(option: EditionOption, selected: Boolean, onClick: () -> Unit) {
    val strings = regionStrings()
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(18.dp))
            .then(if (selected) Modifier.background(colors.primaryContainer.copy(alpha = if (isGlassDark()) 0.45f else 0.7f)) else Modifier)
            .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = strings.editionDescription(option.country, option.language)
                if (selected) stateDescription = strings.selected
            }
            .heightIn(min = 60.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlagTile(option.flag, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(option.country, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(option.language, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        }
    }
}

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
import androidx.compose.ui.text.font.FontWeight
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
 * Settings "Sprache & Region", first card: three choices that never change one another – the
 * app language (applies at once), the news region (flag and country; tap → picker sheet) and
 * the news language (tap → picker sheet) – and, for Germany in German, the Bundesland of the
 * "Region" topic.
 */
@Composable
internal fun LanguageEditionCard(
    settings: AppSettings,
    onLanguage: (AppLanguage?) -> Unit,
    onOpenNewsRegion: () -> Unit,
    onOpenNewsLanguage: () -> Unit,
    onPickRegion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = regionStrings()
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

        NewsEditionRows(edition = settings.edition, onOpenRegion = onOpenNewsRegion, onOpenLanguage = onOpenNewsLanguage)

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

/**
 * The news region and the news language as two rows of their own (settings and first start),
 * and one line on what the pair reads: independent choices ([explain]; the first-start step
 * says so itself), or – where Google News has no edition of the country in that language –
 * that the app searches for the country instead.
 */
@Composable
internal fun NewsEditionRows(
    edition: NewsEdition,
    onOpenRegion: () -> Unit,
    onOpenLanguage: () -> Unit,
    modifier: Modifier = Modifier,
    explain: Boolean = true,
) {
    val strings = regionStrings()
    val lang = LocalAppLanguage.current
    val region = remember(edition.country, lang) { EditionLabels.region(edition.country, lang) }
    val language = remember(edition.language, lang) { EditionLabels.language(edition.language, lang) }
    Column(modifier) {
        ChoiceRow(
            label = strings.newsRegion,
            value = region.name,
            onClick = onOpenRegion,
            tile = { FlagTile(region.flag, size = 48.dp) },
        )
        Spacer(Modifier.height(6.dp))
        ChoiceRow(
            label = strings.newsLanguage,
            value = language.nativeName,
            detail = language.name.takeIf { !it.equals(language.nativeName, ignoreCase = true) },
            onClick = onOpenLanguage,
            tile = { LanguageTile(language.language, size = 48.dp) },
        )
        val hint = when {
            !edition.isNative -> strings.newsSearchedHint(region.name, EditionLabels.languageInSentence(edition.language, lang))
            explain -> strings.newsEditionHint
            else -> null
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** One choice that opens a picker: its tile, what it is (as an overline), the current value in serif and "Ändern". */
@Composable
private fun ChoiceRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    tile: @Composable () -> Unit,
) {
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
                contentDescription = if (detail == null) "$label: $value" else "$label: $value, $detail"
            }
            .heightIn(min = 60.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // An overline that may take a second line: "Région des actualités" beside "Changer" on a narrow phone.
            val locale = LocalAppLanguage.current.locale
            Text(
                text = remember(label, locale) { label.uppercase(locale) },
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.headlineSmall.fontFamily),
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(strings.change, style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** A flag on a soft accent tile (a neutral newspaper symbol when there is no flag). */
@Composable
internal fun FlagTile(flag: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    SoftTile(size, modifier) {
        if (flag.isNotEmpty()) {
            Emoji(flag, size = (size.value * 0.46f).sp)
        } else {
            Icon(Icons.Rounded.Newspaper, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/** A news language on the same soft tile as the flags: its code in capitals ("DE", "EN"). */
@Composable
internal fun LanguageTile(language: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    SoftTile(size, modifier) {
        Text(
            text = language.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = (size.value * 0.32f).sp, letterSpacing = 0.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
        )
    }
}

@Composable
private fun SoftTile(size: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
        content()
    }
}

/**
 * Settings "Sprache & Region", second card: the places the user follows the local news of,
 * each with its country and the news language it is read in (Washington, D.C. → USA · Deutsch),
 * remove, follow again, and "Ort hinzufügen" (the place picker).
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
 * The news region picker as a glass sheet: a search over the countries' names (interface
 * language, English, their own languages), suggestions for this phone while nothing is typed,
 * then every country. Picking one closes the sheet; the news language stays as it is.
 */
@Composable
internal fun NewsRegionPickerSheet(current: NewsEdition, onPick: (country: String) -> Unit, onDismiss: () -> Unit) {
    val strings = regionStrings()
    val lang = LocalAppLanguage.current
    val colors = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    val options = remember(lang) { EditionLabels.regions(lang) }
    val suggestions = remember(current, options) {
        val byCountry = options.associateBy { it.country }
        EditionSearch.regionSuggestions(current, Locale.getDefault()).mapNotNull { byCountry[it] }
    }
    val results = remember(options, query) { EditionSearch.filterRegions(options, query) }
    val searching = query.isNotBlank()

    GlassSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = SheetTextInset, end = SheetTextInset, top = 8.dp, bottom = 12.dp)) {
            SheetHeading(overline = strings.newsRegion, title = strings.newsRegionTitle)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = GlassShapes.chip,
                placeholder = { Text(strings.newsRegionSearch) },
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
            contentPadding = PaddingValues(start = SettingsGutter, end = SettingsGutter, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!searching) {
                item(key = "h-suggested", contentType = "heading") { ListHeading(strings.newsRegionSuggested) }
                items(suggestions, key = { "s:" + it.country }, contentType = { "option" }) { option ->
                    RegionOptionRow(option, selected = option.country == current.country, onClick = { onPick(option.country) })
                }
                item(key = "h-all", contentType = "heading") { ListHeading(strings.newsRegionAll) }
            }
            items(results, key = { "a:" + it.country }, contentType = { "option" }) { option ->
                RegionOptionRow(option, selected = option.country == current.country, onClick = { onPick(option.country) })
            }
            if (searching && results.isEmpty()) {
                item(key = "none", contentType = "empty") {
                    Text(
                        text = strings.newsRegionNone(query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

/**
 * The news language picker as a glass sheet: first the languages Google News has an edition
 * of the current region in, then every other language – for those the app searches for the
 * region by name, which the sheet says. Picking one closes the sheet; the region stays.
 */
@Composable
internal fun NewsLanguagePickerSheet(current: NewsEdition, onPick: (language: String) -> Unit, onDismiss: () -> Unit) {
    val strings = regionStrings()
    val lang = LocalAppLanguage.current
    val colors = MaterialTheme.colorScheme
    val country = remember(current.country, lang) { EditionLabels.country(current.country, lang) }
    val (own, others) = remember(current.country, lang) { EditionSearch.languagesFor(current.country, EditionLabels.languages(lang)) }

    GlassSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = SheetTextInset, end = SheetTextInset, top = 8.dp, bottom = 4.dp)) {
            SheetHeading(overline = strings.newsLanguage, title = strings.newsLanguageTitle)
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .selectableGroup(),
            contentPadding = PaddingValues(start = SettingsGutter, end = SettingsGutter, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "h-own", contentType = "heading") { ListHeading(strings.newsLanguageOwn(country)) }
            items(own, key = { "o:" + it.language }, contentType = { "option" }) { option ->
                LanguageOptionRow(option, selected = option.language == current.language, onClick = { onPick(option.language) })
            }
            item(key = "h-other", contentType = "heading") {
                Column {
                    ListHeading(strings.newsLanguageOther)
                    Text(
                        text = strings.newsLanguageOtherHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                    )
                }
            }
            items(others, key = { "a:" + it.language }, contentType = { "option" }) { option ->
                LanguageOptionRow(option, selected = option.language == current.language, onClick = { onPick(option.language) })
            }
        }
    }
}

/** Where a sheet's own text starts: a little inside the rows, which carry their own padding. */
private val SheetTextInset = SettingsGutter + 8.dp

@Composable
private fun SheetHeading(overline: String, title: String) {
    Overline(overline)
    Spacer(Modifier.height(4.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun ListHeading(text: String) {
    Overline(text, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 6.dp))
}

@Composable
private fun RegionOptionRow(option: RegionOption, selected: Boolean, onClick: () -> Unit) {
    OptionRow(title = option.name, detail = null, selected = selected, onClick = onClick) { FlagTile(option.flag, size = 40.dp) }
}

@Composable
private fun LanguageOptionRow(option: NewsLanguageOption, selected: Boolean, onClick: () -> Unit) {
    OptionRow(
        title = option.nativeName,
        detail = option.name.takeIf { !it.equals(option.nativeName, ignoreCase = true) },
        selected = selected,
        onClick = onClick,
    ) { LanguageTile(option.language, size = 40.dp) }
}

/** One radio row of a picker sheet: tile, title, an optional second line and a check mark when chosen. */
@Composable
private fun OptionRow(title: String, detail: String?, selected: Boolean, onClick: () -> Unit, tile: @Composable () -> Unit) {
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
                contentDescription = if (detail == null) title else strings.editionDescription(title, detail)
                if (selected) stateDescription = strings.selected
            }
            .heightIn(min = 60.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tile()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
        }
    }
}

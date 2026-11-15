package com.cedrickgd.morgenbriefing.ui.discover

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.discoverStrings
import com.cedrickgd.morgenbriefing.ui.components.Emoji
import com.cedrickgd.morgenbriefing.ui.components.GlassSheet
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import com.cedrickgd.morgenbriefing.ui.news.TopicGlyph
import com.cedrickgd.morgenbriefing.ui.news.rememberTopicColors
import com.cedrickgd.morgenbriefing.ui.settings.flagEmoji

/**
 * "Ort hinzufügen" as a glass sheet: a search field with live results (Open-Meteo geocoding,
 * debounced in the view model), each with its flag and the news it would bring: in the news
 * language [newsLanguage], from its country ("Nachrichten auf Deutsch · Vereinigte Staaten").
 * Picking one follows its local news.
 */
@Composable
internal fun PlaceSearchSheet(
    search: PlaceSearchUi,
    newsLanguage: String,
    onQueryChange: (String) -> Unit,
    onPick: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = discoverStrings()
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    GlassSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = strings.placeSheetOverline.uppercase(LocalAppLanguage.current.locale),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = strings.placeSheetTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(14.dp))
            LaunchedEffect(focusRequester) { runCatching { focusRequester.requestFocus() } }
            OutlinedTextField(
                value = search.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                shape = GlassShapes.chip,
                placeholder = { Text(strings.placeSearchHint) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = strings.clearSearch)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface.copy(alpha = if (isGlassDark()) 0.18f else 0.55f),
                    unfocusedContainerColor = colors.surface.copy(alpha = if (isGlassDark()) 0.12f else 0.4f),
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search.results.firstOrNull()?.let(onPick) }),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .heightIn(min = 4.dp),
            ) {
                if (search.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            when {
                search.tooShort -> Note(strings.placeSearchStart)
                search.failed -> Note(strings.placeSearchFailed)
                !search.loading && search.searchedFor == search.query.trim() && search.results.isEmpty() ->
                    Note(strings.placeNothingFound(search.query.trim()))

                else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    search.results.forEach { place -> PlaceResult(place, newsLanguage, onClick = { onPick(place) }) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlaceResult(place: Place, newsLanguage: String, onClick: () -> Unit) {
    val strings = discoverStrings()
    val colors = MaterialTheme.colorScheme
    val topic = remember(place) { Topics.localTopic(place) }
    val topicColors = rememberTopicColors(topic)
    val interaction = remember { MutableInteractionSource() }
    val lang = LocalAppLanguage.current
    // Which news the place brings: its country, read in the user's news language.
    val news = remember(place, lang, newsLanguage) {
        place.countryCode.takeIf { it.length == 2 }
            ?.let { NewsEdition(it.uppercase(), newsLanguage) }
            ?.let { strings.placeNews(displayLanguage(it, lang.locale), displayCountry(it, lang.locale)) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClickLabel = strings.addPlace, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .drawBehind { drawTopicCover(topicColors) },
            contentAlignment = Alignment.Center,
        ) {
            val flag = flagEmoji(place.countryCode)
            if (flag.isNotEmpty()) Emoji(flag, size = 22.sp) else TopicGlyph(topic, tint = Color.White, size = 22.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(place.region.ifBlank { null }, news).joinToString("\n")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Rounded.AddLocationAlt, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

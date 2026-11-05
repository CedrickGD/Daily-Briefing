package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.NewLabel
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicIcon
import com.cedrickgd.morgenbriefing.data.news.Region
import com.cedrickgd.morgenbriefing.data.news.Regions
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.regionStrings
import com.cedrickgd.morgenbriefing.i18n.settingsStrings
import com.cedrickgd.morgenbriefing.i18n.sourcesStrings
import com.cedrickgd.morgenbriefing.ui.components.Emoji
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import com.cedrickgd.morgenbriefing.ui.news.TopicBadge
import com.cedrickgd.morgenbriefing.ui.news.topicColors
import com.cedrickgd.morgenbriefing.ui.news.vector

/** 24-hour time picker for the notification time (dial or keyboard input) in a glass dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotifyTimeDialog(
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val strings = settingsStrings()
    val common = commonStrings()
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    var keyboard by remember { mutableStateOf(false) }
    val dark = isGlassDark()
    val dial = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val colors = TimePickerDefaults.colors(
        clockDialColor = dial,
        timeSelectorUnselectedContainerColor = dial,
        periodSelectorUnselectedContainerColor = dial,
    )

    GlassDialog(
        onDismissRequest = onDismiss,
        title = strings.timeDialogTitle,
        icon = Icons.Rounded.Schedule,
        buttons = {
            IconButton(onClick = { keyboard = !keyboard }) {
                Icon(
                    imageVector = if (keyboard) Icons.Rounded.Schedule else Icons.Rounded.Keyboard,
                    contentDescription = if (keyboard) strings.timeDialogDial else strings.timeDialogKeyboard,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            DialogButton(common.cancel, onDismiss)
            DialogButton(strings.apply, { onConfirm(state.hour, state.minute) })
        },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (keyboard) TimeInput(state = state, colors = colors) else TimePicker(state = state, colors = colors)
        }
    }
}

/**
 * Create or edit an own topic: a live preview badge, the name, a Material symbol (a horizontal
 * grid of every [TopicIcon]), one of [TopicSeeds] and what it reads – feeds the user adds next,
 * or a Google News search.
 *
 * @param topic the topic to edit, or null for a new one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomTopicDialog(
    topic: Topic?,
    onConfirm: (TopicDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = sourcesStrings()
    val common = commonStrings()
    val initial = remember(topic) { topic?.let(TopicDraft::of) ?: TopicDraft() }
    var title by rememberSaveable(topic?.id) { mutableStateOf(initial.title) }
    var iconKey by rememberSaveable(topic?.id) { mutableStateOf(initial.icon?.name) }
    var seed by rememberSaveable(topic?.id) { mutableLongStateOf(initial.seed) }
    var contentName by rememberSaveable(topic?.id) { mutableStateOf(initial.content.name) }
    var query by rememberSaveable(topic?.id) { mutableStateOf(initial.query) }
    val draft = initial.copy(
        title = title,
        icon = TopicIcon.fromKey(iconKey),
        seed = seed,
        content = TopicContent.valueOf(contentName),
        query = query,
    )
    val save = { if (draft.canSave) onConfirm(draft) }

    GlassDialog(
        onDismissRequest = onDismiss,
        title = if (topic == null) strings.newTopicTitle else strings.editTopicTitle,
        icon = Icons.Rounded.NewLabel,
        buttons = {
            DialogButton(common.cancel, onDismiss)
            DialogButton(if (topic == null) strings.create else common.save, save, enabled = draft.canSave)
        },
    ) {
        // Live preview: the badge the feed will show.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val preview = draft.toTopic(topic?.id ?: "preview").copy(title = draft.cleanTitle.ifEmpty { " " })
            TopicBadge(preview, diameter = 52.dp, size = 24.sp)
            Spacer(Modifier.width(14.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(TopicDraft.MAX_TITLE) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(strings.topicName) },
                placeholder = { Text(strings.topicNamePlaceholder) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )
        }

        Spacer(Modifier.height(18.dp))
        Overline(strings.topicContent)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopicContent.entries.forEach { content ->
                ChoicePill(
                    label = if (content == TopicContent.FEEDS) strings.contentFeeds else strings.contentSearch,
                    selected = draft.content == content,
                    icon = if (content == TopicContent.FEEDS) Icons.Rounded.RssFeed else Icons.Rounded.Search,
                    onClick = {
                        val next = TopicDraft.forContent(draft, content)
                        contentName = next.content.name
                        iconKey = next.icon?.name
                    },
                )
            }
        }
        AnimatedContent(
            targetState = draft.content,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "topicContent",
        ) { content ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = if (content == TopicContent.FEEDS) strings.contentFeedsHint else strings.contentSearchHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (content == TopicContent.SEARCH) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(MAX_QUERY) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        singleLine = true,
                        label = { Text(strings.query) },
                        placeholder = { Text(strings.queryPlaceholder) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Overline(strings.topicIcon)
        Spacer(Modifier.height(8.dp))
        IconPicker(
            selected = draft.icon,
            seed = seed,
            legacyEmoji = topic?.emoji?.takeIf { topic.icon == null && it.isNotBlank() },
            onSelect = { iconKey = it?.name },
        )

        Spacer(Modifier.height(18.dp))
        Overline(strings.topicColor)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            TopicSeeds.forEachIndexed { index, candidate ->
                ColorPearl(
                    color = Color(candidate),
                    selected = candidate == seed,
                    description = strings.colorDescription(index + 1, TopicSeeds.size),
                    onClick = { seed = candidate },
                )
            }
        }
    }
}

/**
 * Every [TopicIcon] as a tile in a three-row grid that scrolls sideways; the chosen one is
 * filled in the topic's color. An old own topic can keep its emoji ([legacyEmoji], first tile).
 */
@Composable
private fun IconPicker(selected: TopicIcon?, seed: Long, legacyEmoji: String?, onSelect: (TopicIcon?) -> Unit) {
    val strings = sourcesStrings()
    val dark = isGlassDark()
    val palette = remember(seed, dark) { topicColors(seed, dark) }
    val icons = remember { TopicIcon.entries.toList() }
    val choices: List<TopicIcon?> = remember(legacyEmoji) { if (legacyEmoji != null) listOf<TopicIcon?>(null) + icons else icons }
    LazyHorizontalGrid(
        rows = GridCells.Fixed(ICON_ROWS),
        modifier = Modifier
            .fillMaxWidth()
            .height(ICON_TILE * ICON_ROWS + ICON_GAP * (ICON_ROWS - 1))
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
        verticalArrangement = Arrangement.spacedBy(ICON_GAP),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        itemsIndexed(choices, key = { _, icon -> icon?.name ?: "emoji" }) { index, icon ->
            val isSelected = icon == selected
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.92f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                label = "iconScale",
            )
            Box(
                modifier = Modifier
                    .size(ICON_TILE)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(Brush.linearGradient(listOf(palette.coverStart, palette.coverEnd)))
                        } else {
                            Modifier.lamina(RoundedCornerShape(14.dp), strong = true)
                        },
                    )
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(icon) })
                    .semantics { contentDescription = if (icon == null) strings.keepEmoji else strings.iconDescription(index + 1, choices.size) },
                contentAlignment = Alignment.Center,
            ) {
                if (icon == null) {
                    Emoji(legacyEmoji.orEmpty(), size = 20.sp)
                } else {
                    Icon(
                        imageVector = icon.vector(),
                        contentDescription = null,
                        tint = if (isSelected) Color.White else palette.onContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

/** One color of the own-topic palette as a pearl; the chosen one carries a check. */
@Composable
private fun ColorPearl(color: Color, selected: Boolean, description: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "pearlScale",
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.3f), color)))
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.5f), CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/**
 * "Quelle hinzufügen" to [topic]: paste a feed URL or just the newsroom's website — the app
 * looks for the feed and shows what it found before saving.
 */
@Composable
internal fun AddFeedDialog(
    topic: Topic,
    state: AddFeedState,
    onLookUp: (String) -> Unit,
    onSave: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = sourcesStrings()
    val common = commonStrings()
    var input by rememberSaveable(topic.id) { mutableStateOf("") }
    var name by rememberSaveable(topic.id) { mutableStateOf("") }
    val found = state as? AddFeedState.Found
    LaunchedEffect(found) { if (found != null && name.isBlank()) name = found.title }
    LaunchedEffect(state) { if (state == AddFeedState.Saved) onDismiss() }
    val checking = state == AddFeedState.Checking

    GlassDialog(
        onDismissRequest = onDismiss,
        title = strings.addFeedTitle(topic.title),
        icon = Icons.Rounded.AddLink,
        dismissOnClickOutside = !checking,
        buttons = {
            DialogButton(common.cancel, onDismiss)
            if (found != null) {
                DialogButton(strings.add, { onSave(name) })
            } else {
                DialogButton(if (checking) strings.searching else strings.findFeed, { onLookUp(input) }, enabled = input.isNotBlank() && !checking)
            }
        },
    ) {
        Text(
            text = strings.addFeedIntro,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(14.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !checking && found == null,
            label = { Text(strings.address) },
            placeholder = { Text(strings.addressPlaceholder) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (input.isNotBlank()) onLookUp(input) }),
        )
        Spacer(Modifier.size(10.dp))
        when (state) {
            AddFeedState.Checking -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(strings.lookingUp, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is AddFeedState.Failed -> StateLine(
                icon = Icons.Rounded.ErrorOutline,
                text = strings.feedError(state.error, state.httpCode),
                tint = MaterialTheme.colorScheme.error,
            )

            is AddFeedState.Found -> {
                StateLine(icon = Icons.Rounded.CheckCircle, text = strings.found(state.title, state.itemCount), tint = StatusPalette.ok())
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(TopicDraft.MAX_TITLE) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.sourceName) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(name) }),
                )
            }

            AddFeedState.Idle, AddFeedState.Saved -> Unit
        }
    }
}

/** An icon and a polite live-region sentence (found / failed). */
@Composable
private fun StateLine(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/** Pick the Bundesland behind the "Region" topic. */
@Composable
internal fun RegionPickerDialog(current: String, onPick: (Region) -> Unit, onDismiss: () -> Unit) {
    val strings = regionStrings()
    GlassDialog(
        onDismissRequest = onDismiss,
        title = strings.regionDialogTitle,
        icon = Icons.Rounded.Map,
        buttons = { DialogButton(commonStrings().done, onDismiss) },
    ) {
        Text(
            text = strings.regionDialogText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(Modifier.selectableGroup()) {
            Regions.ALL.forEach { region ->
                val selected = region.key == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(region) })
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(region.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = region.feeds.joinToString(" · ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Confirmation before deleting an own topic with its sources. */
@Composable
internal fun DeleteTopicDialog(topic: Topic, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val strings = sourcesStrings()
    val common = commonStrings()
    GlassDialog(
        onDismissRequest = onDismiss,
        title = strings.deleteTitle(topic.title),
        buttons = {
            DialogButton(common.cancel, onDismiss)
            DialogButton(common.delete, onConfirm, color = MaterialTheme.colorScheme.error)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TopicBadge(topic, diameter = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(strings.deleteText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A row that opens [onClick]: label, value and the action word ("Ändern"). */
@Composable
internal fun ValueRow(label: String, value: String, actionLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private const val ICON_ROWS = 3
private val ICON_TILE = 48.dp
private val ICON_GAP = 8.dp
private const val MAX_QUERY = 120

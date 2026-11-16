package com.cedrickgd.morgenbriefing.ui.history

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cedrickgd.morgenbriefing.data.BERLIN
import com.cedrickgd.morgenbriefing.data.local.HistoryEntry
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.domain.ItemEdition
import com.cedrickgd.morgenbriefing.i18n.HistoryStrings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.i18n.historyStrings
import com.cedrickgd.morgenbriefing.ui.components.GlassIconButton
import com.cedrickgd.morgenbriefing.ui.components.GlassSnackbarHost
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.discover.AccentIconTile
import com.cedrickgd.morgenbriefing.ui.discover.PushedBarHeight
import com.cedrickgd.morgenbriefing.ui.discover.PushedTopBar
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import com.cedrickgd.morgenbriefing.ui.glass.GlassLevel
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.SkyTextInset
import com.cedrickgd.morgenbriefing.ui.glass.glass
import com.cedrickgd.morgenbriefing.ui.glass.glassContent
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import com.cedrickgd.morgenbriefing.ui.news.ArticleVisual
import com.cedrickgd.morgenbriefing.ui.news.CardHairline
import com.cedrickgd.morgenbriefing.ui.news.CardPiece
import com.cedrickgd.morgenbriefing.ui.news.FeedGutter
import com.cedrickgd.morgenbriefing.ui.news.cardPart
import com.cedrickgd.morgenbriefing.ui.news.copyLink
import com.cedrickgd.morgenbriefing.ui.news.say
import com.cedrickgd.morgenbriefing.ui.settings.DialogButton
import com.cedrickgd.morgenbriefing.ui.settings.GlassDialog
import com.cedrickgd.morgenbriefing.ui.shell.reportChromeScroll
import com.cedrickgd.morgenbriefing.ui.theme.OverlineLetterSpacing
import com.cedrickgd.morgenbriefing.ui.theme.Reading
import com.cedrickgd.morgenbriefing.util.horizontalSafeDrawing
import com.cedrickgd.morgenbriefing.util.pageSideInset
import com.cedrickgd.morgenbriefing.util.rememberLinkActions
import com.cedrickgd.morgenbriefing.util.statusBarTop

/** Scrolled this far, the page title has gone under the bar: the bar turns solid and shows it. */
private val SolidBarAfter = 72.dp

/** Thumbnail of an archive row. */
private val RowPictureSize = 64.dp

/**
 * "Gelesen & ausgeblendet" (docs/DESIGN.md §7), glass over the living sky like "Themen
 * entdecken": the page title with two counters (read, hidden) on a glass card, a frosted search
 * field (title or source) and filter pills (Alle · Gelesen · Ausgeblendet), then the stories by
 * day ("Heute", "Gestern", "Dienstag · 6. Oktober") as glass cards – picture, serif title, a
 * status mark, source and time. A tap opens the story ([onOpenArticle]: the reader, or the
 * browser – the link is all it needs, the feed may have dropped the story long ago); ⋮ shares it,
 * copies the link, opens it in the browser, shows a hidden story again or counts a read one as
 * unread (each undone from the snackbar). "Verlauf löschen" (top right) asks first. Empty: a
 * friendly note on what lands here.
 *
 * @param contentPadding bottom room (navigation bar, mini player).
 */
@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = historyStrings()
    val common = commonStrings()
    val feed = feedStrings()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val links = rememberLinkActions()
    val side = FeedGutter + pageSideInset()
    val bottom = contentPadding.calculateBottomPadding()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val rowActions = HistoryRowActions(
        onOpen = { entry -> onOpenArticle(entry.toItem()) },
        onShare = { entry -> links.shareArticle(entry.toItem()) },
        onCopyLink = { entry ->
            val copied = copyLink(context, entry.title.ifBlank { feed.clipLabel }, entry.url)
            // Android 13+ confirms copying itself.
            if (!copied) snackbar.say(scope, feed.copyFailed) else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbar.say(scope, feed.linkCopied)
        },
        onOpenInBrowser = { entry ->
            links.open(entry.url)
        },
        onShowAgain = { entry ->
            viewModel.showAgain(entry)
            snackbar.say(scope, strings.shownAgain, action = feed.undo) { viewModel.restore(entry) }
        },
        onMarkUnread = { entry ->
            viewModel.markUnread(entry)
            snackbar.say(scope, strings.markedUnread, action = feed.undo) { viewModel.restore(entry) }
        },
    )

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .reportChromeScroll()
                .glassContent()
                .horizontalSafeDrawing(),
            contentPadding = PaddingValues(start = side, end = side, top = statusBarTop() + PushedBarHeight, bottom = bottom + 32.dp),
        ) {
            item(key = "head", contentType = "head") {
                HistoryHeader(state, strings, Modifier.padding(bottom = 16.dp))
            }
            if (!state.isEmpty) {
                item(key = "search", contentType = "search") {
                    HistorySearchField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.setQuery(it)
                        },
                        strings = strings,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                item(key = "filters", contentType = "filters") {
                    FilterPills(state, strings, onFilter = viewModel::setFilter, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
            when {
                !state.loaded -> Unit
                state.isEmpty -> item(key = "empty", contentType = "message") {
                    EmptyNote(icon = Icons.Rounded.History, title = strings.emptyTitle, text = strings.emptyText)
                }
                state.days.isEmpty() -> item(key = "no-match", contentType = "message") {
                    if (state.query.isNotBlank()) {
                        EmptyNote(icon = Icons.Rounded.SearchOff, title = strings.noMatch(state.query.trim()), text = strings.noMatchText)
                    } else {
                        EmptyNote(icon = Icons.Rounded.History, title = strings.noneInFilter, text = strings.emptyText)
                    }
                }
                else -> dayItems(state, strings, rowActions)
            }
        }

        PushedTopBar(
            title = strings.title,
            solid = rememberSolidBar(listState),
            backLabel = common.back,
            onBack = onBack,
            actions = {
                AnimatedVisibility(visible = !state.isEmpty, enter = fadeIn(), exit = fadeOut()) {
                    GlassIconButton(Icons.Rounded.DeleteSweep, contentDescription = strings.clearHistory, onClick = { confirmClear = true })
                }
            },
        )

        GlassSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .horizontalSafeDrawing()
                .imePadding()
                .padding(bottom = bottom + 12.dp),
        )
    }

    if (confirmClear) {
        val focus = LocalFocusManager.current
        GlassDialog(
            onDismissRequest = { confirmClear = false },
            title = strings.clearTitle,
            icon = Icons.Rounded.DeleteSweep,
            buttons = {
                DialogButton(common.cancel, { confirmClear = false })
                DialogButton(
                    strings.clearHistory,
                    {
                        confirmClear = false
                        focus.clearFocus()
                        viewModel.clear()
                        query = ""
                        viewModel.setQuery("")
                        snackbar.say(scope, strings.cleared)
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            },
        ) {
            Text(strings.clearText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** What a row of the archive can do. */
private class HistoryRowActions(
    val onOpen: (HistoryEntry) -> Unit,
    val onShare: (HistoryEntry) -> Unit,
    val onCopyLink: (HistoryEntry) -> Unit,
    val onOpenInBrowser: (HistoryEntry) -> Unit,
    val onShowAgain: (HistoryEntry) -> Unit,
    val onMarkUnread: (HistoryEntry) -> Unit,
)

@Composable
private fun rememberSolidBar(listState: LazyListState): Boolean {
    val thresholdPx = with(LocalDensity.current) { SolidBarAfter.toPx() }
    val solid by remember(listState, thresholdPx) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > thresholdPx }
    }
    return solid
}

/**
 * Overline and serif title on the sky (on the cards' text line), then a glass card with the two
 * counters: how many stories were read, how many hidden.
 */
@Composable
private fun HistoryHeader(state: HistoryUiState, strings: HistoryStrings, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = SkyTextInset)) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.overline.uppercase(LocalAppLanguage.current.locale),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                color = colors.onSurfaceVariant,
            )
            Text(
                text = strings.title,
                style = MaterialTheme.typography.displaySmall,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(16.dp))
        GlassCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Counter(Icons.Rounded.Visibility, state.readCount, strings.filterRead, colors.primary, Modifier.weight(1f))
                Box(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .size(width = 1.dp, height = 40.dp)
                        .background(colors.onSurface.copy(alpha = 0.10f)),
                )
                Counter(Icons.Rounded.VisibilityOff, state.hiddenCount, strings.filterHidden, colors.tertiary, Modifier.weight(1f))
            }
        }
    }
}

/** An icon tile in [tint], a large serif number and its label. */
@Composable
private fun Counter(icon: ImageVector, count: Int, label: String, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AccentIconTile(icon, size = 44.dp, tint = tint)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = MaterialTheme.typography.displaySmall.fontFamily),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A frosted field: 🔍, the text (filters as it is typed), ✕ to clear. */
@Composable
private fun HistorySearchField(value: String, onValueChange: (String) -> Unit, strings: HistoryStrings, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .glass(shape = shape, level = GlassLevel.Thin)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = strings.searchPlaceholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = strings.searchPlaceholder },
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = strings.clearSearch, tint = colors.onSurfaceVariant)
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

/** "Alle · Gelesen · Ausgeblendet": the selected one filled in the accent color. */
@Composable
private fun FilterPills(state: HistoryUiState, strings: HistoryStrings, onFilter: (HistoryFilter) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(
        Triple(HistoryFilter.ALL, strings.filterAll, null),
        Triple(HistoryFilter.READ, strings.filterRead, Icons.Rounded.Visibility),
        Triple(HistoryFilter.HIDDEN, strings.filterHidden, Icons.Rounded.VisibilityOff),
    )
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.first.name }) { (filter, label, icon) ->
            FilterPill(label = label, icon = icon, selected = state.filter == filter, onClick = { onFilter(filter) })
        }
    }
}

@Composable
private fun FilterPill(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimary else colors.onSurface
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Tab, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = 38.dp)
                .pressScale(interaction, pressedScale = 0.94f)
                .then(if (selected) Modifier.background(colors.primary, CircleShape) else Modifier.glass(shape = GlassShapes.pill, level = GlassLevel.Thin))
                .padding(start = if (icon != null) 12.dp else 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

/** Every day: its name as an overline on the sky, then its stories as one glass card. */
private fun LazyListScope.dayItems(state: HistoryUiState, strings: HistoryStrings, actions: HistoryRowActions) {
    for (day in state.days) {
        item(key = "day-${day.date}", contentType = "day") {
            val language = LocalAppLanguage.current
            DayHeading(dayLabel(day.date, state.today, language).uppercase(language.locale), Modifier.animateItem())
        }
        day.entries.forEachIndexed { index, entry ->
            item(key = "e-${entry.key}", contentType = "entry") {
                val topic = state.topics[entry.topicId] ?: Topics.resolveOrNeutral(entry.topicId, AppSettings())
                CardPiece(cardPart(index, day.entries.size), Modifier.animateItem().padding(horizontal = FeedGutter)) {
                    HistoryRow(
                        entry = entry,
                        topic = topic,
                        showImage = state.showImages,
                        strings = strings,
                        actions = actions,
                        divider = index < day.entries.size - 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeading(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = SkyTextInset, top = 14.dp, bottom = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = OverlineLetterSpacing),
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * One story of the archive: its picture (or generated cover), the serif title, a small status
 * mark ("Gelesen" / "Ausgeblendet"), source and time, and ⋮. A tap opens it, a long press the menu.
 */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    topic: Topic,
    showImage: Boolean,
    strings: HistoryStrings,
    actions: HistoryRowActions,
    divider: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val feed = feedStrings()
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    var menuOpen by remember { mutableStateOf(false) }
    val item = remember(entry) { entry.toItem() }
    val textLanguage = remember(item.sourceId) { ItemEdition.languageOf(item) }
    val time = remember(entry.touchedAt) { timeOf(entry, BERLIN) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressScale(interaction, pressedScale = 0.985f)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = feed.openArticle,
                    onLongClickLabel = feed.moreOptions,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                    onClick = { actions.onOpen(entry) },
                )
                .padding(top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(RowPictureSize)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                ArticleVisual(item = item, topic = topic, modifier = Modifier.matchParentSize(), showImage = showImage, showCoverBadge = item.image == null)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title.ifBlank { entry.domain.ifBlank { entry.url } },
                    style = Reading.storyRow(language = textLanguage),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (entry.hidden) {
                            StatusMark(Icons.Rounded.VisibilityOff, strings.statusHidden, colors.tertiary)
                        } else {
                            StatusMark(Icons.Rounded.Visibility, strings.statusRead, colors.primary)
                        }
                        Spacer(Modifier.width(8.dp))
                        val source = entry.source.ifBlank { entry.domain }
                        if (source.isNotBlank()) {
                            Text(
                                text = source,
                                style = Reading.storyMeta(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        Text(text = if (source.isNotBlank()) " · $time" else time, style = Reading.storyMeta(), maxLines = 1, softWrap = false)
                    }
                    Box(Modifier.padding(start = 2.dp)) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = feed.moreOptions, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        RowMenu(entry, menuOpen, onDismiss = { menuOpen = false }, strings = strings, actions = actions)
                    }
                }
            }
        }
        if (divider) CardHairline()
    }
}

/** A small tinted label: "👁 Gelesen" or "Ausgeblendet". */
@Composable
private fun StatusMark(icon: ImageVector, label: String, tint: Color) {
    val dark = isGlassDark()
    Row(
        Modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = if (dark) 0.22f else 0.12f))
            .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = tint, maxLines = 1)
    }
}

@Composable
private fun RowMenu(entry: HistoryEntry, open: Boolean, onDismiss: () -> Unit, strings: HistoryStrings, actions: HistoryRowActions) {
    val colors = MaterialTheme.colorScheme
    val common = commonStrings()
    DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = colors.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        MenuEntry(common.share, Icons.Rounded.Share) { onDismiss(); actions.onShare(entry) }
        MenuEntry(common.copyLink, Icons.Rounded.ContentCopy) { onDismiss(); actions.onCopyLink(entry) }
        MenuEntry(common.openInBrowser, Icons.AutoMirrored.Rounded.OpenInNew) { onDismiss(); actions.onOpenInBrowser(entry) }
        if (entry.hidden) {
            MenuEntry(strings.showAgain, Icons.Rounded.Visibility, tint = colors.primary) { onDismiss(); actions.onShowAgain(entry) }
        }
        if (entry.read) {
            MenuEntry(strings.markUnread, Icons.Rounded.Restore) { onDismiss(); actions.onMarkUnread(entry) }
        }
    }
}

@Composable
private fun MenuEntry(label: String, icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/** A calm note when nothing is listed: an icon in a soft accent glow, a serif title and one line. */
@Composable
private fun EmptyNote(icon: ImageVector, title: String, text: String) {
    val colors = MaterialTheme.colorScheme
    val dark = isGlassDark()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SkyTextInset, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(colors.primary.copy(alpha = if (dark) 0.34f else 0.22f), colors.tertiary.copy(alpha = if (dark) 0.10f else 0.06f)),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = if (dark) 0.10f else 0.45f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MaterialTheme.typography.displaySmall.fontFamily),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

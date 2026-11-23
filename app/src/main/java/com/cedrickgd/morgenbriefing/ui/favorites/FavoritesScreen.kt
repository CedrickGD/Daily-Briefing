package com.cedrickgd.morgenbriefing.ui.favorites

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cedrickgd.morgenbriefing.data.local.HistoryEntry
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.FavoritesStrings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.favoritesStrings
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.i18n.historyStrings
import com.cedrickgd.morgenbriefing.ui.components.GlassSnackbarHost
import com.cedrickgd.morgenbriefing.ui.discover.PushedBarHeight
import com.cedrickgd.morgenbriefing.ui.discover.PushedTopBar
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import com.cedrickgd.morgenbriefing.ui.glass.SkyTextInset
import com.cedrickgd.morgenbriefing.ui.glass.glassContent
import com.cedrickgd.morgenbriefing.ui.history.ArchiveRow
import com.cedrickgd.morgenbriefing.ui.history.ArchiveSearchField
import com.cedrickgd.morgenbriefing.ui.history.Counter
import com.cedrickgd.morgenbriefing.ui.history.DayHeading
import com.cedrickgd.morgenbriefing.ui.history.EmptyNote
import com.cedrickgd.morgenbriefing.ui.history.MenuEntry
import com.cedrickgd.morgenbriefing.ui.history.dayLabel
import com.cedrickgd.morgenbriefing.ui.history.rememberSolidBar
import com.cedrickgd.morgenbriefing.ui.news.CardPiece
import com.cedrickgd.morgenbriefing.ui.news.FeedGutter
import com.cedrickgd.morgenbriefing.ui.news.cardPart
import com.cedrickgd.morgenbriefing.ui.news.copyLink
import com.cedrickgd.morgenbriefing.ui.news.say
import com.cedrickgd.morgenbriefing.ui.shell.reportChromeScroll
import com.cedrickgd.morgenbriefing.util.horizontalSafeDrawing
import com.cedrickgd.morgenbriefing.util.pageSideInset
import com.cedrickgd.morgenbriefing.util.rememberLinkActions
import com.cedrickgd.morgenbriefing.util.statusBarTop

/** What a row of the list can do. */
private class FavoriteRowActions(
    val onOpen: (HistoryEntry) -> Unit,
    val onShare: (HistoryEntry) -> Unit,
    val onCopyLink: (HistoryEntry) -> Unit,
    val onOpenInBrowser: (HistoryEntry) -> Unit,
    val onRemove: (HistoryEntry) -> Unit,
)

/**
 * "Favoriten", glass over the living sky like "Gelesen & ausgeblendet" and built from its parts:
 * the page title with the number of starred stories on a glass card, a frosted search field
 * (title or source), then the stories by the day they were starred. A tap opens the story
 * ([onOpenArticle]: the reader, or the browser – the feed may have dropped it long ago); ⋮ shares
 * it, copies the link, opens it in the browser or takes its star off (undone from the snackbar).
 * Empty: how a story gets here.
 *
 * @param contentPadding bottom room (navigation bar, mini player).
 */
@Composable
fun FavoritesScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = favoritesStrings()
    val history = historyStrings()
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

    val rowActions = FavoriteRowActions(
        onOpen = { entry -> onOpenArticle(entry.toItem()) },
        onShare = { entry -> links.shareArticle(entry.toItem()) },
        onCopyLink = { entry ->
            val copied = copyLink(context, entry.title.ifBlank { feed.clipLabel }, entry.url)
            // Android 13+ confirms copying itself.
            if (!copied) snackbar.say(scope, feed.copyFailed) else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbar.say(scope, feed.linkCopied)
        },
        onOpenInBrowser = { entry -> links.open(entry.url) },
        onRemove = { entry ->
            viewModel.remove(entry)
            snackbar.say(scope, strings.removed, action = feed.undo) { viewModel.restore(entry) }
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
                FavoritesHeader(state, strings, Modifier.padding(bottom = 16.dp))
            }
            if (!state.isEmpty) {
                item(key = "search", contentType = "search") {
                    ArchiveSearchField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.setQuery(it)
                        },
                        placeholder = history.searchPlaceholder,
                        clearLabel = history.clearSearch,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            when {
                !state.loaded -> Unit
                state.isEmpty -> item(key = "empty", contentType = "message") {
                    EmptyNote(icon = Icons.Rounded.StarBorder, title = strings.emptyTitle, text = strings.emptyText)
                }
                state.days.isEmpty() -> item(key = "no-match", contentType = "message") {
                    EmptyNote(icon = Icons.Rounded.SearchOff, title = history.noMatch(state.query.trim()), text = history.noMatchText)
                }
                else -> dayItems(state, strings, rowActions)
            }
        }

        PushedTopBar(
            title = strings.title,
            solid = rememberSolidBar(listState),
            backLabel = common.back,
            onBack = onBack,
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
}

/** Overline and serif title on the sky (on the cards' text line), then the count on a glass card. */
@Composable
private fun FavoritesHeader(state: FavoritesUiState, strings: FavoritesStrings, modifier: Modifier = Modifier) {
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
        if (state.loaded && !state.isEmpty) {
            Spacer(Modifier.height(16.dp))
            GlassCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Counter(Icons.Rounded.Star, state.count, strings.saved(state.count), colors.primary, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Every day: its name as an overline on the sky, then its stories as one glass card. */
private fun LazyListScope.dayItems(state: FavoritesUiState, strings: FavoritesStrings, actions: FavoriteRowActions) {
    for (day in state.days) {
        item(key = "day-${day.date}", contentType = "day") {
            val language = LocalAppLanguage.current
            DayHeading(dayLabel(day.date, state.today, language).uppercase(language.locale), Modifier.animateItem())
        }
        day.entries.forEachIndexed { index, entry ->
            item(key = "f-${entry.key}", contentType = "entry") {
                val topic = state.topics[entry.topicId] ?: Topics.resolveOrNeutral(entry.topicId, AppSettings())
                CardPiece(cardPart(index, day.entries.size), Modifier.animateItem().padding(horizontal = FeedGutter)) {
                    ArchiveRow(
                        entry = entry,
                        topic = topic,
                        showImage = state.showImages,
                        divider = index < day.entries.size - 1,
                        onOpen = { actions.onOpen(entry) },
                        mark = null,
                        menu = { open, onDismiss -> FavoriteMenu(entry, open, onDismiss, strings, actions) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteMenu(entry: HistoryEntry, open: Boolean, onDismiss: () -> Unit, strings: FavoritesStrings, actions: FavoriteRowActions) {
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
        MenuEntry(strings.removeFavorite, Icons.Rounded.StarBorder, tint = colors.primary) { onDismiss(); actions.onRemove(entry) }
    }
}

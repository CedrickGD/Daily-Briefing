package com.cedrickgd.morgenbriefing.ui.search

import android.os.Build
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.SearchStrings
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.searchStrings
import com.cedrickgd.morgenbriefing.listen
import com.cedrickgd.morgenbriefing.ui.components.GlassIconButton
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.components.GlassSnackbarHost
import com.cedrickgd.morgenbriefing.ui.components.LoadingSkeleton
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.discover.AccentIconTile
import com.cedrickgd.morgenbriefing.ui.discover.HeadlineRow
import com.cedrickgd.morgenbriefing.ui.discover.rememberNow
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import com.cedrickgd.morgenbriefing.ui.glass.GlassLevel
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.blockTouchesBehind
import com.cedrickgd.morgenbriefing.ui.glass.glass
import com.cedrickgd.morgenbriefing.ui.glass.glassContent
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import com.cedrickgd.morgenbriefing.ui.nav.SearchRoute
import com.cedrickgd.morgenbriefing.ui.news.FeedActions
import com.cedrickgd.morgenbriefing.ui.news.FeedEntry
import com.cedrickgd.morgenbriefing.ui.news.copyLink
import com.cedrickgd.morgenbriefing.ui.shell.reportChromeScroll
import com.cedrickgd.morgenbriefing.util.horizontalSafeDrawing
import com.cedrickgd.morgenbriefing.util.pageSideInset
import com.cedrickgd.morgenbriefing.util.rememberLinkActions
import com.cedrickgd.morgenbriefing.util.statusBarTop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

private const val TAG = "SearchScreen"

/** Height of the floating search bar under the status bar. */
private val SearchBarHeight = 72.dp

/** Results this close to the end of the list load the next page. */
private const val LOAD_AHEAD = 4

/**
 * The news search, glass over the living sky: a floating frosted search field (focused at once,
 * the keyboard's search key searches, ✕ clears) with the back button; below it, before a
 * search, the recent searches as chips and "Gerade wichtig" – the edition's top stories. Typing
 * searches after a short pause; results are the feed's own entries, endless into the past,
 * prefetched for the reader as they come on screen. "Diesem Thema folgen" turns the query into a
 * topic of the feed.
 *
 * @param contentPadding bottom room (navigation bar, mini player).
 */
@Composable
fun SearchScreen(
    route: SearchRoute,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    onOpenDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(route.query))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val strings = searchStrings()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val now = rememberNow()
    val side = 16.dp + pageSideInset()
    val bottom = contentPadding.calculateBottomPadding()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // The field keeps its own selection; the view model gets the text.
    val initial = viewModel.query.value
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        // Opened empty: straight to typing. With a query the results come first.
        if (initial.isBlank()) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    val latestStrings by rememberUpdatedState(strings)
    LaunchedEffect(viewModel, snackbar) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is SearchEvent.Followed -> latestStrings.followed(event.query)
                is SearchEvent.Unfollowed -> latestStrings.unfollowed(event.query)
                SearchEvent.SaveFailed -> latestStrings.saveFailed
            }
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(text) }
        }
    }
    val scope = rememberCoroutineScope()
    val message: (String) -> Unit = { text ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    val results = state.results
    // A new query starts at the top.
    LaunchedEffect(results.query) { if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0) }
    // Scrolling the results by hand puts the keyboard away.
    val hideKeyboardOnDrag = remember(keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) keyboard?.hide()
                return Offset.Zero
            }
        }
    }
    // Endless: the next page loads as the end comes near – again after every page that still
    // leaves the end in sight (each page ends its loading, which makes the trigger true again).
    val latestResults by rememberUpdatedState(results)
    LaunchedEffect(listState, viewModel) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            SearchPaging.shouldLoadMore(latestResults, nearEnd = last >= info.totalItemsCount - 1 - LOAD_AHEAD)
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { viewModel.loadMore() }
    }
    // The visible results are warmed for the reader (and get their pictures).
    val latestArticles by rememberUpdatedState(state.articles)
    LaunchedEffect(listState, viewModel) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                (info.key as? String)?.takeIf { it.startsWith(RESULT_PREFIX) }?.removePrefix(RESULT_PREFIX)
            }
        }
            .distinctUntilChanged()
            .collect { ids ->
                val byId = latestArticles.associateBy { it.item.id }
                viewModel.prefetch(ids.mapNotNull { byId[it]?.item })
            }
    }

    val open: (NewsItem) -> Unit = { item ->
        keyboard?.hide()
        viewModel.onOpened()
        onOpenArticle(item)
    }
    val actions = rememberSearchFeedActions(open, onMessage = message)
    val context = LocalContext.current
    val listenState by remember(context) { context.listen.state }.collectAsStateWithLifecycle()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .nestedScroll(hideKeyboardOnDrag)
                .reportChromeScroll()
                .glassContent()
                .horizontalSafeDrawing(),
            contentPadding = PaddingValues(start = side, end = side, top = statusBarTop() + SearchBarHeight + 8.dp, bottom = bottom + 32.dp),
        ) {
            when (results.phase) {
                SearchPhase.IDLE -> suggestions(
                    state = state,
                    strings = strings,
                    now = now,
                    onSearch = { query ->
                        field = TextFieldValue(query, TextRange(query.length))
                        focusManager.clearFocus()
                        viewModel.submit(query)
                    },
                    onRemoveRecent = viewModel::removeRecent,
                    onClearRecent = viewModel::clearRecent,
                    onOpenArticle = open,
                )

                SearchPhase.LOADING -> {
                    item(key = "searching", contentType = "header") { Searching(strings.searching(results.query)) }
                    items(count = 3, key = { "skeleton-$it" }, contentType = { "skeleton" }) { ResultSkeleton() }
                }

                SearchPhase.FAILED -> item(key = "failed", contentType = "message") {
                    Message(
                        icon = Icons.Rounded.CloudOff,
                        title = strings.failedTitle,
                        body = strings.failedBody,
                        action = commonStrings().retry,
                        actionIcon = Icons.Rounded.Refresh,
                        onAction = viewModel::retry,
                    )
                }

                SearchPhase.READY -> {
                    item(key = "header", contentType = "header") {
                        ResultsHeader(
                            strings = strings,
                            count = results.items.size,
                            followed = state.followedTopicId != null,
                            onToggleFollow = viewModel::toggleFollow,
                        )
                    }
                    if (results.offline) {
                        item(key = "offline", contentType = "banner") { OfflineBanner(strings.offline) }
                    }
                    if (state.articles.isEmpty()) {
                        item(key = "empty", contentType = "message") {
                            Message(
                                icon = Icons.Rounded.SearchOff,
                                title = strings.emptyTitle(results.query),
                                body = strings.emptyBody,
                                action = strings.discover,
                                actionIcon = Icons.Rounded.Explore,
                                onAction = onOpenDiscover,
                            )
                        }
                    }
                    itemsIndexed(state.articles, key = { _, article -> RESULT_PREFIX + article.item.id }, contentType = { _, _ -> "result" }) { _, article ->
                        val key = article.item.key
                        FeedEntry(
                            article = article,
                            now = now,
                            listening = listenState.articleKey == key && listenState.speaking,
                            paused = listenState.articleKey == key && listenState.paused,
                            actions = actions,
                            // Stories from the phone's own topics say which one; Google News results all belong to the query.
                            showTopic = article.topic.id != Topics.SEARCH_ID,
                            showImage = state.showImages,
                            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                        )
                    }
                    item(key = "footer", contentType = "footer") {
                        ResultsFooter(results = results, strings = strings, onRetry = viewModel::retry)
                    }
                }
            }
        }

        SearchBar(
            value = field,
            onValueChange = { value ->
                field = value
                viewModel.onQueryChange(value.text)
            },
            onSearch = {
                keyboard?.hide()
                focusManager.clearFocus()
                viewModel.submit(field.text)
            },
            onClear = {
                field = TextFieldValue("")
                viewModel.onQueryChange("")
                runCatching { focusRequester.requestFocus() }
                keyboard?.show()
            },
            onBack = onBack,
            focusRequester = focusRequester,
            side = side - 4.dp,
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

private const val RESULT_PREFIX = "r:"

/** The feed entry's actions for search results: open in the reader, listen, share, copy. */
@Composable
private fun rememberSearchFeedActions(onOpen: (NewsItem) -> Unit, onMessage: (String) -> Unit): FeedActions {
    val context = LocalContext.current
    val links = rememberLinkActions()
    val strings = searchStrings()
    val latestOpen by rememberUpdatedState(onOpen)
    val latestMessage by rememberUpdatedState(onMessage)
    val latestStrings by rememberUpdatedState(strings)
    return remember(context, links) {
        val listen = context.listen
        FeedActions(
            onOpen = { latestOpen(it.item) },
            onOpenExternal = { links.open(it.item.url) },
            onListen = { article ->
                try {
                    listen.play(article.item)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start reading aloud", e)
                    latestMessage(latestStrings.listenFailed)
                }
            },
            onStopListening = {
                try {
                    listen.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not stop reading aloud", e)
                }
            },
            onShare = { links.shareArticle(it.item) },
            onCopyLink = { article ->
                val copied = copyLink(context, article.item.headline, article.item.url)
                // Android 13+ confirms copying itself.
                if (!copied) {
                    latestMessage(latestStrings.copyFailed)
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    latestMessage(latestStrings.linkCopied)
                }
            },
        )
    }
}

/** The floating frosted search field with the back button. */
@Composable
private fun SearchBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
    side: Dp,
) {
    val strings = searchStrings()
    val common = commonStrings()
    val colors = MaterialTheme.colorScheme
    val statusTop = statusBarTop()
    Box(
        Modifier
            .fillMaxWidth()
            .height(statusTop + SearchBarHeight)
            .glass(shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp), level = GlassLevel.Thick, floating = true)
            .blockTouchesBehind(),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalSafeDrawing()
            .padding(top = statusTop + 10.dp, start = side.coerceAtLeast(12.dp), end = side.coerceAtLeast(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = common.back, onClick = onBack)
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clip(CircleShape)
                .background(colors.surface.copy(alpha = if (isGlassDark()) 0.16f else 0.62f))
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) {
                    Text(strings.fieldHint, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = strings.fieldHint },
                )
            }
            AnimatedVisibility(visible = value.text.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = strings.clear, tint = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Before a search: a hint, the recent searches and the top stories of the edition. */
private fun androidx.compose.foundation.lazy.LazyListScope.suggestions(
    state: SearchUiState,
    strings: SearchStrings,
    now: ZonedDateTime,
    onSearch: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
) {
    item(key = "hint", contentType = "hint") {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(Icons.Rounded.TravelExplore, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(strings.hintTitle, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
                Text(strings.hintBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (state.recent.isNotEmpty()) {
        item(key = "recent", contentType = "recent") {
            RecentSearches(state.recent, strings, onSearch, onRemoveRecent, onClearRecent)
        }
    }
    item(key = "trending", contentType = "trending") {
        GlassCard(
            modifier = Modifier.padding(top = 20.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Whatshot, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Overline(strings.trendingOverline)
                    Text(strings.trendingTitle, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() })
                }
            }
            Spacer(Modifier.height(6.dp))
            when {
                state.trending.isNotEmpty() -> state.trending.forEachIndexed { index, item ->
                    HeadlineRow(item = item, accent = MaterialTheme.colorScheme.primary, now = now, onOpen = onOpenArticle, rank = index + 1)
                }

                state.trendingLoading -> Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(TRENDING_SIZE) { LoadingSkeleton(Modifier.fillMaxWidth(if (it % 2 == 0) 0.92f else 0.74f).height(18.dp)) }
                }

                else -> Text(
                    strings.trendingEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentSearches(
    recent: List<String>,
    strings: SearchStrings,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.padding(top = 16.dp)) {
        Row(Modifier.padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Overline(strings.recentOverline, Modifier.weight(1f))
            Text(
                text = strings.clearRecent,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClear)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recent.forEach { query -> RecentChip(query, strings, onSearch, onRemove) }
        }
    }
}

@Composable
private fun RecentChip(query: String, strings: SearchStrings, onSearch: (String) -> Unit, onRemove: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .pressScale(interaction, pressedScale = 0.95f)
            .glass(shape = GlassShapes.pill, level = GlassLevel.Thin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClickLabel = strings.searchAgain, onClick = { onSearch(query) })
                .heightIn(min = 48.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(query, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { onRemove(query) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = strings.removeRecent(query), tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(LocalAppLanguage.current.locale),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun Searching(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "TREFFER · 23 Meldungen" and the follow button. */
@Composable
private fun ResultsHeader(strings: SearchStrings, count: Int, followed: Boolean, onToggleFollow: () -> Unit) {
    Column(Modifier.padding(start = 4.dp, top = 12.dp)) {
        Overline(strings.resultsOverline)
        Text(
            text = strings.resultCount(count),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(12.dp))
        GlassPillButton(
            text = if (followed) strings.following else strings.follow,
            icon = if (followed) Icons.Rounded.TaskAlt else Icons.Rounded.Add,
            filled = !followed,
            onClick = onToggleFollow,
        )
    }
}

@Composable
private fun OfflineBanner(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(GlassShapes.chip)
            .background(colors.tertiaryContainer.copy(alpha = if (isGlassDark()) 0.5f else 0.75f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onTertiaryContainer)
    }
}

/** A calm centred state: an icon tile, a title, a line and one action. */
@Composable
private fun Message(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AccentIconTile(icon, size = 64.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        GlassPillButton(text = action, icon = actionIcon, onClick = onAction, filled = true)
    }
}

@Composable
private fun ResultsFooter(results: SearchResults, strings: SearchStrings, onRetry: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            results.loadingMore -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
            results.moreFailed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strings.moreFailed, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                GlassPillButton(text = commonStrings().retry, icon = Icons.Rounded.Refresh, onClick = onRetry)
            }
            results.isEnd && results.items.isNotEmpty() -> Text(
                text = strings.end(results.items.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A result while the first page loads: picture and two lines of shimmer. */
@Composable
private fun ResultSkeleton() {
    Column(Modifier.padding(top = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LoadingSkeleton(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(GlassShapes.tile),
        )
        LoadingSkeleton(
            Modifier
                .fillMaxWidth(0.9f)
                .height(24.dp),
        )
        LoadingSkeleton(
            Modifier
                .fillMaxWidth(0.6f)
                .height(24.dp),
        )
    }
}

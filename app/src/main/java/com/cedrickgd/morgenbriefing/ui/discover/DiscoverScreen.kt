package com.cedrickgd.morgenbriefing.ui.discover

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.DiscoverStrings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.discoverStrings
import com.cedrickgd.morgenbriefing.ui.components.Emoji
import com.cedrickgd.morgenbriefing.ui.components.GlassIconButton
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.components.GlassSnackbarHost
import com.cedrickgd.morgenbriefing.ui.components.motionDisabled
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import com.cedrickgd.morgenbriefing.ui.glass.glassContent
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import com.cedrickgd.morgenbriefing.ui.nav.DiscoverRoute
import com.cedrickgd.morgenbriefing.ui.news.FeedGutter
import com.cedrickgd.morgenbriefing.ui.news.TopicGlyph
import com.cedrickgd.morgenbriefing.ui.news.iconVector
import com.cedrickgd.morgenbriefing.ui.news.rememberTopicColors
import com.cedrickgd.morgenbriefing.ui.settings.SectionHeading
import com.cedrickgd.morgenbriefing.ui.settings.Stagger
import com.cedrickgd.morgenbriefing.ui.settings.flagEmoji
import com.cedrickgd.morgenbriefing.ui.settings.rememberStagger
import com.cedrickgd.morgenbriefing.ui.settings.staggerIn
import com.cedrickgd.morgenbriefing.ui.shell.reportChromeScroll
import com.cedrickgd.morgenbriefing.util.horizontalSafeDrawing
import com.cedrickgd.morgenbriefing.util.pageSideInset
import com.cedrickgd.morgenbriefing.util.statusBarTop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Scrolled this far, the frosted bar with the title slides in behind the buttons. */
private val SolidBarAfter = 72.dp

/** Lowest scale of a category while the back gesture pulls it away. */
private const val BACK_SCALE = 0.9f

/** Width of a grid with three tiles per row (landscape, tablets). */
private val ThreeColumnsFrom = 560.dp

/** What the screen's rows can do; one stable instance per view model. */
private class DiscoverActions(
    val openCategory: (String) -> Unit,
    val setFollowed: (String, Boolean) -> Unit,
    val toggleSub: (String) -> Unit,
    val togglePlace: (String) -> Unit,
    val removePlace: (String) -> Unit,
    val removeSearch: (String, String) -> Unit,
    val retry: (String) -> Unit,
)

/**
 * "Themen entdecken" (docs/DESIGN.md: glass over the living sky). The grid of the twelve
 * categories as large tiles in their own colors; tapping one opens it on the same screen (with
 * a spring, and the predictive back gesture pulls it away again): a header in the category's
 * gradient, "Ganze Kategorie folgen", the newest headlines and every sub-topic with its own
 * follow pill and headlines. Below the grid: followed places ("Lokal", with a place search),
 * followed searches and the way to an own topic.
 *
 * @param contentPadding bottom room (navigation bar, mini player).
 * @param onOpenSearch opens the search, with a followed search's query or empty.
 * @param onCreateCustomTopic goes to the settings, where own topics are made.
 */
@Composable
fun DiscoverScreen(
    route: DiscoverRoute,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    onOpenSearch: (query: String?) -> Unit,
    onCreateCustomTopic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModel.factory(route.categoryId))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val placeSearch by viewModel.placeSearch.collectAsStateWithLifecycle()
    val strings = discoverStrings()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // "Ort hinzufügen" on "Heute" opens the screen with the place search.
    var placeSheetOpen by rememberSaveable { mutableStateOf(route.addPlace) }
    val now = rememberNow()

    val latestStrings by rememberUpdatedState(strings)
    LaunchedEffect(viewModel, snackbar) {
        viewModel.events.collect { event ->
            val text = when (event) {
                is DiscoverEvent.PlaceAdded -> latestStrings.placeAdded(event.name)
                is DiscoverEvent.SearchRemoved -> latestStrings.searchRemoved(event.title)
                DiscoverEvent.SaveFailed -> latestStrings.saveFailed
            }
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(text) }
        }
    }
    val onBlocked: () -> Unit = {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(latestStrings.lastTopicHint)
        }
    }

    // The back gesture inside a category: the page shrinks and slides with the finger.
    val backProgress = remember { Animatable(0f) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    val actions = remember(viewModel) {
        DiscoverActions(
            openCategory = { id ->
                draggedId = null
                scope.launch { backProgress.snapTo(0f) }
                viewModel.selectCategory(id)
            },
            setFollowed = viewModel::setFollowed,
            toggleSub = viewModel::toggleSubPreview,
            togglePlace = viewModel::togglePlace,
            removePlace = viewModel::removePlace,
            removeSearch = viewModel::removeSearch,
            retry = viewModel::retryPreview,
        )
    }
    val detailId = state.detail?.category?.topic?.id
    PredictiveBackHandler(enabled = detailId != null) { progress ->
        draggedId = detailId
        try {
            progress.collect { event -> backProgress.snapTo(event.progress) }
            viewModel.selectCategory(null)
        } catch (e: CancellationException) {
            scope.launch { backProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
            throw e
        }
    }

    val homeList = rememberLazyListState()
    // Lives as long as the screen: the grid plays its entrance once, not after every category.
    val homeStagger = rememberStagger(enabled = !motionDisabled())
    // The app-wide screen-edge gutter (docs/DESIGN.md §5), like the feed.
    val side = FeedGutter + pageSideInset()

    Box(modifier.fillMaxSize()) {
        if (state.loaded) {
            AnimatedContent(
                targetState = state.detail,
                contentKey = { it?.category?.topic?.id },
                transitionSpec = {
                    val opening = targetState != null
                    val enter = scaleIn(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow), initialScale = if (opening) 0.9f else 1.04f) +
                        fadeIn(spring(stiffness = Spring.StiffnessMediumLow))
                    val exit = scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = if (opening) 1.04f else 0.9f) +
                        fadeOut(spring(stiffness = Spring.StiffnessMedium))
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "discover",
            ) { detail ->
                if (detail == null) {
                    DiscoverHome(
                        state = state,
                        listState = homeList,
                        stagger = homeStagger,
                        side = side,
                        bottom = contentPadding.calculateBottomPadding(),
                        now = now,
                        actions = actions,
                        onBack = onBack,
                        onOpenArticle = onOpenArticle,
                        onOpenSearch = onOpenSearch,
                        onAddPlace = { placeSheetOpen = true },
                        onCreateCustomTopic = onCreateCustomTopic,
                        onBlocked = onBlocked,
                    )
                } else {
                    val id = detail.category.topic.id
                    CategoryDetail(
                        detail = detail,
                        side = side,
                        bottom = contentPadding.calculateBottomPadding(),
                        now = now,
                        actions = actions,
                        onBack = { viewModel.selectCategory(null) },
                        onOpenArticle = onOpenArticle,
                        onBlocked = onBlocked,
                        modifier = Modifier.graphicsLayer {
                            val p = if (draggedId == id) backProgress.value else 0f
                            val scale = 1f - (1f - BACK_SCALE) * p
                            scaleX = scale
                            scaleY = scale
                            translationX = p * 32.dp.toPx()
                            shape = RoundedCornerShape((36 * p).dp)
                            clip = p > 0f
                        },
                    )
                }
            }
        }
        GlassSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .horizontalSafeDrawing()
                .padding(bottom = contentPadding.calculateBottomPadding() + 12.dp),
        )
    }

    if (placeSheetOpen) {
        PlaceSearchSheet(
            search = placeSearch,
            newsLanguage = state.newsLanguage,
            onQueryChange = viewModel::onPlaceQuery,
            onPick = { place ->
                placeSheetOpen = false
                viewModel.addPlace(place)
            },
            onDismiss = {
                placeSheetOpen = false
                viewModel.resetPlaceSearch()
            },
        )
    }
}

/** Columns of the category grid for the window width (two on phones, three when roomy). */
@Composable
private fun gridColumns(side: Dp): Int {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } - side * 2
    return if (width >= ThreeColumnsFrom) 3 else 2
}

@Composable
private fun rememberSolidBar(listState: LazyListState): Boolean {
    val thresholdPx = with(LocalDensity.current) { SolidBarAfter.toPx() }
    val solid by remember(listState, thresholdPx) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > thresholdPx }
    }
    return solid
}

@Composable
private fun DiscoverHome(
    state: DiscoverUiState,
    listState: LazyListState,
    stagger: Stagger,
    side: Dp,
    bottom: Dp,
    now: ZonedDateTime,
    actions: DiscoverActions,
    onBack: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    onOpenSearch: (String?) -> Unit,
    onAddPlace: () -> Unit,
    onCreateCustomTopic: () -> Unit,
    onBlocked: () -> Unit,
) {
    val strings = discoverStrings()
    val common = commonStrings()
    val columns = gridColumns(side)
    val rows = remember(state.categories, columns) { state.categories.chunked(columns) }
    val statusTop = statusBarTop()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .reportChromeScroll()
                .glassContent()
                .horizontalSafeDrawing(),
            contentPadding = PaddingValues(start = side, end = side, top = statusTop + PushedBarHeight, bottom = bottom + 32.dp),
        ) {
            item(key = "head", contentType = "head") {
                HomeHeader(strings, state.followedCount, Modifier.staggerIn(stagger, "head", 0))
            }
            item(key = "h-categories", contentType = "heading") {
                SectionHeading(overline = strings.categoriesOverline, title = strings.categoriesTitle, top = 28.dp)
            }
            items(rows.size, key = { "row-$it" }, contentType = { "tiles" }) { index ->
                CategoryRow(
                    tiles = rows[index],
                    columns = columns,
                    onOpen = actions.openCategory,
                    modifier = Modifier
                        .padding(bottom = TileGap)
                        .staggerIn(stagger, "row-$index", index + 1),
                )
            }
            item(key = "h-local", contentType = "heading") {
                SectionHeading(overline = strings.localOverline, title = strings.localTitle, top = 28.dp)
            }
            item(key = "local", contentType = "card") {
                LocalCard(state, now, actions, onOpenArticle, onAddPlace)
            }
            item(key = "h-searches", contentType = "heading") {
                SectionHeading(overline = strings.searchesOverline, title = strings.searchesTitle)
            }
            item(key = "searches", contentType = "card") {
                SearchesCard(state, actions, onOpenSearch, onBlocked)
            }
            item(key = "custom", contentType = "card") {
                CustomTopicCard(onCreateCustomTopic, Modifier.padding(top = 16.dp))
            }
        }
        PushedTopBar(
            title = strings.title,
            solid = rememberSolidBar(listState),
            backLabel = common.back,
            onBack = onBack,
            actions = { GlassIconButton(Icons.Rounded.Search, contentDescription = common.search, onClick = { onOpenSearch(null) }) },
        )
    }
}

@Composable
private fun HomeHeader(strings: DiscoverStrings, followedCount: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 4.dp)) {
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
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = if (isGlassDark()) 0.22f else 0.12f))
                .padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.followingSummary(followedCount), style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
        Spacer(Modifier.height(14.dp))
        Text(text = strings.intro, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun LocalCard(
    state: DiscoverUiState,
    now: ZonedDateTime,
    actions: DiscoverActions,
    onOpenArticle: (NewsItem) -> Unit,
    onAddPlace: () -> Unit,
) {
    val strings = discoverStrings()
    GlassCard(contentPadding = PaddingValues(12.dp)) {
        if (state.places.isEmpty()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AccentIconTile(Icons.Rounded.Place)
                Spacer(Modifier.width(14.dp))
                Text(strings.localEmpty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            state.places.forEachIndexed { index, place ->
                if (index > 0) RowDivider()
                PlaceRow(place, now, actions, onOpenArticle)
            }
        }
        Spacer(Modifier.height(8.dp))
        GlassPillButton(
            text = strings.addPlace,
            icon = Icons.Rounded.AddLocationAlt,
            onClick = onAddPlace,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun PlaceRow(place: PlaceUi, now: ZonedDateTime, actions: DiscoverActions, onOpenArticle: (NewsItem) -> Unit) {
    val strings = discoverStrings()
    val topic = remember(place.place) { Topics.localTopic(place.place) }
    val colors = rememberTopicColors(topic)
    val open = place.preview != null
    val chevron by animateFloatAsState(if (open) 180f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "chevron")
    val interaction = remember { MutableInteractionSource() }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressScale(interaction, pressedScale = 0.98f)
                .clip(RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    role = Role.Button,
                    onClickLabel = strings.togglePlacePreview,
                    onClick = { actions.togglePlace(place.topicId) },
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .drawBehind { drawTopicCover(colors) },
                contentAlignment = Alignment.Center,
            ) {
                val flag = flagEmoji(place.place.countryCode)
                if (flag.isNotEmpty()) Emoji(flag, size = 22.sp) else TopicGlyph(topic, tint = Color.White, size = 22.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(place.place.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = strings.placeNews(place.language, place.country),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.onGlass(),
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = chevron },
            )
            IconButton(onClick = { actions.removePlace(place.topicId) }) {
                Icon(Icons.Rounded.Close, contentDescription = strings.removePlace(place.place.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // The last headlines stay while the row closes, so it shrinks with its content.
        val last = remember { arrayOfNulls<PreviewUi>(1) }
        place.preview?.let { last[0] = it }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            val preview = place.preview ?: last[0]
            if (preview != null) {
                PreviewBlock(
                    preview = preview,
                    accent = colors.onGlass(),
                    now = now,
                    onOpen = onOpenArticle,
                    onRetry = { actions.retry(place.topicId) },
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchesCard(state: DiscoverUiState, actions: DiscoverActions, onOpenSearch: (String?) -> Unit, onBlocked: () -> Unit) {
    val strings = discoverStrings()
    GlassCard(contentPadding = PaddingValues(12.dp)) {
        if (state.searches.isEmpty()) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AccentIconTile(Icons.Rounded.Search)
                Spacer(Modifier.width(14.dp))
                Text(strings.searchesEmpty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            GlassPillButton(
                text = strings.openSearch,
                icon = Icons.Rounded.Search,
                onClick = { onOpenSearch(null) },
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        } else {
            state.searches.forEachIndexed { index, search ->
                if (index > 0) RowDivider()
                SearchTopicRow(search, actions, onOpenSearch, onBlocked)
            }
        }
    }
}

@Composable
private fun SearchTopicRow(search: FollowableUi, actions: DiscoverActions, onOpenSearch: (String?) -> Unit, onBlocked: () -> Unit) {
    val strings = discoverStrings()
    val topic = search.topic
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, pressedScale = 0.98f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = strings.searchAgain,
                onClick = { onOpenSearch(topic.query) },
            )
            .heightIn(min = 64.dp)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopicIconTile(topic)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(topic.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(search.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        FollowPill(
            topic = topic,
            title = topic.title,
            followed = search.followed,
            canToggle = search.canToggle,
            onToggle = { actions.setFollowed(topic.id, it) },
            onBlocked = onBlocked,
        )
        IconButton(onClick = { actions.removeSearch(topic.id, topic.title) }) {
            Icon(Icons.Rounded.Close, contentDescription = strings.removeSearch(topic.title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CustomTopicCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val strings = discoverStrings()
    val colors = MaterialTheme.colorScheme
    GlassCard(modifier = modifier, onClick = onClick, onClickLabel = strings.customAction) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(Icons.Rounded.RssFeed, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(strings.customOverline.uppercase(LocalAppLanguage.current.locale), style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp), color = colors.onSurfaceVariant)
                Text(strings.customTitle, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(strings.customBody, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(strings.customAction, style = MaterialTheme.typography.labelLarge, color = colors.primary)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

/** A hairline between rows inside a card. */
@Composable
internal fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isGlassDark()) 0.08f else 0.06f),
    )
}

// ---------------------------------------------------------------------------------------------
// A category
// ---------------------------------------------------------------------------------------------

@Composable
private fun CategoryDetail(
    detail: CategoryDetailUi,
    side: Dp,
    bottom: Dp,
    now: ZonedDateTime,
    actions: DiscoverActions,
    onBack: () -> Unit,
    onOpenArticle: (NewsItem) -> Unit,
    onBlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = discoverStrings()
    val category = detail.category
    val colors = rememberTopicColors(category.topic)
    val listState = rememberLazyListState()
    val stagger = rememberStagger(enabled = !motionDisabled())
    val pad = Modifier.padding(horizontal = side)

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .reportChromeScroll()
                .glassContent(),
            contentPadding = PaddingValues(bottom = bottom + 32.dp),
        ) {
            item(key = "hero", contentType = "hero") { CategoryHero(detail, side) }
            item(key = "follow", contentType = "card") {
                GlassCard(
                    modifier = pad
                        .padding(top = 20.dp)
                        .staggerIn(stagger, "follow", 0),
                    contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(strings.followCategory, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(strings.followCategoryHint(category.topic.title), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        FollowPill(
                            topic = category.topic,
                            title = strings.followCategory,
                            followed = category.followed,
                            canToggle = category.canToggle,
                            onToggle = { actions.setFollowed(category.topic.id, it) },
                            onBlocked = onBlocked,
                        )
                    }
                }
            }
            item(key = "preview", contentType = "card") {
                GlassCard(
                    modifier = pad
                        .padding(top = 14.dp)
                        .staggerIn(stagger, "preview", 1),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
                ) {
                    Text(
                        text = strings.previewOverline.uppercase(LocalAppLanguage.current.locale),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                        color = colors.onGlass(),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    PreviewBlock(
                        preview = detail.preview,
                        accent = colors.onGlass(),
                        now = now,
                        onOpen = onOpenArticle,
                        onRetry = { actions.retry(category.topic.id) },
                    )
                    if (category.followed && detail.preview.items.isNotEmpty()) {
                        Text(
                            text = strings.previewInFeed,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (detail.subTopics.isNotEmpty()) {
                item(key = "h-subs", contentType = "heading") {
                    SectionHeading(overline = strings.subtopicsOverline, title = strings.subtopicsTitle, modifier = pad, top = 28.dp)
                }
                item(key = "subs", contentType = "card") {
                    GlassCard(
                        modifier = pad.staggerIn(stagger, "subs", 2),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        detail.subTopics.forEachIndexed { index, sub ->
                            if (index > 0) RowDivider()
                            SubTopicRow(
                                sub = sub,
                                expanded = detail.expandedSubId == sub.topic.id,
                                preview = detail.subPreview?.takeIf { detail.expandedSubId == sub.topic.id },
                                now = now,
                                actions = actions,
                                onOpenArticle = onOpenArticle,
                                onBlocked = onBlocked,
                            )
                        }
                    }
                }
            }
        }
        PushedTopBar(
            title = category.topic.title,
            solid = rememberSolidBar(listState),
            backLabel = strings.allCategories,
            onBack = onBack,
        )
    }
}

/** The category's header: its gradient under the status bar, the big icon, title and description. */
@Composable
private fun CategoryHero(detail: CategoryDetailUi, side: Dp) {
    val strings = discoverStrings()
    val topic = detail.category.topic
    val colors = rememberTopicColors(topic)
    val watermark = topic.iconVector()
    val shape = RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind { drawTopicCover(colors, glowStrength = 1.1f) },
    ) {
        if (watermark != null) {
            Icon(
                imageVector = watermark,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 36.dp, y = 36.dp)
                    .size(220.dp)
                    .graphicsLayer { rotationZ = -12f },
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .horizontalSafeDrawing()
                .padding(start = side + 4.dp, end = side + 4.dp, top = statusBarTop() + PushedBarHeight + 16.dp, bottom = 28.dp),
        ) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                TopicGlyph(topic, tint = Color.White, size = 34.dp)
            }
            Spacer(Modifier.height(18.dp))
            val count = detail.subTopics.size
            Text(
                text = (if (count > 0) strings.subtopicCount(count) else strings.categoriesOverline).uppercase(LocalAppLanguage.current.locale),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                color = Color.White.copy(alpha = 0.82f),
            )
            Text(
                text = topic.title,
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = detail.category.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun SubTopicRow(
    sub: FollowableUi,
    expanded: Boolean,
    preview: PreviewUi?,
    now: ZonedDateTime,
    actions: DiscoverActions,
    onOpenArticle: (NewsItem) -> Unit,
    onBlocked: () -> Unit,
) {
    val strings = discoverStrings()
    val colors = rememberTopicColors(sub.topic)
    val interaction = remember { MutableInteractionSource() }
    val dark = isGlassDark()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (expanded) colors.container.copy(alpha = if (dark) 0.45f else 0.6f) else Color.Transparent),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressScale(interaction, pressedScale = 0.98f)
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    role = Role.Button,
                    onClickLabel = strings.showPreview,
                    onClick = { actions.toggleSub(sub.topic.id) },
                )
                .heightIn(min = 68.dp)
                .padding(start = 8.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopicIconTile(sub.topic)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(sub.topic.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(sub.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            FollowPill(
                topic = sub.topic,
                title = sub.topic.title,
                followed = sub.followed,
                canToggle = sub.canToggle,
                onToggle = { actions.setFollowed(sub.topic.id, it) },
                onBlocked = onBlocked,
            )
        }
        val last = remember { arrayOfNulls<PreviewUi>(1) }
        preview?.let { last[0] = it }
        AnimatedVisibility(
            visible = expanded && preview != null,
            enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            val shown = preview ?: last[0]
            if (shown != null) {
                PreviewBlock(
                    preview = shown,
                    accent = colors.onGlass(),
                    now = now,
                    onOpen = onOpenArticle,
                    onRetry = { actions.retry(sub.topic.id) },
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
                )
            }
        }
    }
}

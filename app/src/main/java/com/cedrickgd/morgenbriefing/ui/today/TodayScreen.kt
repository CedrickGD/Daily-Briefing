package com.cedrickgd.morgenbriefing.ui.today

import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.components.GlassSnackbarHost
import com.cedrickgd.morgenbriefing.ui.components.InfoCard
import com.cedrickgd.morgenbriefing.ui.components.NoticeBanner
import com.cedrickgd.morgenbriefing.ui.components.PageFooter
import com.cedrickgd.morgenbriefing.ui.components.WarningBanner
import com.cedrickgd.morgenbriefing.ui.components.WeatherCardSkeleton
import com.cedrickgd.morgenbriefing.ui.components.headerOverlineColor
import com.cedrickgd.morgenbriefing.perf.Effects
import com.cedrickgd.morgenbriefing.ui.glass.LocalEffects
import com.cedrickgd.morgenbriefing.ui.glass.TopGlassBar
import com.cedrickgd.morgenbriefing.ui.glass.glassContent
import com.cedrickgd.morgenbriefing.ui.news.CardInset
import com.cedrickgd.morgenbriefing.ui.news.CoverPreloader
import com.cedrickgd.morgenbriefing.ui.news.DiscoverInvite
import com.cedrickgd.morgenbriefing.ui.news.EndAction
import com.cedrickgd.morgenbriefing.ui.news.FeedActions
import com.cedrickgd.morgenbriefing.ui.news.FeedArticle
import com.cedrickgd.morgenbriefing.ui.news.FeedBar
import com.cedrickgd.morgenbriefing.ui.news.FeedFilter
import com.cedrickgd.morgenbriefing.ui.news.FeedGutter
import com.cedrickgd.morgenbriefing.ui.news.FeedHints
import com.cedrickgd.morgenbriefing.ui.news.LeadPictureHeight
import com.cedrickgd.morgenbriefing.ui.news.ListEndNote
import com.cedrickgd.morgenbriefing.ui.news.ListTail
import com.cedrickgd.morgenbriefing.ui.news.LoadOlderRow
import com.cedrickgd.morgenbriefing.ui.news.LoadingMoreRow
import com.cedrickgd.morgenbriefing.ui.news.NewsFeed
import com.cedrickgd.morgenbriefing.ui.news.NowListening
import com.cedrickgd.morgenbriefing.ui.news.SectionUi
import com.cedrickgd.morgenbriefing.ui.news.TabSwitch
import com.cedrickgd.morgenbriefing.ui.news.ThumbnailSize
import com.cedrickgd.morgenbriefing.ui.news.VERIFY_LOW_WATER
import com.cedrickgd.morgenbriefing.ui.news.YourTopicsSheet
import com.cedrickgd.morgenbriefing.ui.news.barParked
import com.cedrickgd.morgenbriefing.ui.news.canUnfollow
import com.cedrickgd.morgenbriefing.ui.news.endAction
import com.cedrickgd.morgenbriefing.ui.news.feedTabs
import com.cedrickgd.morgenbriefing.ui.news.fetchedLabel
import com.cedrickgd.morgenbriefing.ui.news.followSuggestion
import com.cedrickgd.morgenbriefing.ui.news.forYouItems
import com.cedrickgd.morgenbriefing.ui.news.invitesToDiscover
import com.cedrickgd.morgenbriefing.ui.news.listTail
import com.cedrickgd.morgenbriefing.ui.news.nearEnd
import com.cedrickgd.morgenbriefing.ui.news.noteCardItem
import com.cedrickgd.morgenbriefing.ui.news.rememberFeedActions
import com.cedrickgd.morgenbriefing.ui.news.rememberListenController
import com.cedrickgd.morgenbriefing.ui.news.rememberListeningKey
import com.cedrickgd.morgenbriefing.ui.news.resolveFilter
import com.cedrickgd.morgenbriefing.ui.news.revealedCount
import com.cedrickgd.morgenbriefing.ui.news.say
import com.cedrickgd.morgenbriefing.ui.news.sectionFor
import com.cedrickgd.morgenbriefing.ui.news.showAllSwitch
import com.cedrickgd.morgenbriefing.ui.news.storyImageUrl
import com.cedrickgd.morgenbriefing.ui.news.storyListItems
import com.cedrickgd.morgenbriefing.ui.news.suggestionItems
import com.cedrickgd.morgenbriefing.ui.news.tabSwitch
import com.cedrickgd.morgenbriefing.ui.onboarding.OnboardingSheet
import com.cedrickgd.morgenbriefing.ui.shell.LocalSettingsLink
import com.cedrickgd.morgenbriefing.ui.shell.SettingsTarget
import com.cedrickgd.morgenbriefing.ui.shell.reportChromeScroll
import com.cedrickgd.morgenbriefing.ui.theme.OverlineLetterSpacing
import com.cedrickgd.morgenbriefing.ui.theme.rememberReduceMotion
import com.cedrickgd.morgenbriefing.util.KeepListEndAboveChrome
import com.cedrickgd.morgenbriefing.util.bottomContentInset
import com.cedrickgd.morgenbriefing.util.horizontalSafeDrawing
import com.cedrickgd.morgenbriefing.util.pageSideInset
import com.cedrickgd.morgenbriefing.util.statusBarTop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Key of the feed bar in the list. */
private const val BAR_KEY = "today-bar"

/** Height of the feed bar in the list before it was ever measured (field 48 + tabs 48 + margins). */
private val DefaultBarItemHeight = 120.dp

/** Space between the feed bar and what follows it in the list (outside the pinned glass). */
private val BarGap = 8.dp

/** Scrolled this far, content would slide under the status bar: the top glass strip appears. */
private val StripAfter = 12.dp

/** The list must rest this long before the articles on screen are warmed. */
private const val PREFETCH_SETTLE_MS = 350L

/**
 * Page "Heute" (docs/DESIGN.md §7): transparent over the AmbientSky. The date and the greeting
 * directly on the sky, the weather as one compact glass card (tap: more; warnings stay visible
 * below it), then the feed bar – a search field and the tabs "Für dich", "Neueste", one per
 * followed topic and "＋" – which pins to the top once it reaches it: the page's top glass, a
 * strip behind the status bar while the page scrolls, takes it in (one glass surface). Below it
 * the news as glass cards: "Für dich" one card per topic (the first story large, then compact
 * rows, "Alle →" opens the topic's tab), "Neueste" and every topic as one endless card.
 *
 * Every view is endless: at its end the next stories are revealed, then older ones are loaded
 * by themselves – a topic's older pages, and for "Neueste" and the list below the "Für dich"
 * cards the older pages of every topic that can still page (the age setting bounds only the
 * "Für dich" cards). A retry row shows only after a load that failed.
 *
 * Articles open in the reader ([onOpenArticle]); the articles on screen are warmed ahead in the
 * reader's cache (as the prefetch setting allows) and the warming stops when the page is left.
 * The covers of stories without a picture are looked up ahead of the list on any connection,
 * and the pictures of the next screen are loaded ahead (not with "Bilder anzeigen" off).
 *
 * @param contentPadding top is 0 (the page handles the status bar); bottom leaves room for the
 *   floating chrome and the navigation bar.
 * @param openTodaySignal changes on a notification tap — scrolls back to the top.
 * @param onOpenSearch opens the search screen.
 * @param onOpenDiscover opens the discover screen, at [categoryId]'s detail when not null.
 * @param onAddPlace opens the discover screen with its place search ("Ort hinzufügen").
 */
@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    openTodaySignal: Int,
    onOpenSettings: () -> Unit,
    onOpenArticle: (FeedArticle) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSearch: () -> Unit = {},
    onOpenDiscover: (categoryId: String?) -> Unit = {},
    onAddPlace: () -> Unit = {},
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onboarding by viewModel.onboarding.collectAsStateWithLifecycle()
    val images = viewModel.articleImages.collectAsStateWithLifecycle()
    val statuses = viewModel.articleStatus.collectAsStateWithLifecycle()
    val covers = viewModel.articleCovers.collectAsStateWithLifecycle()
    val hints = remember(images, statuses, covers) { FeedHints(images, statuses, covers) }
    val listState = rememberLazyListState()
    // After process death the saved position points into the feed; measuring the header and the
    // loading skeleton first would clamp it to their few items. Until weather and news are known
    // the page runs on a list state of its own; the saved one is attached with the real content.
    val loadingListState = remember { LazyListState() }
    val contentKnownNow = state.feed != null && (state.weather != null || !state.weatherLoading)
    var contentKnown by remember { mutableStateOf(contentKnownNow) }
    LaunchedEffect(contentKnownNow) { if (contentKnownNow) contentKnown = true }
    val settingsLink = LocalSettingsLink.current

    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.stopPrefetch() }
    }

    // Saved with the page state, so coming back does not count as a new notification tap.
    var handledSignal by rememberSaveable { mutableIntStateOf(openTodaySignal) }
    LaunchedEffect(openTodaySignal) {
        if (openTodaySignal != handledSignal) {
            handledSignal = openTodaySignal
            listState.animateScrollToItem(0)
        }
    }

    val openSettingsAt: (SettingsTarget) -> Unit = { target ->
        settingsLink.request(target)
        onOpenSettings()
    }

    val callbacks = TodayCallbacks(
        onRefresh = viewModel::refresh,
        onRetryWeather = viewModel::retryWeather,
        onRetryNews = viewModel::retryNews,
        onOpenSettingsAt = openSettingsAt,
        onOpenArticle = onOpenArticle,
        onOpenSearch = onOpenSearch,
        onOpenDiscover = onOpenDiscover,
        onAddPlace = onAddPlace,
        onSetTopicOrder = viewModel::setTopicOrder,
        onSetSourceEnabled = viewModel::setSourceEnabled,
        onFollow = viewModel::follow,
        onUnfollow = viewModel::unfollow,
        onLoadMore = viewModel::loadMore,
        onLoadOlder = viewModel::loadOlder,
        onPrefetch = viewModel::prefetch,
        onCovers = viewModel::covers,
        onWeatherExpanded = viewModel::setWeatherExpanded,
    )

    TodayContent(
        state = state,
        listState = if (contentKnown) listState else loadingListState,
        contentPadding = contentPadding,
        callbacks = callbacks,
        hints = hints,
        modifier = modifier,
    )

    if (onboarding) {
        OnboardingSheet(
            onDone = viewModel::finishOnboarding,
            onChoosePlace = {
                viewModel.finishOnboarding()
                openSettingsAt(SettingsTarget.HOME_PLACE)
            },
        )
    }
}

/** Everything the page can ask for, bundled to keep the content function readable. */
private class TodayCallbacks(
    val onRefresh: () -> Unit,
    val onRetryWeather: () -> Unit,
    val onRetryNews: () -> Unit,
    val onOpenSettingsAt: (SettingsTarget) -> Unit,
    val onOpenArticle: (FeedArticle) -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenDiscover: (String?) -> Unit,
    val onAddPlace: () -> Unit,
    val onSetTopicOrder: (List<String>) -> Unit,
    val onSetSourceEnabled: (String, Boolean) -> Unit,
    val onFollow: (String, Int?) -> Unit,
    val onUnfollow: (String) -> Unit,
    val onLoadMore: (String) -> Unit,
    val onLoadOlder: () -> Unit,
    val onPrefetch: (List<NewsItem>) -> Unit,
    val onCovers: (List<NewsItem>) -> Unit,
    val onWeatherExpanded: (Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayContent(
    state: TodayUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    callbacks: TodayCallbacks,
    hints: FeedHints,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val strings = feedStrings()
    val common = commonStrings()
    val language = LocalAppLanguage.current
    val statusTop = statusBarTop()
    val statusTopPx = with(density) { statusTop.roundToPx() }
    val stripAfterPx = with(density) { StripAfter.roundToPx() }
    val bottomInset = bottomContentInset(contentPadding)
    val pullState = rememberPullToRefreshState()
    // Landscape and tablets: the feed stays a column, pictures do not grow with the window.
    val pageSide = pageSideInset()
    val snackbar = remember { SnackbarHostState() }

    val weather = state.weather
    val now = state.now
    val prefs = state.prefs
    val feed = state.feed
    val tabs = remember(feed, strings) { feedTabs(feed, strings) }
    var selectedKey by rememberSaveable { mutableStateOf(FeedFilter.ForYou.key) }
    val selected = remember(selectedKey, tabs) { resolveFilter(FeedFilter.fromKey(selectedKey), tabs) }
    val listening by rememberListeningKey(rememberListenController())
    var showTopics by rememberSaveable { mutableStateOf(false) }
    val showImages = prefs?.showImages ?: true

    // The feed bar: in the list until it reaches the status bar, then carried by the top glass.
    val warningsShown = weather != null && WeatherCard.WARNINGS in state.weatherCards && weather.warnings.isNotEmpty()
    val barIndex = if (feed != null) feedBarIndex(warningsShown) else -1
    val tabRowState = rememberLazyListState()
    val parked by remember(listState, statusTopPx, barIndex) {
        derivedStateOf { barParked(listState, barIndex, statusTopPx) }
    }
    var barItemHeightPx by rememberSaveable { mutableIntStateOf(0) }

    val unfollow: (Topic) -> Unit = { topic ->
        val followed = prefs?.followedIds.orEmpty()
        if (!canUnfollow(followed, topic.id)) {
            snackbar.say(scope, strings.lastTopicStays)
        } else {
            val index = followed.indexOf(topic.id).takeIf { it >= 0 }
            callbacks.onUnfollow(topic.id)
            snackbar.say(scope, strings.unfollowed(topic.title), action = strings.undo) {
                callbacks.onFollow(topic.id, index)
            }
        }
    }
    val follow: (Topic) -> Unit = { topic ->
        callbacks.onFollow(topic.id, null)
        snackbar.say(scope, strings.followed(topic.title), action = strings.undo) { callbacks.onUnfollow(topic.id) }
    }
    val hideSource: (FeedArticle) -> Unit = { article ->
        val name = article.item.source.ifBlank { article.item.publisherDomain }
        callbacks.onSetSourceEnabled(article.item.sourceId, false)
        snackbar.say(scope, strings.sourceHidden(name), action = strings.undo) {
            callbacks.onSetSourceEnabled(article.item.sourceId, true)
        }
    }
    val actions = rememberFeedActions(
        onMessage = { snackbar.say(scope, it) },
        onHideSource = hideSource,
        onOpenArticle = callbacks.onOpenArticle.takeIf { prefs?.readInApp != false },
        onUnfollowTopic = unfollow,
        onFollowTopic = follow,
        followSuggestion = { article -> prefs?.let { followSuggestion(article, it.followedIds, it.language) } },
        canUnfollowTopic = { topic -> prefs?.followedIds?.let { topic.id in it && canUnfollow(it, topic.id) } ?: false },
    )
    // The bar pinned at the top, the view starting right below it.
    val scrollToBar: (animate: Boolean) -> Unit = { animate ->
        if (barIndex >= 0) {
            scope.launch {
                if (animate && !reduceMotion) {
                    listState.animateScrollToItem(barIndex, scrollOffset = -statusTopPx)
                } else {
                    listState.scrollToItem(barIndex)
                    listState.scrollBy(-statusTopPx.toFloat())
                }
            }
        }
    }
    val switchTo: (TabSwitch) -> Unit = { switch ->
        // Always remember the tap: after an unfollowed topic fell back to "Für dich", the stored
        // key still names that topic, and tapping "Für dich" must replace it.
        selectedKey = switch.filter.key
        if (switch.scrollToBar) scrollToBar(false)
    }
    val bar: @Composable (onGlass: Boolean) -> Unit = { onGlass ->
        FeedBar(
            tabs = tabs,
            selected = selected,
            onSelect = { filter -> switchTo(tabSwitch(filter, selected, parked)) },
            onSearch = callbacks.onOpenSearch,
            onManageTopics = { showTopics = true },
            rowState = tabRowState,
            onGlass = onGlass,
        )
    }

    // Every view is endless: reveal more before the end comes into view; once everything loaded
    // is revealed, ask for older articles well ahead of the end (nearEnd) – a topic's own, or (the
    // timeline behind "Neueste" and below the "Für dich" cards) those of the topics that reach
    // back least far.
    var pages by rememberSaveable(selectedKey) { mutableIntStateOf(1) }
    val topicId = (selected as? FeedFilter.OneTopic)?.topicId
    val total = when (selected) {
        FeedFilter.Latest -> feed?.timeline?.size ?: 0
        is FeedFilter.OneTopic -> feed?.sectionFor(selected.topicId)?.count ?: 0
        FeedFilter.ForYou -> feed?.more?.size ?: 0
    }
    val revealed = revealedCount(pages, total)
    val canLoadMore = if (topicId != null) state.canLoadMore(topicId) else feed?.olderNext?.isNotEmpty() == true
    val loadingMore = if (topicId != null) topicId in state.loadingMore else state.loadingOlder
    val stalled = if (topicId != null) topicId in state.stalled else state.olderStalled
    // "Nur vollständige Artikel": candidates still to check below, and all candidates of the list.
    val waiting = when (selected) {
        is FeedFilter.OneTopic -> feed?.sectionFor(selected.topicId)?.waiting?.size ?: 0
        FeedFilter.Latest, FeedFilter.ForYou -> feed?.timelineWaiting?.size ?: 0
    }
    val candidates = when (selected) {
        is FeedFilter.OneTopic -> feed?.sectionFor(selected.topicId)?.candidates ?: 0
        FeedFilter.Latest, FeedFilter.ForYou -> feed?.timelineCandidates ?: 0
    }
    val lowOnCandidates = waiting < VERIFY_LOW_WATER
    val currentCallbacks by rememberUpdatedState(callbacks)
    LaunchedEffect(listState, revealed, total, canLoadMore, loadingMore, stalled, topicId, lowOnCandidates, candidates > 0) {
        val action = endAction(revealed, total, canLoadMore, loadingMore, stalled, waiting, candidates)
        if (action == EndAction.NONE) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.filter { (last, count) -> nearEnd(action, last, count) }.first()
        when (action) {
            EndAction.REVEAL -> pages++
            EndAction.LOAD_MORE -> if (topicId != null) currentCallbacks.onLoadMore(topicId) else currentCallbacks.onLoadOlder()
            EndAction.NONE -> Unit
        }
    }

    // Covers ahead of the list, on any connection: the stories on screen and the next page get
    // theirs looked up (the ones whose feed carried no picture), and the pictures of the next
    // screen are loaded in the size their cards will ask for – as soon as an address is known.
    val context = LocalContext.current
    val preloader = remember(context) { CoverPreloader(context) }
    val preloadAhead = if (LocalEffects.current == Effects.FULL) COVER_PRELOAD_AHEAD else COVER_PRELOAD_AHEAD_LIGHT
    val thumbnailPx = with(density) { ThumbnailSize.roundToPx() }
    val leadHeightPx = with(density) { LeadPictureHeight.roundToPx() }
    val leadInsetPx = with(density) { 2 * (pageSide.roundToPx() + FeedGutter.roundToPx() + CardInset.roundToPx()) }
    LaunchedEffect(listState, feed, selected, revealed, showImages, preloadAhead, leadInsetPx) {
        val current = feed ?: return@LaunchedEffect
        if (!showImages) return@LaunchedEffect
        val stories = viewStories(selected, current, revealed)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key } }
            .distinctUntilChanged()
            .collectLatest { keys ->
                delay(COVER_SETTLE_MS)
                val window = coverWindow(stories, keys, COVER_LOOKUP_AHEAD)
                if (window.isEmpty()) return@collectLatest
                currentCallbacks.onCovers(window.map { it.item })
                val leadWidthPx = listState.layoutInfo.viewportSize.width - leadInsetPx
                val near = coverWindow(stories, keys, preloadAhead)
                snapshotFlow { near.mapNotNull { story -> storyImageUrl(story.item, hints.heroImage(story.item.id))?.let { it to story.lead } } }
                    .collect { pictures ->
                        for ((url, lead) in pictures) {
                            if (lead) preloader.preload(url, leadWidthPx, leadHeightPx) else preloader.preload(url, thumbnailPx, thumbnailPx)
                        }
                    }
            }
    }

    // Once the list rests for a moment: verify the candidates ahead ("nur vollständige Artikel"),
    // else warm the articles on screen and the next few. Runs again whenever the feed changes
    // (a new verdict appends articles, so the window moves on).
    val sectionSize = prefs?.sectionSize ?: AppSettings.DEFAULT_SECTION_SIZE
    LaunchedEffect(listState, feed, selected, revealed, sectionSize) {
        val current = feed ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.key } }
            .distinctUntilChanged()
            .collectLatest { keys ->
                delay(PREFETCH_SETTLE_MS)
                val window = warmWindow(selected, current, revealed, keys, sectionSize)
                if (window.isNotEmpty()) currentCallbacks.onPrefetch(window)
            }
    }

    val greeting = Fmt.greeting(now, language)
    val showSkeleton = weather == null && state.weatherLoading
    val notice = remember(feed, state.offline, state.newsLoading, state.newsWarmed, state.newsAttempted, state.verifyOffline, strings, common) {
        newsNotice(feed, state.offline, state.newsLoading, state.newsWarmed, state.newsAttempted, strings, common, verifyOffline = state.verifyOffline)
    }
    val invite = prefs != null && feed != null && selected == FeedFilter.ForYou &&
        feed.sections.isNotEmpty() && invitesToDiscover(prefs.followedIds.size)

    KeepListEndAboveChrome(listState, bottomInset)

    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = callbacks.onRefresh,
            modifier = Modifier.fillMaxSize(),
            state = pullState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = statusTop),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .reportChromeScroll()
                    // The floating glass above (bar, indicator, player) blurs this content.
                    .glassContent()
                    .horizontalSafeDrawing(),
                contentPadding = PaddingValues(start = pageSide, end = pageSide, bottom = bottomInset + 24.dp),
            ) {
                item(key = "header", contentType = "header") {
                    TodayTop(
                        date = state.today,
                        greeting = greeting,
                        // The greeting lines up with the text inside the cards below it.
                        modifier = Modifier
                            .padding(start = FeedGutter + CardInset, end = FeedGutter)
                            .padding(top = statusTop + 12.dp, bottom = 14.dp),
                    )
                }

                // Exactly one item for the weather (card, skeleton or error): see feedBarIndex.
                when {
                    weather != null -> item(key = "weather", contentType = "weather") {
                        WeatherSummaryCard(
                            weather = weather,
                            cards = state.weatherCards,
                            expanded = state.weatherExpanded,
                            onExpandedChange = callbacks.onWeatherExpanded,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = FeedGutter)
                                .padding(bottom = 12.dp),
                        )
                    }
                    showSkeleton -> item(key = "skeleton", contentType = "skeleton") {
                        WeatherCardSkeleton(
                            Modifier
                                .animateItem()
                                .padding(horizontal = FeedGutter)
                                .padding(bottom = 12.dp),
                        )
                    }
                    else -> item(key = "weather-error", contentType = "info") {
                        InfoCard(
                            emoji = "🌦️",
                            title = strings.weatherUnavailableTitle,
                            text = strings.weatherUnavailableText,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = FeedGutter)
                                .padding(bottom = 12.dp),
                            actionLabel = common.retry,
                            onAction = callbacks.onRetryWeather,
                        )
                    }
                }
                if (warningsShown) {
                    // Warnings matter: visible even while the weather card is collapsed.
                    item(key = "warnings", contentType = "warnings") {
                        WarningBanner(
                            warnings = weather.warnings,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = FeedGutter)
                                .padding(bottom = 12.dp),
                        )
                    }
                }

                if (feed == null) {
                    if (!showSkeleton) item(key = "news-loading", contentType = "loading") { NewsLoadingRow(strings.loadingNews) }
                } else {
                    item(key = BAR_KEY, contentType = "bar") {
                        val height = with(density) { if (barItemHeightPx > 0) barItemHeightPx.toDp() else DefaultBarItemHeight }
                        if (parked) {
                            // The top glass shows the bar; this spot keeps its room in the list.
                            Spacer(Modifier.fillMaxWidth().height(height))
                        } else {
                            Column(Modifier.onSizeChanged { barItemHeightPx = it.height }) {
                                bar(false)
                                Spacer(Modifier.height(BarGap))
                            }
                        }
                    }
                    notice?.let { n ->
                        item(key = "news-notice", contentType = "notice") {
                            NoticeBanner(
                                emoji = n.emoji,
                                title = n.title,
                                text = n.text,
                                actionLabel = n.actionLabel.takeIf { n.action != null },
                                onAction = n.action?.let { act -> { noticeAction(act, callbacks, onTopics = { showTopics = true }) } },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = FeedGutter)
                                    .padding(bottom = 12.dp),
                            )
                        }
                    }
                    if (invite && prefs != null) {
                        item(key = "discover-invite", contentType = "invite") {
                            DiscoverInvite(
                                preview = prefs.suggestions,
                                onDiscover = { callbacks.onOpenDiscover(null) },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = FeedGutter)
                                    .padding(bottom = 14.dp),
                            )
                        }
                    }
                    if (prefs != null && feed.sections.isNotEmpty()) {
                        feedItems(
                            view = FeedView(
                                selected = selected,
                                feed = feed,
                                loadingTopics = state.loadingTopics,
                                now = now,
                                listening = listening,
                                revealed = revealed,
                                tail = listTail(revealed, total, canLoadMore, loadingMore, stalled, checking = waiting > 0, offline = state.verifyOffline),
                                showImages = showImages,
                                verifyOffline = state.verifyOffline,
                            ),
                            actions = actions,
                            hints = hints,
                            strings = strings,
                            onShowAll = { section -> switchTo(showAllSwitch(section)) },
                            onOpenDiscover = callbacks.onOpenDiscover,
                            onLoadOlder = { if (topicId != null) callbacks.onLoadMore(topicId) else callbacks.onLoadOlder() },
                            onBackToTop = { scrollToBar(true) },
                            suggestions = { suggestionItems(prefs.suggestions, prefs.subtitles, callbacks.onOpenDiscover) },
                        )
                    }
                }

                if (weather != null || feed != null) {
                    item(key = "footer", contentType = "footer") {
                        PageFooter(
                            line = footerLine(feed, hasWeather = weather != null, weatherOffline = state.weatherFailed, strings = strings),
                            modifier = Modifier.padding(horizontal = FeedGutter),
                        )
                    }
                }
            }
        }

        // One glass surface at the top: a strip behind the status bar while the page scrolls,
        // which takes in the feed bar once it is pinned.
        val scrolled by remember(listState, stripAfterPx) {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > stripAfterPx }
        }
        val pinned = parked && feed != null
        TopGlassBar(
            title = null,
            visible = scrolled || pinned,
            onTap = {
                scope.launch { if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
            },
            bottom = if (pinned) {
                { Column(Modifier.horizontalSafeDrawing().padding(horizontal = pageSide)) { bar(true) } }
            } else {
                null
            },
        )

        GlassSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 8.dp),
        )
    }

    if (showTopics && prefs != null) {
        YourTopicsSheet(
            topics = prefs.topics,
            counts = remember(feed) { feed?.sections?.associate { it.id to it.count }.orEmpty() },
            onSetOrder = callbacks.onSetTopicOrder,
            onUnfollow = unfollow,
            onOpenDiscover = {
                showTopics = false
                callbacks.onOpenDiscover(null)
            },
            onAddPlace = {
                showTopics = false
                callbacks.onAddPlace()
            },
            onDismiss = { showTopics = false },
        )
    }
}

/** The list must rest this long before covers are looked up (a fling passes many stories). */
private const val COVER_SETTLE_MS = 150L

/** Key prefix of the endless list below the "Für dich" cards. */
private const val MORE_KEY = "foryou-more"

/**
 * List index of the feed bar: the header, the one weather item (card, skeleton or error) and the
 * warnings, when they show, come before it. Must mirror the item order of [TodayContent].
 */
internal fun feedBarIndex(warningsShown: Boolean): Int = 2 + if (warningsShown) 1 else 0

/** The articles of the current view, top to bottom, as far as they are revealed (for prefetching). */
internal fun viewItems(selected: FeedFilter, feed: NewsFeed, revealed: Int): List<NewsItem> = when (selected) {
    FeedFilter.ForYou -> feed.sections.flatMap { it.top } + feed.more.take(revealed).map { it.item }
    FeedFilter.Latest -> feed.timeline.take(revealed).map { it.item }
    is FeedFilter.OneTopic -> feed.sectionFor(selected.topicId)?.all?.take(revealed).orEmpty()
}

/** Runs a notice's button. */
private fun noticeAction(action: NoticeAction, callbacks: TodayCallbacks, onTopics: () -> Unit) {
    when (action) {
        NoticeAction.RETRY -> callbacks.onRetryNews()
        NoticeAction.OPEN_TOPICS -> onTopics()
        NoticeAction.OPEN_SOURCES -> callbacks.onOpenSettingsAt(SettingsTarget.SOURCES)
        NoticeAction.DISCOVER -> callbacks.onOpenDiscover(null)
    }
}

/** What the feed below the bar shows right now. */
private class FeedView(
    val selected: FeedFilter,
    val feed: NewsFeed,
    val loadingTopics: Set<String>,
    val now: ZonedDateTime,
    val listening: NowListening?,
    val revealed: Int,
    val tail: ListTail,
    val showImages: Boolean,
    /** "Nur vollständige Artikel" has candidates to check, but the phone is offline. */
    val verifyOffline: Boolean = false,
)

/** The feed below the bar, depending on the selected tab. */
private fun LazyListScope.feedItems(
    view: FeedView,
    actions: FeedActions,
    hints: FeedHints,
    strings: FeedStrings,
    onShowAll: (SectionUi) -> Unit,
    onOpenDiscover: (String?) -> Unit,
    onLoadOlder: () -> Unit,
    onBackToTop: () -> Unit,
    suggestions: LazyListScope.() -> Unit,
) {
    val feed = view.feed
    when (val selected = view.selected) {
        FeedFilter.ForYou -> {
            forYouItems(
                sections = feed.sections,
                loadingTopics = view.loadingTopics,
                now = view.now,
                listening = view.listening,
                actions = actions,
                hints = hints,
                showImages = view.showImages,
                onShowAll = onShowAll,
                keyPrefix = "foryou",
                verifyOffline = view.verifyOffline,
            )
            suggestions()
            // Below the cards the feed goes on: everything else, newest first, endless.
            if (feed.more.isNotEmpty() || view.tail != ListTail.END) {
                item(key = "$MORE_KEY-intro", contentType = "feed-intro") {
                    MoreIntro(strings.latest, Modifier.animateItem().padding(horizontal = FeedGutter))
                }
                storyListItems(
                    articles = feed.more.take(view.revealed),
                    now = view.now,
                    listening = view.listening,
                    actions = actions,
                    hints = hints,
                    showImages = view.showImages,
                    keyPrefix = MORE_KEY,
                    showTopic = true,
                    leadFirst = false,
                )
                tailItems(view.tail, feed.timeline.size, MORE_KEY, strings, onLoadOlder, onBackToTop)
            }
        }

        FeedFilter.Latest -> {
            if (feed.timeline.isEmpty()) {
                val loading = view.loadingTopics.isNotEmpty()
                val checking = feed.timelineWaiting.isNotEmpty()
                noteCardItem(
                    key = "latest-empty",
                    text = when {
                        loading -> strings.loadingArticles
                        checking && view.verifyOffline -> strings.verifyOffline
                        checking -> strings.verifying
                        feed.verifying && feed.timelineCandidates > 0 -> strings.noneComplete
                        else -> strings.noArticlesYet
                    },
                    loading = loading || (checking && !view.verifyOffline),
                )
            } else {
                storyListItems(
                    articles = feed.timeline.take(view.revealed),
                    now = view.now,
                    listening = view.listening,
                    actions = actions,
                    hints = hints,
                    showImages = view.showImages,
                    keyPrefix = "latest",
                    showTopic = true,
                    leadFirst = false,
                )
                tailItems(view.tail, feed.timeline.size, "latest", strings, onLoadOlder, onBackToTop)
            }
        }

        // The selected tab names the topic; a slim line adds its count, stand and the way to its sub-topics.
        is FeedFilter.OneTopic -> {
            val section = feed.sectionFor(selected.topicId) ?: return
            item(key = "topic-${section.id}-intro", contentType = "feed-intro") {
                TopicListIntro(section, feed, strings, onOpenDiscover, Modifier.animateItem().padding(horizontal = FeedGutter).padding(bottom = 8.dp))
            }
            if (section.isEmpty) {
                val loading = section.id in view.loadingTopics
                val checking = section.waiting.isNotEmpty()
                noteCardItem(
                    key = "topic-${section.id}-empty",
                    text = when {
                        loading -> strings.loadingArticles
                        checking && view.verifyOffline -> strings.verifyOffline
                        checking -> strings.verifying
                        section.noneComplete -> strings.noneComplete
                        section.failedSources.isNotEmpty() -> strings.unreachable(section.failedSources.joinToString(", "))
                        else -> strings.nothingHereRightNow
                    },
                    loading = loading || (checking && !view.verifyOffline),
                )
            } else {
                storyListItems(
                    articles = section.all.take(view.revealed).map { FeedArticle(it, section.topic) },
                    now = view.now,
                    listening = view.listening,
                    actions = actions,
                    hints = hints,
                    showImages = view.showImages,
                    keyPrefix = "topic-${section.id}",
                    showTopic = false,
                    leadFirst = true,
                )
                tailItems(view.tail, section.count, "topic-${section.id}", strings, onLoadOlder, onBackToTop)
            }
        }
    }
}

/**
 * Above a topic's own list, on the sky: "37 MELDUNGEN · STAND 07:12" and a way into the discover
 * screen – a category's sub-topics, or the category a sub-topic belongs to.
 */
@Composable
private fun TopicListIntro(
    section: SectionUi,
    feed: NewsFeed,
    strings: FeedStrings,
    onOpenDiscover: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    val colors = MaterialTheme.colorScheme
    val topic = section.topic
    val parent = remember(topic.parentId, language) { topic.parentId?.let { Topics.builtIn(it, language) } }
    val stand = fetchedLabel(feed.fetchedAt).takeIf { it.isNotBlank() }?.let(strings::stand)
    val browseId = if (section.browsable) topic.id else parent?.id
    val browseTitle = if (section.browsable) topic.title else parent?.title
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = CardInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = listOfNotNull(strings.articleCount(section.count), stand).joinToString(" · ").uppercase(language.locale),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = OverlineLetterSpacing),
            color = headerOverlineColor(colors.onSurfaceVariant, colors.onSurface),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (browseId != null && browseTitle != null) {
            // The tab above already names the topic: a short label, the full one for TalkBack.
            val fullLabel = strings.subTopicsOf(browseTitle)
            GlassPillButton(
                text = strings.subTopicsShort,
                onClick = { onOpenDiscover(browseId) },
                icon = Icons.Rounded.Explore,
                modifier = Modifier.semantics { contentDescription = fullLabel },
            )
        }
    }
}

/** On the sky above the endless list that follows the "Für dich" cards: its name as an overline, on the cards' text line. */
@Composable
private fun MoreIntro(title: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = CardInset, top = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title.uppercase(LocalAppLanguage.current.locale),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = OverlineLetterSpacing),
            color = headerOverlineColor(colors.onSurfaceVariant, colors.onSurface),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** The end of an endless list: revealing, loading older articles, "weiter zurück suchen", or the calm end. */
private fun LazyListScope.tailItems(
    tail: ListTail,
    total: Int,
    keyPrefix: String,
    strings: FeedStrings,
    onLoadOlder: () -> Unit,
    onBackToTop: () -> Unit,
) {
    when (tail) {
        ListTail.REVEALING -> item(key = "$keyPrefix-more", contentType = "loading") {
            LoadingMoreRow(strings.moreComing, Modifier.animateItem())
        }
        ListTail.VERIFYING -> item(key = "$keyPrefix-verifying", contentType = "loading") {
            LoadingMoreRow(strings.verifying, Modifier.animateItem())
        }
        ListTail.VERIFY_OFFLINE -> noteCardItem(key = "$keyPrefix-verify-offline", text = strings.verifyOffline, loading = false)
        ListTail.LOADING_OLDER -> item(key = "$keyPrefix-older", contentType = "loading") {
            LoadingMoreRow(strings.loadingOlder, Modifier.animateItem())
        }
        ListTail.STALLED -> item(key = "$keyPrefix-stalled", contentType = "stalled") {
            LoadOlderRow(onLoadOlder = onLoadOlder, modifier = Modifier.animateItem().padding(horizontal = FeedGutter))
        }
        ListTail.END -> item(key = "$keyPrefix-end", contentType = "end") {
            ListEndNote(strings.listEnd(total), modifier = Modifier.animateItem(), onBackToTop = onBackToTop)
        }
    }
}

@Composable
private fun NewsLoadingRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

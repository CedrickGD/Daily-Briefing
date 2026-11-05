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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.cedrickgd.morgenbriefing.ui.brief.WeatherUi
import com.cedrickgd.morgenbriefing.ui.components.ClothingCard
import com.cedrickgd.morgenbriefing.ui.components.DayCurveCard
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.components.GlassSnackbarHost
import com.cedrickgd.morgenbriefing.ui.components.HomeWeatherHero
import com.cedrickgd.morgenbriefing.ui.components.InfoCard
import com.cedrickgd.morgenbriefing.ui.components.LoadingSkeleton
import com.cedrickgd.morgenbriefing.ui.components.NewsSectionTitle
import com.cedrickgd.morgenbriefing.ui.components.NoticeBanner
import com.cedrickgd.morgenbriefing.ui.components.PageFooter
import com.cedrickgd.morgenbriefing.ui.components.WarningBanner
import com.cedrickgd.morgenbriefing.ui.glass.TopGlassBar
import com.cedrickgd.morgenbriefing.ui.glass.glassContent
import com.cedrickgd.morgenbriefing.ui.news.ChipRowHeight
import com.cedrickgd.morgenbriefing.ui.news.DiscoverInvite
import com.cedrickgd.morgenbriefing.ui.news.EmptySectionNote
import com.cedrickgd.morgenbriefing.ui.news.EndAction
import com.cedrickgd.morgenbriefing.ui.news.FeedActions
import com.cedrickgd.morgenbriefing.ui.news.FeedArticle
import com.cedrickgd.morgenbriefing.ui.news.FeedChipRow
import com.cedrickgd.morgenbriefing.ui.news.FeedFilter
import com.cedrickgd.morgenbriefing.ui.news.FeedGutter
import com.cedrickgd.morgenbriefing.ui.news.FeedHints
import com.cedrickgd.morgenbriefing.ui.news.FeedViewIntro
import com.cedrickgd.morgenbriefing.ui.news.ListEndNote
import com.cedrickgd.morgenbriefing.ui.news.ListTail
import com.cedrickgd.morgenbriefing.ui.news.LoadOlderRow
import com.cedrickgd.morgenbriefing.ui.news.LoadingMoreRow
import com.cedrickgd.morgenbriefing.ui.news.NewsFeed
import com.cedrickgd.morgenbriefing.ui.news.NowListening
import com.cedrickgd.morgenbriefing.ui.news.SectionUi
import com.cedrickgd.morgenbriefing.ui.news.TopicsSheet
import com.cedrickgd.morgenbriefing.ui.news.VERIFY_LOW_WATER
import com.cedrickgd.morgenbriefing.ui.news.articleItems
import com.cedrickgd.morgenbriefing.ui.news.canUnfollow
import com.cedrickgd.morgenbriefing.ui.news.chipsParked
import com.cedrickgd.morgenbriefing.ui.news.endAction
import com.cedrickgd.morgenbriefing.ui.news.feedChips
import com.cedrickgd.morgenbriefing.ui.news.fetchedLabel
import com.cedrickgd.morgenbriefing.ui.news.followSuggestion
import com.cedrickgd.morgenbriefing.ui.news.forYouItems
import com.cedrickgd.morgenbriefing.ui.news.invitesToDiscover
import com.cedrickgd.morgenbriefing.ui.news.listTail
import com.cedrickgd.morgenbriefing.ui.news.rememberFeedActions
import com.cedrickgd.morgenbriefing.ui.news.rememberListenController
import com.cedrickgd.morgenbriefing.ui.news.rememberListeningKey
import com.cedrickgd.morgenbriefing.ui.news.resolveFilter
import com.cedrickgd.morgenbriefing.ui.news.revealedCount
import com.cedrickgd.morgenbriefing.ui.news.say
import com.cedrickgd.morgenbriefing.ui.news.sectionFor
import com.cedrickgd.morgenbriefing.ui.news.suggestionItems
import com.cedrickgd.morgenbriefing.ui.onboarding.OnboardingSheet
import com.cedrickgd.morgenbriefing.ui.shell.LocalSettingsLink
import com.cedrickgd.morgenbriefing.ui.shell.SettingsTarget
import com.cedrickgd.morgenbriefing.ui.shell.reportChromeScroll
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

/** Key of the chip row in the list. */
private const val CHIPS_KEY = "today-chips"

/** Height of the TopGlassBar below the status bar (docs/DESIGN.md §4). */
private val TopBarHeight = 52.dp

/** Scrolled this far into the header, the TopGlassBar slides in. */
private val HeaderGoneAfter = 96.dp

/** The list must rest this long before the articles on screen are warmed. */
private const val PREFETCH_SETTLE_MS = 350L

/**
 * Page "Heute" (docs/DESIGN.md §7): transparent over the AmbientSky. Header directly on the
 * sky (with search and discover buttons), the weather as glass cards, then the news: title, the
 * chip row ("Für dich", "Neueste", the followed topics) — which the frosted top bar takes in
 * once it reaches it — and the editorial feed. A topic chip scrolls endlessly: first through
 * everything loaded, then older articles are fetched page by page.
 *
 * Articles open in the reader ([onOpenArticle]); the articles on screen are warmed ahead in the
 * reader's cache (as the prefetch setting allows) and the warming stops when the page is left.
 *
 * @param contentPadding top is 0 (the page handles the status bar); bottom leaves room for the
 *   floating chrome and the navigation bar.
 * @param openTodaySignal changes on a notification tap — scrolls back to the top.
 * @param onOpenSearch opens the search screen.
 * @param onOpenDiscover opens the discover screen, at [categoryId]'s detail when not null.
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
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onboarding by viewModel.onboarding.collectAsStateWithLifecycle()
    val images = viewModel.articleImages.collectAsStateWithLifecycle()
    val statuses = viewModel.articleStatus.collectAsStateWithLifecycle()
    val hints = remember(images, statuses) { FeedHints(images, statuses) }
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
        onSetTopicVisible = viewModel::setTopicVisible,
        onSetTopicOrder = viewModel::setTopicOrder,
        onSetSourceEnabled = viewModel::setSourceEnabled,
        onFollow = viewModel::follow,
        onUnfollow = viewModel::unfollow,
        onLoadMore = viewModel::loadMore,
        onPrefetch = viewModel::prefetch,
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
    val onSetTopicVisible: (String, Boolean) -> Unit,
    val onSetTopicOrder: (List<String>) -> Unit,
    val onSetSourceEnabled: (String, Boolean) -> Unit,
    val onFollow: (String, Int?) -> Unit,
    val onUnfollow: (String) -> Unit,
    val onLoadMore: (String) -> Unit,
    val onPrefetch: (List<NewsItem>) -> Unit,
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
    val barHeightPx = with(density) { (statusTop + TopBarHeight).roundToPx() }
    val headerGonePx = with(density) { HeaderGoneAfter.roundToPx() }
    val bottomInset = bottomContentInset(contentPadding)
    val pullState = rememberPullToRefreshState()
    // Landscape and tablets: the feed stays a column, pictures do not grow with the window.
    val pageSide = pageSideInset()
    val snackbar = remember { SnackbarHostState() }

    val weather = state.weather
    val today = state.today
    val now = state.now
    val prefs = state.prefs
    val feed = state.feed
    val chips = remember(feed, strings) { feedChips(feed, strings) }
    var selectedKey by rememberSaveable { mutableStateOf(FeedFilter.ForYou.key) }
    val selected = remember(selectedKey, chips) { resolveFilter(FeedFilter.fromKey(selectedKey), chips) }
    val listening by rememberListeningKey(rememberListenController())
    var showTopics by rememberSaveable { mutableStateOf(false) }
    val showImages = prefs?.showImages ?: true

    // The chip row: in the list until it reaches the top bar, then carried by the bar.
    val chipsShown = feed != null && feed.sections.isNotEmpty()
    val chipIndex = if (chipsShown) chipRowIndex(weather, state.weatherCards) else -1
    val chipRowState = rememberLazyListState()
    val chipsParked by remember(listState, barHeightPx, chipIndex) {
        derivedStateOf { chipsParked(listState, chipIndex, barHeightPx) }
    }
    val parked = chipsShown && chipsParked

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
    )
    val scrollToFeedTop: () -> Unit = {
        scope.launch {
            val target = (chipIndex - 1).coerceAtLeast(0)
            if (reduceMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
        }
    }
    val onSelect: (FeedFilter) -> Unit = { filter ->
        // Always remember the tap: after an unfollowed topic fell back to "Für dich", the stored
        // key still names that topic, and tapping "Für dich" must replace it.
        selectedKey = filter.key
        if (filter != selected) {
            // Switching while the chips ride in the bar: start the new view right below them.
            if (parked && chipIndex >= 0) {
                scope.launch {
                    listState.scrollToItem(chipIndex)
                    listState.scrollBy(-barHeightPx.toFloat())
                }
            }
        }
    }
    val chipRow: @Composable (onGlass: Boolean) -> Unit = { onGlass ->
        FeedChipRow(
            chips = chips,
            selected = selected,
            onSelect = onSelect,
            onOpenTopics = { showTopics = true },
            rowState = chipRowState,
            onGlass = onGlass,
        )
    }

    // Endless lists ("Neueste", a topic): reveal more whenever the end comes into view; once a
    // topic's loaded articles are all on screen, ask for older ones.
    var pages by rememberSaveable(selectedKey) { mutableIntStateOf(1) }
    val topicId = (selected as? FeedFilter.OneTopic)?.topicId
    val total = when (selected) {
        FeedFilter.Latest -> feed?.latest?.size ?: 0
        is FeedFilter.OneTopic -> feed?.sectionFor(selected.topicId)?.count ?: 0
        FeedFilter.ForYou -> 0
    }
    val revealed = revealedCount(pages, total)
    val canLoadMore = topicId != null && state.canLoadMore(topicId)
    val loadingMore = topicId != null && topicId in state.loadingMore
    val stalled = topicId != null && topicId in state.stalled
    // "Nur vollständige Artikel": candidates still to check below, and all candidates of the list.
    val waiting = when (selected) {
        FeedFilter.Latest -> feed?.latestWaiting?.size ?: 0
        is FeedFilter.OneTopic -> feed?.sectionFor(selected.topicId)?.waiting?.size ?: 0
        FeedFilter.ForYou -> 0
    }
    val candidates = when (selected) {
        FeedFilter.Latest -> feed?.latestCandidates ?: 0
        is FeedFilter.OneTopic -> feed?.sectionFor(selected.topicId)?.candidates ?: 0
        FeedFilter.ForYou -> 0
    }
    val lowOnCandidates = waiting < VERIFY_LOW_WATER
    val currentCallbacks by rememberUpdatedState(callbacks)
    LaunchedEffect(listState, revealed, total, canLoadMore, loadingMore, stalled, topicId, lowOnCandidates, candidates > 0) {
        val action = endAction(revealed, total, canLoadMore, loadingMore, stalled, waiting, candidates)
        if (action == EndAction.NONE) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.filter { (last, count) -> count > 0 && last >= count - REVEAL_AHEAD }.first()
        when (action) {
            EndAction.REVEAL -> pages++
            EndAction.LOAD_MORE -> topicId?.let(currentCallbacks.onLoadMore)
            EndAction.NONE -> Unit
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
    val headline = weather?.headline?.takeIf { it.isNotBlank() } ?: if (state.weatherLoading) strings.headlineLoading else strings.headlineFallback
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "header", contentType = "header") {
                    TodayTop(
                        date = today,
                        greeting = greeting,
                        headline = headline,
                        onSearch = callbacks.onOpenSearch,
                        onDiscover = { callbacks.onOpenDiscover(null) },
                        modifier = Modifier
                            .padding(start = 20.dp, end = FeedGutter)
                            .padding(top = statusTop + 12.dp, bottom = 4.dp),
                    )
                }

                when {
                    weather != null -> weatherItems(weather, state.weatherCards)
                    showSkeleton -> item(key = "skeleton", contentType = "skeleton") {
                        LoadingSkeleton(Modifier.animateItem().padding(horizontal = FeedGutter))
                    }
                    else -> item(key = "weather-error", contentType = "info") {
                        InfoCard(
                            emoji = "🌦️",
                            title = strings.weatherUnavailableTitle,
                            text = strings.weatherUnavailableText,
                            modifier = Modifier.animateItem().padding(horizontal = FeedGutter),
                            actionLabel = common.retry,
                            onAction = callbacks.onRetryWeather,
                        )
                    }
                }

                if (feed == null) {
                    if (!showSkeleton) item(key = "news-loading", contentType = "loading") { NewsLoadingRow(strings.loadingNews) }
                } else {
                    item(key = "news-title", contentType = "news-title") {
                        NewsSectionTitle(
                            stand = fetchedLabel(feed.fetchedAt),
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = FeedGutter)
                                .padding(top = 20.dp),
                        )
                    }
                    if (chipsShown) {
                        item(key = CHIPS_KEY, contentType = "chips") {
                            // Parked: the bar shows the row; this spot keeps its room in the list.
                            if (parked) Spacer(Modifier.height(ChipRowHeight)) else chipRow(false)
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
                                modifier = Modifier.animateItem().padding(horizontal = FeedGutter),
                            )
                        }
                    }
                    if (invite && prefs != null) {
                        item(key = "discover-invite", contentType = "invite") {
                            DiscoverInvite(
                                preview = prefs.suggestions,
                                onDiscover = { callbacks.onOpenDiscover(null) },
                                modifier = Modifier.animateItem().padding(horizontal = FeedGutter),
                            )
                        }
                    }
                    if (chipsShown && prefs != null) {
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
                            onShowAll = { section -> onSelect(FeedFilter.OneTopic(section.id)) },
                            onOpenDiscover = callbacks.onOpenDiscover,
                            onLoadOlder = { topicId?.let(callbacks.onLoadMore) },
                            onBackToTop = scrollToFeedTop,
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

        val headerGone by remember(listState) {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > headerGonePx }
        }
        TopGlassBar(
            title = strings.barTitle(Fmt.shortDate(today, language)),
            visible = headerGone || parked,
            onTap = {
                scope.launch { if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0) }
            },
            bottom = if (parked) {
                {
                    Column(Modifier.horizontalSafeDrawing().padding(horizontal = pageSide)) {
                        chipRow(true)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            } else {
                null
            },
            actions = { TodayBarActions(onSearch = callbacks.onOpenSearch, onDiscover = { callbacks.onOpenDiscover(null) }) },
        )

        GlassSnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + 8.dp),
        )
    }

    if (showTopics && prefs != null) {
        TopicsSheet(
            prefs = prefs,
            counts = remember(feed) { feed?.sections?.associate { it.id to it.count }.orEmpty() },
            onSetVisible = { id, visible -> callbacks.onSetTopicVisible(id, visible) },
            onSetOrder = callbacks.onSetTopicOrder,
            onOpenSettings = {
                showTopics = false
                callbacks.onOpenSettingsAt(SettingsTarget.SOURCES)
            },
            onOpenDiscover = {
                showTopics = false
                callbacks.onOpenDiscover(null)
            },
            onDismiss = { showTopics = false },
        )
    }
}

/** How close to the end (in items) the next page of an endless list is revealed. */
private const val REVEAL_AHEAD = 4

/**
 * List index of the chip row: header, the weather items (or their skeleton / error card) and the
 * news title come before it. Must mirror the item order of [TodayContent].
 */
private fun chipRowIndex(weather: WeatherUi?, cards: Set<WeatherCard>): Int {
    val weatherItems = when {
        weather != null -> 1 +
            (if (WeatherCard.WARNINGS in cards && weather.warnings.isNotEmpty()) 1 else 0) +
            (if (WeatherCard.CURVE in cards) 1 else 0) +
            (if (WeatherCard.CLOTHING in cards && weather.clothing != null) 1 else 0)
        else -> 1 // skeleton or error card
    }
    return 1 + weatherItems + 1
}

/** The articles of the current view, top to bottom, as far as they are revealed (for prefetching). */
internal fun viewItems(selected: FeedFilter, feed: NewsFeed, revealed: Int): List<NewsItem> = when (selected) {
    FeedFilter.ForYou -> feed.sections.flatMap { it.top }
    FeedFilter.Latest -> feed.latest.take(revealed).map { it.item }
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

private fun LazyListScope.weatherItems(weather: WeatherUi, cards: Set<WeatherCard>) {
    item(key = "hero", contentType = "hero") {
        HomeWeatherHero(snapshot = weather.snapshot, modifier = Modifier.animateItem().padding(horizontal = FeedGutter))
    }
    if (WeatherCard.WARNINGS in cards && weather.warnings.isNotEmpty()) {
        item(key = "warnings", contentType = "warnings") {
            WarningBanner(warnings = weather.warnings, modifier = Modifier.animateItem().padding(horizontal = FeedGutter))
        }
    }
    if (WeatherCard.CURVE in cards) {
        item(key = "curve", contentType = "curve") {
            DayCurveCard(snapshot = weather.snapshot, modifier = Modifier.animateItem().padding(horizontal = FeedGutter))
        }
    }
    if (WeatherCard.CLOTHING in cards) {
        weather.clothing?.let { advice ->
            item(key = "clothing", contentType = "clothing") {
                ClothingCard(advice = advice, modifier = Modifier.animateItem().padding(horizontal = FeedGutter))
            }
        }
    }
}

/** What the feed below the chips shows right now. */
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

/** The feed below the chips, depending on the selected chip. */
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
        }

        FeedFilter.Latest -> {
            item(key = "latest-intro", contentType = "feed-intro") {
                FeedViewIntro(
                    overline = strings.latestOverline,
                    title = strings.latestTitle,
                    modifier = Modifier.animateItem().padding(horizontal = FeedGutter).padding(top = 4.dp),
                )
            }
            if (feed.latest.isEmpty()) {
                item(key = "latest-empty", contentType = "feed-empty") {
                    val loading = view.loadingTopics.isNotEmpty()
                    val checking = feed.latestWaiting.isNotEmpty()
                    EmptySectionNote(
                        text = when {
                            loading -> strings.loadingArticles
                            checking && view.verifyOffline -> strings.verifyOffline
                            checking -> strings.verifying
                            feed.verifying && feed.latestCandidates > 0 -> strings.noneComplete
                            else -> strings.noArticlesYet
                        },
                        loading = loading || (checking && !view.verifyOffline),
                        modifier = Modifier.animateItem().padding(horizontal = FeedGutter),
                    )
                }
            } else {
                articleItems(
                    articles = feed.latest.take(view.revealed),
                    now = view.now,
                    listening = view.listening,
                    actions = actions,
                    hints = hints,
                    showImages = view.showImages,
                    keyPrefix = "latest",
                    showTopic = true,
                )
                tailItems(view.tail, feed.latest.size, "latest", strings, onLoadOlder, onBackToTop)
            }
        }

        // The selected chip already names the topic; the intro adds its icon, count, stand and description.
        is FeedFilter.OneTopic -> {
            val section = feed.sectionFor(selected.topicId) ?: return
            item(key = "topic-${section.id}-intro", contentType = "feed-intro") {
                TopicIntro(section, feed, strings, onOpenDiscover, Modifier.animateItem().padding(horizontal = FeedGutter).padding(top = 4.dp))
            }
            if (section.isEmpty) {
                item(key = "topic-${section.id}-empty", contentType = "feed-empty") {
                    val loading = section.id in view.loadingTopics
                    val checking = section.waiting.isNotEmpty()
                    EmptySectionNote(
                        text = when {
                            loading -> strings.loadingArticles
                            checking && view.verifyOffline -> strings.verifyOffline
                            checking -> strings.verifying
                            section.noneComplete -> strings.noneComplete
                            section.failedSources.isNotEmpty() -> strings.unreachable(section.failedSources.joinToString(", "))
                            else -> strings.nothingHereRightNow
                        },
                        loading = loading || (checking && !view.verifyOffline),
                        modifier = Modifier.animateItem().padding(horizontal = FeedGutter),
                    )
                }
            } else {
                articleItems(
                    articles = section.all.take(view.revealed).map { FeedArticle(it, section.topic) },
                    now = view.now,
                    listening = view.listening,
                    actions = actions,
                    hints = hints,
                    showImages = view.showImages,
                    keyPrefix = "topic-${section.id}",
                    showTopic = false,
                )
                tailItems(view.tail, section.count, "topic-${section.id}", strings, onLoadOlder, onBackToTop)
            }
        }
    }
}

/**
 * The intro of a topic's own list: its tile, "37 Meldungen · Stand 07:12", the title and its
 * description, and a way into the discover screen — a category's sub-topics, or the category a
 * sub-topic belongs to.
 */
@Composable
private fun TopicIntro(
    section: SectionUi,
    feed: NewsFeed,
    strings: FeedStrings,
    onOpenDiscover: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val language = LocalAppLanguage.current
    val topic = section.topic
    val parent = remember(topic.parentId, language) { topic.parentId?.let { Topics.builtIn(it, language) } }
    val stand = fetchedLabel(feed.fetchedAt).takeIf { it.isNotBlank() }?.let(strings::stand)
    val browseId = if (section.browsable) topic.id else parent?.id
    val browseTitle = if (section.browsable) topic.title else parent?.title
    FeedViewIntro(
        overline = listOfNotNull(strings.articleCount(section.count), stand).joinToString(" · "),
        title = topic.title,
        topic = topic,
        subtitle = section.subtitle,
        modifier = modifier,
        action = if (browseId != null && browseTitle != null) {
            {
                GlassPillButton(
                    text = strings.subTopicsOf(browseTitle),
                    onClick = { onOpenDiscover(browseId) },
                    icon = Icons.Rounded.Explore,
                )
            }
        } else {
            null
        },
    )
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
        ListTail.VERIFY_OFFLINE -> item(key = "$keyPrefix-verify-offline", contentType = "feed-empty") {
            EmptySectionNote(text = strings.verifyOffline, loading = false, modifier = Modifier.animateItem().padding(horizontal = FeedGutter))
        }
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

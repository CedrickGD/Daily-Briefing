package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cedrickgd.morgenbriefing.data.model.AccentId
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.EffectsMode
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.ThemeMode
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.data.model.WeatherSnapshot
import com.cedrickgd.morgenbriefing.perf.TierAssessment
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.SettingsStrings
import com.cedrickgd.morgenbriefing.i18n.settingsStrings
import com.cedrickgd.morgenbriefing.notify.MorningWorker
import com.cedrickgd.morgenbriefing.ui.glass.PageTitle
import com.cedrickgd.morgenbriefing.ui.glass.TopGlassBar
import com.cedrickgd.morgenbriefing.ui.glass.glassContent
import com.cedrickgd.morgenbriefing.ui.shell.LocalSettingsLink
import com.cedrickgd.morgenbriefing.ui.shell.SettingsTarget
import com.cedrickgd.morgenbriefing.ui.shell.reportChromeScroll
import com.cedrickgd.morgenbriefing.ui.theme.rememberReduceMotion
import com.cedrickgd.morgenbriefing.util.KeepListEndAboveChrome
import com.cedrickgd.morgenbriefing.util.SystemIntents
import com.cedrickgd.morgenbriefing.util.SystemStatus
import com.cedrickgd.morgenbriefing.util.bottomContentInset
import com.cedrickgd.morgenbriefing.util.horizontalSafeDrawing
import com.cedrickgd.morgenbriefing.util.pageSideInset
import com.cedrickgd.morgenbriefing.util.rememberLinkActions
import com.cedrickgd.morgenbriefing.util.rememberNotificationPermissionRequester
import com.cedrickgd.morgenbriefing.util.statusBarTop
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Dialogs and sheets of the settings page; saveable so they survive rotation. */
private enum class SettingsDialog { TIME, HOME_PLACE, DAY_PLACE, LOCAL_PLACE, EDITION, ADD_FEED, NEW_TOPIC, EDIT_TOPIC, DELETE_TOPIC, REGION }

/** Lazy-list keys of the page, in display order. */
private object Block {
    const val TITLE = "title"
    const val LOADING = "loading"
    const val HERO = "hero"
    const val H_LANGUAGE = "h-language"
    const val LANGUAGE = "language"
    const val LOCAL = "local"
    const val H_BRIEFING = "h-briefing"
    const val BRIEFING = "briefing"
    const val H_TOPICS = "h-topics"
    const val TOPICS = "topics"
    const val H_SOURCES = "h-sources"
    const val SOURCES = "sources"
    const val H_FEED = "h-feed"
    const val FEED = "feed"
    const val H_LOOK = "h-look"
    const val LOOK = "look"
    const val H_PLACES = "h-places"
    const val PLACES = "places"
    const val WEATHER_CARDS = "weather-cards"
    const val H_APP = "h-app"
    const val APP = "app"
    const val UPDATE = "update"
    const val LINKS = "links"
    const val FOOTER = "footer"

    val ALL = listOf(
        TITLE, HERO, H_LANGUAGE, LANGUAGE, LOCAL, H_BRIEFING, BRIEFING, H_TOPICS, TOPICS, H_SOURCES, SOURCES,
        H_FEED, FEED, H_LOOK, LOOK, H_PLACES, PLACES, WEATHER_CARDS, H_APP, APP, UPDATE, LINKS, FOOTER,
    )
}

/**
 * Page "Einstellungen" (docs/DESIGN.md §4, §7): transparent over the sky, page title under the
 * status bar and a frosted top bar once it scrolled away. Sections: status hero, Sprache &
 * Region (app language, news edition, Bundesland, local news), Morgenbriefing, Deine Themen,
 * Quellen, Feed, Darstellung, Orte & Zeitfenster (with the weather blocks), App. Every change is
 * saved immediately; every text follows the interface language.
 *
 * @param onOpenDiscover opens the "Themen entdecken" screen (categories, sub-topics, places).
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenDiscover: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
    updateViewModel: UpdateViewModel = viewModel(factory = UpdateViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val placeSearch by viewModel.placeSearch.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val addFeed by viewModel.addFeed.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val weatherLoading by viewModel.weatherLoading.collectAsStateWithLifecycle()
    val sourceStatus by viewModel.sourceStatus.collectAsStateWithLifecycle()
    val blocked by viewModel.blockedDomains.collectAsStateWithLifecycle()
    val deviceTier by viewModel.deviceTier.collectAsStateWithLifecycle()
    val update by updateViewModel.state.collectAsStateWithLifecycle()

    val strings = settingsStrings()
    val language = LocalAppLanguage.current
    val latestLanguage by rememberUpdatedState(language)
    val context = LocalContext.current
    val links = rememberLinkActions()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()
    val stagger = rememberStagger(enabled = !reduceMotion)
    val now = rememberMinuteClock()
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    // The topic a dialog is about (its id), saveable like the dialog itself.
    var dialogTopicId by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedSources by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val density = LocalDensity.current
    val topBarPx = with(density) { (statusBarTop() + TOP_BAR_HEIGHT).toPx() }
    val titleThresholdPx = with(density) { TITLE_SCROLL_THRESHOLD.toPx() }
    val showTopBar by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > titleThresholdPx }
    }

    // The user fixes permissions in the system settings and comes back: re-check on resume.
    var status by remember { mutableStateOf(SystemStatus.read(context)) }
    LifecycleResumeEffect(context, updateViewModel, viewModel) {
        status = SystemStatus.read(context)
        updateViewModel.onResume()
        viewModel.onResume()
        onPauseOrDispose { updateViewModel.onPause() }
    }
    val requestNotifications = rememberNotificationPermissionRequester {
        status = SystemStatus.read(context)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it.text(latestLanguage)) }
    }

    KeepListEndAboveChrome(listState, bottomContentInset(contentPadding))

    fun scrollTo(key: String) {
        val index = Block.ALL.indexOf(key)
        if (index < 0) return
        scope.launch {
            val offset = -topBarPx.toInt()
            if (reduceMotion) listState.scrollToItem(index, offset) else listState.animateScrollToItem(index, offset)
        }
    }

    fun openPlacePicker(target: PlaceTarget) {
        viewModel.resetPlaceSearch()
        dialog = when (target) {
            PlaceTarget.HOME -> SettingsDialog.HOME_PLACE
            PlaceTarget.DAY -> SettingsDialog.DAY_PLACE
            PlaceTarget.LOCAL -> SettingsDialog.LOCAL_PLACE
        }
    }

    fun openAddFeed(topicId: String) {
        viewModel.resetAddFeed()
        dialogTopicId = topicId
        dialog = SettingsDialog.ADD_FEED
    }

    // Sent here from another screen (onboarding, the topic sheet): glide to the right section
    // once the page is on screen, and open its dialog.
    val settingsLink = LocalSettingsLink.current
    val ready = settings != null
    LaunchedEffect(settingsLink, ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow { settingsLink.pending }.filterNotNull().collect { target ->
            delay(LINK_SETTLE_MS)
            settingsLink.consume(target)
            when (target) {
                SettingsTarget.HOME_PLACE -> {
                    scrollTo(Block.H_PLACES)
                    openPlacePicker(PlaceTarget.HOME)
                }
                SettingsTarget.TOPICS -> scrollTo(Block.H_TOPICS)
                SettingsTarget.SOURCES -> scrollTo(Block.H_SOURCES)
            }
        }
    }

    val onTestNotification: () -> Unit = {
        if (!status.canNotify) {
            scope.launch {
                val result = snackbar.showSnackbar(
                    message = strings.testNeedsPermission,
                    actionLabel = strings.allow,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) requestNotifications()
            }
        } else {
            val queued = try {
                MorningWorker.enqueue(context.applicationContext, force = true)
                true
            } catch (e: IllegalStateException) {
                false
            }
            scope.launch { snackbar.showSnackbar(if (queued) strings.testQueued else strings.testFailed) }
        }
    }

    val onPill: (StatusItem) -> Unit = { item ->
        when (item.level) {
            StatusLevel.ATTENTION -> when (item.kind) {
                StatusKind.NOTIFICATIONS -> requestNotifications()
                StatusKind.EXACT_ALARM -> SystemIntents.requestExactAlarms(context)
                StatusKind.BATTERY -> SystemIntents.requestIgnoreBatteryOptimizations(context)
            }
            StatusLevel.NEUTRAL, StatusLevel.OK -> scrollTo(Block.H_BRIEFING)
        }
    }

    val updateActions = UpdateCardActions(
        onCheck = updateViewModel::check,
        onDownload = updateViewModel::downloadAndInstall,
        onCancel = updateViewModel::cancel,
        onInstall = { ready -> updateViewModel.install(ready.release, ready.apk) },
        onAllowInstall = {
            if (!updateViewModel.openInstallPermission()) {
                scope.launch { snackbar.showSnackbar(strings.settingOpenFailed) }
            }
        },
        onDismissError = updateViewModel::dismissError,
    )

    val sourcesActions = SourcesActions(
        onSourceEnabled = viewModel::setSourceEnabled,
        onEnableAll = viewModel::enableAllSources,
        onAddFeed = { topic -> openAddFeed(topic.id) },
        onRemoveFeed = viewModel::removeCustomFeed,
        onEditTopic = { topic ->
            dialogTopicId = topic.id
            dialog = SettingsDialog.EDIT_TOPIC
        },
        onDeleteTopic = { topic ->
            dialogTopicId = topic.id
            dialog = SettingsDialog.DELETE_TOPIC
        },
    )

    val topicsActions = TopicsActions(
        onOpenDiscover = onOpenDiscover,
        onUnfollow = viewModel::unfollow,
        onFollow = viewModel::follow,
        onReorder = viewModel::reorderFollowed,
        onAddTopic = {
            dialogTopicId = null
            dialog = SettingsDialog.NEW_TOPIC
        },
        onEditTopic = { topic ->
            dialogTopicId = topic.id
            dialog = SettingsDialog.EDIT_TOPIC
        },
    )

    val feedActions = FeedCardActions(
        onSectionSize = viewModel::setSectionSize,
        onNewsAge = viewModel::setNewsAge,
        onShowImages = viewModel::setShowImages,
        onReadInApp = viewModel::setReadInApp,
        onReadableOnly = viewModel::setReadableOnly,
        onPrefetch = viewModel::setPrefetch,
    )

    val current = settings
    val keys = if (current == null) listOf(Block.TITLE, Block.LOADING) else Block.ALL

    // After process death the saved position points into the real blocks, and measuring the two
    // loading items first would clamp it to their end: until the settings are known, the list
    // runs on a state of its own and the saved one is attached with the real blocks.
    val loadingListState = remember { LazyListState() }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = if (current == null) loadingListState else listState,
            modifier = Modifier
                .fillMaxSize()
                // Edge-to-edge, the window no longer shrinks for the keyboard: the list does.
                .imePadding()
                .reportChromeScroll()
                // The floating glass above (bar, indicator, player) blurs this content.
                .glassContent()
                .horizontalSafeDrawing(),
            // Landscape and tablets: the cards stay a centred column.
            contentPadding = PaddingValues(
                start = 16.dp + pageSideInset(),
                end = 16.dp + pageSideInset(),
                bottom = bottomContentInset(contentPadding) + 32.dp,
            ),
        ) {
            items(keys, key = { it }, contentType = { if (it.startsWith("h-")) "heading" else it }) { key ->
                val index = keys.indexOf(key)
                val enter = Modifier
                    .fillMaxWidth()
                    .staggerIn(stagger, key, index)
                if (key == Block.TITLE) {
                    PageTitle(overline = strings.pageOverline, title = strings.pageTitle, modifier = enter.padding(horizontal = 4.dp))
                    return@items
                }
                if (current == null) {
                    HeroPlaceholder(enter.padding(top = 16.dp))
                    return@items
                }
                SettingsBlock(
                    key = key,
                    settings = current,
                    modifier = enter,
                    content = BlockContent(
                        status = status,
                        now = now,
                        weather = weather,
                        weatherLoading = weatherLoading,
                        update = update,
                        reduceMotion = reduceMotion,
                        expandedSources = expandedSources,
                        sourceStatus = sourceStatus,
                        blocked = blocked,
                        deviceTier = deviceTier,
                    ),
                    callbacks = BlockCallbacks(
                        onPill = onPill,
                        onOpenAppDetails = { SystemIntents.openAppDetails(context) },
                        onLanguage = viewModel::setUiLanguage,
                        onOpenEditions = { dialog = SettingsDialog.EDITION },
                        onPickRegion = { dialog = SettingsDialog.REGION },
                        onAddLocalPlace = { openPlacePicker(PlaceTarget.LOCAL) },
                        onRemoveLocalPlace = viewModel::removeLocalPlace,
                        onNotificationsToggle = { enabled ->
                            viewModel.setNotificationsEnabled(enabled)
                            if (enabled && !status.canNotify) requestNotifications()
                        },
                        onEditTime = { dialog = SettingsDialog.TIME },
                        onTestNotification = onTestNotification,
                        onThemeMode = viewModel::setThemeMode,
                        onAccent = viewModel::setAccent,
                        onEffects = viewModel::setEffects,
                        topics = topicsActions,
                        sources = sourcesActions,
                        onToggleSourcesExpanded = { id -> expandedSources = if (id in expandedSources) expandedSources - id else expandedSources + id },
                        feed = feedActions,
                        onWeatherCard = viewModel::setWeatherCard,
                        onEditPlace = ::openPlacePicker,
                        onDayWindow = viewModel::setDayWindow,
                        update = updateActions,
                        onOpenLink = links::open,
                    ),
                    listState = listState,
                    topObstruction = topBarPx,
                )
            }
        }
        TopGlassBar(
            title = strings.pageTitle,
            visible = showTopBar,
            onTap = { scope.launch { if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0) } },
        )
        GlassSnackbarHost(
            state = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .horizontalSafeDrawing()
                .padding(bottom = bottomContentInset(contentPadding)),
        )
    }

    current ?: return
    val dialogTopic = dialogTopicId?.let { Topics.resolve(it, current) }
    when (dialog) {
        SettingsDialog.TIME -> NotifyTimeDialog(
            hour = current.notifyHour,
            minute = current.notifyMinute,
            onDismiss = { dialog = null },
            onConfirm = { hour, minute ->
                dialog = null
                viewModel.setNotifyTime(hour, minute)
            },
        )

        SettingsDialog.HOME_PLACE, SettingsDialog.DAY_PLACE, SettingsDialog.LOCAL_PLACE -> {
            val target = when (dialog) {
                SettingsDialog.HOME_PLACE -> PlaceTarget.HOME
                SettingsDialog.DAY_PLACE -> PlaceTarget.DAY
                else -> PlaceTarget.LOCAL
            }
            PlacePickerSheet(
                target = target,
                current = when (target) {
                    PlaceTarget.HOME -> current.home
                    PlaceTarget.DAY -> current.day
                    PlaceTarget.LOCAL -> null
                },
                germanEdition = EditionChange.showsRegionPresets(current.edition),
                search = placeSearch,
                location = location,
                onQueryChange = viewModel::onPlaceQueryChange,
                onUseLocation = {
                    viewModel.useCurrentLocation(needsTown = target == PlaceTarget.LOCAL) { place ->
                        dialog = null
                        viewModel.setPlace(target, place)
                    }
                },
                onPick = { place ->
                    dialog = null
                    viewModel.setPlace(target, place)
                },
                onDismiss = {
                    dialog = null
                    // Also stops a running "Mein Standort" lookup, so the place cannot change later.
                    viewModel.resetPlaceSearch()
                },
            )
        }

        SettingsDialog.EDITION -> EditionPickerSheet(
            current = current.edition,
            onPick = { edition ->
                dialog = null
                viewModel.setEdition(edition)
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.ADD_FEED -> dialogTopic?.let { topic ->
            AddFeedDialog(
                topic = topic,
                state = addFeed,
                onLookUp = viewModel::lookUpFeed,
                onSave = { name -> viewModel.saveFoundFeed(topic.id, name) },
                onDismiss = {
                    dialog = null
                    viewModel.resetAddFeed()
                },
            )
        }

        SettingsDialog.NEW_TOPIC -> CustomTopicDialog(
            topic = null,
            onConfirm = { draft ->
                dialog = null
                viewModel.addCustomTopic(draft) { id ->
                    // A feed topic is empty until it has a feed: ask for the first one right away.
                    if (draft.content == TopicContent.FEEDS) openAddFeed(id)
                }
            },
            onDismiss = { dialog = null },
        )

        SettingsDialog.EDIT_TOPIC -> dialogTopic?.let { topic ->
            CustomTopicDialog(
                topic = topic,
                onConfirm = { draft ->
                    dialog = null
                    viewModel.updateCustomTopic(topic.id, draft)
                },
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.DELETE_TOPIC -> dialogTopic?.let { topic ->
            DeleteTopicDialog(
                topic = topic,
                onConfirm = {
                    dialog = null
                    expandedSources = expandedSources - topic.id
                    viewModel.deleteCustomTopic(topic.id)
                },
                onDismiss = { dialog = null },
            )
        }

        SettingsDialog.REGION -> RegionPickerDialog(
            current = current.region,
            onPick = { region ->
                dialog = null
                viewModel.setRegion(region.key)
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

/** Values every block may need, bundled to keep [SettingsBlock]'s signature readable. */
private class BlockContent(
    val status: SystemStatus,
    val now: ZonedDateTime,
    val weather: WeatherSnapshot?,
    val weatherLoading: Boolean,
    val update: UpdatePanelState,
    val reduceMotion: Boolean,
    val expandedSources: Set<String>,
    val sourceStatus: Map<String, SourceStatus>,
    val blocked: Set<String>,
    val deviceTier: TierAssessment?,
)

/** Callbacks of the blocks. */
private class BlockCallbacks(
    val onPill: (StatusItem) -> Unit,
    val onOpenAppDetails: () -> Unit,
    val onLanguage: (AppLanguage?) -> Unit,
    val onOpenEditions: () -> Unit,
    val onPickRegion: () -> Unit,
    val onAddLocalPlace: () -> Unit,
    val onRemoveLocalPlace: (String) -> Unit,
    val onNotificationsToggle: (Boolean) -> Unit,
    val onEditTime: () -> Unit,
    val onTestNotification: () -> Unit,
    val onThemeMode: (ThemeMode) -> Unit,
    val onAccent: (AccentId) -> Unit,
    val onEffects: (EffectsMode) -> Unit,
    val topics: TopicsActions,
    val sources: SourcesActions,
    val onToggleSourcesExpanded: (String) -> Unit,
    val feed: FeedCardActions,
    val onWeatherCard: (WeatherCard, Boolean) -> Unit,
    val onEditPlace: (PlaceTarget) -> Unit,
    val onDayWindow: (Int, Int) -> Unit,
    val update: UpdateCardActions,
    val onOpenLink: (String) -> Unit,
)

/** One entry of the settings list. */
@Composable
private fun SettingsBlock(
    key: String,
    settings: AppSettings,
    modifier: Modifier,
    content: BlockContent,
    callbacks: BlockCallbacks,
    listState: LazyListState,
    topObstruction: Float,
) {
    val strings = settingsStrings()
    val lang = LocalAppLanguage.current
    when (key) {
        Block.HERO -> {
            val items = remember(content.status, lang) { statusItems(content.status, lang) }
            val next = nextBriefingAt(content.now, settings.notifyHour, settings.notifyMinute)
            val hero = heroText(
                enabled = settings.notificationsEnabled,
                items = items,
                day = dayWord(content.now, next, lang),
                time = clockLabel(settings.notifyHour, settings.notifyMinute),
                lang = lang,
            )
            StatusHero(
                hero = hero,
                enabled = settings.notificationsEnabled,
                items = items,
                reduceMotion = content.reduceMotion,
                onPill = callbacks.onPill,
                onOpenAppDetails = callbacks.onOpenAppDetails,
                modifier = modifier.padding(top = 16.dp),
            )
        }
        Block.H_LANGUAGE -> Heading(strings, key, modifier)
        Block.LANGUAGE -> LanguageEditionCard(
            settings = settings,
            onLanguage = callbacks.onLanguage,
            onOpenEditions = callbacks.onOpenEditions,
            onPickRegion = callbacks.onPickRegion,
            modifier = modifier,
        )
        Block.LOCAL -> LocalNewsCard(
            settings = settings,
            onAddPlace = callbacks.onAddLocalPlace,
            onRemovePlace = callbacks.onRemoveLocalPlace,
            onFollowPlace = callbacks.topics.onFollow,
            reduceMotion = content.reduceMotion,
            modifier = modifier.padding(top = 14.dp),
        )
        Block.H_BRIEFING -> Heading(strings, key, modifier)
        Block.BRIEFING -> BriefingCard(
            settings = settings,
            now = content.now,
            reduceMotion = content.reduceMotion,
            onToggle = callbacks.onNotificationsToggle,
            onEditTime = callbacks.onEditTime,
            onTest = callbacks.onTestNotification,
            modifier = modifier,
        )
        Block.H_TOPICS -> Heading(strings, key, modifier)
        Block.TOPICS -> FollowedTopicsPanel(
            settings = settings,
            reduceMotion = content.reduceMotion,
            listState = listState,
            listItemKey = Block.TOPICS,
            topObstruction = topObstruction,
            actions = callbacks.topics,
            modifier = modifier,
        )
        Block.H_SOURCES -> Heading(strings, key, modifier)
        Block.SOURCES -> SourcesSection(
            settings = settings,
            blocked = content.blocked,
            status = content.sourceStatus,
            expanded = content.expandedSources,
            onToggleExpanded = callbacks.onToggleSourcesExpanded,
            actions = callbacks.sources,
            modifier = modifier,
        )
        Block.H_FEED -> Heading(strings, key, modifier)
        Block.FEED -> FeedCard(settings = settings, actions = callbacks.feed, modifier = modifier)
        Block.H_LOOK -> Heading(strings, key, modifier)
        Block.LOOK -> AppearanceCard(
            themeMode = settings.themeMode,
            accent = settings.accent,
            effects = settings.effects,
            deviceTier = content.deviceTier,
            onThemeMode = callbacks.onThemeMode,
            onAccent = callbacks.onAccent,
            onEffects = callbacks.onEffects,
            modifier = modifier,
        )
        Block.H_PLACES -> Heading(strings, key, modifier)
        Block.PLACES -> {
            val (home, day) = remember(content.weather, settings.home, settings.day, content.now, lang) {
                placeWeather(content.weather, settings.home, settings.day, content.now, lang)
            }
            PlacesSection(
                settings = settings,
                homeWeather = home,
                dayWeather = day,
                weatherLoading = content.weatherLoading,
                onEditPlace = callbacks.onEditPlace,
                onDayWindow = callbacks.onDayWindow,
                modifier = modifier,
            )
        }
        Block.WEATHER_CARDS -> WeatherCardsCard(
            settings = settings,
            onToggle = callbacks.onWeatherCard,
            modifier = modifier.padding(top = 14.dp),
        )
        Block.H_APP -> Heading(strings, key, modifier)
        Block.APP -> AppHeaderCard(installedVersion = content.update.installedVersion, modifier = modifier)
        Block.UPDATE -> UpdateCard(
            state = content.update,
            reduceMotion = content.reduceMotion,
            actions = callbacks.update,
            modifier = modifier.padding(top = 14.dp),
        )
        Block.LINKS -> LinksCard(onOpen = callbacks.onOpenLink, modifier = modifier.padding(top = 14.dp))
        Block.FOOTER -> SettingsFooter(modifier)
    }
}

/** The overline + title of the section that starts at [key]. */
@Composable
private fun Heading(strings: SettingsStrings, key: String, modifier: Modifier) {
    val (overline, title) = when (key) {
        Block.H_LANGUAGE -> strings.languageOverline to strings.languageTitle
        Block.H_BRIEFING -> strings.briefingOverline to strings.briefingTitle
        Block.H_TOPICS -> strings.topicsOverline to strings.topicsTitle
        Block.H_SOURCES -> strings.sourcesOverline to strings.sourcesTitle
        Block.H_FEED -> strings.feedOverline to strings.feedTitle
        Block.H_LOOK -> strings.lookOverline to strings.lookTitle
        Block.H_PLACES -> strings.placesOverline to strings.placesTitle
        else -> strings.appOverline to strings.appTitle
    }
    SectionHeading(overline, title, modifier)
}

private val TOP_BAR_HEIGHT = 52.dp

/** Lets the pager glide to this page before a deep link scrolls and opens a dialog. */
private const val LINK_SETTLE_MS = 380L
private val TITLE_SCROLL_THRESHOLD = 72.dp

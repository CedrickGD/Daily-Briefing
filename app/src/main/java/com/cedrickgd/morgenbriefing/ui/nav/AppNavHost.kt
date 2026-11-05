package com.cedrickgd.morgenbriefing.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.cedrickgd.morgenbriefing.ui.article.ArticleScreen
import com.cedrickgd.morgenbriefing.ui.discover.DiscoverScreen
import com.cedrickgd.morgenbriefing.ui.search.SearchScreen
import com.cedrickgd.morgenbriefing.ui.shell.LocalSettingsLink
import com.cedrickgd.morgenbriefing.ui.shell.MainPage
import com.cedrickgd.morgenbriefing.ui.shell.MainPager
import com.cedrickgd.morgenbriefing.ui.shell.SettingsTarget
import com.cedrickgd.morgenbriefing.ui.shell.ShellNavigator
import com.cedrickgd.morgenbriefing.ui.today.TodayViewModel

/**
 * The navigation graph: [MainRoute] (the page pager) is the start destination. Pushed over it:
 * the article reader ([ArticleRoute], covers the shared sky with its own opaque page),
 * "Themen entdecken" ([DiscoverRoute]) and the search ([SearchRoute]) – both glass over the
 * sky, so the sky keeps living behind them. Every pushed screen follows the predictive back
 * gesture (its pop transition is seeked by the gesture). Their back arrows close only their own
 * entry ([ShellNavigator.back]); articles open through [ShellNavigator.openArticle], which also
 * honours "Artikel in der App lesen".
 *
 * @param pagePadding bottom room for the floating chrome, for the pager pages.
 * @param pushedPadding bottom room for pushed screens (navigation bar, mini player).
 * @param openTodaySignal increments whenever "Heute" should scroll back to the top.
 */
@Composable
internal fun AppNavHost(
    navController: NavHostController,
    navigator: ShellNavigator,
    pagePadding: PaddingValues,
    pushedPadding: PaddingValues,
    openTodaySignal: Int,
    todayViewModel: TodayViewModel,
    modifier: Modifier = Modifier,
) {
    val settingsLink = LocalSettingsLink.current
    NavHost(
        navController = navController,
        startDestination = MainRoute,
        modifier = modifier,
    ) {
        composable<MainRoute> { entry ->
            val top by navController.currentBackStackEntryAsState()
            MainPager(
                navigator = navigator,
                entry = entry,
                onTop = top?.isMain() ?: true,
                contentPadding = pagePadding,
                openTodaySignal = openTodaySignal,
                todayViewModel = todayViewModel,
            )
        }
        composable<ArticleRoute>(
            enterTransition = { slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 3 } + fadeIn() },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { slideOutHorizontally(spring(stiffness = Spring.StiffnessMedium)) { it / 3 } + fadeOut() },
        ) { entry ->
            val route = entry.toRoute<ArticleRoute>()
            ArticleScreen(route = route, onBack = { navigator.back(entry) })
        }
        composable<DiscoverRoute>(
            enterTransition = { pushedEnter() },
            exitTransition = { fadeOut(spring(stiffness = Spring.StiffnessMedium)) },
            popEnterTransition = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) },
            popExitTransition = { pushedPopExit() },
        ) { entry ->
            DiscoverScreen(
                route = entry.toRoute<DiscoverRoute>(),
                contentPadding = pushedPadding,
                onBack = { navigator.back(entry) },
                onOpenArticle = { item -> navigator.openArticle(entry, item) },
                onOpenSearch = { query -> navigator.openSearch(query) },
                onCreateCustomTopic = {
                    settingsLink.request(SettingsTarget.TOPICS)
                    navigator.showPage(MainPage.SETTINGS)
                },
            )
        }
        composable<SearchRoute>(
            enterTransition = { pushedEnter() },
            exitTransition = { fadeOut(spring(stiffness = Spring.StiffnessMedium)) },
            popEnterTransition = { fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) },
            popExitTransition = { pushedPopExit() },
        ) { entry ->
            SearchScreen(
                route = entry.toRoute<SearchRoute>(),
                contentPadding = pushedPadding,
                onBack = { navigator.back(entry) },
                onOpenArticle = { item -> navigator.openArticle(entry, item) },
                onOpenDiscover = { navigator.openDiscover() },
            )
        }
    }
}

/** Glass screens rise softly out of the page (a little scale, a fade) – the sky stays put. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushedEnter(): EnterTransition =
    scaleIn(spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.94f) +
        fadeIn(spring(stiffness = Spring.StiffnessMediumLow))

/** …and sink back into it; with predictive back this follows the finger. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushedPopExit(): ExitTransition =
    scaleOut(spring(stiffness = Spring.StiffnessMedium), targetScale = 0.92f) +
        fadeOut(spring(stiffness = Spring.StiffnessMedium))

internal fun NavBackStackEntry.isMain(): Boolean = destination.hasRoute<MainRoute>()

/** The screen on top hides the sky (its own opaque surface): the article reader. */
internal fun NavBackStackEntry.coversSky(): Boolean = destination.hasRoute<ArticleRoute>()

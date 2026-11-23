package com.cedrickgd.morgenbriefing.ui.shell

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.ui.nav.ArticleHandoff
import com.cedrickgd.morgenbriefing.ui.nav.ArticleRoute
import com.cedrickgd.morgenbriefing.ui.nav.DiscoverRoute
import com.cedrickgd.morgenbriefing.ui.nav.FavoritesRoute
import com.cedrickgd.morgenbriefing.ui.nav.HistoryRoute
import com.cedrickgd.morgenbriefing.ui.nav.MainRoute
import com.cedrickgd.morgenbriefing.ui.nav.SearchRoute
import com.cedrickgd.morgenbriefing.ui.nav.isMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A request to show one of the main pages, e.g. from a notification tap (`AppLinks`).
 * [id] keeps two identical requests in a row distinct.
 */
@Immutable
data class PageRequest(val page: MainPage, val id: Long)

/** Spring for programmatic page changes (indicator tap, back, links) – docs/DESIGN.md §6. */
internal val PageSpring: AnimationSpec<Float> = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)

/**
 * The shell's navigation actions: switching pager pages and pushing the screens that lie over
 * the pager (article reader, "Themen entdecken", search). Event handlers only – nothing here is
 * read during composition.
 *
 * Every way into an article goes through [openArticle], so "Artikel in der App lesen" is decided
 * in one place: [readInApp] says whether the reader opens, otherwise [openInBrowser] gets the link
 * – and the story counts as read ([onReadInBrowser]; the reader notes its own).
 */
@Stable
class ShellNavigator internal constructor(
    private val navController: NavHostController,
    val pagerState: PagerState,
    private val chrome: ChromeController,
    private val scope: CoroutineScope,
    private val onTodayRequested: () -> Unit,
    private val readInApp: () -> Boolean,
    private val openInBrowser: (url: String) -> Unit,
    private val onReadInBrowser: (NewsItem) -> Unit = {},
) {
    /** No screen is pushed over the pager right now. */
    val isMainOnTop: Boolean
        get() = navController.currentBackStackEntry?.isMain() ?: true

    /**
     * Shows [page]: animates the pager when it is visible, otherwise closes any pushed screen
     * and lets the pager reappear on that page. [scrollTodayToTop] also scrolls "Heute" back up
     * (notification tap, tapping the already selected sun icon).
     */
    fun showPage(page: MainPage, scrollTodayToTop: Boolean = false) {
        chrome.show()
        if (page == MainPage.TODAY && scrollTodayToTop) onTodayRequested()
        if (isMainOnTop) {
            if (pagerState.currentPage != page.index || pagerState.currentPageOffsetFraction != 0f) {
                scope.launch { pagerState.animateScrollToPage(page.index, animationSpec = PageSpring) }
            }
        } else {
            // The pager is not composed while covered; this only records the position.
            pagerState.requestScrollToPage(page.index)
            navController.popBackStack<MainRoute>(inclusive = false)
        }
    }

    /**
     * Opens [item] in the article reader over the pager – or in the browser when the user reads
     * articles there. [from] is the entry the tap came from: a tap while that entry is already
     * leaving (double tap, back in flight) is ignored.
     */
    fun openArticle(from: NavBackStackEntry, item: NewsItem) {
        if (!from.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (!readInApp()) {
            openInBrowser(item.url)
            onReadInBrowser(item)
            return
        }
        // Items the repository does not keep (search results) reach the reader this way.
        ArticleHandoff.put(item)
        navController.navigate(ArticleRoute(url = item.url, itemId = item.id, topicId = item.topicId))
    }

    /**
     * Opens "Themen entdecken" over the pager: the category grid, or the category [categoryId]
     * directly. A second tap while it is already open does nothing; coming from the search that
     * was opened from discover goes back instead of stacking (see [NavPlan]).
     */
    fun openDiscover(categoryId: String? = null) = open(DiscoverRoute(categoryId))

    /** Opens "Themen entdecken" with its place search open, to follow a place's local news. */
    fun openPlaceSearch() = open(DiscoverRoute(addPlace = true))

    /** Opens the news search over the pager, running [query] at once when given (see [NavPlan]). */
    fun openSearch(query: String? = null) = open(SearchRoute(query?.trim()?.ifEmpty { null }))

    /** Opens "Gelesen & ausgeblendet", the archive of read and hidden stories, over the pager. */
    fun openHistory() = open(HistoryRoute)

    /** Opens "Favoriten", the starred stories, over the pager. */
    fun openFavorites() = open(FavoritesRoute)

    /**
     * Closes [from], the pushed screen whose back arrow was tapped. Only while it is still the
     * screen on top: a second tap during its exit animation would otherwise close the screen
     * below as well – or pop the pager itself and leave an empty window.
     */
    fun back(from: NavBackStackEntry) {
        if (from.isMain() || navController.currentBackStackEntry?.id != from.id) return
        navController.popBackStack()
    }

    private inline fun <reified T : Any> open(target: T) {
        chrome.show()
        val top = navController.currentBackStackEntry?.routeOrNull<T>()
            ?: navController.currentBackStackEntry?.takeIf { it.isMain() }?.let { MainRoute }
        val below = try {
            navController.getBackStackEntry<T>().toRoute<T>()
        } catch (e: IllegalArgumentException) {
            null
        }
        when (NavPlan.of(top, below, target)) {
            NavPlan.IGNORE -> Unit
            NavPlan.RETURN -> navController.popBackStack<T>(inclusive = false)
            NavPlan.PUSH -> navController.navigate(target) {
                popUpTo<T> { inclusive = true }
            }
        }
    }

    private inline fun <reified T : Any> NavBackStackEntry.routeOrNull(): T? =
        if (destination.hasRoute<T>()) toRoute<T>() else null

    /**
     * Opens the article at [url] (the mini player) unless it is already on top – in the browser
     * when the user reads articles there.
     */
    fun openArticle(url: String) {
        if (!readInApp()) {
            openInBrowser(url)
            return
        }
        val top = navController.currentBackStackEntry ?: return
        if (top.destination.hasRoute<ArticleRoute>() && top.toRoute<ArticleRoute>().url == url) return
        navController.navigate(ArticleRoute(url = url))
    }

    /** Handles a [PageRequest] (always lands on the page; "Heute" also scrolls to the top). */
    fun handle(request: PageRequest) = showPage(request.page, scrollTodayToTop = request.page == MainPage.TODAY)
}

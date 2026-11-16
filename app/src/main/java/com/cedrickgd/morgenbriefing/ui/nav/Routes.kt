package com.cedrickgd.morgenbriefing.ui.nav

import kotlinx.serialization.Serializable

/**
 * Start destination: the swipeable pages Heute → Einstellungen (docs/DESIGN.md §3).
 * Which page is shown lives in the shell's pager state, not in the route.
 */
@Serializable
data object MainRoute

/**
 * The article reader, pushed over the pager: the article at [url]. [itemId] and [topicId] find
 * the feed item (headline, teaser, picture) when the feed still has it; the mini player passes
 * only the URL.
 */
@Serializable
data class ArticleRoute(val url: String, val itemId: String? = null, val topicId: String? = null)

/**
 * "Themen entdecken", pushed over the pager: the category grid, or straight the category
 * [categoryId] (a built-in category id such as `wirtschaft`). The screen switches between grid
 * and category itself; the route only says where it opens. [addPlace] opens it with the place
 * search of its "Lokal" part ("Ort hinzufügen" in "Deine Themen").
 */
@Serializable
data class DiscoverRoute(val categoryId: String? = null, val addPlace: Boolean = false)

/** The news search, pushed over the pager; [query] runs at once (a followed search, a link). */
@Serializable
data class SearchRoute(val query: String? = null)

/**
 * "Gelesen & ausgeblendet", pushed over the pager: every story the user read or hid, newest
 * first, to open, share or bring back into the feed.
 */
@Serializable
data object HistoryRoute

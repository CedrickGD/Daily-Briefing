package com.cedrickgd.morgenbriefing.ui.news

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cedrickgd.morgenbriefing.MorgenbriefingApp
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.listen.ListenController
import com.cedrickgd.morgenbriefing.util.rememberLinkActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "Feed"

/** The app's "Zuhören" controller, or null in previews (no MorgenbriefingApp there). */
@Composable
internal fun rememberListenController(): ListenController? {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as? MorgenbriefingApp)?.listen }
}

/** The article loaded in the player: its `NewsItem.key` and whether it is paused. */
@Immutable
internal data class NowListening(val key: String, val paused: Boolean)

/** True while the article [key] is being read (not paused). */
internal fun NowListening?.reading(key: String): Boolean = this != null && this.key == key && !paused

/** True while the article [key] is loaded but paused. */
internal fun NowListening?.pausedOn(key: String): Boolean = this != null && this.key == key && paused

/**
 * The article being read aloud (or paused) right now, else null. Changes only when that article
 * or its paused state changes — the reading progress does not recompose the feed.
 */
@Composable
internal fun rememberListeningKey(listen: ListenController?): State<NowListening?> {
    val flow: Flow<NowListening?> = remember(listen) {
        val state = try {
            listen?.state
        } catch (e: Exception) {
            Log.w(TAG, "Listen state unavailable", e)
            null
        }
        state?.map { s -> s.articleKey?.takeIf { s.speaking || s.paused }?.let { NowListening(it, paused = s.paused) } }
            ?.distinctUntilChanged() ?: flowOf(null)
    }
    return flow.collectAsStateWithLifecycle(initialValue = null)
}

/**
 * [FeedActions] for a screen: opening (in-app reader or browser), sharing, listening and copying
 * are handled here; following, unfollowing and hiding a source are the screen's. Null callbacks
 * hide those menu items.
 *
 * @param onMessage shows a short message (snackbar), e.g. "Link kopiert".
 * @param followSuggestion the topic the ⋮ menu offers to follow for an article (always the newest lambda is used).
 * @param canUnfollowTopic false for a topic the menus must not offer to unfollow (the last one).
 */
@Composable
internal fun rememberFeedActions(
    onMessage: (String) -> Unit,
    onHideSource: ((FeedArticle) -> Unit)?,
    onOpenArticle: ((FeedArticle) -> Unit)? = null,
    onUnfollowTopic: ((Topic) -> Unit)? = null,
    onFollowTopic: ((Topic) -> Unit)? = null,
    followSuggestion: (FeedArticle) -> Topic? = { null },
    canUnfollowTopic: (Topic) -> Boolean = { true },
): FeedActions {
    val context = LocalContext.current
    val links = rememberLinkActions()
    val listen = rememberListenController()
    val strings = feedStrings()
    val currentStrings by rememberUpdatedState(strings)
    val message by rememberUpdatedState(onMessage)
    val hideSource by rememberUpdatedState(onHideSource)
    val openArticle by rememberUpdatedState(onOpenArticle)
    val unfollow by rememberUpdatedState(onUnfollowTopic)
    val follow by rememberUpdatedState(onFollowTopic)
    val suggestion by rememberUpdatedState(followSuggestion)
    val mayUnfollow by rememberUpdatedState(canUnfollowTopic)
    val canHideSource = onHideSource != null
    val readsInApp = onOpenArticle != null
    val canUnfollow = onUnfollowTopic != null
    val canFollow = onFollowTopic != null
    return remember(context, links, listen, canHideSource, readsInApp, canUnfollow, canFollow) {
        FeedActions(
            onOpen = { article -> openArticle?.invoke(article) ?: links.open(article.item.url) },
            onOpenExternal = { links.open(it.item.url) },
            onListen = { article ->
                try {
                    listen?.play(article.item) ?: message(currentStrings.listenUnavailable)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start reading aloud", e)
                    message(currentStrings.listenFailed)
                }
            },
            onStopListening = {
                try {
                    listen?.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not stop reading aloud", e)
                }
            },
            onShare = { links.shareArticle(it.item) },
            onCopyLink = { article ->
                val copied = copyLink(context, article.item.headline.ifBlank { currentStrings.clipLabel }, article.item.url)
                // Android 13+ confirms copying itself; only older versions get our message.
                if (!copied) {
                    message(currentStrings.copyFailed)
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    message(currentStrings.linkCopied)
                }
            },
            onHideSource = if (canHideSource) { article -> hideSource?.invoke(article) } else null,
            onUnfollowTopic = if (canUnfollow) { topic -> unfollow?.invoke(topic) } else null,
            onFollowTopic = if (canFollow) { topic -> follow?.invoke(topic) } else null,
            followSuggestion = { article -> suggestion(article) },
            canUnfollowTopic = { topic -> mayUnfollow(topic) },
        )
    }
}

/** Puts [url] on the clipboard with [label]; false when there is nothing to copy or the system refused. */
internal fun copyLink(context: Context, label: String, url: String): Boolean {
    if (url.isBlank()) return false
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    return try {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, url.trim()))
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "Clipboard refused", e)
        false
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Clipboard unavailable", e)
        false
    }
}

/** Shows [text] as the only snackbar; runs [onAction] when its action is tapped. */
internal fun SnackbarHostState.say(scope: CoroutineScope, text: String, action: String? = null, onAction: () -> Unit = {}) {
    scope.launch {
        currentSnackbarData?.dismiss()
        val result = showSnackbar(
            message = text,
            actionLabel = action,
            withDismissAction = false,
            duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onAction()
    }
}

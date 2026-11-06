package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.components.GlassSheet
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes

/**
 * "Deine Themen" (docs/DESIGN.md §7), opened by the "＋" at the end of the feed tabs: the one
 * place on "Heute" to manage the topics. Every followed topic in the feed's order with its icon
 * tile and today's article count; the handle (or a long press on the row) drags it to a new
 * place, the order is stored when the finger lifts; ✕ stops following it (the last topic stays).
 * Below: "Themen entdecken" (the discover screen) and "Ort hinzufügen" (its place search).
 *
 * @param counts articles per topic id on screen.
 * @param onSetOrder stores a new order of topic ids.
 * @param onUnfollow stops following a topic (the screen offers to undo it).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YourTopicsSheet(
    topics: List<Topic>,
    counts: Map<String, Int>,
    onSetOrder: (List<String>) -> Unit,
    onUnfollow: (Topic) -> Unit,
    onOpenDiscover: () -> Unit,
    onAddPlace: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = feedStrings()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val stepPx = with(density) { (TopicRowHeight + TopicRowGap).toPx() }
    val gapPx = with(density) { TopicRowGap.toPx() }
    // Measured heights: a wrapped title or a large font makes a row taller than the minimum.
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    var order by remember(topics) { mutableStateOf(topics) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    fun moveBy(id: String, delta: Int) {
        val from = order.indexOfFirst { it.id == id }
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from < 0 || from == to) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    val dragCallbacks = DragCallbacks(
        onStart = { id ->
            dragging = id
            dragOffset = 0f
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onDrag = { id, dy ->
            dragOffset += dy
            // The distance to the neighbour in [direction]: its measured height plus the gap.
            fun pitch(direction: Int): Float {
                val neighbour = order.getOrNull(order.indexOfFirst { it.id == id } + direction) ?: return stepPx
                return (rowHeights[neighbour.id]?.toFloat() ?: (stepPx - gapPx)) + gapPx
            }
            // Swap with the neighbour once the row has travelled half of its height towards it.
            while (order.indexOfFirst { it.id == id } < order.lastIndex && dragOffset > pitch(1) / 2f) {
                dragOffset -= pitch(1)
                moveBy(id, 1)
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            while (order.indexOfFirst { it.id == id } > 0 && dragOffset < -pitch(-1) / 2f) {
                dragOffset += pitch(-1)
                moveBy(id, -1)
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        },
        onEnd = {
            dragging = null
            dragOffset = 0f
            if (order.map { it.id } != topics.map { it.id }) onSetOrder(order.map { it.id })
        },
    )

    GlassSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 10.dp)) {
            Text(
                text = strings.yourTopicsTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = strings.yourTopicsHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState(), enabled = dragging == null)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(TopicRowGap),
        ) {
            val last = order.size <= 1
            order.forEachIndexed { index, topic ->
                key(topic.id) {
                    val lifted = dragging == topic.id
                    FollowedTopicRow(
                        topic = topic,
                        count = counts[topic.id] ?: 0,
                        removable = !last,
                        lifted = lifted,
                        offset = { if (lifted) dragOffset else 0f },
                        canMoveUp = index > 0,
                        canMoveDown = index < order.lastIndex,
                        onRemove = { onUnfollow(topic) },
                        onMoveBy = { delta ->
                            moveBy(topic.id, delta)
                            onSetOrder(order.map { it.id })
                        },
                        drag = dragCallbacks,
                        onHeight = { rowHeights[topic.id] = it },
                    )
                }
            }
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassPillButton(
                text = strings.discoverDescription,
                onClick = onOpenDiscover,
                icon = Icons.Rounded.Explore,
                filled = true,
                modifier = Modifier.weight(1f),
            )
            GlassPillButton(
                text = strings.addPlace,
                onClick = onAddPlace,
                icon = Icons.Rounded.AddLocationAlt,
            )
        }
    }
}

/** Drag-to-sort events of the topic rows. */
private class DragCallbacks(
    val onStart: (id: String) -> Unit,
    val onDrag: (id: String, dy: Float) -> Unit,
    val onEnd: () -> Unit,
)

private val TopicRowHeight = 60.dp
private val TopicRowGap = 4.dp

/**
 * A followed topic: drag handle, icon tile, title and article count, ✕. A long press anywhere on
 * the row also lifts it; TalkBack moves it with the "nach oben / nach unten" actions.
 *
 * @param removable false for the last followed topic: ✕ is off and the row says why.
 */
@Composable
private fun FollowedTopicRow(
    topic: Topic,
    count: Int,
    removable: Boolean,
    lifted: Boolean,
    offset: () -> Float,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRemove: () -> Unit,
    onMoveBy: (Int) -> Unit,
    drag: DragCallbacks,
    /** Reports the row's measured height, so dragging knows how far the neighbours really are. */
    onHeight: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val strings = feedStrings()
    // The gesture coroutines outlive recompositions: always talk to the newest callbacks.
    val currentDrag by rememberUpdatedState(drag)
    val dragGesture = Modifier.pointerInput(topic.id) {
        detectDragGestures(
            onDragStart = { currentDrag.onStart(topic.id) },
            onDragEnd = { currentDrag.onEnd() },
            onDragCancel = { currentDrag.onEnd() },
            onDrag = { change, amount ->
                change.consume()
                currentDrag.onDrag(topic.id, amount.y)
            },
        )
    }
    val longPressDrag = Modifier.pointerInput(topic.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { currentDrag.onStart(topic.id) },
            onDragEnd = { currentDrag.onEnd() },
            onDragCancel = { currentDrag.onEnd() },
            onDrag = { change, amount ->
                change.consume()
                currentDrag.onDrag(topic.id, amount.y)
            },
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                translationY = offset()
                val scale = if (lifted) 1.02f else 1f
                scaleX = scale
                scaleY = scale
                shadowElevation = if (lifted) 12.dp.toPx() else 0f
                shape = GlassShapes.chip
                clip = true
            }
            .heightIn(min = TopicRowHeight)
            .background(if (lifted) colors.surfaceContainerHigh else colors.surface.copy(alpha = 0f))
            .then(longPressDrag)
            .semantics(mergeDescendants = true) {
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(strings.moveUp(topic.title)) { onMoveBy(-1); true })
                    if (canMoveDown) add(CustomAccessibilityAction(strings.moveDown(topic.title)) { onMoveBy(1); true })
                }
            }
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(dragGesture)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.DragIndicator, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        TopicTile(topic, diameter = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            Text(
                text = when {
                    !removable -> strings.lastTopicStays
                    count > 0 -> strings.articleCount(count)
                    else -> strings.noArticlesRightNow
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove, enabled = removable) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = strings.unfollowTopic(topic.title),
                tint = if (removable) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
    }
}

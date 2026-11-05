package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cedrickgd.morgenbriefing.data.SettingsOps
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.sourcesStrings
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import com.cedrickgd.morgenbriefing.ui.news.TopicBadge
import com.cedrickgd.morgenbriefing.ui.news.rememberTopicColors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Callbacks of "Deine Themen". */
internal class TopicsActions(
    val onOpenDiscover: () -> Unit,
    val onUnfollow: (id: String) -> Unit,
    val onFollow: (id: String) -> Unit,
    val onReorder: (order: List<String>) -> Unit,
    val onAddTopic: () -> Unit,
    val onEditTopic: (Topic) -> Unit,
)

/**
 * Settings "Deine Themen": a lively "Themen entdecken" banner (the discover screen), the
 * followed topics in feed order – icon tile in the topic color, title, one line on what it
 * covers, drag handle to sort (a long press on the row works too), ✕ to stop following – the own
 * topics and places that are paused, and "Eigenes Thema".
 *
 * @param listState the page's list, scrolled along when a lifted row nears its edges.
 * @param listItemKey this panel's key in that list.
 * @param topObstruction pixels of the page covered by the floating top bar.
 */
@Composable
internal fun FollowedTopicsPanel(
    settings: AppSettings,
    reduceMotion: Boolean,
    listState: LazyListState,
    listItemKey: Any,
    topObstruction: Float,
    actions: TopicsActions,
    modifier: Modifier = Modifier,
) {
    val strings = sourcesStrings()
    val lang = LocalAppLanguage.current
    val followed = remember(settings, lang) { FollowedTopics.followed(settings, lang) }
    val paused = remember(settings) { FollowedTopics.paused(settings) }
    val canRemove = followed.size > 1

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DiscoverBanner(onClick = actions.onOpenDiscover)
        GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp)) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                Text(strings.followingCount(followed.size), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = if (canRemove) strings.followedHint else strings.keepOne,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            FollowedList(
                topics = followed,
                settings = settings,
                canRemove = canRemove,
                reduceMotion = reduceMotion,
                listState = listState,
                listItemKey = listItemKey,
                topObstruction = topObstruction,
                actions = actions,
            )
            if (paused.isNotEmpty()) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 18.dp)) {
                    Overline(strings.paused)
                    Text(strings.pausedHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    paused.forEach { topic ->
                        key(topic.id) { PausedRow(topic, settings, onFollow = { actions.onFollow(topic.id) }) }
                    }
                }
            }
            Row(Modifier.padding(start = 8.dp, top = 14.dp)) {
                GlassPillButton(text = strings.customTopic, onClick = actions.onAddTopic, icon = Icons.Rounded.Add, style = PillStyle.Tonal)
            }
        }
    }
}

/** The way to the discover screen: an accent gradient banner with a compass. */
@Composable
private fun DiscoverBanner(onClick: () -> Unit) {
    val strings = sourcesStrings()
    val colors = MaterialTheme.colorScheme
    val dark = isGlassDark()
    val interaction = remember { MutableInteractionSource() }
    val shape = GlassShapes.card
    val start = if (dark) lerp(colors.primary, Color.Black, 0.35f) else colors.primary
    val end = if (dark) lerp(colors.tertiary, Color.Black, 0.45f) else lerp(colors.tertiary, colors.primary, 0.35f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(shape)
            .background(Brush.linearGradient(listOf(start, end)))
            .border(1.dp, Color.White.copy(alpha = if (dark) 0.12f else 0.35f), shape)
            .clickable(interactionSource = interaction, indication = ripple(color = Color.White), role = Role.Button, onClick = onClick)
            .heightIn(min = 88.dp)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f))
                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Explore, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = strings.discover,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.headlineSmall.fontFamily),
                color = Color.White,
            )
            Text(text = strings.discoverHint, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * Drag-to-sort state of the followed list. Positions are applied in the draw phase
 * (graphicsLayer), so dragging never recomposes the rows; neighbours glide out of the way.
 */
@Stable
private class FollowedDrag {
    var dragging by mutableStateOf<String?>(null)
    val offset = mutableFloatStateOf(0f)

    /** Measured row heights (px): a wrapped title or a large font makes a row taller. */
    val heights = mutableStateMapOf<String, Int>()

    /** Row tops inside the panel (px), for the auto-scroll at the page edges. */
    val tops = HashMap<String, Float>()
    val glides = HashMap<String, Animatable<Float, AnimationVector1D>>()

    fun glideOf(id: String): Animatable<Float, AnimationVector1D> = glides.getOrPut(id) { Animatable(0f) }
}

@Composable
private fun FollowedList(
    topics: List<Topic>,
    settings: AppSettings,
    canRemove: Boolean,
    reduceMotion: Boolean,
    listState: LazyListState,
    listItemKey: Any,
    topObstruction: Float,
    actions: TopicsActions,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val gapPx = with(density) { ROW_GAP.toPx() }
    val scope = rememberCoroutineScope()
    val stored = remember(topics) { topics.map { it.id } }
    val byId = remember(topics) { topics.associateBy { it.id } }
    var order by remember { mutableStateOf(stored) }
    val drag = remember { FollowedDrag() }
    LaunchedEffect(stored) { if (drag.dragging == null) order = stored }
    val latestOrder by rememberUpdatedState(order)
    val latestStored by rememberUpdatedState(stored)
    val latestReorder by rememberUpdatedState(actions.onReorder)
    var panel by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun heightOf(id: String): Float = drag.heights[id]?.toFloat() ?: with(density) { ROW_MIN_HEIGHT.toPx() }

    fun moveBy(id: String, distance: Float) {
        val before = latestOrder
        drag.offset.floatValue += distance
        val result = RowReorder.step(before, id, drag.offset.floatValue, ::heightOf, gapPx)
        if (result.order != before) {
            val pitch = heightOf(id) + gapPx
            result.order.forEachIndexed { newIndex, other ->
                val oldIndex = before.indexOf(other)
                if (other != id && oldIndex != newIndex) {
                    val glide = drag.glideOf(other)
                    // Undispatched: the jump back is applied before the next frame draws the new slot.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        glide.snapTo(glide.value + (oldIndex - newIndex) * pitch)
                        if (reduceMotion) glide.snapTo(0f) else glide.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                    }
                }
            }
            order = result.order
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        drag.offset.floatValue = result.offset
    }

    fun start(id: String) {
        drag.dragging = id
        drag.offset.floatValue = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun end() {
        val id = drag.dragging ?: return
        val released = drag.offset.floatValue
        drag.dragging = null
        drag.offset.floatValue = 0f
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
        val glide = drag.glideOf(id)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            glide.snapTo(released)
            if (reduceMotion) glide.snapTo(0f) else glide.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        }
        if (latestOrder != latestStored) latestReorder(latestOrder)
    }

    // A lifted row near the visible top/bottom of the page scrolls the page along.
    val dragging = drag.dragging
    val edge = with(density) { 88.dp.toPx() }
    val maxStep = with(density) { 12.dp.toPx() }
    LaunchedEffect(dragging) {
        val id = dragging ?: return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == listItemKey } ?: continue
            val rowTop = drag.tops[id] ?: continue
            val top = item.offset + rowTop + drag.offset.floatValue
            val bottom = top + heightOf(id)
            val viewportTop = info.viewportStartOffset + topObstruction
            val viewportBottom = (info.viewportEndOffset - info.afterContentPadding).toFloat()
            val moving = drag.offset.floatValue
            val step = when {
                moving > 0f && bottom > viewportBottom - edge -> maxStep * ((bottom - (viewportBottom - edge)) / edge).coerceIn(0.15f, 1f)
                moving < 0f && top < viewportTop + edge -> -maxStep * (((viewportTop + edge) - top) / edge).coerceIn(0.15f, 1f)
                else -> 0f
            }
            if (step != 0f) {
                val consumed = listState.scrollBy(step)
                if (consumed != 0f) moveBy(id, consumed)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { panel = it },
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        order.forEachIndexed { index, id ->
            val topic = byId[id] ?: return@forEachIndexed
            key(id) {
                val lifted = drag.dragging == id
                // Created here, not lazily in the gesture: the row's layer must read it from the start.
                val glide = remember(id) { drag.glideOf(id) }
                FollowedRow(
                    topic = topic,
                    subtitle = remember(topic, settings) { Topics.subtitle(topic, settings) },
                    lifted = lifted,
                    canRemove = canRemove && SettingsOps.canUnfollow(settings, id),
                    canMoveUp = index > 0,
                    canMoveDown = index < order.lastIndex,
                    translation = { if (lifted) drag.offset.floatValue else glide.value },
                    onMoveBy = { delta ->
                        val moved = TopicList.moveBy(latestOrder, id, delta)
                        if (moved != latestOrder) {
                            order = moved
                            latestReorder(moved)
                        }
                    },
                    onRemove = { actions.onUnfollow(id) },
                    onEdit = if (topic.custom) ({ actions.onEditTopic(topic) }) else null,
                    onDragStart = { start(id) },
                    onDrag = { dy -> moveBy(id, dy) },
                    onDragEnd = ::end,
                    modifier = Modifier
                        .onSizeChanged { drag.heights[id] = it.height }
                        .onGloballyPositioned { coords ->
                            val root = panel ?: return@onGloballyPositioned
                            if (root.isAttached && coords.isAttached) {
                                // The translation is in the draw phase, so this is the row's slot.
                                drag.tops[id] = root.localPositionOf(coords, Offset.Zero).y
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun FollowedRow(
    topic: Topic,
    subtitle: String,
    lifted: Boolean,
    canRemove: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    translation: () -> Float,
    onMoveBy: (Int) -> Unit,
    onRemove: () -> Unit,
    onEdit: (() -> Unit)?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = sourcesStrings()
    val colors = MaterialTheme.colorScheme
    val topicColors = rememberTopicColors(topic)
    val dark = isGlassDark()
    val lift by animateFloatAsState(if (lifted) 1f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "rowLift")
    // The gesture coroutines outlive recompositions: always talk to the newest callbacks.
    val latestStart by rememberUpdatedState(onDragStart)
    val latestDrag by rememberUpdatedState(onDrag)
    val latestEnd by rememberUpdatedState(onDragEnd)
    val shape = RoundedCornerShape(20.dp)
    val wash = topicColors.strong.copy(alpha = if (dark) 0.16f else 0.07f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (lifted) 2f else 0f)
            .graphicsLayer {
                translationY = translation()
                val scale = 1f + 0.02f * lift
                scaleX = scale
                scaleY = scale
                shadowElevation = 12.dp.toPx() * lift
                this.shape = shape
                clip = true
            }
            .background(if (lifted) colors.surfaceContainerHigh else Color.Transparent)
            .background(wash)
            .pointerInput(topic.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { latestStart() },
                    onDragEnd = { latestEnd() },
                    onDragCancel = { latestEnd() },
                    onDrag = { change, amount ->
                        change.consume()
                        latestDrag(amount.y)
                    },
                )
            }
            .then(if (onEdit != null) Modifier.clickable(role = Role.Button, onClickLabel = strings.edit, onClick = onEdit) else Modifier)
            .semantics(mergeDescendants = true) {
                customActions = buildList {
                    if (canMoveUp) add(CustomAccessibilityAction(strings.moveUp(topic.title)) { onMoveBy(-1); true })
                    if (canMoveDown) add(CustomAccessibilityAction(strings.moveDown(topic.title)) { onMoveBy(1); true })
                    if (canRemove) add(CustomAccessibilityAction(strings.removeTopic(topic.title)) { onRemove(); true })
                }
            }
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 48.dp)
                .pointerInput(topic.id) {
                    detectDragGestures(
                        onDragStart = { latestStart() },
                        onDragEnd = { latestEnd() },
                        onDragCancel = { latestEnd() },
                        onDrag = { change, amount ->
                            change.consume()
                            latestDrag(amount.y)
                        },
                    )
                }
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.DragIndicator, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        TopicBadge(topic, diameter = 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(topic.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = strings.removeTopic(topic.title),
                tint = colors.onSurfaceVariant.copy(alpha = if (canRemove) 1f else DISABLED_ALPHA),
            )
        }
    }
}

/** A paused own topic or place: muted, with "Folgen". */
@Composable
private fun PausedRow(topic: Topic, settings: AppSettings, onFollow: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lamina(RoundedCornerShape(20.dp))
            .heightIn(min = ROW_MIN_HEIGHT)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.graphicsLayer { alpha = 0.7f }) { TopicBadge(topic, diameter = 40.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(topic.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = remember(topic, settings) { Topics.subtitle(topic, settings) }
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        GlassPillButton(text = sourcesStrings().follow, onClick = onFollow, icon = Icons.Rounded.Add, style = PillStyle.Tonal)
    }
}

private val ROW_MIN_HEIGHT = 64.dp
private val ROW_GAP = 6.dp

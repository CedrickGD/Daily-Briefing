package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.glass.GlassLevel
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.glass

/** Width of the soft fade where the tabs run out at the right edge. */
private val TabFadeWidth: Dp = 28.dp

/** Corners of the search field. */
private val SearchFieldShape = RoundedCornerShape(16.dp)

/**
 * The feed bar of "Heute" (docs/DESIGN.md §7): a search field – a button that looks like one and
 * opens the search screen – with a round history button beside it ("Gelesen & ausgeblendet",
 * [onHistory]), and the tabs "Für dich", "Neueste", one per followed topic (with its icon) and a
 * round "＋" at the end that opens "Deine Themen". It sits in the page below the weather and pins
 * to the top once it reaches it (the page's top glass then carries it).
 *
 * @param rowState hoist it to keep the tabs' scroll position when the bar moves into the top glass.
 * @param onGlass the bar sits on the top glass: field and tabs get a quiet fill and rim instead
 *   of their own glass.
 */
@Composable
fun FeedBar(
    tabs: List<FeedTab>,
    selected: FeedFilter,
    onSelect: (FeedFilter) -> Unit,
    onSearch: () -> Unit,
    onManageTopics: () -> Unit,
    modifier: Modifier = Modifier,
    onHistory: (() -> Unit)? = null,
    rowState: LazyListState = rememberLazyListState(),
    onGlass: Boolean = false,
) {
    Column(modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
        Row(Modifier.padding(horizontal = FeedGutter), verticalAlignment = Alignment.CenterVertically) {
            SearchFieldButton(onClick = onSearch, onGlass = onGlass, modifier = Modifier.weight(1f))
            if (onHistory != null) {
                Spacer(Modifier.width(8.dp))
                HistoryButton(onClick = onHistory, onGlass = onGlass)
            }
        }
        Spacer(Modifier.height(6.dp))
        FeedTabRow(tabs = tabs, selected = selected, onSelect = onSelect, onManageTopics = onManageTopics, rowState = rowState, onGlass = onGlass)
    }
}

/** "🔍 Suchen – z. B. SLV-ETF, Midterms": looks like a field, opens the search screen. */
@Composable
private fun SearchFieldButton(onClick: () -> Unit, onGlass: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val strings = feedStrings()
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pressScale(interaction, pressedScale = 0.985f)
            .then(if (onGlass) Modifier.onGlassFill(SearchFieldShape) else Modifier.glass(shape = SearchFieldShape, level = GlassLevel.Thin))
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = strings.searchDescription,
                onClick = onClick,
            )
            .semantics { contentDescription = strings.searchDescription }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = strings.searchPlaceholder,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The button says what it does; the example queries are for the eye only.
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** The round button beside the search field: opens "Gelesen & ausgeblendet", the read and hidden stories. */
@Composable
private fun HistoryButton(onClick: () -> Unit, onGlass: Boolean) {
    val strings = feedStrings()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .pressScale(interaction, pressedScale = 0.92f)
            .then(if (onGlass) Modifier.onGlassFill(SearchFieldShape) else Modifier.glass(shape = SearchFieldShape, level = GlassLevel.Thin))
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = strings.openHistory,
                onClick = onClick,
            )
            .semantics { contentDescription = strings.openHistory },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

/**
 * The tabs, scrolling horizontally (they win over the page swipe until their end), the
 * selected one filled in its topic's (or the accent) color and always scrolled into view –
 * also when "Alle →" of a card selects it. The "＋" closes the row.
 *
 * Inner horizontal scrolling wins over the page swipe (docs/DESIGN.md §3): whatever the row
 * cannot use itself — the rest of a flick at its start or end — is swallowed instead of being
 * handed on to the page pager.
 */
@Composable
private fun FeedTabRow(
    tabs: List<FeedTab>,
    selected: FeedFilter,
    onSelect: (FeedFilter) -> Unit,
    onManageTopics: () -> Unit,
    rowState: LazyListState,
    onGlass: Boolean,
) {
    val strings = feedStrings()
    val selectedIndex = tabs.indexOfFirst { it.filter == selected }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex < 0) return@LaunchedEffect
        val info = rowState.layoutInfo
        val visible = info.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val fullyVisible = visible != null &&
            visible.offset >= info.viewportStartOffset &&
            visible.offset + visible.size <= info.viewportEndOffset - TAB_FADE_ROOM_PX
        if (!fullyVisible) rowState.animateScrollToItem(selectedIndex, scrollOffset = -TAB_SCROLL_MARGIN_PX)
    }
    LazyRow(
        state = rowState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .nestedScroll(KeepHorizontalScroll)
            // The tabs fade out softly at the right edge: more lie beyond.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = TabFadeWidth.toPx()
                if (size.width > fade && rowState.canScrollForward) {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Black,
                            (size.width - fade) / size.width to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .semantics { contentDescription = strings.filterRow },
        contentPadding = PaddingValues(horizontal = FeedGutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs, key = { it.key }) { tab ->
            val filter = tab.filter
            if (tab.kind == TabKind.ADD || filter == null) {
                AddTab(label = tab.label, onClick = onManageTopics, onGlass = onGlass)
            } else {
                FeedTabPill(tab = tab, selected = filter == selected, onGlass = onGlass, onClick = { onSelect(filter) })
            }
        }
    }
}

@Composable
private fun FeedTabPill(tab: FeedTab, selected: Boolean, onGlass: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val topicColors = tab.topic?.let { rememberTopicColors(it) }
    val selectedFill = topicColors?.strong ?: colors.primary
    val selectedText = if (tab.topic == null) colors.onPrimary else Color.White
    val fill by animateColorAsState(
        targetValue = if (selected) selectedFill else selectedFill.copy(alpha = 0f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabFill",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) selectedText else colors.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabText",
    )
    // Unselected, the icon keeps the topic's own color: the row reads as a palette of topics.
    val idleIcon = when {
        topicColors == null -> colors.primary
        isDarkTheme() -> topicColors.onContainer
        else -> topicColors.strong
    }
    val iconTint by animateColorAsState(
        targetValue = if (selected) selectedText else idleIcon,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabIcon",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 38.dp)
                .pressScale(interaction, pressedScale = 0.94f)
                .then(
                    when {
                        selected -> Modifier
                        onGlass -> Modifier.onGlassFill(CircleShape)
                        else -> Modifier.glass(shape = GlassShapes.pill, level = GlassLevel.Thin)
                    },
                )
                .background(fill, CircleShape)
                .indication(interaction, ripple())
                // Large fonts: the pill grows with its label instead of cutting it.
                .padding(start = 12.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (tab.kind) {
                TabKind.TOPIC -> tab.topic?.let { TopicGlyph(it, tint = iconTint, size = 17.dp) }
                TabKind.FOR_YOU -> Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
                TabKind.LATEST -> Icon(Icons.Rounded.Schedule, contentDescription = null, tint = iconTint, modifier = Modifier.size(17.dp))
                TabKind.ADD -> Unit
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The round "＋" at the end of the tabs: opens "Deine Themen". */
@Composable
private fun AddTab(label: String, onClick: () -> Unit, onGlass: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 38.dp)
                .pressScale(interaction, pressedScale = 0.9f)
                .then(if (onGlass) Modifier.onGlassFill(CircleShape) else Modifier.glass(shape = GlassShapes.pill, level = GlassLevel.Thin))
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Swallows the horizontal scroll and fling the tab row could not use itself (at its start or
 * end), so a flick back to "Für dich" never carries on into a page swipe.
 */
internal val KeepHorizontalScroll: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        Velocity(available.x, 0f)
}

/** A surface on the top glass: a quiet fill and a fine rim (a second blur would vanish into the bar). */
@Composable
private fun Modifier.onGlassFill(shape: Shape): Modifier {
    val ink = MaterialTheme.colorScheme.onSurface
    return this
        .clip(shape)
        .background(ink.copy(alpha = 0.06f))
        .border(1.dp, ink.copy(alpha = 0.14f), shape)
}

/**
 * True when the feed bar — the list item at [barIndex] — has reached the lower edge of the
 * status bar ([topPx] from the top of the list) or scrolled past it: from then on the page's top
 * glass carries the bar. Reads list state only; call it inside `derivedStateOf`.
 */
internal fun barParked(listState: LazyListState, barIndex: Int, topPx: Int): Boolean {
    if (barIndex < 0) return false
    val first = listState.firstVisibleItemIndex
    return when {
        first > barIndex -> true
        first == barIndex -> true // its top is at or above the list's top, far above the status bar's edge
        else -> listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == barIndex }
            ?.let { it.offset <= topPx }
            ?: false
    }
}

private const val TAB_FADE_ROOM_PX = 72
private const val TAB_SCROLL_MARGIN_PX = 40

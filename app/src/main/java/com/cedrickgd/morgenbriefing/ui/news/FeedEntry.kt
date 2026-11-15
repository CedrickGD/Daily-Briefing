package com.cedrickgd.morgenbriefing.ui.news

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.domain.ItemEdition
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.ui.components.motionDisabled
import com.cedrickgd.morgenbriefing.ui.components.rememberLoopPhase
import com.cedrickgd.morgenbriefing.ui.components.pressScale
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.theme.OverlineLetterSpacing
import com.cedrickgd.morgenbriefing.ui.theme.Reading
import com.cedrickgd.morgenbriefing.util.windowHeight
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Feed pictures: 16:10, 22 dp corners (docs/DESIGN.md §7). */
private const val FEED_IMAGE_RATIO = 16f / 10f

/** In a short window (landscape) a feed picture takes at most this share of its height. */
private const val FEED_IMAGE_MAX_WINDOW_SHARE = 0.45f

/**
 * Everything a feed entry can do. Null callbacks hide their menu item. Create it once per
 * screen (`remember`) so entries stay skippable.
 */
@Stable
class FeedActions(
    /** Opens the article: the in-app reader, or the browser when the user prefers that. */
    val onOpen: (FeedArticle) -> Unit,
    /** Always the browser (⋮ menu), even when [onOpen] reads in the app. */
    val onOpenExternal: (FeedArticle) -> Unit,
    val onListen: (FeedArticle) -> Unit,
    val onStopListening: () -> Unit,
    val onShare: (FeedArticle) -> Unit,
    val onCopyLink: (FeedArticle) -> Unit,
    val onHideSource: ((FeedArticle) -> Unit)? = null,
    /** Stops following the topic an article is shown under (⋮ → "„Wirtschaft“ nicht mehr folgen"). */
    val onUnfollowTopic: ((Topic) -> Unit)? = null,
    /** Follows a topic the ⋮ menu offers ([followSuggestion]). */
    val onFollowTopic: ((Topic) -> Unit)? = null,
    /** The topic the ⋮ menu offers to follow for an article (its sub-topic), if any. */
    val followSuggestion: (FeedArticle) -> Topic? = { null },
    /** False for the last followed topic: the story menus do not offer to unfollow it. */
    val canUnfollowTopic: (Topic) -> Boolean = { true },
)

/**
 * What the app learned about the articles on screen: the picture of articles that came without
 * one (Google News) – the lead picture the prefetcher read, else the cover looked up ahead of
 * the list ([com.cedrickgd.morgenbriefing.data.article.CoverResolver]) – and whether the reader
 * can show them. Entries read single values through `derivedStateOf`, so one new status
 * recomposes only its own card.
 */
@Stable
class FeedHints(
    private val images: State<Map<String, String>>,
    private val statuses: State<Map<String, ArticleStatus>>,
    private val covers: State<Map<String, String>> = NO_COVERS,
) {
    /** The article's picture found by the prefetcher or the cover lookup, if any. */
    fun heroImage(itemId: String): String? = images.value[itemId] ?: covers.value[itemId]

    fun status(itemId: String): ArticleStatus = statuses.value[itemId] ?: ArticleStatus.UNKNOWN

    companion object {
        /** Nothing known (previews, screens without a prefetcher). */
        val None = FeedHints(mutableStateOf(emptyMap()), mutableStateOf(emptyMap()))

        private val NO_COVERS: State<Map<String, String>> = mutableStateOf(emptyMap())
    }
}

/**
 * One article in the editorial feed, without a card frame, directly on the sky: a full-width
 * picture (or generated cover), the headline big in serif, the serif lead with an inline
 * "… Weiterlesen" and a meta row with "Zuhören", share and a ⋮ menu. Picture, headline and
 * lead open the article (the in-app reader, or the browser).
 *
 * Articles that came without a picture (Google News) show the page's lead picture once the
 * prefetcher found it, fading in over the cover; a small hint on the picture tells whether the
 * full text is ready in the app or the article sits behind a paywall.
 *
 * @param listening this article is being read aloud right now — the button turns into "Stopp".
 * @param paused this article is loaded in the player but paused — the button offers "Weiter".
 * @param showTopic adds a small topic overline (mixed views: "Neueste").
 * @param showImage false: only the typographic cover (Einstellungen → Feed → Bilder).
 */
@Composable
fun FeedEntry(
    article: FeedArticle,
    now: ZonedDateTime,
    listening: Boolean,
    actions: FeedActions,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
    showTopic: Boolean = false,
    showImage: Boolean = true,
    hints: FeedHints = FeedHints.None,
) {
    val item = article.item
    val strings = feedStrings()
    val language = LocalAppLanguage.current
    val interaction = remember { MutableInteractionSource() }
    val meta = remember(item, now, language) { feedMetaParts(item, now, language) }
    val maxImageHeight = windowHeight() * FEED_IMAGE_MAX_WINDOW_SHARE
    val hero by remember(hints, item.id, item.image) {
        derivedStateOf { if (item.image == null) hints.heroImage(item.id) else null }
    }
    val badge by remember(hints, item.id) { derivedStateOf { cardBadge(hints.status(item.id)) } }
    val visualItem = remember(item, hero) { hero?.let { item.copy(image = ArticleImage(it)) } ?: item }
    // Feeds that repeat the title as their description get no second copy of it.
    val teaser = remember(item.headline, item.summary) { cardTeaser(item.headline, item.summary) }
    // Hyphenate in the article's own language (English local news under a German edition).
    val textLanguage = remember(item.sourceId) { ItemEdition.languageOf(item) }

    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .pressScale(interaction, pressedScale = 0.985f)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = strings.openArticle,
                    onClick = { actions.onOpen(article) },
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatioAtMost(FEED_IMAGE_RATIO, maxImageHeight)
                    .clip(GlassShapes.tile),
            ) {
                ArticleVisual(
                    item = visualItem,
                    topic = article.topic,
                    modifier = Modifier.matchParentSize(),
                    showImage = showImage,
                    contentDescription = item.image?.alt?.takeIf { it.isNotBlank() },
                )
                CardBadgePill(
                    badge = badge,
                    strings = strings,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            if (showTopic) {
                TopicOverline(article)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = item.headline,
                style = Reading.feedHeadline(language = textLanguage),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (teaser.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LeadText(teaser, more = strings.readMore, language = textLanguage)
            }
        }
        FeedMetaRow(
            article = article,
            meta = meta,
            listening = listening,
            paused = paused,
            actions = actions,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The readability hint on a feed picture ("✓ Volltext", "🔒 Bezahlinhalt", "Nur im Browser"):
 * a small dark frosted pill that pops in once the prefetcher knows; nothing while unknown.
 */
@Composable
private fun CardBadgePill(badge: CardBadge?, strings: FeedStrings, modifier: Modifier = Modifier) {
    val still = motionDisabled()
    AnimatedVisibility(
        visible = badge != null,
        modifier = modifier,
        enter = if (still) EnterTransition.None else fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
            scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.8f),
        exit = if (still) ExitTransition.None else fadeOut(),
    ) {
        val shown = badge ?: return@AnimatedVisibility
        val (icon, label, description) = when (shown) {
            CardBadge.FULL_TEXT -> Triple(Icons.Rounded.CheckCircle, strings.fullText, strings.fullTextDescription)
            CardBadge.PAYWALL -> Triple(Icons.Rounded.Lock, strings.paywall, strings.paywallDescription)
            CardBadge.BROWSER_ONLY -> Triple(Icons.Rounded.Public, strings.browserOnly, strings.browserOnlyDescription)
        }
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.52f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .clearAndSetSemantics { contentDescription = description }
                .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
        }
    }
}

/** The topic's icon and "KI & TECH" above the headline, in the topic's color. */
@Composable
private fun TopicOverline(article: FeedArticle) {
    val colors = rememberTopicColors(article.topic)
    val tint = if (isDarkTheme()) colors.onContainer else colors.strong
    Row(verticalAlignment = Alignment.CenterVertically) {
        TopicGlyph(article.topic, tint = tint, size = 15.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = article.topic.title.uppercase(LocalAppLanguage.current.locale),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = OverlineLetterSpacing),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The lead in serif with "… [more]" appended inline: the lead is cut at a word so that the
 * suffix still fits into [maxLines] lines; a lead that fits gets " [more]" without the
 * ellipsis. Measured once per text and width.
 */
@Composable
internal fun LeadText(text: String, more: String, modifier: Modifier = Modifier, maxLines: Int = 3, language: String? = null) {
    val style = Reading.feedLead(language = language)
    // A quiet continuation: lighter than the lead, never bolder.
    val suffix = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer(cacheSize = 8)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val annotated = remember(text, more, width, style, suffix, measurer) {
            leadWithMore(text.trim(), more, width, style, suffix, measurer, maxLines)
        }
        Text(text = annotated, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

private fun leadWithMore(
    text: String,
    more: String,
    width: Int,
    style: TextStyle,
    suffix: SpanStyle,
    measurer: TextMeasurer,
    maxLines: Int,
): AnnotatedString {
    fun build(body: String, cut: Boolean) = buildAnnotatedString {
        append(body)
        append(if (cut) "… " else " ")
        withStyle(suffix) { append(more) }
    }
    if (width <= 0 || width == Constraints.Infinity) return build(text, cut = false)
    val constraints = Constraints(maxWidth = width)
    fun fits(candidate: AnnotatedString): Boolean =
        !measurer.measure(candidate, style, maxLines = maxLines, constraints = constraints).hasVisualOverflow

    val whole = build(text, cut = false)
    if (fits(whole)) return whole

    // Start where the plain lead is cut off, then step back word by word until the suffix fits.
    val plain = measurer.measure(text, style, maxLines = maxLines, constraints = constraints)
    val lastLine = (minOf(maxLines, plain.lineCount) - 1).coerceAtLeast(0)
    var candidate = text.substring(0, plain.getLineEnd(lastLine, visibleEnd = true).coerceIn(0, text.length))
    repeat(MAX_CUT_STEPS) {
        val body = candidate.trimEnd().trimEnd(',', ';', ':', '–', '-', '.', '…').trimEnd()
        val attempt = build(body, cut = true)
        if (fits(attempt)) return attempt
        val space = body.lastIndexOf(' ')
        if (space <= 0) return attempt
        candidate = body.substring(0, space)
    }
    return build(candidate.trimEnd(), cut = true)
}

private const val MAX_CUT_STEPS = 24

/**
 * Meta line + "Zuhören" + share + ⋮ menu. The time stays whole and only the source is
 * shortened, on one line — the row never wraps or jumps.
 */
@Composable
private fun FeedMetaRow(
    article: FeedArticle,
    meta: FeedMetaParts,
    listening: Boolean,
    paused: Boolean,
    actions: FeedActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            meta.time?.let { time ->
                Text(text = time, style = Reading.meta(), maxLines = 1, softWrap = false)
            }
            meta.source?.let { source ->
                Text(
                    text = if (meta.time != null) " · $source" else source,
                    style = Reading.meta(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
        ListenButton(
            listening = listening,
            paused = paused,
            headline = article.item.headline,
            onClick = { if (listening) actions.onStopListening() else actions.onListen(article) },
        )
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(width = 1.dp, height = 20.dp)
                .background(colors.onSurfaceVariant.copy(alpha = 0.3f)),
        )
        IconButton(onClick = { actions.onShare(article) }) {
            Icon(Icons.Rounded.Share, contentDescription = commonStrings().share, tint = colors.onSurfaceVariant)
        }
        FeedMenu(article = article, actions = actions)
    }
}

/**
 * "🎧 Zuhören"; while this article is read, a little equalizer and "Stopp"; while it is paused,
 * "Weiter". The button always keeps the width of its longest label (in every language), so
 * toggling never reflows the row.
 */
@Composable
private fun ListenButton(listening: Boolean, paused: Boolean, headline: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val strings = feedStrings()
    val tint = if (listening || paused) colors.primary else colors.onSurfaceVariant
    val label = when {
        listening -> strings.stop
        paused -> strings.resume
        else -> strings.listen
    }
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = when {
                    listening -> strings.stopListening
                    paused -> strings.resumeListening(headline)
                    else -> strings.listenTo(headline)
                }
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Invisible labels: reserve the room of the widest, so switching labels never resizes the button.
        for (reserved in listOf(strings.listen, strings.stop, strings.resume)) {
            Row(Modifier.graphicsLayer { alpha = 0f }, verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(26.dp))
                Text(text = reserved, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (listening) {
                Equalizer(color = tint, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Rounded.Headphones, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1)
        }
    }
}

/**
 * Three bars bouncing gently while an article is read aloud (static with reduced motion; at
 * 10 fps with light effects).
 */
@Composable
private fun Equalizer(color: Color, modifier: Modifier = Modifier) {
    val still = motionDisabled()
    val phase = if (still) null else rememberLoopPhase(periodMillis = 900, label = "equalizerPhase")
    Canvas(modifier) {
        val bar = size.width / 5f
        val t = phase?.value ?: 0.3f
        for (i in 0 until 3) {
            val wave = sin((t + i * 0.27f) * 2f * PI.toFloat())
            val h = size.height * (0.3f + 0.6f * wave * wave)
            drawRoundRect(
                color = color,
                topLeft = Offset(bar * (i * 2f), (size.height - h) / 2f),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2f),
            )
        }
    }
}

@Composable
private fun FeedMenu(article: FeedArticle, actions: FeedActions) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val strings = feedStrings()
    val common = commonStrings()
    val source = article.item.source.ifBlank { publisherHost(article.item) }.ifBlank { hostOf(article.item.url) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = strings.moreOptions, tint = colors.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            MenuItem(strings.readArticle, Icons.AutoMirrored.Rounded.Article) { open = false; actions.onOpen(article) }
            MenuItem(common.openInBrowser, Icons.AutoMirrored.Rounded.OpenInNew) { open = false; actions.onOpenExternal(article) }
            MenuItem(common.copyLink, Icons.Rounded.ContentCopy) { open = false; actions.onCopyLink(article) }
            if (open) {
                actions.onFollowTopic?.let { follow ->
                    actions.followSuggestion(article)?.let { topic ->
                        MenuItem(strings.followTopic(topic.title), Icons.Rounded.AddCircleOutline, tint = colors.primary) {
                            open = false
                            follow(topic)
                        }
                    }
                }
            }
            actions.onHideSource?.let { hide ->
                if (source.isNotBlank() && canHideSource(article.item)) {
                    MenuItem(strings.hideSource(source), Icons.AutoMirrored.Rounded.VolumeOff) { open = false; hide(article) }
                }
            }
            actions.onUnfollowTopic?.let { unfollow ->
                MenuItem(strings.unfollowTopic(article.topic.title), Icons.Rounded.RemoveCircleOutline) { open = false; unfollow(article.topic) }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = tint) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/**
 * The full width at [ratio] (width / height), but never taller than [maxHeight]: in landscape
 * a picture as wide as the column would be taller than the window, so it is cropped wider.
 */
private fun Modifier.aspectRatioAtMost(ratio: Float, maxHeight: Dp): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = (width / ratio).roundToInt()
        .coerceAtMost(maxHeight.roundToPx())
        .coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
}

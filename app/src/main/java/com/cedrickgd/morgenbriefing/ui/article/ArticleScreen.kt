package com.cedrickgd.morgenbriefing.ui.article

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.ReaderSettings
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.domain.ItemEdition
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.favoritesStrings
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.i18n.readerStrings
import com.cedrickgd.morgenbriefing.listen
import com.cedrickgd.morgenbriefing.listen.ListenMiniPlayerDefaults
import com.cedrickgd.morgenbriefing.ui.components.GlassIconButton
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.components.GlassRoundButton
import com.cedrickgd.morgenbriefing.ui.components.LoadingSkeleton
import com.cedrickgd.morgenbriefing.ui.glass.GlassLevel
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.blockTouchesBehind
import com.cedrickgd.morgenbriefing.ui.glass.glass
import com.cedrickgd.morgenbriefing.ui.glass.glassContent
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import com.cedrickgd.morgenbriefing.ui.nav.ArticleRoute
import com.cedrickgd.morgenbriefing.ui.news.ArticleVisual
import com.cedrickgd.morgenbriefing.ui.news.TopicColors
import com.cedrickgd.morgenbriefing.ui.news.copyLink
import com.cedrickgd.morgenbriefing.ui.news.rememberTopicColors
import com.cedrickgd.morgenbriefing.util.rememberLinkActions
import com.cedrickgd.morgenbriefing.util.statusBarTop

/** Distance of the reader's text (head, paragraphs, end note) from the screen's edge. */
private val TextGutter = 16.dp

/** The failure panel reaches closer to the edge than the text (the card edge); the picture is edge to edge. */
private val ImageGutter = 10.dp

/** The floating controls (back, "Aa", star, listen, ⋮) sit this far from the edge. */
private val ControlsGutter = 10.dp

/** Height of the controls' row under the status bar; the bar behind them is status bar + this. */
private val ControlsBarHeight = 64.dp

/** The bar is there this far before the text reaches it, so no line ever shows above its edge. */
private val ControlsBarLead = 24.dp

/** Corner radius of the bar's lower edge. */
private val ControlsBarCorner = 24.dp

/**
 * The article reader (docs/DESIGN.md §7), short and clean like Google News or Perplexity: the
 * article's text on a calm, opaque surface — no ads, no cookie banners, no navigation, no source
 * lines, and one picture: the card's (or the page's lead picture) edge-to-edge under the status
 * bar. Then the time, the headline in serif, the teaser and the text, in the size, font and line
 * spacing of "Darstellung". A long article opens with its beginning ([shorten]) and "Ganzen
 * Artikel lesen · noch X Min." below it. Floating glass controls: back, "Aa" (the sheet
 * "Darstellung"), the star ("Favoriten"), "Zuhören" (reads what is shown: the short version, or
 * the whole article once opened) and ⋮ (share, copy the link, "Im Browser öffnen" – the way out
 * when a page cannot be read). Over the picture they float alone; once the text reaches them, a
 * frosted bar behind them takes the status bar and the controls' row, so the text never runs
 * under the clock or the buttons.
 */
@Composable
fun ArticleScreen(
    route: ArticleRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ArticleViewModel = viewModel(key = "article:${route.url}", factory = ArticleViewModel.factory(route))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val links = rememberLinkActions()
    val listen = remember(context) { context.listen }
    val listenState by listen.state.collectAsStateWithLifecycle()
    val listeningThis = listenState.articleKey == state.item.key && (listenState.speaking || listenState.paused)
    val playerShown = listenState.speaking || listenState.paused
    val colors = MaterialTheme.colorScheme
    val statusTop = statusBarTop()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val playerLift = if (playerShown) ListenMiniPlayerDefaults.Height + ListenMiniPlayerDefaults.Gap else 0.dp
    val openExternal = { links.open(state.item.url) }
    val common = commonStrings()
    val reader = readerStrings()
    val feed = feedStrings()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val heroHeight = minOf(statusTop + maxWidth * 0.72f, maxHeight * 0.5f)
        val listState = rememberLazyListState()
        // The short version of a long article, or all of it once "Ganzen Artikel lesen" was tapped.
        val content = state.shown
        // The picture has scrolled up to the controls: from here on, text passes beneath them.
        val barAfterPx = with(LocalDensity.current) { (heroHeight - statusTop - ControlsBarHeight - ControlsBarLead).toPx() }
        val textUnderControls by remember(listState, barAfterPx) {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > barAfterPx }
        }
        val look = appearance
        val type = rememberReaderType(look?.reader ?: ReaderSettings(), content?.language?.ifBlank { null } ?: ItemEdition.languageOf(state.item))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // The article is what the floating glass (controls, mini player) blurs.
                .glassContent(),
            contentPadding = PaddingValues(bottom = navBottom + 32.dp + playerLift),
        ) {
            item(key = "hero") { Hero(state, heroHeight, showImage = look?.showImages ?: true) }
            when {
                // Until "Darstellung" is read the text waits (a frame or two) instead of jumping from one size to another.
                look == null -> Unit

                content != null -> {
                    item(key = "head") { Head(state, type) }
                    // The text only: ArticleCleaner.arrange took every picture and caption out (the one picture is on top).
                    // The short version is a prefix of the whole text, block by block: opening the
                    // rest only adds items below (same keys above), so what is read stays put.
                    itemsIndexed(content.blocks, key = { index, _ -> "block$index" }, contentType = { _, block -> block.type }) { _, block ->
                        BlockView(block, type)
                    }
                    if (state.canExpand) {
                        item(key = "more") { ReadFull(minutes = state.restMinutes, onClick = viewModel::expand) }
                    } else {
                        item(key = "end") { EndNote(state, onOpenExternal = openExternal) }
                    }
                }

                state.loading -> {
                    item(key = "head") { Head(state, type) }
                    item(key = "skeleton") { BodySkeleton() }
                }

                else -> {
                    item(key = "head") { Head(state, type) }
                    item(key = "failed") {
                        LoadFailed(
                            failure = state.failure,
                            accent = rememberTopicColors(state.topic),
                            onRetry = viewModel::retry,
                            onOpenExternal = openExternal,
                        )
                    }
                }
            }
        }

        ReaderControls(
            solid = textUnderControls,
            back = { ReaderControl(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = common.back, onClick = onBack) },
        ) {
            AppearanceControl(contentDescription = reader.appearance, onClick = { appearanceOpen = true })
            StarControl(starred = state.starred, onClick = viewModel::toggleStar)
            val speakingThis = listeningThis && listenState.speaking
            ReaderControl(
                icon = if (speakingThis) Icons.Rounded.Pause else Icons.Rounded.Headphones,
                contentDescription = if (speakingThis) reader.pauseListening else reader.listen,
                onClick = {
                    // Reads what is shown: the short version, or the whole article once opened.
                    if (listeningThis) listen.pauseOrResume() else listen.play(state.item, state.shown?.text)
                },
            )
            ReaderOverflow(
                onShare = { links.shareArticle(state.item) },
                onCopyLink = { copyArticleLink(context, state.item, feed) },
                onOpenExternal = openExternal,
            )
        }

        val shown = appearance
        if (appearanceOpen && shown != null) {
            ReaderAppearanceSheet(
                appearance = shown,
                onPreview = viewModel::previewReader,
                onChange = viewModel::setReader,
                onShowImages = viewModel::setShowImages,
                onDismiss = { appearanceOpen = false },
            )
        }
    }
}

/**
 * ⋮ in the reader's bar: what used to be three buttons up there – share, copy the link and "Im
 * Browser öffnen" (also the way out when the page cannot be read), in the feed's menu style.
 */
@Composable
private fun ReaderOverflow(onShare: () -> Unit, onCopyLink: () -> Unit, onOpenExternal: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val common = commonStrings()
    val colors = MaterialTheme.colorScheme
    Box {
        ReaderControl(Icons.Rounded.MoreVert, contentDescription = feedStrings().moreOptions, onClick = { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = colors.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
        ) {
            OverflowEntry(common.share, Icons.Rounded.Share) { open = false; onShare() }
            OverflowEntry(common.copyLink, Icons.Rounded.ContentCopy) { open = false; onCopyLink() }
            OverflowEntry(common.openInBrowser, Icons.AutoMirrored.Rounded.OpenInNew) { open = false; onOpenExternal() }
        }
    }
}

@Composable
private fun OverflowEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/** Puts the article's link on the clipboard (as the feed's menu does); Android 13+ confirms it itself. */
private fun copyArticleLink(context: Context, item: NewsItem, strings: FeedStrings) {
    val copied = copyLink(context, item.headline.ifBlank { strings.clipLabel }, item.url)
    if (!copied) {
        Toast.makeText(context, strings.copyFailed, Toast.LENGTH_SHORT).show()
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, strings.linkCopied, Toast.LENGTH_SHORT).show()
    }
}

/**
 * The reader's controls at the top: [back] on the left, the [actions] on the right and, once the
 * text has reached them ([solid]), a thick frosted bar behind them, from the top of the screen
 * down to under the buttons – without a title: the source is not the story. The bar is floating
 * glass: it blurs the text gliding beneath it (opaque without real blur) and takes every touch
 * inside it. The reader's page has the colour of the bar, so a hairline along its rounded lower
 * edge and a soft shadow set it off.
 */
@Composable
private fun BoxScope.ReaderControls(
    solid: Boolean,
    back: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val statusTop = statusBarTop()
    val barShape = RoundedCornerShape(bottomStart = ControlsBarCorner, bottomEnd = ControlsBarCorner)
    val hairline = MaterialTheme.colorScheme.outlineVariant
    AnimatedVisibility(
        visible = solid,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusTop + ControlsBarHeight)
                .shadow(12.dp, barShape, ambientColor = Color.Black.copy(alpha = 0.14f), spotColor = Color.Black.copy(alpha = 0.14f))
                .glass(shape = barShape, level = GlassLevel.Thick, floating = true)
                .drawWithContent {
                    drawContent()
                    // Only the lower edge: up the sides and along the top the bar meets the screen's edge.
                    val stroke = 1.dp.toPx()
                    val half = stroke / 2f
                    val corner = ControlsBarCorner.toPx()
                    val bottom = size.height - half
                    val edge = Path().apply {
                        moveTo(half, bottom - corner)
                        arcTo(Rect(half, bottom - 2 * corner, half + 2 * corner, bottom), 180f, -90f, false)
                        lineTo(size.width - half - corner, bottom)
                        arcTo(Rect(size.width - half - 2 * corner, bottom - 2 * corner, size.width - half, bottom), 90f, -90f, false)
                    }
                    drawPath(edge, hairline, style = Stroke(width = stroke))
                }
                .blockTouchesBehind(),
        )
    }
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(top = statusTop + 8.dp, start = ControlsGutter, end = ControlsGutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        back()
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/**
 * One of the reader's round controls. The page beneath is the theme's own surface colour, on
 * which plain glass disappears: these are tinted a step darker (light) or lighter (dark) than
 * the page and float, so they stand out over the white page, the bar and any picture alike.
 */
@Composable
private fun ReaderControl(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    GlassIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        level = GlassLevel.Regular,
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        floating = true,
    )
}

/**
 * "A" in the reader's bar: a serif "Aa" (as the reader's text-size button had it in version 1) on
 * the same glass as the other controls; it opens "Darstellung".
 */
@Composable
private fun AppearanceControl(contentDescription: String, onClick: () -> Unit) {
    GlassRoundButton(
        contentDescription = contentDescription,
        onClick = onClick,
        level = GlassLevel.Regular,
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        floating = true,
    ) {
        Text(
            text = "Aa",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * ☆ in the reader's bar: stars the story ("Favoriten") or takes its star off; filled in the accent
 * colour while starred. Its label says what a tap does.
 */
@Composable
private fun StarControl(starred: Boolean, onClick: () -> Unit) {
    val strings = favoritesStrings()
    GlassIconButton(
        icon = if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
        contentDescription = if (starred) strings.removeFavorite else strings.addFavorite,
        onClick = onClick,
        level = GlassLevel.Regular,
        tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        floating = true,
    )
}

@Composable
private fun Hero(state: ArticleUiState, height: Dp, showImage: Boolean) {
    val item = state.item
    // The article's one picture (ArticleUiState.hero: the card's, else the page's) — through the same loader and cover.
    val visualItem = remember(item, state.hero) {
        val url = state.hero
        if (item.image == null && url != null) item.copy(image = ArticleImage(url)) else item
    }
    val background = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        ArticleVisual(
            item = visualItem,
            topic = state.topic,
            modifier = Modifier.fillMaxSize(),
            showImage = showImage,
            contentDescription = visualItem.image?.alt?.ifBlank { null },
            showCoverBadge = true,
        )
        // Legible status bar icons and controls over any picture; the picture melts into the page below.
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, background))),
        )
    }
}

/** Time, headline and teaser – no source: the newsroom's name is not the story. */
@Composable
private fun Head(state: ArticleUiState, type: ReaderType) {
    val colors = MaterialTheme.colorScheme
    val item = state.item
    val headline = item.headline.ifBlank { state.content?.title.orEmpty() }
    val lang = LocalAppLanguage.current
    val time = remember(item, state.now, lang) { Fmt.relative(item.publishedAt, state.now, lang)?.uppercase(lang.locale) }
    Column(Modifier.padding(horizontal = TextGutter)) {
        if (!time.isNullOrBlank()) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (headline.isNotBlank()) {
            Text(text = headline, style = type.headline, color = colors.onSurface)
        }
        if (state.teaser.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(text = state.teaser, style = type.teaser, color = colors.onSurface.copy(alpha = 0.92f))
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** One block of the text in the reader's [type]. Pictures and captions never get here (one picture, on top). */
@Composable
private fun BlockView(block: ArticleBlock, type: ReaderType) {
    val colors = MaterialTheme.colorScheme
    val body = type.body
    when (block.type) {
        ArticleBlock.PARAGRAPH -> Text(
            text = block.text,
            style = body,
            color = colors.onSurface,
            modifier = Modifier.padding(horizontal = TextGutter, vertical = 8.dp),
        )

        ArticleBlock.HEADING -> Text(
            text = block.text,
            style = type.subheading,
            color = colors.onSurface,
            modifier = Modifier.padding(start = TextGutter, end = TextGutter, top = 20.dp, bottom = 4.dp),
        )

        ArticleBlock.QUOTE -> Row(
            Modifier
                .padding(horizontal = TextGutter, vertical = 10.dp)
                .height(IntrinsicSize.Min),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(colors.primary, CircleShape),
            )
            Text(
                text = block.text,
                style = body.copy(fontStyle = FontStyle.Italic),
                color = colors.onSurface.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = 14.dp),
            )
        }

        ArticleBlock.LIST -> Column(Modifier.padding(horizontal = TextGutter, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEach { entry ->
                Row {
                    Text("•", style = body, color = colors.primary, modifier = Modifier.width(22.dp))
                    Text(entry, style = body, color = colors.onSurface, modifier = Modifier.weight(1f))
                }
            }
        }

        else -> Unit
    }
}

/**
 * The end of the article: nothing but the way to the rest when the text is probably only its
 * beginning – no source line ("Quelle: …"), and "Im Browser öffnen" otherwise lives in ⋮.
 */
@Composable
private fun EndNote(state: ArticleUiState, onOpenExternal: () -> Unit) {
    if (!state.partial) return
    Column(Modifier.padding(start = TextGutter, end = TextGutter, top = 24.dp)) {
        Text(
            text = readerStrings().truncatedNote,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        GlassPillButton(text = commonStrings().openInBrowser, icon = Icons.AutoMirrored.Rounded.OpenInNew, onClick = onOpenExternal)
    }
}

/**
 * Under the short version of a long article: "Ganzen Artikel lesen · noch X Min." as a pill, in
 * the place and style of the end note. A tap shows the rest below.
 */
@Composable
private fun ReadFull(minutes: Int, onClick: () -> Unit) {
    Column(Modifier.padding(start = TextGutter, end = TextGutter, top = 20.dp)) {
        GlassPillButton(text = readerStrings().readFull(minutes), icon = Icons.Rounded.ExpandMore, onClick = onClick)
    }
}

@Composable
private fun BodySkeleton() {
    Column(
        Modifier.padding(horizontal = TextGutter, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listOf(1f, 0.94f, 0.98f, 0.62f, 1f, 0.9f, 0.97f, 0.48f).forEach { width ->
            LoadingSkeleton(
                Modifier
                    .fillMaxWidth(width)
                    .height(18.dp),
            )
        }
    }
}

/**
 * Why the article is not here, as a deliberate panel rather than an error: a tile in the topic's
 * gradient with the reason's symbol (a lock for a paywall, a cookie for a consent wall …), a
 * short title, one or two sentences and what can be done: "Nochmal versuchen" only where it can
 * help, "Im Browser öffnen" as the quiet way out. The headline and teaser stay above it.
 */
@Composable
private fun LoadFailed(failure: ArticleFailure?, accent: TopicColors, onRetry: () -> Unit, onOpenExternal: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reader = readerStrings()
    val common = commonStrings()
    val dark = isGlassDark()
    val tint = if (dark) accent.onContainer else accent.strong
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ImageGutter, vertical = 20.dp)
            .clip(GlassShapes.card)
            .background(accent.container.copy(alpha = if (dark) 0.55f else 0.7f))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(accent.coverStart, accent.coverEnd))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(failureIcon(failure).vector(), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = reader.failureTitle(failure?.outcome),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = reader.failureReason(failure?.outcome, failure?.site.orEmpty(), failure?.httpCode, failure?.via),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        failureActions(failure).forEachIndexed { index, action ->
            when (action) {
                FailureAction.RETRY -> GlassPillButton(text = common.retry, icon = Icons.Rounded.Refresh, onClick = onRetry, filled = index == 0)
                FailureAction.OPEN_IN_BROWSER -> GlassPillButton(
                    text = common.openInBrowser,
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = onOpenExternal,
                    contentColor = tint,
                )
            }
        }
    }
}

/** The Material symbol of a [FailureIcon]. */
private fun FailureIcon.vector(): ImageVector = when (this) {
    FailureIcon.PAYWALL -> Icons.Rounded.Lock
    FailureIcon.CONSENT -> Icons.Rounded.Cookie
    FailureIcon.BLOCKED -> Icons.Rounded.GppBad
    FailureIcon.GONE -> Icons.Rounded.LinkOff
    FailureIcon.NOT_ARTICLE -> Icons.Rounded.PlayCircle
    FailureIcon.OFFLINE -> Icons.Rounded.CloudOff
    FailureIcon.ERROR -> Icons.Rounded.ErrorOutline
}

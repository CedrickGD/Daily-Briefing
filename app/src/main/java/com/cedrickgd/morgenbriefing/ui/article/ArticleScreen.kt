package com.cedrickgd.morgenbriefing.ui.article

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.cedrickgd.morgenbriefing.data.model.ArticleBlock
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.readerStrings
import com.cedrickgd.morgenbriefing.listen
import com.cedrickgd.morgenbriefing.listen.ListenMiniPlayerDefaults
import com.cedrickgd.morgenbriefing.ui.components.GlassIconButton
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.components.LoadingSkeleton
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.glass.glassContent
import com.cedrickgd.morgenbriefing.ui.glass.isGlassDark
import com.cedrickgd.morgenbriefing.ui.nav.ArticleRoute
import com.cedrickgd.morgenbriefing.ui.news.ArticleVisual
import com.cedrickgd.morgenbriefing.ui.news.TopicColors
import com.cedrickgd.morgenbriefing.ui.news.hostOf
import com.cedrickgd.morgenbriefing.ui.news.rememberTopicColors
import com.cedrickgd.morgenbriefing.util.rememberLinkActions
import com.cedrickgd.morgenbriefing.util.statusBarTop

/** Distance of the reader's text (head, paragraphs, captions, end note) from the screen's edge. */
private val TextGutter = 16.dp

/** Pictures in the text and the failure panel reach closer to the edge than the text; the hero is edge to edge. */
private val ImageGutter = 10.dp

/** The floating controls (back, listen, share, browser) sit this far from the edge. */
private val ControlsGutter = 10.dp

/**
 * The article reader (docs/DESIGN.md §7): the article's own text and pictures on a calm, opaque
 * surface — no ads, no cookie banners, no navigation. The feed's picture (or the page's lead
 * picture) sits edge-to-edge under the status bar, then source and time, the headline in serif,
 * the teaser, and the extracted body. Floating glass controls: back, "Zuhören" (reads the full
 * text), share and "Im Browser öffnen" as the way out when a page cannot be read.
 */
@Composable
fun ArticleScreen(
    route: ArticleRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ArticleViewModel = viewModel(key = "article:${route.url}", factory = ArticleViewModel.factory(route))
    val state by viewModel.state.collectAsStateWithLifecycle()
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

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        val heroHeight = minOf(statusTop + maxWidth * 0.72f, maxHeight * 0.5f)
        val listState = rememberLazyListState()
        val content = state.content
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // The article is what the floating glass (controls, mini player) blurs.
                .glassContent(),
            contentPadding = PaddingValues(bottom = navBottom + 32.dp + playerLift),
        ) {
            item(key = "hero") { Hero(state, heroHeight) }
            item(key = "head") { Head(state) }
            when {
                content != null -> {
                    itemsIndexed(content.blocks, key = { index, _ -> "block$index" }, contentType = { _, block -> block.type }) { index, block ->
                        // The picture on top is not among the blocks (ArticleCleaner.arrange took it out).
                        BlockView(block, showImages = state.showImages, showAlt = showsAlt(content.blocks, index))
                    }
                    item(key = "end") { EndNote(state, content, onOpenExternal = openExternal) }
                }

                state.loading -> item(key = "skeleton") { BodySkeleton() }

                else -> item(key = "failed") {
                    LoadFailed(
                        failure = state.failure,
                        accent = rememberTopicColors(state.topic),
                        onRetry = viewModel::retry,
                        onOpenExternal = openExternal,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = statusTop + 8.dp, start = ControlsGutter, end = ControlsGutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = common.back, onClick = onBack)
            Spacer(Modifier.weight(1f))
            val speakingThis = listeningThis && listenState.speaking
            GlassIconButton(
                icon = if (speakingThis) Icons.Rounded.Pause else Icons.Rounded.Headphones,
                contentDescription = if (speakingThis) reader.pauseListening else reader.listen,
                onClick = {
                    if (listeningThis) listen.pauseOrResume() else listen.play(state.item, state.content?.text)
                },
            )
            GlassIconButton(Icons.Rounded.Share, contentDescription = common.share, onClick = { links.shareArticle(state.item) })
            GlassIconButton(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = common.openInBrowser, onClick = openExternal)
        }
    }
}

@Composable
private fun Hero(state: ArticleUiState, height: Dp) {
    val item = state.item
    // The feed's picture, else the page's lead picture (ArticleUiState.hero) — through the same loader and cover.
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
            showImage = state.showImages,
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

@Composable
private fun Head(state: ArticleUiState) {
    val colors = MaterialTheme.colorScheme
    val item = state.item
    val headline = item.headline.ifBlank { state.content?.title.orEmpty() }
    val lang = LocalAppLanguage.current
    val meta = remember(item, state.now, lang) {
        val source = item.source.trim().ifEmpty { item.publisherDomain.ifBlank { hostOf(item.url) } }
        listOfNotNull(Fmt.relative(item.publishedAt, state.now, lang), source.ifBlank { null }).joinToString(" · ")
    }
    Column(Modifier.padding(horizontal = TextGutter)) {
        Text(
            text = meta.uppercase(lang.locale),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        if (headline.isNotBlank()) {
            Text(text = headline, style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        }
        if (state.teaser.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = state.teaser,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 19.sp,
                    lineHeight = 29.sp,
                ),
                color = colors.onSurface.copy(alpha = 0.92f),
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun BlockView(block: ArticleBlock, showImages: Boolean, showAlt: Boolean) {
    val colors = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Serif, fontSize = 18.sp, lineHeight = 29.sp)
    when (block.type) {
        ArticleBlock.PARAGRAPH -> Text(
            text = block.text,
            style = body,
            color = colors.onSurface,
            modifier = Modifier.padding(horizontal = TextGutter, vertical = 8.dp),
        )

        ArticleBlock.HEADING -> Text(
            text = block.text,
            style = MaterialTheme.typography.headlineSmall,
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

        ArticleBlock.IMAGE -> if (showImages) {
            Column(Modifier.padding(horizontal = ImageGutter, vertical = 12.dp)) {
                AsyncImage(
                    model = block.url,
                    contentDescription = block.alt.ifBlank { null },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .clip(GlassShapes.tile),
                )
                // Only without a caption below (showsAlt): the alt text often is the caption in other words.
                if (showAlt) {
                    Text(
                        text = block.alt,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = TextGutter - ImageGutter, end = TextGutter - ImageGutter, top = 6.dp),
                    )
                }
            }
        }

        ArticleBlock.CAPTION -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = TextGutter, end = TextGutter, bottom = 8.dp),
        )
    }
}

@Composable
private fun EndNote(state: ArticleUiState, content: ArticleContent, onOpenExternal: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val reader = readerStrings()
    Column(Modifier.padding(start = TextGutter, end = TextGutter, top = 24.dp)) {
        if (state.partial) {
            Text(
                text = reader.truncatedNote,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        val source = state.item.source.ifBlank { content.site }
        if (source.isNotBlank()) {
            Text(
                text = reader.source(source),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        GlassPillButton(text = commonStrings().openInBrowser, icon = Icons.AutoMirrored.Rounded.OpenInNew, onClick = onOpenExternal)
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

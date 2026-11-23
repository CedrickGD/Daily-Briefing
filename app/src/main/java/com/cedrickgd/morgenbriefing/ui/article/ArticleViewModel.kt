package com.cedrickgd.morgenbriefing.ui.article

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cedrickgd.morgenbriefing.AppContainer
import com.cedrickgd.morgenbriefing.MorgenbriefingApp
import com.cedrickgd.morgenbriefing.data.article.ArticleCleaner
import com.cedrickgd.morgenbriefing.data.article.ArticleCompleteness
import com.cedrickgd.morgenbriefing.data.article.ArticleException
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleContent
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.ReadOutcome
import com.cedrickgd.morgenbriefing.data.model.ReaderSettings
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import com.cedrickgd.morgenbriefing.ui.nav.ArticleHandoff
import com.cedrickgd.morgenbriefing.ui.nav.ArticleRoute
import com.cedrickgd.morgenbriefing.ui.news.hostOf
import com.cedrickgd.morgenbriefing.util.attempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Why the reader shows no article: the typed [outcome] (null = an unexpected error), the
 * newsroom's [site] name and the server's [httpCode]; [via] names the aggregator (Google News)
 * that could not be asked for the article's address, [httpCode] is then its status. Turned
 * into text by `ReaderStrings`.
 */
@Immutable
data class ArticleFailure(
    val outcome: ReadOutcome?,
    val site: String = "",
    val httpCode: Int? = null,
    val via: String? = null,
) {
    /** The site keeps the text to itself (paywall, consent, bot block): only the browser helps. */
    val isWall: Boolean get() = outcome?.isWall == true

    companion object {
        fun of(error: Throwable, fallbackSite: String): ArticleFailure = when (error) {
            is ArticleException -> ArticleFailure(error.outcome, error.site.ifBlank { fallbackSite }, error.httpCode, error.via)
            else -> ArticleFailure(null, fallbackSite)
        }
    }
}

/** What the article reader shows. */
@Immutable
data class ArticleUiState(
    /** The feed item (headline, teaser, source, picture); a stub when the feed no longer has it. */
    val item: NewsItem,
    val topic: Topic,
    /**
     * The extracted article without what the head above it already shows – for a long article only
     * its beginning ([shorten]; the rest is [full]); null while loading or when loading failed.
     */
    val content: ArticleContent? = null,
    /** The whole article when [content] is its short version; null when [content] is all of it. */
    val full: ArticleContent? = null,
    /** "Ganzen Artikel lesen" was tapped: [full] shows. */
    val expanded: Boolean = false,
    /** "noch X Min." for the part of [full] beyond [content]. */
    val restMinutes: Int = 0,
    /**
     * The teaser shown under the headline: the feed's (else the page's own), shortened or
     * replaced so it never repeats the text below.
     */
    val teaser: String = item.summary,
    /**
     * The article's one picture, under the status bar (null = the topic's cover): the card's
     * picture until the article is here, then the one the text was arranged around – the text
     * itself shows none ([ArticleCleaner.arrange]).
     */
    val hero: String? = item.image?.url,
    val loading: Boolean = true,
    /** Why the article could not be loaded; null while loading or on success. */
    val failure: ArticleFailure? = null,
    /** Reference for "vor 3 Min.". */
    val now: ZonedDateTime,
    /**
     * The text is probably only the free lead of the article
     * ([com.cedrickgd.morgenbriefing.data.article.ArticleCompleteness]): the reader says so at
     * its end. Judged on the whole extracted text, before the head is taken out.
     */
    val partial: Boolean = false,
    /** The user starred the story ("Favoriten"). */
    val starred: Boolean = false,
) {
    /** What the reader shows and "Zuhören" reads: the whole article once expanded, else [content]. */
    val shown: ArticleContent? get() = if (expanded && full != null) full else content

    /** A short version is shown and the rest waits behind "Ganzen Artikel lesen". */
    val canExpand: Boolean get() = full != null && !expanded
}

/**
 * Loads the readable article for [route]: the feed item is taken from the news repository (by
 * id, else by URL), the text from [AppContainer.articles]. When the feed has forgotten the
 * item (opened from the mini player much later), a stub carries the URL and the page's own
 * title fills the headline. The result is arranged under the reader's head (headline, teaser,
 * hero picture) so nothing shows twice ([ArticleCleaner.arrange]), and reported to the
 * prefetcher so the feed knows the article is readable.
 */
class ArticleViewModel(
    private val container: AppContainer,
    private val route: ArticleRoute,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.of("Europe/Berlin")) },
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ArticleUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            attempt { container.favorites.warm() }.onFailure { Log.w(TAG, "Favorites unavailable", it) }
            container.favorites.state.collect { favorites ->
                _state.update { s -> s.copy(starred = favorites.isStarred(s.item)) }
            }
        }
        load(refresh = false)
        // Opened in the reader: the story counts as read. A bare link (the mini player) is noted once its title is known.
        _state.value.item.takeIf { it.headline.isNotBlank() }?.let(::noteRead)
    }

    private fun noteRead(item: NewsItem) {
        attempt { container.history.markRead(item) }.onFailure { Log.w(TAG, "Could not note the article as read", it) }
    }

    private fun initialState(): ArticleUiState {
        val known = container.news.state.value.topics.values.asSequence()
            .flatMap { it.items.asSequence() }
            .firstOrNull { (route.itemId != null && it.id == route.itemId) || it.url == route.url }
            // Search results are not kept by the repository; the search screen hands them over.
            ?: ArticleHandoff.find(route.itemId, route.url)
        val item = known ?: NewsItem(
            id = route.itemId ?: RssClient.stableId(route.url),
            headline = "",
            url = route.url,
            source = hostOf(route.url),
            sourceId = "",
            topicId = route.topicId ?: FALLBACK_TOPIC,
        )
        // The card's picture (FeedEntry.heroImage): the feed's, else what the prefetcher or the cover lookup found.
        val picture = item.image?.url?.takeIf { it.isNotBlank() }
            ?: container.prefetcher.images.value[item.id]
            ?: container.covers.covers.value[item.id]
        return ArticleUiState(item = item, topic = Topics.resolveOrNeutral(item.topicId, AppSettings()), hero = picture, now = clock())
    }

    /**
     * The "Darstellung" chosen on this screen: shown at once – while the text-size slider still
     * moves, and before the stored settings come back with it (no flicker to the old size).
     */
    private val draft = MutableStateFlow<ReaderSettings?>(null)

    /**
     * How the text looks – the "Darstellung" chosen here ([draft]), else the stored one – and
     * whether pictures show. Null until the settings are read, so the text never opens in one
     * size and jumps to another.
     */
    val appearance: StateFlow<ReaderAppearance?> = combine(container.settings.settings, draft) { settings, draft ->
        ReaderAppearance(reader = draft ?: settings.reader, showImages = settings.showImages)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SETTINGS_TIMEOUT_MS), null)

    /** Shows [reader] on the open article at once, without storing it (the slider is still moving). */
    fun previewReader(reader: ReaderSettings) {
        draft.value = reader
    }

    /** Stores [reader] ("Darstellung"); the open article already shows it. */
    fun setReader(reader: ReaderSettings) {
        draft.value = reader
        saveSettings(onFailure = { draft.value = null }) { it.copy(reader = reader) }
    }

    /** "Bilder anzeigen" – the same setting as in Einstellungen → Feed & Lesen (it hides the feed's pictures too). */
    fun setShowImages(show: Boolean) = saveSettings { it.copy(showImages = show) }

    private fun saveSettings(onFailure: () -> Unit = {}, transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            attempt { container.settings.update(transform) }.onFailure {
                Log.w(TAG, "Could not store the reader's appearance", it)
                // Not stored: the article shows what the settings say again.
                onFailure()
            }
        }
    }

    /** Loads (or, with [refresh], re-reads) the article. */
    fun load(refresh: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, failure = null) }
            val settings = attempt { container.settings.current() }.getOrNull() ?: AppSettings()
            _state.update { s -> s.copy(topic = Topics.resolveOrNeutral(s.item.topicId, settings)) }
            attempt { container.articles.pruneOnce() }.onFailure { Log.w(TAG, "Could not prune the article cache", it) }
            val item = _state.value.item
            val result = attempt { container.articles.load(item, refresh, language = settings.edition.language) }
            ensureActive()
            result.fold(
                onSuccess = { content ->
                    // The card's picture the reader opened with: the one picture unless the feed has its own.
                    val cover = _state.value.hero
                    val current = _state.value.item
                    val shown = if (current.headline.isBlank()) current.copy(headline = content.title) else current
                    if (current.headline.isBlank() && shown.headline.isNotBlank()) noteRead(shown)
                    // Cleaning up the blocks and judging completeness walk the whole text: off the main thread.
                    val (arranged, partial) = withContext(Dispatchers.Default) {
                        container.prefetcher.report(item, content)
                        arrange(content, shown, cover) to !ArticleCompleteness.isComplete(content, item)
                    }
                    // A long article opens with its beginning; the rest waits behind "Ganzen Artikel lesen".
                    val split = withContext(Dispatchers.Default) { split(arranged.content) }
                    ensureActive()
                    _state.update { s ->
                        s.copy(
                            item = shown, content = split.content, full = split.full, restMinutes = split.restMinutes,
                            teaser = arranged.teaser, hero = arranged.hero,
                            loading = false, failure = null, now = clock(), partial = partial,
                            starred = container.favorites.state.value.isStarred(shown),
                        )
                    }
                },
                onFailure = { e ->
                    Log.i(TAG, "Article unavailable: ${e.message}")
                    withContext(Dispatchers.Default) { container.prefetcher.report(item, e) }
                    _state.update { it.copy(loading = false, failure = ArticleFailure.of(e, it.item.source)) }
                },
            )
        }
    }

    fun retry() = load(refresh = true)

    /** "Ganzen Artikel lesen": the rest of a shortened article shows below what is there. */
    fun expand() {
        _state.update { it.copy(expanded = true) }
    }

    /** The star in the reader's bar: stars the story or takes its star off. */
    fun toggleStar() {
        val s = _state.value
        val starred = !s.starred
        _state.update { it.copy(starred = starred) }
        attempt { container.favorites.setStarred(s.item, starred) }.onFailure {
            Log.w(TAG, "Could not change the star", it)
            _state.update { it.copy(starred = !starred) }
        }
    }

    companion object {
        private const val TAG = "ArticleViewModel"
        private const val FALLBACK_TOPIC = "welt"

        private const val SETTINGS_TIMEOUT_MS = 5_000L

        /**
         * [content] arranged under the reader's head for [item]: the teaser is the feed's (else
         * the page's `og:description`); the one picture is the feed's, else the card's [cover],
         * else the page's lead picture, else the first picture of the text – and the text keeps
         * none. A line that only names the newsroom goes too.
         */
        internal fun arrange(content: ArticleContent, item: NewsItem, cover: String? = null): ArticleCleaner.Arranged =
            ArticleCleaner.arrange(
                content = content,
                teaser = item.summary.ifBlank { content.description },
                headline = item.headline,
                hero = item.image?.url?.takeIf { it.isNotBlank() } ?: cover?.takeIf { it.isNotBlank() } ?: content.image,
                pictureFromText = true,
                sources = listOf(item.source, item.publisherDomain, hostOf(item.url).removePrefix("www."), content.site),
            )

        /** The reader's text split into its short version and the whole ([shorten]). */
        internal data class Split(val content: ArticleContent, val full: ArticleContent? = null, val restMinutes: Int = 0)

        /**
         * [content] as the reader opens it: a long article as its short version ([shorten]) with
         * the whole one beside it, a short one as it is.
         */
        internal fun split(content: ArticleContent): Split {
            val short = shorten(content.blocks) ?: return Split(content)
            return Split(content = content.copy(blocks = short.short), full = content.copy(blocks = short.full), restMinutes = short.restMinutes)
        }

        /**
         * [content] without the lead paragraph(s) that repeat [summary] (see [ArticleCleaner.arrange]
         * for the teaser that is shown instead).
         */
        internal fun withoutTeaser(content: ArticleContent, summary: String): ArticleContent =
            ArticleCleaner.arrange(content, summary).content

        fun factory(route: ArticleRoute): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MorgenbriefingApp
                ArticleViewModel(container = app.container, route = route)
            }
        }
    }
}

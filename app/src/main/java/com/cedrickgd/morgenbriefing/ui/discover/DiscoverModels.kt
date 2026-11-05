package com.cedrickgd.morgenbriefing.ui.discover

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.SettingsOps
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import java.util.Locale

/** Headlines shown under a category, a sub-topic or a place. */
internal const val PREVIEW_SIZE = 3

/** One category tile of the grid. */
@Immutable
data class CategoryTileUi(
    val topic: Topic,
    val subtitle: String,
    /** How many sub-topics the category has (0 for "Schlagzeilen" and "Region"). */
    val subCount: Int,
    /** The category itself plus its followed sub-topics. */
    val followedCount: Int,
    val categoryFollowed: Boolean,
)

/** A topic that can be followed from the discover screen (category, sub-topic, search). */
@Immutable
data class FollowableUi(
    val topic: Topic,
    val subtitle: String,
    val followed: Boolean,
    /** False only for the last followed topic, which stays (the feed never ends up empty). */
    val canToggle: Boolean,
)

/** The newest headlines of one topic, loaded on demand. */
@Immutable
data class PreviewUi(
    val topicId: String,
    val items: List<NewsItem>,
    /** A refresh of the topic is running. */
    val loading: Boolean,
    /** The last refresh failed and nothing is cached. */
    val failed: Boolean,
    /** The topic was fetched at least once (else nothing can be said about it yet). */
    val fetched: Boolean,
) {
    /** Nothing to show yet, but something is coming: skeleton lines. */
    val waiting: Boolean get() = items.isEmpty() && !failed && (loading || !fetched)

    /** Fetched, nothing there. */
    val empty: Boolean get() = items.isEmpty() && !failed && !loading && fetched
}

/** A category opened from the grid. */
@Immutable
data class CategoryDetailUi(
    val category: FollowableUi,
    val subTopics: List<FollowableUi>,
    /** The category's own newest headlines. */
    val preview: PreviewUi,
    /** The sub-topic row that is open, showing [subPreview] under it. */
    val expandedSubId: String? = null,
    val subPreview: PreviewUi? = null,
)

/** A followed place ("Lokal"). */
@Immutable
data class PlaceUi(
    val topicId: String,
    val place: Place,
    /** The place's news language and country in the interface language ("Englisch", "Vereinigte Staaten"). */
    val language: String,
    val country: String,
    val followed: Boolean,
    /** Its newest headlines while the row is open; null when closed. */
    val preview: PreviewUi?,
)

/** Everything "Themen entdecken" shows. */
@Immutable
data class DiscoverUiState(
    /** False until the settings are known. */
    val loaded: Boolean = false,
    val followedCount: Int = 0,
    val categories: List<CategoryTileUi> = emptyList(),
    /** The open category; null on the grid. */
    val detail: CategoryDetailUi? = null,
    val places: List<PlaceUi> = emptyList(),
    /** Own topics that follow a search ("Midterms"). */
    val searches: List<FollowableUi> = emptyList(),
    val showImages: Boolean = true,
)

/** What the user picked on the screen; the rest of [DiscoverUiState] follows from the data. */
@Immutable
data class DiscoverSelection(
    val categoryId: String? = null,
    /** The sub-topic whose headlines are open under its row in the detail. */
    val previewId: String? = null,
    /** The place row that is open. */
    val expandedPlaceId: String? = null,
)

/**
 * Builds the discover screen from the settings, the repository's news and the user's
 * [selection]. Pure (unit-tested); runs off the main thread in the view model.
 *
 * @param loading topics being refreshed right now.
 * @param failed topics whose last refresh failed.
 */
internal fun buildDiscoverState(
    settings: AppSettings,
    lang: AppLanguage,
    news: Map<String, TopicNews>,
    loading: Set<String>,
    failed: Set<String>,
    selection: DiscoverSelection,
): DiscoverUiState {
    val followedIds = Topics.followedIds(settings)
    val followed = followedIds.toSet()

    fun followable(topic: Topic) = FollowableUi(
        topic = topic,
        subtitle = Topics.subtitle(topic, settings, lang),
        followed = topic.id in followed,
        canToggle = SettingsOps.canUnfollow(settings, topic.id),
    )

    fun preview(id: String) = PreviewUi(
        topicId = id,
        items = news[id]?.items.orEmpty().asSequence()
            .filter { it.feedSourceId !in settings.disabledFeeds }
            .take(PREVIEW_SIZE)
            .toList(),
        loading = id in loading,
        failed = id in failed && news[id]?.items.isNullOrEmpty(),
        fetched = news[id] != null,
    )

    val categories = Topics.categories(lang).map { generic ->
        val topic = Topics.resolve(generic.id, settings, lang) ?: generic
        val children = Topics.children(topic.id, lang)
        CategoryTileUi(
            topic = topic,
            subtitle = Topics.subtitle(topic, settings, lang),
            subCount = children.size,
            followedCount = (if (topic.id in followed) 1 else 0) + children.count { it.id in followed },
            categoryFollowed = topic.id in followed,
        )
    }

    val detail = selection.categoryId
        ?.let { id -> categories.firstOrNull { it.topic.id == id } }
        ?.let { tile ->
            val subs = Topics.children(tile.topic.id, lang).map(::followable)
            val expanded = subs.firstOrNull { it.topic.id == selection.previewId }?.topic?.id
            CategoryDetailUi(
                category = followable(tile.topic),
                subTopics = subs,
                preview = preview(tile.topic.id),
                expandedSubId = expanded,
                subPreview = expanded?.let(::preview),
            )
        }

    val uiLocale = lang.locale
    val places = settings.localPlaces.map { place ->
        val id = Topics.localId(place)
        val edition = placeEdition(place, settings)
        PlaceUi(
            topicId = id,
            place = place,
            language = displayLanguage(edition, uiLocale),
            country = displayCountry(edition, uiLocale),
            followed = id in followed,
            preview = if (id == selection.expandedPlaceId) preview(id) else null,
        )
    }

    val searches = settings.customTopics
        .filter { !it.query.isNullOrBlank() }
        .map { followable(it.copy(custom = true)) }

    return DiscoverUiState(
        loaded = true,
        followedCount = Topics.visible(settings, lang).size,
        categories = categories,
        detail = detail,
        places = places,
        searches = searches,
        showImages = settings.showImages,
    )
}

/** The topic whose headlines the screen should keep fresh for [selection]; null when none is shown. */
internal fun previewTarget(selection: DiscoverSelection): String? =
    selection.expandedPlaceId?.takeIf { selection.categoryId == null }
        ?: selection.categoryId?.let { selection.previewId ?: it }

/** The edition a place's news come from: its country's own, else the main edition (as the engine resolves it). */
internal fun placeEdition(place: Place, settings: AppSettings): NewsEdition =
    NewsEdition.forCountry(place.countryCode) ?: settings.edition

/** "Englisch" for an English edition in a German interface. */
internal fun displayLanguage(edition: NewsEdition, uiLocale: Locale): String =
    Locale.forLanguageTag(edition.language).getDisplayLanguage(uiLocale)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(uiLocale) else it.toString() }
        .let { name -> if (uiLocale.language in LOWERCASE_LANGUAGE_NAMES) name.lowercase(uiLocale) else name }

/** "Vereinigte Staaten" for a US edition in a German interface. */
internal fun displayCountry(edition: NewsEdition, uiLocale: Locale): String =
    Locale.Builder().setRegion(edition.country).build().getDisplayCountry(uiLocale)

/** Interface languages that write language names in lower case mid-sentence ("en anglais"). */
private val LOWERCASE_LANGUAGE_NAMES = setOf("fr", "es", "it")

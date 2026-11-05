package com.cedrickgd.morgenbriefing.data.model

import com.cedrickgd.morgenbriefing.data.news.Regions
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import com.cedrickgd.morgenbriefing.data.news.TopicDef
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.TopicStrings
import java.text.Normalizer
import java.util.Locale

/**
 * Every topic the app knows, as the feed and the settings use them: the built-in ones of
 * [Taxonomy] (titled in the interface language via [TopicStrings]), the places the user follows
 * (`local:` ids, [AppSettings.localPlaces]) and the user's own topics (`custom:` ids,
 * [AppSettings.customTopics]). Ids are stable; they are stored in the settings.
 *
 * What the feed shows is [AppSettings.followed] ([visible]); [catalogue] adds the categories,
 * places and own topics that are not followed (for the old topic sheet and the settings grid).
 */
object Topics {
    const val TOP_ID = Taxonomy.TOP
    const val REGION_ID = Taxonomy.REGION

    /** Topic id of search results ([com.cedrickgd.morgenbriefing.data.NewsRepository.search]); never followed. */
    const val SEARCH_ID = "search"

    const val LOCAL_PREFIX = "local:"
    private const val CUSTOM_PREFIX = "custom:"

    /** Neutral color for topics the app does not know (a custom topic that was deleted). */
    const val NEUTRAL_SEED = 0xFF5F6B7A

    /** Color of place topics: the "Region" orange. */
    const val LOCAL_SEED = 0xFFC6613F

    /** The categories in display order. */
    val DEFAULT_ORDER: List<String> = Taxonomy.CATEGORIES.map { it.id }

    /**
     * The ten built-in topics of app version 2 in their order. Installs from then stored their
     * order relative to these; only the settings migration reads it.
     */
    val LEGACY_ORDER: List<String> = listOf(
        "ki", "wirtschaft", "politik", "welt", REGION_ID, "sport", "wissenschaft", "gesundheit", "klima", "entertainment",
    )

    /**
     * Every built-in topic (categories and sub-topics) titled in [AppLanguage.FALLBACK]; for
     * checks over all topics (colors, ids). Screens use [all] with the interface language.
     */
    val ALL: List<Topic> by lazy { all(AppLanguage.FALLBACK) }

    /** Every built-in topic, categories followed by their sub-topics, titled in [lang]. */
    fun all(lang: AppLanguage): List<Topic> = Taxonomy.ALL.map { it.toTopic(lang) }

    /** The categories, titled in [lang]. */
    fun categories(lang: AppLanguage): List<Topic> = Taxonomy.CATEGORIES.map { it.toTopic(lang) }

    /** The sub-topics of [categoryId], titled in [lang]. */
    fun children(categoryId: String, lang: AppLanguage): List<Topic> = Taxonomy.children(categoryId).map { it.toTopic(lang) }

    /** A built-in topic by id with its generic title (the region topic as "Region"), or null. */
    fun builtIn(id: String, lang: AppLanguage): Topic? = Taxonomy.byId(id)?.toTopic(lang)

    /** One line on what [topic] covers, in [lang] (built-in, place, search or own topic). */
    fun subtitle(topic: Topic, settings: AppSettings, lang: AppLanguage = settings.language): String {
        val strings = TopicStrings.of(lang)
        return when {
            isLocalId(topic.id) -> strings.localSubtitle(placeFor(topic.id, settings)?.name ?: topic.title)
            topic.id == REGION_ID && Regions.servesEdition(settings.edition) ->
                Regions.byKey(settings.region).feeds.joinToString(", ") { it.name }
            topic.custom && !topic.query.isNullOrBlank() -> strings.searchSubtitle(topic.query)
            topic.custom -> strings.customSubtitle
            else -> strings.subtitle(topic.id).orEmpty()
        }
    }

    /** True for ids the user made up (own topics, followed searches). */
    fun isCustomId(id: String): Boolean = id.startsWith(CUSTOM_PREFIX)

    /** True for the topics of followed places. */
    fun isLocalId(id: String): Boolean = id.startsWith(LOCAL_PREFIX)

    /** The id of a new custom topic. */
    fun newCustomId(): String = CUSTOM_PREFIX + java.util.UUID.randomUUID().toString().take(8)

    /**
     * The stable topic id of a followed place: `local:washington-us-1a2b` (name slug, country,
     * a short hash of the rounded coordinates so two Springfields differ).
     */
    fun localId(place: Place): String {
        val slug = Normalizer.normalize(place.name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trimEnd('-')
            .ifEmpty { "place" }
        val country = place.countryCode.lowercase(Locale.ROOT).filter { it in 'a'..'z' }.ifEmpty { "xx" }
        val where = String.format(Locale.ROOT, "%.2f,%.2f", place.lat, place.lon)
        val hash = (where.hashCode().toLong() and 0xFFFF).toString(16).padStart(4, '0')
        return "$LOCAL_PREFIX$slug-$country-$hash"
    }

    /** The followed place behind a `local:` id, or null. */
    fun placeFor(id: String, settings: AppSettings): Place? =
        if (!isLocalId(id)) null else settings.localPlaces.firstOrNull { localId(it) == id }

    /**
     * The ids the feed shows, in order. [AppSettings.followed] is the source of truth; settings
     * built in code without it (tests, previews, [AppSettings] defaults) fall back to the old
     * [AppSettings.topicOrder] minus [AppSettings.hiddenTopics], as version 2 showed them.
     */
    fun followedIds(settings: AppSettings): List<String> =
        settings.followed.ifEmpty { legacyFollowed(settings.topicOrder, settings.hiddenTopics, settings.customTopics.map { it.id }) }

    /**
     * What a version 2 install showed: its stored order (ids it no longer knows dropped, the
     * categories and own topics missing from it appended) without the hidden topics.
     */
    fun legacyFollowed(order: List<String>, hidden: Set<String>, customIds: List<String>, known: List<String> = DEFAULT_ORDER): List<String> {
        val all = known + customIds
        val ordered = order.filter { it in all }.distinct()
        return (ordered + all.filterNot { it in ordered }).filterNot { it in hidden }
    }

    /** The followed topics in order, titled in [lang]; ids that no longer resolve are skipped. */
    fun visible(settings: AppSettings, lang: AppLanguage = settings.language): List<Topic> =
        followedIds(settings).distinct().mapNotNull { resolve(it, settings, lang) }

    /**
     * [visible] followed by every category, place and own topic that is not followed (in that
     * order). Sub-topics appear only when followed.
     */
    fun catalogue(settings: AppSettings, lang: AppLanguage = settings.language): List<Topic> {
        val followed = visible(settings, lang)
        val seen = followed.mapTo(HashSet()) { it.id }
        val rest = buildList {
            addAll(categories(lang).map { it.withRegionTitle(settings) })
            settings.localPlaces.forEach { add(localTopic(it)) }
            settings.customTopics.forEach { add(it.copy(custom = true)) }
        }.filter { seen.add(it.id) }
        return followed + rest
    }

    /** Ids of [catalogue] that are not followed: what the old topic sheet shows as switched off. */
    fun hidden(settings: AppSettings): Set<String> {
        val followed = followedIds(settings).toSet()
        return catalogue(settings).map { it.id }.filterNot { it in followed }.toSet()
    }

    /** Resolves [id] against the built-in topics, the followed places and the user's topics; null when unknown. */
    fun resolve(id: String, settings: AppSettings, lang: AppLanguage = settings.language): Topic? = when {
        isLocalId(id) -> placeFor(id, settings)?.let(::localTopic)
        isCustomId(id) -> settings.customTopics.firstOrNull { it.id == id }?.copy(custom = true)
        id == SEARCH_ID -> searchTopic(lang)
        else -> Taxonomy.byId(id)?.toTopic(lang)?.withRegionTitle(settings)
    }

    /** Like [resolve], but never null: unknown ids get a neutral stand-in. */
    fun resolveOrNeutral(id: String, settings: AppSettings, lang: AppLanguage = settings.language): Topic =
        resolve(id, settings, lang)
            ?: Topic(id, id.substringAfterLast('/').replaceFirstChar { it.uppercase() }, "📰", NEUTRAL_SEED, custom = isCustomId(id), icon = TopicIcon.NEWSPAPER.name)

    /** The topic of a followed place. */
    fun localTopic(place: Place): Topic =
        Topic(localId(place), place.name, "📍", LOCAL_SEED, icon = TopicIcon.LOCATION.name)

    /** The stand-in topic of search results. */
    fun searchTopic(lang: AppLanguage): Topic =
        Topic(SEARCH_ID, TopicStrings.of(lang).title(SEARCH_ID).orEmpty(), "🔎", NEUTRAL_SEED, icon = TopicIcon.SEARCH.name)

    private fun TopicDef.toTopic(lang: AppLanguage): Topic = Topic(
        id = id,
        title = TopicStrings.of(lang).title(id) ?: id,
        emoji = emoji,
        seed = seed,
        icon = icon.name,
        parentId = parentId,
    )

    /** In Germany the region is the chosen Bundesland ("Sachsen"); elsewhere the home place ("Vienna"). */
    private fun Topic.withRegionTitle(settings: AppSettings): Topic = when {
        id != REGION_ID -> this
        Regions.servesEdition(settings.edition) -> copy(title = Regions.byKey(settings.region).name)
        settings.home.name.isNotBlank() -> copy(title = settings.home.name)
        else -> this
    }
}

package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.NewsSection
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import com.cedrickgd.morgenbriefing.data.news.TopicSources
import com.cedrickgd.morgenbriefing.data.remote.FeedError
import com.cedrickgd.morgenbriefing.data.remote.FeedException
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.SourcesStrings

/*
 * Pure logic of Settings → "Quellen": which sources each topic reads for the current edition,
 * how a Google News source is described in one understandable line, and how the last fetch of
 * a source went. Unit-tested.
 */

/** What a Google News source of a topic reads, for its one-line description. */
@Immutable
internal sealed interface GoogleNewsLine {
    /** The edition's top stories. */
    data object TopStories : GoogleNewsLine

    /** A section of the edition ("Wirtschaft"). */
    data class Section(val section: NewsSection) : GoogleNewsLine

    /** A search, shortened for display ("Politik, Bundestag, Bundesregierung …"). */
    data class Search(val query: String) : GoogleNewsLine

    /** The news of a place ("Leipzig"). */
    data class Local(val place: String) : GoogleNewsLine
}

/** How the last fetch of a source went. */
@Immutable
internal sealed interface SourceHealth {
    data class Ok(val count: Int) : SourceHealth
    data class Failed(val error: FeedError, val httpCode: Int?) : SourceHealth
}

/** One topic of the sources section with every source it reads (switched-off ones included). */
@Immutable
internal data class TopicSourcesGroup(
    val topic: Topic,
    val sources: List<FeedSource>,
    val followed: Boolean,
    /** Built-in feeds left out because the reader cannot show their articles ([AppSettings.readableOnly]). */
    val hiddenUnreadable: Int,
)

internal object SourcesLogic {

    /** At most this many terms of a search are shown. */
    private const val SHOWN_TERMS = 3

    /**
     * The topics of the section: the followed ones in feed order, then own topics that are not
     * followed (so their feeds stay editable), each with its sources for the current edition.
     * [blocked] are the sites the reader cannot show (as the news engine gets them).
     */
    fun groups(settings: AppSettings, blocked: Collection<String>, lang: AppLanguage): List<TopicSourcesGroup> {
        val followed = Topics.visible(settings, lang)
        val followedIds = followed.mapTo(HashSet()) { it.id }
        val paused = settings.customTopics.filter { it.id !in followedIds }.map { it.copy(custom = true) }
        val everything = if (settings.readableOnly) settings.copy(readableOnly = false) else null
        return (followed + paused).map { topic ->
            val sources = TopicSources.resolve(topic.id, settings, blocked, includeDisabled = true)
            val hidden = everything?.let { all ->
                val shown = sources.mapTo(HashSet()) { it.id }
                TopicSources.resolve(topic.id, all, blocked, includeDisabled = true).count { !it.isGoogleNews && it.id !in shown }
            } ?: 0
            TopicSourcesGroup(topic, sources, topic.id in followedIds, hidden)
        }
    }

    /** What the Google News [source] of a topic reads. */
    fun googleLine(source: FeedSource, settings: AppSettings): GoogleNewsLine {
        val topicId = source.topicId
        return when {
            Topics.isLocalId(topicId) -> GoogleNewsLine.Local(Topics.placeFor(topicId, settings)?.name ?: source.name)
            topicId == Topics.REGION_ID -> GoogleNewsLine.Local(settings.home.name)
            Topics.isCustomId(topicId) -> GoogleNewsLine.Search(
                shortQuery(settings.customTopics.firstOrNull { it.id == topicId }?.query.orEmpty()),
            )
            else -> {
                val def = Taxonomy.byId(topicId)
                val edition = NewsEdition.fromKey(source.edition) ?: settings.edition
                // A pair Google News has no edition of searches for the country instead ([TopicSources.googleFeed]).
                when (def?.let { TopicSources.googleFeed(it, edition) }) {
                    null, TopicSources.GoogleFeed.TOP_STORIES -> GoogleNewsLine.TopStories
                    TopicSources.GoogleFeed.SECTION -> def.section?.let { GoogleNewsLine.Section(it) } ?: GoogleNewsLine.TopStories
                    TopicSources.GoogleFeed.SEARCH -> GoogleNewsLine.Search(shortQuery(def.queryFor(edition).orEmpty()))
                }
            }
        }
    }

    /** [line] as the settings show it: "Google News · Suche: Börse, DAX, Aktien …". */
    fun describe(line: GoogleNewsLine, lang: AppLanguage): String {
        val s = SourcesStrings.of(lang)
        return when (line) {
            GoogleNewsLine.TopStories -> s.googleTopStories
            is GoogleNewsLine.Section -> s.googleSection(s.section(line.section))
            is GoogleNewsLine.Search -> s.googleSearch(line.query)
            is GoogleNewsLine.Local -> s.googlePlace(line.place)
        }
    }

    /**
     * A Google News query in a few readable words: the first [SHOWN_TERMS] alternatives of an
     * `OR` list without quotes and operators, "…" when there are more.
     * `"White House" OR Congress OR Senate OR lawmakers` → `White House, Congress, Senate …`.
     */
    fun shortQuery(query: String): String {
        val terms = query.split(Regex("\\s+OR\\s+"))
            .map { term ->
                term.split(Regex("\\s+"))
                    .filterNot { it.startsWith("-") || it.contains(':') }
                    .joinToString(" ")
                    .replace("\"", "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
        if (terms.isEmpty()) return query.trim()
        val shown = terms.take(SHOWN_TERMS).joinToString(", ")
        return if (terms.size > SHOWN_TERMS) "$shown …" else shown
    }

    /** How [status] went, or null when the source was not fetched yet. */
    fun health(status: SourceStatus?): SourceHealth? {
        status ?: return null
        if (status.ok) return SourceHealth.Ok(status.count)
        val (error, code) = FeedException.parse(status.error) ?: (FeedError.UNREADABLE to null)
        return SourceHealth.Failed(error, code)
    }

    /** "20 Meldungen" or the reason in [lang]. */
    fun healthLabel(health: SourceHealth, lang: AppLanguage): String {
        val s = SourcesStrings.of(lang)
        return when (health) {
            is SourceHealth.Ok -> s.articles(health.count)
            is SourceHealth.Failed -> s.feedError(health.error, health.httpCode)
        }
    }

    /** The one-line summary of a topic's row: "4 Quellen", "3 von 4 Quellen an", plus hints. */
    fun summary(group: TopicSourcesGroup, disabled: Set<String>, lang: AppLanguage): String {
        val s = SourcesStrings.of(lang)
        val total = group.sources.size
        val on = group.sources.count { it.id !in disabled }
        val base = when {
            total == 0 -> s.noSourceYet
            on == total -> s.sourceCount(total)
            else -> s.sourcesOn(on, total)
        }
        return if (group.followed) base else "$base · ${s.notFollowedSuffix}"
    }
}

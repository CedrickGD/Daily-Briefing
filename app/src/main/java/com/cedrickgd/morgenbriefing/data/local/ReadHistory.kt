package com.cedrickgd.morgenbriefing.data.local

import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.news.StoryTwins
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import kotlinx.serialization.Serializable

/*
 * The reading history as plain values (unit-tested): one entry per story the user read or hid,
 * an immutable snapshot of all of them, and the index that tells whether a feed article is one
 * of those stories – also when it arrives from another feed, with another link.
 */

/**
 * One story in the reading history ([ReadHistoryStore]): opened in the reader or the browser,
 * listened to, marked read by hand ([readAt]) – or hidden by hand from every list ([hiddenAt]),
 * or both. Keeps what the archive shows (title, source, picture, time) and what the reader needs
 * ([url]), so a story stays reachable after the feed dropped it.
 *
 * @property key the feed article's id ([NewsItem.id]).
 * @property domain the publisher's site without `www.` ([NewsItem.publisherDomain] or the link's host).
 * @property resolvedUrl the publisher's own link behind a Google News link, when it was known.
 * @property aggregator "google" for a Google News article ([NewsItem.aggregator]).
 */
@Serializable
data class HistoryEntry(
    val key: String,
    val title: String,
    val url: String,
    val source: String = "",
    val domain: String = "",
    val image: String? = null,
    val publishedAt: String? = null,
    val topicId: String = "",
    val readAt: Long? = null,
    val hiddenAt: Long? = null,
    val resolvedUrl: String? = null,
    val aggregator: String? = null,
    /** When the user starred the story ([FavoritesStore]); the reading history never sets it. */
    val starredAt: Long? = null,
) {
    val read: Boolean get() = readAt != null
    val hidden: Boolean get() = hiddenAt != null
    val starred: Boolean get() = starredAt != null

    /** When the user last did something with the story (archive order, pruning, the favorites' day). */
    val touchedAt: Long get() = maxOf(readAt ?: 0L, hiddenAt ?: 0L, starredAt ?: 0L)

    /** The story as a feed article again: for the reader, sharing, copying and matching. */
    fun toItem(): NewsItem = NewsItem(
        id = key,
        headline = title,
        url = url,
        source = source,
        sourceId = "",
        topicId = topicId,
        publishedAt = publishedAt,
        image = image?.let { ArticleImage(it) },
        publisherDomain = domain,
        aggregator = aggregator,
    )

    companion object {
        /** The entry for [item], neither read nor hidden yet; [resolved]: the publisher's link behind its link, if known. */
        fun of(item: NewsItem, resolved: String? = null): HistoryEntry = HistoryEntry(
            key = item.id,
            title = item.headline.trim(),
            url = item.url,
            source = item.source.trim(),
            domain = item.publisherDomain.trim().lowercase().removePrefix("www."),
            image = item.image?.url?.takeIf { it.isNotBlank() },
            publishedAt = item.publishedAt,
            topicId = item.topicId,
            resolvedUrl = resolved?.takeIf { it.isNotBlank() && it != item.url },
            aggregator = item.aggregator,
        )
    }
}

/**
 * Finds the history entry of a feed article by the same story identity the feed folds
 * duplicates with ([NewsRepository.dedupe], [StoryTwins]): the same id, the same (resolved)
 * link, the same headline once case, accents and punctuation are folded, or the same publisher
 * with nearly the same headline at nearly the same time. So a story read in "Politik" also counts
 * as read when Google News brings it to "Schlagzeilen" with another link.
 *
 * Immutable once built and safe to read from several threads. Equal when it holds the same
 * entries (keys): a new index for the same stories changes nothing for the lists.
 *
 * @param resolved the publisher's link behind a Google News link, when known
 *   ([com.cedrickgd.morgenbriefing.data.remote.GoogleNewsDecoder.peek]).
 */
class StoryIndex(entries: Collection<HistoryEntry>, private val resolved: (String) -> String? = { null }) {
    private val list: List<HistoryEntry> = entries.toList()

    /** The keys of the entries held. */
    val keys: Set<String> = list.mapTo(HashSet(list.size * 2)) { it.key }

    private val byKey = HashMap<String, HistoryEntry>(list.size * 2)
    private val byLink = HashMap<String, HistoryEntry>(list.size * 2)
    private val byHeadline = HashMap<String, HistoryEntry>(list.size * 2)
    private val twins = StoryTwins.Finder()

    init {
        list.forEachIndexed { index, entry ->
            byKey.putIfAbsent(entry.key, entry)
            linkKeys(entry.url, entry.resolvedUrl).forEach { byLink.putIfAbsent(it, entry) }
            val item = entry.toItem()
            NewsRepository.headlineKey(item)?.let { byHeadline.putIfAbsent(it, entry) }
            twins.add(item, index)
        }
    }

    val isEmpty: Boolean get() = list.isEmpty()
    val size: Int get() = list.size

    /** The entry that stands for [item]'s story, or null when the history does not know it. */
    fun find(item: NewsItem): HistoryEntry? {
        if (list.isEmpty()) return null
        byKey[item.id]?.let { return it }
        for (link in linkKeys(item.url, null)) byLink[link]?.let { return it }
        NewsRepository.headlineKey(item)?.let { headline -> byHeadline[headline]?.let { return it } }
        return twins.find(item)?.let(list::getOrNull)
    }

    /** Every entry that stands for [item]'s story (the same story can have been read from two feeds). */
    fun findAll(item: NewsItem): List<HistoryEntry> {
        if (list.isEmpty()) return emptyList()
        val links = linkKeys(item.url, null)
        val headline = NewsRepository.headlineKey(item)
        val found = LinkedHashMap<String, HistoryEntry>()
        for (entry in list) {
            if (entry.key == item.id ||
                linkKeys(entry.url, entry.resolvedUrl).any { it in links } ||
                (headline != null && NewsRepository.headlineKey(entry.toItem()) == headline)
            ) {
                found[entry.key] = entry
            }
        }
        twins.find(item)?.let(list::getOrNull)?.let { found.putIfAbsent(it.key, it) }
        return found.values.toList()
    }

    /** True when [item] is one of the stories held. */
    operator fun contains(item: NewsItem): Boolean = find(item) != null

    /**
     * The link keys of a story: its link and the publisher's link behind it ([known], else asked
     * of [resolved] for a Google News link), normalized like article ids.
     */
    private fun linkKeys(url: String, known: String?): Set<String> {
        val out = LinkedHashSet<String>(3)
        if (url.isBlank()) return out
        out += normalized(url)
        val behind = known?.takeIf { it.isNotBlank() } ?: if (GOOGLE_NEWS_HOST in url) resolved(url) else null
        behind?.takeIf { it.isNotBlank() }?.let { out += normalized(it) }
        return out
    }

    override fun equals(other: Any?): Boolean = other is StoryIndex && other.keys == keys

    override fun hashCode(): Int = keys.hashCode()

    companion object {
        val EMPTY = StoryIndex(emptyList())

        private const val GOOGLE_NEWS_HOST = "news.google.com"
        private const val MAX_CACHED_LINKS = 8000

        /** Normalizing a link runs a few regular expressions: done once per link, indexes and lists are built often. */
        private val links = object : LinkedHashMap<String, String>(512, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean = size > MAX_CACHED_LINKS
        }

        private fun normalized(url: String): String = synchronized(links) { links.getOrPut(url) { RssClient.normalizeLinkForId(url) } }
    }
}

/**
 * Everything the history holds at one moment: the entries newest first ([HistoryEntry.touchedAt])
 * and, built when first asked, the indexes of the read and of the hidden stories. Immutable; a
 * change of the store publishes a new one.
 */
class ReadHistory(entries: List<HistoryEntry>, private val resolved: (String) -> String? = { null }) {
    /** Newest first. */
    val entries: List<HistoryEntry> = entries.sortedByDescending { it.touchedAt }

    /** The stories the user read. */
    val read: StoryIndex by lazy { StoryIndex(this.entries.filter { it.read }, resolved) }

    /** The stories the user hid by hand. */
    val hidden: StoryIndex by lazy { StoryIndex(this.entries.filter { it.hidden }, resolved) }

    /** Every story held, read or hidden. */
    val all: StoryIndex by lazy { StoryIndex(this.entries, resolved) }

    val isEmpty: Boolean get() = entries.isEmpty()
    val readCount: Int get() = entries.count { it.read }
    val hiddenCount: Int get() = entries.count { it.hidden }

    /** True when the user read [item]'s story. */
    fun isRead(item: NewsItem): Boolean = item in read

    /** True when the user hid [item]'s story by hand. */
    fun isHidden(item: NewsItem): Boolean = item in hidden

    companion object {
        val EMPTY = ReadHistory(emptyList())
    }
}

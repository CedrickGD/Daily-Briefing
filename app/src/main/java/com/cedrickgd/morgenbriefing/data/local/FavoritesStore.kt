package com.cedrickgd.morgenbriefing.data.local

import android.util.Log
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The stories the user starred ("Favoriten"), kept across app starts: ⋮ → "Zu Favoriten" on a
 * card, or the star in the reader's bar. The favorites screen lists them, newest star first.
 *
 * One entry per story ([HistoryEntry] with [HistoryEntry.starredAt]): a story already starred
 * under another link or from another feed ([StoryIndex], the identity the feed folds duplicates
 * with) is not starred twice. Never pruned: a favorite only goes when the user removes its star.
 *
 * Persisted as JSON (`<dir>/favorites.json`); writes are collected for [SAVE_DELAY_MS] and done on
 * [scope]. [state] publishes every change at once. Thread-safe; [warm] reads the file off the main
 * thread (call it early – a change before that reads it on the caller's thread).
 *
 * @param resolved the publisher's link behind a Google News link, when known.
 */
class FavoritesStore(
    private val file: File,
    private val json: Json,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val resolved: (String) -> String? = { null },
) {
    @Serializable
    private data class Snapshot(val version: Int = VERSION, val entries: List<HistoryEntry> = emptyList())

    private val lock = Any()

    /** Key → entry. Guarded by [lock]. */
    private val entries = LinkedHashMap<String, HistoryEntry>()
    @Volatile private var loaded = false
    private val writeLock = Mutex()
    private var pendingWrite: Job? = null

    private val mutableState = MutableStateFlow(Favorites.EMPTY)

    /** Every starred story, newest star first; a new value after every change. */
    val state: StateFlow<Favorites> = mutableState.asStateFlow()

    /** Reads the file once (on IO), so [state] holds what earlier sessions kept. */
    suspend fun warm() {
        if (loaded) return
        withContext(Dispatchers.IO) { ensureLoaded() }
    }

    /** Stars [item]'s story (now); a story starred already keeps its first star. */
    fun star(item: NewsItem) {
        if (item.id.isBlank() || item.url.isBlank()) return
        update { map ->
            if (mutableState.value.index.find(item)?.takeIf { map[it.key] == it } != null) return@update
            map[item.id] = HistoryEntry.of(item, resolved(item.url)).copy(starredAt = clock())
        }
    }

    /** Takes the star off [item]'s story (every entry that stands for it). */
    fun unstar(item: NewsItem) {
        update { map -> mutableState.value.index.findAll(item).forEach { map.remove(it.key) } }
    }

    /** Stars [item] ([starred]) or takes its star off. */
    fun setStarred(item: NewsItem, starred: Boolean) = if (starred) star(item) else unstar(item)

    /** The favorites screen's row [key] loses its star; [restore] brings it back. */
    fun remove(key: String) {
        update { map -> map.remove(key) }
    }

    /** Puts [entry] back as it was (the undo of removing a star). */
    fun restore(entry: HistoryEntry) {
        if (entry.key.isBlank() || entry.url.isBlank() || !entry.starred) return
        update { map ->
            map.remove(entry.key)
            map[entry.key] = entry
        }
    }

    /** Writes the favorites now (also what every change schedules). */
    suspend fun save() {
        try {
            withContext(Dispatchers.IO) {
                writeLock.withLock {
                    val payload = synchronized(lock) { json.encodeToString(Snapshot.serializer(), Snapshot(entries = entries.values.toList())) }
                    JsonFiles.writeAtomically(file, payload)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the favorites", e)
        }
    }

    /** Runs [block] on the entries, publishes the new state and schedules a write. */
    private fun update(block: (LinkedHashMap<String, HistoryEntry>) -> Unit) {
        ensureLoaded()
        synchronized(lock) {
            block(entries)
            publish()
            if (pendingWrite?.isActive == true) return
            pendingWrite = scope.launch {
                delay(SAVE_DELAY_MS)
                save()
            }
        }
    }

    /** Under [lock]. */
    private fun publish() {
        mutableState.value = Favorites(entries.values.toList(), resolved)
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val snapshot = JsonFiles.readOrNull(file)?.let { text ->
                try {
                    json.decodeFromString(Snapshot.serializer(), text)
                } catch (e: Exception) {
                    Log.w(TAG, "Favorites unreadable, starting over", e)
                    null
                }
            }
            snapshot?.takeIf { it.version == VERSION }?.entries
                ?.filter { it.key.isNotBlank() && it.url.isNotBlank() && it.starred }
                ?.sortedBy { it.starredAt }
                ?.forEach { entries[it.key] = it }
            publish()
            loaded = true
        }
    }

    companion object {
        private const val TAG = "FavoritesStore"
        const val VERSION = 1

        /** Changes within this time are written together. */
        const val SAVE_DELAY_MS = 1_500L
    }
}

/**
 * Every starred story at one moment: the entries newest star first and, built when first asked,
 * the index that tells whether a feed article is one of them – also under another link. Immutable;
 * a change of the store publishes a new one.
 */
class Favorites(entries: List<HistoryEntry>, private val resolved: (String) -> String? = { null }) {
    /** Newest star first. */
    val entries: List<HistoryEntry> = entries.sortedByDescending { it.starredAt ?: 0L }

    val index: StoryIndex by lazy { StoryIndex(this.entries, resolved) }

    val isEmpty: Boolean get() = entries.isEmpty()
    val size: Int get() = entries.size

    /** True when the user starred [item]'s story. */
    fun isStarred(item: NewsItem): Boolean = item in index

    companion object {
        val EMPTY = Favorites(emptyList())
    }
}

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
import java.time.Duration

/**
 * The stories the user read or hid ("Gelesen & ausgeblendet"), kept across app starts: an
 * article counts as read once it is opened in the reader or the browser or listened to; hidden
 * ones were taken out of every list by hand (⋮ → "Ausblenden"). The feed calms read cards and
 * – with "Gelesene Artikel ausblenden" – leaves them out; hidden ones never show. The archive
 * screen lists every entry and brings stories back.
 *
 * One entry per story: an article whose story is already held ([StoryIndex], the same identity
 * the feed folds duplicates with) updates that entry instead of adding a second one.
 *
 * Bounded: entries untouched for [keepFor] go, and at most [maxEntries] stay (the oldest go
 * first) – pruned on every change and when read. Persisted as JSON (`<dir>/read-history.json`);
 * writes are collected for [SAVE_DELAY_MS] and done on [scope]. [state] publishes every change at
 * once. Thread-safe; [warm] reads the file off the main thread (call it early – a change before
 * that reads it on the caller's thread).
 *
 * @param resolved the publisher's link behind a Google News link, when known.
 */
class ReadHistoryStore(
    private val file: File,
    private val json: Json,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = MAX_ENTRIES,
    private val keepFor: Duration = KEEP_FOR,
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

    private val mutableState = MutableStateFlow(ReadHistory.EMPTY)

    /** Everything held, newest first; a new value after every change. */
    val state: StateFlow<ReadHistory> = mutableState.asStateFlow()

    /** Reads the file once (on IO), so [state] holds what earlier sessions kept. */
    suspend fun warm() {
        if (loaded) return
        withContext(Dispatchers.IO) { ensureLoaded() }
    }

    /** [item] was opened or listened to: its story counts as read (now). */
    fun markRead(item: NewsItem) = touch(item) { it.copy(readAt = clock()) }

    /** Hides [item]'s story from every list (⋮ → "Ausblenden"); [unhide] undoes it. */
    fun hide(item: NewsItem) = touch(item) { it.copy(hiddenAt = clock()) }

    /** [item]'s story counts as unread again (every entry that stands for it). */
    fun markUnread(item: NewsItem) = change { current -> current.all.findAll(item).map { it.copy(readAt = null) } }

    /** [item]'s story shows again (every entry that stands for it) – also the undo of [hide]. */
    fun unhide(item: NewsItem) = change { current -> current.all.findAll(item).map { it.copy(hiddenAt = null) } }

    /** The archive's row [key]: read or not. */
    fun setRead(key: String, read: Boolean) = changeKey(key) { it.copy(readAt = if (read) it.readAt ?: clock() else null) }

    /** The archive's row [key]: hidden or not. */
    fun setHidden(key: String, hidden: Boolean) = changeKey(key) { it.copy(hiddenAt = if (hidden) it.hiddenAt ?: clock() else null) }

    /** Puts [entry] back as it was (the undo of an archive action). */
    fun restore(entry: HistoryEntry) {
        if (entry.key.isBlank()) return
        update { map ->
            map.remove(entry.key)
            if (entry.read || entry.hidden) map[entry.key] = entry
        }
    }

    /** Forgets everything ("Verlauf löschen"); hidden stories show again. Written at once. */
    fun clear() {
        update { it.clear() }
        synchronized(lock) { pendingWrite?.cancel() }
        scope.launch { save() }
    }

    /** Writes the history now (also what every change schedules). */
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
            Log.w(TAG, "Could not save the reading history", e)
        }
    }

    /**
     * Applies [edit] to the entry of [item]'s story – the one already held for it, else a new
     * one – keeping what the held entry lacks from [item] (title, picture).
     */
    private fun touch(item: NewsItem, edit: (HistoryEntry) -> HistoryEntry) {
        if (item.id.isBlank() || item.url.isBlank()) return
        val fresh = HistoryEntry.of(item, resolved(item.url))
        update { map ->
            // The published state mirrors the map; its indexes are usually built already (the feed reads them).
            val current = mutableState.value
            val held = (current.read.find(item) ?: current.hidden.find(item))?.takeIf { map[it.key] == it }
            val base = if (held == null) {
                fresh
            } else {
                held.copy(
                    title = held.title.ifBlank { fresh.title },
                    image = held.image ?: fresh.image,
                    source = held.source.ifBlank { fresh.source },
                    domain = held.domain.ifBlank { fresh.domain },
                    publishedAt = held.publishedAt ?: fresh.publishedAt,
                    resolvedUrl = held.resolvedUrl ?: fresh.resolvedUrl,
                )
            }
            map.remove(base.key)
            map[base.key] = edit(base)
        }
    }

    /** Replaces the entries [pick] returns; one neither read nor hidden any more is dropped. */
    private fun change(pick: (ReadHistory) -> List<HistoryEntry>) {
        update { map ->
            for (entry in pick(mutableState.value)) {
                map.remove(entry.key)
                if (entry.read || entry.hidden) map[entry.key] = entry
            }
        }
    }

    private fun changeKey(key: String, edit: (HistoryEntry) -> HistoryEntry) {
        update { map ->
            val entry = map[key] ?: return@update
            val next = edit(entry)
            map.remove(key)
            if (next.read || next.hidden) map[key] = next
        }
    }

    /** Runs [block] on the entries, prunes, publishes the new state and schedules a write. */
    private fun update(block: (LinkedHashMap<String, HistoryEntry>) -> Unit) {
        ensureLoaded()
        synchronized(lock) {
            block(entries)
            prune(entries, clock())
            publish()
            if (pendingWrite?.isActive == true) return
            pendingWrite = scope.launch {
                delay(SAVE_DELAY_MS)
                save()
            }
        }
    }

    /** Drops what is older than [keepFor] and the oldest beyond [maxEntries]. Under [lock]. */
    private fun prune(map: LinkedHashMap<String, HistoryEntry>, now: Long) {
        val before = now - keepFor.toMillis()
        map.values.removeAll { it.touchedAt < before || (!it.read && !it.hidden) }
        if (map.size <= maxEntries) return
        val oldest = map.values.sortedBy { it.touchedAt }.take(map.size - maxEntries).map { it.key }
        oldest.forEach(map::remove)
    }

    /** Under [lock]. */
    private fun publish() {
        mutableState.value = ReadHistory(entries.values.toList(), resolved)
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val snapshot = JsonFiles.readOrNull(file)?.let { text ->
                try {
                    json.decodeFromString(Snapshot.serializer(), text)
                } catch (e: Exception) {
                    Log.w(TAG, "Reading history unreadable, starting over", e)
                    null
                }
            }
            snapshot?.takeIf { it.version == VERSION }?.entries
                ?.filter { it.key.isNotBlank() && it.url.isNotBlank() }
                ?.sortedBy { it.touchedAt }
                ?.forEach { entries[it.key] = it }
            prune(entries, clock())
            publish()
            loaded = true
        }
    }

    companion object {
        private const val TAG = "ReadHistoryStore"
        const val VERSION = 1

        /** About two months of reading at a few dozen stories a day. */
        const val MAX_ENTRIES = 1000

        /** A story untouched this long leaves the history (the feeds dropped it long before). */
        val KEEP_FOR: Duration = Duration.ofDays(60)

        /** Changes within this time are written together. */
        const val SAVE_DELAY_MS = 1_500L
    }
}

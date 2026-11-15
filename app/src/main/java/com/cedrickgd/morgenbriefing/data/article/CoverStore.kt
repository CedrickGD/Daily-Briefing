package com.cedrickgd.morgenbriefing.data.article

import android.util.Log
import com.cedrickgd.morgenbriefing.data.local.JsonFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration

/**
 * The cover pictures found for articles whose feed carried none ([CoverResolver]), kept across
 * app starts so the cards show them at once – and the articles that have none, so they are not
 * asked for again before [retryAfter].
 *
 * Bounded: at most [maxEntries] (the oldest go first), a found picture is kept [keepFor].
 * Persisted as JSON (`<dir>/covers.json`); writes are collected for [SAVE_DELAY_MS] and done on
 * [scope]. Thread-safe; [restore] reads the file (call it off the main thread).
 */
class CoverStore(
    private val file: File,
    private val json: Json,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = MAX_ENTRIES,
    private val keepFor: Duration = KEEP_FOR,
    private val retryAfter: Duration = RETRY_AFTER,
) {
    /** [url] empty: looked at [at], the article has no picture. */
    @Serializable
    private data class Entry(val id: String, val url: String = "", val at: Long)

    @Serializable
    private data class Snapshot(val version: Int = VERSION, val entries: List<Entry> = emptyList())

    private val lock = Any()

    /** Item id → entry, oldest first. Guarded by [lock]. */
    private val entries = LinkedHashMap<String, Entry>()
    @Volatile private var loaded = false
    private val writeLock = Mutex()
    private var pendingWrite: Job? = null

    /** The pictures on disk that are still valid, by item id (reads the file). */
    fun restore(): Map<String, String> {
        ensureLoaded()
        return synchronized(lock) { entries.values.filter { it.url.isNotEmpty() }.associate { it.id to it.url } }
    }

    /** True while [itemId] is known to have no picture (looked up less than [retryAfter] ago). */
    fun isKnownMissing(itemId: String): Boolean {
        ensureLoaded()
        return synchronized(lock) { entries[itemId]?.let { it.url.isEmpty() && fresh(it, clock()) } == true }
    }

    /** Notes the picture [url] of [itemId]; null: it has none (asked again after [retryAfter]). Saved shortly after. */
    fun record(itemId: String, url: String?) {
        if (itemId.isBlank()) return
        ensureLoaded()
        synchronized(lock) {
            val old = entries[itemId]
            // The same picture again changes nothing; "none" never replaces a picture that is still kept.
            if (old != null && old.url.isNotEmpty() && fresh(old, clock()) && (url == null || url == old.url)) return
            entries.remove(itemId)
            entries[itemId] = Entry(itemId, url.orEmpty(), clock())
            while (entries.size > maxEntries) entries.remove(entries.keys.first())
            if (pendingWrite?.isActive == true) return
            pendingWrite = scope.launch {
                delay(SAVE_DELAY_MS)
                save()
            }
        }
    }

    /** Writes the entries now (also what [record] schedules). */
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
            Log.w(TAG, "Could not save the cover pictures", e)
        }
    }

    private fun fresh(entry: Entry, now: Long): Boolean =
        now - entry.at <= (if (entry.url.isEmpty()) retryAfter else keepFor).toMillis()

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val snapshot = JsonFiles.readOrNull(file)?.let { text ->
                try {
                    json.decodeFromString(Snapshot.serializer(), text)
                } catch (e: Exception) {
                    Log.w(TAG, "Cover pictures unreadable, starting over", e)
                    null
                }
            }
            val now = clock()
            snapshot?.takeIf { it.version == VERSION }?.entries
                ?.filter { it.id.isNotBlank() && fresh(it, now) && (it.url.isEmpty() || it.url.startsWith("https://")) }
                ?.sortedBy { it.at }
                ?.takeLast(maxEntries)
                ?.forEach { entries[it.id] = it }
            loaded = true
        }
    }

    companion object {
        private const val TAG = "CoverStore"
        const val VERSION = 1

        /** Enough for every followed topic's endless list. */
        const val MAX_ENTRIES = 6000

        /** A found picture is kept this long (articles stay in the lists for weeks). */
        val KEEP_FOR: Duration = Duration.ofDays(30)

        /** An article without a picture is asked again after this (newsrooms add pictures later). */
        val RETRY_AFTER: Duration = Duration.ofHours(12)

        /** Results arriving within this time are written together. */
        const val SAVE_DELAY_MS = 2_000L
    }
}

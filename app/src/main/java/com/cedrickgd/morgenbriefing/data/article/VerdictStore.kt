package com.cedrickgd.morgenbriefing.data.article

import android.util.Log
import com.cedrickgd.morgenbriefing.data.local.JsonFiles
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
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
 * What the reader found out about single articles, kept across app starts, so an article is
 * fetched and judged once ([ArticlePrefetcher]): verified complete ([ArticleStatus.READABLE]),
 * paid or only a teaser ([ArticleStatus.PAYWALL]), walled ([ArticleStatus.BLOCKED]) or no article
 * at all ([ArticleStatus.FAILED]). Network failures say nothing about the article and are never
 * recorded. The text of a complete article stays in the article cache, so it still opens
 * instantly and offline. With the verdict goes the article's length in words, when it was
 * measured ([LongReads], "Lange Artikel ausblenden"), so it is never fetched again for that.
 *
 * Bounded: at most [maxEntries] verdicts (the oldest go first), each kept [keepFor] – as long as
 * the article cache keeps the text ([ArticleRepository.KEEP_FOR]). Persisted as JSON
 * (`<dir>/verdicts.json`); writes are collected for [SAVE_DELAY_MS] and done on [scope].
 * Verdicts of an older judgement ([VERSION]) are dropped when read. Thread-safe.
 */
class VerdictStore(
    private val file: File,
    private val json: Json,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = MAX_ENTRIES,
    private val keepFor: Duration = ArticleRepository.KEEP_FOR,
) {
    /** [words]: the article's length; 0 = not measured (verdicts of earlier versions). */
    @Serializable
    private data class Entry(val id: String, val status: ArticleStatus, val at: Long, val words: Int = 0)

    @Serializable
    private data class Snapshot(val version: Int = VERSION, val entries: List<Entry> = emptyList())

    private val lock = Any()

    /** Item id → verdict, oldest first. Guarded by [lock]. */
    private val entries = LinkedHashMap<String, Entry>()
    @Volatile private var loaded = false
    private val writeLock = Mutex()
    private var pendingWrite: Job? = null

    /** The verdicts on disk that are still valid, by item id (reads the file: call off the main thread). */
    fun restore(): Map<String, ArticleStatus> {
        ensureLoaded()
        return synchronized(lock) { entries.mapValues { it.value.status } }
    }

    /** The measured lengths on disk that are still valid, by item id (reads the file: call off the main thread). */
    fun lengths(): Map<String, Int> {
        ensureLoaded()
        return synchronized(lock) { entries.values.filter { it.words > 0 }.associate { it.id to it.words } }
    }

    /** The verdict for [itemId], null when none (or an expired one) is known. */
    fun verdictOf(itemId: String): ArticleStatus? {
        ensureLoaded()
        return synchronized(lock) { entries[itemId]?.takeIf { fresh(it, clock()) }?.status }
    }

    /**
     * Notes [status] for [itemId] with its length in [words] (0 = not measured); only the final
     * verdicts count (see the class comment), others are ignored. A length measured before stays
     * when none comes with the verdict. Saved shortly after (several verdicts in a row are
     * written once).
     */
    fun record(itemId: String, status: ArticleStatus, words: Int = 0) {
        if (status !in KEPT || itemId.isBlank()) return
        ensureLoaded()
        synchronized(lock) {
            val old = entries[itemId]
            val length = if (words > 0) words else old?.words ?: 0
            if (old?.status == status && old.words == length) return
            entries.remove(itemId)
            entries[itemId] = Entry(itemId, status, if (old?.status == status) old.at else clock(), length)
            while (entries.size > maxEntries) entries.remove(entries.keys.first())
            if (pendingWrite?.isActive == true) return
            pendingWrite = scope.launch {
                delay(SAVE_DELAY_MS)
                save()
            }
        }
    }

    /** Writes the verdicts now (also what [record] schedules). */
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
            Log.w(TAG, "Could not save the article verdicts", e)
        }
    }

    private fun fresh(entry: Entry, now: Long): Boolean = now - entry.at <= keepFor.toMillis()

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val snapshot = JsonFiles.readOrNull(file)?.let { text ->
                try {
                    json.decodeFromString(Snapshot.serializer(), text)
                } catch (e: Exception) {
                    Log.w(TAG, "Article verdicts unreadable, starting over", e)
                    null
                }
            }
            val now = clock()
            snapshot?.takeIf { it.version == VERSION }?.entries
                ?.filter { it.status in KEPT && fresh(it, now) }
                ?.sortedBy { it.at }
                ?.takeLast(maxEntries)
                ?.forEach { entries[it.id] = it }
            loaded = true
        }
    }

    companion object {
        private const val TAG = "VerdictStore"

        /** Bump when [ArticleCompleteness] judges differently: older verdicts are then forgotten. */
        const val VERSION = 1

        const val MAX_ENTRIES = 3000

        /** Verdicts arriving within this time are written together. */
        const val SAVE_DELAY_MS = 2_000L

        /** The verdicts worth keeping: final ones, nothing that may change on the next try. */
        private val KEPT = setOf(ArticleStatus.READABLE, ArticleStatus.PAYWALL, ArticleStatus.BLOCKED, ArticleStatus.FAILED)
    }
}

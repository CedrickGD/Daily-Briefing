package com.cedrickgd.morgenbriefing.listen

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext

/*
 * "Natürliche Stimmen": Piper voices (rhasspy/piper, converted by k2-fsa for sherpa-onnx) that the
 * user downloads one by one from the k2-fsa GitHub release "tts-models". Nothing is bundled; each
 * voice is about 20 MB and lives in `files/neural-voices/<id>/` until deleted.
 */

/** A natural voice is stored in `ListenSettings.voices` as "neural:<id>", next to engine voice names. */
internal object NeuralVoiceIds {
    const val PREFIX = "neural:"

    fun choice(id: String): String = PREFIX + id

    /** The voice id of a stored choice; null for an engine voice, automatic or garbage. */
    fun idOf(stored: String?): String? = stored?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.isNotBlank() }
}

/** One downloadable voice and where its recordings come from (shown in the settings). */
data class NeuralVoice(
    /** Piper's name: "de_DE-thorsten-medium" (also the model file "<id>.onnx" in the archive). */
    val id: String,
    /** ISO 639-1 ("de"). */
    val language: String,
    /** ISO 3166-1 alpha-2 ("DE"). */
    val country: String,
    /** The speaker's name as Piper calls the voice ("Thorsten"). */
    val name: String,
    val gender: VoiceGender,
    /** Size of the download in bytes (the release asset). */
    val bytes: Long,
    /** SHA-256 of the archive, from the release's checksum.txt. */
    val sha256: String,
    /** The recordings the voice was trained on ("Thorsten-Voice"). */
    val dataset: String,
    /** Their licence ("CC0", "CC BY 4.0", "Gemeinfrei" is localised by the caller from [PUBLIC_DOMAIN]). */
    val licence: String,
    /** Speaker of a multi-speaker model (Sharvard: 0 = male, 1 = female). */
    val speaker: Int = 0,
) {
    val archive: String get() = "vits-piper-$id-int8.tar.bz2"

    companion object {
        const val RELEASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"

        /** [licence] of recordings in the public domain; the settings say it in the app's language. */
        const val PUBLIC_DOMAIN = "public-domain"
    }
}

/**
 * The voices on offer. Each one's model card (rhasspy/piper-voices on Hugging Face, and the copy
 * inside the k2-fsa archive) was checked on 2026-11-23; only recordings under a free licence are
 * listed. Left out: lessac (Blizzard licence), Ryan and hfc_* (CC BY-NC-SA), pavoque (CC BY-NC-SA),
 * tom (AGPL), Amy, Alan, Jenny and the M-AILABS voices (licence only "see URL"), GLaDOS and other
 * fan voices, miro/dii (no model card).
 */
internal object NeuralVoiceCatalog {
    val ALL: List<NeuralVoice> = listOf(
        NeuralVoice(
            "de_DE-thorsten-medium", "de", "DE", "Thorsten", VoiceGender.MALE, 20_949_833,
            "07e240b7b9c1fc9211d5a69512f8cbe11b3286c2ed79c15c076ac6ed427fdf13", "Thorsten-Voice", "CC0",
        ),
        NeuralVoice(
            "de_DE-kerstin-low", "de", "DE", "Kerstin", VoiceGender.FEMALE, 21_174_728,
            "bcd8039667940cf2efc939b844f4b33d0823096572fcc1a8caaa2faa77f3379c", "dataset-voice-kerstin", "CC0",
        ),
        NeuralVoice(
            "en_US-ljspeech-medium", "en", "US", "Linda", VoiceGender.FEMALE, 21_090_429,
            "24dc3bd77dd48c291e52c297878d3437c9492f245d823d7f6a06c4bbb67f4b6b", "LJ Speech", NeuralVoice.PUBLIC_DOMAIN,
        ),
        NeuralVoice(
            "en_US-norman-medium", "en", "US", "Norman", VoiceGender.MALE, 20_987_233,
            "cb481a514bc213ccf3899391c0f27fdcc4e4b814ec30496f28089a027b5aa01b", "LibriVox", NeuralVoice.PUBLIC_DOMAIN,
        ),
        NeuralVoice(
            "fr_FR-siwis-medium", "fr", "FR", "Siwis", VoiceGender.FEMALE, 20_914_888,
            "3909cff9b3cfd4820c66aa13bf554315c82e34899c161f0b446ece372bc4b5ec", "SIWIS", "CC BY 4.0",
        ),
        NeuralVoice(
            "fr_FR-gilles-low", "fr", "FR", "Gilles", VoiceGender.MALE, 21_248_965,
            "92d2bfd9b6b32b9787557c466b15620e453aecc10ec521762660e034941b3c43", "French Single Speaker", "CC0",
        ),
        NeuralVoice(
            "es_ES-davefx-medium", "es", "ES", "Davefx", VoiceGender.MALE, 21_171_632,
            "8bb8ac1cefb727caec9bd9c6c3185c673c8b42c53bd29bb25d5a7715dac37125", "davefx", "CC0",
        ),
        NeuralVoice(
            "es_ES-sharvard-medium", "es", "ES", "Sharvard", VoiceGender.FEMALE, 23_477_120,
            "bf5507cbdedea650365bd6175b3164a8ecca5847533ddf97afcba9d76ea599d1", "Sharvard", "CC BY 3.0", speaker = 1,
        ),
        NeuralVoice(
            "it_IT-paola-medium", "it", "IT", "Paola", VoiceGender.FEMALE, 21_143_212,
            "2b975ed305391c056944a4dde67ee754dd824099503a860295bb4c1d724662d8", "Voice-Dataset-Italian", "CC0",
        ),
    )

    fun byId(id: String?): NeuralVoice? = id?.let { wanted -> ALL.firstOrNull { it.id == wanted } }

    fun forLanguage(language: String): List<NeuralVoice> = ALL.filter { it.language == language.lowercase(Locale.ROOT) }
}

/** The files sherpa-onnx needs for one installed voice. */
data class NeuralVoiceFiles(val model: File, val tokens: File, val dataDir: File) {
    companion object {
        /** Where an installed voice keeps its files (no disk access). */
        fun of(dir: File, id: String) = NeuralVoiceFiles(File(dir, "$id.onnx"), File(dir, "tokens.txt"), File(dir, "espeak-ng-data"))

        /** [of], when everything the voice needs is really there. */
        fun verified(dir: File, id: String): NeuralVoiceFiles? {
            val files = of(dir, id)
            val complete = files.model.length() > 0 && files.tokens.length() > 0 && File(files.dataDir, "phontab").isFile
            return files.takeIf { complete }
        }
    }
}

/** Unpacks a voice archive (`.tar.bz2` with one top folder) safely and without the parts no one reads. */
internal object NeuralArchive {
    /**
     * Unpacks [input] into [target]: the archive's top folder is dropped, entries that would land
     * outside [target] fail the whole archive, and of espeak-ng's dictionaries only the voice's
     * [language] and English stay (the others are 15 MB no one reads).
     */
    fun unpack(input: InputStream, target: File, language: String, cancelled: () -> Boolean = { false }) {
        val root = target.canonicalFile
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(input, BUFFER))).use { tar ->
            while (true) {
                if (cancelled()) throw IOException("cancelled")
                val entry = tar.nextEntry ?: break
                val path = relativePath(entry.name) ?: continue
                val out = File(root, path).canonicalFile
                if (!out.path.startsWith(root.path + File.separator)) throw IOException("Entry outside the voice folder: ${entry.name}")
                when {
                    entry.isDirectory -> out.mkdirs()
                    // Links, devices: a voice has none, and following one could leave the folder.
                    !entry.isFile -> continue
                    !keep(path, language) -> continue
                    else -> {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { tar.copyTo(it, BUFFER) }
                    }
                }
            }
        }
    }

    /** "vits-piper-…-int8/espeak-ng-data/de_dict" → "espeak-ng-data/de_dict"; null for the top folder or unsafe names. */
    internal fun relativePath(name: String): String? {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.size < 2 || parts.any { it == ".." }) return null
        if (name.startsWith("/")) return null
        return parts.drop(1).joinToString("/")
    }

    /** False for espeak-ng dictionaries of other languages than [language] and English. */
    internal fun keep(path: String, language: String): Boolean {
        val name = path.substringAfterLast('/')
        val dictionary = path.startsWith("espeak-ng-data/") && !path.removePrefix("espeak-ng-data/").contains('/') && name.endsWith("_dict")
        if (!dictionary) return true
        val code = name.removeSuffix("_dict")
        return code == language.lowercase(Locale.ROOT) || code == "en"
    }

    private const val BUFFER = 64 * 1024
}

/** A download in progress: [done] of [total] bytes, then [unpacking]. */
data class NeuralDownload(val done: Long, val total: Long, val unpacking: Boolean = false) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

/** What the settings show about the natural voices. */
data class NeuralVoicesState(
    /** Installed voice ids (complete and checked). */
    val installed: Set<String> = emptySet(),
    val downloads: Map<String, NeuralDownload> = emptyMap(),
    /** Voices whose last download failed (cleared by the next try). */
    val failed: Set<String> = emptySet(),
    /** The installed voices were read from disk at least once. */
    val loaded: Boolean = false,
)

/**
 * Downloads, unpacks, lists and deletes natural voices under [root] (`files/neural-voices`).
 * A download is checked against the archive's SHA-256 before it is unpacked; it is unpacked into a
 * temporary folder and moved into place only when complete, so a half voice never shows up.
 */
class NeuralVoices(
    private val root: File,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** Where the archives are downloaded from (tests point it at a local server). */
    private val release: String = NeuralVoice.RELEASE,
) {
    private val mutableState = MutableStateFlow(NeuralVoicesState())
    val state: StateFlow<NeuralVoicesState> = mutableState.asStateFlow()

    private val jobs = mutableMapOf<String, Job>()
    private val partial = File(root, ".partial")

    /** Reads what is installed and clears leftovers; downloads wait for it, so it never clears theirs. */
    private val scanned: Job = scope.launch(io) { scan() }

    /** The files of an installed voice; null when it is not installed. */
    fun files(id: String): NeuralVoiceFiles? =
        if (id in mutableState.value.installed) NeuralVoiceFiles.of(File(root, id), id) else null

    /** Starts downloading [voice] unless it is installed or already coming; [onInstalled] runs once it is ready. */
    fun download(voice: NeuralVoice, onInstalled: () -> Unit = {}) {
        synchronized(jobs) {
            if (voice.id in mutableState.value.installed || jobs[voice.id]?.isActive == true) return
            mutableState.update { it.copy(failed = it.failed - voice.id, downloads = it.downloads + (voice.id to NeuralDownload(0, voice.bytes))) }
            jobs[voice.id] = scope.launch(io) {
                scanned.join()
                val ok = try {
                    install(voice)
                    true
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) Log.w(TAG, "Voice ${voice.id} failed", e)
                    false
                } finally {
                    File(partial, voice.id).deleteRecursively()
                    File(partial, voice.archive).delete()
                }
                mutableState.update {
                    it.copy(
                        downloads = it.downloads - voice.id,
                        installed = if (ok) it.installed + voice.id else it.installed,
                        failed = if (ok || !coroutineContext[Job]!!.isActive) it.failed else it.failed + voice.id,
                    )
                }
                synchronized(jobs) { jobs.remove(voice.id) }
                if (ok) onInstalled()
            }
        }
    }

    /** Stops a download; nothing of it stays. */
    fun cancel(id: String) {
        val job = synchronized(jobs) { jobs.remove(id) } ?: return
        job.cancel()
        mutableState.update { it.copy(downloads = it.downloads - id) }
    }

    /** Removes an installed voice from the phone. */
    fun delete(id: String) {
        mutableState.update { it.copy(installed = it.installed - id, failed = it.failed - id) }
        scope.launch(io) {
            val dir = File(root, id)
            if (dir.exists() && !dir.deleteRecursively()) Log.w(TAG, "Could not delete all of $dir")
        }
    }

    private suspend fun install(voice: NeuralVoice) {
        partial.mkdirs()
        val archive = File(partial, voice.archive)
        val digest = MessageDigest.getInstance("SHA-256")
        val request = Request.Builder().url("$release/${voice.archive}").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${voice.archive}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: voice.bytes
            body.byteStream().use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (done - shown >= PROGRESS_STEP || done == total) {
                            shown = done
                            progress(voice.id, NeuralDownload(done, total))
                        }
                    }
                }
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (!hash.equals(voice.sha256, ignoreCase = true)) throw IOException("Checksum mismatch for ${voice.archive}")
        progress(voice.id, NeuralDownload(voice.bytes, voice.bytes, unpacking = true))
        val unpacked = File(partial, voice.id)
        unpacked.deleteRecursively()
        val context = coroutineContext
        archive.inputStream().use { NeuralArchive.unpack(it, unpacked, voice.language) { !context[Job]!!.isActive } }
        if (NeuralVoiceFiles.verified(unpacked, voice.id) == null) throw IOException("${voice.archive} lacks the model, tokens or espeak-ng data")
        val target = File(root, voice.id)
        target.deleteRecursively()
        if (!unpacked.renameTo(target)) throw IOException("Could not move ${voice.id} into place")
    }

    private fun progress(id: String, download: NeuralDownload) {
        mutableState.update { if (id in it.downloads) it.copy(downloads = it.downloads + (id to download)) else it }
    }

    /** Lists the installed voices; leftovers of a download the process did not finish are removed. */
    private suspend fun scan() = withContext(io) {
        partial.deleteRecursively()
        val installed = NeuralVoiceCatalog.ALL.filter { NeuralVoiceFiles.verified(File(root, it.id), it.id) != null }.map { it.id }.toSet()
        mutableState.update { it.copy(installed = it.installed + installed, loaded = true) }
    }

    private companion object {
        const val TAG = "NeuralVoices"

        /** Progress updates every 256 kB (about 80 per voice). */
        const val PROGRESS_STEP = 256L * 1024
    }
}

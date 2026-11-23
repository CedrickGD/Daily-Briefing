package com.cedrickgd.morgenbriefing.listen

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Text → audio with one natural voice. Used from one thread at a time ([NeuralSpeaker]'s generator). */
internal interface NeuralSynth {
    val sampleRate: Int

    /** Generates [text] at [speed] (1 = the voice's own pace), handing samples to [onSamples]; false from it stops early. */
    fun generate(text: String, speed: Float, onSamples: (FloatArray) -> Boolean)

    fun release()
}

/** Opens natural voices; one per process ([NeuralRuntime.engine]). */
internal interface NeuralEngine {
    /** Loads [voice] from [files]; throws when the model cannot be read. */
    fun open(voice: NeuralVoice, files: NeuralVoiceFiles): NeuralSynth
}

/**
 * Whether natural voices can play on this build and phone. The engine (the speech library) is
 * loaded at most once, off the main thread; when it is missing or fails to load, the settings
 * hide "Natürliche Stimmen" and reading stays with the phone's text-to-speech.
 */
internal object NeuralRuntime {
    val engine: NeuralEngine? by lazy {
        try {
            createNeuralEngine()
        } catch (e: Throwable) {
            Log.w("NeuralRuntime", "Natural voices unavailable", e)
            null
        }
    }

    /** True when [engine] loads; call from a coroutine (the first call loads the library on IO). */
    suspend fun probe(): Boolean = withContext(Dispatchers.IO) { engine != null }
}

/**
 * Reads parts of a [ListenScript] with a natural voice: one thread generates sentence by sentence
 * (so the first words come after one sentence, not after a whole paragraph), another plays them
 * through an [AudioTrack] while the next sentence is generated. Speed is the model's own pace,
 * pitch a playback parameter (Android 6+). Callbacks come on these threads, tagged with the token
 * of the [speak] call; [stop] silences at once.
 */
internal class NeuralSpeaker(private val engine: NeuralEngine, private val listener: Listener) {
    interface Listener {
        /** Part [index] starts to sound. */
        fun onPartStart(token: Int, index: Int)

        /** The sentence at [start] (characters into the text handed over for part [index]) starts to sound. */
        fun onPartRange(token: Int, index: Int, start: Int)

        /** Part [index] (and its pause) has been played; for the last part, once it is really heard. */
        fun onPartDone(token: Int, index: Int)

        /** The voice could not be loaded or the audio output failed at part [index]; nothing more comes for [token]. */
        fun onFailed(token: Int, index: Int)
    }

    private val generator: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT); runnable.run() }, "neural-voice").apply { isDaemon = true }
    }
    private val player: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO); runnable.run() }, "neural-audio").apply { isDaemon = true }
    }

    @Volatile private var current: Session? = null

    // Generator thread only.
    private var synth: NeuralSynth? = null
    private var synthVoice: String? = null

    // Player thread only (and [stop], which only pauses and flushes it).
    @Volatile private var track: AudioTrack? = null
    private var trackRate = 0

    /**
     * Speaks [parts] with [voice]; the first of them is part number [firstIndex] of the script
     * (the callbacks use script numbers). Replaces whatever was playing.
     */
    fun speak(token: Int, voice: NeuralVoice, files: NeuralVoiceFiles, parts: List<SpeechPart>, firstIndex: Int, rate: Float, pitch: Float) {
        stop()
        val session = Session(token)
        current = session
        generator.execute { generate(session, voice, files, parts, firstIndex, rate) }
        player.execute { play(session, pitch) }
    }

    /** Silences the voice now; callbacks of the stopped [speak] stop too. */
    fun stop() {
        val session = current ?: return
        current = null
        session.cancelled = true
        session.queue.clear()
        try {
            track?.let {
                it.pause()
                it.flush()
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack stop failed", e)
        }
    }

    /** Frees the model and the audio output (after a while without reading); the next [speak] loads them again. */
    fun release() {
        stop()
        generator.execute {
            synth?.release()
            synth = null
            synthVoice = null
        }
        player.execute {
            track?.release()
            track = null
            trackRate = 0
        }
    }

    private fun generate(session: Session, voice: NeuralVoice, files: NeuralVoiceFiles, parts: List<SpeechPart>, firstIndex: Int, rate: Float) {
        if (session.cancelled) return
        val active = try {
            loadSynth(voice, files)
        } catch (e: Throwable) {
            Log.w(TAG, "Could not load ${voice.id}", e)
            session.put(Item.Failed(firstIndex))
            return
        }
        session.put(Item.Format(active.sampleRate))
        parts.forEachIndexed { i, part ->
            val index = firstIndex + i
            for ((start, end) in NeuralSentences.ranges(part.text)) {
                if (session.cancelled) return
                val sentence = part.text.substring(start, end).trim()
                if (sentence.isEmpty()) continue
                val samples = ArrayList<FloatArray>()
                try {
                    active.generate(sentence, rate.coerceIn(0.5f, 2f)) { chunk ->
                        samples += chunk
                        !session.cancelled
                    }
                } catch (e: Exception) {
                    // One sentence the model cannot say: skip it, keep reading.
                    Log.w(TAG, "Sentence skipped", e)
                }
                if (samples.isNotEmpty()) session.put(Item.Sentence(index, start, join(samples)))
            }
            if (part.pauseAfterMs > 0) session.put(Item.Silence(part.pauseAfterMs))
            session.put(Item.PartEnd(index, last = i == parts.lastIndex))
        }
    }

    private fun loadSynth(voice: NeuralVoice, files: NeuralVoiceFiles): NeuralSynth {
        synth?.takeIf { synthVoice == voice.id }?.let { return it }
        synth?.release()
        synth = null
        synthVoice = null
        return engine.open(voice, files).also {
            synth = it
            synthVoice = voice.id
        }
    }

    private fun play(session: Session, pitch: Float) {
        var output: AudioTrack? = null
        var written = 0L
        var started = -1
        while (!session.cancelled) {
            val item = session.queue.poll(POLL_MS, TimeUnit.MILLISECONDS) ?: continue
            when (item) {
                is Item.Failed -> {
                    if (!session.cancelled) listener.onFailed(session.token, item.index)
                    return
                }
                is Item.Format -> {
                    output = try {
                        prepareTrack(item.sampleRate, pitch)
                    } catch (e: Exception) {
                        Log.w(TAG, "No audio output", e)
                        if (!session.cancelled) listener.onFailed(session.token, -1)
                        return
                    }
                    written = 0L
                }
                is Item.Sentence -> {
                    val out = output ?: return
                    if (session.cancelled) return
                    if (item.index != started) {
                        started = item.index
                        listener.onPartStart(session.token, item.index)
                    }
                    listener.onPartRange(session.token, item.index, item.start)
                    written += write(session, out, item.samples) ?: return
                }
                is Item.Silence -> {
                    val out = output ?: return
                    written += write(session, out, FloatArray(trackRate * item.ms / 1000)) ?: return
                }
                is Item.PartEnd -> {
                    val out = output ?: return
                    if (item.last) {
                        drain(session, out, written)
                        if (session.cancelled) return
                        out.pause()
                    }
                    if (session.cancelled) return
                    listener.onPartDone(session.token, item.index)
                    if (item.last) return
                }
            }
        }
    }

    /** The track for [sampleRate], emptied and playing; reused while the voice's rate stays the same. */
    private fun prepareTrack(sampleRate: Int, pitch: Float): AudioTrack {
        val existing = track
        val audio = if (existing != null && trackRate == sampleRate) {
            existing
        } else {
            existing?.release()
            val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            AudioTrack.Builder()
                .setAudioAttributes(AudioFocus.attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minimum, sampleRate / 4 * BYTES_PER_SAMPLE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also {
                    track = it
                    trackRate = sampleRate
                }
        }
        audio.pause()
        audio.flush()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                audio.playbackParams = PlaybackParams().setSpeed(1f).setPitch(pitch.coerceIn(0.5f, 2f))
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Pitch $pitch not supported", e)
            }
        }
        audio.play()
        return audio
    }

    /** Writes [samples] in small pieces, so [stop] is heard within a fraction of a second; null when stopped or failed. */
    private fun write(session: Session, audio: AudioTrack, samples: FloatArray): Long? {
        var offset = 0
        while (offset < samples.size) {
            if (session.cancelled) return null
            val count = minOf(WRITE_PIECE, samples.size - offset)
            val result = audio.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
            if (result < 0) {
                Log.w(TAG, "AudioTrack.write failed: $result")
                if (!session.cancelled) listener.onFailed(session.token, -1)
                return null
            }
            offset += result
        }
        return samples.size.toLong()
    }

    /** Waits until everything written has been heard (at most a few seconds). */
    private fun drain(session: Session, audio: AudioTrack, written: Long) {
        val deadline = System.currentTimeMillis() + DRAIN_LIMIT_MS
        while (!session.cancelled && System.currentTimeMillis() < deadline) {
            val heard = audio.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (heard >= written) return
            Thread.sleep(DRAIN_POLL_MS)
        }
    }

    private class Session(val token: Int) {
        @Volatile var cancelled = false
        val queue = LinkedBlockingQueue<Item>(QUEUE_SIZE)

        /** Hands [item] to the player, waiting while it is [QUEUE_SIZE] sentences ahead. */
        fun put(item: Item) {
            while (!cancelled) {
                if (queue.offer(item, POLL_MS, TimeUnit.MILLISECONDS)) return
            }
        }
    }

    private sealed interface Item {
        data class Format(val sampleRate: Int) : Item
        class Sentence(val index: Int, val start: Int, val samples: FloatArray) : Item
        data class Silence(val ms: Int) : Item
        data class PartEnd(val index: Int, val last: Boolean) : Item
        data class Failed(val index: Int) : Item
    }

    private companion object {
        const val TAG = "NeuralSpeaker"
        const val BYTES_PER_SAMPLE = 4
        const val QUEUE_SIZE = 4
        const val POLL_MS = 100L
        const val WRITE_PIECE = 2048
        const val DRAIN_LIMIT_MS = 5_000L
        const val DRAIN_POLL_MS = 20L

        fun join(chunks: List<FloatArray>): FloatArray {
            if (chunks.size == 1) return chunks[0]
            val all = FloatArray(chunks.sumOf { it.size })
            var at = 0
            chunks.forEach { chunk ->
                chunk.copyInto(all, at)
                at += chunk.size
            }
            return all
        }
    }
}

/** The sentences of a part, for generating one at a time. Pure. */
internal object NeuralSentences {
    /** (start, end) of each sentence of [text], in order, covering it from the first sentence to the end. */
    fun ranges(text: String): List<Pair<Int, Int>> {
        if (text.isBlank()) return emptyList()
        val starts = mutableListOf<Int>()
        var from = 0
        for (sentence in splitSentences(text)) {
            val at = text.indexOf(sentence, from)
            if (at < 0) continue
            starts += at
            from = at + sentence.length
        }
        if (starts.isEmpty()) starts += 0
        return starts.mapIndexed { i, start -> start to (starts.getOrNull(i + 1) ?: text.length) }
    }
}

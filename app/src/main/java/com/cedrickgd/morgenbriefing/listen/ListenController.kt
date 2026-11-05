package com.cedrickgd.morgenbriefing.listen

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.cedrickgd.morgenbriefing.MorgenbriefingApp
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.domain.ItemEdition
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ListenStrings
import com.cedrickgd.morgenbriefing.util.findActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/** Why "Zuhören" cannot read right now; the mini player explains it and offers a fix. */
enum class ListenIssue {
    /** No text-to-speech engine on the device, or it refused to start. */
    NO_ENGINE,

    /**
     * The engine has neither a voice for the article's language ([ListenState.voiceLanguage])
     * nor one for the device language (voice data missing).
     */
    MISSING_VOICE,
}

data class ListenState(
    /**
     * False once a reading attempt showed that this device cannot read aloud ([issue] says
     * why). Optimistically true before the first attempt, so "Zuhören" stays on offer; it turns
     * true again after the user went to install voice data.
     */
    val available: Boolean = true,
    /** Reading (or about to: see [preparing]). */
    val speaking: Boolean = false,
    val paused: Boolean = false,
    val title: String? = null,
    /** `NewsItem.key` of the article being read. */
    val articleKey: String? = null,
    /** Link of that article; the mini player opens it. */
    val articleUrl: String? = null,
    /** 0f..1f over the whole article. */
    val progress: Float = 0f,
    /** The speech engine is still starting; reading begins in a moment. */
    val preparing: Boolean = false,
    /** Estimated length of the whole reading in seconds (for "noch etwa 3 Min."). */
    val estimatedSeconds: Int = 0,
    /** Set after a failed attempt; cleared by [ListenController.stop]. */
    val issue: ListenIssue? = null,
    /**
     * The engine only has a voice for the news language that needs the internet (no offline
     * voice installed): reading works now, but not offline — the mini player offers to install one.
     */
    val onlineVoiceOnly: Boolean = false,
    /**
     * ISO 639-1 code of the language the voice reads in: the article's language while one is
     * loaded or just failed, else the main news edition's.
     */
    val voiceLanguage: String = NewsEdition.GERMANY.language,
) {
    /** Something is loaded: reading, preparing or paused. */
    val active: Boolean get() = speaking || paused

    /** True while the article with [key] is being read or paused. */
    fun isReading(key: String): Boolean = active && articleKey == key
}

/**
 * "Zuhören" (Vorlesen): reads headline, lead, body and sources of an article with Android
 * TextToSpeech in chunks below `getMaxSpeechInputLength()`. The voice speaks the language of the
 * article's own edition ([ItemEdition] – Washington local news in English while the main
 * edition is German; the device language if there is no such voice), see [ListenLanguages].
 * One instance per process (`context.listen`), safe to call from any thread (everything runs on
 * the main thread).
 *
 * * The engine is created on the first [play] and shut down after a minute without reading.
 * * Audio focus: any loss pauses; a short one (call, navigation prompt) resumes by itself.
 * * Headphones unplugged or the app sent to the background (screen still on) → pause; with the
 *   screen simply turned off it keeps reading.
 * * [pauseOrResume] restarts the interrupted sentence group, not the whole article.
 */
class ListenController(context: Context) {
    private val app: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val mutableState = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = mutableState.asStateFlow()

    // ---- Everything below is confined to the main thread. ----

    private enum class Engine { OFF, STARTING, READY }

    /** The settings (null until loaded), the main news edition and the interface language, kept in sync. */
    private var settings: AppSettings? = null
    private var edition: NewsEdition = NewsEdition.GERMANY
    private var uiLanguage: AppLanguage = AppLanguage.system()

    /** The edition of the article being read (its voice); the main edition while idle. */
    private var voiceEdition: NewsEdition = NewsEdition.GERMANY

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var tts: TextToSpeech? = null
    private var engine = Engine.OFF

    /** The locale the ready engine was set to; an article in another language re-picks the voice. */
    private var engineLocale: Locale? = null

    /** The ready engine reads the article's language only with a network voice (see [ListenState.onlineVoiceOnly]). */
    private var voiceOnlineOnly = false

    /** Bumped for every engine instance; callbacks of an older one are ignored. */
    private var engineGeneration = 0

    private var script: ListenScript? = null

    /** Part to (re)start from: the one being spoken, or the next one. */
    private var position = 0

    /** Bumped on every play/pause/stop; utterance callbacks of another session are ignored. */
    private var session = 0

    /** One automatic engine restart per reading when the TTS service dies mid-way. */
    private var restartsLeft = 0
    private var resumeOnFocusGain = false
    private var noisyRegistered = false
    private var lifecycleObserved = false

    private val audioFocus: AudioFocus by lazy {
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AudioFocus.create(audio, main, ::onFocusChange)
    }

    private val releaseEngineTask = Runnable { releaseEngine() }
    private val initWatchdog = Runnable { onInitTimeout() }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onMain { pause(keepFocus = false) }
        }
    }

    private val appLifecycle = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // Left the app with the screen on: nobody can see the controls any more → pause.
            // Screen turned off while listening (phone in the pocket) → keep reading.
            if (mutableState.value.speaking && isScreenOn()) pause(keepFocus = false)
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = dispatch(utteranceId) { index, silence ->
            if (!silence) onPartStart(index)
        }

        override fun onDone(utteranceId: String?) = dispatch(utteranceId) { index, silence ->
            if (!silence) onPartDone(index)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = dispatch(utteranceId) { index, silence ->
            if (!silence) onPartError(index, TextToSpeech.ERROR)
        }

        override fun onError(utteranceId: String?, errorCode: Int) = dispatch(utteranceId) { index, silence ->
            if (!silence) onPartError(index, errorCode)
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) =
            dispatch(utteranceId) { index, silence -> if (!silence) onPartRange(index, start) }
    }

    /** The running script reads the article's full text (the reader), not only the teaser. */
    private var scriptIsFull = false

    init {
        val settingsFlow = (app as? MorgenbriefingApp)?.container?.settings?.settings
        if (settingsFlow != null) {
            scope.launch {
                settingsFlow
                    .distinctUntilChanged()
                    .catch { Log.w(TAG, "Settings unavailable; reading with the last known languages", it) }
                    .collect { newSettings ->
                        settings = newSettings
                        edition = newSettings.edition
                        uiLanguage = newSettings.language
                        // An idle hint names the voice of the current edition.
                        if (script == null) voiceEdition = newSettings.edition
                        mutableState.update { if (it.active || it.issue != null) it else it.copy(voiceLanguage = newSettings.edition.language) }
                    }
            }
        }
    }

    /**
     * Reads [item] aloud — with [body] (the reader's full text) the whole article, else headline
     * and teaser. Another article that is being read is replaced; the same article keeps reading
     * (or resumes when it was paused) unless the full text arrives for a teaser that is playing.
     * Tapping the mini player opens the article.
     */
    fun play(item: NewsItem, body: String? = null) = onMain {
        val key = item.key
        val current = mutableState.value
        if (script != null && current.articleKey == key && (body.isNullOrBlank() || scriptIsFull)) {
            if (current.paused) resume()
            return@onMain
        }
        val maxLength = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(ListenScript.DEFAULT_MAX_LENGTH)
        val itemEdition = ListenLanguages.editionOf(item, settings, edition)
        val next = ListenScript.from(item, ListenLanguages.scriptLanguage(uiLanguage, itemEdition), maxLength, body)
        if (next.isEmpty) return@onMain

        voiceEdition = itemEdition

        session++
        tts?.stop()
        script = next
        scriptIsFull = !body.isNullOrBlank()
        position = 0
        restartsLeft = 1
        resumeOnFocusGain = false
        mutableState.value = ListenState(
            available = true,
            speaking = true,
            title = item.headline.trim().ifEmpty { ListenStrings.of(uiLanguage).articleFallback },
            articleKey = key,
            articleUrl = item.url,
            preparing = engine != Engine.READY,
            onlineVoiceOnly = voiceOnlineOnly,
            estimatedSeconds = next.estimatedSeconds,
            voiceLanguage = itemEdition.language,
        )
        // This article is in another language than the engine's voice: switch it before reading.
        if (engine == Engine.READY && engineLocale !in ListenLanguages.voiceLocales(itemEdition)) {
            val engineTts = tts
            if (engineTts == null || !applyVoice(engineTts)) return@onMain
        }
        begin()
    }

    /** Pauses a reading or resumes a paused one; does nothing when idle. */
    fun pauseOrResume() = onMain {
        val current = mutableState.value
        when {
            current.speaking -> pause(keepFocus = false)
            current.paused -> resume()
        }
    }

    /** Stops reading (and clears an [ListenState.issue]). */
    fun stop() = onMain {
        session++
        tts?.stop()
        script = null
        position = 0
        resumeOnFocusGain = false
        abandonFocus()
        unregisterNoisy()
        voiceEdition = edition
        mutableState.value = ListenState(available = mutableState.value.available, voiceLanguage = edition.language)
        scheduleRelease()
    }

    /**
     * Opens the system screen that installs voice data (falls back to the text-to-speech
     * settings). The next [play] checks the voices again.
     */
    fun installVoiceData(from: Context) {
        val intents = listOf(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent(TTS_SETTINGS_ACTION),
            Intent(Settings.ACTION_SETTINGS),
        )
        val launched = intents.any { intent ->
            if (from.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                from.startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                false
            } catch (e: SecurityException) {
                false
            }
        }
        if (!launched) {
            // The language that is missing: the failed article's, read before stop() resets it.
            val language = mutableState.value.voiceLanguage
            Toast.makeText(app, ListenStrings.of(uiLanguage).installVoiceHint(language), Toast.LENGTH_LONG).show()
        }
        onMain {
            stop()
            releaseEngine()
            mutableState.update { it.copy(available = true, issue = null) }
        }
    }

    // ---- Playback ----

    /** Starts (or continues) reading the current script from [position]. */
    private fun begin() {
        main.removeCallbacks(releaseEngineTask)
        if (!audioFocus.request()) {
            // Someone else holds the audio (e.g. a call): wait for the user to try again.
            Log.i(TAG, "Audio focus denied; staying paused")
            mutableState.update { it.copy(speaking = false, paused = true, preparing = false) }
            return
        }
        registerNoisy()
        observeAppLifecycle()
        when (engine) {
            Engine.READY -> speakFrom(position)
            Engine.STARTING -> Unit // onEngineInit continues.
            Engine.OFF -> startEngine()
        }
    }

    private fun resume() {
        if (script == null) {
            stop()
            return
        }
        resumeOnFocusGain = false
        session++
        mutableState.update { it.copy(speaking = true, paused = false, preparing = engine != Engine.READY, issue = null) }
        begin()
    }

    private fun pause(keepFocus: Boolean) {
        val current = mutableState.value
        if (!current.speaking) return
        session++
        tts?.stop()
        resumeOnFocusGain = keepFocus
        if (!keepFocus) abandonFocus()
        unregisterNoisy()
        val progress = script?.progressAt(position) ?: current.progress
        mutableState.update { it.copy(speaking = false, paused = true, preparing = false, progress = progress) }
        scheduleRelease()
    }

    private fun finish() {
        session++
        script = null
        position = 0
        resumeOnFocusGain = false
        abandonFocus()
        unregisterNoisy()
        voiceEdition = edition
        mutableState.value = ListenState(available = true, voiceLanguage = edition.language)
        scheduleRelease()
    }

    private fun speakFrom(index: Int) {
        val engineTts = tts ?: return startEngine()
        val current = script ?: return
        if (index > current.lastIndex) return finish()
        val id = session
        for (i in index..current.lastIndex) {
            val part = current.parts[i]
            val mode = if (i == index) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engineTts.speak(part.text, mode, null, UtteranceIds.speech(id, i)) == TextToSpeech.ERROR) {
                Log.w(TAG, "TextToSpeech refused part $i; restarting the engine")
                return restartEngine(fromIndex = i)
            }
            if (part.pauseAfterMs > 0) {
                engineTts.playSilentUtterance(part.pauseAfterMs.toLong(), TextToSpeech.QUEUE_ADD, UtteranceIds.silence(id, i))
            }
        }
        mutableState.update { it.copy(preparing = false, progress = current.progressAt(index)) }
    }

    private fun onPartStart(index: Int) {
        val current = script ?: return
        position = index
        publishProgress(current.progressAt(index), force = true)
    }

    private fun onPartRange(index: Int, start: Int) {
        val current = script ?: return
        publishProgress(current.progressAt(index, start), force = false)
    }

    private fun onPartDone(index: Int) {
        val current = script ?: return
        position = (index + 1).coerceAtMost(current.lastIndex)
        if (index >= current.lastIndex) finish()
    }

    private fun onPartError(index: Int, code: Int) {
        Log.w(TAG, "TextToSpeech error $code on part $index")
        when (code) {
            ERROR_NOT_INSTALLED_YET -> failEngine(ListenIssue.MISSING_VOICE)
            ERROR_SERVICE -> restartEngine(fromIndex = index)
            ERROR_NETWORK, ERROR_NETWORK_TIMEOUT -> {
                position = index
                pause(keepFocus = false)
            }
            // Synthesis/output hiccup on one part: the engine continues with the next one.
            else -> if (index >= (script?.lastIndex ?: 0)) finish()
        }
    }

    private fun publishProgress(progress: Float, force: Boolean) {
        val current = mutableState.value
        if (!current.speaking) return
        if (force || current.preparing || abs(progress - current.progress) >= PROGRESS_STEP) {
            mutableState.value = current.copy(progress = progress, preparing = false)
        }
    }

    // ---- Engine ----

    private fun startEngine() {
        main.removeCallbacks(releaseEngineTask)
        engine = Engine.STARTING
        val generation = ++engineGeneration
        mutableState.update { if (it.speaking) it.copy(preparing = true) else it }
        tts = try {
            // onInit may even run inside the constructor (no engine): always hop to the queue.
            TextToSpeech(app) { status -> main.post { onEngineInit(generation, status) } }
        } catch (e: RuntimeException) {
            Log.w(TAG, "TextToSpeech could not be created", e)
            null
        }
        if (tts == null) {
            failEngine(ListenIssue.NO_ENGINE)
        } else {
            main.removeCallbacks(initWatchdog)
            main.postDelayed(initWatchdog, INIT_TIMEOUT_MS)
        }
    }

    private fun onEngineInit(generation: Int, status: Int) {
        if (generation != engineGeneration || engine != Engine.STARTING) return
        main.removeCallbacks(initWatchdog)
        val engineTts = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TextToSpeech init failed ($status)")
            return failEngine(ListenIssue.NO_ENGINE)
        }
        if (!applyVoice(engineTts)) return
        try {
            engineTts.setAudioAttributes(AudioFocus.attributes)
            engineTts.setOnUtteranceProgressListener(progressListener)
        } catch (e: RuntimeException) {
            Log.w(TAG, "TextToSpeech setup failed", e)
            return failEngine(ListenIssue.NO_ENGINE)
        }
        engine = Engine.READY
        mutableState.update { it.copy(available = true, issue = null, onlineVoiceOnly = voiceOnlineOnly) }
        if (mutableState.value.speaking && script != null) speakFrom(position) else scheduleRelease()
    }

    private fun onInitTimeout() {
        if (engine == Engine.STARTING) {
            Log.w(TAG, "TextToSpeech did not start in time")
            failEngine(ListenIssue.NO_ENGINE)
        }
    }

    /**
     * Sets the engine to the voice of the article's edition (see [pickLanguage]) and prefers an
     * offline voice. False after failing the engine with [ListenIssue.MISSING_VOICE].
     */
    private fun applyVoice(engineTts: TextToSpeech): Boolean {
        val language = pickLanguage(engineTts)
        if (language == null) {
            Log.w(TAG, "No voice for ${voiceEdition.language} (nor the device language) available")
            failEngine(ListenIssue.MISSING_VOICE)
            return false
        }
        engineLocale = language
        voiceOnlineOnly = !preferOfflineVoice(engineTts, language)
        mutableState.update { it.copy(onlineVoiceOnly = voiceOnlineOnly) }
        return true
    }

    /**
     * The article edition's language in its country ("en-US"), else the plain language ("en"),
     * else the device language; null when none of them has a voice.
     */
    private fun pickLanguage(engineTts: TextToSpeech): Locale? {
        val wanted = ListenLanguages.voiceLocales(voiceEdition)
        wanted.firstOrNull { setLanguage(engineTts, it) >= TextToSpeech.LANG_AVAILABLE }?.let { return it }
        val fallback = Locale.getDefault()
        if (wanted.none { it.language == fallback.language } && setLanguage(engineTts, fallback) >= TextToSpeech.LANG_AVAILABLE) {
            return fallback
        }
        return null
    }

    /**
     * Picks an installed voice for [locale] that works without internet, if the engine has one.
     * Returns false when only network voices exist (reading will fail offline). Engines that do
     * not list their voices are trusted (true).
     */
    private fun preferOfflineVoice(engineTts: TextToSpeech, locale: Locale): Boolean = try {
        val voices = engineTts.voices.orEmpty().filter { it.locale.language == locale.language }
        val offline = voices
            .filter { !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .maxByOrNull { it.quality }
        when {
            offline != null -> {
                engineTts.voice = offline
                true
            }
            else -> voices.isEmpty()
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Voice list unavailable", e)
        true
    }

    private fun setLanguage(engineTts: TextToSpeech, locale: Locale): Int = try {
        engineTts.setLanguage(locale)
    } catch (e: RuntimeException) {
        Log.w(TAG, "setLanguage($locale) failed", e)
        TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** The TTS service died mid-way: start a fresh engine once and continue at [fromIndex]. */
    private fun restartEngine(fromIndex: Int) {
        if (restartsLeft <= 0) return failEngine(ListenIssue.NO_ENGINE)
        restartsLeft--
        position = fromIndex
        shutdownEngine()
        if (mutableState.value.speaking) startEngine()
    }

    private fun failEngine(issue: ListenIssue) {
        main.removeCallbacks(initWatchdog)
        shutdownEngine()
        session++
        script = null
        position = 0
        resumeOnFocusGain = false
        abandonFocus()
        unregisterNoisy()
        // The hint names the voice that is missing: the article's language, not the main edition's.
        mutableState.value = ListenState(available = false, issue = issue, voiceLanguage = voiceEdition.language)
    }

    private fun scheduleRelease() {
        main.removeCallbacks(releaseEngineTask)
        main.postDelayed(releaseEngineTask, IDLE_RELEASE_MS)
    }

    private fun releaseEngine() {
        main.removeCallbacks(releaseEngineTask)
        if (mutableState.value.speaking) return
        shutdownEngine()
    }

    private fun shutdownEngine() {
        main.removeCallbacks(initWatchdog)
        val old = tts
        tts = null
        engine = Engine.OFF
        engineLocale = null
        engineGeneration++
        if (old != null) {
            try {
                old.stop()
                old.shutdown()
            } catch (e: RuntimeException) {
                Log.w(TAG, "TextToSpeech shutdown failed", e)
            }
        }
    }

    // ---- Focus, noise, app lifecycle ----

    private fun onFocusChange(change: FocusChange) {
        when (change) {
            FocusChange.LOST -> pause(keepFocus = false)
            FocusChange.LOST_TRANSIENT -> pause(keepFocus = true)
            FocusChange.GAINED -> if (resumeOnFocusGain && mutableState.value.paused) resume()
        }
    }

    private fun abandonFocus() {
        try {
            audioFocus.abandon()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not abandon audio focus", e)
        }
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        try {
            ContextCompat.registerReceiver(
                app,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyRegistered = true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not watch for unplugged headphones", e)
        }
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        noisyRegistered = false
        try {
            app.unregisterReceiver(noisyReceiver)
        } catch (e: IllegalArgumentException) {
            // Already gone.
        }
    }

    private fun observeAppLifecycle() {
        if (lifecycleObserved) return
        lifecycleObserved = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycle)
    }

    private fun isScreenOn(): Boolean =
        (app.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true

    // ---- Threading ----

    private inline fun dispatch(utteranceId: String?, crossinline block: (index: Int, silence: Boolean) -> Unit) {
        val (id, index, silence) = UtteranceIds.parse(utteranceId) ?: return
        main.post { if (id == session) block(index, silence) }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    private companion object {
        const val TAG = "ListenController"

        /** Shut the engine down after this long without reading. */
        const val IDLE_RELEASE_MS = 60_000L

        /** A healthy engine starts in well under a second; some cold starts take a few. */
        const val INIT_TIMEOUT_MS = 10_000L

        /** Smallest progress change worth a state update (word callbacks come ~3× a second). */
        const val PROGRESS_STEP = 0.002f

        const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"

        // TextToSpeech.ERROR_* (API 21+), spelled out for readability in when-branches.
        const val ERROR_SERVICE = -4
        const val ERROR_NETWORK = -6
        const val ERROR_NETWORK_TIMEOUT = -7
        const val ERROR_NOT_INSTALLED_YET = -9
    }
}

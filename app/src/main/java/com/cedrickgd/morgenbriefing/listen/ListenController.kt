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
import android.speech.tts.Voice
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.cedrickgd.morgenbriefing.MorgenbriefingApp
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ListenSettings
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.VoicePreference
import com.cedrickgd.morgenbriefing.domain.ItemEdition
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ListenStrings
import com.cedrickgd.morgenbriefing.util.attempt
import com.cedrickgd.morgenbriefing.util.findActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** Estimated length of the whole reading in seconds at normal speed (for "noch etwa 3 Min."). */
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
     * loaded or just failed (or the fixed reading language), else the main news edition's.
     */
    val voiceLanguage: String = NewsEdition.GERMANY.language,
    /** The speech rate of the settings (the mini player's speed chip shows and cycles it). */
    val rate: Float = ListenSettings.DEFAULT_RATE,
) {
    /** Something is loaded: reading, preparing or paused. */
    val active: Boolean get() = speaking || paused

    /** True while the article with [key] is being read or paused. */
    fun isReading(key: String): Boolean = active && articleKey == key
}

/** The engines and voices for the settings card "Vorlesen" ([ListenController.refreshVoices]). */
data class ListenVoices(
    /** The engine is starting or listing its voices. */
    val loading: Boolean = false,
    /** The engine did not start or answer. */
    val failed: Boolean = false,
    /** Installed engines; empty until loaded. */
    val engines: List<TtsEngine> = emptyList(),
    /** The phone's default engine (package). */
    val defaultEngine: String? = null,
    /** The engine whose voices [voices] are (package). */
    val engine: String? = null,
    /** Every voice of that engine, all languages; empty when it lists none. */
    val voices: List<VoiceInfo> = emptyList(),
    /** True once [voices] were read from an engine at least once. */
    val loaded: Boolean = false,
    /** The voice whose sample plays right now ([AUTO_VOICE] for "Automatisch"), or null. */
    val previewing: String? = null,
) {
    companion object {
        /** [previewing] for the sample of the automatic choice. */
        const val AUTO_VOICE = "\u0000auto"
    }
}

/**
 * "Zuhören" (Vorlesen): reads headline, lead, body and sources of an article with Android
 * TextToSpeech in chunks below `getMaxSpeechInputLength()`. The voice speaks the language of the
 * article's own edition ([ItemEdition] – Washington local news in English while the main
 * edition is German; the device language if there is no such voice), see [ListenLanguages].
 * One instance per process (`context.listen`), safe to call from any thread (everything runs on
 * the main thread).
 *
 * * "Vorlesen" ([ListenSettings]): the engine package, a fixed reading language, the voice per
 *   language (chosen → preferred gender → the engine's best offline voice, [VoiceChoice]), speed
 *   and pitch. Applied before every reading; a change while reading continues at the current
 *   sentence in the new voice or speed. Nothing configured reads exactly as before.
 * * The engine is created on the first [play] (or [refreshVoices]/[preview]) and shut down after
 *   a minute without reading.
 * * Audio focus: any loss pauses; a short one (call, navigation prompt) resumes by itself.
 * * Headphones unplugged or the app sent to the background (screen still on) → pause; with the
 *   screen simply turned off it keeps reading.
 * * [pauseOrResume] restarts the interrupted sentence group, not the whole article.
 *
 * @param onPlay told of every article that starts reading (it counts as read).
 */
class ListenController(context: Context, private val onPlay: (NewsItem) -> Unit = {}) {
    private val app: Context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private val mutableState = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = mutableState.asStateFlow()

    private val mutableVoices = MutableStateFlow(ListenVoices())

    /** Engines and voices for the settings; filled by [refreshVoices]. */
    val voices: StateFlow<ListenVoices> = mutableVoices.asStateFlow()

    // ---- Everything below is confined to the main thread. ----

    private enum class Engine { OFF, STARTING, READY }

    /** The settings (null until loaded), the main news edition and the interface language, kept in sync. */
    private var settings: AppSettings? = null
    private var edition: NewsEdition = NewsEdition.GERMANY
    private var uiLanguage: AppLanguage = AppLanguage.system()
    private var listen: ListenSettings = ListenSettings()

    /** The edition of the article being read (its own language); the main edition while idle. */
    private var articleEdition: NewsEdition = NewsEdition.GERMANY

    /** The edition whose voice reads ([articleEdition], or the fixed reading language). */
    private var voiceEdition: NewsEdition = NewsEdition.GERMANY

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var tts: TextToSpeech? = null
    private var engine = Engine.OFF

    /** The engine package the running instance was asked for (null = the phone's default). */
    private var requestedEngine: String? = null

    /** The locale the ready engine was set to. */
    private var engineLocale: Locale? = null

    /** The voice settings the ready engine is set to; anything else re-picks the voice before reading. */
    private var appliedVoice: AppliedVoice? = null

    /** Speed and pitch set on the engine instance; null = never set (the phone's defaults apply). */
    private var appliedRate: Float? = null
    private var appliedPitch: Float? = null

    /** The voices of the running engine, read once per instance. */
    private var engineVoices: List<Voice>? = null

    /** The ready engine reads the article's language only with a network voice (see [ListenState.onlineVoiceOnly]). */
    private var voiceOnlineOnly = false

    /** Bumped for every engine instance; callbacks of an older one are ignored. */
    private var engineGeneration = 0

    private var script: ListenScript? = null

    /** Part to (re)start from: the one being spoken, or the next one. */
    private var position = 0

    /** Where the voice is in part [position] (characters, from `onRangeStart`). */
    private var rangeOffset = 0

    /** The first part of a reading restarted mid-way is spoken from character [offsetChars] on. */
    private var offsetIndex = -1
    private var offsetChars = 0

    /** Bumped on every play/pause/stop; utterance callbacks of another session are ignored. */
    private var session = 0

    /** One automatic engine restart per reading when the TTS service dies mid-way. */
    private var restartsLeft = 0
    private var resumeOnFocusGain = false
    private var noisyRegistered = false
    private var lifecycleObserved = false

    /** The settings showed the voices once: an engine switch loads the new engine's voices. */
    private var catalogWanted = false

    /** Load the voice list as soon as the engine is ready. */
    private var catalogPending = false

    /** Voice data may have been installed meanwhile: the next [refreshVoices] starts a fresh engine. */
    private var catalogStale = false

    private var pendingPreview: PreviewRequest? = null
    private var previewCount = 0

    /** Natural voices on the phone (downloaded in the settings); null in tests without the app. */
    private val neuralVoices: NeuralVoices? get() = (app as? MorgenbriefingApp)?.container?.neuralVoices

    /** Plays natural voices; created on the first reading with one ([NeuralRuntime] loaded). */
    private var neuralSpeaker: NeuralSpeaker? = null

    /** The current reading speaks with a natural voice (not the engine). */
    private var neuralActive = false

    /** Natural voices that failed to load in this process: their language reads with the engine. */
    private val brokenNeural = mutableSetOf<String>()

    private val neuralListener = object : NeuralSpeaker.Listener {
        override fun onPartStart(token: Int, index: Int) {
            main.post { if (token == session && neuralActive) onPartStart(index) }
        }

        override fun onPartRange(token: Int, index: Int, start: Int) {
            main.post { if (token == session && neuralActive) onPartRange(index, start) }
        }

        override fun onPartDone(token: Int, index: Int) {
            main.post {
                when {
                    token == session && neuralActive -> onPartDone(index)
                    token < 0 && token == -previewCount -> onPreviewDone()
                }
            }
        }

        override fun onFailed(token: Int, index: Int) {
            main.post { onNeuralFailed(token, index) }
        }
    }

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

        override fun onDone(utteranceId: String?) {
            if (previewEnded(utteranceId)) return
            dispatch(utteranceId) { index, silence -> if (!silence) onPartDone(index) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (previewEnded(utteranceId)) return
            dispatch(utteranceId) { index, silence -> if (!silence) onPartError(index, TextToSpeech.ERROR) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (previewEnded(utteranceId)) return
            dispatch(utteranceId) { index, silence -> if (!silence) onPartError(index, errorCode) }
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            previewEnded(utteranceId)
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
                    .collect(::onSettings)
            }
        }
    }

    private fun onSettings(newSettings: AppSettings) {
        val previous = settings?.listen
        settings = newSettings
        edition = newSettings.edition
        uiLanguage = newSettings.language
        listen = newSettings.listen
        // An idle hint names the voice of the current edition.
        if (script == null) articleEdition = newSettings.edition
        voiceEdition = ListenLanguages.voiceEdition(articleEdition, listen.language, edition)
        mutableState.update {
            val withRate = it.copy(rate = listen.rate)
            if (it.active || it.issue != null) withRate else withRate.copy(voiceLanguage = voiceEdition.language)
        }
        if (previous != null && previous != listen) onListenSettingsChanged(previous, listen)
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
        val spoken = ListenLanguages.voiceEdition(itemEdition, listen.language, edition)
        val next = ListenScript.from(item, ListenLanguages.scriptLanguage(uiLanguage, spoken), maxLength, body)
        if (next.isEmpty) return@onMain

        articleEdition = itemEdition
        voiceEdition = spoken
        // A sample still waiting for the engine gives way to the article (a playing one is flushed below).
        if (pendingPreview != null) {
            pendingPreview = null
            mutableVoices.update { it.copy(previewing = null) }
        }

        session++
        stopSpeech()
        script = next
        scriptIsFull = !body.isNullOrBlank()
        position = 0
        rangeOffset = 0
        offsetIndex = -1
        restartsLeft = 1
        resumeOnFocusGain = false
        mutableState.value = ListenState(
            available = true,
            speaking = true,
            title = item.headline.trim().ifEmpty { ListenStrings.of(uiLanguage).articleFallback },
            articleKey = key,
            articleUrl = item.url,
            preparing = engine != Engine.READY || neuralPick() != null,
            onlineVoiceOnly = voiceOnlineOnly,
            estimatedSeconds = next.estimatedSeconds,
            voiceLanguage = spoken.language,
            rate = listen.rate,
        )
        // A voice for another language than the engine's is picked right before speaking (ensureVoice).
        begin()
        try {
            onPlay(item)
        } catch (e: Exception) {
            Log.w(TAG, "Could not note the article as read", e)
        }
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
        stopSpeech()
        script = null
        position = 0
        resumeOnFocusGain = false
        abandonFocus()
        unregisterNoisy()
        articleEdition = edition
        voiceEdition = ListenLanguages.voiceEdition(edition, listen.language, edition)
        mutableState.value = idleState(available = mutableState.value.available)
        scheduleRelease()
    }

    /**
     * The mini player's speed chip: 1× → 1,25× → 1,5× → 2× → 0,75× → 1×, stored in the settings
     * (atomically, so quick taps each count) and applied to the reading at once.
     */
    fun cycleRate() {
        val repository = (app as? MorgenbriefingApp)?.container?.settings ?: return
        scope.launch {
            attempt { repository.update { it.copy(listen = it.listen.copy(rate = ListenSettings.nextChipRate(it.listen.rate))) } }
                .onFailure { Log.w(TAG, "Could not store the speech rate", it) }
        }
    }

    /**
     * Loads the installed engines and the chosen engine's voices into [voices], starting the
     * engine when needed (it is released again after a minute without reading). Called by the
     * settings whenever they show the card; after "Weitere Stimmen installieren" it starts a
     * fresh engine, so new voices appear.
     */
    fun refreshVoices() = onMain {
        catalogWanted = true
        if (catalogStale && engine != Engine.OFF && !mutableState.value.speaking) shutdownEngine()
        catalogStale = false
        when (engine) {
            Engine.READY -> tts?.let(::loadCatalog)
            Engine.STARTING -> catalogPending = true
            Engine.OFF -> {
                catalogPending = true
                mutableVoices.update { it.copy(loading = true, failed = false) }
                startEngine()
            }
        }
    }

    /**
     * "Probehören": speaks a short sample in [language] with the voice [voiceName] (null: the
     * voice "Automatisch" picks) at [rate] and [pitch]. A reading in progress pauses.
     */
    fun preview(language: String, voiceName: String?, rate: Float, pitch: Float) = onMain {
        if (mutableState.value.speaking) pause(keepFocus = false)
        mutableVoices.update { it.copy(previewing = voiceName ?: ListenVoices.AUTO_VOICE) }
        val neural = NeuralVoiceIds.idOf(voiceName)
        if (neural != null) {
            pendingPreview = null
            return@onMain previewNeural(language.lowercase(Locale.ROOT), neural, rate, pitch)
        }
        pendingPreview = PreviewRequest(language.lowercase(Locale.ROOT), voiceName, rate, pitch)
        when (engine) {
            Engine.READY -> runPreview()
            Engine.STARTING -> Unit // onEngineInit plays it.
            Engine.OFF -> startEngine()
        }
    }

    /**
     * Opens the system screen that installs voice data (falls back to the text-to-speech
     * settings). The next [play] checks the voices again.
     */
    fun installVoiceData(from: Context) {
        if (!launchVoiceInstaller(from)) {
            // The language that is missing: the failed article's, read before stop() resets it.
            val language = mutableState.value.voiceLanguage
            Toast.makeText(app, ListenStrings.of(uiLanguage).installVoiceHint(language), Toast.LENGTH_LONG).show()
        }
        onMain {
            stop()
            releaseEngine()
            catalogStale = true
            mutableState.update { it.copy(available = true, issue = null) }
        }
    }

    /**
     * "Weitere Stimmen installieren" in the settings: opens the engine's voice installer without
     * touching a reading; the settings show the new voices when they come back ([refreshVoices]).
     */
    fun openVoiceInstaller(from: Context, language: String) {
        if (!launchVoiceInstaller(from)) {
            Toast.makeText(app, ListenStrings.of(uiLanguage).installVoiceHint(language), Toast.LENGTH_LONG).show()
        }
        onMain { catalogStale = true }
    }

    /** The installer of the engine in use, any installer, the text-to-speech settings, the settings. */
    private fun launchVoiceInstaller(from: Context): Boolean {
        val enginePackage = mutableVoices.value.engine ?: listen.engine
        val intents = listOfNotNull(
            enginePackage?.let { Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(it) },
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent(TTS_SETTINGS_ACTION),
            Intent(Settings.ACTION_SETTINGS),
        )
        return intents.any { intent ->
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
        // A natural voice needs no engine.
        if (neuralPick() != null) return speakFrom(position)
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
        stopSpeech()
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
        articleEdition = edition
        voiceEdition = ListenLanguages.voiceEdition(edition, listen.language, edition)
        mutableState.value = idleState(available = true)
        scheduleRelease()
    }

    /** Nothing loaded: the hint names the voice of the main edition (or the fixed language). */
    private fun idleState(available: Boolean): ListenState =
        ListenState(available = available, voiceLanguage = voiceEdition.language, rate = listen.rate)

    /**
     * Queues the script from part [index] on; the first part from character [charOffset] (a
     * sentence start) when a reading continues in a new voice or speed.
     */
    private fun speakFrom(index: Int, charOffset: Int = 0) {
        val current = script ?: return
        if (index > current.lastIndex) return finish()
        neuralPick()?.let { (voice, files) -> return speakNeural(current, index, charOffset, voice, files) }
        neuralActive = false
        val engineTts = tts ?: return startEngine()
        if (!ensureVoice(engineTts)) return
        val first = current.parts[index].text
        offsetIndex = index
        offsetChars = charOffset.takeIf { it in 1 until first.length } ?: 0
        position = index
        rangeOffset = offsetChars
        val id = session
        for (i in index..current.lastIndex) {
            val part = current.parts[i]
            val text = if (i == index && offsetChars > 0) part.text.substring(offsetChars) else part.text
            val mode = if (i == index) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (engineTts.speak(text, mode, null, UtteranceIds.speech(id, i)) == TextToSpeech.ERROR) {
                Log.w(TAG, "TextToSpeech refused part $i; restarting the engine")
                return restartEngine(fromIndex = i)
            }
            if (part.pauseAfterMs > 0) {
                engineTts.playSilentUtterance(part.pauseAfterMs.toLong(), TextToSpeech.QUEUE_ADD, UtteranceIds.silence(id, i))
            }
        }
        mutableState.update { it.copy(preparing = false, progress = current.progressAt(index, offsetChars)) }
    }

    /**
     * The natural voice chosen for the language being read, when it is installed and the speech
     * library loads; null reads with the engine (also for a voice that failed before).
     */
    private fun neuralPick(): Pair<NeuralVoice, NeuralVoiceFiles>? {
        val id = NeuralVoiceIds.idOf(listen.voiceFor(voiceEdition.language)) ?: return null
        if (id in brokenNeural) return null
        val voice = NeuralVoiceCatalog.byId(id) ?: return null
        val files = neuralVoices?.files(id) ?: return null
        return speakerOrNull()?.let { voice to files }
    }

    private fun speakerOrNull(): NeuralSpeaker? {
        neuralSpeaker?.let { return it }
        val engine = NeuralRuntime.engine ?: return null
        return NeuralSpeaker(engine, neuralListener).also { neuralSpeaker = it }
    }

    /** [speakFrom] with a natural voice: the same parts and offsets, played by [NeuralSpeaker]. */
    private fun speakNeural(current: ListenScript, index: Int, charOffset: Int, voice: NeuralVoice, files: NeuralVoiceFiles) {
        val speaker = speakerOrNull() ?: return
        // The engine may still say a sample: one voice at a time.
        tts?.stop()
        neuralActive = true
        val first = current.parts[index].text
        offsetIndex = index
        offsetChars = charOffset.takeIf { it in 1 until first.length } ?: 0
        position = index
        rangeOffset = offsetChars
        val parts = current.parts.subList(index, current.parts.size).mapIndexed { i, part ->
            if (i == 0 && offsetChars > 0) part.copy(text = part.text.substring(offsetChars)) else part
        }
        voiceOnlineOnly = false
        // Stays "preparing" until the first sentence sounds (onPartStart).
        mutableState.update { it.copy(onlineVoiceOnly = false, progress = current.progressAt(index, offsetChars)) }
        speaker.speak(session, voice, files, parts, index, listen.rate, listen.pitch)
    }

    /** A natural voice did not load or play: remember it and read on with the engine where the reading is. */
    private fun onNeuralFailed(token: Int, index: Int) {
        if (token < 0) {
            if (token == -previewCount) onPreviewDone()
            return
        }
        if (token != session || !neuralActive) return
        NeuralVoiceIds.idOf(listen.voiceFor(voiceEdition.language))?.let { brokenNeural += it }
        Log.w(TAG, "Natural voice failed at part $index; reading on with the engine")
        neuralActive = false
        session++
        if (mutableState.value.speaking && script != null) speakFrom(if (index >= 0) index else position)
    }

    /** Silences whichever voice reads (engine or natural). */
    private fun stopSpeech() {
        tts?.stop()
        neuralSpeaker?.stop()
    }

    /** Characters of part [index] skipped by a mid-way restart (its utterance starts there). */
    private fun offsetOf(index: Int): Int = if (index == offsetIndex) offsetChars else 0

    private fun onPartStart(index: Int) {
        val current = script ?: return
        position = index
        rangeOffset = offsetOf(index)
        publishProgress(current.progressAt(index, rangeOffset), force = true)
    }

    private fun onPartRange(index: Int, start: Int) {
        val current = script ?: return
        val absolute = start + offsetOf(index)
        if (index == position) rangeOffset = absolute
        publishProgress(current.progressAt(index, absolute), force = false)
    }

    private fun onPartDone(index: Int) {
        val current = script ?: return
        position = (index + 1).coerceAtMost(current.lastIndex)
        rangeOffset = 0
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

    // ---- Settings while reading ----

    /** [onSettings] already moved [voiceEdition] to a new fixed language; this makes the reading follow. */
    private fun onListenSettingsChanged(old: ListenSettings, new: ListenSettings) {
        if (old.engine != new.engine) return switchEngine()
        if (old.language != new.language) mutableState.update { if (it.active) it.copy(voiceLanguage = voiceEdition.language) else it }
        // Only what changes the voice being heard (not a voice chosen for another language)
        // restarts the sentence; a paused reading picks it up when it resumes (speakFrom → ensureVoice).
        val heard = engineLocale?.language?.takeIf { !neuralActive }?.let(VoiceLanguages::language) ?: voiceEdition.language
        val audible = old.rate != new.rate || old.pitch != new.pitch || old.language != new.language ||
            old.voicePreference != new.voicePreference || old.voiceFor(heard) != new.voiceFor(heard)
        if (audible && mutableState.value.speaking && (neuralActive || engine == Engine.READY) && script != null) continueInNewVoice()
    }

    /** Re-queues the reading from the sentence the voice is in, so the change is heard at once. */
    private fun continueInNewVoice() {
        val current = script ?: return
        val index = position.coerceIn(0, current.lastIndex)
        val restart = sentenceStartBefore(current.parts[index].text, rangeOffset)
        session++
        stopSpeech()
        speakFrom(index, restart)
    }

    /** Another engine was chosen: start it (continuing a reading where it is) or start it for the voice list. */
    private fun switchEngine() {
        // A reading with a natural voice goes on; only the engine's voice list changes.
        val reading = mutableState.value.speaking && script != null && !neuralActive
        // A sample of the old engine's voice makes no sense in the new one.
        pendingPreview = null
        mutableVoices.update { it.copy(voices = emptyList(), loaded = false, loading = catalogWanted, failed = false, previewing = null) }
        if (engine != Engine.OFF) {
            session++
            shutdownEngine()
        }
        when {
            reading -> startEngine() // onEngineInit continues at [position].
            catalogWanted -> {
                catalogPending = true
                startEngine()
            }
        }
    }

    // ---- Engine ----

    private fun startEngine() {
        main.removeCallbacks(releaseEngineTask)
        engine = Engine.STARTING
        val generation = ++engineGeneration
        val requested = listen.engine
        requestedEngine = requested
        mutableState.update { if (it.speaking) it.copy(preparing = true) else it }
        tts = try {
            // onInit may even run inside the constructor (no engine): always hop to the queue.
            // A requested engine that is no longer installed falls back to the default one.
            val listener = TextToSpeech.OnInitListener { status -> main.post { onEngineInit(generation, status) } }
            if (requested == null) TextToSpeech(app, listener) else TextToSpeech(app, listener, requested)
        } catch (e: RuntimeException) {
            Log.w(TAG, "TextToSpeech could not be created", e)
            null
        }
        if (tts == null) {
            engineFailed()
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
            return engineFailed()
        }
        try {
            engineTts.setAudioAttributes(AudioFocus.attributes)
            engineTts.setOnUtteranceProgressListener(progressListener)
        } catch (e: RuntimeException) {
            Log.w(TAG, "TextToSpeech setup failed", e)
            return engineFailed()
        }
        engine = Engine.READY
        if (catalogPending) loadCatalog(engineTts)
        when {
            mutableState.value.speaking && script != null -> {
                mutableState.update { it.copy(available = true, issue = null) }
                speakFrom(position)
            }
            pendingPreview != null -> runPreview()
            else -> scheduleRelease()
        }
    }

    private fun onInitTimeout() {
        if (engine == Engine.STARTING) {
            Log.w(TAG, "TextToSpeech did not start in time")
            engineFailed()
        }
    }

    /**
     * The engine did not start. A reading fails with [ListenIssue.NO_ENGINE] (the mini player
     * explains it); an engine started only for the settings' voice list or a sample leaves the
     * reading state alone and reports it there.
     */
    private fun engineFailed() {
        if (mutableState.value.speaking && script != null) return failEngine(ListenIssue.NO_ENGINE)
        shutdownEngine()
        pendingPreview = null
        catalogPending = false
        mutableVoices.update { it.copy(loading = false, failed = true, previewing = null) }
    }

    /**
     * Sets the engine to the voice for [voiceEdition] and [listen] unless it already is: the
     * language (see [pickLanguage]), then the chosen voice, a voice of the preferred gender, or
     * the best offline voice ([VoiceChoice]); then speed and pitch. False after failing the engine
     * with [ListenIssue.MISSING_VOICE].
     */
    private fun ensureVoice(engineTts: TextToSpeech): Boolean {
        val wanted = AppliedVoice(ListenLanguages.voiceLocales(voiceEdition), listen.voices, listen.voicePreference)
        if (wanted != appliedVoice) {
            val language = pickLanguage(engineTts)
            if (language == null) {
                Log.w(TAG, "No voice for ${voiceEdition.language} (nor the device language) available")
                failEngine(ListenIssue.MISSING_VOICE)
                return false
            }
            engineLocale = language
            voiceOnlineOnly = !applyVoiceFor(engineTts, language)
            appliedVoice = wanted
            mutableState.update { it.copy(onlineVoiceOnly = voiceOnlineOnly) }
        }
        applyProsody(engineTts, listen.rate, listen.pitch)
        return true
    }

    /**
     * Picks the voice for [locale] ([VoiceChoice]) on the engine. Returns false when only network
     * voices exist (reading will fail offline). Engines that do not list their voices are
     * trusted (true).
     */
    private fun applyVoiceFor(engineTts: TextToSpeech, locale: Locale): Boolean {
        val voices = voiceList(engineTts) ?: return true
        val infos = voices.map(::voiceInfo)
        val language = VoiceLanguages.language(locale.language)
        val country = if (language == voiceEdition.language) voiceEdition.country else VoiceLanguages.country(locale.country)
        val pick = VoiceChoice.resolve(infos, language, listen.voiceFor(language), listen.voicePreference, country)
        if (pick.chosenMissing) Log.i(TAG, "The chosen $language voice is gone; reading with ${pick.source}")
        pick.voice?.let { info -> voices.firstOrNull { it.name == info.name } }?.let { setVoice(engineTts, it) }
        return !VoiceChoice.onlineOnly(infos, language)
    }

    /** Speed and pitch relative to the phone's own text-to-speech settings (1× = as the phone reads). */
    private fun applyProsody(engineTts: TextToSpeech, rate: Float, pitch: Float) {
        // Never touched on this instance and normal: leave the phone's defaults (as before these settings).
        if (rate != appliedRate && (appliedRate != null || rate != ListenSettings.DEFAULT_RATE)) {
            try {
                engineTts.setSpeechRate(systemDefault(Settings.Secure.TTS_DEFAULT_RATE) * rate)
                appliedRate = rate
            } catch (e: RuntimeException) {
                Log.w(TAG, "setSpeechRate($rate) failed", e)
            }
        }
        if (pitch != appliedPitch && (appliedPitch != null || pitch != ListenSettings.DEFAULT_PITCH)) {
            try {
                engineTts.setPitch(systemDefault(Settings.Secure.TTS_DEFAULT_PITCH) * pitch)
                appliedPitch = pitch
            } catch (e: RuntimeException) {
                Log.w(TAG, "setPitch($pitch) failed", e)
            }
        }
    }

    /** The phone's text-to-speech speed or pitch (Einstellungen › Sprachausgabe) as a factor; 1 when unreadable. */
    private fun systemDefault(key: String): Float = try {
        (Settings.Secure.getInt(app.contentResolver, key, 100) / 100f).takeIf { it in 0.1f..10f } ?: 1f
    } catch (e: RuntimeException) {
        1f
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

    /** The engine's voices (read once per engine instance); null when it cannot list them. */
    private fun voiceList(engineTts: TextToSpeech): List<Voice>? {
        engineVoices?.let { return it }
        return try {
            engineTts.voices.orEmpty().toList().also { engineVoices = it }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Voice list unavailable", e)
            null
        }
    }

    private fun setVoice(engineTts: TextToSpeech, voice: Voice) {
        try {
            if (engineTts.setVoice(voice) != TextToSpeech.SUCCESS) Log.w(TAG, "setVoice(${voice.name}) refused")
        } catch (e: RuntimeException) {
            Log.w(TAG, "setVoice(${voice.name}) failed", e)
        }
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
        neuralSpeaker?.stop()
        neuralActive = false
        session++
        script = null
        position = 0
        resumeOnFocusGain = false
        pendingPreview = null
        abandonFocus()
        unregisterNoisy()
        if (catalogPending) {
            catalogPending = false
            mutableVoices.update { it.copy(loading = false, failed = true) }
        }
        // The hint names the voice that is missing: the article's language, not the main edition's.
        mutableState.value = ListenState(available = false, issue = issue, voiceLanguage = voiceEdition.language, rate = listen.rate)
    }

    private fun scheduleRelease() {
        main.removeCallbacks(releaseEngineTask)
        main.postDelayed(releaseEngineTask, IDLE_RELEASE_MS)
    }

    private fun releaseEngine() {
        main.removeCallbacks(releaseEngineTask)
        if (mutableState.value.speaking) return
        shutdownEngine()
        neuralSpeaker?.release()
    }

    private fun shutdownEngine() {
        main.removeCallbacks(initWatchdog)
        val old = tts
        tts = null
        engine = Engine.OFF
        engineLocale = null
        appliedVoice = null
        appliedRate = null
        appliedPitch = null
        engineVoices = null
        engineGeneration++
        if (mutableVoices.value.previewing != null) mutableVoices.update { it.copy(previewing = null) }
        if (old != null) {
            try {
                old.stop()
                old.shutdown()
            } catch (e: RuntimeException) {
                Log.w(TAG, "TextToSpeech shutdown failed", e)
            }
        }
    }

    // ---- Voice list and samples (settings) ----

    /** Reads the engines and voices off the main thread (the voice list can be long) into [voices]. */
    private fun loadCatalog(engineTts: TextToSpeech) {
        catalogPending = false
        val generation = engineGeneration
        val requested = requestedEngine
        mutableVoices.update { it.copy(loading = true, failed = false) }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                attempt {
                    val engines = engineTts.engines.orEmpty().map { TtsEngine(it.name, it.label.orEmpty()) }
                    Triple(engines, engineTts.defaultEngine, engineTts.voices.orEmpty().toList())
                }
            }
            // Another engine was chosen meanwhile: its own list follows.
            if (requested != listen.engine) return@launch
            result.fold(
                onSuccess = { (engines, defaultEngine, list) ->
                    val current = generation == engineGeneration
                    if (current) engineVoices = list
                    // An engine released meanwhile answers with nothing: keep what is shown.
                    if (!current && list.isEmpty()) {
                        mutableVoices.update { it.copy(loading = false) }
                        return@fold
                    }
                    val running = requested?.takeIf { wanted -> engines.any { it.name == wanted } } ?: defaultEngine
                    mutableVoices.update {
                        it.copy(
                            loading = false,
                            failed = false,
                            engines = engines,
                            defaultEngine = defaultEngine,
                            engine = running,
                            voices = list.map(::voiceInfo),
                            loaded = true,
                        )
                    }
                },
                onFailure = { e ->
                    Log.w(TAG, "Could not list the voices", e)
                    mutableVoices.update { it.copy(loading = false, failed = true) }
                },
            )
        }
    }

    private fun runPreview() {
        val request = pendingPreview ?: return
        pendingPreview = null
        val engineTts = tts ?: return
        main.removeCallbacks(releaseEngineTask)
        neuralSpeaker?.stop()
        // The sample changes the engine's language and voice: the next reading sets its own again.
        appliedVoice = null
        val sampleEdition = ListenLanguages.voiceEdition(edition, request.language, edition)
        val locale = ListenLanguages.voiceLocales(sampleEdition).firstOrNull { setLanguage(engineTts, it) >= TextToSpeech.LANG_AVAILABLE }
        if (locale == null || !audioFocus.request()) {
            onPreviewDone()
            return
        }
        val voices = voiceList(engineTts).orEmpty()
        val name = request.voice ?: VoiceChoice.resolve(
            voices.map(::voiceInfo), request.language, chosen = null, preference = listen.voicePreference, preferredCountry = sampleEdition.country,
        ).voice?.name
        name?.let { wanted -> voices.firstOrNull { it.name == wanted } }?.let { setVoice(engineTts, it) }
        applyProsody(engineTts, request.rate, request.pitch)
        val text = ListenStrings.of(ListenLanguages.scriptLanguage(uiLanguage, sampleEdition)).sampleSentence
        val result = try {
            engineTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UtteranceIds.preview(++previewCount))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Sample refused", e)
            TextToSpeech.ERROR
        }
        if (result == TextToSpeech.ERROR) onPreviewDone()
    }

    /** "Probehören" of a natural voice: the sample sentence of [language], played by [NeuralSpeaker]. */
    private fun previewNeural(language: String, id: String, rate: Float, pitch: Float) {
        val voice = NeuralVoiceCatalog.byId(id)
        val files = neuralVoices?.files(id)
        val speaker = speakerOrNull()
        if (voice == null || files == null || speaker == null || !audioFocus.request()) {
            onPreviewDone()
            return
        }
        main.removeCallbacks(releaseEngineTask)
        tts?.stop()
        val sampleEdition = ListenLanguages.voiceEdition(edition, language, edition)
        val text = ListenStrings.of(ListenLanguages.scriptLanguage(uiLanguage, sampleEdition)).sampleSentence
        speaker.speak(-(++previewCount), voice, files, listOf(SpeechPart(text)), 0, rate, pitch)
    }

    /**
     * "Laden" in the settings: downloads [voice] and, once it is on the phone, reads its language
     * with it (the user asked for exactly this voice).
     */
    fun downloadNeural(voice: NeuralVoice) {
        val voices = neuralVoices ?: return
        voices.download(voice) {
            val repository = (app as? MorgenbriefingApp)?.container?.settings ?: return@download
            scope.launch {
                attempt { repository.update { it.copy(listen = it.listen.withVoice(voice.language, NeuralVoiceIds.choice(voice.id))) } }
                    .onFailure { Log.w(TAG, "Could not choose ${voice.id}", it) }
            }
        }
    }

    /** Stops downloading [voice]. */
    fun cancelNeural(voice: NeuralVoice) {
        neuralVoices?.cancel(voice.id)
    }

    /** Removes a natural voice; a language that read with it goes back to "Automatisch". */
    fun deleteNeural(voice: NeuralVoice) {
        neuralVoices?.delete(voice.id)
        onMain { brokenNeural -= voice.id }
        val repository = (app as? MorgenbriefingApp)?.container?.settings ?: return
        scope.launch {
            attempt {
                repository.update {
                    val chosen = it.listen.voiceFor(voice.language) == NeuralVoiceIds.choice(voice.id)
                    if (chosen) it.copy(listen = it.listen.withVoice(voice.language, null)) else it
                }
            }.onFailure { Log.w(TAG, "Could not reset the voice after deleting ${voice.id}", it) }
        }
    }

    /** True for a sample's utterance id (handled here: the sample ended), false for everything else. */
    private fun previewEnded(utteranceId: String?): Boolean {
        val count = UtteranceIds.previewCount(utteranceId) ?: return false
        main.post { if (count == previewCount) onPreviewDone() }
        return true
    }

    private fun onPreviewDone() {
        mutableVoices.update { it.copy(previewing = null) }
        if (!mutableState.value.speaking) {
            abandonFocus()
            scheduleRelease()
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

    /** The voice-related settings an engine was set to for a language. */
    private data class AppliedVoice(
        val locales: List<Locale>,
        val chosen: Map<String, String>,
        val preference: VoicePreference,
    )

    private data class PreviewRequest(val language: String, val voice: String?, val rate: Float, val pitch: Float)

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

        /** What the app needs to know about an engine's voice. */
        fun voiceInfo(voice: Voice): VoiceInfo {
            val locale: Locale? = voice.locale
            val features = voice.features.orEmpty()
            return VoiceInfo(
                name = voice.name,
                language = VoiceLanguages.language(locale?.language.orEmpty()),
                country = VoiceLanguages.country(locale?.country.orEmpty()),
                quality = voice.quality,
                latency = voice.latency,
                networkRequired = voice.isNetworkConnectionRequired,
                notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in features,
                features = features,
            )
        }
    }
}

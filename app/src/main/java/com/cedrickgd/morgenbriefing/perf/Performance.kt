package com.cedrickgd.morgenbriefing.perf

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.cedrickgd.morgenbriefing.data.model.EffectsMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The phone's [DeviceTier] and the [Effects] the app runs with, for the whole process (owned
 * by the AppContainer). The hardware is probed once off the main thread; the battery saver is
 * watched while the process lives. [effects] follows the "Effekte" setting ([modes]) live.
 *
 * The UI resolves the same [Effects] from the settings it already holds (`AppRoot` →
 * `LocalEffects`); background work asks [light].
 */
class Performance(context: Context, modes: Flow<EffectsMode>, scope: CoroutineScope) {
    private val app = context.applicationContext

    /** The probe's answer once it is in (read by [hardwareTier] without waiting). */
    @Volatile private var probed: DeviceSpecs? = null

    private val specs = scope.async(Dispatchers.IO) {
        val found = try {
            DeviceProbe.specs(app)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Device probe failed", e)
            DeviceSpecs(Build.VERSION.SDK_INT, totalRamBytes = 0, isLowRamDevice = false, maxCpuKhz = null)
        }
        probed = found
        found
    }

    /** The tier and why; null for the first few milliseconds, until the probe answered. */
    val assessment: StateFlow<TierAssessment?> = combine(
        flow { emit(specs.await()) },
        DeviceProbe.powerSaveMode(app).catch { e ->
            Log.w(TAG, "Battery saver unavailable", e)
            emit(false)
        },
    ) { found, powerSave -> DeviceTiers.assess(found, powerSave) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The effects in use: the setting resolved against [assessment]; null until both are known. */
    val effects: StateFlow<Effects?> = combine(
        assessment.filterNotNull(),
        modes.distinctUntilChanged().catch { emit(EffectsMode.AUTO) },
    ) { found, mode -> Effects.resolve(mode, found.tier) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * True when background work should stay light (fewer parallel article checks, parsing on
     * [BackgroundWork.parsing]). Also while the effects are not known yet – a few milliseconds
     * after the start – so a budget phone never starts with a burst.
     */
    val light: Boolean get() = effects.value != Effects.FULL

    /** The hardware alone, without the battery saver (memory budgets); null until probed. */
    val hardwareTier: DeviceTier?
        get() = probed?.let { DeviceTiers.assess(it, powerSave = false).tier }

    private companion object {
        const val TAG = "Performance"
    }
}

/**
 * Where CPU-heavy background work runs on phones with light effects (article pages parsed and
 * extracted for "nur vollständige Artikel" and preloading): [THREADS] threads at background
 * priority, so two article parses at most compete with the UI and lose against it when they do.
 * Idle threads end after a while. Full effects keep `Dispatchers.Default`.
 */
object BackgroundWork {
    const val THREADS = 2

    val parsing: CoroutineDispatcher by lazy {
        ThreadPoolExecutor(THREADS, THREADS, IDLE_SECONDS, TimeUnit.SECONDS, LinkedBlockingQueue(), LowPriorityThreads)
            .apply { allowCoreThreadTimeOut(true) }
            .asCoroutineDispatcher()
    }

    private const val IDLE_SECONDS = 30L

    private object LowPriorityThreads : ThreadFactory {
        private val count = AtomicInteger()

        override fun newThread(task: Runnable): Thread = Thread(
            {
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                } catch (e: RuntimeException) {
                    // Not on Android (unit tests): the default priority is fine there.
                }
                task.run()
            },
            "mb-parse-${count.incrementAndGet()}",
        ).apply { isDaemon = true }
    }
}

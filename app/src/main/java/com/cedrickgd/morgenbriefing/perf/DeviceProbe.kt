package com.cedrickgd.morgenbriefing.perf

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

/**
 * Reads what the phone is made of ([DeviceSpecs]) and watches the battery saver. [specs] reads
 * a few small sysfs files: call it off the main thread (once per process is enough).
 */
object DeviceProbe {
    private const val TAG = "DeviceProbe"

    /** Where Linux lists the cores: `cpu0`, `cpu1` … each with `cpufreq/cpuinfo_max_freq` (kHz). */
    private val CPU_ROOT = File("/sys/devices/system/cpu")

    /** Memory, the low-RAM flag and the fastest core; anything unreadable counts as unknown. */
    fun specs(context: Context): DeviceSpecs {
        val activity = context.getSystemService(ActivityManager::class.java)
        val memory = try {
            ActivityManager.MemoryInfo().also { activity?.getMemoryInfo(it) }.totalMem
        } catch (e: RuntimeException) {
            Log.w(TAG, "Memory size unavailable", e)
            0L
        }
        return DeviceSpecs(
            sdk = Build.VERSION.SDK_INT,
            totalRamBytes = memory,
            isLowRamDevice = activity?.isLowRamDevice == true,
            maxCpuKhz = maxCpuKhz(CPU_ROOT),
        )
    }

    /**
     * The highest `cpuinfo_max_freq` (kHz) of all cores below [root], or null when no core tells
     * (emulators, SELinux denying the read). Looks at `cpuN/cpufreq/` and, as on some kernels,
     * at `cpufreq/policyN/`; when the directory cannot be listed it tries `cpu0` … `cpu15`.
     */
    fun maxCpuKhz(root: File): Long? {
        val listed = try {
            root.listFiles()?.filter { CORE_DIR.matches(it.name) }.orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
        val cores = listed.ifEmpty { (0 until FALLBACK_CORES).map { File(root, "cpu$it") } }
        val policies = try {
            File(root, "cpufreq").listFiles()?.filter { POLICY_DIR.matches(it.name) }.orEmpty()
        } catch (e: SecurityException) {
            emptyList()
        }
        val files = cores.map { File(it, "cpufreq/cpuinfo_max_freq") } + policies.map { File(it, "cpuinfo_max_freq") }
        return files.mapNotNull(::readKhz).maxOrNull()
    }

    /** A positive kHz value from a sysfs file; null when it is missing, unreadable or garbage. */
    internal fun readKhz(file: File): Long? = try {
        if (file.isFile) parseKhz(file.readText()) else null
    } catch (e: java.io.IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    internal fun parseKhz(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it > 0 }

    /** The battery saver, now and whenever it changes (`ACTION_POWER_SAVE_MODE_CHANGED`). */
    fun powerSaveMode(context: Context): Flow<Boolean> = callbackFlow {
        val app = context.applicationContext
        val power = app.getSystemService(PowerManager::class.java)
        fun current(): Boolean = power?.isPowerSaveMode == true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(current())
            }
        }
        ContextCompat.registerReceiver(
            app,
            receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            // A system broadcast: it arrives at a receiver that other apps cannot reach.
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(current())
        awaitClose { runCatching { app.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()

    private val CORE_DIR = Regex("cpu\\d+")
    private val POLICY_DIR = Regex("policy\\d+")
    private const val FALLBACK_CORES = 16
}

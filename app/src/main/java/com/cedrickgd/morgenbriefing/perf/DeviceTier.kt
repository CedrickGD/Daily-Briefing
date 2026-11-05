package com.cedrickgd.morgenbriefing.perf

import com.cedrickgd.morgenbriefing.data.model.EffectsMode

/**
 * How much the phone can carry. [FULL] gets every effect (real glass blur, the 60 fps sky),
 * [LOW] the light set ([Effects.LIGHT]) when the "Effekte" setting is on automatic.
 */
enum class DeviceTier { LOW, FULL }

/**
 * Why [DeviceTiers.assess] chose its tier, for the hint in Einstellungen → Darstellung. In the
 * order the assessment checks them: hardware first (it stays), the battery saver last (it passes).
 */
enum class TierReason {
    /** Android's own "low RAM" flag (Android Go and similar). */
    LOW_RAM_DEVICE,

    /** Android 7 (API < 26). */
    OLD_ANDROID,

    /** Less than [DeviceTiers.MIN_RAM_BYTES] of memory. */
    LITTLE_MEMORY,

    /** The fastest core is slower than [DeviceTiers.MIN_CPU_KHZ]. */
    SLOW_CPU,

    /** Capable hardware, but the battery saver is on. */
    POWER_SAVER,

    /** Nothing speaks against full effects. */
    CAPABLE,
}

data class TierAssessment(val tier: DeviceTier, val reason: TierReason)

/**
 * What the phone is made of, probed once ([DeviceProbe]). The battery saver is not part of it:
 * it changes while the app runs.
 *
 * @param totalRamBytes `ActivityManager.MemoryInfo.totalMem`; 0 or less = unknown.
 * @param maxCpuKhz the highest `cpuinfo_max_freq` of all cores; null = unknown (unreadable).
 */
data class DeviceSpecs(
    val sdk: Int,
    val totalRamBytes: Long,
    val isLowRamDevice: Boolean,
    val maxCpuKhz: Long?,
)

/**
 * Sorts phones into [DeviceTier]s. A phone is [DeviceTier.LOW] when any of these holds:
 *
 * * Android flags it as a low-RAM device (`ActivityManager.isLowRamDevice`, Android Go),
 * * it runs Android 7 (API < 26: no hardware bitmaps, slow blur fallbacks),
 * * it has less than 5 GiB of memory ([MIN_RAM_BYTES]) – Android reports the memory left after
 *   the firmware's reservations: a "4 GB" phone shows ≈ 3.6–3.8 GiB, a "6 GB" one ≈ 5.3–5.7 GiB,
 *   so 5 GiB cleanly splits 4 GB phones from 6 GB ones,
 * * its fastest core is slower than 2.2 GHz ([MIN_CPU_KHZ]) – only when the clock can be read:
 *   budget chips with only efficiency cores (Exynos 850: 8 × Cortex-A55 at 2.0 GHz) stay below,
 *   every chip with a performance core is above,
 * * the battery saver is on.
 *
 * Examples: Galaxy A13 (4 GB, 2.0 GHz) → LOW; a 6 GB / 2.4 GHz mid-range phone → FULL;
 * Galaxy S26 Ultra (12 GB, 4.4 GHz) → FULL.
 */
object DeviceTiers {
    /** API level from which a phone may get full effects (Android 8.0). */
    const val MIN_SDK = 26

    /** Memory a phone needs for full effects: 5 GiB as Android reports it (`totalMem`). */
    const val MIN_RAM_BYTES = 5L * 1024 * 1024 * 1024

    /** Clock of the fastest core a phone needs for full effects: 2.2 GHz, in kHz as sysfs reports it. */
    const val MIN_CPU_KHZ = 2_200_000L

    fun classify(sdk: Int, totalRamBytes: Long, isLowRamDevice: Boolean, maxCpuKhz: Long?, powerSave: Boolean): DeviceTier =
        assess(sdk, totalRamBytes, isLowRamDevice, maxCpuKhz, powerSave).tier

    fun assess(specs: DeviceSpecs, powerSave: Boolean): TierAssessment =
        assess(specs.sdk, specs.totalRamBytes, specs.isLowRamDevice, specs.maxCpuKhz, powerSave)

    fun assess(sdk: Int, totalRamBytes: Long, isLowRamDevice: Boolean, maxCpuKhz: Long?, powerSave: Boolean): TierAssessment {
        val reason = when {
            isLowRamDevice -> TierReason.LOW_RAM_DEVICE
            sdk < MIN_SDK -> TierReason.OLD_ANDROID
            totalRamBytes in 1 until MIN_RAM_BYTES -> TierReason.LITTLE_MEMORY
            maxCpuKhz != null && maxCpuKhz in 1 until MIN_CPU_KHZ -> TierReason.SLOW_CPU
            powerSave -> TierReason.POWER_SAVER
            else -> TierReason.CAPABLE
        }
        return TierAssessment(if (reason == TierReason.CAPABLE) DeviceTier.FULL else DeviceTier.LOW, reason)
    }
}

/**
 * The effects the app draws, resolved from the "Effekte" setting ([EffectsMode]) and the
 * phone's [DeviceTier]: [FULL] is the complete "Morgenglas" look, [LIGHT] the same design
 * without the expensive parts (frosted glass instead of real blur, a calm 10 fps sky with still
 * particles, no decorative loops) and with lighter background work.
 */
enum class Effects {
    FULL,
    LIGHT,
    ;

    /** Real blur behind glass: full effects, on Android 12+ (RenderEffect). */
    fun blursGlass(sdk: Int): Boolean = this == FULL && sdk >= BLUR_MIN_SDK

    /** The sky drifts at 10 fps with still particles: light effects, and Android 7 always (as before). */
    fun gentleSky(sdk: Int): Boolean = this == LIGHT || sdk < DeviceTiers.MIN_SDK

    /** Decorative loops (the pulsing seal, the bobbing weather emoji) run at full frame rate. */
    val decorativeMotion: Boolean get() = this == FULL

    companion object {
        /** RenderEffect, which real blur needs: Android 12. */
        const val BLUR_MIN_SDK = 31

        /** [EffectsMode.AUTO] follows the [tier]; the other modes are the user's explicit choice. */
        fun resolve(mode: EffectsMode, tier: DeviceTier): Effects = when (mode) {
            EffectsMode.AUTO -> if (tier == DeviceTier.FULL) FULL else LIGHT
            EffectsMode.FULL -> FULL
            EffectsMode.LIGHT -> LIGHT
        }
    }
}

package com.cedrickgd.morgenbriefing.perf

import com.cedrickgd.morgenbriefing.data.model.EffectsMode
import com.cedrickgd.morgenbriefing.ui.news.memoryCachePercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTierTest {

    /** Memory as Android reports it (`totalMem`), in GiB. */
    private fun gib(value: Double): Long = (value * 1024 * 1024 * 1024).toLong()

    private data class Phone(
        val name: String,
        val sdk: Int,
        val ram: Long,
        val lowRam: Boolean,
        val cpuKhz: Long?,
        val powerSave: Boolean,
        val tier: DeviceTier,
        val reason: TierReason,
    )

    private val phones = listOf(
        // Exynos 850 (8 × A55 @ 2.0 GHz), "4 GB" → 3.6 GiB reported, Android 14.
        Phone("Galaxy A13", 34, gib(3.6), false, 2_002_000, false, DeviceTier.LOW, TierReason.LITTLE_MEMORY),
        // The same chip with more memory is still a budget phone: the clock decides.
        Phone("Galaxy A13, 6 GB variant", 34, gib(5.5), false, 2_002_000, false, DeviceTier.LOW, TierReason.SLOW_CPU),
        Phone("Galaxy S26 Ultra", 36, gib(11.2), false, 4_400_000, false, DeviceTier.FULL, TierReason.CAPABLE),
        Phone("6 GB / 2.4 GHz mid-range", 34, gib(5.5), false, 2_400_000, false, DeviceTier.FULL, TierReason.CAPABLE),
        Phone("Android Go phone", 30, gib(1.8), true, 1_800_000, false, DeviceTier.LOW, TierReason.LOW_RAM_DEVICE),
        // Emulators and locked-down kernels: no clock → memory alone.
        Phone("unknown CPU, 8 GB", 34, gib(7.5), false, null, false, DeviceTier.FULL, TierReason.CAPABLE),
        Phone("unknown CPU, 4 GB (A13-like emulator)", 34, gib(3.8), false, null, false, DeviceTier.LOW, TierReason.LITTLE_MEMORY),
        Phone("S26 Ultra in battery saver", 36, gib(11.2), false, 4_400_000, true, DeviceTier.LOW, TierReason.POWER_SAVER),
        Phone("Android 7 flagship", 24, gib(5.6), false, 2_450_000, false, DeviceTier.LOW, TierReason.OLD_ANDROID),
        Phone("unknown memory, fast CPU", 33, 0, false, 3_000_000, false, DeviceTier.FULL, TierReason.CAPABLE),
    )

    @Test
    fun `phones are sorted into tiers`() {
        phones.forEach { p ->
            val assessed = DeviceTiers.assess(p.sdk, p.ram, p.lowRam, p.cpuKhz, p.powerSave)
            assertEquals(p.name, p.tier, assessed.tier)
            assertEquals(p.name, p.reason, assessed.reason)
            assertEquals(p.name, p.tier, DeviceTiers.classify(p.sdk, p.ram, p.lowRam, p.cpuKhz, p.powerSave))
        }
    }

    @Test
    fun `thresholds are inclusive for full effects`() {
        fun tier(ram: Long, cpu: Long?, sdk: Int = 34) = DeviceTiers.classify(sdk, ram, false, cpu, powerSave = false)
        assertEquals(DeviceTier.FULL, tier(DeviceTiers.MIN_RAM_BYTES, DeviceTiers.MIN_CPU_KHZ))
        assertEquals(DeviceTier.LOW, tier(DeviceTiers.MIN_RAM_BYTES - 1, DeviceTiers.MIN_CPU_KHZ))
        assertEquals(DeviceTier.LOW, tier(DeviceTiers.MIN_RAM_BYTES, DeviceTiers.MIN_CPU_KHZ - 1))
        assertEquals(DeviceTier.FULL, tier(DeviceTiers.MIN_RAM_BYTES, DeviceTiers.MIN_CPU_KHZ, sdk = DeviceTiers.MIN_SDK))
        assertEquals(DeviceTier.LOW, tier(DeviceTiers.MIN_RAM_BYTES, DeviceTiers.MIN_CPU_KHZ, sdk = DeviceTiers.MIN_SDK - 1))
        // A clock of 0 is no reading, not a slow core.
        assertEquals(DeviceTier.FULL, tier(gib(8.0), 0))
    }

    @Test
    fun `hardware reasons outrank the battery saver`() {
        val a13 = DeviceTiers.assess(34, gib(3.6), false, 2_002_000, powerSave = true)
        assertEquals(TierReason.LITTLE_MEMORY, a13.reason)
        val specs = DeviceSpecs(36, gib(11.2), false, 4_400_000)
        assertEquals(TierAssessment(DeviceTier.FULL, TierReason.CAPABLE), DeviceTiers.assess(specs, powerSave = false))
        assertEquals(TierAssessment(DeviceTier.LOW, TierReason.POWER_SAVER), DeviceTiers.assess(specs, powerSave = true))
    }

    @Test
    fun `automatic follows the tier, the other modes are the user's choice`() {
        assertEquals(Effects.FULL, Effects.resolve(EffectsMode.AUTO, DeviceTier.FULL))
        assertEquals(Effects.LIGHT, Effects.resolve(EffectsMode.AUTO, DeviceTier.LOW))
        DeviceTier.entries.forEach { tier ->
            assertEquals(Effects.FULL, Effects.resolve(EffectsMode.FULL, tier))
            assertEquals(Effects.LIGHT, Effects.resolve(EffectsMode.LIGHT, tier))
        }
    }

    @Test
    fun `what each effects level draws`() {
        // Real blur: full effects on Android 12+ only.
        assertTrue(Effects.FULL.blursGlass(31))
        assertTrue(Effects.FULL.blursGlass(36))
        assertFalse(Effects.FULL.blursGlass(30))
        assertFalse(Effects.LIGHT.blursGlass(36))
        // The gentle sky: light effects, and Android 7 always (as before).
        assertFalse(Effects.FULL.gentleSky(34))
        assertTrue(Effects.FULL.gentleSky(25))
        assertTrue(Effects.LIGHT.gentleSky(36))
        assertTrue(Effects.FULL.decorativeMotion)
        assertFalse(Effects.LIGHT.decorativeMotion)
    }

    @Test
    fun `budget phones keep fewer decoded pictures in memory`() {
        assertEquals(0.20, memoryCachePercent(DeviceTier.FULL), 0.0)
        assertTrue(memoryCachePercent(DeviceTier.LOW) < memoryCachePercent(DeviceTier.FULL))
    }
}

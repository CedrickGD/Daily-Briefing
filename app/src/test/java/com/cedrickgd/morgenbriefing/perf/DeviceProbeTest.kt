package com.cedrickgd.morgenbriefing.perf

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DeviceProbeTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("cpu").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun core(name: String, khz: String) {
        File(root, "$name/cpufreq").apply { mkdirs() }.resolve("cpuinfo_max_freq").writeText(khz)
    }

    @Test
    fun `the fastest core counts`() {
        // A big.LITTLE phone: four efficiency cores, three performance cores, one prime core.
        (0..3).forEach { core("cpu$it", "2016000\n") }
        (4..6).forEach { core("cpu$it", "3200000\n") }
        core("cpu7", "4320000\n")
        // Not cores: ignored.
        File(root, "cpufreq").mkdirs()
        File(root, "cpuidle").mkdirs()
        assertEquals(4_320_000L, DeviceProbe.maxCpuKhz(root))
    }

    @Test
    fun `Galaxy A13 reads 2 GHz`() {
        (0..7).forEach { core("cpu$it", "2002000") }
        assertEquals(2_002_000L, DeviceProbe.maxCpuKhz(root))
    }

    @Test
    fun `policy directories are read too`() {
        File(root, "cpufreq/policy0").apply { mkdirs() }.resolve("cpuinfo_max_freq").writeText("1800000")
        File(root, "cpufreq/policy4").apply { mkdirs() }.resolve("cpuinfo_max_freq").writeText("2400000")
        assertEquals(2_400_000L, DeviceProbe.maxCpuKhz(root))
    }

    @Test
    fun `nothing readable is unknown, not slow`() {
        assertNull(DeviceProbe.maxCpuKhz(root))
        assertNull(DeviceProbe.maxCpuKhz(File(root, "missing")))
        core("cpu0", "garbage")
        core("cpu1", "0")
        core("cpu2", "-5")
        File(root, "cpu3").mkdirs() // a core without cpufreq (offline, emulator)
        assertNull(DeviceProbe.maxCpuKhz(root))
    }

    @Test
    fun `sysfs values are parsed leniently`() {
        assertEquals(2_002_000L, DeviceProbe.parseKhz(" 2002000\n"))
        assertNull(DeviceProbe.parseKhz(""))
        assertNull(DeviceProbe.parseKhz("2.0 GHz"))
    }
}

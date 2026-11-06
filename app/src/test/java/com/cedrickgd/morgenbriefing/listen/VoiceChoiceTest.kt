package com.cedrickgd.morgenbriefing.listen

import com.cedrickgd.morgenbriefing.data.model.VoicePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which voice reads: chosen > preferred gender > the engine's best offline voice (as before) > engine default. */
class VoiceChoiceTest {

    private fun voice(name: String, country: String, quality: Int = VoiceInfo.QUALITY_NORMAL, network: Boolean = false, notInstalled: Boolean = false) =
        VoiceInfo(name, "en", country, quality, VoiceInfo.LATENCY_NORMAL, network, notInstalled)

    /** A Samsung-like engine: a female and a male US voice, a female UK voice, a newer voice of unknown gender. */
    private val samsung = listOf(
        voice("en-US-SMTl03", "US", quality = VoiceInfo.QUALITY_VERY_HIGH),
        voice("en-GB-SMTf00", "GB", quality = VoiceInfo.QUALITY_HIGH),
        voice("en-US-SMTf00", "US", quality = VoiceInfo.QUALITY_HIGH),
        voice("en-US-SMTm00", "US", quality = VoiceInfo.QUALITY_HIGH),
        voice("en-US-SMTm01", "US", quality = VoiceInfo.QUALITY_VERY_HIGH, notInstalled = true),
    )

    @Test
    fun `nothing configured picks the best offline voice, exactly as before`() {
        val pick = VoiceChoice.resolve(samsung, "en", chosen = null, preference = VoicePreference.ANY, preferredCountry = "US")
        assertEquals("en-US-SMTl03", pick.voice?.name)
        assertEquals(VoiceSource.BEST_OFFLINE, pick.source)
        assertFalse(pick.chosenMissing)
        // Equal quality: the first in the engine's order (the old maxByOrNull), not the edition's country.
        val tie = listOf(voice("en-GB-a", "GB"), voice("en-US-b", "US"))
        assertEquals("en-GB-a", VoiceChoice.resolve(tie, "en", null, VoicePreference.ANY, "US").voice?.name)
    }

    @Test
    fun `a chosen voice wins over the preference`() {
        val pick = VoiceChoice.resolve(samsung, "en", chosen = "en-US-SMTm00", preference = VoicePreference.FEMALE, preferredCountry = "US")
        assertEquals("en-US-SMTm00", pick.voice?.name)
        assertEquals(VoiceSource.CHOSEN, pick.source)
        // Even an online voice, when that is what the user picked.
        val online = listOf(voice("en-us-x-iol-network", "US", network = true), voice("en-us-x-iol-local", "US"))
        assertEquals("en-us-x-iol-network", VoiceChoice.resolve(online, "en", "en-us-x-iol-network", VoicePreference.ANY).voice?.name)
    }

    @Test
    fun `the preference picks a voice of that gender, offline and of the edition's country first`() {
        val female = VoiceChoice.resolve(samsung, "en", chosen = null, preference = VoicePreference.FEMALE, preferredCountry = "US")
        assertEquals("en-US-SMTf00", female.voice?.name)
        assertEquals(VoiceSource.PREFERENCE, female.source)
        assertEquals("en-GB-SMTf00", VoiceChoice.resolve(samsung, "en", null, VoicePreference.FEMALE, "GB").voice?.name)
        // The male voice that is not installed is never picked.
        assertEquals("en-US-SMTm00", VoiceChoice.resolve(samsung, "en", null, VoicePreference.MALE, "US").voice?.name)
        val google = listOf(
            voice("en-us-x-iom-network", "US", quality = VoiceInfo.QUALITY_VERY_HIGH, network = true),
            voice("en-us-x-iom-local", "US"),
        )
        assertEquals("en-us-x-iom-local", VoiceChoice.resolve(google, "en", null, VoicePreference.MALE, "US").voice?.name)
    }

    @Test
    fun `a chosen voice that is gone falls back to the preference, then to the best offline voice`() {
        val toPreference = VoiceChoice.resolve(samsung, "en", chosen = "en-us-x-sfg-local", preference = VoicePreference.MALE, preferredCountry = "US")
        assertEquals("en-US-SMTm00", toPreference.voice?.name)
        assertEquals(VoiceSource.PREFERENCE, toPreference.source)
        assertTrue(toPreference.chosenMissing)

        // Not installed counts as gone.
        val notInstalled = VoiceChoice.resolve(samsung, "en", chosen = "en-US-SMTm01", preference = VoicePreference.ANY, preferredCountry = "US")
        assertEquals("en-US-SMTl03", notInstalled.voice?.name)
        assertEquals(VoiceSource.BEST_OFFLINE, notInstalled.source)
        assertTrue(notInstalled.chosenMissing)
    }

    @Test
    fun `a preference no voice can serve reads as without one`() {
        // Google voices outside the table reveal nothing: "Weiblich" changes nothing there.
        val unknown = listOf(voice("en-au-x-aua-local", "AU"), voice("en-au-x-aub-local", "AU", quality = VoiceInfo.QUALITY_HIGH))
        val pick = VoiceChoice.resolve(unknown, "en", null, VoicePreference.FEMALE, "AU")
        assertEquals("en-au-x-aub-local", pick.voice?.name)
        assertEquals(VoiceSource.BEST_OFFLINE, pick.source)
    }

    @Test
    fun `without an offline voice the engine's default reads, and online-only is flagged`() {
        val online = listOf(voice("en-us-x-iob-network", "US", network = true), voice("en-us-x-iog-local", "US", notInstalled = true))
        val pick = VoiceChoice.resolve(online, "en", null, VoicePreference.ANY, "US")
        assertNull(pick.voice)
        assertEquals(VoiceSource.ENGINE_DEFAULT, pick.source)
        assertTrue(VoiceChoice.onlineOnly(online, "en"))
        // No voices listed at all (engines that do not list them): trusted, not "online only".
        assertFalse(VoiceChoice.onlineOnly(emptyList(), "en"))
        assertFalse(VoiceChoice.onlineOnly(samsung, "en"))
        // Voices of other languages do not count.
        assertEquals(VoiceSource.ENGINE_DEFAULT, VoiceChoice.resolve(samsung, "de", null, VoicePreference.ANY, "DE").source)
    }
}

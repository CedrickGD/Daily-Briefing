package com.cedrickgd.morgenbriefing.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopPhaseTest {

    private fun ms(value: Long) = value * 1_000_000L

    @Test
    fun `the phase runs from 0 to 1 and starts over`() {
        assertEquals(0f, loopPhase(0, 900), 0f)
        assertEquals(0.5f, loopPhase(ms(450), 900), 1e-6f)
        assertEquals(0f, loopPhase(ms(900), 900), 0f)
        assertEquals(0.25f, loopPhase(ms(1_400 * 3 + 350), 1_400), 1e-6f)
    }

    @Test
    fun `hours of reading aloud never lose precision or leave the range`() {
        // 5 h = 18 000 000 ms ≡ 200 ms (mod 1 400); + 700 ms → 900 ms into the loop.
        val phase = loopPhase(ms(5L * 60 * 60 * 1000 + 700), 1_400)
        assertEquals(900f / 1_400f, phase, 1e-6f)
        listOf(0L, ms(99), ms(100), ms(123_456_789)).forEach {
            val p = loopPhase(it, 900)
            assertTrue("$p", p >= 0f && p < 1f)
        }
    }

    @Test
    fun `odd inputs stay calm`() {
        assertEquals(0f, loopPhase(-5, 900), 0f)
        assertEquals(0f, loopPhase(ms(100), 0), 0f)
    }

    @Test
    fun `light effects step at 10 fps`() {
        assertEquals(100L, LIGHT_STEP_MS)
    }
}

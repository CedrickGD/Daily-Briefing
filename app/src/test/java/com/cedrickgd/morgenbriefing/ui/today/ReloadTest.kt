package com.cedrickgd.morgenbriefing.ui.today

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Heute" reloads the news by itself once, on a cold start; after that only the user does. */
class ReloadTest {

    @Test
    fun `a cold start reloads the news`() {
        assertTrue(Reload.atStart(restored = false, cached = true).news)
        assertTrue(Reload.atStart(restored = false, cached = false).news)
    }

    @Test
    fun `a restored process keeps the feed it had`() {
        assertFalse(Reload.atStart(restored = true, cached = true).news)
        // Nothing on the phone to show: it loads after all.
        assertTrue(Reload.atStart(restored = true, cached = false).news)
        // The weather never moves the list: it may load either way.
        assertTrue(Reload.atStart(restored = true, cached = true).weather)
    }

    @Test
    fun `coming back never reloads the news`() {
        for (stale in listOf(true, false)) {
            for (tried in listOf(true, false)) {
                assertFalse(Reload.onResume(weatherStale = stale, weatherTriedLately = tried).news)
            }
        }
    }

    @Test
    fun `coming back refreshes old weather once`() {
        assertTrue(Reload.onResume(weatherStale = true, weatherTriedLately = false).weather)
        assertFalse(Reload.onResume(weatherStale = true, weatherTriedLately = true).weather)
        assertFalse(Reload.onResume(weatherStale = false, weatherTriedLately = false).weather)
    }
}

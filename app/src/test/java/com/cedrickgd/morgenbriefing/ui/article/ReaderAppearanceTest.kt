package com.cedrickgd.morgenbriefing.ui.article

import com.cedrickgd.morgenbriefing.data.model.ReaderFont
import com.cedrickgd.morgenbriefing.data.model.ReaderSettings
import com.cedrickgd.morgenbriefing.data.model.ReaderSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Darstellung": the sizes on offer and what they do to the reader's text. */
class ReaderAppearanceTest {

    private val eps = 0.001f

    @Test
    fun `the text sizes are 85 to 140 percent in steps of 5, as the reader's text size always was`() {
        assertEquals(12, ReaderSettings.SCALES.size)
        assertEquals(0.85f, ReaderSettings.SCALES.first(), eps)
        assertEquals(1.4f, ReaderSettings.SCALES.last(), eps)
        assertTrue(1f in ReaderSettings.SCALES)
        assertEquals(10, ReaderSettings.SLIDER_STEPS)
        assertEquals(115, ReaderSettings(textScale = 1.15f).percent)
    }

    @Test
    fun `a dragged or stored size snaps to the nearest step, nonsense reads as normal`() {
        assertEquals(1.1f, ReaderSettings.snapScale(1.12f), eps)
        assertEquals(0.85f, ReaderSettings.snapScale(0.2f), eps)
        assertEquals(1.4f, ReaderSettings.snapScale(9f), eps)
        assertEquals(1f, ReaderSettings.snapScale(Float.NaN), eps)
        assertEquals(1f, ReaderSettings.snapScale(Float.POSITIVE_INFINITY), eps)
    }

    @Test
    fun `the defaults are the reader as it was - serif 18 on 29, headline 32`() {
        val normal = ReaderSettings()
        assertEquals(ReaderMetrics.Size(18f, 29f), ReaderMetrics.body(normal))
        assertEquals(ReaderMetrics.Size(19f, 29f), ReaderMetrics.teaser(normal))
        assertEquals(ReaderMetrics.Size(32f, 40f), ReaderMetrics.headline(normal))
        assertEquals(ReaderMetrics.Size(24f, 30f), ReaderMetrics.subheading(normal))
    }

    @Test
    fun `size and spacing move the running text, headings grow more calmly`() {
        val large = ReaderSettings(textScale = 1.4f, spacing = ReaderSpacing.WIDE)
        val body = ReaderMetrics.body(large)
        assertEquals(18f * 1.4f, body.size, eps)
        assertEquals(29f * 1.4f * 1.15f, body.lineHeight, eps)
        // 140 % text, but a headline at 124 %: it would fill the screen otherwise.
        assertEquals(32f * 1.24f, ReaderMetrics.headline(large).size, eps)
        assertEquals(40f * 1.24f, ReaderMetrics.headline(large).lineHeight, eps)
        assertEquals(0.91f, ReaderMetrics.headingScale(0.85f), eps)
        // Compact spacing only tightens the lines, not the letters.
        val compact = ReaderMetrics.body(ReaderSettings(spacing = ReaderSpacing.COMPACT))
        assertEquals(18f, compact.size, eps)
        assertEquals(29f * 0.9f, compact.lineHeight, eps)
    }

    @Test
    fun `sans runs a step smaller than serif, the headline keeps its serif size`() {
        val sans = ReaderSettings(font = ReaderFont.SANS)
        assertEquals(17f, ReaderMetrics.body(sans).size, eps)
        assertEquals(18f, ReaderMetrics.teaser(sans).size, eps)
        assertEquals(ReaderMetrics.headline(ReaderSettings()), ReaderMetrics.headline(sans))
    }
}

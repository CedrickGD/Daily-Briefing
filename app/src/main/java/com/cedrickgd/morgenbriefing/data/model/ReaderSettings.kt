package com.cedrickgd.morgenbriefing.data.model

import kotlin.math.abs
import kotlin.math.roundToInt

/** The typeface of the reader's text ("Darstellung" → Schrift). Stored by enum name, never rename one. */
enum class ReaderFont { SERIF, SANS }

/**
 * The line spacing of the reader's running text ("Darstellung" → Zeilenabstand): a factor on the
 * text's own line height. Stored by enum name, never rename one.
 */
enum class ReaderSpacing(val factor: Float) {
    COMPACT(0.9f),
    NORMAL(1f),
    WIDE(1.15f),
}

/**
 * How articles look in the reader – the sheet "Darstellung" behind the "A" in the reader's bar
 * (docs/DESIGN.md §7). Every change shows at once on the open article and is stored with the
 * other settings. The defaults are the reader as it was before these settings existed: serif
 * text at its normal size and spacing.
 */
data class ReaderSettings(
    /** Text size, one of [SCALES] (0,85–1,4 in steps of 0,05); 1 = the reader's normal size. */
    val textScale: Float = DEFAULT_SCALE,
    val font: ReaderFont = ReaderFont.SERIF,
    val spacing: ReaderSpacing = ReaderSpacing.NORMAL,
) {
    /** The text size in percent, as the sheet shows it ("115 %"). */
    val percent: Int get() = (textScale * 100).roundToInt()

    companion object {
        const val DEFAULT_SCALE = 1f
        const val MIN_SCALE = 0.85f
        const val MAX_SCALE = 1.4f
        private const val SCALE_STEP = 0.05f

        /** The sizes on offer: the same range and steps as the reader's text size had in version 1. */
        val SCALES: List<Float> = (0..((MAX_SCALE - MIN_SCALE) / SCALE_STEP).roundToInt()).map { step ->
            ((MIN_SCALE + step * SCALE_STEP) * 100).roundToInt() / 100f
        }

        /** Stops of the slider between its two ends (a Material slider counts only the inner ones). */
        val SLIDER_STEPS: Int = SCALES.size - 2

        /** The offered size closest to [value] (stored or dragged); nonsense reads as the normal size. */
        fun snapScale(value: Float): Float =
            if (value.isNaN() || value.isInfinite()) DEFAULT_SCALE else SCALES.minBy { abs(it - value) }
    }
}

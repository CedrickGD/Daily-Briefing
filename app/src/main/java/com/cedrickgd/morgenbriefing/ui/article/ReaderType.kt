package com.cedrickgd.morgenbriefing.ui.article

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.ReaderFont
import com.cedrickgd.morgenbriefing.data.model.ReaderSettings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.ui.theme.hyphenated
import com.cedrickgd.morgenbriefing.ui.theme.readingLanguageTag

/** How the reader shows the open article: its "Darstellung" and whether its picture shows. */
@Immutable
data class ReaderAppearance(
    val reader: ReaderSettings = ReaderSettings(),
    /** "Bilder anzeigen" (Einstellungen → Feed & Lesen); off: the topic's cover instead of the picture. */
    val showImages: Boolean = true,
)

/**
 * The sizes of the reader's text (sp) at a [ReaderSettings] – pure numbers, unit-tested. The
 * running text (paragraphs, lists, quotes) and the teaser follow the text size, the font and the
 * line spacing. The headline and the subheadings grow more calmly with the size ([HEADING_SHARE]:
 * a headline at 140 % would fill the screen) and keep the serif of the app's headlines.
 */
internal object ReaderMetrics {
    /** A size and its line height, in sp. */
    data class Size(val size: Float, val lineHeight: Float)

    private val SERIF_BODY = Size(18f, 29f)

    /** Sans looks larger than serif at the same size: one step smaller, the same rhythm. */
    private val SANS_BODY = Size(17f, 27.5f)
    private val SERIF_TEASER = Size(19f, 29f)
    private val SANS_TEASER = Size(18f, 27.5f)
    private val HEADLINE = Size(32f, 40f)
    private val SUBHEADING = Size(24f, 30f)

    /** Headings grow by this share of what the text grows. */
    const val HEADING_SHARE = 0.6f

    fun body(reader: ReaderSettings): Size = running(if (reader.font == ReaderFont.SANS) SANS_BODY else SERIF_BODY, reader)

    fun teaser(reader: ReaderSettings): Size = running(if (reader.font == ReaderFont.SANS) SANS_TEASER else SERIF_TEASER, reader)

    fun headline(reader: ReaderSettings): Size = heading(HEADLINE, reader)

    fun subheading(reader: ReaderSettings): Size = heading(SUBHEADING, reader)

    /** The factor headings grow by at [scale]: 1,4 → 1,24; 0,85 → 0,91. */
    fun headingScale(scale: Float): Float = 1f + (scale - 1f) * HEADING_SHARE

    private fun running(base: Size, reader: ReaderSettings): Size {
        val scale = ReaderSettings.snapScale(reader.textScale)
        return Size(base.size * scale, base.lineHeight * scale * reader.spacing.factor)
    }

    private fun heading(base: Size, reader: ReaderSettings): Size {
        val scale = headingScale(ReaderSettings.snapScale(reader.textScale))
        return Size(base.size * scale, base.lineHeight * scale)
    }
}

/** The reader's text styles for one "Darstellung" (colours are set where the text is drawn). */
@Immutable
internal class ReaderType(
    val headline: TextStyle,
    val teaser: TextStyle,
    val body: TextStyle,
    val subheading: TextStyle,
)

/**
 * The reader's text styles for [reader], hyphenated in the article's [language] (ISO 639-1; null =
 * the interface language): the running text in the chosen font, the headings in serif.
 */
@Composable
internal fun rememberReaderType(reader: ReaderSettings, language: String?): ReaderType {
    val typography = MaterialTheme.typography
    val tag = readingLanguageTag(language, LocalAppLanguage.current)
    return remember(reader, typography, tag) {
        val family = if (reader.font == ReaderFont.SANS) FontFamily.Default else FontFamily.Serif
        val body = ReaderMetrics.body(reader)
        val teaser = ReaderMetrics.teaser(reader)
        val headline = ReaderMetrics.headline(reader)
        val subheading = ReaderMetrics.subheading(reader)
        ReaderType(
            headline = typography.headlineLarge.copy(fontSize = headline.size.sp, lineHeight = headline.lineHeight.sp).hyphenated(tag),
            teaser = typography.bodyLarge.copy(
                fontFamily = family,
                fontWeight = FontWeight.Medium,
                fontSize = teaser.size.sp,
                lineHeight = teaser.lineHeight.sp,
            ).hyphenated(tag),
            body = typography.bodyLarge.copy(fontFamily = family, fontSize = body.size.sp, lineHeight = body.lineHeight.sp).hyphenated(tag),
            subheading = typography.headlineSmall.copy(fontSize = subheading.size.sp, lineHeight = subheading.lineHeight.sp).hyphenated(tag),
        )
    }
}

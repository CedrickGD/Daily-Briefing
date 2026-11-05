package com.cedrickgd.morgenbriefing.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage

/**
 * Editorial text styles shared by the news feed and the article reader (docs/DESIGN.md §5/§7,
 * the user's reference: big serif headline, serif lead). [scale] is the reader text size
 * (settings.readerTextScale, 0.85–1.4); the feed uses 1. All of them hyphenate long words in
 * the text's own [language] (ISO 639-1 of the article, see
 * [com.cedrickgd.morgenbriefing.domain.ItemEdition.languageOf]; null = the interface language).
 */
object Reading {
    @Composable
    @ReadOnlyComposable
    fun feedHeadline(scale: Float = 1f, language: String? = null): TextStyle =
        serif(26.sp * scale, 32.sp * scale, FontWeight.SemiBold, language)

    @Composable
    @ReadOnlyComposable
    fun feedLead(scale: Float = 1f, language: String? = null): TextStyle =
        serif(18.sp * scale, 27.sp * scale, FontWeight.Normal, language)
            .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    @Composable
    @ReadOnlyComposable
    fun articleHeadline(scale: Float = 1f, language: String? = null): TextStyle =
        serif(32.sp * scale, 38.sp * scale, FontWeight.SemiBold, language)

    @Composable
    @ReadOnlyComposable
    fun articleLead(scale: Float = 1f, language: String? = null): TextStyle =
        serif(20.sp * scale, 30.sp * scale, FontWeight.Medium, language)

    @Composable
    @ReadOnlyComposable
    fun articleBody(scale: Float = 1f, language: String? = null): TextStyle =
        serif(18.sp * scale, 29.sp * scale, FontWeight.Normal, language)

    @Composable
    @ReadOnlyComposable
    fun articleSubheading(scale: Float = 1f, language: String? = null): TextStyle =
        serif(22.sp * scale, 28.sp * scale, FontWeight.SemiBold, language)

    /** Meta lines ("vor 3 Std. · Handelsblatt"): sans, quiet. */
    @Composable
    @ReadOnlyComposable
    fun meta(): TextStyle = MaterialTheme.typography.labelLarge.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.1.sp,
    )

    @Composable
    @ReadOnlyComposable
    private fun serif(size: TextUnit, lineHeight: TextUnit, weight: FontWeight, language: String?): TextStyle =
        MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Serif,
            fontSize = size,
            lineHeight = lineHeight,
            fontWeight = weight,
            letterSpacing = (-0.005).em,
            color = MaterialTheme.colorScheme.onSurface,
        ).hyphenated(readingLanguageTag(language, LocalAppLanguage.current))
}

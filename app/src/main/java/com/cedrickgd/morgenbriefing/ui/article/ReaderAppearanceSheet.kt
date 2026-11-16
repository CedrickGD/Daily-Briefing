package com.cedrickgd.morgenbriefing.ui.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.ReaderFont
import com.cedrickgd.morgenbriefing.data.model.ReaderSettings
import com.cedrickgd.morgenbriefing.data.model.ReaderSpacing
import com.cedrickgd.morgenbriefing.i18n.readerStrings
import com.cedrickgd.morgenbriefing.ui.components.GlassPillButton
import com.cedrickgd.morgenbriefing.ui.components.GlassSheet
import com.cedrickgd.morgenbriefing.ui.settings.CalmSliderThumb
import com.cedrickgd.morgenbriefing.ui.settings.CalmSliderTrack
import com.cedrickgd.morgenbriefing.ui.settings.ChoicePill
import com.cedrickgd.morgenbriefing.ui.settings.IconTile
import com.cedrickgd.morgenbriefing.ui.settings.Overline
import com.cedrickgd.morgenbriefing.ui.settings.ToggleRow

/** The page behind "Darstellung" stays readable: every change shows on it at once. */
private val LightScrim = Color.Black.copy(alpha = 0.12f)

/**
 * "Darstellung" – the sheet behind the "A" in the reader's bar (docs/DESIGN.md §7): thick glass
 * whose every change shows at once on the open article above it (the scrim stays light).
 *
 * - **Textgröße**: a calm slider between a small and a large "A", 85–140 % in steps of 5 %, the
 *   value in percent above it. While it moves the article follows ([onPreview]); it is stored
 *   when the finger lifts ([onChange]).
 * - **Schrift**: with or without serifs, **Zeilenabstand**: compact, normal or relaxed.
 * - **Bilder anzeigen**: the app-wide picture setting – it hides the feed's pictures too, and
 *   says so.
 * - "Zurücksetzen" while anything differs from the reader's normal look.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReaderAppearanceSheet(
    appearance: ReaderAppearance,
    onPreview: (ReaderSettings) -> Unit,
    onChange: (ReaderSettings) -> Unit,
    onShowImages: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = readerStrings()
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val reader = appearance.reader
    // Local while dragging; follows the shown value whenever it changes.
    var scale by remember(reader.textScale) { mutableFloatStateOf(reader.textScale) }
    val percent = strings.percent(reader.copy(textScale = scale).percent)

    GlassSheet(onDismissRequest = onDismiss, scrimColor = LightScrim) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 4.dp)) {
            Text(
                text = strings.appearance,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Overline(strings.textSize, modifier = Modifier.weight(1f))
                Text(text = percent, style = MaterialTheme.typography.titleMedium, color = colors.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SizeMark(size = 15)
                Slider(
                    value = scale,
                    onValueChange = { raw ->
                        val next = ReaderSettings.snapScale(raw)
                        if (next != scale) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            scale = next
                            onPreview(reader.copy(textScale = next))
                        }
                    },
                    onValueChangeFinished = { onChange(reader.copy(textScale = scale)) },
                    valueRange = ReaderSettings.MIN_SCALE..ReaderSettings.MAX_SCALE,
                    steps = ReaderSettings.SLIDER_STEPS,
                    thumb = { CalmSliderThumb() },
                    track = { state -> CalmSliderTrack(state) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                        .semantics {
                            contentDescription = strings.textSize
                            stateDescription = percent
                        },
                )
                SizeMark(size = 26)
            }

            Spacer(Modifier.height(14.dp))
            Overline(strings.font)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                ReaderFont.entries.forEach { font ->
                    ChoicePill(label = strings.fontName(font), selected = font == reader.font, onClick = { onChange(reader.copy(font = font)) })
                }
            }

            Spacer(Modifier.height(16.dp))
            Overline(strings.spacing)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                ReaderSpacing.entries.forEach { spacing ->
                    ChoicePill(
                        label = strings.spacingName(spacing),
                        selected = spacing == reader.spacing,
                        onClick = { onChange(reader.copy(spacing = spacing)) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            ToggleRow(
                title = strings.showImages,
                subtitle = strings.showImagesHint,
                checked = appearance.showImages,
                onCheckedChange = onShowImages,
                leading = { IconTile(Icons.Rounded.Image) },
            )

            if (reader != ReaderSettings()) {
                Spacer(Modifier.height(6.dp))
                GlassPillButton(
                    text = strings.resetAppearance,
                    icon = Icons.Rounded.RestartAlt,
                    onClick = { onChange(ReaderSettings()) },
                    modifier = Modifier.widthIn(min = 48.dp),
                )
            }
        }
    }
}

/** The small and the large "A" at the ends of the text-size slider (decorative: the slider says it). */
@Composable
private fun SizeMark(size: Int) {
    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "A",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif, fontSize = size.sp, lineHeight = (size + 4).sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

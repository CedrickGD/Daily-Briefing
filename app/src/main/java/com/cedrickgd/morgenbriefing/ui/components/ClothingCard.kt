package com.cedrickgd.morgenbriefing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.domain.ClothingAdvice
import com.cedrickgd.morgenbriefing.domain.ClothingItem
import com.cedrickgd.morgenbriefing.i18n.ComponentStrings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.ui.glass.GlassShapes
import com.cedrickgd.morgenbriefing.ui.theme.OverlineLetterSpacing

/**
 * "ANZIEHEN": pictogram tiles (🧥 Jacke, ☂️ Schirm …) and the friendly advice text below – the
 * clothing part of the expanded weather card on "Heute" (the card is the caller's).
 */
@Composable
fun ClothingSection(advice: ClothingAdvice, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val language = LocalAppLanguage.current
    Column(modifier.fillMaxWidth()) {
        Text(
            text = ComponentStrings.of(language).clothingTitle.uppercase(language.locale),
            style = typography.labelMedium.copy(letterSpacing = OverlineLetterSpacing),
            color = colors.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        if (advice.items.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (item in advice.items) ClothingTile(item)
            }
        }
        if (advice.text.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text = advice.text, style = typography.bodyMedium, color = colors.onSurface)
        }
    }
}

@Composable
private fun ClothingTile(item: ClothingItem) {
    val colors = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        Column(
            modifier = Modifier
                .semantics(mergeDescendants = true) { }
                .background(colors.primary.copy(alpha = 0.10f), GlassShapes.chip)
                .defaultMinSize(minWidth = 74.dp)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Emoji(item.emoji, size = 26.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

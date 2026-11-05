package com.cedrickgd.morgenbriefing.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.ui.components.GlassIconButton
import com.cedrickgd.morgenbriefing.ui.components.entrance
import com.cedrickgd.morgenbriefing.ui.components.headerOverlineColor
import com.cedrickgd.morgenbriefing.ui.components.rememberEntrance
import com.cedrickgd.morgenbriefing.ui.theme.OverlineLetterSpacing
import java.time.LocalDate

/**
 * The head of "Heute" (docs/DESIGN.md §4/§7), directly on the sky: the date overline and the
 * greeting on the left, round glass buttons for the search and the discover screen on the
 * right, then the serif headline across the full width. Fades and slides in once.
 */
@Composable
internal fun TodayTop(
    date: LocalDate,
    greeting: String,
    headline: String,
    onSearch: () -> Unit,
    onDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val strings = feedStrings()
    val language = LocalAppLanguage.current
    val entrance = rememberEntrance(durationMillis = 640)

    Column(modifier.entrance(entrance, distance = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = Fmt.dateLine(date, language),
                    style = typography.labelMedium.copy(letterSpacing = OverlineLetterSpacing),
                    color = headerOverlineColor(colors.onSurfaceVariant, colors.onSurface),
                )
                if (greeting.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(text = greeting, style = typography.titleMedium, color = colors.onSurface)
                }
            }
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassIconButton(icon = Icons.Rounded.Search, contentDescription = strings.searchDescription, onClick = onSearch)
                GlassIconButton(icon = Icons.Rounded.Explore, contentDescription = strings.discoverDescription, onClick = onDiscover)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = headline,
            style = typography.headlineMedium,
            color = colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** The search and discover buttons inside the frosted top bar (already glass: plain icon buttons). */
@Composable
internal fun TodayBarActions(onSearch: () -> Unit, onDiscover: () -> Unit) {
    val strings = feedStrings()
    val tint = MaterialTheme.colorScheme.onSurface
    IconButton(onClick = onSearch) {
        Icon(Icons.Rounded.Search, contentDescription = strings.searchDescription, tint = tint)
    }
    IconButton(onClick = onDiscover) {
        Icon(Icons.Rounded.Explore, contentDescription = strings.discoverDescription, tint = tint)
    }
}

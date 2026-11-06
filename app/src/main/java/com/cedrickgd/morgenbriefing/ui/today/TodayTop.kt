package com.cedrickgd.morgenbriefing.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.ui.components.entrance
import com.cedrickgd.morgenbriefing.ui.components.headerOverlineColor
import com.cedrickgd.morgenbriefing.ui.components.rememberEntrance
import com.cedrickgd.morgenbriefing.ui.theme.OverlineLetterSpacing
import java.time.LocalDate

/**
 * The head of "Heute" (docs/DESIGN.md §4/§7), directly on the sky: the date overline and the
 * greeting in serif. Search and topics live in the feed bar below the weather. Fades and slides
 * in once.
 */
@Composable
internal fun TodayTop(
    date: LocalDate,
    greeting: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val language = LocalAppLanguage.current
    val entrance = rememberEntrance(durationMillis = 640)

    Column(modifier.entrance(entrance, distance = 20.dp)) {
        Text(
            text = Fmt.dateLine(date, language),
            style = typography.labelMedium.copy(letterSpacing = OverlineLetterSpacing),
            color = headerOverlineColor(colors.onSurfaceVariant, colors.onSurface),
        )
        if (greeting.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = greeting,
                style = typography.headlineSmall,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

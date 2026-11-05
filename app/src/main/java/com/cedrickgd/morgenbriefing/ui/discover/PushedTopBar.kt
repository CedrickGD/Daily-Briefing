package com.cedrickgd.morgenbriefing.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.ui.components.GlassIconButton
import com.cedrickgd.morgenbriefing.ui.glass.GlassLevel
import com.cedrickgd.morgenbriefing.ui.glass.blockTouchesBehind
import com.cedrickgd.morgenbriefing.ui.glass.glass
import com.cedrickgd.morgenbriefing.util.horizontalSafeDrawing
import com.cedrickgd.morgenbriefing.util.statusBarTop

/** Height of the bar's row under the status bar. */
internal val PushedBarHeight = 64.dp

/**
 * The top of a pushed glass screen: round glass buttons (back, [actions]) that always float
 * at the top, and – once the page title has scrolled away ([solid]) – a thick frosted bar
 * behind them with the [title] in small serif, so the buttons and the title stay legible over
 * whatever scrolls beneath. The bar takes every touch inside it.
 */
@Composable
internal fun BoxScope.PushedTopBar(
    title: String,
    solid: Boolean,
    backLabel: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val statusTop = statusBarTop()
    AnimatedVisibility(
        visible = solid,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(spring(stiffness = Spring.StiffnessMedium)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusTop + PushedBarHeight)
                .glass(shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp), level = GlassLevel.Thick, edge = true, floating = true)
                .blockTouchesBehind(),
        )
    }
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .horizontalSafeDrawing()
            .padding(top = statusTop + 8.dp, start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backLabel, onClick = onBack)
        Box(Modifier.weight(1f)) {
            this@Row.AnimatedVisibility(visible = solid, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = MaterialTheme.typography.displaySmall.fontFamily),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .semantics { heading() },
                )
            }
        }
        actions()
    }
}

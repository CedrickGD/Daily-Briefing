package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cedrickgd.morgenbriefing.BuildConfig
import com.cedrickgd.morgenbriefing.i18n.commonStrings
import com.cedrickgd.morgenbriefing.i18n.settingsStrings
import com.cedrickgd.morgenbriefing.ui.glass.GlassCard
import com.cedrickgd.morgenbriefing.util.Links

/** Settings section "App": name, version and the in-app updater below it. */
@Composable
internal fun AppHeaderCard(installedVersion: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val strings = settingsStrings()
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFFFD27A), Color(0xFFF08A3C), colors.primary))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(commonStrings().appName, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
                Text(
                    text = strings.version(installedVersion.removeSuffix("-debug")) + if (BuildConfig.DEBUG) " · ${strings.testBuild}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Text(
            text = strings.aboutApp,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

private data class AppLink(val icon: ImageVector, val title: String, val subtitle: String, val url: String)

/** Credits and source code as tappable rows. */
@Composable
internal fun LinksCard(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val strings = settingsStrings()
    val links = listOf(
        AppLink(Icons.Rounded.Cloud, strings.linkWeather, strings.linkWeatherText, Links.OPEN_METEO),
        AppLink(Icons.Rounded.RssFeed, strings.linkNews, strings.linkNewsText, Links.SOURCE_CODE + "#quellen"),
        AppLink(Icons.Rounded.Code, strings.linkCode, Links.SOURCE_CODE.removePrefix("https://"), Links.SOURCE_CODE),
    )
    GlassCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        links.forEachIndexed { index, link ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp, end = 20.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClickLabel = strings.openWebsite) { onOpen(link.url) }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(link.icon, size = 36.dp, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(link.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(link.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The quiet sign-off at the end of the page. */
@Composable
internal fun SettingsFooter(modifier: Modifier = Modifier) {
    Text(
        text = settingsStrings().footer,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 8.dp),
    )
}

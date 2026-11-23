package com.cedrickgd.morgenbriefing.ui.today

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.DayWeather
import com.cedrickgd.morgenbriefing.data.model.WeatherCard
import com.cedrickgd.morgenbriefing.domain.Condition
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.domain.condition
import com.cedrickgd.morgenbriefing.domain.precipKind
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.FeedStrings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.feedStrings
import com.cedrickgd.morgenbriefing.ui.brief.WeatherUi
import com.cedrickgd.morgenbriefing.ui.components.ClothingSection
import com.cedrickgd.morgenbriefing.ui.components.CountUpTemperature
import com.cedrickgd.morgenbriefing.ui.components.DayCurveSection
import com.cedrickgd.morgenbriefing.ui.components.FloatingEmoji
import com.cedrickgd.morgenbriefing.ui.components.WeatherNowDetails
import com.cedrickgd.morgenbriefing.ui.components.entrance
import com.cedrickgd.morgenbriefing.ui.components.heroDescription
import com.cedrickgd.morgenbriefing.ui.components.motionDisabled
import com.cedrickgd.morgenbriefing.ui.components.rememberEntrance
import com.cedrickgd.morgenbriefing.ui.glass.GlassLevel
import com.cedrickgd.morgenbriefing.ui.glass.glass

/** From this rain probability on (in the day window) the summary names the rain instead of "trocken". */
internal const val RAIN_NOTE_FROM = 30

/** "Berlin · sonnig": the home place and the condition there now. Pure. */
internal fun weatherPlaceLine(weather: WeatherUi, lang: AppLanguage): String {
    val home = weather.snapshot.home
    val condition = Condition.fromCode(home.current.code)
    return listOf(home.place.name.trim(), condition.labelFor(home.current.isDay, lang))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

/**
 * The one line under the place on the collapsed weather card: "5° – 10° · trocken · Jacke" – the
 * day window's range, its rain ("Regen 60 %", "Schnee 40 %" or "trocken") and the first piece of
 * the clothing advice, which only shows while "Anziehen" is switched on ([cards]). Pure.
 */
internal fun weatherSummaryLine(weather: WeatherUi, cards: Set<WeatherCard>, lang: AppLanguage): String {
    val day = weather.snapshot.day
    return buildList {
        if (day.hours.isNotEmpty()) add("${Fmt.temp(day.minTemp)} – ${Fmt.temp(day.maxTemp)}")
        add(rainNote(day, lang))
        if (WeatherCard.CLOTHING in cards) {
            weather.clothing?.items?.firstOrNull()?.label?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        }
    }.joinToString(" · ")
}

/** "Regen 60 %" (or snow, sleet) from [RAIN_NOTE_FROM] % in the day window, else "trocken". */
internal fun rainNote(day: DayWeather, lang: AppLanguage): String {
    val pop = day.maxPop.coerceIn(0, 100)
    if (pop < RAIN_NOTE_FROM) return FeedStrings.of(lang).dry
    val kind = precipKind(day.hours.map { it.condition })
    return "${kind.word(lang)} ${Fmt.percent(pop, lang)}"
}

/**
 * The weather of "Heute" as one compact glass card (docs/DESIGN.md §7). Collapsed: the
 * condition, the big serif temperature, "Ort · Zustand" and one line "min – max · Regen/trocken ·
 * Kleidungstipp", and a chevron. A tap expands it smoothly to what the separate cards showed: the
 * day's headline, "Vor der Tür" (felt temperature, chips, the note for leaving the house), the day
 * curve with its summary and the clothing pieces – each as the weather blocks in the settings
 * allow ([cards]). Warnings are not part of it: they stay visible below the card.
 *
 * @param expanded remembered across launches by the caller.
 */
@Composable
internal fun WeatherSummaryCard(
    weather: WeatherUi,
    cards: Set<WeatherCard>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = weather.snapshot
    val current = snapshot.home.current
    val condition = Condition.fromCode(current.code)
    val language = LocalAppLanguage.current
    val strings = feedStrings()
    val colors = MaterialTheme.colorScheme
    val still = motionDisabled()
    val place = remember(weather, language) { weatherPlaceLine(weather, language) }
    val summary = remember(weather, cards, language) { weatherSummaryLine(weather, cards, language) }
    val description = remember(weather, summary, language) {
        heroDescription(snapshot.home, condition, language) + ". " + summary
    }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "weatherChevron",
    )

    Column(
        modifier
            .fillMaxWidth()
            .glass(shape = WeatherCardShape, level = GlassLevel.Regular)
            .then(if (still) Modifier else Modifier.animateContentSize(spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(
                    indication = ripple(),
                    interactionSource = null,
                    role = Role.Button,
                    onClickLabel = if (expanded) strings.weatherCollapse else strings.weatherExpand,
                    onClick = { onExpandedChange(!expanded) },
                )
                .clearAndSetSemantics {
                    contentDescription = description
                    stateDescription = if (expanded) strings.weatherExpandedState else strings.weatherCollapsedState
                }
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingEmoji(condition.emoji(current.isDay), size = 32.sp)
            Spacer(Modifier.width(12.dp))
            CountUpTemperature(
                target = current.temp,
                style = TextStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                ),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = place,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        // One line; large font sizes may take a second one instead of losing the tip.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = chevron },
            )
        }
        if (expanded) {
            ExpandedWeather(weather, cards, Modifier.padding(start = ExpandedPadding, end = ExpandedPadding, bottom = ExpandedPadding))
        }
    }
}

/** The expanded card's inner padding (the day chart reaches into it). */
private val ExpandedPadding = 18.dp

/**
 * How far the day curve reaches into [ExpandedPadding]. With the chart's own 6 dp inset the first
 * and last hour sit 12 dp from the card's edge: half-way to the text edge, wide without touching it.
 */
private val CurveBleed = 12.dp

/** What the card adds when expanded; fades in each time it opens. */
@Composable
private fun ExpandedWeather(weather: WeatherUi, cards: Set<WeatherCard>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val entrance = rememberEntrance(durationMillis = 360)
    Column(modifier.fillMaxWidth().entrance(entrance, distance = 8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.onSurface.copy(alpha = 0.10f)),
        )
        Spacer(Modifier.height(14.dp))
        if (weather.headline.isNotBlank()) {
            Text(
                text = weather.headline,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
            )
            Spacer(Modifier.height(14.dp))
        }
        WeatherNowDetails(weather.snapshot)
        if (WeatherCard.CURVE in cards) {
            Spacer(Modifier.height(20.dp))
            // The curve reaches past the text edge towards the card's edge; title and summary stay on the text edge.
            DayCurveSection(weather.snapshot, chartBleed = CurveBleed)
        }
        if (WeatherCard.CLOTHING in cards) {
            weather.clothing?.let { advice ->
                Spacer(Modifier.height(20.dp))
                ClothingSection(advice)
            }
        }
    }
}

private val WeatherCardShape = RoundedCornerShape(26.dp)

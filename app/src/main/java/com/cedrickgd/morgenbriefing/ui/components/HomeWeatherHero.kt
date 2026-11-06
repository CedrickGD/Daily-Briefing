package com.cedrickgd.morgenbriefing.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cedrickgd.morgenbriefing.data.model.CurrentConditions
import com.cedrickgd.morgenbriefing.data.model.HomeWeather
import com.cedrickgd.morgenbriefing.data.model.WeatherSnapshot
import com.cedrickgd.morgenbriefing.domain.Condition
import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.domain.WeatherInsights
import com.cedrickgd.morgenbriefing.domain.precipKind
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ComponentStrings
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import com.cedrickgd.morgenbriefing.i18n.WeatherStrings
import com.cedrickgd.morgenbriefing.ui.glass.decorativeMotion
import com.cedrickgd.morgenbriefing.ui.theme.OverlineLetterSpacing
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/** Wind gusts from this speed on are worth mentioning. */
private const val GUST_NOTE_KMH = 40.0

/** Below this visibility the "Sicht" chip appears. */
private const val LOW_VISIBILITY_M = 5_000.0

/**
 * What "Vor der Tür" adds beyond the compact weather card (docs/DESIGN.md §7): "Vor der Tür ·
 * Bad Düben · 07:12", the felt temperature with the condition, the chips (rain, wind, sun,
 * visibility) and the note for leaving the house. Plain content: the card around it is the
 * caller's.
 */
@Composable
fun WeatherNowDetails(snapshot: WeatherSnapshot, modifier: Modifier = Modifier) {
    val home = snapshot.home
    val current = home.current
    val condition = Condition.fromCode(current.code)
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val language = LocalAppLanguage.current
    val strings = ComponentStrings.of(language)

    Column(modifier.fillMaxWidth()) {
        // At large font sizes only the place shortens ("…"); the time of the reading stays.
        val overline = typography.labelMedium.copy(letterSpacing = OverlineLetterSpacing)
        val time = Fmt.clock(home.current.time)
        Row {
            Text(
                text = strings.outsideAt(home.place.name).uppercase(language.locale),
                style = overline,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (time.isNotBlank()) {
                Text(text = " · $time", style = overline, color = colors.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${WeatherStrings.of(language).feelsLike(Fmt.temp(current.feels))} · ${condition.labelFor(current.isDay, language)}",
            style = typography.bodyMedium,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (chip in heroChips(home, language)) {
                Pill(
                    text = chip.text,
                    emoji = chip.emoji,
                    container = colors.onSurface.copy(alpha = 0.07f),
                )
            }
        }

        val note = WeatherInsights.homeNote(snapshot, language)
        if (note.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(text = note, style = typography.bodyMedium, color = colors.onSurface)
        }
    }
}

/** Temperature that counts up from 0° on first appearance and glides to new values on refresh. */
@Composable
internal fun CountUpTemperature(target: Double, style: TextStyle, modifier: Modifier = Modifier) {
    val skip = motionDisabled()
    var played by rememberSaveable { mutableStateOf(false) }
    val value = remember { Animatable(if (played || skip) target.toFloat() else 0f) }
    LaunchedEffect(target, skip) {
        if (skip) {
            value.snapTo(target.toFloat())
        } else {
            value.animateTo(
                target.toFloat(),
                tween(durationMillis = if (played) 600 else 1_100, easing = FastOutSlowInEasing),
            )
        }
        played = true
    }
    Text(
        text = Fmt.temp(value.value.toDouble()),
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The condition emoji, bobbing gently (static with reduced motion and with light effects). */
@Composable
internal fun FloatingEmoji(emoji: String, modifier: Modifier = Modifier, size: TextUnit = 56.sp) {
    val skip = motionDisabled() || !decorativeMotion()
    val bob = if (skip) {
        null
    } else {
        rememberInfiniteTransition(label = "emoji").animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Reverse),
            label = "emojiBob",
        )
    }
    Emoji(
        emoji = emoji,
        size = size,
        modifier = modifier
            .graphicsLayer {
                val b = bob?.value ?: 0f
                // Smooth ease in/out of the linear ping-pong value.
                val eased = b * (1.5f - 0.5f * b * b)
                translationY = eased * 3.dp.toPx()
                rotationZ = eased * 2f
            },
    )
}

internal data class HeroChip(val emoji: String, val text: String)

/** A number never wraps away from its unit inside a chip ("80 %", "12 km/h"). */
private fun String.keepUnits(): String = replace(" %", " %").replace(" km", " km")

private fun heroChips(home: HomeWeather, lang: AppLanguage): List<HeroChip> = buildList {
    val strings = ComponentStrings.of(lang)
    val current = home.current
    val maxPop = home.nextHours.maxOfOrNull { it.pop } ?: 0
    // "Schnee 80 %" on a snowy morning, "Schneeregen" when both fall: the word follows the weather codes.
    val kind = precipKind(home)
    add(HeroChip(kind.emoji, "${kind.word(lang)} ${Fmt.percent(maxPop.coerceIn(0, 100), lang)}".keepUnits()))

    val wind = strings.wind(current.windKmh.roundToInt()).keepUnits()
    add(
        HeroChip(
            "💨",
            if (current.gustKmh >= GUST_NOTE_KMH) "$wind · ${strings.gusts(current.gustKmh.roundToInt())}" else wind,
        ),
    )

    sunChip(home, strings)?.let(::add)

    val visibility = current.visibilityM
    if (visibility != null && visibility < LOW_VISIBILITY_M) {
        add(HeroChip("👁️", strings.visibility(Fmt.decimal(visibility / 1000.0, 1, lang)).keepUnits()))
    }
}

/**
 * 🌅 sunrise while it is still dark before sunrise, 🌇 sunset during the day — and after sunset
 * tomorrow's 🌅 sunrise, never a sunset that is hours past.
 */
internal fun sunChip(home: HomeWeather, strings: ComponentStrings): HeroChip? {
    val now = localTimeOf(home.current.time)
    val sunrise = parseClock(home.sunrise)
    val sunset = parseClock(home.sunset)
    return when {
        now != null && sunrise != null && now.isBefore(sunrise) ->
            HeroChip("🌅", strings.sunrise(home.sunrise.trim()))
        now != null && sunset != null && !now.isBefore(sunset) && home.sunriseTomorrow.isNotBlank() ->
            HeroChip("🌅", strings.sunrise(home.sunriseTomorrow.trim()))
        home.sunset.isNotBlank() -> HeroChip("🌇", strings.sunset(home.sunset.trim()))
        else -> null
    }
}

/** "Vor der Tür in Berlin: 10°, gefühlt 8°, sonnig" for TalkBack. */
internal fun heroDescription(home: HomeWeather, condition: Condition, lang: AppLanguage): String {
    val current: CurrentConditions = home.current
    return ComponentStrings.of(lang).outsideDescription(
        place = home.place.name,
        temp = Fmt.temp(current.temp),
        feels = Fmt.temp(current.feels),
        condition = condition.labelFor(current.isDay, lang),
    )
}

/** Local time of an ISO timestamp with or without offset; null when unparsable. */
internal fun localTimeOf(iso: String): LocalTime? =
    runCatching { LocalDateTime.parse(iso.trim()).toLocalTime() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso.trim()).toLocalTime() }.getOrNull()

/** `HH:mm` (or ISO date-time) → time; null when unparsable. */
internal fun parseClock(value: String): LocalTime? =
    runCatching { LocalTime.parse(value.trim()) }.getOrNull() ?: localTimeOf(value)

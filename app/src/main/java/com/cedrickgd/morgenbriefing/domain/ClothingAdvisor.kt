package com.cedrickgd.morgenbriefing.domain

import com.cedrickgd.morgenbriefing.data.model.WeatherSnapshot
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.ClothingReason
import com.cedrickgd.morgenbriefing.i18n.ClothingStrings
import com.cedrickgd.morgenbriefing.i18n.SunExtras
import kotlin.math.max

/** A piece of clothing or an extra the advice can show; the label comes from [ClothingStrings]. */
enum class ClothingPiece(val emoji: String) {
    WINTER_COAT("🧥"),
    WINTER_JACKET("🧥"),
    WARM_JACKET("🧥"),
    JACKET("🧥"),
    LIGHT_JACKET("🧥"),
    SWEATER("🧶"),
    THIN_SWEATER("🧶"),
    T_SHIRT("👕"),
    SHORTS("🩳"),
    SCARF("🧣"),
    GLOVES("🧤"),
    UMBRELLA("☂️"),

    // Not the coat: next to "warme Jacke" the two chips must look different (the yellow vest reads as rain gear).
    RAIN_JACKET("🦺"),
    SUNGLASSES("🕶️"),
    SUNSCREEN("🧴"),
    STURDY_SHOES("🥾"),
}

/** Clothing bands by felt temperature, coldest first (ordinal order matters). */
enum class ClothingBand(
    val items: List<ClothingPiece>,
    /** Added only when the advice would otherwise show a single item. */
    val filler: ClothingPiece?,
) {
    ARCTIC(listOf(ClothingPiece.WINTER_COAT, ClothingPiece.SCARF, ClothingPiece.GLOVES), filler = null),
    COLD(listOf(ClothingPiece.WINTER_JACKET, ClothingPiece.GLOVES), filler = ClothingPiece.SCARF),
    WARM_JACKET(listOf(ClothingPiece.WARM_JACKET), filler = ClothingPiece.SCARF),
    JACKET(listOf(ClothingPiece.JACKET), filler = ClothingPiece.SWEATER),
    LIGHT(listOf(ClothingPiece.LIGHT_JACKET), filler = ClothingPiece.SWEATER),
    LONGSLEEVE(listOf(ClothingPiece.THIN_SWEATER), filler = ClothingPiece.T_SHIRT),
    TSHIRT(listOf(ClothingPiece.T_SHIRT), filler = ClothingPiece.SHORTS),
    HOT(listOf(ClothingPiece.T_SHIRT, ClothingPiece.SHORTS), filler = null);

    val isWinter: Boolean get() = this <= COLD

    companion object {
        fun of(feels: Double): ClothingBand = when {
            feels < -5 -> ARCTIC
            feels < 2 -> COLD
            feels < 8 -> WARM_JACKET
            feels < 13 -> JACKET
            feels < 17 -> LIGHT
            feels < 21 -> LONGSLEEVE
            feels <= 26 -> TSHIRT
            else -> HOT
        }
    }
}

/**
 * "Was ziehe ich an?" — layers from the felt temperature at home now and the warmest felt
 * temperature in the day window, plus rain, sun and ice extras. Pure and deterministic; the
 * wording comes from [ClothingStrings] in the requested language.
 */
internal object ClothingAdvisor {

    private const val MAX_ITEMS = 5
    private const val MAX_SHORT_WORDS = 8

    private enum class RainGear { NONE, UMBRELLA, RAINJACKET }

    private sealed interface Change {
        data object Same : Change
        data class Warmer(val band: ClothingBand, val hour: Int?) : Change
        data class Colder(val band: ClothingBand, val hour: Int?) : Change
    }

    private data class Pick(val piece: ClothingPiece, val priority: Int)

    fun advise(s: WeatherSnapshot, lang: AppLanguage): ClothingAdvice {
        val c = ClothingStrings.of(lang)
        val home = HomeFacts(s.home)
        val day = DayFacts(s.day)
        val tomorrow = s.day.isTomorrow

        // For tomorrow's window the current evening temperature says nothing about the morning.
        val morningFeels = if (tomorrow) day.hours.firstOrNull()?.feels ?: home.now.feels else home.now.feels
        val morning = ClothingBand.of(morningFeels)
        val change = changeOf(morning, day)

        val wetNow = !tomorrow && (home.wetNow || home.wetSoon != null)
        val needsRainGear = day.maxPop >= 40 || day.precipSum >= 0.5 || wetNow
        val gust = if (tomorrow) day.maxGust else max(day.maxGust, home.maxGust)
        val gear = when {
            !needsRainGear -> RainGear.NONE
            gust >= 40 -> RainGear.RAINJACKET
            else -> RainGear.UMBRELLA
        }
        // A rain jacket replaces an ordinary jacket instead of being worn over it.
        val merged = gear == RainGear.RAINJACKET && (morning == ClothingBand.JACKET || morning == ClothingBand.LIGHT)

        val sunglasses = day.sunnyHours >= 3
        val sunscreen = s.day.uvMax >= 5
        val shoes = day.conditions.any { it.isWintry } ||
            (day.isWetDay && day.minTemp <= 1.0) ||
            (!tomorrow && (home.slippery || home.wintryPrecip))

        val items = items(morning, change, gear, merged, shoes, sunscreen, sunglasses)
            .map { ClothingItem(it.emoji, c.piece(it)) }
        val reason = if (tomorrow) null else reasonFor(c, home, morning)
        val text = text(c, morning, change, gear, merged, shoes, sunscreen, sunglasses, reason, rainWhy(c, home, day, tomorrow))
        val short = short(c, morning, change, gear, merged)
        return ClothingAdvice(items = items, text = text, short = short)
    }

    private fun changeOf(morning: ClothingBand, day: DayFacts): Change {
        val warm = ClothingBand.of(day.maxFeels)
        val cold = ClothingBand.of(day.minFeels)
        return when {
            warm > morning && !(warm.isWinter && morning.isWinter) ->
                Change.Warmer(warm, day.hours.firstOrNull { ClothingBand.of(it.feels) >= warm }?.hour)
            cold < morning && !(cold.isWinter && morning.isWinter) ->
                Change.Colder(cold, day.hours.firstOrNull { ClothingBand.of(it.feels) <= cold }?.hour)
            else -> Change.Same
        }
    }

    private fun items(
        morning: ClothingBand,
        change: Change,
        gear: RainGear,
        merged: Boolean,
        shoes: Boolean,
        sunscreen: Boolean,
        sunglasses: Boolean,
    ): List<ClothingPiece> {
        val picks = mutableListOf<Pick>()
        morning.items.forEach { picks += Pick(it, 3) }
        if (merged) {
            val jacketIndex = picks.indexOfFirst { it.piece == morning.items.first() }
            picks[jacketIndex] = Pick(ClothingPiece.RAIN_JACKET, 5)
        }
        val target = when (change) {
            is Change.Warmer -> change.band
            is Change.Colder -> change.band
            Change.Same -> null
        }
        val additions = when (change) {
            // Going from one jacket to a lighter jacket adds nothing to carry.
            is Change.Warmer -> if (change.band >= ClothingBand.LONGSLEEVE) change.band.items else emptyList()
            // Already in a jacket: only the extras (gloves …); otherwise the jacket for later.
            is Change.Colder -> if (morning <= ClothingBand.LIGHT) change.band.items.drop(1) else change.band.items.take(1)
            Change.Same -> emptyList()
        }
        additions.filter { piece -> picks.none { it.piece == piece } }.forEach { picks += Pick(it, 3) }
        when (gear) {
            RainGear.UMBRELLA -> picks += Pick(ClothingPiece.UMBRELLA, 5)
            RainGear.RAINJACKET -> if (!merged) picks += Pick(ClothingPiece.RAIN_JACKET, 5)
            RainGear.NONE -> Unit
        }
        if (shoes) picks += Pick(ClothingPiece.STURDY_SHOES, 4)
        if (sunscreen) picks += Pick(ClothingPiece.SUNSCREEN, 2)
        if (sunglasses) picks += Pick(ClothingPiece.SUNGLASSES, 1)

        val unique = picks.distinctBy { it.piece }.toMutableList()
        if (unique.size < 2) {
            listOfNotNull(morning.filler, target?.filler, ClothingPiece.SWEATER)
                .firstOrNull { piece -> unique.none { it.piece == piece } }
                ?.let { unique += Pick(it, 0) }
        }
        // Keep the most important items, but show them in their natural order.
        return unique.withIndex()
            .sortedByDescending { it.value.priority }
            .take(MAX_ITEMS)
            .sortedBy { it.index }
            .map { it.value.piece }
    }

    private fun reasonFor(c: ClothingStrings, home: HomeFacts, morning: ClothingBand): String? {
        if (morning > ClothingBand.LIGHT) return null
        val feels = Fmt.temp(home.now.feels)
        val reason = when {
            home.condition == Condition.FOG -> ClothingReason.FOG
            home.now.temp - home.now.feels >= 3 -> ClothingReason.WIND_CHILL
            home.now.feels <= 0 -> ClothingReason.FREEZING
            home.now.humidity >= 90 && home.now.feels <= 12 -> ClothingReason.DAMP
            else -> return null
        }
        return c.reason(reason, feels)
    }

    private fun rainWhy(c: ClothingStrings, home: HomeFacts, day: DayFacts, tomorrow: Boolean): String {
        val firstWet = day.firstWet
        return when {
            !tomorrow && home.wetNow -> c.wetNowWhy
            !tomorrow && home.wetSoon != null -> c.wetSoonWhy
            day.rainStartsLater && firstWet != null -> c.wetFromWhy(firstWet.hour)
            day.maxPop >= 40 -> c.popWhy(day.maxPop)
            else -> c.maybeWetWhy
        }
    }

    private fun text(
        c: ClothingStrings,
        morning: ClothingBand,
        change: Change,
        gear: RainGear,
        merged: Boolean,
        shoes: Boolean,
        sunscreen: Boolean,
        sunglasses: Boolean,
        reason: String?,
        why: String,
    ): String {
        val sun = when {
            sunglasses && sunscreen -> c.sun(SunExtras.GLASSES_AND_CREAM)
            sunglasses -> c.sun(SunExtras.GLASSES)
            sunscreen -> c.sun(SunExtras.CREAM)
            else -> null
        }
        val wear = if (merged) c.mergedWear(light = morning == ClothingBand.LIGHT) else c.wear(morning)
        val sentences = mutableListOf<String>()
        when (change) {
            is Change.Warmer -> {
                val overShirt = !merged && change.band >= ClothingBand.TSHIRT && morning in ClothingBand.WARM_JACKET..ClothingBand.LIGHT
                sentences += c.morningWarmer(wear, overShirt, reason)
                sentences += c.laterWarmer(change.hour, change.band, sun)
            }
            is Change.Colder -> {
                sentences += c.morningColder(wear, reason, sun)
                sentences += c.laterColder(change.hour, change.band)
            }
            Change.Same -> {
                sentences += if (morning >= ClothingBand.LONGSLEEVE && !merged) {
                    c.todayEnough(wear, morning, reason)
                } else {
                    c.allDay(wear, reason)
                }
                sun?.let { sentences += c.sunSentence(it) }
            }
        }
        sentences += when (gear) {
            RainGear.NONE -> c.noUmbrella(shoes)
            RainGear.UMBRELLA -> c.umbrella(shoes, why)
            RainGear.RAINJACKET -> c.rainJacket(shoes)
        }
        return sentences.joinToString(" ")
    }

    private fun short(c: ClothingStrings, morning: ClothingBand, change: Change, gear: RainGear, merged: Boolean): String {
        val rain = when {
            merged -> c.noUmbrellaShort
            gear == RainGear.UMBRELLA -> c.packUmbrellaShort
            gear == RainGear.RAINJACKET -> c.rainJacketShort
            else -> c.noUmbrellaShort
        }
        val m = if (merged) c.rainJacketLayer else c.compact(morning)
        val candidates = when (change) {
            is Change.Warmer -> c.shortWarmer(m, c.compact(change.band), change.hour, rain)
            is Change.Colder -> c.shortColder(m, c.compact(change.band), change.hour, rain)
            Change.Same -> if (merged) listOf(c.shortMerged) else c.shortSame(morning, rain)
        }
        val chosen = candidates.firstOrNull { it.wordCount() <= MAX_SHORT_WORDS } ?: candidates.last()
        return chosen.capitalizeFirst() + "."
    }
}

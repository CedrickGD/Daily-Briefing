package com.cedrickgd.morgenbriefing.i18n

import com.cedrickgd.morgenbriefing.domain.ClothingBand
import com.cedrickgd.morgenbriefing.domain.ClothingPiece

/*
 * The texts of the clothing advice ("Was ziehe ich an?") per language. ClothingAdvisor picks the
 * layers, extras and the change during the day; these objects phrase them. A band's [wear] is a
 * complete noun phrase with its article where the language needs one ("a jacket or fleece"),
 * so sentences stay grammatical (verb agreement, gender) in every language.
 */

/** Why the morning feels colder than the number says. */
enum class ClothingReason { FOG, WIND_CHILL, FREEZING, DAMP }

/** The sun extras of the day. */
enum class SunExtras { GLASSES_AND_CREAM, GLASSES, CREAM }

interface ClothingStrings {
    /** Chip label of a piece ("Jacke", "Rain jacket"). */
    fun piece(piece: ClothingPiece): String

    /** Noun phrase for the card text ("Jacke oder Fleece", "a jacket or fleece"). */
    fun wear(band: ClothingBand): String

    /** 1–2 words for the change form of the short line ("Jacke"). */
    fun compact(band: ClothingBand): String

    /** ≤ 3 words for the all-day form of the short line ("Winterjacke und Handschuhe"). */
    fun short(band: ClothingBand): String

    /** The reason clause after the morning layer ("gefühlt nur 3°"), [feels] formatted. */
    fun reason(reason: ClothingReason, feels: String): String
    fun sun(extras: SunExtras): String
    /** Under a rain jacket instead of a jacket: [light] = the light-jacket band. */
    fun mergedWear(light: Boolean): String

    /** "Morgens <wear>[ übers Shirt][ – <because>]." */
    fun morningWarmer(wear: String, overShirt: Boolean, because: String?): String
    /** "Ab Mittag reicht T-Shirt[, <sun>]." */
    fun laterWarmer(hour: Int?, band: ClothingBand, sun: String?): String
    /** "Morgens <wear>[ – <because>][, <sun>]." */
    fun morningColder(wear: String, because: String?, sun: String?): String
    /** "Ab 16 Uhr wird's kühler – dann <wear>." */
    fun laterColder(hour: Int?, band: ClothingBand): String
    /** "Heute reicht <wear>[ – <because>]." for a light band that stays all day. */
    fun todayEnough(wear: String, band: ClothingBand, because: String?): String
    /** "Den ganzen Tag <wear>[ – <because>]." */
    fun allDay(wear: String, because: String?): String
    /** A sun extra as its own sentence. */
    fun sunSentence(sun: String): String

    fun noUmbrella(shoes: Boolean): String
    fun umbrella(shoes: Boolean, why: String): String
    fun rainJacket(shoes: Boolean): String

    val wetNowWhy: String
    val wetSoonWhy: String
    fun wetFromWhy(hour: Int): String
    fun popWhy(pop: Int): String
    val maybeWetWhy: String

    // Short line (≤ 8 words), candidates best first.
    val noUmbrellaShort: String
    val packUmbrellaShort: String
    val rainJacketShort: String
    /** The rain jacket as the morning layer of the short line. */
    val rainJacketLayer: String
    fun shortWarmer(morning: String, later: String, hour: Int?, rain: String): List<String>
    fun shortColder(morning: String, later: String, hour: Int?, rain: String): List<String>
    val shortMerged: String
    fun shortSame(band: ClothingBand, rain: String): List<String>

    companion object {
        fun of(lang: AppLanguage): ClothingStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    // =========================================================================================
    private object De : ClothingStrings {
        private fun fromNoon(hour: Int?) = when (hour) {
            null -> "später"
            12, 13 -> "ab Mittag"
            else -> "ab $hour Uhr"
        }
        private fun cap(text: String) = text.replaceFirstChar { it.uppercase() }
        private fun because(reason: String?) = reason?.let { " – $it" }.orEmpty()
        private fun plural(band: ClothingBand) = band == ClothingBand.COLD || band == ClothingBand.HOT

        override fun piece(piece: ClothingPiece) = when (piece) {
            ClothingPiece.WINTER_COAT -> "Wintermantel"
            ClothingPiece.WINTER_JACKET -> "Winterjacke"
            ClothingPiece.WARM_JACKET -> "warme Jacke"
            ClothingPiece.JACKET -> "Jacke"
            ClothingPiece.LIGHT_JACKET -> "leichte Jacke"
            ClothingPiece.SWEATER -> "Pulli"
            ClothingPiece.THIN_SWEATER -> "dünner Pulli"
            ClothingPiece.T_SHIRT -> "T-Shirt"
            ClothingPiece.SHORTS -> "kurze Hose"
            ClothingPiece.SCARF -> "Schal"
            ClothingPiece.GLOVES -> "Handschuhe"
            ClothingPiece.UMBRELLA -> "Schirm"
            ClothingPiece.RAIN_JACKET -> "Regenjacke"
            ClothingPiece.SUNGLASSES -> "Sonnenbrille"
            ClothingPiece.SUNSCREEN -> "Sonnencreme"
            ClothingPiece.STURDY_SHOES -> "feste Schuhe"
        }
        override fun wear(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "dicker Wintermantel mit Mütze, Handschuhen und Schal"
            ClothingBand.COLD -> "warme Winterjacke, Mütze und Handschuhe"
            ClothingBand.WARM_JACKET -> "warme Jacke"
            ClothingBand.JACKET -> "Jacke oder Fleece"
            ClothingBand.LIGHT -> "leichte Jacke oder Pulli"
            ClothingBand.LONGSLEEVE -> "Langarm oder dünner Pulli"
            ClothingBand.TSHIRT -> "T-Shirt"
            ClothingBand.HOT -> "luftige Sachen und kurze Hose"
        }
        override fun compact(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "Wintermantel"
            ClothingBand.COLD -> "Winterjacke"
            ClothingBand.WARM_JACKET -> "warme Jacke"
            ClothingBand.JACKET -> "Jacke"
            ClothingBand.LIGHT -> "leichte Jacke"
            ClothingBand.LONGSLEEVE -> "Pulli"
            ClothingBand.TSHIRT -> "T-Shirt"
            ClothingBand.HOT -> "kurze Hose"
        }
        override fun short(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "Wintermantel und Handschuhe"
            ClothingBand.COLD -> "Winterjacke und Handschuhe"
            ClothingBand.LONGSLEEVE -> "dünner Pulli"
            else -> compact(band)
        }
        override fun reason(reason: ClothingReason, feels: String) = when (reason) {
            ClothingReason.FOG -> "im Nebel fühlen sich $feels klamm an"
            ClothingReason.WIND_CHILL -> "gefühlt nur $feels"
            ClothingReason.FREEZING -> "gefühlt $feels"
            ClothingReason.DAMP -> "die feuchte Luft macht's klamm"
        }
        override fun sun(extras: SunExtras) = when (extras) {
            SunExtras.GLASSES_AND_CREAM -> "Sonnenbrille und Sonnencreme nicht vergessen"
            SunExtras.GLASSES -> "Sonnenbrille lohnt sich"
            SunExtras.CREAM -> "Sonnencreme nicht vergessen"
        }
        override fun mergedWear(light: Boolean) =
            if (light) "Shirt oder dünner Pulli unter der Regenjacke" else "Pulli oder Fleece unter der Regenjacke"
        override fun morningWarmer(wear: String, overShirt: Boolean, because: String?) =
            "Morgens $wear${if (overShirt) " übers Shirt" else ""}${because(because)}."
        override fun laterWarmer(hour: Int?, band: ClothingBand, sun: String?) =
            "${cap(fromNoon(hour))} ${if (plural(band)) "reichen" else "reicht"} ${wear(band)}${sun?.let { ", $it" }.orEmpty()}."
        override fun morningColder(wear: String, because: String?, sun: String?) =
            "Morgens $wear${because(because)}${sun?.let { ", $it" }.orEmpty()}."
        override fun laterColder(hour: Int?, band: ClothingBand) = "${cap(fromNoon(hour))} wird's kühler – dann ${wear(band)}."
        override fun todayEnough(wear: String, band: ClothingBand, because: String?) =
            "Heute ${if (plural(band)) "reichen" else "reicht"} $wear${because(because)}."
        override fun allDay(wear: String, because: String?) = "Den ganzen Tag $wear${because(because)}."
        override fun sunSentence(sun: String) = "$sun."
        override fun noUmbrella(shoes: Boolean) =
            if (shoes) "Kein Schirm nötig, aber feste Schuhe – es kann glatt sein." else "Kein Schirm nötig."
        override fun umbrella(shoes: Boolean, why: String) =
            if (shoes) "Schirm einpacken und feste Schuhe an – es kann glatt werden." else "Schirm einpacken – $why."
        override fun rainJacket(shoes: Boolean) =
            if (shoes) "Regenjacke statt Schirm – zu windig; dazu feste Schuhe, es kann glatt werden." else "Regenjacke statt Schirm – zu windig."
        override val wetNowWhy = "draußen ist's gerade nass"
        override val wetSoonWhy = "gleich wird's nass"
        override fun wetFromWhy(hour: Int) = "ab $hour Uhr wird's nass"
        override fun popWhy(pop: Int) = "bis $pop % Regenrisiko"
        override val maybeWetWhy = "es kann nass werden"
        override val noUmbrellaShort = "kein Schirm"
        override val packUmbrellaShort = "Schirm einpacken"
        override val rainJacketShort = "Regenjacke statt Schirm"
        override val rainJacketLayer = "Regenjacke"
        override fun shortWarmer(morning: String, later: String, hour: Int?, rain: String) = listOf(
            "Morgens $morning, ${fromNoon(hour)} $later, $rain",
            "Morgens $morning, später $later, $rain",
            "$morning, später $later, $rain",
            "$morning, $rain",
        )
        override fun shortColder(morning: String, later: String, hour: Int?, rain: String) = listOf(
            "Erst $morning, ${fromNoon(hour)} $later, $rain",
            "Erst $morning, später $later, $rain",
            "$morning, später $later",
            "$morning, $rain",
        )
        override val shortMerged = "Regenjacke statt Schirm, drunter Pulli"
        override fun shortSame(band: ClothingBand, rain: String) =
            listOf("Den ganzen Tag ${short(band)}, $rain", "${short(band)}, $rain", "${compact(band)}, $rain")
    }

    // =========================================================================================
    private object En : ClothingStrings {
        private fun fromNoon(hour: Int?) = when (hour) {
            null -> "later"
            12, 13 -> "from midday"
            else -> "from ${FormatStrings.of(AppLanguage.EN).hour(hour)}"
        }
        private fun cap(text: String) = text.replaceFirstChar { it.uppercase() }
        private fun because(reason: String?) = reason?.let { " – $it" }.orEmpty()

        override fun piece(piece: ClothingPiece) = when (piece) {
            ClothingPiece.WINTER_COAT -> "Winter coat"
            ClothingPiece.WINTER_JACKET -> "Winter jacket"
            ClothingPiece.WARM_JACKET -> "Warm jacket"
            ClothingPiece.JACKET -> "Jacket"
            ClothingPiece.LIGHT_JACKET -> "Light jacket"
            ClothingPiece.SWEATER -> "Sweater"
            ClothingPiece.THIN_SWEATER -> "Thin sweater"
            ClothingPiece.T_SHIRT -> "T-shirt"
            ClothingPiece.SHORTS -> "Shorts"
            ClothingPiece.SCARF -> "Scarf"
            ClothingPiece.GLOVES -> "Gloves"
            ClothingPiece.UMBRELLA -> "Umbrella"
            ClothingPiece.RAIN_JACKET -> "Rain jacket"
            ClothingPiece.SUNGLASSES -> "Sunglasses"
            ClothingPiece.SUNSCREEN -> "Sunscreen"
            ClothingPiece.STURDY_SHOES -> "Sturdy shoes"
        }
        override fun wear(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "a thick winter coat with hat, gloves and scarf"
            ClothingBand.COLD -> "a warm winter jacket, hat and gloves"
            ClothingBand.WARM_JACKET -> "a warm jacket"
            ClothingBand.JACKET -> "a jacket or fleece"
            ClothingBand.LIGHT -> "a light jacket or sweater"
            ClothingBand.LONGSLEEVE -> "long sleeves or a thin sweater"
            ClothingBand.TSHIRT -> "a T-shirt"
            ClothingBand.HOT -> "light clothes and shorts"
        }
        override fun compact(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "winter coat"
            ClothingBand.COLD -> "winter jacket"
            ClothingBand.WARM_JACKET -> "warm jacket"
            ClothingBand.JACKET -> "jacket"
            ClothingBand.LIGHT -> "light jacket"
            ClothingBand.LONGSLEEVE -> "sweater"
            ClothingBand.TSHIRT -> "T-shirt"
            ClothingBand.HOT -> "shorts"
        }
        override fun short(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "winter coat and gloves"
            ClothingBand.COLD -> "winter jacket and gloves"
            ClothingBand.LONGSLEEVE -> "thin sweater"
            else -> compact(band)
        }
        override fun reason(reason: ClothingReason, feels: String) = when (reason) {
            ClothingReason.FOG -> "the fog makes $feels feel damp"
            ClothingReason.WIND_CHILL -> "feels like only $feels"
            ClothingReason.FREEZING -> "feels like $feels"
            ClothingReason.DAMP -> "the damp air makes it feel chilly"
        }
        override fun sun(extras: SunExtras) = when (extras) {
            SunExtras.GLASSES_AND_CREAM -> "don't forget sunglasses and sunscreen"
            SunExtras.GLASSES -> "sunglasses are worth it"
            SunExtras.CREAM -> "don't forget sunscreen"
        }
        override fun mergedWear(light: Boolean) =
            if (light) "a shirt or thin sweater under the rain jacket" else "a sweater or fleece under the rain jacket"
        override fun morningWarmer(wear: String, overShirt: Boolean, because: String?) =
            "In the morning ${wear}${if (overShirt) " over your shirt" else ""}${because(because)}."
        override fun laterWarmer(hour: Int?, band: ClothingBand, sun: String?) =
            "${cap(fromNoon(hour))} ${wear(band)} will do${sun?.let { ", $it" }.orEmpty()}."
        override fun morningColder(wear: String, because: String?, sun: String?) =
            "In the morning $wear${because(because)}${sun?.let { ", $it" }.orEmpty()}."
        override fun laterColder(hour: Int?, band: ClothingBand) = "${cap(fromNoon(hour))} it gets cooler – then ${wear(band)}."
        override fun todayEnough(wear: String, band: ClothingBand, because: String?) = "Today $wear will do${because(because)}."
        override fun allDay(wear: String, because: String?) = "${cap(wear)} all day${because(because)}."
        override fun sunSentence(sun: String) = "${cap(sun)}."
        override fun noUmbrella(shoes: Boolean) =
            if (shoes) "No umbrella needed, but sturdy shoes – it may be slippery." else "No umbrella needed."
        override fun umbrella(shoes: Boolean, why: String) =
            if (shoes) "Pack an umbrella and wear sturdy shoes – it may get slippery." else "Pack an umbrella – $why."
        override fun rainJacket(shoes: Boolean) =
            if (shoes) {
                "Rain jacket instead of an umbrella – too windy; sturdy shoes too, it may get slippery."
            } else {
                "Rain jacket instead of an umbrella – too windy."
            }
        override val wetNowWhy = "it's wet out there right now"
        override val wetSoonWhy = "it's about to get wet"
        override fun wetFromWhy(hour: Int) = "it gets wet from ${FormatStrings.of(AppLanguage.EN).hour(hour)}"
        override fun popWhy(pop: Int) = "up to $pop% chance of rain"
        override val maybeWetWhy = "it may get wet"
        override val noUmbrellaShort = "no umbrella"
        override val packUmbrellaShort = "pack an umbrella"
        override val rainJacketShort = "rain jacket, no umbrella"
        override val rainJacketLayer = "rain jacket"
        override fun shortWarmer(morning: String, later: String, hour: Int?, rain: String) = listOf(
            "$morning first, $later ${fromNoon(hour)}, $rain",
            "$morning first, $later later, $rain",
            "$morning, $later later, $rain",
            "$morning, $rain",
        )
        override fun shortColder(morning: String, later: String, hour: Int?, rain: String) = listOf(
            "$morning first, $later ${fromNoon(hour)}, $rain",
            "$morning first, $later later, $rain",
            "$morning, $later later",
            "$morning, $rain",
        )
        override val shortMerged = "Rain jacket instead of umbrella, sweater underneath"
        override fun shortSame(band: ClothingBand, rain: String) =
            listOf("${short(band)} all day, $rain", "${short(band)}, $rain", "${compact(band)}, $rain")
    }

    // =========================================================================================
    private object Fr : ClothingStrings {
        private fun at(hour: Int) = when (val h = Math.floorMod(hour, 24)) {
            0 -> "minuit"
            12 -> "midi"
            else -> "$h h"
        }
        private fun fromNoon(hour: Int?) = when (hour) {
            null -> "plus tard"
            12, 13 -> "dès midi"
            else -> "dès ${at(hour)}"
        }
        private fun cap(text: String) = text.replaceFirstChar { it.uppercase() }
        private fun because(reason: String?) = reason?.let { " – $it" }.orEmpty()
        private fun plural(band: ClothingBand) = band == ClothingBand.COLD || band == ClothingBand.HOT

        override fun piece(piece: ClothingPiece) = when (piece) {
            ClothingPiece.WINTER_COAT -> "Manteau d'hiver"
            ClothingPiece.WINTER_JACKET -> "Doudoune"
            ClothingPiece.WARM_JACKET -> "Veste chaude"
            ClothingPiece.JACKET -> "Veste"
            ClothingPiece.LIGHT_JACKET -> "Veste légère"
            ClothingPiece.SWEATER -> "Pull"
            ClothingPiece.THIN_SWEATER -> "Pull fin"
            ClothingPiece.T_SHIRT -> "T-shirt"
            ClothingPiece.SHORTS -> "Short"
            ClothingPiece.SCARF -> "Écharpe"
            ClothingPiece.GLOVES -> "Gants"
            ClothingPiece.UMBRELLA -> "Parapluie"
            ClothingPiece.RAIN_JACKET -> "Veste de pluie"
            ClothingPiece.SUNGLASSES -> "Lunettes de soleil"
            ClothingPiece.SUNSCREEN -> "Crème solaire"
            ClothingPiece.STURDY_SHOES -> "Bonnes chaussures"
        }
        override fun wear(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "un gros manteau d'hiver avec bonnet, gants et écharpe"
            ClothingBand.COLD -> "une doudoune, un bonnet et des gants"
            ClothingBand.WARM_JACKET -> "une veste chaude"
            ClothingBand.JACKET -> "une veste ou une polaire"
            ClothingBand.LIGHT -> "une veste légère ou un pull"
            ClothingBand.LONGSLEEVE -> "un haut à manches longues ou un pull fin"
            ClothingBand.TSHIRT -> "un t-shirt"
            ClothingBand.HOT -> "des vêtements légers et un short"
        }
        override fun compact(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "manteau d'hiver"
            ClothingBand.COLD -> "doudoune"
            ClothingBand.WARM_JACKET -> "veste chaude"
            ClothingBand.JACKET -> "veste"
            ClothingBand.LIGHT -> "veste légère"
            ClothingBand.LONGSLEEVE -> "pull"
            ClothingBand.TSHIRT -> "t-shirt"
            ClothingBand.HOT -> "short"
        }
        override fun short(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "manteau d'hiver et gants"
            ClothingBand.COLD -> "doudoune et gants"
            ClothingBand.LONGSLEEVE -> "pull fin"
            else -> compact(band)
        }
        override fun reason(reason: ClothingReason, feels: String) = when (reason) {
            ClothingReason.FOG -> "avec le brouillard, $feels paraissent bien frais"
            ClothingReason.WIND_CHILL -> "ressenti seulement $feels"
            ClothingReason.FREEZING -> "ressenti $feels"
            ClothingReason.DAMP -> "l'air humide donne une sensation de froid"
        }
        override fun sun(extras: SunExtras) = when (extras) {
            SunExtras.GLASSES_AND_CREAM -> "n'oublie pas lunettes de soleil et crème solaire"
            SunExtras.GLASSES -> "les lunettes de soleil valent le coup"
            SunExtras.CREAM -> "n'oublie pas la crème solaire"
        }
        override fun mergedWear(light: Boolean) =
            if (light) "un t-shirt ou un pull fin sous la veste de pluie" else "un pull ou une polaire sous la veste de pluie"
        override fun morningWarmer(wear: String, overShirt: Boolean, because: String?) =
            "Le matin, $wear${if (overShirt) " sur le t-shirt" else ""}${because(because)}."
        override fun laterWarmer(hour: Int?, band: ClothingBand, sun: String?) =
            "${cap(fromNoon(hour))}, ${wear(band)} ${if (plural(band)) "suffisent" else "suffit"}${sun?.let { ", $it" }.orEmpty()}."
        override fun morningColder(wear: String, because: String?, sun: String?) =
            "Le matin, $wear${because(because)}${sun?.let { ", $it" }.orEmpty()}."
        override fun laterColder(hour: Int?, band: ClothingBand) = "${cap(fromNoon(hour))}, ça se rafraîchit – alors ${wear(band)}."
        override fun todayEnough(wear: String, band: ClothingBand, because: String?) =
            "Aujourd'hui, $wear ${if (plural(band)) "suffisent" else "suffit"}${because(because)}."
        override fun allDay(wear: String, because: String?) = "Toute la journée, $wear${because(because)}."
        override fun sunSentence(sun: String) = "${cap(sun)}."
        override fun noUmbrella(shoes: Boolean) =
            if (shoes) "Pas besoin de parapluie, mais de bonnes chaussures – ça peut glisser." else "Pas besoin de parapluie."
        override fun umbrella(shoes: Boolean, why: String) =
            if (shoes) "Prends un parapluie et de bonnes chaussures – ça peut glisser." else "Prends un parapluie – $why."
        override fun rainJacket(shoes: Boolean) =
            if (shoes) {
                "Veste de pluie plutôt que parapluie – trop de vent ; et de bonnes chaussures, ça peut glisser."
            } else {
                "Veste de pluie plutôt que parapluie – trop de vent."
            }
        override val wetNowWhy = "dehors, c'est mouillé"
        override val wetSoonWhy = "la pluie arrive"
        override fun wetFromWhy(hour: Int) = "pluie dès ${at(hour)}"
        override fun popWhy(pop: Int) = "jusqu'à $pop % de risque de pluie"
        override val maybeWetWhy = "il peut pleuvoir"
        override val noUmbrellaShort = "pas de parapluie"
        override val packUmbrellaShort = "prends un parapluie"
        override val rainJacketShort = "veste de pluie, pas de parapluie"
        override val rainJacketLayer = "veste de pluie"
        override fun shortWarmer(morning: String, later: String, hour: Int?, rain: String) = listOf(
            "D'abord $morning, $later ${fromNoon(hour)}, $rain",
            "D'abord $morning, $later plus tard, $rain",
            "$morning, $later plus tard, $rain",
            "$morning, $rain",
        )
        override fun shortColder(morning: String, later: String, hour: Int?, rain: String) = listOf(
            "D'abord $morning, $later ${fromNoon(hour)}, $rain",
            "D'abord $morning, $later plus tard, $rain",
            "$morning, $later plus tard",
            "$morning, $rain",
        )
        override val shortMerged = "Veste de pluie plutôt que parapluie, pull dessous"
        override fun shortSame(band: ClothingBand, rain: String) =
            listOf("${short(band)} toute la journée, $rain", "${short(band)}, $rain", "${compact(band)}, $rain")
    }

    // =========================================================================================
    private object Es : ClothingStrings {
        private fun at(hour: Int) = when (val h = Math.floorMod(hour, 24)) {
            0 -> "medianoche"
            1 -> "la 1 h"
            else -> "las $h h"
        }
        private fun fromNoon(hour: Int?) = when (hour) {
            null -> "más tarde"
            12, 13 -> "desde el mediodía"
            else -> "desde ${at(hour)}"
        }
        private fun cap(text: String) = text.replaceFirstChar { it.uppercase() }
        private fun because(reason: String?) = reason?.let { " – $it" }.orEmpty()

        override fun piece(piece: ClothingPiece) = when (piece) {
            ClothingPiece.WINTER_COAT -> "Abrigo de invierno"
            ClothingPiece.WINTER_JACKET -> "Chaqueta de invierno"
            ClothingPiece.WARM_JACKET -> "Chaqueta abrigada"
            ClothingPiece.JACKET -> "Chaqueta"
            ClothingPiece.LIGHT_JACKET -> "Chaqueta ligera"
            ClothingPiece.SWEATER -> "Jersey"
            ClothingPiece.THIN_SWEATER -> "Jersey fino"
            ClothingPiece.T_SHIRT -> "Camiseta"
            ClothingPiece.SHORTS -> "Pantalón corto"
            ClothingPiece.SCARF -> "Bufanda"
            ClothingPiece.GLOVES -> "Guantes"
            ClothingPiece.UMBRELLA -> "Paraguas"
            ClothingPiece.RAIN_JACKET -> "Chubasquero"
            ClothingPiece.SUNGLASSES -> "Gafas de sol"
            ClothingPiece.SUNSCREEN -> "Crema solar"
            ClothingPiece.STURDY_SHOES -> "Buen calzado"
        }
        override fun wear(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "un abrigo grueso de invierno con gorro, guantes y bufanda"
            ClothingBand.COLD -> "una chaqueta de invierno, gorro y guantes"
            ClothingBand.WARM_JACKET -> "una chaqueta abrigada"
            ClothingBand.JACKET -> "una chaqueta o un forro polar"
            ClothingBand.LIGHT -> "una chaqueta ligera o un jersey"
            ClothingBand.LONGSLEEVE -> "manga larga o un jersey fino"
            ClothingBand.TSHIRT -> "una camiseta"
            ClothingBand.HOT -> "ropa ligera y pantalón corto"
        }
        override fun compact(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "abrigo"
            ClothingBand.COLD -> "chaqueta de invierno"
            ClothingBand.WARM_JACKET -> "chaqueta abrigada"
            ClothingBand.JACKET -> "chaqueta"
            ClothingBand.LIGHT -> "chaqueta ligera"
            ClothingBand.LONGSLEEVE -> "jersey"
            ClothingBand.TSHIRT -> "camiseta"
            ClothingBand.HOT -> "pantalón corto"
        }
        override fun short(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "abrigo y guantes"
            ClothingBand.COLD -> "chaqueta y guantes"
            ClothingBand.LONGSLEEVE -> "jersey fino"
            else -> compact(band)
        }
        override fun reason(reason: ClothingReason, feels: String) = when (reason) {
            ClothingReason.FOG -> "con la niebla, $feels se sienten húmedos"
            ClothingReason.WIND_CHILL -> "sensación de solo $feels"
            ClothingReason.FREEZING -> "sensación de $feels"
            ClothingReason.DAMP -> "el aire húmedo da sensación de frío"
        }
        override fun sun(extras: SunExtras) = when (extras) {
            SunExtras.GLASSES_AND_CREAM -> "no olvides las gafas de sol y la crema solar"
            SunExtras.GLASSES -> "merecen la pena las gafas de sol"
            SunExtras.CREAM -> "no olvides la crema solar"
        }
        override fun mergedWear(light: Boolean) =
            if (light) "una camiseta o un jersey fino bajo el chubasquero" else "un jersey o un forro polar bajo el chubasquero"
        override fun morningWarmer(wear: String, overShirt: Boolean, because: String?) =
            "Por la mañana, $wear${if (overShirt) " sobre la camiseta" else ""}${because(because)}."
        override fun laterWarmer(hour: Int?, band: ClothingBand, sun: String?) =
            "${cap(fromNoon(hour))} basta con ${wear(band)}${sun?.let { ", $it" }.orEmpty()}."
        override fun morningColder(wear: String, because: String?, sun: String?) =
            "Por la mañana, $wear${because(because)}${sun?.let { ", $it" }.orEmpty()}."
        override fun laterColder(hour: Int?, band: ClothingBand) = "${cap(fromNoon(hour))} refresca – entonces, ${wear(band)}."
        override fun todayEnough(wear: String, band: ClothingBand, because: String?) = "Hoy basta con $wear${because(because)}."
        override fun allDay(wear: String, because: String?) = "Todo el día, $wear${because(because)}."
        override fun sunSentence(sun: String) = "${cap(sun)}."
        override fun noUmbrella(shoes: Boolean) =
            if (shoes) "No hace falta paraguas, pero sí buen calzado – puede resbalar." else "No hace falta paraguas."
        override fun umbrella(shoes: Boolean, why: String) =
            if (shoes) "Llévate paraguas y buen calzado – puede resbalar." else "Llévate paraguas – $why."
        override fun rainJacket(shoes: Boolean) =
            if (shoes) {
                "Chubasquero en vez de paraguas – hace demasiado viento; y buen calzado, puede resbalar."
            } else {
                "Chubasquero en vez de paraguas – hace demasiado viento."
            }
        override val wetNowWhy = "fuera está mojado"
        override val wetSoonWhy = "enseguida va a llover"
        override fun wetFromWhy(hour: Int) = "lloverá desde ${at(hour)}"
        override fun popWhy(pop: Int) = "hasta un $pop % de probabilidad de lluvia"
        override val maybeWetWhy = "puede llover"
        override val noUmbrellaShort = "sin paraguas"
        override val packUmbrellaShort = "llévate paraguas"
        override val rainJacketShort = "chubasquero, no paraguas"
        override val rainJacketLayer = "chubasquero"
        override fun shortWarmer(morning: String, later: String, hour: Int?, rain: String) = listOf(
            "Primero $morning, $later ${fromNoon(hour)}, $rain",
            "Primero $morning, $later más tarde, $rain",
            "$morning, $later más tarde, $rain",
            "$morning, $rain",
        )
        override fun shortColder(morning: String, later: String, hour: Int?, rain: String) = listOf(
            "Primero $morning, $later ${fromNoon(hour)}, $rain",
            "Primero $morning, $later más tarde, $rain",
            "$morning, $later más tarde",
            "$morning, $rain",
        )
        override val shortMerged = "Chubasquero en vez de paraguas, jersey debajo"
        override fun shortSame(band: ClothingBand, rain: String) =
            listOf("${short(band)} todo el día, $rain", "${short(band)}, $rain", "${compact(band)}, $rain")
    }

    // =========================================================================================
    private object It : ClothingStrings {
        private fun from(hour: Int) = when (val h = Math.floorMod(hour, 24)) {
            0 -> "da mezzanotte"
            1 -> "dall'una"
            12 -> "da mezzogiorno"
            else -> "dalle $h"
        }
        private fun fromNoon(hour: Int?) = when (hour) {
            null -> "più tardi"
            12, 13 -> "da mezzogiorno"
            else -> from(hour)
        }
        private fun cap(text: String) = text.replaceFirstChar { it.uppercase() }
        private fun because(reason: String?) = reason?.let { " – $it" }.orEmpty()
        private fun plural(band: ClothingBand) = band == ClothingBand.COLD || band == ClothingBand.HOT

        override fun piece(piece: ClothingPiece) = when (piece) {
            ClothingPiece.WINTER_COAT -> "Cappotto invernale"
            ClothingPiece.WINTER_JACKET -> "Giaccone"
            ClothingPiece.WARM_JACKET -> "Giacca pesante"
            ClothingPiece.JACKET -> "Giacca"
            ClothingPiece.LIGHT_JACKET -> "Giacca leggera"
            ClothingPiece.SWEATER -> "Maglione"
            ClothingPiece.THIN_SWEATER -> "Maglioncino"
            ClothingPiece.T_SHIRT -> "Maglietta"
            ClothingPiece.SHORTS -> "Pantaloncini"
            ClothingPiece.SCARF -> "Sciarpa"
            ClothingPiece.GLOVES -> "Guanti"
            ClothingPiece.UMBRELLA -> "Ombrello"
            ClothingPiece.RAIN_JACKET -> "Impermeabile"
            ClothingPiece.SUNGLASSES -> "Occhiali da sole"
            ClothingPiece.SUNSCREEN -> "Crema solare"
            ClothingPiece.STURDY_SHOES -> "Scarpe robuste"
        }
        override fun wear(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "un cappotto invernale pesante con cappello, guanti e sciarpa"
            ClothingBand.COLD -> "giaccone invernale, cappello e guanti"
            ClothingBand.WARM_JACKET -> "una giacca pesante"
            ClothingBand.JACKET -> "una giacca o un pile"
            ClothingBand.LIGHT -> "una giacca leggera o un maglione"
            ClothingBand.LONGSLEEVE -> "maniche lunghe o un maglioncino"
            ClothingBand.TSHIRT -> "una maglietta"
            ClothingBand.HOT -> "vestiti leggeri e pantaloncini"
        }
        override fun compact(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "cappotto"
            ClothingBand.COLD -> "giaccone"
            ClothingBand.WARM_JACKET -> "giacca pesante"
            ClothingBand.JACKET -> "giacca"
            ClothingBand.LIGHT -> "giacca leggera"
            ClothingBand.LONGSLEEVE -> "maglione"
            ClothingBand.TSHIRT -> "maglietta"
            ClothingBand.HOT -> "pantaloncini"
        }
        override fun short(band: ClothingBand) = when (band) {
            ClothingBand.ARCTIC -> "cappotto e guanti"
            ClothingBand.COLD -> "giaccone e guanti"
            ClothingBand.LONGSLEEVE -> "maglioncino"
            else -> compact(band)
        }
        override fun reason(reason: ClothingReason, feels: String) = when (reason) {
            ClothingReason.FOG -> "con la nebbia $feels sembrano più freddi"
            ClothingReason.WIND_CHILL -> "percepita solo $feels"
            ClothingReason.FREEZING -> "percepita $feels"
            ClothingReason.DAMP -> "l'aria umida fa sentire più freddo"
        }
        override fun sun(extras: SunExtras) = when (extras) {
            SunExtras.GLASSES_AND_CREAM -> "non dimenticare occhiali da sole e crema solare"
            SunExtras.GLASSES -> "gli occhiali da sole servono"
            SunExtras.CREAM -> "non dimenticare la crema solare"
        }
        override fun mergedWear(light: Boolean) =
            if (light) "una maglietta o un maglioncino sotto l'impermeabile" else "un maglione o un pile sotto l'impermeabile"
        override fun morningWarmer(wear: String, overShirt: Boolean, because: String?) =
            "Al mattino $wear${if (overShirt) " sopra la maglietta" else ""}${because(because)}."
        override fun laterWarmer(hour: Int?, band: ClothingBand, sun: String?) =
            "${cap(fromNoon(hour))} ${if (plural(band)) "bastano" else "basta"} ${wear(band)}${sun?.let { ", $it" }.orEmpty()}."
        override fun morningColder(wear: String, because: String?, sun: String?) =
            "Al mattino $wear${because(because)}${sun?.let { ", $it" }.orEmpty()}."
        override fun laterColder(hour: Int?, band: ClothingBand) = "${cap(fromNoon(hour))} rinfresca – allora ${wear(band)}."
        override fun todayEnough(wear: String, band: ClothingBand, because: String?) =
            "Oggi ${if (plural(band)) "bastano" else "basta"} $wear${because(because)}."
        override fun allDay(wear: String, because: String?) = "Tutto il giorno $wear${because(because)}."
        override fun sunSentence(sun: String) = "${cap(sun)}."
        override fun noUmbrella(shoes: Boolean) =
            if (shoes) "Niente ombrello, ma scarpe robuste – si può scivolare." else "Niente ombrello."
        override fun umbrella(shoes: Boolean, why: String) =
            if (shoes) "Porta l'ombrello e metti scarpe robuste – si può scivolare." else "Porta l'ombrello – $why."
        override fun rainJacket(shoes: Boolean) =
            if (shoes) {
                "Impermeabile al posto dell'ombrello – troppo vento; e scarpe robuste, si può scivolare."
            } else {
                "Impermeabile al posto dell'ombrello – troppo vento."
            }
        override val wetNowWhy = "fuori è bagnato"
        override val wetSoonWhy = "tra poco piove"
        override fun wetFromWhy(hour: Int) = "pioggia ${from(hour)}"
        override fun popWhy(pop: Int) = "probabilità di pioggia $pop%"
        override val maybeWetWhy = "potrebbe piovere"
        override val noUmbrellaShort = "niente ombrello"
        override val packUmbrellaShort = "porta l'ombrello"
        override val rainJacketShort = "impermeabile, niente ombrello"
        override val rainJacketLayer = "impermeabile"
        override fun shortWarmer(morning: String, later: String, hour: Int?, rain: String) = listOf(
            "Prima $morning, $later ${fromNoon(hour)}, $rain",
            "Prima $morning, $later più tardi, $rain",
            "$morning, $later più tardi, $rain",
            "$morning, $rain",
        )
        override fun shortColder(morning: String, later: String, hour: Int?, rain: String) = listOf(
            "Prima $morning, $later ${fromNoon(hour)}, $rain",
            "Prima $morning, $later più tardi, $rain",
            "$morning, $later più tardi",
            "$morning, $rain",
        )
        override val shortMerged = "Impermeabile al posto dell'ombrello, sotto un maglione"
        override fun shortSame(band: ClothingBand, rain: String) =
            listOf("${short(band)} tutto il giorno, $rain", "${short(band)}, $rain", "${compact(band)}, $rain")
    }
}

package com.cedrickgd.morgenbriefing.location

import kotlin.math.roundToInt

/** How a reverse-geocoded address becomes a [com.cedrickgd.morgenbriefing.data.model.Place]. Pure, unit-tested. */
internal object PlaceNaming {

    /** The most specific non-blank part: town, district, state, feature; null when all are blank. */
    fun townName(vararg candidates: String?): String? =
        candidates.firstOrNull { !it.isNullOrBlank() }?.trim()

    /** "Sachsen, Deutschland": state and country, without repeating the town's own name. */
    fun region(name: String, adminArea: String?, countryName: String?): String =
        listOf(adminArea, countryName)
            .mapNotNull { it?.trim() }
            .filter { it.isNotEmpty() && !it.equals(name, ignoreCase = true) }
            .joinToString(", ")

    /** The name in the country's language when it differs from the shown [name]; else empty. */
    fun localName(name: String, local: String?): String =
        local?.trim()?.takeIf { it.isNotEmpty() && !it.equals(name, ignoreCase = true) }.orEmpty()

    /** "51.59, 12.59" – the label of a spot without a town. */
    fun coordinatesLabel(lat: Double, lon: Double): String {
        fun fmt(v: Double) = ((v * 100).roundToInt() / 100.0).toString()
        return "${fmt(lat)}, ${fmt(lon)}"
    }
}

package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * The languages the app's own interface speaks. The language of the *news* is separate
 * ([com.cedrickgd.morgenbriefing.data.model.NewsEdition]): someone can read the app in German
 * and follow local news from Washington, D.C. in English.
 *
 * Every user-visible text lives in a feature strings interface (`i18n/<Feature>Strings.kt`) with one
 * implementation per entry here, so the compiler refuses a language that misses a text.
 */
enum class AppLanguage(
    /** ISO 639-1 code, also used for [Locale] and stored in the settings. */
    val code: String,
    /** The language's name in itself, for the picker ("Deutsch", "English" …). */
    val nativeName: String,
) {
    DE("de", "Deutsch"),
    EN("en", "English"),
    FR("fr", "Français"),
    ES("es", "Español"),
    IT("it", "Italiano"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(code)

    companion object {
        /** Used when the phone's language is none of ours. */
        val FALLBACK: AppLanguage = EN

        fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        /** The phone's language when we speak it, else [FALLBACK]. */
        fun system(locale: Locale = Locale.getDefault()): AppLanguage = fromCode(locale.language) ?: FALLBACK

        /** The user's choice ([pref], null = follow the phone) resolved to a concrete language. */
        fun resolve(pref: AppLanguage?, locale: Locale = Locale.getDefault()): AppLanguage = pref ?: system(locale)
    }
}

/**
 * The interface language of the current composition. Provided once at the root (AppRoot) from
 * the settings; feature strings are read with e.g. `TodayStrings.of(LocalAppLanguage.current)`
 * or the `rememberXStrings()` helper of each feature file.
 */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.DE }

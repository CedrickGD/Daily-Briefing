package com.cedrickgd.morgenbriefing.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/** Texts of the app's shell: the names of the main pages (glass page indicator, TalkBack). */
interface ShellStrings {
    /** The page with weather and news. */
    val today: String

    /** The settings page. */
    val settings: String

    companion object {
        fun of(lang: AppLanguage): ShellStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : ShellStrings {
        override val today = "Heute"
        override val settings = "Einstellungen"
    }

    private object En : ShellStrings {
        override val today = "Today"
        override val settings = "Settings"
    }

    private object Fr : ShellStrings {
        override val today = "Aujourd’hui"
        override val settings = "Réglages"
    }

    private object Es : ShellStrings {
        override val today = "Hoy"
        override val settings = "Ajustes"
    }

    private object It : ShellStrings {
        override val today = "Oggi"
        override val settings = "Impostazioni"
    }
}

@Composable
@ReadOnlyComposable
fun shellStrings(): ShellStrings = ShellStrings.of(LocalAppLanguage.current)

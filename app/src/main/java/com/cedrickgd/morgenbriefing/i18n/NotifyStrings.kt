package com.cedrickgd.morgenbriefing.i18n

/**
 * Texts of the notifications the app posts itself (the morning text proper comes from
 * [WeatherStrings]). The channel names live in the `strings.xml` resources, because Android shows
 * them in the system settings in the phone's language.
 */
interface NotifyStrings {
    /** Title of the plain fallback when the briefing text could not be built. */
    val fallbackTitle: String
    val fallbackText: String

    /** The quiet notice while expedited work runs as a foreground service (below API 31). */
    val syncTitle: String
    val syncText: String

    companion object {
        fun of(lang: AppLanguage): NotifyStrings = when (lang) {
            AppLanguage.DE -> De
            AppLanguage.EN -> En
            AppLanguage.FR -> Fr
            AppLanguage.ES -> Es
            AppLanguage.IT -> It
        }
    }

    private object De : NotifyStrings {
        override val fallbackTitle = "Guten Morgen! ☀️"
        override val fallbackText = "Wetter und News warten auf dich."
        override val syncTitle = "Briefing wird geladen…"
        override val syncText = "Wetter und News werden gerade frisch geholt."
    }

    private object En : NotifyStrings {
        override val fallbackTitle = "Good morning! ☀️"
        override val fallbackText = "Your weather and news are waiting."
        override val syncTitle = "Loading your briefing…"
        override val syncText = "Fetching fresh weather and news."
    }

    private object Fr : NotifyStrings {
        override val fallbackTitle = "Bonjour ! ☀️"
        override val fallbackText = "La météo et les infos t'attendent."
        override val syncTitle = "Chargement du briefing…"
        override val syncText = "Météo et infos en cours d'actualisation."
    }

    private object Es : NotifyStrings {
        override val fallbackTitle = "¡Buenos días! ☀️"
        override val fallbackText = "El tiempo y las noticias te esperan."
        override val syncTitle = "Cargando tu briefing…"
        override val syncText = "Actualizando el tiempo y las noticias."
    }

    private object It : NotifyStrings {
        override val fallbackTitle = "Buongiorno! ☀️"
        override val fallbackText = "Meteo e notizie ti aspettano."
        override val syncTitle = "Caricamento del briefing…"
        override val syncText = "Aggiornamento di meteo e notizie in corso."
    }
}

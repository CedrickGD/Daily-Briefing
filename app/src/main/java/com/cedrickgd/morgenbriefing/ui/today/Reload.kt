package com.cedrickgd.morgenbriefing.ui.today

/**
 * What "Heute" loads by itself (pure, unit-tested). The news reload on their own only once, on
 * a cold start – or whenever nothing is cached, so a first start never stays empty. Coming back
 * to the screen (from the reader, from the background) never reloads them, and neither does a
 * process Android restored after stopping it: the list stays where the reader left it. After
 * that only the user reloads (pull-to-refresh, "Erneut versuchen"), and a change of a topic's
 * sources in the settings does. The weather may refresh whenever it is old: it never moves the list.
 */
internal data class Reload(val weather: Boolean, val news: Boolean) {
    companion object {
        /**
         * When the screen starts. [restored]: Android brought a stopped process back (the saved
         * state says the screen ran before); [cached]: the news cache holds stories.
         */
        fun atStart(restored: Boolean, cached: Boolean): Reload = Reload(weather = true, news = !restored || !cached)

        /**
         * When the screen comes back. The weather once it is [weatherStale] and was not just
         * tried ([weatherTriedLately]); the news never.
         */
        fun onResume(weatherStale: Boolean, weatherTriedLately: Boolean): Reload =
            Reload(weather = weatherStale && !weatherTriedLately, news = false)
    }
}

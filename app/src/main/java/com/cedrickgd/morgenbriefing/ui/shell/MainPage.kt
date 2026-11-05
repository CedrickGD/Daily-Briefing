package com.cedrickgd.morgenbriefing.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.cedrickgd.morgenbriefing.i18n.ShellStrings
import com.cedrickgd.morgenbriefing.ui.glass.PageSpec

/**
 * The two pages of the main pager, in swipe order (docs/DESIGN.md §3):
 * **Heute** → Einstellungen. [index] is the pager page; the names come from [ShellStrings].
 */
enum class MainPage(val icon: ImageVector, val link: String) {
    TODAY(Icons.Rounded.WbSunny, AppLinks.PAGE_TODAY),
    SETTINGS(Icons.Rounded.Settings, AppLinks.PAGE_SETTINGS),
    ;

    val index: Int get() = ordinal

    /** The page's name in the interface language (indicator, TalkBack). */
    fun label(strings: ShellStrings): String = when (this) {
        TODAY -> strings.today
        SETTINGS -> strings.settings
    }

    companion object {
        /** The page the app opens on. */
        val Start = TODAY

        /** Specs for the glass page indicator, in pager order, named in [strings]' language. */
        fun specs(strings: ShellStrings): List<PageSpec> = entries.map { PageSpec(it.label(strings), it.icon) }

        fun ofIndex(index: Int): MainPage = entries.getOrElse(index) { Start }

        /** Maps an [AppLinks.EXTRA_OPEN_PAGE] value; unknown or missing values open [Start]. */
        fun fromLink(value: String?): MainPage = entries.firstOrNull { it.link == value } ?: Start

        /**
         * The page a new intent for the running app asks for. Only an intent that carries
         * [AppLinks.EXTRA_OPEN_PAGE] (the notifications) navigates; without it (the launcher
         * icon) null: the app stays where it is.
         */
        fun requestedBy(value: String?): MainPage? = value?.let(::fromLink)
    }
}

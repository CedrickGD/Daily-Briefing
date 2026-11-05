package com.cedrickgd.morgenbriefing.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.LocalAppLanguage
import java.util.Locale

/**
 * An activity whose resources follow the app's interface language instead of the phone's
 * (MainActivity). Dialogs, bottom sheets and popups build their own window from the activity
 * and read its resources, so a composition-level override alone would not reach them.
 */
interface AppLocaleHost {
    fun useAppLocale(locale: Locale)
}

/**
 * Puts [content] into the interface [language]: the app's own texts read it from
 * [LocalAppLanguage], and the Android resources follow it too – Material 3's built-in texts
 * (the time picker's "Hour"/"Minute", the bottom sheet's and dialogs' screen-reader labels) come
 * from androidx string resources in the [Configuration]'s locale, which would otherwise stay the
 * phone's language inside an English app on a German phone.
 *
 * The [AppLocaleHost] activity switches its resources (that covers every dialog window); this
 * composition additionally gets a [LocalConfiguration] in the language, so texts already on
 * screen are read again when the language changes, and a [LocalContext] with localized
 * resources that wraps the activity (everything that looks for the activity keeps working).
 * No activity restart, no system per-app locale that would make "follow the phone" ambiguous.
 */
@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val activity = LocalActivity.current
    // In composition, before any child reads a resource.
    remember(activity, language) { (activity as? AppLocaleHost)?.useAppLocale(language.locale) }
    val context = LocalContext.current
    val base = LocalConfiguration.current
    val configuration = remember(base, language) { base.withLocale(language.locale) }
    val localized = remember(context, configuration) { LocalizedContext(context, configuration) }
    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalConfiguration provides configuration,
        LocalContext provides localized,
        content = content,
    )
}

/** A copy of this configuration that speaks [locale] (screen, density, night mode unchanged). */
internal fun Configuration.withLocale(locale: Locale): Configuration = Configuration(this).apply {
    setLocale(locale)
    setLayoutDirection(locale)
}

/** The context with resources in [configuration]'s locale. */
private class LocalizedContext(base: Context, configuration: Configuration) : ContextWrapper(base) {
    private val localizedResources: Resources = base.createConfigurationContext(configuration).resources

    override fun getResources(): Resources = localizedResources
}

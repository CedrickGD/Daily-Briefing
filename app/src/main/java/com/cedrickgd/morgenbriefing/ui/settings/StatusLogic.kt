package com.cedrickgd.morgenbriefing.ui.settings

import androidx.compose.runtime.Immutable
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.SettingsStrings
import com.cedrickgd.morgenbriefing.util.SystemStatus

/*
 * Pure logic behind the status hero of Settings (docs/DESIGN.md §7, Einstellungen 1):
 * which requirements are met and what the hero says. No Android types, unit-tested.
 */

/** One requirement shown as a pill in the status hero. */
enum class StatusKind { NOTIFICATIONS, EXACT_ALARM, BATTERY }

/** Green (fine), amber (needs a tap) or neutral (optional / still checking). */
enum class StatusLevel { OK, ATTENTION, NEUTRAL }

@Immutable
data class StatusItem(
    val kind: StatusKind,
    val level: StatusLevel,
    /** Pill text, e.g. "Benachrichtigungen". */
    val label: String,
    /** Spoken state, e.g. "erlaubt" / "nicht erlaubt". */
    val state: String,
)

/** The hero's big line and the friendly sentence below it. */
@Immutable
data class HeroText(val title: String, val subtitle: String, val ready: Boolean)

/** The pills of the status hero, in display order, worded in [lang]. */
internal fun statusItems(system: SystemStatus, lang: AppLanguage): List<StatusItem> {
    val s = SettingsStrings.of(lang)
    // Below Android 12 every alarm may be exact, so the pill is simply green there.
    val exact = !system.exactAlarmRelevant || system.exactAlarmsAllowed
    return listOf(
        StatusItem(
            kind = StatusKind.NOTIFICATIONS,
            level = if (system.canNotify) StatusLevel.OK else StatusLevel.ATTENTION,
            label = s.statusNotifications,
            state = when {
                system.canNotify -> s.stateAllowed
                system.permissionGranted && system.appNotificationsEnabled -> s.stateChannelOff
                else -> s.stateNotAllowed
            },
        ),
        StatusItem(
            kind = StatusKind.EXACT_ALARM,
            level = if (exact) StatusLevel.OK else StatusLevel.ATTENTION,
            label = s.statusExactAlarm,
            state = if (exact) s.stateAllowed else s.stateNotAllowed,
        ),
        StatusItem(
            kind = StatusKind.BATTERY,
            level = if (system.batteryUnrestricted) StatusLevel.OK else StatusLevel.ATTENTION,
            label = s.statusBattery,
            state = if (system.batteryUnrestricted) s.stateUnrestricted else s.stateOptimized,
        ),
    )
}

/**
 * What the hero says in [lang]. [day] is "morgen"/"heute"/… ([dayWord]), [time] "05:30". A
 * blocked notification is the worst case; the sentence counts every yellow pill it asks to tap.
 */
internal fun heroText(enabled: Boolean, items: List<StatusItem>, day: String, time: String, lang: AppLanguage): HeroText {
    val s = SettingsStrings.of(lang)
    if (!enabled) return HeroText(title = s.heroOffTitle, subtitle = s.heroOffSubtitle(time), ready = false)
    val problems = items.filter { it.level == StatusLevel.ATTENTION }
    if (problems.isEmpty()) return HeroText(title = s.heroReadyTitle(day, time), subtitle = s.heroReadySubtitle, ready = true)
    val title = if (problems.any { it.kind == StatusKind.NOTIFICATIONS }) s.heroNothingArrives else s.heroAlmostReady(day, time)
    val subtitle = if (problems.size == 1) {
        when (problems.single().kind) {
            StatusKind.NOTIFICATIONS -> s.hintNotifications
            StatusKind.EXACT_ALARM -> s.hintExactAlarm
            StatusKind.BATTERY -> s.hintBattery
        }
    } else {
        s.heroProblems(problems.size)
    }
    return HeroText(title = title, subtitle = subtitle, ready = false)
}

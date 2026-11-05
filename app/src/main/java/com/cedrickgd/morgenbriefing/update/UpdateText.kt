package com.cedrickgd.morgenbriefing.update

import com.cedrickgd.morgenbriefing.domain.Fmt
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.FormatStrings
import com.cedrickgd.morgenbriefing.i18n.UpdateStrings
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Texts of the updater (settings row and notification) in the interface language. Pure, unit-tested. */
object UpdateText {

    /** Friendly explanation for [error] in [lang]; [debugBuild] tailors the wrong-package case. */
    fun errorMessage(error: UpdateError, lang: AppLanguage, debugBuild: Boolean = false): String =
        UpdateStrings.of(lang).error(error, debugBuild)

    /**
     * A short plain-text excerpt of GitHub release notes: markdown headings, emphasis, links and
     * the auto-generated "Full Changelog" line are stripped, bullets become "•". Ends with "…"
     * when something was cut.
     */
    fun notesExcerpt(notes: String, maxLines: Int = 6, maxChars: Int = 320): String {
        val lines = notes.lineSequence()
            .map(::plainLine)
            .filter { it.isNotEmpty() && !it.startsWith("Full Changelog", ignoreCase = true) }
            .toList()
        if (lines.isEmpty()) return ""
        var truncated = lines.size > maxLines
        var text = lines.take(maxLines).joinToString("\n")
        if (text.length > maxChars) {
            val cut = text.lastIndexOfAny(charArrayOf(' ', '\n'), startIndex = maxChars).takeIf { it > maxChars / 2 } ?: maxChars
            text = text.substring(0, cut).trimEnd(' ', '\n', ',', ';', ':', '–', '-')
            truncated = true
        }
        return if (truncated) "$text …" else text
    }

    private val LINK = Regex("""!?\[([^\]]*)]\([^)]*\)""")
    private val HEADING = Regex("""^#{1,6}\s+""")
    private val BULLET = Regex("""^[-*+]\s+""")
    private val RULE = Regex("""^([-*_])\1{2,}$""")

    private fun plainLine(raw: String): String {
        var line = raw.trim()
        if (RULE.matches(line)) return ""
        line = HEADING.replace(line, "")
        line = LINK.replace(line) { it.groupValues[1] }
        line = line.replace("**", "").replace("__", "").replace("`", "")
        line = BULLET.replace(line, "• ")
        return line.trim()
    }

    /** "12,3 MB" / "12.3 MB" (decimal megabytes, the language's separator). */
    fun megabytes(bytes: Long, lang: AppLanguage): String {
        val mb = (bytes.coerceAtLeast(0) / 1_000_000.0).coerceAtLeast(if (bytes > 0) 0.1 else 0.0)
        return "${Fmt.decimal(mb, 1, lang)} MB"
    }

    /** "42 %" / "42%" — rounded down, so 100 % only shows when everything arrived. */
    fun percent(fraction: Float, lang: AppLanguage): String = Fmt.percent((fraction.coerceIn(0f, 1f) * 100).toInt(), lang)

    /** "Zuletzt geprüft: heute um 14:32" etc.; "Noch nicht geprüft" for null. */
    fun lastCheck(now: ZonedDateTime, checkedAt: ZonedDateTime?, lang: AppLanguage): String {
        val strings = UpdateStrings.of(lang)
        if (checkedAt == null) return strings.neverChecked
        val at = checkedAt.withZoneSameInstant(now.zone)
        val ago = Duration.between(at, now)
        if (!ago.isNegative && ago < Duration.ofMinutes(1)) return strings.checkedJustNow
        val time = String.format(Locale.ROOT, "%02d:%02d", at.hour, at.minute)
        val format = FormatStrings.of(lang)
        val day = when (ChronoUnit.DAYS.between(at.toLocalDate(), now.toLocalDate())) {
            0L -> format.today
            1L -> format.yesterday
            else -> Fmt.shortDate(at.toLocalDate(), lang)
        }
        return strings.checkedAt(day, time)
    }
}

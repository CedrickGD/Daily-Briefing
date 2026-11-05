package com.cedrickgd.morgenbriefing.update

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cedrickgd.morgenbriefing.R
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import com.cedrickgd.morgenbriefing.i18n.UpdateStrings
import com.cedrickgd.morgenbriefing.notify.Notifications
import com.cedrickgd.morgenbriefing.ui.shell.AppLinks

/** The "Update verfügbar" notification on its own channel [CHANNEL_UPDATES]. */
object UpdateNotifications {
    const val CHANNEL_UPDATES = "updates"
    const val ID_UPDATE = 540

    private const val TAG = "UpdateNotifications"

    /** Idempotent; no-op below API 26. Name and description from the resources (phone language). */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannelCompat.Builder(CHANNEL_UPDATES, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.channel_updates_name))
            .setDescription(context.getString(R.string.channel_updates_desc))
            .setShowBadge(true)
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** "Update verfügbar: Daily Briefing 1.2.0" in [lang]; tapping opens the app. Skips when blocked. */
    fun showAvailable(context: Context, release: ReleaseInfo, lang: AppLanguage) {
        if (!Notifications.canPost(context)) {
            Log.i(TAG, "Notifications are blocked, not announcing ${release.tag}")
            return
        }
        val strings = UpdateStrings.of(lang)
        val excerpt = UpdateText.notesExcerpt(release.notes, maxLines = 4, maxChars = 220)
        val text = strings.availableText
        val builder = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_sun)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(strings.availableTitle(release.version))
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (excerpt.isEmpty()) text else "$excerpt\n\n$text",
                ),
            )
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        openAppIntent(context)?.let(builder::setContentIntent)
        post(context, builder)
    }

    /** Removes the notification (e.g. once the update is installed or the user started it). */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_UPDATE)
    }

    /** Opens the app on the "Einstellungen" page, where the update card lives. */
    private fun openAppIntent(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launch.putExtra(AppLinks.EXTRA_OPEN_PAGE, AppLinks.PAGE_SETTINGS)
        return PendingIntent.getActivity(
            context,
            ID_UPDATE,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Callers check [Notifications.canPost] first; the catch covers a revocation race. */
    @SuppressLint("MissingPermission")
    private fun post(context: Context, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(ID_UPDATE, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to post the update notification", e)
        }
    }
}

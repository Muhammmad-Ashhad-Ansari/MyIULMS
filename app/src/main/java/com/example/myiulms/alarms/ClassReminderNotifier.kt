package com.example.myiulms.alarms

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myiulms.MainActivity
import com.example.myiulms.R
import com.example.myiulms.WeeklyScheduleEntry
import com.example.myiulms.ui.dashboard.formatRoomLabel
import com.example.myiulms.ui.widget.EXTRA_OPEN_TAB
import com.example.myiulms.ui.widget.SCHEDULE_TAB

/**
 * The "Class Reminders" channel and the class notification. A class keeps ONE
 * notification for its whole lifecycle (same id): the reminder is replaced by
 * "started", which is replaced by "ended · Next: …", so the shade never piles up.
 */
object ClassReminderNotifier {
    private const val CHANNEL_ID = "class_reminders"
    private const val MINUTE_MILLIS = 60_000L
    /** How long the end-of-class note stays before clearing itself. */
    private const val END_NOTE_LIFETIME_MILLIS = 30 * MINUTE_MILLIS

    fun ensureChannel(context: Context) {
        // HIGH = heads-up pop-up. Android fixes a channel's importance when it is
        // first created; raising it later needs a new channel id.
        val channel = NotificationChannel(CHANNEL_ID, "Class Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Before, at the start and at the end of each class"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // Guarded by ClassAlertPermissions.canNotify.
    fun show(context: Context, event: ScheduleEvent, entries: List<WeeklyScheduleEntry>, nowMillis: Long) {
        val occurrence = event.occurrence ?: return
        if (!ClassAlertPermissions.canNotify(context)) return
        ensureChannel(context)

        val entry = occurrence.entry
        val course = entry.courseTitle.ifBlank { entry.courseCode }.ifBlank { "Class" }
        val room = formatRoomLabel(entry.location)
        val start = formatClassClock(occurrence.startMillis)
        val end = formatClassClock(occurrence.endMillis)

        // Minutes are counted from now, so a slightly late alarm never overstates them.
        val (title, text, lifetime) = when (event.type) {
            ScheduleEventType.Reminder -> {
                val minutes = ((occurrence.startMillis - nowMillis + MINUTE_MILLIS - 1) / MINUTE_MILLIS).coerceAtLeast(0)
                Triple(
                    if (minutes == 0L) "$course · starting now" else "$course · starts in $minutes min",
                    listOfNotNull("$start – $end", room).joinToString(" · "),
                    occurrence.endMillis - nowMillis
                )
            }
            ScheduleEventType.ClassStart -> Triple(
                "$course · started",
                listOfNotNull("Until $end", room).joinToString(" · "),
                occurrence.endMillis - nowMillis
            )
            ScheduleEventType.ClassEnd -> Triple(
                "$course · ended",
                nextClassMessage(entries, occurrence),
                END_NOTE_LIFETIME_MILLIS
            )
            ScheduleEventType.DayChange -> return
        }

        val openSchedule = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TAB, SCHEDULE_TAB)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // Lock screen shows only this; course and room stay private.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_class)
            .setContentTitle("Class update")
            .build()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_class)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openSchedule)
            .setAutoCancel(true)
            .setTimeoutAfter(lifetime.coerceAtLeast(MINUTE_MILLIS))
            .build()

        NotificationManagerCompat.from(context).notify(occurrence.startMillis.hashCode(), notification)
    }
}

package com.example.myiulms.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.myiulms.appContainer
import com.example.myiulms.ui.widget.NextClassWidgetReceiver
import com.example.myiulms.ui.widget.NextClassWidgetSession

/**
 * Keeps exactly one alarm armed and hops it event to event: reminder → class
 * start → (class end, widget only) → next class (see [nextScheduleEvent]). Each firing re-arms
 * the following one in [WidgetUpdateReceiver]; anything that changes the inputs
 * calls [reschedule].
 */
object WidgetAlarmScheduler {
    const val ACTION_SCHEDULE_TICK = "com.example.myiulms.action.SCHEDULE_TICK"

    // Fallback when exact alarms are not allowed: Android may deliver within this window.
    private const val INEXACT_WINDOW_MILLIS = 10 * 60_000L
    // Safety-net: if the schedule is temporarily unavailable, re-arm in this many ms so the
    // chain stays alive instead of permanently dead.
    private const val SAFETY_NET_MILLIS = 15 * 60_000L

    /**
     * Re-arms (or cancels) the alarm from the current cache, sign-in state,
     * widgets on screen and reminder setting. Safe to call often.
     */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(AlarmManager::class.java)
        val pendingIntent = tickIntent(app)

        val signedIn = NextClassWidgetSession.signedIn(app).value
        val widgetEvents = hasWidgets(app)
        val reminders = ReminderSettings.isEnabled(app)
        val entries = if (signedIn && (widgetEvents || reminders)) {
            app.appContainer.portal.cachedWeeklySchedule()?.payload?.entries
        } else {
            null
        }
        val event = entries?.let {
            nextScheduleEvent(it, System.currentTimeMillis(), widgetEvents, reminders, ReminderSettings.leadMinutes(app))
        }

        // Safety-net: if the schedule is temporarily unavailable, re-arm in
        // SAFETY_NET_MILLIS instead of permanently cancelling. This prevents a cache miss,
        // sync delay, or brief network outage from killing the whole reminder chain.
        val safetyPending = safetyNetPendingIntent(app, alarmManager, pendingIntent)

        if (event == null) {
            // Cancel the exact alarm but keep a safety-net alive so the chain doesn't die.
            alarmManager.cancel(pendingIntent)
            if (reminders) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + SAFETY_NET_MILLIS, safetyPending)
            }
            return
        }

        // With reminders on, every hop wakes the phone, including the silent
        // class-end refresh when a widget exists: a sleeping phone must never
        // hold back the chain past the next reminder. Widget-only (reminders off)
        // can wait until the screen is next on, when anyone sees it.
        val wakeUp = reminders
        val type = if (wakeUp) AlarmManager.RTC_WAKEUP else AlarmManager.RTC
        if (ClassAlertPermissions.canScheduleExact(app)) {
            if (wakeUp) {
                alarmManager.setExactAndAllowWhileIdle(type, event.atMillis, pendingIntent)
            } else {
                alarmManager.setExact(type, event.atMillis, pendingIntent)
            }
        } else if (wakeUp) {
            alarmManager.setAndAllowWhileIdle(type, event.atMillis, pendingIntent)
        } else {
            alarmManager.setWindow(type, event.atMillis, INEXACT_WINDOW_MILLIS, pendingIntent)
        }
        // Also keep the safety-net armed concurrently so a late-arriving cache refresh
        // can override it, but if the exact alarm expires first the safety-net still fires.
        if (reminders && wakeUp) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + SAFETY_NET_MILLIS, safetyPending)
        }
    }

    private fun safetyNetPendingIntent(app: Context, alarmManager: AlarmManager,
                                       basePending: PendingIntent): PendingIntent {
        // Re-use the same base action but with a different request code so it can be
        // independently cancelled/overridden if the exact alarm is set later.
        return PendingIntent.getBroadcast(
            app,
            99,  // distinct request code for safety-net
            Intent(app, WidgetUpdateReceiver::class.java).setAction(ACTION_SCHEDULE_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun hasWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, NextClassWidgetReceiver::class.java))
            .isNotEmpty()

    private fun tickIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetUpdateReceiver::class.java).setAction(ACTION_SCHEDULE_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}

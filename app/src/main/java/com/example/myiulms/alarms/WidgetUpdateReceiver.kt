package com.example.myiulms.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.util.Log
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.example.myiulms.appContainer
import com.example.myiulms.ui.widget.NextClassRefresh
import com.example.myiulms.ui.widget.NextClassWidget
import com.example.myiulms.ui.widget.NextClassWidgetSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Handles the schedule alarm and the system events that wipe or invalidate it
 * (reboot, clock or time-zone change, app update, exact-alarm access granted).
 * Every case does the same three things: refresh the widget, post the class
 * notifications that are due (reminder, started; never class end), and arm the
 * next alarm.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                handle(app)
            } catch (e: Exception) {
                Log.w("ClassAlerts", "WidgetUpdateReceiver.handle failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context) {
        // Tick the refresh count first; this never throws.
        NextClassRefresh.ticks.update { it + 1 }
        NextClassWidget().updateAll(context)

        // Post notifications that are due — guard each step so a single failure
        // never silently kills the rest.
        if (!ReminderSettings.isEnabled(context)) {
            Log.d("ClassAlerts", "Reminders disabled, skipping postDueNotifications")
        } else {
            val signedIn = NextClassWidgetSession.signedIn(context).value
            if (!signedIn) {
                Log.d("ClassAlerts", "User not signed in, skipping postDueNotifications")
            } else {
                val entries = context.appContainer.portal.cachedWeeklySchedule()?.payload?.entries
                if (entries == null) {
                    Log.d("ClassAlerts", "No cached weekly schedule entries, skipping postDueNotifications")
                } else {
                    val now = System.currentTimeMillis()
                    val handledUntil = ReminderSettings.handledUntil(context)
                    val lead = ReminderSettings.leadMinutes(context)
                    dueNotifications(entries, now, handledUntil, lead)
                        .forEach { ClassReminderNotifier.show(context, it, now) }
                    ReminderSettings.setHandledUntil(context, now)
                }
            }
        }

        WidgetAlarmScheduler.reschedule(context)
    }

    // Which stage fired is worked out from the schedule, not the intent: one
    // firing can cover several (one class starting as the next one's reminder
    // is due), and a delayed alarm still lands on the right stage.
    private suspend fun postDueNotifications(context: Context) {
        if (!ReminderSettings.isEnabled(context)) return
        if (!NextClassWidgetSession.signedIn(context).value) return
        val entries = context.appContainer.portal.cachedWeeklySchedule()?.payload?.entries ?: return
        val now = System.currentTimeMillis()
        dueNotifications(entries, now, ReminderSettings.handledUntil(context), ReminderSettings.leadMinutes(context))
            .forEach { ClassReminderNotifier.show(context, it, now) }
        ReminderSettings.setHandledUntil(context, now)
    }

    private companion object {
        // Outlives the receiver call; goAsync keeps the process alive until finish().
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

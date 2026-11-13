package com.example.myiulms.alarms

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.example.myiulms.appContainer
import com.example.myiulms.ui.widget.NextClassRefresh
import com.example.myiulms.ui.widget.NextClassWidget
import com.example.myiulms.ui.widget.NextClassWidgetSession
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One pass of the class-alert pipeline: refresh the widget, post the stages
 * that are due, remember how far they were handled, and re-arm the next alarm.
 *
 * Shared by [WidgetUpdateReceiver] (alarm and system events) and
 * [ClassReminderWorker] (the WorkManager safety net). The mutex serializes the
 * two, and [dueNotifications] only returns stages after the handled mark, so
 * the same stage can never be posted twice whichever of them arrives first.
 *
 * Each step is guarded on its own: a missing widget host or an unreadable
 * cache must never stop the notification or the next alarm.
 */
object ClassAlertCycle {
    private val mutex = Mutex()

    suspend fun run(context: Context, source: String) {
        mutex.withLock {
            val app = context.applicationContext
            try {
                NextClassRefresh.ticks.update { it + 1 }
                NextClassWidget().updateAll(app)
            } catch (e: Exception) {
                Log.w("ClassAlerts", "$source: widget refresh failed", e)
            }
            try {
                postDue(app)
            } catch (e: Exception) {
                Log.w("ClassAlerts", "$source: posting due notifications failed", e)
            }
            try {
                WidgetAlarmScheduler.reschedule(app)
            } catch (e: Exception) {
                Log.w("ClassAlerts", "$source: alarm reschedule failed", e)
            }
        }
    }

    /**
     * Posts every stage that came due since the last pass. Does nothing when
     * the schedule cannot be known (reminders off, signed out, no cached
     * schedule); the mark moves only after the due stages were handed over.
     */
    private suspend fun postDue(context: Context) {
        if (!ReminderSettings.isEnabled(context)) return
        if (!NextClassWidgetSession.signedIn(context).value) return
        val entries = context.appContainer.portal.cachedWeeklySchedule()?.payload?.entries ?: return
        val now = System.currentTimeMillis()
        dueNotifications(entries, now, ReminderSettings.handledUntil(context), ReminderSettings.leadMinutes(context))
            .forEach { ClassReminderNotifier.show(context, it, now) }
        ReminderSettings.setHandledUntil(context, now)
    }
}

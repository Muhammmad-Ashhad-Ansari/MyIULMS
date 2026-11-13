package com.example.myiulms.alarms

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myiulms.appContainer
import com.example.myiulms.ui.widget.NextClassWidgetSession
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The safety net behind the alarm chain. Alarms are the fast path, but OEM task
 * killers (Vivo Funtouch, MIUI/HyperOS, ColorOS) and some battery savers cancel
 * them and swallow the re-arming broadcast, leaving reminders silent forever.
 * JobScheduler-backed WorkManager work is re-registered after a reboot and is
 * harder to starve, so one killed chain costs one interval, not every class.
 *
 * A run is the same [ClassAlertCycle] pass as the alarm: it posts whatever came
 * due since the last pass and re-arms the alarm, which also revives the chain.
 */
class ClassReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        // sync() cancels this work when the state changes, but it may have been
        // enqueued before that. Re-check first, and read the cache only when the
        // pass could run: without one (signed in without "Remember me", or the
        // timetable not synced yet) there is nothing to post or arm, so finish
        // successfully instead of running an empty cycle.
        val enabled = ReminderSettings.isEnabled(app)
        val signedIn = NextClassWidgetSession.signedIn(app).value
        val scheduleCached = ClassAlertGuidance.shouldKeepFallback(enabled, signedIn) &&
            app.appContainer.portal.cachedWeeklySchedule() != null
        if (!ClassAlertGuidance.canRunPass(enabled, signedIn, scheduleCached)) return Result.success()
        ClassAlertCycle.run(app, "worker")
        return Result.success()
    }
}

/**
 * Keeps the periodic safety net in step with the current state: enqueued while
 * reminders are on and a student is signed in, cancelled otherwise. Safe to
 * call often; [ExistingPeriodicWorkPolicy.UPDATE] keeps the existing schedule.
 */
object ClassAlertWork {
    private const val NAME = "class-reminder-safety-net"

    // WorkManager's minimum periodic interval; matches the alarm chain's own
    // 15-minute safety net so a dead chain is noticed within one hop.
    private const val INTERVAL_MINUTES = 15L

    // Process-wide, like the widget updates; the first call opens WorkManager's
    // database, which must not happen on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun sync(context: Context) {
        val app = context.applicationContext
        // Everything (including the first preference read) stays off the main
        // thread, so a cold start that calls this pays nothing.
        scope.launch {
            try {
                val wanted = ClassAlertGuidance.shouldKeepFallback(
                    ReminderSettings.isEnabled(app),
                    NextClassWidgetSession.signedIn(app).value
                )
                val workManager = WorkManager.getInstance(app)
                if (!wanted) {
                    workManager.cancelUniqueWork(NAME)
                    return@launch
                }
                workManager.enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<ClassReminderWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES).build()
                )
            } catch (e: Exception) {
                Log.w("ClassAlerts", "ClassAlertWork.sync failed", e)
            }
        }
    }
}

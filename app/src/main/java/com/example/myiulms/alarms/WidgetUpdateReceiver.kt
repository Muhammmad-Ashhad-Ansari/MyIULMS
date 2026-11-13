package com.example.myiulms.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the schedule alarm and the system events that wipe or invalidate it
 * (reboot, clock or time-zone change, app update, exact-alarm access granted).
 * All of them run the same [ClassAlertCycle] pass. Which stage fired is worked
 * out from the schedule, not the intent: one firing can cover several stages
 * (a class starting as the next one's reminder is due), and a delayed alarm
 * still lands on the right stage.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        // goAsync keeps the process alive until finish().
        val pending = goAsync()
        scope.launch {
            try {
                ClassAlertCycle.run(app, "alarm")
            } catch (e: Exception) {
                Log.w("ClassAlerts", "WidgetUpdateReceiver failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        // Outlives the receiver call; goAsync keeps the process alive until finish().
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

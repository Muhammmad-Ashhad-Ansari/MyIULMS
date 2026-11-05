package com.example.myiulms.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myiulms.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY (src/debug). Prints the cached weekly schedule's identifiers
 * (day, title, EDP code, course code) so they can be compared with attendance
 * course names. Reads the app's own encrypted cache; guarded by DUMP (adb only):
 *
 *   adb shell am broadcast -a com.example.myiulms.debug.DUMP_SCHEDULE \
 *       -n com.example.myiulms/.debug.DebugScheduleDumpReceiver
 */
class DebugScheduleDumpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entries = context.appContainer.portal.cachedWeeklySchedule()?.payload?.entries
                pending.resultData = entries?.joinToString(" | ") {
                    "${it.day};${it.courseTitle};edp=${it.edpCode};code=${it.courseCode}"
                } ?: "no cached schedule"
            } finally {
                pending.finish()
            }
        }
    }
}

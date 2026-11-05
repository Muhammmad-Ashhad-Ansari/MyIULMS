package com.example.myiulms.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myiulms.WeeklyScheduleEntry
import com.example.myiulms.alarms.ClassOccurrence
import com.example.myiulms.alarms.ClassReminderNotifier
import com.example.myiulms.alarms.ReminderSettings
import com.example.myiulms.alarms.ScheduleEvent
import com.example.myiulms.alarms.ScheduleEventType

/**
 * DEBUG BUILDS ONLY (src/debug). Posts a real class notification through
 * [ClassReminderNotifier] for a made-up class, so the icon, channel and text can
 * be checked without waiting for a real class. Guarded by the DUMP permission,
 * which only adb (shell) and the system hold:
 *
 *   adb shell am broadcast -a com.example.myiulms.debug.TEST_REMINDER \
 *       -n com.example.myiulms/.debug.DebugReminderReceiver --es stage start
 *
 * stage = reminder (default) | start
 */
class DebugReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val lead = ReminderSettings.leadMinutes(context)
        val isStart = intent.getStringExtra("stage") == "start"
        // A reminder is for a class starting `lead` minutes from now; a start is now.
        val start = if (isStart) now else now + lead * 60_000L
        val entry = WeeklyScheduleEntry(
            day = "",
            time = "",
            courseTitle = "TEST CLASS",
            faculty = "",
            location = "E-000",
            edpCode = "",
            courseCode = ""
        )
        val occurrence = ClassOccurrence(entry, startMillis = start, endMillis = start + 80 * 60_000L)
        val type = if (isStart) ScheduleEventType.ClassStart else ScheduleEventType.Reminder
        ClassReminderNotifier.show(context, ScheduleEvent(now, type, occurrence), now)
        resultData = "posted ${type.name} (lead $lead min)"
    }
}

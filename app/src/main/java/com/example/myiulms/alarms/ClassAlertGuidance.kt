package com.example.myiulms.alarms

/**
 * What still stands between the student and on-time class reminders, in the
 * order the tip banner should offer it. Pure, so the priority is JVM-tested.
 */
enum class ClassAlertBlocker {
    /** Notifications are refused or switched off: nothing can be shown. */
    Notifications,

    /** Exact alarms are not allowed: reminders can be minutes late. */
    ExactAlarms,

    /** The battery saver may freeze the app between alarms (typical on OEM phones). */
    BatteryOptimizations
}

object ClassAlertGuidance {
    /**
     * The one system blocker worth showing now, or null when reminders are off
     * or nothing is missing. Only one card is shown at a time: after the student
     * resolves it, the next blocker (if any) takes its place.
     *
     * "Remember me" is not part of this list: it is a prerequisite of the
     * reminders toggle in the overflow menu, not a permission to nag about over
     * the timetable.
     */
    fun blocker(
        remindersEnabled: Boolean,
        notificationsAllowed: Boolean,
        exactAlarmsAllowed: Boolean,
        batteryUnrestricted: Boolean
    ): ClassAlertBlocker? = when {
        !remindersEnabled -> null
        !notificationsAllowed -> ClassAlertBlocker.Notifications
        !exactAlarmsAllowed -> ClassAlertBlocker.ExactAlarms
        !batteryUnrestricted -> ClassAlertBlocker.BatteryOptimizations
        else -> null
    }

    /**
     * Whether the periodic WorkManager fallback should exist at all. Without a
     * signed-in student or with reminders off there is no schedule to check and
     * nothing to post, so the work is cancelled instead of waking the app.
     */
    fun shouldKeepFallback(remindersEnabled: Boolean, signedIn: Boolean): Boolean =
        remindersEnabled && signedIn

    /**
     * Whether a fallback pass can accomplish anything. With no cached timetable
     * (signed in without "Remember me", or not synced yet) the worker would
     * read an empty cache and re-arm an alarm with nothing to fire, so it
     * finishes successfully without running the cycle.
     */
    fun canRunPass(remindersEnabled: Boolean, signedIn: Boolean, scheduleCached: Boolean): Boolean =
        shouldKeepFallback(remindersEnabled, signedIn) && scheduleCached
}

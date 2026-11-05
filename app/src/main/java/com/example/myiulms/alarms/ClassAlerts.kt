package com.example.myiulms.alarms

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Class-reminder preferences: whether reminders are on (default: on; the
 * overflow-menu switch turns them off), whether the permission banner was
 * dismissed, and when the receiver last posted notifications. No personal
 * data, so it can follow normal backup rules.
 */
object ReminderSettings {
    private const val PREFS = "iulms_reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BANNER_DISMISSED = "banner_dismissed"
    private const val KEY_HANDLED_UNTIL = "handled_until"
    private const val KEY_LEAD_MINUTES = "lead_minutes"
    private const val DEFAULT_ENABLED = true

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var enabledState: MutableStateFlow<Boolean>? = null

    private fun enabledFlow(context: Context): MutableStateFlow<Boolean> =
        enabledState ?: synchronized(this) {
            enabledState ?: MutableStateFlow(prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED))
                .also { enabledState = it }
        }

    fun isEnabled(context: Context): Boolean = enabledFlow(context).value

    /** For the switch and the banner, so both follow a change made in either. */
    fun enabled(context: Context): StateFlow<Boolean> = enabledFlow(context).asStateFlow()

    // Turning on starts from now, so nothing that already happened is announced.
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(KEY_ENABLED, enabled)
            if (enabled) putLong(KEY_HANDLED_UNTIL, System.currentTimeMillis())
        }
        enabledFlow(context).value = enabled
    }

    @Volatile
    private var leadState: MutableStateFlow<Long>? = null

    private fun leadFlow(context: Context): MutableStateFlow<Long> =
        leadState ?: synchronized(this) {
            leadState ?: MutableStateFlow(
                prefs(context).getLong(KEY_LEAD_MINUTES, DEFAULT_REMINDER_LEAD_MINUTES)
                    .takeIf { it in REMINDER_LEAD_OPTIONS } ?: DEFAULT_REMINDER_LEAD_MINUTES
            ).also { leadState = it }
        }

    /** Minutes before class that the reminder fires; always one of [REMINDER_LEAD_OPTIONS]. */
    fun leadMinutes(context: Context): Long = leadFlow(context).value

    fun leadMinutesFlow(context: Context): StateFlow<Long> = leadFlow(context).asStateFlow()

    fun setLeadMinutes(context: Context, minutes: Long) {
        require(minutes in REMINDER_LEAD_OPTIONS) { "Unsupported reminder lead time: $minutes" }
        prefs(context).edit { putLong(KEY_LEAD_MINUTES, minutes) }
        leadFlow(context).value = minutes
    }

    fun isBannerDismissed(context: Context): Boolean = prefs(context).getBoolean(KEY_BANNER_DISMISSED, false)

    fun dismissBanner(context: Context) = prefs(context).edit { putBoolean(KEY_BANNER_DISMISSED, true) }

    /** Notifications for stages at or before this were already handled. */
    fun handledUntil(context: Context): Long = prefs(context).getLong(KEY_HANDLED_UNTIL, 0L)

    fun setHandledUntil(context: Context, millis: Long) =
        prefs(context).edit { putLong(KEY_HANDLED_UNTIL, millis) }
}

/** What is still missing for exact widget timing and reminders on this device. */
object ClassAlertPermissions {
    /** POST_NOTIFICATIONS is a runtime permission only from Android 13. */
    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /** Notifications can be blocked in Settings even when the permission is granted. */
    fun canNotify(context: Context): Boolean =
        !needsNotificationPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Exact alarms are special app access from Android 12; Android 13+ leaves it
     * off for new installs, so the student grants it in Settings.
     */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}

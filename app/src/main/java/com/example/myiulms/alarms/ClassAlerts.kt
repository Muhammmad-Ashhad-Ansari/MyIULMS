package com.example.myiulms.alarms

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * Class-reminder preferences: whether the student turned reminders on, whether
 * the banner was dismissed, and when the receiver last posted notifications.
 * No personal data, so it can follow normal backup rules.
 */
object ReminderSettings {
    private const val PREFS = "iulms_reminders"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_BANNER_DISMISSED = "banner_dismissed"
    private const val KEY_HANDLED_UNTIL = "handled_until"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    // Turning on starts from now, so nothing that already happened is announced.
    fun setEnabled(context: Context, enabled: Boolean) = prefs(context).edit {
        putBoolean(KEY_ENABLED, enabled)
        if (enabled) putLong(KEY_HANDLED_UNTIL, System.currentTimeMillis())
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

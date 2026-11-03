package com.example.myiulms.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myiulms.alarms.ClassAlertPermissions
import com.example.myiulms.alarms.ReminderSettings
import com.example.myiulms.alarms.WidgetAlarmScheduler
import com.example.myiulms.ui.theme.AppRadius
import com.example.myiulms.ui.theme.AppSpacing
import kotlinx.coroutines.launch

/** Snapshot of what the banner needs; re-read on every resume (Settings may have changed it). */
private data class ClassAlertsState(
    val enabled: Boolean,
    val dismissed: Boolean,
    val missingNotifications: Boolean,
    val missingExactAlarms: Boolean
) {
    val showBanner: Boolean
        get() = !dismissed && (!enabled || missingNotifications || missingExactAlarms)
}

private fun readState(context: Context) = ClassAlertsState(
    enabled = ReminderSettings.isEnabled(context),
    dismissed = ReminderSettings.isBannerDismissed(context),
    missingNotifications = !ClassAlertPermissions.canNotify(context),
    missingExactAlarms = !ClassAlertPermissions.canScheduleExact(context)
)

/**
 * Opt-in for reminders and exact widget timing. Nothing is asked until the
 * student taps "Turn on"; "Not now" hides it for good (reminders stay off).
 *
 * Flow on "Turn on": enable reminders, then the notification permission
 * (Android 13+; App notification settings if it was already refused), then the
 * exact-alarm access page (Android 12+). Each step is skipped when granted.
 */
@Composable
fun ClassAlertsBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(readState(context)) }

    fun refresh() {
        state = readState(context)
        scope.launch { WidgetAlarmScheduler.reschedule(context) }
    }

    fun openExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
        if (!ClassAlertPermissions.canScheduleExact(context)) openExactAlarmAccess()
    }

    // Returning from a Settings page is the only signal that access changed.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!state.showBanner) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(AppSpacing.Lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                Text(
                    text = "Enable alarms and notifications for real-time widget updates and class reminders.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm, Alignment.End)
            ) {
                TextButton(onClick = {
                    ReminderSettings.dismissBanner(context)
                    refresh()
                }) { Text("Not now") }
                FilledTonalButton(onClick = {
                    ReminderSettings.setEnabled(context, true)
                    when {
                        ClassAlertPermissions.needsNotificationPermission(context) && !state.enabled ->
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        !ClassAlertPermissions.canNotify(context) ->
                            // Already refused once, or blocked in Settings: the system
                            // will not ask again, so open the app's notification page.
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }
                        !ClassAlertPermissions.canScheduleExact(context) -> openExactAlarmAccess()
                    }
                    refresh()
                }) { Text(if (state.enabled) "Fix settings" else "Turn on") }
            }
        }
    }
}

package com.example.myiulms.ui.dashboard

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myiulms.alarms.ClassAlertBlocker
import com.example.myiulms.alarms.ClassAlertGuidance
import com.example.myiulms.alarms.ClassAlertPermissions
import com.example.myiulms.alarms.ClassAlertWork
import com.example.myiulms.alarms.ReminderSettings
import com.example.myiulms.alarms.WidgetAlarmScheduler
import com.example.myiulms.ui.theme.AppRadius
import com.example.myiulms.ui.theme.AppSpacing
import com.example.myiulms.ui.widget.NextClassWidgetReceiver
import kotlinx.coroutines.launch

/**
 * At most one tip card at the top of the Schedule tab, most important first:
 *  1. reminders are on but Android still blocks notifications, exact alarms or
 *     the battery exemption — see [ClassAlertGuidance];
 *  2. the home-screen widget is not pinned yet.
 * Both re-check on every resume, since the answers change in Settings or the
 * launcher's pin dialog, outside the app.
 *
 * A temporary session ("Remember me" off) has no cached timetable, so the
 * alarm tips would have nothing to guarantee: only the widget promo remains.
 * The prerequisite is explained next to the reminders toggle instead.
 */
@Composable
fun ScheduleTips(
    /** Whether the encrypted login (and with it the timetable cache) is kept. */
    rememberMe: Boolean,
    modifier: Modifier = Modifier,
    // The predictive-back preview keeps this banner on screen for the life of
    // the screen instead of only during a gesture, so it must not run the
    // banner's side effects a second time: with interactive = false it skips
    // the resume observer (which re-arms the class alarm) and reads its state
    // once, at composition. Only the visible content is mirrored.
    interactive: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remindersOn by ReminderSettings.enabled(context).collectAsState()
    // Bumped on resume: forces the permission and pinned-widget reads below to re-run.
    var resumeTick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, interactive) {
        if (!interactive) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
                scope.launch { WidgetAlarmScheduler.reschedule(context) }
                // Reminders or the sign-in may have changed in Settings.
                ClassAlertWork.sync(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Only one blocker is offered at a time; resolving it reveals the next.
    // Without "Remember me" there is no cached timetable to keep, so no alarm
    // tip applies: the toggles in the overflow menu carry the prerequisite.
    val blocker = remember(resumeTick, remindersOn, rememberMe) {
        if (!rememberMe) {
            null
        } else {
            ClassAlertGuidance.blocker(
                remindersEnabled = remindersOn,
                notificationsAllowed = ClassAlertPermissions.canNotify(context),
                exactAlarmsAllowed = ClassAlertPermissions.canScheduleExact(context),
                batteryUnrestricted = ClassAlertPermissions.isIgnoringBatteryOptimizations(context)
            )
        }
    }
    var alertsDismissed by remember(resumeTick) { mutableStateOf(ReminderSettings.isBannerDismissed(context)) }
    var widgetPromoHidden by remember(resumeTick) { mutableStateOf(TipPrefs.isWidgetPromoHidden(context)) }

    when {
        blocker != null && !alertsDismissed ->
            ClassAlertsBanner(
                blocker = blocker,
                onDismiss = {
                    ReminderSettings.dismissBanner(context)
                    alertsDismissed = true
                },
                modifier = modifier
            )
        !widgetPromoHidden -> WidgetPromoCard(
            onDismiss = {
                TipPrefs.dismissWidgetPromo(context)
                widgetPromoHidden = true
            },
            modifier = modifier
        )
    }
}

/**
 * Reminders are on by default; this only appears while Android still blocks
 * them: the notification permission (Android 13+; the app's notification page
 * once it was refused), exact-alarm access (Android 12+), or the battery
 * exemption (Doze and OEM task killers). Each card offers the one action that
 * resolves it; the next blocker shows after the student returns. "Not now"
 * hides the permission tips for good. The overflow-menu switch still controls
 * reminders.
 */
@Composable
private fun ClassAlertsBanner(
    blocker: ClassAlertBlocker,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun openNotificationSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    fun openExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    fun openBatterySettings() {
        // The app-specific exemption dialog; OEM builds that lack it fall back
        // to the battery-optimization list.
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        if (runCatching { context.startActivity(direct) }.isFailure) {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    when (blocker) {
        ClassAlertBlocker.Notifications -> TipCard(
            icon = Icons.Rounded.NotificationsActive,
            text = "Allow notifications so class reminders and widget updates can reach you.",
            actionLabel = "Allow",
            onAction = {
                when {
                    ClassAlertPermissions.needsNotificationPermission(context) &&
                        !TipPrefs.askedForNotifications(context) -> {
                        TipPrefs.markAskedForNotifications(context)
                        notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    // Refused once, or blocked in Settings: the system will not
                    // ask again, so open the app's notification page instead.
                    else -> openNotificationSettings()
                }
            },
            onDismiss = onDismiss,
            modifier = modifier
        )
        ClassAlertBlocker.ExactAlarms -> TipCard(
            icon = Icons.Rounded.Alarm,
            text = "Allow exact alarms so class reminders arrive on time. Android grants this separately from notifications.",
            actionLabel = "Allow",
            onAction = { openExactAlarmAccess() },
            onDismiss = onDismiss,
            modifier = modifier
        )
        ClassAlertBlocker.BatteryOptimizations -> TipCard(
            icon = Icons.Rounded.BatteryAlert,
            text = "Some phones pause background apps and silence reminders. Allow auto-start and set battery usage to Unrestricted.",
            actionLabel = "Open settings",
            onAction = { openBatterySettings() },
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
}

/**
 * "Pin your schedule to the home screen": one tap opens the launcher's own
 * add-widget dialog (requestPinAppWidget). Shown only when the launcher supports
 * that and no Next Class widget exists; it hides itself once one is pinned.
 */
@Composable
private fun WidgetPromoCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TipCard(
        icon = Icons.Rounded.Widgets,
        text = "Pin your schedule to the home screen to see your next class without opening the app.",
        actionLabel = "Add widget",
        onAction = {
            val manager = AppWidgetManager.getInstance(context)
            val requested = runCatching {
                manager.requestPinAppWidget(ComponentName(context, NextClassWidgetReceiver::class.java), null, null)
            }.getOrDefault(false)
            // The launcher refused (or cannot pin): don't keep offering a dead button.
            if (!requested) onDismiss()
        },
        onDismiss = onDismiss,
        modifier = modifier
    )
}

@Composable
private fun TipCard(
    icon: ImageVector,
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppRadius.Medium),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(modifier = Modifier.padding(AppSpacing.Lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.Sm)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Md)) {
                Icon(icon, contentDescription = null)
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.Sm, Alignment.End)
            ) {
                if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Not now") }
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/**
 * Tip-card state: the widget promo (hidden once pinned, dismissed, or not
 * supported by the launcher) and whether the notification prompt was shown.
 */
private object TipPrefs {
    private const val PREFS = "iulms_tips"
    private const val KEY_WIDGET_DISMISSED = "widget_promo_dismissed"
    private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isWidgetPromoHidden(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val pinned = manager
            .getAppWidgetIds(ComponentName(context, NextClassWidgetReceiver::class.java))
            .isNotEmpty()
        return pinned || !manager.isRequestPinAppWidgetSupported ||
            prefs(context).getBoolean(KEY_WIDGET_DISMISSED, false)
    }

    fun dismissWidgetPromo(context: Context) = prefs(context).edit { putBoolean(KEY_WIDGET_DISMISSED, true) }

    fun askedForNotifications(context: Context): Boolean = prefs(context).getBoolean(KEY_ASKED_NOTIFICATIONS, false)

    fun markAskedForNotifications(context: Context) = prefs(context).edit { putBoolean(KEY_ASKED_NOTIFICATIONS, true) }
}

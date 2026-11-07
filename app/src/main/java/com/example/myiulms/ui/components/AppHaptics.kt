package com.example.myiulms.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The app's haptics, sent straight to the hosting View with feedback
 * constants that every vibrator supports.
 *
 * Why not Compose's HapticFeedbackType.TextHandleMove / GestureThresholdActivate:
 * Android maps TextHandleMove to the "texture tick" effect, and the gesture
 * threshold constant only exists from Android 14 (older builds fall back to
 * whatever the platform picks). Many vibrators (common on budget and OEM
 * phones) cannot play texture ticks at all, so the call succeeds and nothing
 * is felt. CONTEXT_CLICK (a plain tick) and CONFIRM / VIRTUAL_KEY (a click)
 * map to the basic effects every vibrator implements.
 *
 * FLAG_IGNORE_VIEW_SETTING keeps a view-level haptics switch from silencing
 * them; the phone's own "Touch feedback" setting is still respected.
 */
class AppHaptics(private val view: View) {

    /** Light tick: changing tabs. */
    fun tick() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    /** Firmer click: a pull reaching "release to refresh". */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.VIRTUAL_KEY
    )

    private fun perform(constant: Int) {
        view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) { AppHaptics(view) }
}

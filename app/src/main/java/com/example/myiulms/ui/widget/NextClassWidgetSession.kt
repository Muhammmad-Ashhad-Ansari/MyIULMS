package com.example.myiulms.ui.widget

import android.content.Context
import androidx.core.content.edit
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The widget's view of the sign-in state.
 *
 * Persisted (one boolean, no personal data) because the widget must stay signed
 * out after a logout even once the app's process has died; held in a flow so a
 * widget already on screen recomposes immediately. Excluded from backups so a
 * restored phone never shows a signed-in widget for an app that is not.
 */
object NextClassWidgetSession {
    private const val PREFS = "iulms_widget"
    private const val KEY_SIGNED_IN = "signed_in"

    // Process-wide: the update must outlive whichever screen triggered it.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var state: MutableStateFlow<Boolean>? = null

    fun signedIn(context: Context): StateFlow<Boolean> = flow(context).asStateFlow()

    /**
     * Called on every sign-in state change, including a sign-in that replaces a
     * previous account, so the widget always re-renders for the current user.
     * Signing out wipes the widget's preferences entirely.
     */
    fun onAuthChanged(context: Context, signedIn: Boolean) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            clear()
            if (signedIn) putBoolean(KEY_SIGNED_IN, true)
        }
        flow(app).value = signedIn
        NextClassRefresh.ticks.update { it + 1 }
        // Covers widgets with no running session; ones on screen already
        // recomposed from the flow above.
        scope.launch { runCatching { NextClassWidget().updateAll(app) } }
    }

    /** The cached schedule changed: re-read it in every widget. */
    fun onDataChanged(context: Context) {
        val app = context.applicationContext
        NextClassRefresh.ticks.update { it + 1 }
        scope.launch { runCatching { NextClassWidget().updateAll(app) } }
    }

    private fun flow(context: Context): MutableStateFlow<Boolean> =
        state ?: synchronized(this) {
            state ?: MutableStateFlow(
                context.applicationContext
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_SIGNED_IN, false)
            ).also { state = it }
        }
}

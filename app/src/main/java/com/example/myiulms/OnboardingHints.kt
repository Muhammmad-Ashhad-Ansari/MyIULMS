package com.example.myiulms

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-time feature hints, remembered on this device ("iulms_ui" prefs, the
 * same file as the theme choice). Held once per process as a StateFlow, so
 * every screen sees a dismissal immediately and it survives restarts.
 */
object OnboardingHints {
    private const val PREFS = "iulms_ui"
    private const val KEY_CLEAN_CGPA_SEEN = "hint_clean_cgpa_seen"

    @Volatile
    private var cleanCgpa: MutableStateFlow<Boolean>? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cleanCgpaFlow(context: Context): MutableStateFlow<Boolean> =
        cleanCgpa ?: synchronized(this) {
            cleanCgpa ?: MutableStateFlow(prefs(context).getBoolean(KEY_CLEAN_CGPA_SEEN, false))
                .also { cleanCgpa = it }
        }

    /** True once the student has used the Excluding-F/W/I toggle on the Transcript card. */
    fun cleanCgpaDiscovered(context: Context): StateFlow<Boolean> = cleanCgpaFlow(context).asStateFlow()

    fun markCleanCgpaDiscovered(context: Context) {
        val flow = cleanCgpaFlow(context)
        if (flow.value) return
        prefs(context).edit { putBoolean(KEY_CLEAN_CGPA_SEEN, true) }
        flow.value = true
    }
}

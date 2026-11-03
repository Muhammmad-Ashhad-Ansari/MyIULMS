package com.example.myiulms.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()

    // Widget events are only scheduled while a widget exists.
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        NextClassWidgetSession.rescheduleAlarms(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        NextClassWidgetSession.rescheduleAlarms(context)
    }
}

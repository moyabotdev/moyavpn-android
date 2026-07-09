package com.moyavpn.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * Homescreen-Widget „Server wechseln": Tap schaltet auf den naechsten aktiven
 * Server (und verbindet ihn). Zeigt den gerade laufenden Server an.
 */
class SwitchWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refreshWidgets(context)
    }
}

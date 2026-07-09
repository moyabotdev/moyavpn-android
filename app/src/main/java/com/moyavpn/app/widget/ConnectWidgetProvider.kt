package com.moyavpn.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * Homescreen-Widget „1-Tap-Connect": Tap verbindet den Favoriten (mit
 * Auto-Rotation) bzw. trennt. Das eigentliche Schalten macht die
 * [WidgetProxyActivity]; hier wird nur gezeichnet.
 */
class ConnectWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        refreshWidgets(context)
    }
}

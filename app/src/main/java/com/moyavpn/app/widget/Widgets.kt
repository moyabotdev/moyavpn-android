package com.moyavpn.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.moyavpn.app.MainActivity
import com.moyavpn.app.R
import com.moyavpn.app.data.CachedServer
import com.moyavpn.app.data.SplitTunnelStore
import com.moyavpn.app.vpn.VpnState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Prozessweiter Scope fuer Widget-Hintergrundarbeit (Verbinden/Trennen ausgeloest
 * per Widget-Tap). Bewusst app-weit, nicht an eine Activity gebunden — der Tap
 * soll den Tunnel schalten, auch wenn die (unsichtbare) Proxy-Activity sofort
 * wieder schliesst. Sobald der VpnService laeuft, haelt dieser den Prozess am Leben.
 */
object WidgetScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Fire-and-forget: beide Widgets mit dem aktuellen Zustand neu zeichnen. */
fun refreshWidgets(context: Context) {
    val app = context.applicationContext
    WidgetScope.launch { pushWidgets(app) }
}

/** Liest Zustand + Server-Cache und aktualisiert alle vorhandenen Widget-Instanzen. */
private suspend fun pushWidgets(context: Context) {
    val mgr = AppWidgetManager.getInstance(context)
    val store = SplitTunnelStore(context)
    val servers = store.cachedServers.first()

    val activeId = VpnState.activeServerId.value
    val connectingId = VpnState.connecting.value
    val activeSrv = servers.firstOrNull { it.serverId == activeId }

    // ── Connect-Widget ──
    connectViews(context, servers, activeId, connectingId, activeSrv).let { rv ->
        mgr.getAppWidgetIds(ComponentName(context, ConnectWidgetProvider::class.java))
            .forEach { mgr.updateAppWidget(it, rv) }
    }
    // ── Server-wechseln-Widget ──
    switchViews(context, activeSrv, connectingId != null).let { rv ->
        mgr.getAppWidgetIds(ComponentName(context, SwitchWidgetProvider::class.java))
            .forEach { mgr.updateAppWidget(it, rv) }
    }
}

private fun label(s: CachedServer?): String =
    if (s == null) "" else "${s.flag ?: "🌐"} ${s.serverName}"

private fun connectViews(
    context: Context,
    servers: List<CachedServer>,
    activeId: String?,
    connectingId: String?,
    activeSrv: CachedServer?,
): RemoteViews {
    val rv = RemoteViews(context.packageName, R.layout.widget_connect)
    rv.setInt(R.id.widget_root, "setBackgroundResource",
        if (activeId != null) R.drawable.widget_bg_active else R.drawable.widget_bg)
    rv.setTextViewText(R.id.widget_title, "🛡 " + context.getString(R.string.app_name))
    val status = when {
        connectingId != null -> "🟡 " + context.getString(R.string.status_connecting)
        activeId != null -> "🟢 " + context.getString(R.string.status_connected) +
            (activeSrv?.let { " · ${label(it)}" } ?: "")
        else -> "⚪ " + context.getString(R.string.status_disconnected)
    }
    rv.setTextViewText(R.id.widget_status, status)
    val hint = if (servers.isEmpty()) context.getString(R.string.widget_login_first)
               else if (activeId != null) context.getString(R.string.widget_tap_disconnect)
               else context.getString(R.string.widget_tap_connect)
    rv.setTextViewText(R.id.widget_sub, hint)
    rv.setOnClickPendingIntent(R.id.widget_root, proxyIntent(context, WidgetProxyActivity.ACTION_TOGGLE, 1))
    return rv
}

private fun switchViews(context: Context, activeSrv: CachedServer?, connecting: Boolean): RemoteViews {
    val rv = RemoteViews(context.packageName, R.layout.widget_switch)
    rv.setInt(R.id.widget_root, "setBackgroundResource",
        if (activeSrv != null) R.drawable.widget_bg_active else R.drawable.widget_bg)
    rv.setTextViewText(R.id.widget_title, context.getString(R.string.widget_switch_title))
    val line = when {
        connecting -> "🟡 " + context.getString(R.string.status_connecting)
        activeSrv != null -> label(activeSrv)
        else -> "⚪ " + context.getString(R.string.status_disconnected)
    }
    rv.setTextViewText(R.id.widget_sub, line)
    rv.setOnClickPendingIntent(R.id.widget_root, proxyIntent(context, WidgetProxyActivity.ACTION_SWITCH, 2))
    return rv
}

private fun proxyIntent(context: Context, action: String, requestCode: Int): PendingIntent {
    val intent = Intent(context, WidgetProxyActivity::class.java).apply {
        this.action = action
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
    }
    return PendingIntent.getActivity(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Oeffnet die App (Widget-Tap ohne vorhandene Server / ohne Login). */
internal fun openApp(context: Context) {
    val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(i)
}

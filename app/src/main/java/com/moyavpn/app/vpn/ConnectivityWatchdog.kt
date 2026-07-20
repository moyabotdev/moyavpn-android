package com.moyavpn.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moyavpn.app.MainActivity
import com.moyavpn.app.R
import com.moyavpn.app.data.CachedServer
import com.moyavpn.app.data.SplitTunnelStore
import com.moyavpn.app.widget.refreshWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Verbindungswaechter (Auto-Failover).
 *
 * Waehrend eine Verbindung laeuft, prueft dieser Wachhund regelmaessig, ob durch
 * den Tunnel wirklich Daten zu einer Westseite fliessen. In Russland kann ein
 * bislang funktionierender Server mitten in der Sitzung von der DPI blockiert
 * werden — dann steht der Tunnel zwar noch, aber es kommt nichts mehr durch.
 *
 * Erkennt der Waechter zweimal hintereinander eine Blockade, rotiert er
 * automatisch auf den naechsten aktiven Server (Favorit zuerst, den blockierten
 * ausgeschlossen) und meldet den Wechsel per Notification.
 *
 * Sonde: zuerst eine schnelle neutrale Erreichbarkeitspruefung (gstatic/google
 * `generate_204`); nur wenn die scheitert, wird zusaetzlich **instagram.com**
 * geprueft — in RU ohne VPN gesperrt, also ein starker Beweis fuer echtes,
 * ungefiltertes Durchkommen. Erst wenn ALLE scheitern, gilt der Server als tot.
 *
 * An/Aus ueber [SplitTunnelStore.watchdog] (Standard AN). Laeuft prozessweit,
 * unabhaengig von der Activity — der AmneziaWG-Foregroundservice haelt den
 * Prozess am Leben, solange der Tunnel steht.
 */
object ConnectivityWatchdog {

    private const val INITIAL_GRACE_MS = 12_000L   // frische Verbindung erst einschwingen lassen
    private const val CHECK_INTERVAL_MS = 30_000L  // Pruefintervall
    private const val PROBE_TIMEOUT_MS = 4_000      // je Sonde
    private const val STRIKES_TO_ROTATE = 2         // aufeinanderfolgende Fehlschlaege bis Wechsel
    private const val CHANNEL = "moyavpn_watchdog"
    private const val NOTIF_ID = 4711

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false
    private var loop: Job? = null

    /** Einmalig starten (idempotent). Beobachtet den Verbindungszustand. */
    fun start(context: Context) {
        synchronized(this) {
            if (started) return
            started = true
        }
        val app = context.applicationContext
        scope.launch {
            VpnState.activeServerId.collect { id ->
                loop?.cancel()
                loop = if (id != null) scope.launch { watchLoop(app, id) } else null
            }
        }
    }

    /** Prueft die laufende Sitzung [sessionId], bis sie endet oder rotiert wird. */
    private suspend fun watchLoop(app: Context, sessionId: String) {
        val store = SplitTunnelStore(app)
        delay(INITIAL_GRACE_MS)
        var strikes = 0
        while (coroutineContext.isActive) {
            if (VpnState.activeServerId.value != sessionId) return   // Sitzung gewechselt
            if (store.watchdog.first()) {
                if (probeOk()) {
                    strikes = 0
                } else {
                    strikes++
                    if (strikes >= STRIKES_TO_ROTATE) {
                        rotate(app, sessionId)
                        return   // neue Sitzung startet ihren eigenen Loop
                    }
                }
            } else {
                strikes = 0   // Waechter aus → nur passiv weiterlaufen
            }
            delay(CHECK_INTERVAL_MS)
        }
    }

    /** true, sobald IRGENDEINE Westseite durch den Tunnel erreichbar ist. */
    private suspend fun probeOk(): Boolean =
        reachable("https://www.gstatic.com/generate_204") ||
        reachable("https://www.google.com/generate_204") ||
        reachable("https://www.instagram.com/favicon.ico")

    /** Erreichbarkeit: kommt IRGENDEIN HTTP-Status zurueck, floss Traffic durch. */
    private suspend fun reachable(url: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = PROBE_TIMEOUT_MS
                readTimeout = PROBE_TIMEOUT_MS
                requestMethod = "HEAD"
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MoyaVPN-Watchdog")
            }
            val code = c.responseCode   // wirft bei totem Tunnel (Timeout/IOException)
            c.disconnect()
            code > 0
        }.getOrDefault(false)
    }

    /** Auf den naechsten aktiven Server rotieren (Favorit zuerst, [blockedId] raus). */
    private suspend fun rotate(app: Context, blockedId: String) {
        val store = SplitTunnelStore(app)
        val candidates = store.cachedServers.first().filter { it.serverId != blockedId }
        if (candidates.isEmpty()) return
        val ordered = VpnConnector.order(candidates, store.favorite.first())
        VpnState.setConnecting(ordered.first().serverId)
        val connected = VpnConnector.connectWithFallback(app, ordered)
        refreshWidgets(app)
        if (connected != null) notifySwitch(app, connected)
    }

    private fun notifySwitch(app: Context, srv: CachedServer) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        app.getString(R.string.watchdog_notif_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
            val pi = PendingIntent.getActivity(
                app, 7,
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val label = "${srv.flag ?: "🌐"} ${srv.serverName}"
            val n = NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_shield)
                .setContentTitle(app.getString(R.string.watchdog_notif_title))
                .setContentText(app.getString(R.string.watchdog_notif_text, label))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            NotificationManagerCompat.from(app).notify(NOTIF_ID, n)
        }
    }
}

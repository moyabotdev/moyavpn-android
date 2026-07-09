package com.moyavpn.app.vpn

import android.content.Context
import com.moyavpn.app.data.CachedServer
import com.moyavpn.app.data.SplitTunnelStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Gemeinsame Verbindungs-Logik fuer App-UI **und** Widgets:
 *  - wendet die gespeicherte Split-Tunneling-Wahl an,
 *  - prueft nach dem Aufbau, ob wirklich ein Handshake zustande kommt,
 *  - rotiert bei Blockade automatisch auf den naechsten Server (Favorit zuerst).
 *
 * Die VPN-Erlaubnis (VpnService.prepare) muss der Aufrufer VORHER sicherstellen
 * (nur eine Activity darf den System-Dialog zeigen). Ist die Erlaubnis erteilt,
 * laeuft hier alles ohne weitere UI.
 */
object VpnConnector {

    /** Max. Wartezeit auf den ersten empfangenen Byte (Handshake-Antwort). */
    private const val HANDSHAKE_TIMEOUT_MS = 8_000L
    private const val POLL_MS = 500L

    /**
     * Verbindet der Reihe nach mit [ordered], bis einer wirklich einen Handshake
     * liefert. Liefert den verbundenen Server zurueck — oder null, wenn keiner
     * durchkam (dann ist der Tunnel getrennt).
     */
    suspend fun connectWithFallback(
        context: Context,
        ordered: List<CachedServer>,
    ): CachedServer? {
        if (ordered.isEmpty()) return null
        val app = context.applicationContext
        val store = SplitTunnelStore(app)
        val mode = store.mode.first()
        val pkgs = store.packages.first().toList()
        val splitKey = SplitTunnelStore.keyFor(mode)

        for (srv in ordered) {
            VpnState.setConnecting(srv.serverId)
            val started = runCatching {
                TunnelManager.connect(app, srv.config, splitKey, pkgs)
            }.isSuccess
            if (!started) continue                      // Backend-Fehler → naechster
            if (handshakeOk(app)) {
                VpnState.setActive(srv.serverId)         // laeuft
                return srv
            }
            // Aufgebaut, aber keine Antwort (DPI-Blockade?) → naechsten probieren
        }
        runCatching { TunnelManager.disconnect(app) }
        VpnState.setActive(null)
        return null
    }

    /** Trennt und aktualisiert den geteilten Zustand. */
    suspend fun disconnect(context: Context) {
        TunnelManager.disconnect(context.applicationContext)
        VpnState.setActive(null)
    }

    /**
     * Pollt die Tunnel-Statistik: sobald **empfangene** Bytes > 0 sind, ist der
     * Handshake durch (der Server hat geantwortet). Kommt bis zum Timeout nichts
     * zurueck, gilt der Server als blockiert.
     */
    private suspend fun handshakeOk(context: Context): Boolean {
        var waited = 0L
        while (waited < HANDSHAKE_TIMEOUT_MS) {
            val (rx, _) = TunnelManager.statistics(context)
            if (rx > 0) return true
            delay(POLL_MS)
            waited += POLL_MS
        }
        return false
    }

    /**
     * Baut die Reihenfolge fuer den Fallback: [favorit] zuerst (falls gesetzt und
     * aktiv), danach alle uebrigen aktiven Server in Listen-Reihenfolge.
     */
    fun order(servers: List<CachedServer>, favoriteId: String?): List<CachedServer> {
        val fav = servers.firstOrNull { it.serverId == favoriteId }
        return if (fav == null) servers else listOf(fav) + servers.filter { it.serverId != fav.serverId }
    }
}

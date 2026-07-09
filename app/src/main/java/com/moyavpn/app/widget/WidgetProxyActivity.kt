package com.moyavpn.app.widget

import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.moyavpn.app.data.CachedServer
import com.moyavpn.app.data.SplitTunnelStore
import com.moyavpn.app.vpn.VpnConnector
import com.moyavpn.app.vpn.VpnState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Unsichtbare Brücke zwischen Widget-Tap und Tunnel. Homescreen-Widgets duerfen
 * den VPN-Dienst nicht direkt starten (der System-Consent-Dialog braucht eine
 * Activity). Diese translucente Activity holt — nur beim allerersten Mal — die
 * VPN-Erlaubnis und schaltet dann den Tunnel; danach schliesst sie sofort.
 *
 * Ist die Erlaubnis bereits erteilt (Normalfall), laeuft die eigentliche Arbeit
 * im [WidgetScope] (prozessweit), damit der Tap sofort wirkt und die Activity
 * nicht sichtbar haengt.
 */
class WidgetProxyActivity : ComponentActivity() {

    private var pending: (suspend () -> Unit)? = null

    private val consent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val work = pending
            pending = null
            if (result.resultCode == RESULT_OK && work != null) WidgetScope.launch { work() }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent?.action) {
            ACTION_TOGGLE -> onToggle()
            ACTION_SWITCH -> onSwitch()
            else -> finish()
        }
    }

    /** Verbindet den Favoriten (mit Fallback) oder trennt, falls schon verbunden. */
    private fun onToggle() {
        val app = applicationContext
        if (VpnState.activeServerId.value != null) {
            WidgetScope.launch {
                VpnConnector.disconnect(app)
                refreshWidgets(app)
            }
            finish()
            return
        }
        runWithConsent {
            val store = SplitTunnelStore(app)
            val servers = store.cachedServers.first()
            if (servers.isEmpty()) { openApp(app); return@runWithConsent }
            val fav = store.favorite.first()
            VpnConnector.connectWithFallback(app, VpnConnector.order(servers, fav))
            refreshWidgets(app)
        }
    }

    /** Schaltet auf den naechsten aktiven Server (rotiert durch die Liste). */
    private fun onSwitch() {
        val app = applicationContext
        runWithConsent {
            val store = SplitTunnelStore(app)
            val servers = store.cachedServers.first()
            if (servers.isEmpty()) { openApp(app); return@runWithConsent }
            val curId = VpnState.activeServerId.value
            val idx = servers.indexOfFirst { it.serverId == curId }
            val next = servers[(idx + 1).mod(servers.size)]   // -1 (getrennt) → erster Server
            val ordered = listOf(next) + servers.filter { it.serverId != next.serverId }
            VpnConnector.connectWithFallback(app, ordered)
            refreshWidgets(app)
        }
    }

    /**
     * Fuehrt [work] aus. Ist die VPN-Erlaubnis noch nicht erteilt, wird zuerst der
     * System-Dialog gezeigt (Activity bleibt bis zum Ergebnis). Sonst laeuft die
     * Arbeit sofort im Hintergrund und die Activity schliesst.
     */
    private fun runWithConsent(work: suspend () -> Unit) {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            pending = work
            runCatching { consent.launch(prepare) }
                .onFailure { pending = null; finish() }
        } else {
            WidgetScope.launch { work() }
            finish()
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.moyavpn.app.widget.TOGGLE"
        const val ACTION_SWITCH = "com.moyavpn.app.widget.SWITCH"
    }
}

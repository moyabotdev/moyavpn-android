package com.moyavpn.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Prozessweite, einzige Wahrheit ueber den aktuellen Tunnel-Zustand — geteilt
 * zwischen App-UI (MainViewModel) und den Homescreen-Widgets/der Proxy-Activity.
 *
 * TunnelManager ist ein Singleton, aber sein Zustand (welcher Server laeuft) lebte
 * bisher nur im ViewModel. Widgets laufen ohne ViewModel; sie lesen/schreiben hier.
 */
object VpnState {

    private val _activeServerId = MutableStateFlow<String?>(null)
    /** ServerId der laufenden Verbindung, oder null wenn getrennt. */
    val activeServerId: StateFlow<String?> = _activeServerId.asStateFlow()

    private val _connecting = MutableStateFlow<String?>(null)
    /** ServerId, die gerade verbunden wird (Spinner/Widget-Status), oder null. */
    val connecting: StateFlow<String?> = _connecting.asStateFlow()

    private val _connectedSince = MutableStateFlow<Long?>(null)
    /** Zeitpunkt (ms) des Verbindungsaufbaus, oder null wenn getrennt — für die Uptime-Anzeige. */
    val connectedSince: StateFlow<Long?> = _connectedSince.asStateFlow()

    fun setActive(serverId: String?) {
        // Uptime-Start nur bei echtem Wechsel setzen; bei Trennung löschen.
        if (serverId != null && _activeServerId.value != serverId) {
            _connectedSince.value = System.currentTimeMillis()
        } else if (serverId == null) {
            _connectedSince.value = null
        }
        _activeServerId.value = serverId
        _connecting.value = null
    }

    fun setConnecting(serverId: String?) {
        _connecting.value = serverId
    }
}

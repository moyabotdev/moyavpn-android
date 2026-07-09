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

    fun setActive(serverId: String?) {
        _activeServerId.value = serverId
        _connecting.value = null
    }

    fun setConnecting(serverId: String?) {
        _connecting.value = serverId
    }
}

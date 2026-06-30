package com.moyavpn.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moyavpn.app.data.AccountResponse
import com.moyavpn.app.data.Connection
import com.moyavpn.app.data.MoyaApi
import com.moyavpn.app.data.TokenStore
import com.moyavpn.app.vpn.TunnelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Was der UI gerade anzeigen soll. */
sealed interface UiState {
    data object Loading : UiState
    data object NeedsLogin : UiState
    data class Error(val message: String) : UiState
    data class Ready(
        val account: AccountResponse,
        val activeServerId: String? = null,   // welche Verbindung laeuft gerade
        val connectingTo: String? = null,     // welcher Server wird gerade verbunden
        val rxBytes: Long = 0,
        val txBytes: Long = 0,
        val connectError: String? = null,     // letzter Verbindungsfehler (für Anzeige)
    ) : UiState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val api = MoyaApi.create()
    private val tokenStore = TokenStore(app)

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val token = tokenStore.token.first()
            if (token.isNullOrBlank()) _state.value = UiState.NeedsLogin
            else loadAccount(token)
        }
    }

    /** Token aus dem Login-Feld speichern und Konto laden. */
    fun login(token: String) {
        val clean = token.trim()
        if (clean.isEmpty()) return
        _state.value = UiState.Loading
        viewModelScope.launch {
            tokenStore.save(clean)
            loadAccount(clean)
        }
    }

    fun logout() {
        viewModelScope.launch {
            TunnelManager.disconnect(getApplication())
            tokenStore.clear()
            _state.value = UiState.NeedsLogin
        }
    }

    fun retry() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val token = tokenStore.token.first()
            if (token.isNullOrBlank()) _state.value = UiState.NeedsLogin
            else loadAccount(token)
        }
    }

    private suspend fun loadAccount(token: String) {
        runCatching { api.account("Bearer $token") }
            .onSuccess { acc ->
                _state.value = UiState.Ready(
                    account = acc,
                    activeServerId = if (TunnelManager.isUp) currentServerId else null,
                )
            }
            .onFailure { e ->
                val msg = when {
                    e.message?.contains("401") == true -> "Ungültiger oder abgelaufener Code."
                    else -> "Verbindung zum Server fehlgeschlagen. Bitte später erneut versuchen."
                }
                _state.value = UiState.Error(msg)
            }
    }

    private var currentServerId: String? = null

    /**
     * Verbindet mit der gewählten Verbindung (oder trennt, wenn sie schon läuft).
     * Beim Wechsel auf einen anderen Server trennt die AmneziaWG-Engine den alten
     * Tunnel automatisch und baut den neuen auf.
     */
    fun toggle(conn: Connection) {
        val ready = _state.value as? UiState.Ready ?: return
        viewModelScope.launch {
            if (ready.activeServerId == conn.serverId) {
                TunnelManager.disconnect(getApplication())
                currentServerId = null
                _state.value = ready.copy(activeServerId = null, rxBytes = 0, txBytes = 0)
                return@launch
            }
            // Spinner am Ziel-Server anzeigen
            _state.value = ready.copy(connectingTo = conn.serverId, connectError = null)
            runCatching { TunnelManager.connect(getApplication(), conn.config) }
                .onSuccess {
                    currentServerId = conn.serverId
                    _state.value = ready.copy(activeServerId = conn.serverId, connectingTo = null)
                    refreshStats()
                }
                .onFailure { e ->
                    currentServerId = null
                    val reason = (e as? org.amnezia.awg.backend.BackendException)?.reason?.name
                    val msg = reason ?: "${e.javaClass.simpleName}: ${e.message ?: "unbekannt"}"
                    _state.value = ready.copy(
                        activeServerId = null,
                        connectingTo = null,
                        connectError = msg,
                    )
                }
        }
    }

    /** Holt aktuelle Traffic-Zahlen, solange ein Tunnel laeuft. */
    fun refreshStats() {
        viewModelScope.launch {
            val ready = _state.value as? UiState.Ready ?: return@launch
            if (ready.activeServerId == null) return@launch
            val (rx, tx) = TunnelManager.statistics(getApplication())
            _state.value = (_state.value as? UiState.Ready)?.copy(rxBytes = rx, txBytes = tx)
                ?: return@launch
        }
    }
}

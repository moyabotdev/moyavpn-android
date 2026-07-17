package com.moyavpn.app.ui

import android.app.Application
import android.content.Intent
import com.moyavpn.app.BuildConfig
import com.moyavpn.app.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moyavpn.app.data.AccountResponse
import com.moyavpn.app.data.CachedServer
import com.moyavpn.app.data.Connection
import com.moyavpn.app.data.MoyaApi
import com.moyavpn.app.data.ServerPing
import com.moyavpn.app.data.SplitTunnelStore
import com.moyavpn.app.data.TokenStore
import com.moyavpn.app.data.UpdateChecker
import com.moyavpn.app.data.UpdateInfo
import com.moyavpn.app.vpn.TunnelManager
import com.moyavpn.app.vpn.VpnConnector
import com.moyavpn.app.vpn.VpnState
import com.moyavpn.app.vpn.XrayBridge
import com.moyavpn.app.widget.refreshWidgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Eine startbare App fuer die Split-Tunneling-Auswahl. */
data class AppEntry(val pkg: String, val label: String)

/** Zustand des Einstellungen-Screens (Split-Tunneling). */
data class SettingsUi(
    val mode: String = SplitTunnelStore.MODE_OFF,
    val selected: Set<String> = emptySet(),
    val apps: List<AppEntry> = emptyList(),
    val loadingApps: Boolean = false,
)

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
    private val splitStore = SplitTunnelStore(app)

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Einstellungen (Split-Tunneling) — von der DataStore-Quelle gespeist.
    private val _settings = MutableStateFlow(SettingsUi())
    val settings: StateFlow<SettingsUi> = _settings.asStateFlow()

    // Verfuegbares Update (nur direct-Variante); null = keins.
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    // ServerId des angehefteten Favoriten (⭐) — Standard fuer Hero-Tap + Widgets.
    private val _favorite = MutableStateFlow<String?>(null)
    val favorite: StateFlow<String?> = _favorite.asStateFlow()

    // Gemessene Latenzen je Server (nur Info-Anzeige, kein Auto-Select).
    private val _pings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pings: StateFlow<Map<String, Int>> = _pings.asStateFlow()

    init {
        viewModelScope.launch {
            val token = tokenStore.token.first()
            if (token.isNullOrBlank()) _state.value = UiState.NeedsLogin
            else loadAccount(token)
        }
        // Split-Modus + Auswahl live aus dem Store spiegeln.
        viewModelScope.launch {
            splitStore.mode.combine(splitStore.packages) { m, p -> m to p }
                .collect { (m, p) -> _settings.value = _settings.value.copy(mode = m, selected = p) }
        }
        // Favorit live spiegeln.
        viewModelScope.launch { splitStore.favorite.collect { _favorite.value = it } }
        checkUpdate()
    }

    /** Update-Pruefung — nur in der direct-Variante (Play updatet selbst). */
    fun checkUpdate() {
        if (!BuildConfig.SHOW_PURCHASE) return
        viewModelScope.launch { _update.value = UpdateChecker.check() }
    }

    fun dismissUpdate() { _update.value = null }

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

    /** Schnell-Verbinden: erstellt einen 4h-Trial und meldet sich damit an. */
    fun startTrial() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            runCatching { api.trial() }
                .onSuccess { res ->
                    tokenStore.save(res.token)
                    loadAccount(res.token)
                }
                .onFailure { e ->
                    val res = if (e.message?.contains("429") == true) R.string.err_trial_limit
                              else R.string.err_server
                    _state.value = UiState.Error(getApplication<Application>().getString(res))
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            VpnConnector.disconnect(getApplication())
            currentServerId = null
            refreshWidgets(getApplication())
            tokenStore.clear()
            splitStore.cacheServers(emptyList())
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
                onConnectionsLoaded(acc)
            }
            .onFailure { e ->
                val res = if (e.message?.contains("401") == true) R.string.err_invalid_code
                          else R.string.err_server
                _state.value = UiState.Error(getApplication<Application>().getString(res))
            }
    }

    /** Nach jedem Account-Load: Server fuer Widgets cachen + Pings messen. */
    private fun onConnectionsLoaded(acc: AccountResponse) {
        viewModelScope.launch {
            splitStore.cacheServers(CachedServer.fromConnections(acc.connections))
        }
        measurePings(acc.connections)
    }

    /** Latenzen aller aktiven Server parallel messen (nur Info). */
    fun measurePings(connections: List<Connection>) {
        viewModelScope.launch {
            val hosts = connections
                .filter { it.status == "active" }
                .mapNotNull { c -> ServerPing.endpointHost(c.config)?.let { c.serverId to it } }
                .toMap()
            if (hosts.isEmpty()) return@launch
            _pings.value = ServerPing.pingAll(hosts)
        }
    }

    private var currentServerId: String? = null

    /** Favorit anheften / abwaehlen (Stern in der Serverliste). */
    fun setFavorite(serverId: String) {
        viewModelScope.launch {
            val cur = splitStore.favorite.first()
            splitStore.setFavorite(if (cur == serverId) null else serverId)
            refreshWidgets(getApplication())
        }
    }

    /**
     * Welche Verbindung soll der „1-Tap" (Hero/Widget) nutzen? Der Favorit,
     * sonst die erste aktive Verbindung. Null, wenn keine aktiv ist.
     */
    fun defaultConnection(): Connection? {
        val ready = _state.value as? UiState.Ready ?: return null
        val active = ready.account.connections.filter { it.status == "active" }
        val favId = favorite.value
        return active.firstOrNull { it.serverId == favId } ?: active.firstOrNull()
    }

    /**
     * Hero-Tap / „Sofort verbinden": trennt, falls schon verbunden — sonst baut
     * er den Favoriten mit automatischer Fallback-Rotation auf. Die VPN-Erlaubnis
     * muss der Aufrufer (Activity) vorher sichergestellt haben.
     */
    fun smartToggle() {
        val ready = _state.value as? UiState.Ready ?: return
        if (ready.activeServerId != null) {
            disconnect()
            return
        }
        val ordered = VpnConnector.order(
            CachedServer.fromConnections(ready.account.connections),
            favorite.value,
        )
        if (ordered.isEmpty()) return
        viewModelScope.launch {
            _state.value = ready.copy(connectingTo = ordered.first().serverId, connectError = null)
            VpnState.setConnecting(ordered.first().serverId)
            val connected = VpnConnector.connectWithFallback(getApplication(), ordered)
            currentServerId = connected?.serverId
            val cur = _state.value as? UiState.Ready ?: ready
            _state.value = if (connected != null) {
                cur.copy(activeServerId = connected.serverId, connectingTo = null, connectError = null)
            } else {
                cur.copy(
                    activeServerId = null,
                    connectingTo = null,
                    connectError = getApplication<Application>().getString(R.string.err_all_blocked),
                )
            }
            refreshWidgets(getApplication())
            if (connected != null) refreshStats()
        }
    }

    /** Trennen (Hero-Tap bei aktiver Verbindung). */
    fun disconnect() {
        viewModelScope.launch {
            VpnConnector.disconnect(getApplication())
            currentServerId = null
            (_state.value as? UiState.Ready)?.let {
                _state.value = it.copy(activeServerId = null, connectingTo = null, rxBytes = 0, txBytes = 0)
            }
            refreshWidgets(getApplication())
        }
    }

    /**
     * Verbindet mit der gewählten Verbindung (oder trennt, wenn sie schon läuft).
     * Explizite Kartenauswahl — hier KEINE Rotation, der Nutzer hat bewusst einen
     * bestimmten Server gewaehlt. Beim Wechsel trennt die AmneziaWG-Engine den
     * alten Tunnel automatisch und baut den neuen auf.
     */
    fun toggle(conn: Connection) {
        val ready = _state.value as? UiState.Ready ?: return
        viewModelScope.launch {
            if (ready.activeServerId == conn.serverId) {
                VpnConnector.disconnect(getApplication())
                currentServerId = null
                _state.value = ready.copy(activeServerId = null, rxBytes = 0, txBytes = 0)
                refreshWidgets(getApplication())
                return@launch
            }
            // Spinner am Ziel-Server anzeigen
            _state.value = ready.copy(connectingTo = conn.serverId, connectError = null)
            VpnState.setConnecting(conn.serverId)
            // Aktuelle Split-Tunneling-Wahl beim Verbinden anwenden.
            val mode = splitStore.mode.first()
            val pkgs = splitStore.packages.first().toList()
            val splitKey = SplitTunnelStore.keyFor(mode)
            runCatching {
                if (conn.protocol == "xray" && conn.xray != null) {
                    runCatching { TunnelManager.disconnect(getApplication()) }   // AWG aus, falls aktiv
                    // wartet auf den Service und wirft dessen echten Fehler
                    XrayBridge.connect(getApplication(), conn.xray)
                } else {
                    runCatching { XrayBridge.disconnect(getApplication()) }       // xray aus, falls aktiv
                    TunnelManager.connect(getApplication(), conn.config, splitKey, pkgs)
                }
            }
                .onSuccess {
                    currentServerId = conn.serverId
                    VpnState.setActive(conn.serverId)
                    _state.value = ready.copy(activeServerId = conn.serverId, connectingTo = null)
                    refreshWidgets(getApplication())
                    refreshStats()
                }
                .onFailure { e ->
                    currentServerId = null
                    VpnState.setActive(null)
                    val reason = (e as? org.amnezia.awg.backend.BackendException)?.reason?.name
                    val msg = reason ?: "${e.javaClass.simpleName}: ${e.message ?: "unbekannt"}"
                    _state.value = ready.copy(
                        activeServerId = null,
                        connectingTo = null,
                        connectError = msg,
                    )
                    refreshWidgets(getApplication())
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

    /**
     * Stiller Neuabgleich der Server-Liste (kein Lade-Spinner). Wird beim
     * Zurueckkehren in die App (onResume) und per Aktualisieren-Button gerufen,
     * damit neu hinzugefuegte Server erscheinen und weggefallene verschwinden —
     * ohne Aus-/Einloggen. Eine laufende Verbindung bleibt bestehen, solange ihr
     * Server noch in der Liste ist.
     */
    fun refreshAccount() {
        viewModelScope.launch {
            val token = tokenStore.token.first()
            if (token.isNullOrBlank()) return@launch
            val current = _state.value
            runCatching { api.account("Bearer $token") }
                .onSuccess { acc ->
                    val ready = current as? UiState.Ready
                    if (ready != null) {
                        val stillThere = acc.connections.any { it.serverId == ready.activeServerId }
                        _state.value = ready.copy(
                            account = acc,
                            activeServerId = if (stillThere) ready.activeServerId else null,
                        )
                        if (!stillThere && ready.activeServerId != null) {
                            VpnConnector.disconnect(getApplication())
                            currentServerId = null
                            refreshWidgets(getApplication())
                        }
                    } else {
                        _state.value = UiState.Ready(
                            account = acc,
                            activeServerId = if (TunnelManager.isUp) currentServerId else null,
                        )
                    }
                    onConnectionsLoaded(acc)
                }
                // Fehler beim stillen Refresh bewusst ignorieren — alte Liste bleibt.
        }
        checkUpdate()
    }

    /** Split-Tunneling-Modus setzen (off/include/exclude). */
    fun setSplitMode(mode: String) {
        viewModelScope.launch { splitStore.setMode(mode) }
    }

    /** Eine App fuer Split-Tunneling an-/abwaehlen. */
    fun toggleApp(pkg: String) {
        viewModelScope.launch {
            val cur = splitStore.packages.first()
            val next = if (pkg in cur) cur - pkg else cur + pkg
            splitStore.setPackages(next)
        }
    }

    /** Startbare Apps fuer die Split-Auswahl laden (via Launcher-Query). */
    fun loadApps() {
        if (_settings.value.apps.isNotEmpty() || _settings.value.loadingApps) return
        _settings.value = _settings.value.copy(loadingApps = true)
        viewModelScope.launch {
            val pm = getApplication<Application>().packageManager
            val self = getApplication<Application>().packageName
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val entries = runCatching {
                pm.queryIntentActivities(intent, 0)
                    .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .distinctBy { it.pkg }
                    .filter { it.pkg != self }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
            _settings.value = _settings.value.copy(apps = entries, loadingApps = false)
        }
    }
}

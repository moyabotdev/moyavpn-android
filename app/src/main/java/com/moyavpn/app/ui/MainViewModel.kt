package com.moyavpn.app.ui

import android.app.Application
import android.content.Intent
import com.moyavpn.app.BuildConfig
import com.moyavpn.app.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moyavpn.app.data.AccountResponse
import com.moyavpn.app.data.Connection
import com.moyavpn.app.data.MoyaApi
import com.moyavpn.app.data.SplitTunnelStore
import com.moyavpn.app.data.TokenStore
import com.moyavpn.app.data.UpdateChecker
import com.moyavpn.app.data.UpdateInfo
import com.moyavpn.app.vpn.TunnelManager
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
                val res = if (e.message?.contains("401") == true) R.string.err_invalid_code
                          else R.string.err_server
                _state.value = UiState.Error(getApplication<Application>().getString(res))
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
            // Aktuelle Split-Tunneling-Wahl beim Verbinden anwenden.
            val mode = splitStore.mode.first()
            val pkgs = splitStore.packages.first().toList()
            val splitKey = SplitTunnelStore.keyFor(mode)
            runCatching { TunnelManager.connect(getApplication(), conn.config, splitKey, pkgs) }
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
                            TunnelManager.disconnect(getApplication())
                            currentServerId = null
                        }
                    } else {
                        _state.value = UiState.Ready(
                            account = acc,
                            activeServerId = if (TunnelManager.isUp) currentServerId else null,
                        )
                    }
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

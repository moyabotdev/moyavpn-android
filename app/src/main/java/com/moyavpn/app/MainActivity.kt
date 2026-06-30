package com.moyavpn.app

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moyavpn.app.data.Connection
import com.moyavpn.app.ui.MainScreen
import com.moyavpn.app.ui.MainViewModel
import com.moyavpn.app.ui.UiState
import com.moyavpn.app.ui.theme.MoyaTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // Merkt sich die Verbindung, die nach erteilter VPN-Erlaubnis gestartet werden soll.
    private var pendingConnection: Connection? = null

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingConnection?.let { vm.toggle(it) }
            }
            pendingConnection = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoyaTheme {
                val state by vm.state.collectAsStateWithLifecycle()

                // Solange ein Tunnel laeuft: Traffic alle 2s aktualisieren
                LaunchedEffect(state) {
                    while (state is UiState.Ready && (state as UiState.Ready).activeServerId != null) {
                        vm.refreshStats()
                        delay(2000)
                    }
                }

                MainScreen(
                    state = state,
                    onLogin = vm::login,
                    onToggle = { conn -> handleToggle(state, conn) },
                    onLogout = vm::logout,
                    onRetry = vm::retry,
                )
            }
        }
    }

    /** Beim Verbinden zuerst die VPN-Erlaubnis sicherstellen, beim Trennen direkt. */
    private fun handleToggle(state: UiState, conn: Connection) {
        val ready = state as? UiState.Ready
        val isActive = ready?.activeServerId == conn.serverId
        if (isActive) {
            vm.toggle(conn)   // Trennen — keine Erlaubnis noetig
            return
        }
        val prepare: Intent? = VpnService.prepare(this)
        if (prepare != null) {
            pendingConnection = conn
            vpnPermission.launch(prepare)
        } else {
            vm.toggle(conn)
        }
    }
}

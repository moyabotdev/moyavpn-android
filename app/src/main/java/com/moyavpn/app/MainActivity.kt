package com.moyavpn.app

import android.content.Intent
import android.net.Uri
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
        handleDeepLink(intent)
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
                    onOpenBot = ::openRenew,
                    onGetAccess = ::openGetAccess,
                    onOpenSupport = ::openSupport,
                    onQuickConnect = vm::startTrial,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /** „Zeit nachkaufen" → Verlängerungs-Flow im Bot. */
    private fun openRenew() = openUrl("https://t.me/moyavpnbot?start=renew")

    /** Login „Zugang holen" → Bot zeigt direkt Code + Verbinden-Link (ein Tap). */
    private fun openGetAccess() = openUrl("https://t.me/moyavpnbot?start=app")

    /** „?" → Support-Chat im Bot. */
    private fun openSupport() = openUrl("https://t.me/moyavpnbot?start=support")

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /**
     * Liest den App-Code aus einem Deep Link und loggt automatisch ein.
     * Unterstützt:
     *   moyavpn://login?token=<code>
     *   https://app.moyabot.ru/i/<code>   (oder ?token=<code>)
     */
    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val token = data.getQueryParameter("token")
            ?: data.pathSegments?.lastOrNull()?.takeIf { it.startsWith("moya_") }
            ?: return
        if (token.isNotBlank()) vm.login(token)
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

package com.moyavpn.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.res.painterResource
import com.moyavpn.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moyavpn.app.data.Connection

@Composable
fun MainScreen(
    state: UiState,
    onLogin: (String) -> Unit,
    onToggle: (Connection) -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit,
    onOpenBot: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            is UiState.Loading -> CenterBox { CircularProgressIndicator() }
            is UiState.NeedsLogin -> LoginView(onLogin, onOpenBot)
            is UiState.Error -> CenterBox {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) { Text("Erneut versuchen") }
                    TextButton(onClick = onLogout) { Text("Anderen Code eingeben") }
                }
            }
            is UiState.Ready -> ReadyView(state, onToggle, onLogout, onOpenBot, onOpenSupport)
        }
    }
}

@Composable
private fun CenterBox(content: @Composable BoxScope.() -> Unit) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)

@Composable
private fun LoginView(onLogin: (String) -> Unit, onGetAccess: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.moyavpn_logo),
            contentDescription = "MoyaVPN",
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("MoyaVPN", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Gib deinen App-Zugangscode ein. Du bekommst ihn im MoyaBot über das Menü „App-Zugang“.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("App-Zugangscode") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onLogin(code) },
            enabled = code.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Anmelden") }

        Spacer(Modifier.height(20.dp))
        Text("Noch keinen Zugang?", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = onGetAccess,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Zugang über Telegram holen")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyView(
    state: UiState.Ready,
    onToggle: (Connection) -> Unit,
    onLogout: () -> Unit,
    onOpenBot: () -> Unit,
    onOpenSupport: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MoyaVPN", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSupport) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Support")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = "Abmelden")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Kopf: Nutzer + Ablauf + „Zeit nachkaufen“
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(state.account.user.name, style = MaterialTheme.typography.titleMedium)
                    state.account.user.expiresAt?.let {
                        Text("Gültig bis $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                FilledTonalButton(onClick = onOpenBot) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Zeit nachkaufen")
                }
            }

            if (state.activeServerId != null) {
                TrafficBar(state.rxBytes, state.txBytes)
            }

            state.connectError?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                ) {
                    Text(
                        "Verbindung fehlgeschlagen: $err",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Text(
                "Deine Verbindungen",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.account.connections) { conn ->
                    ConnectionCard(
                        conn = conn,
                        active = state.activeServerId == conn.serverId,
                        busy = state.connectingTo == conn.serverId,
                        onClick = { onToggle(conn) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficBar(rx: Long, tx: Long) {
    Surface(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("↓ ${humanBytes(rx)}", style = MaterialTheme.typography.bodyMedium)
            Text("↑ ${humanBytes(tx)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ConnectionCard(conn: Connection, active: Boolean, busy: Boolean, onClick: () -> Unit) {
    val disabled = conn.status != "active"
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(conn.flag ?: "🌐", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(conn.serverName, style = MaterialTheme.typography.titleMedium)
                val sub = when (conn.status) {
                    "active"   -> conn.expiresAt?.let { "bis $it" } ?: "aktiv"
                    "expired"  -> "abgelaufen"
                    else        -> "deaktiviert"
                }
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(12.dp))
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                FilledTonalButton(onClick = onClick, enabled = !disabled) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (active) "Trennen" else "Verbinden")
                }
            }
        }
    }
}

private fun humanBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}

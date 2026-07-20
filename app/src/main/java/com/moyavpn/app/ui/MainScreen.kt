package com.moyavpn.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.moyavpn.app.BuildConfig
import com.moyavpn.app.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moyavpn.app.data.Connection
import com.moyavpn.app.data.ServerPing
import com.moyavpn.app.data.SplitTunnelStore
import com.moyavpn.app.data.UpdateInfo

@Composable
fun MainScreen(
    state: UiState,
    update: UpdateInfo?,
    favoriteId: String?,
    pings: Map<String, Int>,
    watchdogOn: Boolean,
    onWatchdog: (Boolean) -> Unit,
    onLogin: (String) -> Unit,
    onToggle: (Connection) -> Unit,
    onHeroTap: () -> Unit,
    onFavorite: (Connection) -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit,
    onOpenBot: () -> Unit,
    onGetAccess: () -> Unit,
    onOpenSupport: () -> Unit,
    onQuickConnect: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdate: (String) -> Unit,
    onDismissUpdate: () -> Unit,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            update?.let { UpdateBanner(it, onUpdate, onDismissUpdate) }
            Box(Modifier.weight(1f)) {
                when (state) {
                    is UiState.Loading -> CenterBox { CircularProgressIndicator() }
                    is UiState.NeedsLogin -> LoginView(onLogin, onGetAccess, onQuickConnect)
                    is UiState.Error -> CenterBox {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            TextButton(onClick = onLogout) { Text(stringResource(R.string.other_code)) }
                        }
                    }
                    is UiState.Ready -> ReadyView(
                        state, favoriteId, pings, watchdogOn, onWatchdog,
                        onToggle, onHeroTap, onFavorite,
                        onLogout, onOpenBot, onOpenSupport,
                        onGetAccess, onRefresh, onOpenSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable BoxScope.() -> Unit) =
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)

/** Update-Hinweis (nur direct-Variante liefert je ein UpdateInfo). */
@Composable
private fun UpdateBanner(info: UpdateInfo, onUpdate: (String) -> Unit, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.update_available),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                info.versionName?.let {
                    Text(
                        stringResource(R.string.update_to, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            Button(onClick = { onUpdate(info.url) }) { Text(stringResource(R.string.update_now)) }
        }
    }
}

@Composable
private fun LoginView(onLogin: (String) -> Unit, onGetAccess: () -> Unit, onQuickConnect: () -> Unit) {
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
            stringResource(R.string.login_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        if (BuildConfig.SHOW_PURCHASE) {
            // direct-Variante: 4h-Gratistest ist der auffaellige Primaer-Button.
            // Zweizeilig (fett + kleinere Unterzeile), Hoehe waechst mit dem Text.
            Button(
                onClick = onQuickConnect,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.trial_big),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.trial_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Upgrade: unbegrenzt & schnellere Server → Telegram-Bot (Zeit nachkaufen).
            OutlinedButton(onClick = onGetAccess, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.upgrade_btn))
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.have_code), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.code_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { onLogin(code) },
                enabled = code.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.login_button)) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onGetAccess) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.get_access))
            }
        } else {
            // play-Variante: unveraendert — Code-Login primaer, Test sekundaer.
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text(stringResource(R.string.code_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onLogin(code) },
                enabled = code.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.login_button)) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onQuickConnect,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quick_connect))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyView(
    state: UiState.Ready,
    favoriteId: String?,
    pings: Map<String, Int>,
    watchdogOn: Boolean,
    onWatchdog: (Boolean) -> Unit,
    onToggle: (Connection) -> Unit,
    onHeroTap: () -> Unit,
    onFavorite: (Connection) -> Unit,
    onLogout: () -> Unit,
    onOpenBot: () -> Unit,
    onOpenSupport: () -> Unit,
    onGetAccess: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.moyavpn_logo),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("MoyaVPN", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    IconButton(onClick = onOpenSupport) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.support))
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Logout, contentDescription = stringResource(R.string.logout))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Glanzstueck: animierter Verbindungs-Status auf einen Blick.
            // Tap auf die Animation verbindet den Favoriten / trennt.
            ConnectionHero(state, onHeroTap)

            // Kopf: Nutzer + Ablauf + „Zeit nachkaufen“
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(state.account.user.name, style = MaterialTheme.typography.titleMedium)
                }
                // Kauf-Link nur in der direct-Variante (Play-Store: ausgeblendet)
                if (BuildConfig.SHOW_PURCHASE) {
                    FilledTonalButton(onClick = onOpenBot) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.buy_time))
                    }
                }
            }

            // Trial-Hinweis: Zugang in Telegram holen, dann ausloggen & neu verbinden
            if (state.account.user.trial) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "⏳ " + stringResource(R.string.trial_notice),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (BuildConfig.SHOW_PURCHASE) {
                            Spacer(Modifier.height(8.dp))
                            FilledTonalButton(onClick = onGetAccess) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.get_access))
                            }
                        }
                    }
                }
            }

            if (state.activeServerId != null) {
                TrafficBar(state.rxBytes, state.txBytes)
                ConnectedTime()
            }
            // Free-Server: Tagesverbrauch + Upgrade-Anreiz (nur wenn der aktive Server der Free-Server ist).
            state.account.connections.firstOrNull { it.serverId == state.activeServerId }?.let { ac ->
                if (ac.serverId == "free" && ac.dailyLimitMb != null) {
                    FreeUsageCard(ac.dailyUsedMb ?: 0, ac.dailyLimitMb, onGetAccess)
                }
            }

            state.connectError?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.connect_failed, err),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            // Verbindungswächter — nur mit ≥2 Servern (Premium) sinnvoll; sonst Upsell.
            val premiumServers = state.account.connections.count { it.status == "active" } >= 2
            var showWatchdogUpsell by remember { mutableStateOf(false) }
            Surface(
                onClick = { if (premiumServers) onWatchdog(!watchdogOn) else showWatchdogUpsell = true },
                shape = RoundedCornerShape(12.dp),
                color = if (watchdogOn && premiumServers) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🛡", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.watchdog_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(
                                when {
                                    !premiumServers -> R.string.watchdog_premium_only
                                    watchdogOn      -> R.string.watchdog_on
                                    else            -> R.string.watchdog_off
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (premiumServers) {
                        Switch(checked = watchdogOn, onCheckedChange = onWatchdog)
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (showWatchdogUpsell) {
                AlertDialog(
                    onDismissRequest = { showWatchdogUpsell = false },
                    icon = { Text("🛡", style = MaterialTheme.typography.headlineSmall) },
                    title = { Text(stringResource(R.string.watchdog_premium_title)) },
                    text = { Text(stringResource(R.string.watchdog_premium_desc)) },
                    confirmButton = {
                        TextButton(onClick = { showWatchdogUpsell = false; onGetAccess() }) {
                            Text(stringResource(R.string.upgrade_btn))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showWatchdogUpsell = false }) {
                            Text(stringResource(R.string.close))
                        }
                    },
                )
            }

            Text(
                stringResource(R.string.your_connections),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.account.connections) { conn ->
                    ConnectionCard(
                        conn = conn,
                        active = state.activeServerId == conn.serverId,
                        busy = state.connectingTo == conn.serverId,
                        favorite = favoriteId == conn.serverId,
                        pingMs = pings[conn.serverId],
                        onClick = { onToggle(conn) },
                        onFavorite = { onFavorite(conn) },
                    )
                }
                // Ablaufzeit einmal, klein und ganz unten (gilt für alle Server).
                state.account.user.expiresAt?.let { exp ->
                    item {
                        Text(
                            stringResource(R.string.valid_until, exp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animierter Status-Kopf: pulsierendes Schild in Akzentfarbe je nach Zustand.
 * Tap auf das Schild verbindet den Favoriten bzw. trennt (via [onTap]).
 */
@Composable
private fun ConnectionHero(state: UiState.Ready, onTap: () -> Unit) {
    val active = state.account.connections.firstOrNull { it.serverId == state.activeServerId }
    val target = state.account.connections.firstOrNull { it.serverId == state.connectingTo }
    val connecting = state.connectingTo != null
    val connected = active != null

    val accent by animateColorAsState(
        targetValue = when {
            connected -> MaterialTheme.colorScheme.primary
            connecting -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.outline
        },
        label = "accent",
    )
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "pulseValue",
    )
    val animate = connected || connecting

    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(132.dp)
                // Tap togglet die Verbindung; waehrend des Verbindens gesperrt.
                .clickable(enabled = !connecting) { onTap() },
        ) {
            if (animate) {
                // nach aussen laufender, verblassender Ring
                Box(
                    Modifier
                        .size(120.dp)
                        .scale(0.6f + pulse * 0.5f)
                        .alpha((1f - pulse).coerceIn(0f, 1f))
                        .background(accent.copy(alpha = 0.35f), CircleShape),
                )
            }
            Box(
                Modifier.size(92.dp).background(accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                connected -> stringResource(R.string.status_connected)
                connecting -> stringResource(R.string.status_connecting)
                else -> stringResource(R.string.status_disconnected)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        (active ?: target)?.let { srv ->
            Text(
                "${srv.flag ?: "🌐"}  ${srv.serverName}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Tap-Hinweis: nur wenn nichts laeuft und nicht gerade verbunden wird.
        if (!connected && !connecting) {
            Text(
                stringResource(R.string.hero_tap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Free-Server-Tagesverbrauch: Fortschrittsbalken „X / 100 MB heute" + Restvolumen
 * + Upgrade-CTA. Praktischer Ersteindruck fürs Gratis-Tier — Design wie Premium.
 */
@Composable
private fun FreeUsageCard(usedMb: Int, limitMb: Int, onUpgrade: () -> Unit) {
    val frac = if (limitMb > 0) (usedMb.toFloat() / limitMb).coerceIn(0f, 1f) else 0f
    val remaining = (limitMb - usedMb).coerceAtLeast(0)
    val nearLimit = frac >= 0.8f
    val onBg = MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = onBg)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.free_usage_title), style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = onBg, modifier = Modifier.weight(1f))
                Text("$usedMb / $limitMb MB", style = MaterialTheme.typography.bodyMedium, color = onBg)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.fillMaxWidth().height(8.dp)
                    .background(onBg.copy(alpha = 0.15f), RoundedCornerShape(4.dp)),
            ) {
                Box(
                    Modifier.fillMaxWidth(frac).height(8.dp).background(
                        if (nearLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(4.dp),
                    ),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.free_usage_remaining, remaining),
                style = MaterialTheme.typography.labelSmall, color = onBg.copy(alpha = 0.8f))
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.upgrade_btn))
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
private fun ConnectionCard(
    conn: Connection,
    active: Boolean,
    busy: Boolean,
    favorite: Boolean,
    pingMs: Int?,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
) {
    val disabled = conn.status != "active"
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, top = 5.dp, bottom = 5.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (conn.serverId == "free") {
                Icon(
                    Icons.Default.CardGiftcard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Text(conn.flag ?: "🌐", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(conn.serverName, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (disabled) {
                        Text(
                            if (conn.status == "expired") stringResource(R.string.status_expired)
                            else stringResource(R.string.status_disabled),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        // Ablaufzeit steht global unten (gilt für alle) — hier nur Empfang.
                        SignalBars(pingMs)
                    }
                }
            }
            // Favoriten-Stern: nur fuer aktive Server sinnvoll.
            if (!disabled) {
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = stringResource(
                            if (favorite) R.string.favorite_remove else R.string.favorite_set
                        ),
                        tint = if (favorite) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                FilledTonalButton(onClick = onClick, enabled = !disabled) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (active) R.string.disconnect else R.string.connect))
                }
            }
        }
    }
}

/** Verbundene Zeit (Session-Dauer) des aktiven Servers — tickt jede Sekunde. */
@Composable
private fun ConnectedTime() {
    val since by com.moyavpn.app.vpn.VpnState.connectedSince.collectAsState()
    val start = since ?: return
    var now by remember(start) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val secs = ((now - start) / 1000).coerceAtLeast(0)
    val txt = if (secs >= 3600) "%d:%02d:%02d".format(secs / 3600, (secs % 3600) / 60, secs % 60)
              else "%02d:%02d".format(secs / 60, secs % 60)
    Text(
        stringResource(R.string.connected_for, txt),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
    )
}

/**
 * Kompakte Empfangs-Anzeige (nur Info, statt ms-Zahlen — spart Platz):
 * 4 kleine Balken wie ein Empfangsbereich. Grün = gut (<80 ms), gelb = ok
 * (<180 ms), rot = schwach; keine gefüllten Balken = nicht erreichbar.
 */
@Composable
private fun SignalBars(pingMs: Int?) {
    val (filled, color) = when {
        pingMs == null || pingMs == ServerPing.UNREACHABLE -> 0 to MaterialTheme.colorScheme.outline
        pingMs < 80  -> 4 to Color(0xFF2E7D32)
        pingMs < 180 -> 3 to Color(0xFFF9A825)
        else         -> 2 to Color(0xFFC62828)
    }
    val muted = MaterialTheme.colorScheme.outlineVariant
    Row(verticalAlignment = Alignment.Bottom) {
        listOf(6.dp, 9.dp, 12.dp, 15.dp).forEachIndexed { i, h ->
            Box(
                Modifier
                    .padding(end = 2.dp)
                    .width(3.dp)
                    .height(h)
                    .background(if (i < filled) color else muted, RoundedCornerShape(1.dp))
            )
        }
    }
}

/**
 * Einstellungen: Always-on-Verknuepfung (oeffnet Android-VPN-Settings) und
 * Split-Tunneling (VPN nur fuer / ausser bestimmte Apps).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsUi,
    onBack: () -> Unit,
    onMode: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onAlwaysOn: () -> Unit,
    onWatchdog: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // ── Verbindungswaechter (Auto-Failover) ──
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.watchdog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.watchdog_desc), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = settings.watchdog, onCheckedChange = onWatchdog)
            }

            HorizontalDivider()

            // ── Always-on ──
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.always_on_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.always_on_desc), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onAlwaysOn) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.always_on_open))
                }
            }

            HorizontalDivider()

            // ── Split-Tunneling ──
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.split_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.split_desc), style = MaterialTheme.typography.bodySmall)
            }
            SplitModeOption(R.string.split_off, SplitTunnelStore.MODE_OFF, settings.mode, onMode)
            SplitModeOption(R.string.split_include, SplitTunnelStore.MODE_INCLUDE, settings.mode, onMode)
            SplitModeOption(R.string.split_exclude, SplitTunnelStore.MODE_EXCLUDE, settings.mode, onMode)

            if (settings.mode != SplitTunnelStore.MODE_OFF) {
                Text(
                    stringResource(R.string.split_selected_count, settings.selected.size),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                if (settings.loadingApps) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(settings.apps) { app ->
                            AppRow(app, checked = app.pkg in settings.selected) { onToggleApp(app.pkg) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitModeOption(labelRes: Int, value: String, current: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onSelect(value) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = current == value, onClick = { onSelect(value) })
        Spacer(Modifier.width(8.dp))
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyMedium)
            Text(app.pkg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
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

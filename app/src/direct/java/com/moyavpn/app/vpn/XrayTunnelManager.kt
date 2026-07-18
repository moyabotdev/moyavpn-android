package com.moyavpn.app.vpn

import android.content.Context
import android.content.Intent
import com.moyavpn.app.data.XrayParams
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Startet/stoppt den XRay-VpnService (nur direct-Flavor). Pendant zu [TunnelManager]
 * (AmneziaWG), aber fuer VLESS/Reality via [MoyaXrayVpnService].
 *
 * VpnService.prepare() muss der Aufrufer VORHER sichergestellt haben (wie beim AWG-Pfad).
 */
object XrayTunnelManager {

    /**
     * Verbindet und wartet, bis der Service den Tunnel wirklich stehen hat.
     * Wirft mit der *echten* Fehlermeldung aus dem Service, wenn xray die Config
     * ablehnt oder das TUN nicht aufgebaut werden kann.
     */
    suspend fun connect(context: Context, params: XrayParams) {
        // Frisches Diagnose-Log je Verbindungsversuch (der „Log teilen“-Knopf liest es).
        val logFile = File(context.applicationContext.filesDir, XrayConfigBuilder.LOG_FILE)
        try { logFile.delete() } catch (_: Exception) {}
        val config = XrayConfigBuilder.build(params, logFile.absolutePath)
        val started = MoyaXrayVpnService.armStart()
        val i = Intent(context.applicationContext, MoyaXrayVpnService::class.java).apply {
            action = MoyaXrayVpnService.ACTION_START
            putExtra(MoyaXrayVpnService.EXTRA_CONFIG, config)
        }
        context.applicationContext.startService(i)
        try {
            withTimeout(15_000) { started.await() }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("XRay-Start hat nicht geantwortet (Zeitueberschreitung)")
        }
    }

    /**
     * Trennt und **wartet**, bis hev + xray + TUN vollstaendig abgebaut sind. Das ist
     * beim Umschalten auf einen AWG-Server zwingend: sonst wuerde AWG das TUN
     * uebernehmen, waehrend hev es noch liest → nativer Absturz (App schliesst sich).
     * No-op, wenn kein XRay-Tunnel laeuft.
     */
    suspend fun disconnect(context: Context) {
        if (!MoyaXrayVpnService.running) return
        val stopped = MoyaXrayVpnService.armStop()
        val i = Intent(context.applicationContext, MoyaXrayVpnService::class.java).apply {
            action = MoyaXrayVpnService.ACTION_STOP
        }
        context.applicationContext.startService(i)
        try {
            withTimeout(8_000) { stopped.await() }
        } catch (_: TimeoutCancellationException) {
            // Nicht blockieren; Service raeumt sich ohnehin selbst ab.
        }
    }

    val isRunning: Boolean get() = MoyaXrayVpnService.running
}

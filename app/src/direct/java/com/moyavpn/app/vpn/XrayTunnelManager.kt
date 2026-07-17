package com.moyavpn.app.vpn

import android.content.Context
import android.content.Intent
import com.moyavpn.app.data.XrayParams
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

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
        val config = XrayConfigBuilder.build(params)
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

    fun disconnect(context: Context) {
        val i = Intent(context.applicationContext, MoyaXrayVpnService::class.java).apply {
            action = MoyaXrayVpnService.ACTION_STOP
        }
        context.applicationContext.startService(i)
    }

    val isRunning: Boolean get() = MoyaXrayVpnService.running
}

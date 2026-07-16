package com.moyavpn.app.vpn

import android.content.Context
import android.content.Intent
import com.moyavpn.app.data.XrayParams

/**
 * Startet/stoppt den XRay-VpnService (nur direct-Flavor). Pendant zu [TunnelManager]
 * (AmneziaWG), aber fuer VLESS/Reality via [MoyaXrayVpnService].
 *
 * VpnService.prepare() muss der Aufrufer VORHER sichergestellt haben (wie beim AWG-Pfad).
 */
object XrayTunnelManager {

    fun connect(context: Context, params: XrayParams) {
        val config = XrayConfigBuilder.build(params)
        val i = Intent(context.applicationContext, MoyaXrayVpnService::class.java).apply {
            action = MoyaXrayVpnService.ACTION_START
            putExtra(MoyaXrayVpnService.EXTRA_CONFIG, config)
        }
        context.applicationContext.startService(i)
    }

    fun disconnect(context: Context) {
        val i = Intent(context.applicationContext, MoyaXrayVpnService::class.java).apply {
            action = MoyaXrayVpnService.ACTION_STOP
        }
        context.applicationContext.startService(i)
    }

    val isRunning: Boolean get() = MoyaXrayVpnService.running
}

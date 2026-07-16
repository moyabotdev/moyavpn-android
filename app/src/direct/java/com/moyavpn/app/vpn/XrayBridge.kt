package com.moyavpn.app.vpn

import android.content.Context
import com.moyavpn.app.data.XrayParams

/**
 * Flavor-Brücke zum XRay-Pfad. direct-Impl → echter [XrayTunnelManager].
 * (play-Flavor hat eine no-op-Variante gleichen Namens → main bleibt flavor-neutral.)
 */
object XrayBridge {
    fun connect(context: Context, params: XrayParams) = XrayTunnelManager.connect(context, params)
    fun disconnect(context: Context) = XrayTunnelManager.disconnect(context)
    val isRunning: Boolean get() = XrayTunnelManager.isRunning
}

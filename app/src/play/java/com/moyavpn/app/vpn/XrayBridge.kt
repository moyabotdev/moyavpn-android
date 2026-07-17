package com.moyavpn.app.vpn

import android.content.Context
import com.moyavpn.app.data.XrayParams

/**
 * play-Flavor: XRay-Core ist im Store-Build NICHT enthalten → no-op.
 * (Play-Clients bekommen vom Backend ohnehin keine xray-Verbindungen.)
 */
object XrayBridge {
    suspend fun connect(context: Context, params: XrayParams): Unit =
        throw UnsupportedOperationException("XRay ist im Play-Build nicht enthalten")
    suspend fun disconnect(context: Context) { }
    val isRunning: Boolean get() = false
}

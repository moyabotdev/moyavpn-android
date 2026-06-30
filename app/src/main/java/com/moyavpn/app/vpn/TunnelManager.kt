package com.moyavpn.app.vpn

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * Duenne Huelle um den WireGuard-GoBackend.
 *
 * MVP: nutzt com.wireguard.* (Standard-WireGuard).
 * Fuer AmneziaWG (DPI-resistent) spaeter org.amnezia.awg.* + die AWG-Parameter
 * aus [com.moyavpn.app.data.AwgParams] in die Config einsetzen.
 */
object TunnelManager {

    private const val TUNNEL_NAME = "moyavpn"

    @Volatile private var backend: Backend? = null
    private val tunnel = MoyaTunnel()

    private fun backend(context: Context): Backend =
        backend ?: synchronized(this) {
            backend ?: GoBackend(context.applicationContext).also { backend = it }
        }

    val isUp: Boolean
        get() = tunnel.state == Tunnel.State.UP

    /** Parst eine .conf und bringt den Tunnel hoch. Laeuft auf IO. */
    suspend fun connect(context: Context, wgConfig: String) = withContext(Dispatchers.IO) {
        val config = Config.parse(ByteArrayInputStream(wgConfig.toByteArray()))
        backend(context).setState(tunnel, Tunnel.State.UP, config)
    }

    suspend fun disconnect(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            backend(context).setState(tunnel, Tunnel.State.DOWN, null)
        }
    }

    /** Aktueller RX/TX in Bytes (fuer die Traffic-Anzeige). */
    suspend fun statistics(context: Context): Pair<Long, Long> = withContext(Dispatchers.IO) {
        runCatching {
            val s = backend(context).getStatistics(tunnel)
            s.totalRx() to s.totalTx()
        }.getOrDefault(0L to 0L)
    }

    private class MoyaTunnel : Tunnel {
        @Volatile var state: Tunnel.State = Tunnel.State.DOWN
        override fun getName() = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) { state = newState }
    }
}

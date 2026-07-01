package com.moyavpn.app.vpn

import android.content.Context
import org.amnezia.awg.backend.Backend
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/**
 * Hülle um das AmneziaWG-GoBackend (org.amnezia.awg.*).
 *
 * AmneziaWG = obfuskiertes WireGuard (DPI-resistent). Die Config aus dem
 * MoyaBot-Server enthält bereits die Obfuskations-Parameter (Jc/Jmin/.../H1-H4);
 * der AmneziaWG-Config-Parser liest sie direkt.
 *
 * Das tunnel-AAR wird in CI aus amnezia-vpn/amneziawg-android gebaut und nach
 * app/libs/awg-tunnel.aar gelegt (siehe .github/workflows/android.yml).
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

    /**
     * Parst eine .conf und bringt den Tunnel hoch. Läuft auf IO.
     *
     * [splitKey] ist entweder "IncludedApplications", "ExcludedApplications" oder
     * null (kein Split-Tunneling). Ist er gesetzt UND [packages] nicht leer, wird
     * die entsprechende Zeile in den [Interface]-Block der Config injiziert.
     * Standardnutzer (splitKey=null) bekommen die Config unveraendert.
     */
    suspend fun connect(
        context: Context,
        wgConfig: String,
        splitKey: String? = null,
        packages: List<String> = emptyList(),
    ) = withContext(Dispatchers.IO) {
        val effective = applySplit(wgConfig, splitKey, packages)
        val config = Config.parse(ByteArrayInputStream(effective.toByteArray()))
        backend(context).setState(tunnel, Tunnel.State.UP, config)
    }

    /** Fuegt IncludedApplications/ExcludedApplications in den [Interface]-Block ein. */
    private fun applySplit(conf: String, splitKey: String?, packages: List<String>): String {
        if (splitKey.isNullOrBlank() || packages.isEmpty()) return conf
        val line = "$splitKey = ${packages.joinToString(", ")}"
        var inserted = false
        return conf.lineSequence().flatMap { l ->
            if (!inserted && l.trim().equals("[Interface]", ignoreCase = true)) {
                inserted = true
                sequenceOf(l, line)
            } else sequenceOf(l)
        }.joinToString("\n")
    }

    suspend fun disconnect(context: Context) = withContext(Dispatchers.IO) {
        runCatching {
            backend(context).setState(tunnel, Tunnel.State.DOWN, null)
        }
    }

    /** Aktueller RX/TX in Bytes (für die Traffic-Anzeige). */
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

package com.moyavpn.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Grobe Erreichbarkeits-/Latenz-Messung pro Server — nur als **Info-Anzeige**.
 *
 * WICHTIG: WireGuard/AmneziaWG laeuft ueber UDP und antwortet nicht auf klassisches
 * ICMP-Ping. Der hier gemessene Wert ist daher ein *relativer* Reachability-Indikator
 * (Netzweg zum Endpoint-Host), keine exakte Handshake-RTT. Er wird bewusst NICHT zur
 * automatischen Serverwahl benutzt — der Nutzer waehlt seinen Favoriten selbst
 * (ein naher RU-Server haette den kleinsten Ping, wuerde die Sperre aber nicht umgehen).
 */
object ServerPing {

    /** Ergebnis: null = wird noch gemessen / unbekannt; -1 = nicht erreichbar; sonst ms. */
    const val UNREACHABLE = -1

    private val ENDPOINT = Regex("""(?im)^\s*Endpoint\s*=\s*(.+?):(\d+)\s*$""")

    /** Liest "Endpoint = host:port" aus einer .conf und liefert den Host (Domain oder IP). */
    fun endpointHost(config: String): String? =
        ENDPOINT.find(config)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Misst einen einzelnen Host. Versucht erst einen TCP-Connect auf Port 443
     * (haeufig offen, gibt echte RTT), faellt sonst auf ICMP/echo (isReachable)
     * zurueck. Liefert Millisekunden oder [UNREACHABLE].
     */
    suspend fun ping(host: String, timeoutMs: Int = 1500): Int = withContext(Dispatchers.IO) {
        runCatching {
            val addr = InetAddress.getByName(host)
            // 1) TCP-Connect (echte Round-Trip-Zeit, wenn ein TCP-Port offen ist)
            val tcp = tcpPing(addr, 443, timeoutMs)
            if (tcp >= 0) return@withContext tcp
            // 2) Fallback: ICMP-Echo / Port-7-Probe
            val start = System.nanoTime()
            if (addr.isReachable(timeoutMs)) {
                ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
            } else UNREACHABLE
        }.getOrDefault(UNREACHABLE)
    }

    private fun tcpPing(addr: InetAddress, port: Int, timeoutMs: Int): Int =
        runCatching {
            Socket().use { s ->
                val start = System.nanoTime()
                s.connect(InetSocketAddress(addr, port), timeoutMs)
                ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
            }
        }.getOrDefault(-1)

    /** Misst mehrere Server parallel. Key = serverId. */
    suspend fun pingAll(hostsByServer: Map<String, String>): Map<String, Int> = coroutineScope {
        hostsByServer.map { (id, host) ->
            async { id to ping(host) }
        }.awaitAll().toMap()
    }
}

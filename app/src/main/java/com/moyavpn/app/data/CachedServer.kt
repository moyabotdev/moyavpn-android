package com.moyavpn.app.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Leichtgewichtige Kopie einer aktiven Verbindung, lokal gespeichert (DataStore),
 * damit die Widgets und die Proxy-Activity den Tunnel ohne Netzabruf aufbauen
 * koennen. Enthaelt genau, was [com.moyavpn.app.vpn.TunnelManager.connect] braucht.
 */
data class CachedServer(
    val serverId: String,
    val serverName: String,
    val flag: String?,
    val config: String,
) {
    companion object {
        private val adapter by lazy {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val type = Types.newParameterizedType(List::class.java, CachedServer::class.java)
            moshi.adapter<List<CachedServer>>(type)
        }

        fun listToJson(list: List<CachedServer>): String = adapter.toJson(list)

        fun listFromJson(json: String?): List<CachedServer> =
            if (json.isNullOrBlank()) emptyList()
            else runCatching { adapter.fromJson(json) }.getOrNull().orEmpty()

        /** Wandelt aktive [Connection]s in speicherbare CachedServer um. */
        fun fromConnections(conns: List<Connection>): List<CachedServer> =
            conns.filter { it.status == "active" }
                .map { CachedServer(it.serverId, it.serverName, it.flag, it.config) }
    }
}

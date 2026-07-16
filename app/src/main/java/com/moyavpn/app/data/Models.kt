package com.moyavpn.app.data

import com.squareup.moshi.Json

/**
 * Antwort von GET /app/v1/account — alles was die App nach dem Login braucht.
 * Siehe API-Contract im README.
 *
 * Hinweis: Wir nutzen Moshis Reflection-Adapter (KotlinJsonAdapterFactory),
 * daher kein @JsonClass/Codegen noetig — haelt den Build ohne KSP einfach.
 */
data class AccountResponse(
    val user: AccountUser,
    val connections: List<Connection>,
)

data class AccountUser(
    val name: String,
    @Json(name = "expires_at") val expiresAt: String?,
    val trial: Boolean = false,
)

/** Antwort von POST /app/v1/trial. */
data class TrialResponse(
    val token: String,
    @Json(name = "expires_at") val expiresAt: String?,
)

data class Connection(
    @Json(name = "server_id")   val serverId: String,
    @Json(name = "server_name") val serverName: String,
    val flag: String?,
    val status: String,                       // "active" | "expired" | "disabled"
    @Json(name = "expires_at")  val expiresAt: String?,
    // protocol steuert, welcher Core den Tunnel faehrt. Fehlt das Feld (alte
    // Backends), gilt "awg" → voll rueckwaertskompatibel.
    //   "awg"         → GoBackend (AmneziaWG)
    //   "xray"        → Xray-core, Outbound VLESS+Reality
    //   "shadowsocks" → Xray-core, Outbound Shadowsocks   (gleicher Core wie xray)
    val protocol: String = "awg",
    val config: String = "",                  // AmneziaWG .conf (nur protocol=awg)
    val awg: AwgParams? = null,               // Obfuskations-Parameter (nur AmneziaWG)
    val xray: XrayParams? = null,             // VLESS/Reality-Parameter (protocol=xray)
    @Json(name = "shadowsocks")
    val shadowsocks: ShadowsocksParams? = null, // Shadowsocks-Parameter (protocol=shadowsocks)
)

/** AmneziaWG-Obfuskationsparameter — vom MVP-WireGuard-Core ignoriert. */
data class AwgParams(
    val jc: Int?,  val jmin: Int?, val jmax: Int?,
    val s1: Int?,  val s2: Int?,
    val h1: Long?, val h2: Long?,  val h3: Long?, val h4: Long?,
)

/**
 * VLESS-Reality-Parameter fuer den XRay-Core (nur direct-Flavor).
 * flow bleibt bewusst leer ("") — xtls-rprx-vision ist mit Mux inkompatibel,
 * und wir wollen maximale Client-Kompatibilitaet. Reales Reality tarnt bereits stark.
 */
data class XrayParams(
    val address: String,                      // Server-Host/IP
    val port: Int,
    val uuid: String,
    @Json(name = "public_key") val publicKey: String,
    @Json(name = "short_id")   val shortId: String,
    val sni: String,                          // Deko-Domain (serverName)
    val fingerprint: String = "chrome",
    val flow: String = "",
)

/** Shadowsocks-Parameter — laeuft ueber denselben Xray-core wie [XrayParams]. */
data class ShadowsocksParams(
    val address: String,
    val port: Int,
    val method: String,                       // z.B. "2022-blake3-aes-128-gcm" oder "aes-256-gcm"
    val password: String,
)

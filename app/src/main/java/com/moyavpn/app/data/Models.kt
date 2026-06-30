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
    val config: String,                       // vollstaendige WireGuard/AmneziaWG .conf
    val awg: AwgParams?,                       // Obfuskations-Parameter (nur AmneziaWG)
)

/** AmneziaWG-Obfuskationsparameter — vom MVP-WireGuard-Core ignoriert. */
data class AwgParams(
    val jc: Int?,  val jmin: Int?, val jmax: Int?,
    val s1: Int?,  val s2: Int?,
    val h1: Long?, val h2: Long?,  val h3: Long?, val h4: Long?,
)

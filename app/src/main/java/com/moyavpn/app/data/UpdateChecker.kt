package com.moyavpn.app.data

import com.moyavpn.app.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Inhalt von version.json (haengt am GitHub 'latest'-Release). */
data class UpdateInfo(
    @Json(name = "version_code") val versionCode: Int,
    @Json(name = "version_name") val versionName: String?,
    val url: String,
    val notes: String? = null,
)

/**
 * Prueft, ob es fuer die per Sideload (direct-Variante) installierte App eine
 * neuere Version gibt. Die Play-Variante braucht das nicht — dort updatet der
 * Store. Quelle ist eine statische version.json am 'latest'-Release (keine
 * GitHub-API-Ratelimits).
 */
object UpdateChecker {

    private const val MANIFEST_URL =
        "https://github.com/moyabotdev/moyavpn-android/releases/download/latest/version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(UpdateInfo::class.java)

    /**
     * Liefert die Update-Info nur, wenn die Remote-Version echt neuer ist als
     * die laufende — sonst null (kein Update, oder Netzfehler → still ignorieren).
     */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(MANIFEST_URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val info = adapter.fromJson(body) ?: return@use null
                if (info.versionCode > BuildConfig.VERSION_CODE) info else null
            }
        }.getOrNull()
    }
}

package com.moyavpn.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Eigener DataStore-Name (anderer als "moyavpn" in TokenStore) — zwei DataStores
// mit unterschiedlichem Namen sind erlaubt; gleicher Name zweimal wuerde crashen.
private val Context.splitStore by preferencesDataStore(name = "moyavpn_settings")

/**
 * Speichert die Split-Tunneling-Einstellung: Modus (aus/nur-diese/ausser-diese)
 * plus die gewaehlten App-Paketnamen. Wird beim Verbinden in die AmneziaWG-Config
 * injiziert (IncludedApplications / ExcludedApplications).
 */
class SplitTunnelStore(private val context: Context) {

    private val keyMode = stringPreferencesKey("split_mode")       // off | include | exclude
    private val keyPkgs = stringSetPreferencesKey("split_pkgs")

    val mode: Flow<String> = context.splitStore.data.map { it[keyMode] ?: MODE_OFF }
    val packages: Flow<Set<String>> = context.splitStore.data.map { it[keyPkgs] ?: emptySet() }

    suspend fun setMode(m: String) {
        context.splitStore.edit { it[keyMode] = m }
    }

    suspend fun setPackages(pkgs: Set<String>) {
        context.splitStore.edit { it[keyPkgs] = pkgs }
    }

    companion object {
        const val MODE_OFF = "off"
        const val MODE_INCLUDE = "include"   // VPN NUR fuer diese Apps
        const val MODE_EXCLUDE = "exclude"   // VPN fuer alle AUSSER diese Apps

        /** Config-Schluessel fuer den jeweiligen Modus (null = kein Split). */
        fun keyFor(mode: String): String? = when (mode) {
            MODE_INCLUDE -> "IncludedApplications"
            MODE_EXCLUDE -> "ExcludedApplications"
            else -> null
        }
    }
}

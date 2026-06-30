package com.moyavpn.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "moyavpn")

/** Speichert den App-Token persistent (ueberlebt App-Neustarts). */
class TokenStore(private val context: Context) {

    private val keyToken = stringPreferencesKey("app_token")

    val token: Flow<String?> = context.dataStore.data.map { it[keyToken] }

    suspend fun save(token: String) {
        context.dataStore.edit { it[keyToken] = token.trim() }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(keyToken) }
    }
}

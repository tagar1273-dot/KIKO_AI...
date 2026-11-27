package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kikoDataStore: DataStore<Preferences> by preferencesDataStore(name = "kiko_local_prefs")

data class LocalProviderConfig(
    val backendBearerToken: String = "",
    val preferredOpenAiModel: String = "gpt-4o-mini",
    val lastSyncedOfflineTime: String = "",
)

class LocalPreferencesStore(private val context: Context) {

    private object Keys {
        val BACKEND_BEARER_TOKEN = stringPreferencesKey("backend_bearer_token")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
    }

    val configFlow: Flow<LocalProviderConfig> = context.kikoDataStore.data.map { prefs ->
        LocalProviderConfig(
            backendBearerToken = prefs[Keys.BACKEND_BEARER_TOKEN] ?: "",
            preferredOpenAiModel = prefs[Keys.OPENAI_MODEL] ?: "gpt-4o-mini",
        )
    }

    suspend fun saveBackendToken(token: String) {
        context.kikoDataStore.edit { prefs ->
            if (token.isBlank()) {
                prefs.remove(Keys.BACKEND_BEARER_TOKEN)
            } else {
                prefs[Keys.BACKEND_BEARER_TOKEN] = token.trim()
            }
        }
    }

    suspend fun clearSensitiveTokens() {
        context.kikoDataStore.edit { prefs ->
            prefs.remove(Keys.BACKEND_BEARER_TOKEN)
        }
    }
}

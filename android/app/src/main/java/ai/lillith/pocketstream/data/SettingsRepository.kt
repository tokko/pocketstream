package ai.lillith.pocketstream.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_LAST_URL = stringPreferencesKey("last_url")
        val KEY_LAST_RESULT = stringPreferencesKey("last_result")
        const val DEFAULT_BACKEND_URL = "http://192.168.68.110:8080"
        const val DEFAULT_API_KEY = ""
    }

    val backendUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BACKEND_URL] ?: DEFAULT_BACKEND_URL
    }

    val apiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_API_KEY] ?: DEFAULT_API_KEY
    }

    val lastResult: Flow<Pair<String, String>?> = context.dataStore.data.map { prefs ->
        val url = prefs[KEY_LAST_URL]
        val result = prefs[KEY_LAST_RESULT]
        if (url != null && result != null) url to result else null
    }

    suspend fun saveBackendUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BACKEND_URL] = url
        }
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_API_KEY] = key
        }
    }

    suspend fun saveLastResult(url: String, result: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_URL] = url
            prefs[KEY_LAST_RESULT] = result
        }
    }
}

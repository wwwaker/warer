package com.wwwaker.warer_android.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "warer_settings")

class SettingsManager(private val context: Context) {

    companion object {
        /** 符号计算后端地址。与 `BuildConfig.DEFAULT_API_BASE` 保持一致。 */
        const val DEFAULT_BASE_URL = "http://localhost:8000/"

        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_PRECISION = intPreferencesKey("precision")
        private val KEY_ENABLE_HAPTIC = booleanPreferencesKey("enable_haptic")
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_BASE_URL] ?: DEFAULT_BASE_URL
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "system"
    }

    val precision: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_PRECISION] ?: 15
    }

    val enableHaptic: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ENABLE_HAPTIC] ?: true
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = url
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    suspend fun setPrecision(value: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PRECISION] = value.coerceIn(1, 50)
        }
    }

    suspend fun setEnableHaptic(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENABLE_HAPTIC] = enabled
        }
    }
}

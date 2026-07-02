package com.netcoremessenger.core.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "netcore_prefs")

object PreferenceKeys {
    val ACCESS_TOKEN = stringPreferencesKey("access_token")
    val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    val USER_ID = longPreferencesKey("user_id")
    val DEVICE_ID = stringPreferencesKey("device_id")
    val LAST_SORT_KEY = longPreferencesKey("last_sort_key")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_STYLE = stringPreferencesKey("accent_style")
    val FONT_STYLE = stringPreferencesKey("font_style")
}

@Singleton
class TokenDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val accessToken: Flow<String?> = context.dataStore.data.map { it[PreferenceKeys.ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[PreferenceKeys.REFRESH_TOKEN] }
    val userId: Flow<Long?> = context.dataStore.data.map { it[PreferenceKeys.USER_ID] }
    val deviceId: Flow<String?> = context.dataStore.data.map { it[PreferenceKeys.DEVICE_ID] }
    val lastSortKey: Flow<Long?> = context.dataStore.data.map { it[PreferenceKeys.LAST_SORT_KEY] }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.ACCESS_TOKEN] = accessToken
            prefs[PreferenceKeys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun saveUserId(userId: Long) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.USER_ID] = userId
        }
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[PreferenceKeys.THEME_MODE] ?: "dark" }
    val accentStyle: Flow<String> = context.dataStore.data.map { it[PreferenceKeys.ACCENT_STYLE] ?: "indigo" }
    val fontStyle: Flow<String> = context.dataStore.data.map { it[PreferenceKeys.FONT_STYLE] ?: "default" }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.THEME_MODE] = mode
        }
    }

    suspend fun saveAccentStyle(style: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.ACCENT_STYLE] = style
        }
    }

    suspend fun saveFontStyle(style: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.FONT_STYLE] = style
        }
    }

    suspend fun saveDeviceId(deviceId: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.DEVICE_ID] = deviceId
        }
    }

    suspend fun saveLastSortKey(sortKey: Long) {
        context.dataStore.edit { prefs ->
            prefs[PreferenceKeys.LAST_SORT_KEY] = sortKey
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.ACCESS_TOKEN)
            prefs.remove(PreferenceKeys.REFRESH_TOKEN)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}

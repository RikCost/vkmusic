package com.vkmusic.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val isDemoMode: Boolean = false,
    val dynamicColor: Boolean = true,
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            isDemoMode = prefs[Keys.DEMO_MODE] ?: false,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
        )
    }

    suspend fun setDemoMode(value: Boolean) = dataStore.edit { it[Keys.DEMO_MODE] = value }

    suspend fun setDynamicColor(value: Boolean) = dataStore.edit { it[Keys.DYNAMIC_COLOR] = value }

    private object Keys {
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}

package com.vkmusic.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class VkSession(val accessToken: String, val userId: Long)

@Singleton
class SessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: KeystoreCipher,
    private val json: Json,
) {
    val session: Flow<VkSession?> = dataStore.data.map { prefs ->
        prefs[KEY]?.let { encrypted ->
            runCatching { json.decodeFromString(VkSession.serializer(), cipher.decrypt(encrypted)) }.getOrNull()
        }
    }

    suspend fun current(): VkSession? = session.first()

    suspend fun save(session: VkSession) {
        val encrypted = cipher.encrypt(json.encodeToString(VkSession.serializer(), session))
        dataStore.edit { it[KEY] = encrypted }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("vk_session")
    }
}

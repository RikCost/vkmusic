package com.vkmusic.data.auth

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vkmusic.data.web.CookiePersistence
import com.vkmusic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Веб-сессия VK: токен из login.vk.ru/?act=web_token и момент его получения (обновляется раз в 5 минут).
 * Сами cookie лежат отдельным ключом. [web] = false у сессий старого входа по паролю — они больше не годятся.
 */
@Serializable
data class VkSession(
    val accessToken: String,
    val userId: Long,
    val obtainedAtMs: Long = 0,
    val web: Boolean = false,
)

@Singleton
class SessionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: KeystoreCipher,
    private val json: Json,
    @param:ApplicationScope private val scope: CoroutineScope,
) : CookiePersistence {

    private val cookieMutex = Mutex()

    val session: Flow<VkSession?> = dataStore.data.map { prefs ->
        prefs[KEY]?.let { encrypted ->
            runCatching { json.decodeFromString(VkSession.serializer(), cipher.decrypt(encrypted)) }.getOrNull()
        }?.takeIf { it.web }
    }

    suspend fun current(): VkSession? = session.first()

    suspend fun save(session: VkSession) {
        val encrypted = cipher.encrypt(json.encodeToString(VkSession.serializer(), session))
        dataStore.edit { it[KEY] = encrypted }
    }

    suspend fun clear() = cookieMutex.withLock {
        dataStore.edit {
            it.remove(KEY)
            it.remove(COOKIES_KEY)
        }
    }

    override suspend fun loadCookies(): String? =
        dataStore.data.first()[COOKIES_KEY]?.let { runCatching { cipher.decrypt(it) }.getOrNull() }

    override fun saveCookies(serialized: String?) {
        scope.launch {
            cookieMutex.withLock {
                // Запись идёт в фоне без вызывающего: сбой диска (fsync) иначе роняет приложение.
                // Cookie остаются в памяти jar и сохранятся со следующим ответом VK.
                try {
                    dataStore.edit { prefs ->
                        if (serialized == null) prefs.remove(COOKIES_KEY) else prefs[COOKIES_KEY] = cipher.encrypt(serialized)
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "cookies not saved", e)
                }
            }
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("vk_session")
        val COOKIES_KEY = stringPreferencesKey("vk_web_cookies")
        const val TAG = "SessionStore"
    }
}

package com.vkmusic.data.web

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Куда сохранять cookie веб-сессии (в приложении — зашифрованно в DataStore). */
interface CookiePersistence {
    suspend fun loadCookies(): String?
    fun saveCookies(serialized: String?)
}

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
)

/**
 * Cookie веб-сессии VK. Meridius хранит их в файле (tough-cookie-file-store) вместе с сессионными —
 * иначе после перезапуска пропадает remixsid. Здесь так же: сохраняются все cookie, включая сессионные.
 */
class PersistentCookieJar(
    private val persistence: CookiePersistence,
    private val json: Json,
) : CookieJar {

    private val serializer = ListSerializer(StoredCookie.serializer())
    private val cookies = mutableListOf<Cookie>()

    @Volatile
    private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return
        val stored = persistence.loadCookies()?.let { raw ->
            runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
        }.orEmpty()
        synchronized(this) {
            if (loaded) return
            stored.mapTo(cookies) { it.toCookie() }
            loaded = true
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        cookies.forEach(::put)
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.persistent && it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }

    /** Добавить cookie, полученные вне OkHttp (из WebView). */
    @Synchronized
    fun import(url: HttpUrl, cookies: List<Cookie>) {
        loaded = true
        cookies.forEach(::put)
        persist()
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        loaded = true
        persistence.saveCookies(null)
    }

    private fun put(cookie: Cookie) {
        cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
        if (!cookie.persistent || cookie.expiresAt > System.currentTimeMillis()) cookies += cookie
    }

    private fun persist() {
        persistence.saveCookies(json.encodeToString(serializer, cookies.map { it.toStored() }))
    }

    private fun Cookie.toStored() = StoredCookie(
        name = name,
        value = value,
        expiresAt = if (persistent) expiresAt else Long.MAX_VALUE,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        hostOnly = hostOnly,
    )

    private fun StoredCookie.toCookie(): Cookie = Cookie.Builder()
        .name(name)
        .value(value)
        .path(path)
        .apply {
            if (hostOnly) hostOnlyDomain(domain) else domain(domain)
            if (expiresAt != Long.MAX_VALUE) expiresAt(expiresAt)
            if (secure) secure()
            if (httpOnly) httpOnly()
        }
        .build()
}

package com.vkmusic.data.auth

import com.vkmusic.data.web.VkQrAuth
import com.vkmusic.data.web.VkWebClient
import com.vkmusic.data.web.VkWebConfig
import com.vkmusic.data.web.WebToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Веб-сессия VK как в Meridius: cookie + токен веб-клиента, который обновляется по cookie
 * каждые [VkWebConfig.WEB_TOKEN_REFRESH_MS] (стор `vk/REFRESH_WEBTOKEN`).
 */
@Singleton
class WebSessionManager @Inject constructor(
    private val sessionStore: SessionStore,
    private val web: VkWebClient,
    private val qrAuth: VkQrAuth,
) {
    private val refreshMutex = Mutex()

    /** Токен для официального API; обновляется, если устарел. null — сессии нет. */
    suspend fun accessToken(): String? {
        val session = sessionStore.current() ?: return null
        if (System.currentTimeMillis() - session.obtainedAtMs < VkWebConfig.WEB_TOKEN_REFRESH_MS) return session.accessToken
        return refreshMutex.withLock {
            val latest = sessionStore.current() ?: return@withLock null
            if (System.currentTimeMillis() - latest.obtainedAtMs < VkWebConfig.WEB_TOKEN_REFRESH_MS) return@withLock latest.accessToken
            // Сеть недоступна — работаем со старым токеном; VK сам ответит ошибкой, если он истёк.
            runCatching { obtain() }.getOrNull()?.accessToken ?: latest.accessToken
        }
    }

    suspend fun userId(): Long? = sessionStore.current()?.userId

    /**
     * После QR или WebView: cookie уже в jar, получить токен и сохранить сессию.
     * Успешный web_token и есть признак рабочей веб-сессии.
     */
    suspend fun completeLogin(): VkSession {
        val session = obtain()
        // meridius-core login(): главная vk.ru по cookie — VK досоздаёт веб-сессию; результат не блокирует вход.
        runCatching { isWebSessionAlive() }
        return session
    }

    /** Как `login()` в Meridius: на главной vk.ru нет формы входа — веб-сессия жива. */
    suspend fun isWebSessionAlive(): Boolean = !web.get(VkWebConfig.BASE_URL).contains(LOGIN_PAGE_MARKER)

    suspend fun signOut() {
        web.clearSession()
        sessionStore.clear()
    }

    private suspend fun obtain(): VkSession {
        val token: WebToken = qrAuth.webToken()
        return VkSession(token.accessToken, token.userId, System.currentTimeMillis(), web = true).also { sessionStore.save(it) }
    }

    private companion object {
        // regex ACTION_LOGIN из meridius-core: признак страницы входа VK ID.
        const val LOGIN_PAGE_MARKER = "Index.initRedesignedVkId"
    }
}

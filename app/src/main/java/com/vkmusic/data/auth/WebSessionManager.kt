package com.vkmusic.data.auth

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.CancellationException
import com.vkmusic.data.web.VkSessionExpiredException
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

    // Канал, а не SharedFlow: событие ждёт подписчика. Сессия часто оказывается истёкшей на первом же
    // запросе после запуска, когда экран ещё не подписался, — раньше оно терялось, и пользователь
    // оставался на главной с ошибкой «Сессия VK истекла» и бесполезной кнопкой «Повторить».
    private val _sessionExpired = Channel<Unit>(Channel.CONFLATED)

    /** VK явно сообщил, что сессии больше нет: интерфейс выходит на экран входа. Подписчик один — корневой экран. */
    val sessionExpired: Flow<Unit> = _sessionExpired.receiveAsFlow()

    // После сетевого сбоя web_token не запрашивается на каждом запросе — только раз в [REFRESH_BACKOFF_MS].
    @Volatile
    private var refreshBlockedUntil = 0L

    init {
        web.onSessionLost = { _sessionExpired.trySend(Unit) }
    }

    /** Токен для официального API; обновляется, если устарел. null — сессии нет. */
    suspend fun accessToken(): String? {
        val session = sessionStore.current() ?: return null
        if (System.currentTimeMillis() - session.obtainedAtMs < VkWebConfig.WEB_TOKEN_REFRESH_MS) return session.accessToken
        return refreshMutex.withLock {
            val latest = sessionStore.current() ?: return@withLock null
            if (System.currentTimeMillis() - latest.obtainedAtMs < VkWebConfig.WEB_TOKEN_REFRESH_MS) return@withLock latest.accessToken
            if (System.currentTimeMillis() < refreshBlockedUntil) return@withLock latest.accessToken
            // Сеть недоступна — работаем со старым токеном; VK сам ответит ошибкой, если он истёк.
            tryObtain()?.accessToken ?: latest.accessToken
        }
    }

    /**
     * Официальный API отверг токен [rejectedToken] (ошибка 5): пробуем получить новый. Конец сессии — отказ VK
     * выдать токен или отказ принять только что выданный: VK закрывает сессию не сразу целиком, и какое-то
     * время web_token ещё отвечает, а API его токены уже не берёт. Без второго условия приложение в этот
     * промежуток зависало на ошибке с кнопкой «Повторить», которая ничего не могла исправить.
     */
    suspend fun onApiAuthError(rejectedToken: String) {
        refreshMutex.withLock {
            val session = sessionStore.current() ?: return
            // Отвергнут уже заменённый токен: параллельный запрос успел его обновить.
            if (session.accessToken != rejectedToken) return
            if (isFreshToken(session.obtainedAtMs, System.currentTimeMillis())) _sessionExpired.trySend(Unit) else tryObtain()
        }
    }

    private suspend fun tryObtain(): VkSession? = try {
        obtain()
    } catch (e: CancellationException) {
        throw e
    } catch (e: VkSessionExpiredException) {
        _sessionExpired.trySend(Unit)
        null
    } catch (e: Exception) {
        refreshBlockedUntil = System.currentTimeMillis() + REFRESH_BACKOFF_MS
        null
    }

    suspend fun userId(): Long? = sessionStore.current()?.userId

    /**
     * После QR или WebView: cookie уже в jar, получить токен и сохранить сессию.
     * Успешный web_token и есть признак рабочей веб-сессии.
     */
    suspend fun completeLogin(): VkSession {
        val session = obtain()
        // meridius-core login(): главная vk.ru по cookie — VK досоздаёт веб-сессию; результат не блокирует вход.
        runCatching { web.warmUpSession(force = true) }
        return session
    }

    suspend fun signOut() {
        web.clearSession()
        sessionStore.clear()
    }

    private suspend fun obtain(): VkSession {
        val generation = sessionStore.generation
        val token: WebToken = qrAuth.webToken()
        // Если пока шёл запрос пользователь вышел, сохранение отбрасывается — выход не отменяется.
        return VkSession(token.accessToken, token.userId, System.currentTimeMillis(), web = true).also { sessionStore.save(it, generation) }
    }

    internal companion object {
        // Токен моложе этого отвергнут не из-за возраста: его обновляют раз в пять минут.
        private const val FRESH_TOKEN_MS = 60_000L

        fun isFreshToken(obtainedAtMs: Long, nowMs: Long): Boolean = nowMs - obtainedAtMs < FRESH_TOKEN_MS
        const val REFRESH_BACKOFF_MS = 30_000L
    }
}

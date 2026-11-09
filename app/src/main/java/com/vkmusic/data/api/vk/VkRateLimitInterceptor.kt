package com.vkmusic.data.api.vk

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Темп запросов к VK: короткая пачка проходит сразу (страница при открытии тоже шлёт несколько запросов
 * подряд), а дальше — не чаще одного в [intervalMs]. Сотни запросов подряд без пауз (длинная библиотека,
 * ссылки на весь плейлист) — признак бота, а не человека в браузере.
 */
internal class RequestPacer(private val burst: Int, private val intervalMs: Long) {
    private var tokens = burst.toDouble()
    private var updatedAtMs = 0L

    /** Сколько подождать перед запросом, пришедшим в [nowMs]. Место в очереди занимается сразу. */
    @Synchronized
    fun reserve(nowMs: Long): Long {
        tokens = (tokens + (nowMs - updatedAtMs).coerceAtLeast(0) / intervalMs.toDouble()).coerceAtMost(burst.toDouble())
        updatedAtMs = nowMs
        tokens -= 1
        return if (tokens >= 0) 0 else (-tokens * intervalMs).toLong()
    }
}

/** Притормаживает запросы к API и веб-версии VK. Аудио с CDN не трогает: там паузы — это заикание плеера. */
class VkRateLimitInterceptor : Interceptor {
    private val pacer = RequestPacer(BURST, INTERVAL_MS)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host.isVkApiHost) {
            val wait = pacer.reserve(System.currentTimeMillis())
            if (wait > 0) Thread.sleep(wait)
        }
        return chain.proceed(request)
    }

    private companion object {
        const val BURST = 5
        const val INTERVAL_MS = 350L
    }
}

// Только хосты API и веб-версии: аудио и обложки лежат на других доменах.
private val API_HOSTS = setOf("vk.ru", "vk.com", "m.vk.ru", "api.vk.ru", "api.vk.com", "login.vk.ru", "id.vk.ru")

private val String.isVkApiHost: Boolean get() = this in API_HOSTS

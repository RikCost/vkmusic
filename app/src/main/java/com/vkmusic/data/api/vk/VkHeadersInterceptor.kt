package com.vkmusic.data.api.vk

import com.vkmusic.data.web.VkWebConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Для VK приложение — один клиент: браузер, в котором выполнен вход ([VkWebConfig.USER_AGENT]). Его
 * заголовки уходят и в API, и при загрузке аудио с CDN.
 *
 * Раньше эти запросы шли с User-Agent официального приложения VK для Android, хотя токен выдан
 * веб-сессии Firefox: одна сессия выглядела сразу двумя разными устройствами — так выглядит угнанный
 * токен, и VK отвечал на это сбросом сессии и просьбой сменить пароль.
 */
class VkHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.isVkHost) return chain.proceed(request)
        val builder = request.newBuilder().header("User-Agent", VkWebConfig.USER_AGENT)
        if (request.header("Accept-Language") == null) builder.header("Accept-Language", VkWebConfig.ACCEPT_LANGUAGE)
        // API и CDN страница vk.ru запрашивает как чужой домен — с Origin и Referer.
        if (request.header("Origin") == null) builder.header("Origin", VkWebConfig.WEB_ORIGIN)
        if (request.header("Referer") == null) builder.header("Referer", VkWebConfig.BASE_URL)
        return chain.proceed(builder.build())
    }
}

// Вместе с доменами, с которых VK отдаёт само аудио (ссылки ведут и на CDN «Одноклассников»).
private val VK_DOMAINS = listOf("vk.com", "vk.ru", "vk.me", "vkuseraudio.net", "vkuseraudio.com", "vk.video", "okcdn.ru", "mycdn.me", "vk-cdn.net")

internal val String.isVkHost: Boolean
    get() = VK_DOMAINS.any { this == it || endsWith(".$it") }

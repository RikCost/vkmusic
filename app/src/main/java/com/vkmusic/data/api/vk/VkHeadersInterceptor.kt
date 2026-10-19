package com.vkmusic.data.api.vk

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Эталонный клиент задаёт UA и X-VK-Android-Client одним общим билдером запросов (dex@0x120d4a—0x120dca),
 * то есть заголовки уходят и в api.*, и при скачивании ссылок/HLS. Повторяем это для VK-хостов.
 */
class VkHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.isVkHost) return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header("User-Agent", VkConfig.USER_AGENT)
                .header(VkConfig.ANDROID_CLIENT_HEADER, VkConfig.ANDROID_CLIENT_VALUE)
                .build(),
        )
    }
}

private val VK_DOMAINS = listOf("vk.com", "vk.ru", "vk.me", "vkuseraudio.net", "vk.video")

private val String.isVkHost: Boolean
    get() = VK_DOMAINS.any { this == it || endsWith(".$it") }

package com.vkmusic.data.api.vk

import com.vkmusic.BuildConfig

object VkConfig {
    // Хост для методов audio.* (токен, полученный через VK ID, работает и здесь).
    const val API_URL = "https://api.vk.com/method/"
    const val API_VERSION = "5.131"

    // User-Agent официального клиента: методы audio.* отдаются только им.
    const val USER_AGENT = "VKAndroidApp/8.175-51775 (Android 14; SDK 34; arm64-v8a; ru)"

    // Официальный клиент помечает этим заголовком каждый запрос (dex@0x120dbe: setProperty("X-VK-Android-Client", "new")).
    const val ANDROID_CLIENT_HEADER = "X-VK-Android-Client"
    const val ANDROID_CLIENT_VALUE = "new"
}

/**
 * Параметры авторизации через VK ID (экосистемный OAuth на api.vk.ru).
 *
 * CLIENT_ID и CLIENT_SECRET берутся из локального `local.properties` (в `.gitignore`):
 * это реквизиты эталонного Android-клиента, а не нашего приложения. вход по паролю с ними
 * работает только пока VK считает это приложение доверенным — см. docs/vk-auth.md.
 */
object VkIdConfig {
    const val API_URL = "https://api.vk.ru/method/"
    const val OAUTH_TOKEN_URL = "https://api.vk.ru/oauth/token"

    // Версия API и scope, с которыми эталонный клиент ходит в oauth/token и auth.getAnonymToken
    // (dex@0xc9cee 'v'='5.276', 0xc9c6c 'scope'='all').
    const val API_VERSION = "5.276"

    // Значения берутся из local.properties (vk.clientId / vk.clientSecret) через BuildConfig.
    val CLIENT_ID: String get() = BuildConfig.VK_CLIENT_ID
    val CLIENT_SECRET: String get() = BuildConfig.VK_CLIENT_SECRET

    const val SCOPE = "all"

    val isConfigured: Boolean get() = CLIENT_ID.isNotBlank() && CLIENT_SECRET.isNotBlank()
}

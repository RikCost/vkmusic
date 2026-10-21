package com.vkmusic.data.api.vk

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

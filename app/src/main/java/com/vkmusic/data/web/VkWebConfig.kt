package com.vkmusic.data.web

import java.util.Base64

/**
 * Параметры веб-сессии VK как у Meridius (meridius-core `configuration`, `service.json`).
 * Вход — QR-код VK ID на id.vk.ru, дальше всё держится на cookie веб-версии vk.ru,
 * а токен для официального API выдаёт login.vk.ru/?act=web_token.
 */
object VkWebConfig {
    const val BASE_URL = "https://vk.ru/"
    const val AL_AUDIO_URL = "https://vk.ru/al_audio.php"
    const val AUTH_PAGE_URL = "https://id.vk.ru/auth"
    const val GET_AUTH_CODE_URL = "https://api.vk.ru/method/auth.getAuthCode"
    const val CHECK_AUTH_CODE_URL = "https://api.vk.ru/method/auth.checkAuthCode"
    const val VALIDATE_AUTH_CODE_URL = "https://api.vk.ru/method/auth.validateAuthCode"
    const val CONNECT_CODE_AUTH_URL = "https://login.vk.ru/?act=connect_code_auth"
    const val WEB_TOKEN_URL = "https://login.vk.ru/?act=web_token"

    // configuration.auth.app — приложение страницы входа VK ID.
    const val AUTH_APP_ID = 7913379
    const val AUTH_API_VERSION = "5.258"

    // service.json → webToken: токен веб-клиента VK, обновляется раз в 5 минут (webTokenRefreshTimeout).
    const val WEB_TOKEN_APP_ID = 6287487
    const val WEB_TOKEN_VERSION = 1
    const val WEB_TOKEN_REFRESH_MS = 300_000L

    // configuration.headers: Meridius ходит в веб как обычный Firefox.
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:143.0) Gecko/20100101 Firefox/143.0"
    const val ID_ORIGIN = "https://id.vk.ru"

    // QR в Meridius опрашивается каждые 3 секунды.
    const val QR_POLL_INTERVAL_MS = 3_000L

    /** Домены, cookie которых составляют веб-сессию (переносятся и из WebView). */
    val SESSION_URLS = listOf("https://vk.ru/", "https://login.vk.ru/", "https://id.vk.ru/", "https://m.vk.ru/")

    /** configuration.auth.pageParams + uuid/app_id — страница входа, отдающая window.init. */
    fun authPageParams(uuid: String): Map<String, String> = linkedMapOf(
        "action" to Base64.getEncoder().encodeToString(QR_ACTION.encodeToByteArray()),
        "scheme" to "dark",
        "is_redesigned" to "1",
        "response_type" to "silent_token",
        "v" to "1.3.0",
        "redirect_uri" to BASE_URL,
        "uuid" to uuid,
        "app_id" to AUTH_APP_ID.toString(),
    )

    private const val QR_ACTION = """{"name":"qr_auth","token":"qr_auth_scanned","entry":{"source":"main","screen":"start"}}"""
}

package com.vkmusic.data.web

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Base64
import kotlin.random.Random

data class QrCode(val url: String, val expiresAtSec: Long)

sealed interface QrStatus {
    /** Код ещё не отсканирован или ждёт подтверждения в приложении. */
    data object Waiting : QrStatus

    /** VK просит ввести код из приложения (status 5). */
    data object NeedCode : QrStatus

    /** Вход подтверждён, cookie веб-сессии получены. */
    data object Confirmed : QrStatus

    data object Expired : QrStatus
}

data class WebToken(val accessToken: String, val userId: Long, val expiresAtSec: Long)

/**
 * Вход по QR-коду VK ID — порт meridius-core `HTTPClient.requestQR/checkQRStatus/validateAuthCode`:
 * 1) страница id.vk.ru/auth отдаёт `window.init` (анонимный токен и host_app_id);
 * 2) auth.getAuthCode → ссылка для QR; auth.checkAuthCode опрашивается каждые 3 с;
 * 3) статус 2 → login.vk.ru/?act=connect_code_auth с super_app_token, переход на next_step_url
 *    ставит cookie vk.ru; дальше токен берётся из login.vk.ru/?act=web_token.
 */
class VkQrAuth(
    private val web: VkWebClient,
    private val deviceName: String,
) {
    private var uuid = randomUuid()
    private var hostAppId = ""
    private var anonymousToken = ""
    private var authHash = ""
    private var expiresAtSec = 0L

    suspend fun requestQr(): QrCode {
        uuid = randomUuid()
        val pageUrl = VkWebConfig.AUTH_PAGE_URL.toHttpUrl().newBuilder().apply {
            VkWebConfig.authPageParams(uuid).forEach { (key, value) -> addQueryParameter(key, value) }
        }.build().toString()
        val page = web.get(pageUrl, mapOf("Priority" to "u=0, i"))
        val init = extractInit(page) ?: throw VkWebAuthException("Ошибка инициализации QR-кода")
        val auth = init["auth"] as? JsonObject ?: throw VkWebAuthException("Ошибка инициализации QR-кода")
        hostAppId = auth["host_app_id"].text() ?: throw VkWebAuthException("Ошибка инициализации QR-кода")
        anonymousToken = auth["anonymous_token"].text() ?: throw VkWebAuthException("Ошибка инициализации QR-кода")

        val response = apiCall(
            VkWebConfig.GET_AUTH_CODE_URL,
            mapOf(
                "device_name" to deviceName,
                "auth_code_flow" to 0,
                "verification_hash" to "",
                "force_regenerate" to 0,
                "anonymous_token" to anonymousToken,
                "is_switcher_flow" to "",
                "access_token" to "",
            ),
        )
        authHash = response["auth_hash"].text() ?: throw VkWebAuthException("Ошибка инициализации QR-кода")
        expiresAtSec = (response["expires_in"] as? JsonPrimitive)?.longOrNull ?: 0L
        val url = response["auth_url"].text() ?: throw VkWebAuthException("Ошибка инициализации QR-кода")
        return QrCode(url, expiresAtSec)
    }

    suspend fun checkStatus(): QrStatus {
        if (expiresAtSec > 0 && expiresAtSec < System.currentTimeMillis() / 1000) return QrStatus.Expired
        val response = apiCall(
            VkWebConfig.CHECK_AUTH_CODE_URL,
            mapOf("auth_hash" to authHash, "web_auth" to 1, "anonymous_token" to anonymousToken, "access_token" to ""),
        )
        return when ((response["status"] as? JsonPrimitive)?.intOrNull) {
            STATUS_CONFIRMED -> {
                connect(response["super_app_token"].text() ?: throw VkWebAuthException("Ошибка завершения авторизации"))
                QrStatus.Confirmed
            }
            STATUS_NEED_CODE -> QrStatus.NeedCode
            else -> QrStatus.Waiting
        }
    }

    /** Код из приложения VK (status 5). @return false, если код неверный. */
    suspend fun validateCode(code: String): Boolean {
        val response = apiCall(
            VkWebConfig.VALIDATE_AUTH_CODE_URL,
            mapOf("auth_hash" to authHash, "validation_code" to code, "access_token" to anonymousToken),
        )
        val status = (response["status"] as? JsonPrimitive)?.intOrNull
        return status == STATUS_WAITING || status == STATUS_CONFIRMED
    }

    /** Токен веб-клиента VK по cookie сессии (meridius-core `webToken`, service.json → webToken). */
    suspend fun webToken(): WebToken {
        val response = web.postJson(
            VkWebConfig.WEB_TOKEN_URL,
            mapOf("version" to VkWebConfig.WEB_TOKEN_VERSION, "app_id" to VkWebConfig.WEB_TOKEN_APP_ID),
            ORIGIN_HEADERS,
        )
        if (response["type"].text() != "okay") throw VkWebAuthException("Сессия VK истекла — войдите заново")
        val data = response["data"] as? JsonObject ?: throw VkWebAuthException("Сессия VK истекла — войдите заново")
        val token = data["access_token"].text() ?: throw VkWebAuthException("Сессия VK истекла — войдите заново")
        val userId = (data["user_id"] as? JsonPrimitive)?.longOrNull
            ?: ((data["auth_info"] as? JsonObject)?.get("user") as? JsonObject)?.get("id")?.jsonPrimitive?.longOrNull
            ?: throw VkWebAuthException("VK не вернул id пользователя")
        return WebToken(token, userId, (data["expires"] as? JsonPrimitive)?.longOrNull ?: 0L)
    }

    private suspend fun connect(superAppToken: String) {
        val response = web.postJson(
            VkWebConfig.CONNECT_CODE_AUTH_URL,
            mapOf(
                "token" to superAppToken,
                "uuid" to uuid,
                "app_id" to hostAppId,
                "flow_start_state" to "",
                "is_external_carousel" to "",
                "oauth_version" to "",
                "sid" to "",
                "oauth_force_hash" to 0,
                "is_registration" to 0,
                "oauth_response_type" to "silent_token",
                "vkid_oauth_hash" to "",
                "is_oauth_migrated_flow" to 0,
                "oauth_state" to "",
                "to" to Base64.getEncoder().encodeToString(VkWebConfig.BASE_URL.encodeToByteArray()),
                "version" to 1,
            ),
            ORIGIN_HEADERS,
        )
        if (response["type"].text() == "error") throw VkWebAuthException("Ошибка завершения авторизации")
        // meridius-core final(): this.request(next_step_url) — POST (метод request по умолчанию) с ручными
        // редиректами; этот шаг ставит cookie веб-сессии vk.ru.
        (response["data"] as? JsonObject)?.get("next_step_url").text()?.let { web.postForm(it, emptyMap()) }
    }

    private suspend fun apiCall(url: String, params: Map<String, Any?>): JsonObject {
        val fullUrl = url.toHttpUrl().newBuilder()
            .addQueryParameter("v", VkWebConfig.AUTH_API_VERSION)
            .addQueryParameter("client_id", hostAppId)
            .build()
            .toString()
        val root = web.postJson(fullUrl, params, ORIGIN_HEADERS)
        (root["error"] as? JsonObject)?.let { error ->
            throw VkWebAuthException(error["error_msg"].text() ?: "Ошибка VK ID")
        }
        return root["response"]?.jsonObject ?: throw VkWebAuthException("Пустой ответ VK ID")
    }

    private fun extractInit(html: String): JsonObject? {
        val start = html.indexOf(INIT_MARKER).takeIf { it >= 0 }?.let { html.indexOf('{', it) } ?: return null
        val end = matchingBrace(html, start) ?: return null
        return web.parseJson(html.substring(start, end + 1)) as? JsonObject
    }

    // window.init — JSON внутри <script>; ищем закрывающую скобку с учётом строк.
    private fun matchingBrace(s: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = inString
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> if (--depth == 0) return i
            }
        }
        return null
    }

    private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun randomUuid(): String = String(CharArray(6) { 'a' + Random.nextInt(26) })

    private companion object {
        const val INIT_MARKER = "window.init = "
        const val STATUS_WAITING = 0
        const val STATUS_CONFIRMED = 2
        const val STATUS_NEED_CODE = 5

        // configuration.auth.options.additionalHeaders
        val ORIGIN_HEADERS = mapOf(
            "Origin" to VkWebConfig.ID_ORIGIN,
            "Referer" to "${VkWebConfig.ID_ORIGIN}/",
            "Priority" to "u=1, i",
        )
    }
}

package com.vkmusic.data.api.vk

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.UUID

data class VkCredentials(val username: String, val password: String)

data class CaptchaAnswer(val sid: String, val key: String)

sealed interface AuthResult {
    data class Success(val accessToken: String, val userId: Long) : AuthResult

    /** otp — результат автоматической доставки кода по этому `sid` (null, если канал неизвестен). */
    data class NeedValidation(
        val sid: String?,
        val type: String?,
        val phoneMask: String?,
        val otp: OtpResult? = null,
    ) : AuthResult

    data class NeedCaptcha(val sid: String, val imageUrl: String) : AuthResult
    data class Failure(val message: String) : AuthResult
}

// ecosystem.sendOtp{Email|Sms|Push}: названия методов seen в classes.dex эталонного клиента (0xca56c—0xca578).
enum class OtpChannel(val method: String) {
    Email("ecosystem.sendOtpEmail"),
    Sms("ecosystem.sendOtpSms"),
    Push("ecosystem.sendOtpPush"),
}

// validation_type из need_validation -> канал доставки кода. Подтверждение в самом VK (2fa_app/codegen) кода не требует.
internal fun otpChannel(type: String?): OtpChannel? = when (type) {
    "2fa_email", "email_code_confirmation" -> OtpChannel.Email
    "2fa_sms", "phone_code_confirmation", "sms", "callreset" -> OtpChannel.Sms
    "2fa_push" -> OtpChannel.Push
    else -> null
}

sealed interface OtpResult {
    data object Sent : OtpResult
    data class Rejected(val message: String) : OtpResult
}

// sAk-версия VK ID SDK, с которой эталонный клиент ходит в ecosystem.* (dex@0xca498).
private const val SAK_VERSION = "1.144.0"

/**
 * Авторизация через VK ID на api.vk.ru:
 * 1) auth.getAnonymToken — анонимный токен приложения;
 * 2) auth.validateAccount — проверка логина, подсказывает доступные способы подтверждения;
 * 3) oauth/token (grant_type=password) — финальный access_token, либо need_validation / need_captcha;
 * 4) при need_validation: [sendOtp] доставляет код (например на почту), а повторный [login]
 *    с теми же credentials + `sid` + `code` завершает вход.
 */
class VkAuthApi(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val apiUrl: HttpUrl = VkIdConfig.API_URL.toHttpUrl(),
    private val oauthTokenUrl: HttpUrl = VkIdConfig.OAUTH_TOKEN_URL.toHttpUrl(),
    private val isConfigured: () -> Boolean = { VkIdConfig.isConfigured },
) {
    private val deviceId: String = UUID.randomUUID().toString().replace("-", "").take(16)
    private val deviceLanguage: String = Locale.getDefault().language

    @Volatile
    private var anonymToken: String? = null

    // sid из auth.validateAccount: именно его эталонный клиент передаёт в ecosystem.sendOtp* (dex@0xca43e).
    @Volatile
    private var flowSid: String? = null

    suspend fun login(
        credentials: VkCredentials,
        code: String? = null,
        captcha: CaptchaAnswer? = null,
        sid: String? = null,
        forceSms: Boolean = false,
    ): AuthResult {
        if (!isConfigured()) {
            return AuthResult.Failure(
                "Вход через VK ID не настроен: укажите CLIENT_ID и CLIENT_SECRET в VkIdConfig (см. docs/vk-auth.md).",
            )
        }
        return runCatching { authorize(credentials, code, captcha, sid, forceSms) }
            .getOrElse { if (it is VkApiException) AuthResult.Failure(it.message ?: "Ошибка VK") else throw it }
    }

    /** Запрос кода подтверждения на почту/SMS/в push по `sid`, полученному в need_validation. */
    suspend fun sendOtp(sid: String, channel: OtpChannel): OtpResult =
        runCatching {
            callMethod(
                channel.method,
                SendOtpResponse.serializer(),
                mapOf("sid" to sid, "access_token" to ensureAnonymToken()) + ecosystemParams(),
            )
        }.fold(
            onSuccess = { OtpResult.Sent },
            onFailure = { if (it is VkApiException) OtpResult.Rejected(it.message ?: "VK отклонил отправку кода") else throw it },
        )

    // Набор параметров экосистемных методов эталонного клиента (dex@0xca464—0xca500, 0x120a6e—0x120b0e).
    private fun ecosystemParams(): Map<String, String> = mapOf(
        "lang" to deviceLanguage,
        "device_id" to deviceId,
        "sak_version" to SAK_VERSION,
        "flow_type" to "tg_flow",
        "api_id" to VkIdConfig.CLIENT_ID,
        "https" to "1",
    )

    private suspend fun authorize(
        credentials: VkCredentials,
        code: String?,
        captcha: CaptchaAnswer?,
        sid: String?,
        forceSms: Boolean,
    ): AuthResult {
        val anonym = ensureAnonymToken()
        // Необязательный шаг: подсказывает доступные способы подтверждения и sid потока.
        runCatching { validateAccount(credentials.username, anonym) }

        val body = FormBody.Builder()
            .add("grant_type", "password")
            .add("client_id", VkIdConfig.CLIENT_ID)
            .add("client_secret", VkIdConfig.CLIENT_SECRET)
            .add("api_id", VkIdConfig.CLIENT_ID)
            .add("username", credentials.username)
            .add("password", credentials.password)
            .add("anonymous_token", anonym)
            .add("device_id", deviceId)
            .add("scope", VkIdConfig.SCOPE)
            .add("2fa_supported", "1")
            .add("libverify_support", "1")
            .add("lang", deviceLanguage)
            .add("https", "1")
            .add("sak_version", SAK_VERSION)
            .add("v", VkIdConfig.API_VERSION)
            .apply {
                code?.let { add("code", it) }
                // Без sid повтор с кодом VK не принимает: это идентификатор текущего need_validation.
                if (code != null && sid != null) add("sid", sid)
                if (forceSms) add("force_sms", "1")
                captcha?.let {
                    add("captcha_sid", it.sid)
                    add("captcha_key", it.key)
                }
            }
            .build()
        val request = Request.Builder()
            .url(oauthTokenUrl)
            .header("User-Agent", VkConfig.USER_AGENT)
            .header(VkConfig.ANDROID_CLIENT_HEADER, VkConfig.ANDROID_CLIENT_VALUE)
            .post(body)
            .build()
        val text = okHttpClient.newCall(request).awaitBody(requireSuccess = false)
        val result = parse(text)
        // need_validation сам по себе код не рассылает: VK ждёт вызова ecosystem.sendOtp*.
        return if (result is AuthResult.NeedValidation && code == null) dispatchOtp(result) else result
    }

    private suspend fun dispatchOtp(validation: AuthResult.NeedValidation): AuthResult.NeedValidation {
        val channel = otpChannel(validation.type) ?: return validation
        val sid = validation.sid ?: return validation
        return validation.copy(otp = sendOtp(sid, channel))
    }

    private suspend fun ensureAnonymToken(): String {
        anonymToken?.let { return it }
        val response = callMethod(
            "auth.getAnonymToken",
            AnonymTokenResponse.serializer(),
            mapOf(
                "client_id" to VkIdConfig.CLIENT_ID,
                "client_secret" to VkIdConfig.CLIENT_SECRET,
                "device_id" to deviceId,
            ),
        )
        return (response.token ?: throw VkApiException(-1, "VK не выдал анонимный токен")).also { anonymToken = it }
    }

    private suspend fun validateAccount(login: String, anonym: String): ValidateAccountResponse =
        callMethod(
            "auth.validateAccount",
            ValidateAccountResponse.serializer(),
            ecosystemParams() + mapOf(
                "login" to login,
                "anonymous_token" to anonym,
                "client_id" to VkIdConfig.CLIENT_ID,
                "force_password" to "0",
                "flow_type" to "auth_without_password",
                "supported_ways" to "push,email,sms,callreset,password,reserve_code,codegen,passkey,libverify",
            ),
        ).also { flowSid = it.sid ?: flowSid }

    private suspend fun <T> callMethod(method: String, deserializer: DeserializationStrategy<T>, params: Map<String, String>): T {
        val body = FormBody.Builder().apply {
            params.forEach { (key, value) -> add(key, value) }
            add("v", VkIdConfig.API_VERSION)
        }.build()
        val request = Request.Builder()
            .url(apiUrl.newBuilder().addPathSegment(method).build())
            .header("User-Agent", VkConfig.USER_AGENT)
            .header(VkConfig.ANDROID_CLIENT_HEADER, VkConfig.ANDROID_CLIENT_VALUE)
            .post(body)
            .build()
        val text = okHttpClient.newCall(request).awaitBody()
        val root = json.parseToJsonElement(text).jsonObject
        root["error"]?.let {
            // Методы ecosystem.* кладут ошибку плоско: "error":1 и рядом error_code/error_msg.
            val error = json.decodeFromJsonElement(VkError.serializer(), it as? JsonObject ?: root)
            throw VkApiException(error.code, error.message, error.captchaSid, error.captchaImg)
        }
        val response = root["response"] ?: throw VkApiException(-1, "Пустой ответ VK")
        return json.decodeFromJsonElement(deserializer, response)
    }

    internal fun parse(text: String): AuthResult {
        val response = runCatching { json.decodeFromString(TokenResponse.serializer(), text) }
            .getOrElse { return AuthResult.Failure("Некорректный ответ сервера авторизации") }
        return when {
            response.accessToken != null && response.userId != null ->
                AuthResult.Success(response.accessToken, response.userId)
            response.error == "need_validation" ->
                AuthResult.NeedValidation(response.validationSid ?: flowSid, response.validationType, response.phoneMask)
            response.error == "need_captcha" && response.captchaSid != null && response.captchaImg != null ->
                AuthResult.NeedCaptcha(response.captchaSid, response.captchaImg)
            else -> AuthResult.Failure(response.errorDescription ?: response.error ?: "Не удалось войти")
        }
    }
}

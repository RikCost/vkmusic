package com.vkmusic.data.api.vk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class VkApiException(val code: Int, message: String, val captchaSid: String? = null, val captchaImg: String? = null) :
    Exception(message) {
    val isAuthError: Boolean get() = code == 5
}

class VkApiClient(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val tokenProvider: suspend () -> String?,
    private val apiUrl: HttpUrl = VkConfig.API_URL.toHttpUrl(),
    private val onAuthError: suspend () -> Unit = {},
) {
    suspend fun <T> call(
        method: String,
        deserializer: DeserializationStrategy<T>,
        params: Map<String, Any?> = emptyMap(),
        version: String = VkConfig.API_VERSION,
    ): T {
        val token = tokenProvider() ?: throw VkApiException(5, "Нет токена: войдите в аккаунт")
        val body = FormBody.Builder().apply {
            params.forEach { (key, value) -> if (value != null) add(key, value.toString()) }
            add("access_token", token)
            add("v", version)
        }.build()
        val request = Request.Builder()
            .url(apiUrl.newBuilder().addPathSegment(method).build())
            .header("User-Agent", VkConfig.USER_AGENT)
            .header(VkConfig.ANDROID_CLIENT_HEADER, VkConfig.ANDROID_CLIENT_VALUE)
            .post(body)
            .build()
        val text = okHttpClient.newCall(request).awaitBody()
        return try {
            withContext(Dispatchers.Default) { parse(text, deserializer) }
        } catch (e: VkApiException) {
            if (e.isAuthError) onAuthError()
            throw e
        }
    }

    internal fun <T> parse(text: String, deserializer: DeserializationStrategy<T>): T {
        val root = json.parseToJsonElement(text).jsonObject
        root["error"]?.let {
            val error = json.decodeFromJsonElement(VkError.serializer(), it)
            throw VkApiException(error.code, error.message, error.captchaSid, error.captchaImg)
        }
        val response = root["response"] ?: throw VkApiException(-1, "Пустой ответ VK")
        return json.decodeFromJsonElement(deserializer, response)
    }
}

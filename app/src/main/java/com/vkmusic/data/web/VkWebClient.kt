package com.vkmusic.data.web

import com.vkmusic.data.api.vk.VkHeadersInterceptor
import com.vkmusic.data.api.vk.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException

/** Веб-сессия отсутствует или протухла: нужен повторный вход. */
class VkWebAuthException(message: String) : Exception(message)

/**
 * HTTP-клиент веб-версии VK (meridius-core `HTTPClient`): заголовки Firefox, cookie веб-сессии.
 * Общий OkHttp приложения подменяет User-Agent на Android-клиент VK — здесь этот перехватчик снят.
 */
class VkWebClient(
    baseClient: OkHttpClient,
    val cookieJar: PersistentCookieJar,
    private val json: Json,
) {
    private val http: OkHttpClient = baseClient.newBuilder()
        .apply { interceptors().removeAll { it is VkHeadersInterceptor } }
        .cookieJar(cookieJar)
        // Редиректы проходятся вручную (см. [execute]): OkHttp превращает POST в GET и теряет тело.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Ответ веб-VK вместе с адресом, на котором закончились редиректы (для диагностики). */
    data class WebResponse(val code: Int, val finalUrl: String, val contentType: String?, val body: String)

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = execute(url, null, headers).bodyOrThrow()

    suspend fun postForm(url: String, params: Map<String, Any?>, headers: Map<String, String> = emptyMap()): String =
        execute(url, formBody(params), headers).bodyOrThrow()

    suspend fun rawPost(url: String, params: Map<String, Any?>): WebResponse = execute(url, formBody(params), emptyMap())

    suspend fun rawGet(url: String): WebResponse = execute(url, null, emptyMap())

    /**
     * Как `HTTPClient.request` в meridius-core: на 301/302/303/307 тот же запрос (метод и тело)
     * повторяется по адресу из Location; cookie каждого шага сохраняет jar.
     */
    private suspend fun execute(url: String, body: RequestBody?, headers: Map<String, String>): WebResponse {
        cookieJar.ensureLoaded()
        var current = url.toHttpUrl()
        repeat(MAX_REDIRECTS) {
            val builder = Request.Builder().url(current).applyHeaders(headers)
            val request = if (body != null) builder.post(body).build() else builder.get().build()
            val response: Response = http.newCall(request).await()
            response.use {
                val location = it.header("Location")
                if (it.code in REDIRECT_CODES && location != null) {
                    current = current.resolve(location) ?: throw IOException("Некорректный редирект VK")
                } else {
                    return withContext(Dispatchers.IO) {
                        WebResponse(it.code, current.toString(), it.header("Content-Type"), it.body.string())
                    }
                }
            }
        }
        throw IOException("Слишком много редиректов VK")
    }

    private fun WebResponse.bodyOrThrow(): String = if (code in 200..299) body else throw IOException("HTTP $code")

    private fun formBody(params: Map<String, Any?>) = FormBody.Builder().apply {
        params.forEach { (key, value) -> if (value != null) add(key, value.toString()) }
    }.build()

    suspend fun postJson(url: String, params: Map<String, Any?>, headers: Map<String, String> = emptyMap()): JsonObject =
        parseJson(postForm(url, params, headers)) as? JsonObject ?: throw VkWebAuthException("Неожиданный ответ VK")

    /** Запрос к al_audio.php (meridius-core `requestEndpoint`): ответ `{"payload":[code, [...]]}`. */
    suspend fun alAudio(params: Map<String, Any?>): JsonArray {
        val text = postForm(VkWebConfig.AL_AUDIO_URL, params + ("al" to 1))
        val root = parseJson(text) as? JsonObject
            ?: throw VkWebAuthException("Сессия VK истекла — войдите заново")
        val payload = root["payload"] as? JsonArray ?: throw VkWebAuthException("Сессия VK истекла — войдите заново")
        return payload.getOrNull(1) as? JsonArray ?: JsonArray(emptyList())
    }

    /** HTML-страница al_audio.php без al=1 — оттуда Meridius берёт хеши (newPlaylistHash и т. п.). */
    suspend fun alAudioPage(): String = postForm(VkWebConfig.AL_AUDIO_URL, emptyMap())

    // VK иногда предваряет JSON комментарием «<!--» (meridius-core parseJson).
    fun parseJson(text: String): JsonElement? {
        val trimmed = text.trim().removePrefix("<!--")
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
        return runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
    }

    fun clearSession() = cookieJar.clear()

    /** Имена cookie для адреса — без значений, для диагностики. */
    fun cookieNames(url: String): List<String> = cookieJar.loadForRequest(url.toHttpUrl()).map { it.name }.sorted()

    fun importCookies(url: String, cookieHeader: String) {
        val httpUrl = url.toHttpUrl()
        val cookies = cookieHeader.split(";").mapNotNull { part ->
            val pair = part.trim()
            if (pair.isEmpty() || !pair.contains('=')) return@mapNotNull null
            okhttp3.Cookie.Builder()
                .name(pair.substringBefore('=').trim())
                .value(pair.substringAfter('=').trim())
                .domain(httpUrl.host)
                .path("/")
                .secure()
                .build()
        }
        cookieJar.import(httpUrl, cookies)
    }

    private fun Request.Builder.applyHeaders(extra: Map<String, String>): Request.Builder = apply {
        header("User-Agent", VkWebConfig.USER_AGENT)
        header("Accept", "*/*")
        extra.forEach { (name, value) -> header(name, value) }
    }

    private companion object {
        const val MAX_REDIRECTS = 10
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

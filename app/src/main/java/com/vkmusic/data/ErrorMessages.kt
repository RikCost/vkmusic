package com.vkmusic.data

import com.vkmusic.data.api.vk.VkApiException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Ответ сервера с неуспешным HTTP-кодом. */
class HttpStatusException(val code: Int) : IOException("HTTP $code")

/** VK выполнил запрос, но отказал в действии (ненулевой код в `payload[0]` ответа al_audio.php). */
class VkWebActionException(val code: String, message: String) : Exception(message)

/**
 * Текст ошибки для пользователя. Сетевые сбои, HTTP-коды и коды VK переводятся на русский;
 * чужие технические сообщения (английские, «HTTP 502», имена исключений) заменяются [fallback].
 */
fun Throwable.toUserMessage(fallback: String = "Что-то пошло не так — попробуйте ещё раз"): String = when (this) {
    is UnknownHostException, is ConnectException, is NoRouteToHostException -> "Нет соединения с интернетом"
    is InterruptedIOException -> "VK долго не отвечает — попробуйте ещё раз"
    is SSLException -> "Не удалось установить защищённое соединение с VK"
    is HttpStatusException -> when {
        code == 429 -> TOO_MANY_REQUESTS
        code >= 500 -> "VK временно недоступен (ошибка $code)"
        else -> "VK отклонил запрос (ошибка $code)"
    }
    is VkApiException -> when (code) {
        5 -> "Сессия VK истекла — войдите заново"
        6, 9, 29 -> TOO_MANY_REQUESTS
        14 -> "VK просит подтвердить, что вы не робот, — попробуйте позже"
        15, 18, 30, 201, 203 -> "Нет доступа"
        else -> russianOr(fallback)
    }
    else -> russianOr(fallback)
}

private const val TOO_MANY_REQUESTS = "Слишком много запросов — подождите немного"

private val CYRILLIC = Regex("[А-Яа-яЁё]")

private fun Throwable.russianOr(fallback: String): String =
    message?.takeIf { CYRILLIC.containsMatchIn(it) && it.length <= MAX_MESSAGE_LENGTH } ?: fallback

private const val MAX_MESSAGE_LENGTH = 200

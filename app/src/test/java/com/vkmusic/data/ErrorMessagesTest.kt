package com.vkmusic.data

import com.vkmusic.data.api.vk.VkApiException
import com.vkmusic.data.web.actionError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorMessagesTest {

    @Test
    fun `network and http failures are explained in russian`() {
        assertEquals("Нет соединения с интернетом", UnknownHostException("Unable to resolve host \"vk.ru\"").toUserMessage())
        assertEquals("VK долго не отвечает — попробуйте ещё раз", SocketTimeoutException("timeout").toUserMessage())
        assertEquals("VK временно недоступен (ошибка 502)", HttpStatusException(502).toUserMessage())
        assertEquals("Слишком много запросов — подождите немного", HttpStatusException(429).toUserMessage())
        assertEquals("Слишком много запросов — подождите немного", VkApiException(6, "Too many requests per second").toUserMessage())
    }

    @Test
    fun `english technical text is replaced and russian text is kept`() {
        assertEquals("Запасной текст", IllegalStateException("Expected BEGIN_OBJECT").toUserMessage("Запасной текст"))
        assertEquals("Плейлисты недоступны", IllegalStateException("Плейлисты недоступны").toUserMessage())
    }

    @Test
    fun `non zero action code is an error with vk reason`() {
        assertNull(actionError(payload("""[0, []]""")))
        assertNull(actionError(payload("""["0", []]""")))
        assertEquals("Слишком часто & быстро", actionError(payload("""["8", ["Слишком часто &amp; быстро"]]"""))?.message)
        assertEquals("VK не выполнил действие — попробуйте позже", actionError(payload("""[3, []]"""))?.message)
    }

    private fun payload(text: String) = Json.parseToJsonElement(text) as JsonArray
}

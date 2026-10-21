package com.vkmusic.data.web

import com.vkmusic.data.api.vk.VkApiAudioRepository
import com.vkmusic.data.auth.SessionStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отчёт о веб-сессии VK для разбора проблем: какие cookie есть (только имена), чем отвечают vk.ru
 * и al_audio.php. Значения cookie и токены в отчёт не попадают, длинные хеши замазываются.
 */
@Singleton
class VkWebDiagnostics @Inject constructor(
    private val web: VkWebClient,
    private val sessionStore: SessionStore,
    private val official: VkApiAudioRepository,
) {
    /** Что возвращают официальный audio.search и веб-поиск по запросу: блоки, размеры, первые треки. */
    suspend fun searchReport(query: String): String = buildString {
        val userId = sessionStore.current()?.userId ?: return "Сессии нет"
        appendLine("Запрос: «$query»")
        appendLine()
        appendLine("Официальный audio.search:")
        runCatching { official.search(query, 0, 50) }
            .onSuccess { tracks ->
                appendLine("  треков: ${tracks.size}, со ссылкой: ${tracks.count { it.url != null }}, с access_key: ${tracks.count { it.accessKey != null }}")
                tracks.take(SAMPLE).forEach { appendLine("  • ${it.artist} — ${it.title}") }
            }
            .onFailure { appendLine("  ошибка: ${it.message}") }
        appendLine()
        appendLine("Веб section=search:")
        val page = runCatching {
            web.alAudio(mapOf("act" to "section", "claim" to 0, "is_layer" to 0, "owner_id" to userId, "section" to "search", "q" to query))
        }.getOrElse {
            appendLine("  ошибка: ${it.message}")
            return@buildString
        }
        val body = page.getOrNull(1) as? JsonObject
        appendLine("  payload[1]: ${page.size} элем., [0] — ${describeType(page.getOrNull(0))}")
        if (body == null) {
            appendLine("  [1] не объект: ${describeType(page.getOrNull(1))}")
            return@buildString
        }
        appendLine("  ключи [1]: ${body.keys.joinToString()}")
        (body["playlists"] as? JsonArray)?.forEachIndexed { index, block -> describeBlock("playlists[$index]", block, userId) }
        listOf("playlist", "playlistData").forEach { key -> body[key]?.let { describeBlock(key, it, userId) } }
    }

    private fun StringBuilder.describeBlock(name: String, element: JsonElement, userId: Long) {
        val block = element as? JsonObject
        if (block == null) {
            appendLine("  $name: ${describeType(element)}")
            return
        }
        val tracks = WebAudioParser.tracks(block["list"], userId)
        val meta = listOf("id", "blockId", "title", "type", "nextOffset", "hasMore", "totalCount")
            .mapNotNull { key -> (block[key] as? JsonPrimitive)?.contentOrNull?.let { "$key=${redact(it)}" } }
        appendLine("  $name: треков ${tracks.size}; ${meta.joinToString()}")
        tracks.take(SAMPLE).forEach { appendLine("    • ${it.artist} — ${it.title}") }
    }

    private fun describeType(element: JsonElement?): String = when (element) {
        null -> "нет"
        is JsonArray -> "массив (${element.size})"
        is JsonObject -> "объект"
        is JsonPrimitive -> "строка/число (${element.contentOrNull?.length ?: 0} симв.)"
    }

    suspend fun run(): String = buildString {
        val session = sessionStore.current()
        appendLine("Сессия: ${if (session == null) "нет" else "пользователь ${session.userId}"}")
        appendLine()
        appendLine("Cookie (только имена):")
        VkWebConfig.SESSION_URLS.forEach { url ->
            appendLine("  $url: ${web.cookieNames(url).joinToString().ifEmpty { "—" }}")
        }
        appendLine()
        probe("Главная vk.ru (GET)") { web.rawGet(VkWebConfig.BASE_URL) }?.let { response ->
            appendLine("  страница входа: ${if (response.body.contains("Index.initRedesignedVkId")) "да — веб-сессии нет" else "нет"}")
        }
        val userId = session?.userId ?: return@buildString
        probe("al_audio load_section playlist_id=-1 (моя музыка)") {
            web.rawPost(
                VkWebConfig.AL_AUDIO_URL,
                mapOf(
                    "act" to "load_section", "al" to 1, "access_hash" to "", "claim" to 0, "context" to "my", "from_id" to userId,
                    "is_loading_all" to 1, "is_preload" to 0, "offset" to 0, "owner_id" to userId, "playlist_id" to -1, "type" to "playlist",
                ),
            )
        }
        probe("al_audio section=all") { web.rawPost(VkWebConfig.AL_AUDIO_URL, section("all", userId)) }
        probe("al_audio owner_playlists") {
            web.rawPost(
                VkWebConfig.AL_AUDIO_URL,
                mapOf("access_hash" to "", "act" to "owner_playlists", "al" to 1, "is_attach" to 0, "offset" to 0, "owner_id" to userId, "isPlaylist" to true),
            )
        }
    }

    private suspend fun StringBuilder.probe(title: String, call: suspend () -> VkWebClient.WebResponse): VkWebClient.WebResponse? {
        appendLine("$title:")
        val response = runCatching { call() }.getOrElse {
            appendLine("  ошибка: ${it.javaClass.simpleName}: ${it.message}")
            appendLine()
            return null
        }
        appendLine("  HTTP ${response.code}, ${response.contentType ?: "без Content-Type"}, ${response.body.length} симв.")
        appendLine("  итоговый адрес: ${response.finalUrl.substringBefore('?')}")
        appendLine("  начало ответа: ${redact(response.body.take(PREVIEW_LENGTH)).replace('\n', ' ')}")
        appendLine()
        return response
    }

    private fun section(name: String, userId: Long) =
        mapOf("act" to "section", "al" to 1, "claim" to 0, "is_layer" to 0, "owner_id" to userId, "section" to name)

    private fun redact(text: String): String = text
        .replace(Regex("(\"[A-Za-z_]*(?:token|Token|hash|Hash)[A-Za-z_]*\"\\s*:\\s*\")[^\"]*\""), "$1***\"")
        .replace(Regex("[A-Za-z0-9_-]{24,}"), "***")

    private companion object {
        const val PREVIEW_LENGTH = 600
        const val SAMPLE = 5
    }
}

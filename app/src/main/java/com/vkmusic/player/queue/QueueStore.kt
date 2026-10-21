package com.vkmusic.player.queue

import android.content.Context
import com.vkmusic.data.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SavedQueue(val tracks: List<Track>, val index: Int, val positionMs: Long)

/** «Сохранять текущий трек» Meridius: последняя очередь и позиция между запусками. */
@Singleton
class QueueStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val file get() = File(context.filesDir, "latest_queue.json")
    private val mutex = Mutex()

    suspend fun save(queue: SavedQueue) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val tmp = File(context.filesDir, "latest_queue.json.tmp")
            tmp.writeText(json.encodeToString(SavedQueue.serializer(), queue))
            tmp.renameTo(file)
        }
    }

    suspend fun load(): SavedQueue? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(SavedQueue.serializer(), file.readText()) }.getOrNull()
    }

    suspend fun clear() = mutex.withLock { withContext(Dispatchers.IO) { file.delete() } }
}

/**
 * Источник «бесконечной» очереди (VK Микс): когда до конца остаётся пара треков, сервис
 * запрашивает следующую порцию. Сбрасывается любым обычным запуском очереди.
 */
@Singleton
class QueueExtender @Inject constructor() {
    @Volatile
    var source: (suspend () -> List<Track>)? = null
}

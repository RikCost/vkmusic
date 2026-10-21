package com.vkmusic.data.export

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отмена трека в очереди сохранения. Задачи идут одной цепочкой, а отмена задачи WorkManager'ом
 * отменила бы и все следующие — поэтому трек помечается, и задача сама пропускает его на старте.
 */
@Singleton
class Mp3ExportCancellations @Inject constructor() {
    private val ids = ConcurrentHashMap.newKeySet<String>()

    fun cancel(trackId: String) {
        ids += trackId
    }

    fun forget(trackId: String) {
        ids -= trackId
    }

    /** true — трек отменён (отметка снимается). */
    fun consume(trackId: String): Boolean = ids.remove(trackId)
}

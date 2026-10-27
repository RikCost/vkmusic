package com.vkmusic.data.export

import com.vkmusic.data.db.ExportedTrackDao
import com.vkmusic.data.db.ExportedTrackEntity
import com.vkmusic.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реестр сохранённых файлом треков. Пока трек здесь, он играется с локального файла без ссылки VK,
 * статус «Сохранено» берётся отсюда, а не из истории WorkManager, и файл можно удалить вместе с записью.
 */
@Singleton
class SavedAudioStore @Inject constructor(
    private val dao: ExportedTrackDao,
    private val storage: ExportStorage,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    val files: StateFlow<Map<String, ExportedTrackEntity>> = dao.observeAll()
        .map { list -> list.associateBy { it.id } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        // Файл могли убрать из проводника: реестр не имеет права обещать то, чего нет.
        scope.launch(Dispatchers.IO) {
            dao.all().forEach { entity ->
                if (!storage.exists(entity.uri)) runCatching { dao.delete(entity.id) }
            }
        }
    }

    fun uriFor(trackId: String): String? = files.value[trackId]?.uri

    suspend fun put(trackId: String, file: SavedFile) = dao.upsert(
        ExportedTrackEntity(
            id = trackId,
            uri = file.uri,
            location = file.location,
            mimeType = file.mimeType,
            sizeBytes = file.sizeBytes,
            savedAtMs = System.currentTimeMillis(),
        ),
    )

    /** @return false, если провайдер не отдал файл: запись сохраняется, чтобы не потерять трек из виду. */
    suspend fun remove(trackId: String): Boolean {
        val entity = files.value[trackId] ?: return true
        // Файла уже нет — его убрали из проводника, записи всё равно нужно снять.
        if (!storage.exists(entity.uri)) {
            dao.delete(trackId)
            return true
        }
        if (!storage.delete(entity.uri)) return false
        dao.delete(trackId)
        return true
    }

    /**
     * Очистить всё сохранённое: файлы удаляются из папки, записи снимаются все — треки в музыке
     * при этом не трогаются, они лишь снова играют из VK.
     * @return сколько файлов провайдер не отдал (записи о них всё равно снимаются).
     */
    suspend fun removeAll(): Int {
        val stuck = dao.all().count { entity -> storage.exists(entity.uri) && !storage.delete(entity.uri) }
        dao.deleteAll()
        return stuck
    }
}

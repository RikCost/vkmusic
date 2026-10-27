package com.vkmusic.data.export

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vkmusic.data.model.Track
import com.vkmusic.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ExportStatus {
    data object Queued : ExportStatus
    data class Running(val percent: Int) : ExportStatus
    data class Saved(val location: String) : ExportStatus
    data class Failed(val message: String) : ExportStatus
}

/** Очередь сохранения в MP3 поверх WorkManager: постановка, статусы по трекам и сообщения о завершении. */
@Singleton
class Mp3ExportRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
    private val cancellations: Mp3ExportCancellations,
    private val savedAudioStore: SavedAudioStore,
    @ApplicationScope scope: CoroutineScope,
) {
    private val workManager = WorkManager.getInstance(context)
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private var known: Map<String, ExportStatus>? = null
    private val titles = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val batchSaved = mutableListOf<String>()
    private val batchFailed = mutableListOf<String>()

    /** Сообщения «Сохранено…» / «Не удалось…» для снекбара. */
    val events: Flow<String> = _events.asSharedFlow()

    /**
     * Статусы по трекам: живой прогресс — из WorkManager, «Сохранено» — из реестра файлов.
     * История задач чистится, а файл остаётся: без реестра трек снова выглядел бы нескачанным.
     */
    val statuses: StateFlow<Map<String, ExportStatus>> = combine(
        workManager.getWorkInfosByTagFlow(TAG).map { infos -> infos.mapNotNull { info -> info.trackId()?.let { it to info.toStatus() } }.toMap() },
        savedAudioStore.files,
    ) { work, saved ->
        work.toMutableMap().apply {
            saved.forEach { (trackId, file) ->
                val busy = work[trackId] is ExportStatus.Queued || work[trackId] is ExportStatus.Running
                if (!busy) put(trackId, ExportStatus.Saved(file.location))
            }
        }
    }
        .onEach(::emitTransitions)
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Удалить сохранённый файл вместе с записью. @return false, если провайдер не отдал файл. */
    suspend fun deleteFile(track: Track): Boolean = savedAudioStore.remove(track.id)

    fun enqueue(tracks: List<Track>, subfolder: String? = null) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val pending = statuses.value.filterValues { it is ExportStatus.Queued || it is ExportStatus.Running }.keys
        tracks.filter { it.isPlayable && it.id !in pending }.distinctBy { it.id }.forEach { track ->
            titles[track.id] = "${track.artist} — ${track.title}"
            cancellations.forget(track.id)
            val request = OneTimeWorkRequestBuilder<Mp3ExportWorker>()
                .setInputData(
                    workDataOf(
                        Mp3ExportWorker.KEY_TRACK to json.encodeToString(Track.serializer(), track),
                        Mp3ExportWorker.KEY_FOLDER to subfolder,
                    ),
                )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .addTag(TRACK_TAG_PREFIX + track.id)
                .build()
            // Одна цепочка на всё сохранение: треки идут по очереди. Параллельно VK отвечал на reload_audios
            // пустотой, и часть треков плейлиста падала без ссылки.
            workManager.enqueueUniqueWork(QUEUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }

    fun cancel(track: Track) = cancellations.cancel(track.id)

    // Итог — одним сообщением, когда очередь опустела: сохранение плейлиста не засыпает экран снекбарами.
    // Первая выборка только запоминается, иначе после перезапуска всплыли бы старые результаты.
    private fun emitTransitions(current: Map<String, ExportStatus>) {
        val previous = known
        known = current
        if (previous == null) return
        current.forEach { (id, status) ->
            if (previous[id] == status) return@forEach
            val title = titles[id] ?: "трек"
            when (status) {
                is ExportStatus.Saved -> batchSaved += title
                is ExportStatus.Failed -> if (status.message != CANCELLED) batchFailed += "«$title»: ${status.message}"
                else -> Unit
            }
        }
        val busy = current.values.any { it is ExportStatus.Queued || it is ExportStatus.Running }
        if (busy || batchSaved.size + batchFailed.size == 0) return
        val message = when {
            batchSaved.size == 1 && batchFailed.isEmpty() -> "Сохранено: ${batchSaved.single()}"
            batchSaved.isEmpty() && batchFailed.size == 1 -> "Не удалось сохранить ${batchFailed.single()}"
            // Причину первой неудачи показываем сразу — по одному числу «не удалось: 3» ничего не понять.
            else -> "Сохранено в MP3: ${batchSaved.size}" +
                if (batchFailed.isNotEmpty()) ", не удалось: ${batchFailed.size} (${batchFailed.first()})" else ""
        }
        batchSaved.clear()
        batchFailed.clear()
        _events.tryEmit(message)
    }

    private fun WorkInfo.trackId(): String? = tags.firstOrNull { it.startsWith(TRACK_TAG_PREFIX) }?.removePrefix(TRACK_TAG_PREFIX)

    private fun WorkInfo.toStatus(): ExportStatus = when (state) {
        WorkInfo.State.RUNNING -> ExportStatus.Running(progress.getInt(Mp3ExportWorker.KEY_PERCENT, 0))
        WorkInfo.State.SUCCEEDED -> outputData.getString(Mp3ExportWorker.KEY_ERROR)?.let(ExportStatus::Failed)
            ?: ExportStatus.Saved(outputData.getString(Mp3ExportWorker.KEY_LOCATION).orEmpty())
        WorkInfo.State.FAILED -> ExportStatus.Failed(outputData.getString(Mp3ExportWorker.KEY_ERROR) ?: "ошибка")
        WorkInfo.State.CANCELLED -> ExportStatus.Failed(CANCELLED)
        else -> ExportStatus.Queued
    }

    private companion object {
        const val TAG = "mp3_export"
        const val TRACK_TAG_PREFIX = "mp3_track:"
        const val QUEUE_NAME = "mp3_export_queue"
        const val CANCELLED = "отменено"
        const val BACKOFF_SECONDS = 15L
    }
}

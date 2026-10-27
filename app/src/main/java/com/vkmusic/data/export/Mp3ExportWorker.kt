package com.vkmusic.data.export

import com.vkmusic.data.toUserMessage
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vkmusic.data.model.Track
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Один трек → один файл в папке сохранения. Повторяется WorkManager'ом при сетевых сбоях. */
@HiltWorker
class Mp3ExportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val exporter: Mp3Exporter,
    private val savedAudioStore: SavedAudioStore,
    private val json: Json,
    private val cancellations: Mp3ExportCancellations,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val track = inputData.getString(KEY_TRACK)
            ?.let { runCatching { json.decodeFromString(Track.serializer(), it) }.getOrNull() }
            ?: return failed("Повреждённые данные трека")
        if (cancellations.consume(track.id)) return failed(CANCELLED)
        return try {
            val file = exporter.export(track, inputData.getString(KEY_FOLDER)) { percent ->
                // Отмена уже идущего трека: прерываем на ближайшем обновлении прогресса.
                if (cancellations.consume(track.id)) throw IllegalStateException(CANCELLED)
                setProgress(workDataOf(KEY_PERCENT to percent))
            }
            // Без записи в реестр файл не станет источником воспроизведения: статус берётся оттуда, а не из истории задач.
            savedAudioStore.put(track.id, file)
            Result.success(workDataOf(KEY_LOCATION to file.location, KEY_URI to file.uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else failed(e.toUserMessage("Ошибка сети"))
        } catch (e: Exception) {
            failed(e.toUserMessage())
        }
    }

    // Задачи идут цепочкой: настоящий FAILED оборвал бы все следующие треки, поэтому неудача —
    // это успешное завершение с текстом ошибки (Mp3ExportRepository читает его как Failed).
    private fun failed(message: String) = Result.success(workDataOf(KEY_ERROR to message))

    companion object {
        const val KEY_TRACK = "track"
        const val KEY_FOLDER = "folder"
        const val KEY_PERCENT = "percent"
        const val KEY_LOCATION = "location"
        const val KEY_URI = "uri"
        const val KEY_ERROR = "error"
        private const val MAX_ATTEMPTS = 2
        private const val CANCELLED = "отменено"
    }
}

package com.vkmusic.data.export

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
    private val json: Json,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val track = inputData.getString(KEY_TRACK)
            ?.let { runCatching { json.decodeFromString(Track.serializer(), it) }.getOrNull() }
            ?: return Result.failure(workDataOf(KEY_ERROR to "Повреждённые данные трека"))
        return try {
            val location = exporter.export(track, inputData.getString(KEY_FOLDER)) { percent ->
                setProgress(workDataOf(KEY_PERCENT to percent))
            }
            Result.success(workDataOf(KEY_LOCATION to location))
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Ошибка сети")))
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        }
    }

    companion object {
        const val KEY_TRACK = "track"
        const val KEY_FOLDER = "folder"
        const val KEY_PERCENT = "percent"
        const val KEY_LOCATION = "location"
        const val KEY_ERROR = "error"
        private const val MAX_ATTEMPTS = 2
    }
}

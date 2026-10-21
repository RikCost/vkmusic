package com.vkmusic.player.queue

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.model.Track
import com.vkmusic.data.repository.SettingsRepository
import com.vkmusic.data.repository.TrackUrlResolver
import com.vkmusic.player.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: VkAudioRepository,
    private val settingsRepository: SettingsRepository,
    private val queueStore: QueueStore,
    private val queueExtender: QueueExtender,
    private val trackUrlResolver: TrackUrlResolver,
) {
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var restoreAttempted = false

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                val controller = runCatching { future.get() }.getOrNull()
                _player.value = controller
                if (controller == null) controllerFuture = null else restoreLatest(controller)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        _player.value = null
    }

    /**
     * Запустить очередь. [continuation] делает её бесконечной (VK Микс): сервис дозапрашивает треки,
     * когда очередь подходит к концу. Без него прежний источник продолжения отключается.
     */
    fun playQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        continuation: (suspend () -> List<Track>)? = null,
    ) {
        val player = _player.value ?: return
        val playable = tracks.filter { it.isPlayable }.distinctBy { it.id }
        if (playable.isEmpty()) return
        queueExtender.source = continuation
        val startId = tracks.getOrNull(startIndex)?.id
        val start = if (shuffle) playable.indices.random() else playable.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        // Стартовому треку ссылка нужна сразу; остальные сервис получит по мере продвижения очереди.
        scope.launch {
            val first = trackUrlResolver.resolve(listOf(playable[start])).first()
            if (_player.value !== player) return@launch
            val items = playable.map { if (it.id == first.id) first.toMediaItem() else it.toMediaItem() }
            player.shuffleModeEnabled = shuffle
            player.setMediaItems(items, start, 0L)
            player.prepare()
            player.play()
        }
    }

    /** Загрузить первую порцию и запустить бесконечную очередь. @return false, если треков нет. */
    suspend fun playEndless(load: suspend () -> List<Track>): Boolean {
        val first = load()
        if (first.none { it.isPlayable }) return false
        playQueue(first, continuation = load)
        return true
    }

    /** @return true, если трек гарантированно сыграет следующим (при перемешивании позиция случайная). */
    fun playNext(track: Track): Boolean {
        val player = _player.value ?: return false
        if (!track.isPlayable) return false
        if (player.mediaItemCount == 0) {
            playQueue(listOf(track))
            return true
        }
        player.addMediaItem(player.currentMediaItemIndex + 1, track.toMediaItem())
        return !player.shuffleModeEnabled
    }

    fun forgetQueue() {
        queueExtender.source = null
        scope.launch { queueStore.clear() }
    }

    // «Сохранять текущий трек» / «Воспроизводить при запуске» Meridius: один раз за жизнь процесса,
    // если сервис пуст. Ссылки VK истекают, поэтому перед восстановлением они обновляются через getById;
    // скачанные треки играют из кэша и со старой ссылкой.
    private fun restoreLatest(controller: MediaController) {
        if (restoreAttempted) return
        restoreAttempted = true
        scope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.saveLatest || controller.mediaItemCount > 0) return@launch
            val saved = queueStore.load() ?: return@launch
            if (saved.tracks.isEmpty()) return@launch
            // Старые ссылки сбрасываются: веб-треки получат свежие по хешам, остальные — через getByIds.
            val stale = saved.tracks.map { if (it.webHashes != null) it.copy(url = null) else it }
            val fresh = withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                runCatching { api.getByIds(stale.filter { it.webHashes == null }) }.getOrNull()
            }?.associateBy { it.id }.orEmpty()
            var tracks = stale.map { old -> fresh[old.id]?.takeIf { it.url != null } ?: old }.filter { it.isPlayable }
            if (tracks.isEmpty()) return@launch
            val index = tracks.indexOfFirst { it.id == saved.tracks.getOrNull(saved.index)?.id }.coerceAtLeast(0)
            // Иначе плеер начнёт с заглушки и потеряет сохранённую позицию.
            val current = trackUrlResolver.resolve(listOf(tracks[index])).first()
            tracks = tracks.toMutableList().also { it[index] = current }
            if (_player.value !== controller || controller.mediaItemCount > 0) return@launch
            controller.setMediaItems(tracks.map { it.toMediaItem() }, index, saved.positionMs)
            controller.prepare()
            if (settings.playLatestOnStart) controller.play()
        }
    }

    private companion object {
        const val REFRESH_TIMEOUT_MS = 10_000L
    }
}

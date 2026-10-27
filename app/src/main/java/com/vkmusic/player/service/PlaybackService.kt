@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.player.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.vkmusic.MainActivity
import com.vkmusic.R
import com.vkmusic.data.export.SavedAudioStore
import com.vkmusic.data.repository.SettingsRepository
import com.vkmusic.data.repository.TrackUrlResolver
import com.vkmusic.di.ApplicationScope
import com.vkmusic.di.PlayerDataSource
import com.vkmusic.player.audio.AudioEffects
import com.vkmusic.player.audio.EqualizerAudioProcessor
import com.vkmusic.player.audio.LoudnessNormalizerAudioProcessor
import com.vkmusic.player.queue.MAX_RESOLVE_ATTEMPTS
import com.vkmusic.player.queue.QueueExtender
import com.vkmusic.player.queue.QueueStore
import com.vkmusic.player.queue.SavedQueue
import com.vkmusic.player.queue.UnresolvedAction
import com.vkmusic.player.queue.canRefreshUrl
import com.vkmusic.player.queue.httpResponseCode
import com.vkmusic.player.queue.isExpiredLinkResponse
import com.vkmusic.player.queue.needsUrl
import com.vkmusic.player.queue.toMediaItem
import com.vkmusic.player.queue.toTrack
import com.vkmusic.player.queue.unresolvedAction
import com.vkmusic.player.queue.withResolvedUri
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject @field:PlayerDataSource lateinit var dataSourceFactory: DataSource.Factory
    @Inject lateinit var audioEffects: AudioEffects
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var queueExtender: QueueExtender
    @Inject lateinit var queueStore: QueueStore
    @Inject lateinit var savedAudioStore: SavedAudioStore
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var trackUrlResolver: TrackUrlResolver

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var rewindToStart = true
    private var saveLatest = true
    private var extending = false
    private val resolving = mutableSetOf<String>()

    // Сколько раз подряд запрашивали ссылку трека; счётчик сбрасывается, когда трек становится текущим.
    private val resolveAttempts = mutableMapOf<String, Int>()
    private var consecutiveSkips = 0
    private var retryJob: Job? = null

    // Идёт подмена элемента очереди: промежуточные переходы Media3 (вставка нового + удаление старого) — не настоящие.
    private var replacing = false
    private var currentId: String? = null

    // Треки, которым уже выдали новую ссылку после ошибки: второй раз ошибка — не просроченная ссылка.
    private val refreshedAfterError = mutableSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
        val player = ExoPlayer.Builder(this, EffectsRenderersFactory(this, audioEffects))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(QueueListener(player))

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, RewindingPlayer(player))
            .setSessionActivity(sessionActivity)
            .setCallback(SessionCallback())
            .build()

        scope.launch {
            settingsRepository.settings.collect { settings ->
                audioEffects.equalizer = settings.equalizer
                audioEffects.normalizer = settings.normalizer
                rewindToStart = settings.rewindToStart
                saveLatest = settings.saveLatest
                if (player.playbackParameters.speed != settings.playbackSpeed) player.setPlaybackSpeed(settings.playbackSpeed)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaybackOngoing) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        mediaSession?.player?.let(::saveQueue)
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * «Перемотка в начало трека» Meridius: «назад» перезапускает текущий трек, если он играет
     * [REWIND_THRESHOLD_MS] и дольше; при выключенной опции всегда переходит к предыдущему.
     */
    private inner class RewindingPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {
        // Плеер встал с ошибкой (например, без сети исчерпал попытки и пропуски): «Играть» начинает
        // заново — сбрасывает счётчики и готовит плеер, иначе Media3 в состоянии ошибки ничего не делает.
        override fun play() {
            if (exo.playerError != null) {
                exo.currentMediaItem?.mediaId?.let(resolveAttempts::remove)
                consecutiveSkips = 0
                exo.prepare()
                resolveAhead(exo)
            }
            super.play()
        }

        override fun seekToPrevious() {
            val restart = rewindToStart && currentPosition >= REWIND_THRESHOLD_MS
            if (restart || !hasPreviousMediaItem()) seekTo(0) else seekToPreviousMediaItem()
        }
    }

    private inner class QueueListener(private val player: ExoPlayer) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (replacing) return
            // Если подмена шла из колбэка плеера, её события приходят уже после неё: смотрим на фактический
            // текущий трек, а не на mediaItem промежуточного перехода.
            val id = player.currentMediaItem?.mediaId
            if (id != currentId) {
                currentId = id
                refreshedAfterError.clear()
                id?.let(resolveAttempts::remove)
                retryJob?.cancel()
            }
            resolveAhead(player)
            extendQueueIfNeeded(player)
            saveQueue(player)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (!replacing) resolveAhead(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = player.currentMediaItem ?: return
            if (item.needsUrl) {
                recoverUnresolved(player)
                return
            }
            // Ссылка веб-VK истекла (трек долго лежал в очереди или в списке): берём новую по хешам / access_key.
            // Прочие ошибки (формат, обрыв сети) новой ссылкой не лечатся.
            val code = error.httpResponseCode() ?: return
            if (!isExpiredLinkResponse(code)) return
            val track = item.toTrack()?.takeIf { it.canRefreshUrl } ?: return
            if (!refreshedAfterError.add(track.id)) return
            replaceItem(player, player.currentMediaItemIndex, track.copy(url = null, isHls = false).toMediaItem())
            resolveAhead(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) consecutiveSkips = 0 else saveQueue(player)
        }
    }

    // Веб-VK: ссылка трека получается по хешам, когда до него доходит очередь (текущий и пара следующих).
    private fun resolveAhead(player: ExoPlayer) {
        if (player.mediaItemCount == 0) return
        val timeline = player.currentTimeline
        val indices = mutableListOf<Int>()
        var index = player.currentMediaItemIndex
        while (index != C.INDEX_UNSET && index < player.mediaItemCount && indices.size < RESOLVE_AHEAD) {
            indices += index
            index = if (timeline.isEmpty) C.INDEX_UNSET else timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        val pending = indices.map(player::getMediaItemAt)
            .filter { it.needsUrl && it.mediaId !in resolving && (resolveAttempts[it.mediaId] ?: 0) < MAX_RESOLVE_ATTEMPTS }
            .mapNotNull { it.toTrack() }
        if (pending.isEmpty()) return
        val ids = pending.map { it.id }.toSet()
        resolving += ids
        ids.forEach { resolveAttempts[it] = (resolveAttempts[it] ?: 0) + 1 }
        scope.launch {
            try {
                val resolved = trackUrlResolver.resolve(pending).filter { it.url != null }
                // Резолвер глотает исключения, и отмену тоже: без проверки подмена пошла бы в освобождённый плеер.
                ensureActive()
                for (track in resolved) {
                    resolveAttempts -= track.id
                    val position = (0 until player.mediaItemCount).firstOrNull {
                        val item = player.getMediaItemAt(it)
                        item.mediaId == track.id && item.needsUrl
                    } ?: continue
                    val failedHere = position == player.currentMediaItemIndex && player.playerError != null
                    replaceItem(player, position, track.toMediaItem(savedAudioStore.uriFor(track.id)))
                    if (failedHere) player.prepare()
                }
            } finally {
                // Только после подмены: события плеера внутри неё снова зовут resolveAhead,
                // и треки, которые ещё подменяются, не должны запрашиваться повторно.
                resolving -= ids
            }
            recoverUnresolved(player)
        }
    }

    /**
     * Подменить элемент очереди. URI у нового другой, поэтому Media3 не обновляет элемент, а вставляет новый и
     * удаляет старый: перемешанный порядок ставит вставленный в случайное место (следующие треки пропускаются),
     * а текущий трек на миг сменяется другим и теряет позицию. Порядок и позиция возвращаются как были.
     */
    private fun replaceItem(player: ExoPlayer, index: Int, item: MediaItem) {
        val shuffleOrder = player.shuffleOrder
        val isCurrent = index == player.currentMediaItemIndex
        val position = player.currentPosition
        replacing = true
        try {
            player.replaceMediaItem(index, item)
            player.shuffleOrder = shuffleOrder
            if (isCurrent) player.seekTo(index, position)
        } finally {
            replacing = false
        }
    }

    // Текущий трек так и не получил ссылку, а плеер встал на заглушке: ещё попытка или следующий трек.
    private fun recoverUnresolved(player: ExoPlayer) {
        val item = player.currentMediaItem ?: return
        if (player.playerError == null || !item.needsUrl || item.mediaId in resolving) return
        val attempts = resolveAttempts[item.mediaId] ?: 0
        when (unresolvedAction(attempts, consecutiveSkips, player.hasNextMediaItem())) {
            UnresolvedAction.RETRY -> {
                retryJob?.cancel()
                retryJob = scope.launch {
                    delay(RESOLVE_RETRY_DELAY_MS * attempts)
                    resolveAhead(player)
                }
            }
            UnresolvedAction.SKIP -> {
                consecutiveSkips++
                player.seekToNextMediaItem()
                player.prepare()
            }
            UnresolvedAction.GIVE_UP -> Unit
        }
    }

    private fun extendQueueIfNeeded(player: Player) {
        val source = queueExtender.source ?: return
        if (extending || player.mediaItemCount - player.currentMediaItemIndex > EXTEND_THRESHOLD) return
        extending = true
        scope.launch {
            val known = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
            // Сохранённый файлом трек играбельен и без ссылки VK.
            val more = runCatching { source() }.getOrDefault(emptyList())
                .filter { (it.isPlayable || savedAudioStore.uriFor(it.id) != null) && it.id !in known }
                .distinctBy { it.id }
            if (queueExtender.source === source && more.isNotEmpty()) player.addMediaItems(more.map { it.toMediaItem(savedAudioStore.uriFor(it.id)) })
            extending = false
        }
    }

    private fun saveQueue(player: Player) {
        if (!saveLatest || player.mediaItemCount == 0) return
        val current = player.currentMediaItemIndex
        val from = (current - SAVED_QUEUE_LIMIT / 2).coerceAtLeast(0)
        val until = (from + SAVED_QUEUE_LIMIT).coerceAtMost(player.mediaItemCount)
        val tracks = (from until until).mapNotNull { player.getMediaItemAt(it).toTrack() }
        if (tracks.size != until - from) return
        val queue = SavedQueue(tracks, current - from, player.currentPosition)
        // Не в scope сервиса: при onDestroy он отменяется раньше, чем файл успеет записаться.
        appScope.launch { runCatching { queueStore.save(queue) } }
    }

    private class EffectsRenderersFactory(context: Context, private val effects: AudioEffects) : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParameters: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(EqualizerAudioProcessor(effects), LoudnessNormalizerAudioProcessor(effects)))
            .build()
    }

    private class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withResolvedUri() }.toMutableList())
    }

    private companion object {
        // Meridius: service.json → config.rewind = 5 (секунд).
        const val REWIND_THRESHOLD_MS = 5_000L

        // Meridius подгружает микс по 3 трека; здесь — когда до конца очереди осталось столько же.
        const val EXTEND_THRESHOLD = 2
        const val SAVED_QUEUE_LIMIT = 500
        const val RESOLVE_AHEAD = 3
        const val RESOLVE_RETRY_DELAY_MS = 1_000L
    }
}

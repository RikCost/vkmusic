@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.player.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.datasource.cache.CacheDataSource
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
import com.vkmusic.data.repository.SettingsRepository
import com.vkmusic.data.repository.TrackUrlResolver
import com.vkmusic.di.ApplicationScope
import com.vkmusic.player.audio.AudioEffects
import com.vkmusic.player.audio.CrossfadeCurve
import com.vkmusic.player.audio.EqualizerAudioProcessor
import com.vkmusic.player.audio.LoudnessNormalizerAudioProcessor
import com.vkmusic.player.queue.QueueExtender
import com.vkmusic.player.queue.QueueStore
import com.vkmusic.player.queue.SavedQueue
import com.vkmusic.player.queue.needsUrl
import com.vkmusic.player.queue.toMediaItem
import com.vkmusic.player.queue.toTrack
import com.vkmusic.player.queue.withResolvedUri
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var cacheDataSourceFactory: CacheDataSource.Factory
    @Inject lateinit var audioEffects: AudioEffects
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var queueExtender: QueueExtender
    @Inject lateinit var queueStore: QueueStore
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var trackUrlResolver: TrackUrlResolver

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var rewindToStart = true
    private var saveLatest = true
    private var crossfadeMs = 0L
    private var extending = false
    private val resolving = mutableSetOf<String>()

    // Плеер сессии. При плавном переходе сессия переезжает на новый плеер, а старый доигрывает хвост.
    private lateinit var player: ExoPlayer
    private lateinit var queueListener: QueueListener
    private var crossfade: Crossfade? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
        player = buildPlayer()
        queueListener = QueueListener(player)
        player.addListener(queueListener)

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
                crossfadeMs = settings.crossfadeSeconds * 1_000L
                if (player.playbackParameters.speed != settings.playbackSpeed) player.setPlaybackSpeed(settings.playbackSpeed)
            }
        }
        scope.launch {
            while (isActive) {
                delay(if (crossfade == null) CROSSFADE_POLL_MS else CROSSFADE_TICK_MS)
                val active = crossfade
                if (active == null) maybeStartCrossfade() else active.tick()
            }
        }
    }

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(this, EffectsRenderersFactory(this, audioEffects))
        .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
        .setAudioAttributes(AUDIO_ATTRIBUTES, true)
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    /**
     * Запускает переход, когда до конца трека остаётся [crossfadeMs]. Следующий трек сразу играет
     * в новом плеере, и сессия переходит на него: интерфейс и уведомление показывают уже новый трек.
     * Трек без ссылки (веб-VK ещё не вернул её) играет обычным переходом.
     */
    private fun maybeStartCrossfade() {
        val old = player
        if (crossfadeMs <= 0 || !old.isPlaying || old.isCurrentMediaItemLive || old.repeatMode == Player.REPEAT_MODE_ONE) return
        val duration = old.duration
        if (duration == C.TIME_UNSET || duration < crossfadeMs * 2) return
        val remaining = ((duration - old.currentPosition) / old.playbackParameters.speed).toLong()
        if (remaining > crossfadeMs || remaining < MIN_CROSSFADE_MS) return
        val nextIndex = old.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || old.getMediaItemAt(nextIndex).needsUrl) return

        // Без этого новый плеер заберёт аудиофокус у старого, и тот встанет на паузу.
        old.setAudioAttributes(AUDIO_ATTRIBUTES, false)
        old.removeListener(queueListener)

        val next = buildPlayer()
        queueListener = QueueListener(next)
        next.addListener(queueListener)
        next.volume = 0f
        next.playbackParameters = old.playbackParameters
        next.repeatMode = old.repeatMode
        next.shuffleModeEnabled = old.shuffleModeEnabled
        next.setMediaItems(List(old.mediaItemCount, old::getMediaItemAt), nextIndex, 0L)
        next.prepare()
        next.play()

        // Старый плеер оставляет только текущий трек, чтобы на его конце не перейти к следующему.
        old.repeatMode = Player.REPEAT_MODE_OFF
        val current = old.currentMediaItemIndex
        if (current + 1 < old.mediaItemCount) old.removeMediaItems(current + 1, old.mediaItemCount)
        if (current > 0) old.removeMediaItems(0, current)

        player = next
        mediaSession?.player = RewindingPlayer(next)
        crossfade = Crossfade(old, next, remaining).also { next.addListener(it) }
    }

    /**
     * Затухание [outgoing] и нарастание [incoming]. Время идёт, только пока новый трек реально играет,
     * чтобы его буферизация не съела переход. Любое действие пользователя с новым плеером обрывает хвост.
     */
    private inner class Crossfade(
        private val outgoing: ExoPlayer,
        private val incoming: ExoPlayer,
        private val durationMs: Long,
    ) : Player.Listener {
        private var elapsedMs = 0L
        private var lastTick = SystemClock.elapsedRealtime()

        fun tick() {
            val now = SystemClock.elapsedRealtime()
            if (incoming.isPlaying) elapsedMs += now - lastTick
            lastTick = now
            if (elapsedMs >= durationMs || outgoing.playbackState == Player.STATE_ENDED || outgoing.playerError != null) {
                finish()
                return
            }
            val (out, `in`) = CrossfadeCurve.gains(elapsedMs.toFloat() / durationMs)
            outgoing.volume = out
            incoming.volume = `in`
        }

        fun finish() {
            if (crossfade !== this) return
            crossfade = null
            incoming.removeListener(this)
            incoming.volume = 1f
            outgoing.release()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) finish()
        }

        // Например, звонок: новый плеер замолкает сам, а хвост без аудиофокуса продолжил бы играть.
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
            if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) finish()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = finish()

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) finish()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!isPlaybackOngoing) pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        mediaSession?.player?.let(::saveQueue)
        crossfade?.finish()
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
    private inner class RewindingPlayer(player: Player) : ForwardingPlayer(player) {
        override fun seekToPrevious() {
            val restart = rewindToStart && currentPosition >= REWIND_THRESHOLD_MS
            if (restart || !hasPreviousMediaItem()) seekTo(0) else seekToPreviousMediaItem()
        }
    }

    private inner class QueueListener(private val player: Player) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            resolveAhead(player)
            extendQueueIfNeeded(player)
            saveQueue(player)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            resolveAhead(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            if (player.currentMediaItem?.needsUrl == true) resolveAhead(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveQueue(player)
        }
    }

    // Веб-VK: ссылка трека получается по хешам, когда до него доходит очередь (текущий и пара следующих).
    private fun resolveAhead(player: Player) {
        if (player.mediaItemCount == 0) return
        val timeline = player.currentTimeline
        val indices = mutableListOf<Int>()
        var index = player.currentMediaItemIndex
        while (index != C.INDEX_UNSET && index < player.mediaItemCount && indices.size < RESOLVE_AHEAD) {
            indices += index
            index = if (timeline.isEmpty) C.INDEX_UNSET else timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        val pending = indices.map(player::getMediaItemAt).filter { it.needsUrl && it.mediaId !in resolving }.mapNotNull { it.toTrack() }
        if (pending.isEmpty()) return
        resolving += pending.map { it.id }
        scope.launch {
            val resolved = trackUrlResolver.resolve(pending).filter { it.url != null }
            resolving -= pending.map { it.id }.toSet()
            for (track in resolved) {
                val position = (0 until player.mediaItemCount).firstOrNull {
                    val item = player.getMediaItemAt(it)
                    item.mediaId == track.id && item.needsUrl
                } ?: continue
                val failedHere = position == player.currentMediaItemIndex && player.playerError != null
                player.replaceMediaItem(position, track.toMediaItem())
                if (failedHere) player.prepare()
            }
        }
    }

    private fun extendQueueIfNeeded(player: Player) {
        val source = queueExtender.source ?: return
        if (extending || player.mediaItemCount - player.currentMediaItemIndex > EXTEND_THRESHOLD) return
        extending = true
        scope.launch {
            val known = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
            val more = runCatching { source() }.getOrDefault(emptyList())
                .filter { it.isPlayable && it.id !in known }
                .distinctBy { it.id }
            if (queueExtender.source === source && more.isNotEmpty()) player.addMediaItems(more.map { it.toMediaItem() })
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

        const val CROSSFADE_POLL_MS = 250L
        const val CROSSFADE_TICK_MS = 50L
        const val MIN_CROSSFADE_MS = 1_000L

        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}

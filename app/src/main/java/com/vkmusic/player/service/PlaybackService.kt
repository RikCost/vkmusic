@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.player.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import com.vkmusic.di.ApplicationScope
import com.vkmusic.player.audio.AudioEffects
import com.vkmusic.player.audio.EqualizerAudioProcessor
import com.vkmusic.player.audio.LoudnessNormalizerAudioProcessor
import com.vkmusic.player.queue.QueueExtender
import com.vkmusic.player.queue.QueueStore
import com.vkmusic.player.queue.SavedQueue
import com.vkmusic.player.queue.toMediaItem
import com.vkmusic.player.queue.toTrack
import com.vkmusic.player.queue.withResolvedUri
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private var rewindToStart = true
    private var saveLatest = true
    private var extending = false

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )
        val player = ExoPlayer.Builder(this, EffectsRenderersFactory(this, audioEffects))
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
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
    private inner class RewindingPlayer(player: Player) : ForwardingPlayer(player) {
        override fun seekToPrevious() {
            val restart = rewindToStart && currentPosition >= REWIND_THRESHOLD_MS
            if (restart || !hasPreviousMediaItem()) seekTo(0) else seekToPreviousMediaItem()
        }
    }

    private inner class QueueListener(private val player: Player) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            extendQueueIfNeeded(player)
            saveQueue(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveQueue(player)
        }
    }

    private fun extendQueueIfNeeded(player: Player) {
        val source = queueExtender.source ?: return
        if (extending || player.mediaItemCount - player.currentMediaItemIndex > EXTEND_THRESHOLD) return
        extending = true
        scope.launch {
            val known = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).mediaId }
            val more = runCatching { source() }.getOrDefault(emptyList())
                .filter { it.url != null && it.id !in known }
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
    }
}

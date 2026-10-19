package com.vkmusic.player.queue

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.vkmusic.data.model.Track
import com.vkmusic.player.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerConnection @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val knownTracks = mutableMapOf<String, Track>()

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                _player.value = runCatching { future.get() }.getOrNull()
                if (_player.value == null) controllerFuture = null
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun release() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        _player.value = null
    }

    fun trackFor(mediaId: String?): Track? = mediaId?.let(knownTracks::get)

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        val player = _player.value ?: return
        val playable = tracks.filter { it.url != null }
        if (playable.isEmpty()) return
        playable.associateByTo(knownTracks) { it.id }
        val startId = tracks.getOrNull(startIndex)?.id
        val start = if (shuffle) playable.indices.random() else playable.indexOfFirst { it.id == startId }.coerceAtLeast(0)
        player.shuffleModeEnabled = shuffle
        player.setMediaItems(playable.map { it.toMediaItem() }, start, 0L)
        player.prepare()
        player.play()
    }

    /** @return true, если трек гарантированно сыграет следующим (при перемешивании позиция случайная). */
    fun playNext(track: Track): Boolean {
        val player = _player.value ?: return false
        if (track.url == null) return false
        knownTracks[track.id] = track
        if (player.mediaItemCount == 0) {
            playQueue(listOf(track))
            return true
        }
        player.addMediaItem(player.currentMediaItemIndex + 1, track.toMediaItem())
        return !player.shuffleModeEnabled
    }
}

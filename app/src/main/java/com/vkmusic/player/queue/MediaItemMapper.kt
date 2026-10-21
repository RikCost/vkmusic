@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.player.queue

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.vkmusic.data.model.Track
import kotlinx.serialization.json.Json

private const val EXTRA_TRACK = "com.vkmusic.track"

private val trackJson = Json { ignoreUnknownKeys = true }

fun Track.toMediaItem(): MediaItem {
    val uri = url?.toUri()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null)
        .setCustomCacheKey(if (isHls) null else id)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(albumTitle)
                .setArtworkUri(coverUrl?.toUri())
                .setDurationMs(durationSec * 1000L)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                // Полный трек едет вместе с элементом очереди: очередь читают и сервис, и UI.
                .setExtras(Bundle().apply { putString(EXTRA_TRACK, trackJson.encodeToString(Track.serializer(), this@toMediaItem)) })
                .build(),
        )
        .build()
}

fun MediaItem.toTrack(): Track? =
    mediaMetadata.extras?.getString(EXTRA_TRACK)?.let { runCatching { trackJson.decodeFromString(Track.serializer(), it) }.getOrNull() }

fun MediaItem.withResolvedUri(): MediaItem {
    val uri = requestMetadata.mediaUri
    if (localConfiguration != null || uri == null) return this
    val isHls = uri.path?.endsWith(".m3u8") == true
    return buildUpon()
        .setUri(uri)
        .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null)
        .setCustomCacheKey(if (isHls) null else mediaId)
        .build()
}

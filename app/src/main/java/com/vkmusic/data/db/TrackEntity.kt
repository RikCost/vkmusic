package com.vkmusic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vkmusic.data.model.Track

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val ownerId: Long,
    val audioId: Long,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val url: String?,
    val coverUrl: String?,
    val isHls: Boolean,
    val position: Int,
    val albumTitle: String? = null,
    val isExplicit: Boolean = false,
    val hasLyrics: Boolean = false,
    val artistId: String? = null,
    val accessKey: String? = null,
    val webHashes: String? = null,
)

fun TrackEntity.toTrack() = Track(
    ownerId = ownerId,
    audioId = audioId,
    title = title,
    artist = artist,
    durationSec = durationSec,
    url = url,
    coverUrl = coverUrl,
    isHls = isHls,
    albumTitle = albumTitle,
    isExplicit = isExplicit,
    hasLyrics = hasLyrics,
    artistId = artistId,
    accessKey = accessKey,
    webHashes = webHashes,
)

fun Track.toEntity(position: Int) = TrackEntity(
    id = id,
    ownerId = ownerId,
    audioId = audioId,
    title = title,
    artist = artist,
    durationSec = durationSec,
    url = url,
    coverUrl = coverUrl,
    isHls = isHls,
    position = position,
    albumTitle = albumTitle,
    isExplicit = isExplicit,
    hasLyrics = hasLyrics,
    artistId = artistId,
    accessKey = accessKey,
    webHashes = webHashes,
)

package com.vkmusic.data.repository

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.vkmusic.data.model.Track
import com.vkmusic.di.ApplicationScope
import com.vkmusic.player.download.MusicDownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadStatus {
    data object Queued : DownloadStatus
    data class Downloading(val percent: Float) : DownloadStatus
    data object Completed : DownloadStatus
    data object Failed : DownloadStatus
}

data class TrackDownload(val track: Track, val status: DownloadStatus, val updatedAt: Long)

@OptIn(UnstableApi::class)
@Singleton
class DownloadRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
    private val json: Json,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _downloads = MutableStateFlow<Map<String, TrackDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, TrackDownload>> = _downloads.asStateFlow()

    private var progressJob: Job? = null

    init {
        scope.launch(Dispatchers.Main.immediate) {
            downloadManager.addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                        put(download)
                        ensureProgressTicker()
                    }

                    override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                        _downloads.update { it - download.request.id }
                    }
                },
            )
            val stored = withContext(Dispatchers.IO) {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.download) }
                }
            }
            _downloads.update { current -> stored.mapNotNull { it.toTrackDownload() }.associateBy { it.track.id } + current }
            ensureProgressTicker()
        }
    }

    fun download(tracks: List<Track>) {
        tracks.filter { it.url != null && _downloads.value[it.id]?.status.let { s -> s == null || s == DownloadStatus.Failed } }.forEach { track ->
            val request = DownloadRequest.Builder(track.id, track.url!!.toUri())
                .setCustomCacheKey(if (track.isHls) null else track.id)
                .setMimeType(if (track.isHls) MimeTypes.APPLICATION_M3U8 else null)
                .setData(json.encodeToString(Track.serializer(), track).encodeToByteArray())
                .build()
            DownloadService.sendAddDownload(context, MusicDownloadService::class.java, request, false)
        }
    }

    fun remove(track: Track) {
        DownloadService.sendRemoveDownload(context, MusicDownloadService::class.java, track.id, false)
    }

    private fun put(download: Download) {
        val item = download.toTrackDownload() ?: return
        _downloads.update { it + (item.track.id to item) }
    }

    private fun ensureProgressTicker() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                val active = downloadManager.currentDownloads.filter { it.state == Download.STATE_DOWNLOADING }
                if (active.isEmpty()) break
                active.forEach(::put)
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun Download.toTrackDownload(): TrackDownload? {
        val track = runCatching {
            json.decodeFromString(Track.serializer(), request.data.decodeToString())
        }.getOrNull() ?: return null
        val status = when (state) {
            Download.STATE_COMPLETED -> DownloadStatus.Completed
            Download.STATE_FAILED -> DownloadStatus.Failed
            Download.STATE_DOWNLOADING -> DownloadStatus.Downloading(percentDownloaded.coerceAtLeast(0f))
            else -> DownloadStatus.Queued
        }
        return TrackDownload(track, status, updateTimeMs)
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 1_000L
    }
}

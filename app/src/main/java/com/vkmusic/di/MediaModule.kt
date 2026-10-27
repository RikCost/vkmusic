package com.vkmusic.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import com.vkmusic.data.repository.AppSettings
import com.vkmusic.data.storage.TunableLruCacheEvictor
import com.vkmusic.player.datasource.PlayerDataSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton

/** Исчезающий кэш прослушанного: системный кэш приложения, вытеснение по LRU. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class StreamingCache

/** Постоянное хранилище офлайн-загрузок: файлы приложения, ничего не вытесняется. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DownloadCache

/** Загрузки как источник для плеера: трек из постоянного хранилища отдаётся без сети. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class OfflineUpstream

/** Источник плеера: сеть и кэш — через кэш, сохранённые файлом треки — напрямую с диска. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PlayerDataSource

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    // Ссылки VK содержат истекающие параметры (?extra=...), поэтому HLS-плейлисты и сегменты
    // кэшируются по URL без query: иначе скачанный трек не находится в кэше после обновления ссылки.
    private val cacheKeyFactory = CacheKeyFactory { spec ->
        spec.key ?: spec.uri.buildUpon().clearQuery().build().toString()
    }

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    fun provideStreamingCacheEvictor(): TunableLruCacheEvictor = TunableLruCacheEvictor(AppSettings.DEFAULT_CACHE_MAX_BYTES)

    @Provides
    @Singleton
    @StreamingCache
    fun provideStreamingCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        evictor: TunableLruCacheEvictor,
    ): Cache = SimpleCache(File(context.cacheDir, "music_cache"), evictor, databaseProvider)

    @Provides
    @Singleton
    @DownloadCache
    fun provideDownloadCache(@ApplicationContext context: Context, databaseProvider: DatabaseProvider): Cache =
        SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), databaseProvider)

    @Provides
    @Singleton
    fun provideUpstreamDataSourceFactory(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): DataSource.Factory = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient))

    // Офлайн-загрузки читаются из постоянного хранилища, но поток прослушивания туда не пишется:
    // иначе кэш рос бы в filesDir бес вытеснения, а «Очистить кэш» не освобождал бы место.
    @Provides
    @Singleton
    @OfflineUpstream
    fun provideOfflineUpstreamFactory(@DownloadCache cache: Cache, upstream: DataSource.Factory): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(cacheKeyFactory)
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    // Плеер: пишет в исчезающий кэш, при попадании в него отсутствующего контента идёт через загрузки,
    // а сохранённые файлом треки (file://, content://) отдаёт напрямую — без копии в кэше.
    @Provides
    @Singleton
    @PlayerDataSource
    fun providePlayerDataSourceFactory(
        @StreamingCache cache: Cache,
        @OfflineUpstream offlineUpstream: DataSource.Factory,
        upstream: DataSource.Factory,
    ): DataSource.Factory = PlayerDataSourceFactory(
        cached = CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(cacheKeyFactory)
            .setUpstreamDataSourceFactory(offlineUpstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
        local = upstream,
    )

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        @DownloadCache cache: Cache,
        upstream: DataSource.Factory,
    ): DownloadManager {
        val downloadDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(cacheKeyFactory)
            .setUpstreamDataSourceFactory(upstream)
        return DownloadManager(
            context,
            DefaultDownloadIndex(databaseProvider),
            DefaultDownloaderFactory(downloadDataSourceFactory, Executors.newFixedThreadPool(2)),
        ).apply {
            maxParallelDownloads = 2
        }
    }
}

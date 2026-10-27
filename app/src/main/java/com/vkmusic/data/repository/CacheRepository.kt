package com.vkmusic.data.repository

import androidx.media3.datasource.cache.Cache
import com.vkmusic.data.storage.TunableLruCacheEvictor
import com.vkmusic.di.ApplicationScope
import com.vkmusic.di.StreamingCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Исчезающий кэш прослушанного: лимит берётся из настроек и действует сразу, объём и очистка — отсюда.
 * Офлайн-загрузки и сохранённые MP3 здесь не хранятся, поэтому «Очистить кэш» их не трогает.
 */
@Singleton
class CacheRepository @Inject constructor(
    @StreamingCache private val cache: Cache,
    private val evictor: TunableLruCacheEvictor,
    settingsRepository: SettingsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    init {
        scope.launch {
            settingsRepository.settings
                .map { it.cacheMaxBytes }
                .distinctUntilChanged()
                .collect { evictor.maxBytes = it }
        }
    }

    suspend fun usageBytes(): Long = withContext(Dispatchers.IO) { cache.cacheSpace }

    suspend fun clear() = withContext(Dispatchers.IO) {
        cache.keys.forEach(cache::removeResource)
    }
}

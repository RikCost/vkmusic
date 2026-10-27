package com.vkmusic.data.storage

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * LRU-вытеснение кэша прослушанного с лимитом, который можно менять на лету: у
 * [androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor] предел фиксируется в конструкторе,
 * а размер кэша выбирается в настройках и должен действовать сразу.
 *
 * Порядок сэмплов выбирается по времени последнего обращения (как в Media3), решение о вытеснении — по
 * фактическому объёму кэша: SimpleCache считает его сам и не теряет содержимое между запусками.
 */
@OptIn(UnstableApi::class)
class TunableLruCacheEvictor(maxBytes: Long) : CacheEvictor {

    // Предел меняется из настроек, поэтому читается из вольт-поля, а не фиксируется в конструкторе.
    @Volatile
    var maxBytes: Long = maxBytes

    private val spans = TreeSet<CacheSpan>()

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, start: Long, length: Long) = evict(cache, length)

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        if (span.isHoleSpan) return
        spans += span
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        spans -= span
    }

    // Коснувшись сэмпла, SimpleCache записывает новый с другим временем: старый в порядке уже не участник.
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        spans -= oldSpan
        spans += newSpan
    }

    private fun evict(cache: Cache, incomingBytes: Long) {
        val limit = maxBytes
        if (limit <= 0L) return
        while (cache.cacheSpace + incomingBytes > limit) {
            val oldest = spans.firstOrNull() ?: return
            val before = cache.cacheSpace
            cache.removeSpan(oldest)
            // Снимок не стал меньше: удаляли то, чего в кэше уже нет, — повтор бессмысленен.
            if (cache.cacheSpace >= before) return
        }
    }
}

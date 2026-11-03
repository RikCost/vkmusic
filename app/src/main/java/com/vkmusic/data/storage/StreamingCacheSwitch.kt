package com.vkmusic.data.storage

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Включён ли кэш прослушанного. Плеер спрашивает флаг на каждый новый источник данных, поэтому
 * переключатель в настройках действует со следующего трека без пересоздания плеера.
 */
@Singleton
class StreamingCacheSwitch @Inject constructor() {
    @Volatile
    var enabled: Boolean = true
}

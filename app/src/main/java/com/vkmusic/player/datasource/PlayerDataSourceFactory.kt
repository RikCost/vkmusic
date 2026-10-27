@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vkmusic.player.datasource

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener

/**
 * Локальные схемы (file:// и content:// из папки сохранения) читаются напрямую, без кэша плеера:
 * копия уже скачанного трека заняла бы место вдвое, а LRU вытеснил бы то, что пользователь считает сохранённым.
 * Остальные ссылки идут в прежнюю цепочку «кэш прослушанного → офлайн-загрузки → сеть».
 */
class PlayerDataSourceFactory(
    private val cached: DataSource.Factory,
    private val local: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = LocalFirstDataSource(cached.createDataSource(), local.createDataSource())

    private class LocalFirstDataSource(
        private val cached: DataSource,
        private val local: DataSource,
    ) : DataSource {
        private var delegate: DataSource = cached

        override fun addTransferListener(transferListener: TransferListener) {
            cached.addTransferListener(transferListener)
            local.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            delegate = if (dataSpec.uri.scheme in LOCAL_SCHEMES) local else cached
            return delegate.open(dataSpec)
        }

        override fun getUri(): Uri? = delegate.uri

        override fun read(target: ByteArray, offset: Int, length: Int): Int = delegate.read(target, offset, length)

        override fun close() {
            delegate.close()
        }
    }

    private companion object {
        val LOCAL_SCHEMES = setOf("file", "content", "android.resource", "asset", "resraw", "res")
    }
}

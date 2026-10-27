package com.vkmusic

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vkmusic.data.repository.CacheRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MuVkApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Создаётся вместе с приложением: лимит кэша должен действовать до открытия настроек.
    @Inject lateinit var cacheRepository: CacheRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}

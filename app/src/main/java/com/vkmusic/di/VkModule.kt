package com.vkmusic.di

import com.vkmusic.data.api.SwitchingVkAudioRepository
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.api.vk.VkApiAudioRepository
import com.vkmusic.data.api.vk.VkApiClient
import com.vkmusic.data.api.vk.VkAuthApi
import com.vkmusic.data.auth.SessionStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VkModule {

    @Binds
    @Singleton
    abstract fun bindVkAudioRepository(impl: SwitchingVkAudioRepository): VkAudioRepository

    companion object {
        @Provides
        @Singleton
        fun provideVkApiClient(okHttpClient: OkHttpClient, json: Json, sessionStore: SessionStore) =
            VkApiClient(okHttpClient, json, tokenProvider = { sessionStore.current()?.accessToken })

        @Provides
        @Singleton
        fun provideVkAuthApi(okHttpClient: OkHttpClient, json: Json) = VkAuthApi(okHttpClient, json)

        @Provides
        @Singleton
        fun provideVkApiAudioRepository(client: VkApiClient, sessionStore: SessionStore) =
            VkApiAudioRepository(client) {
                sessionStore.current()?.userId ?: error("Нет активной сессии VK")
            }
    }
}

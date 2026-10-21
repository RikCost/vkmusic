package com.vkmusic.di

import android.os.Build
import com.vkmusic.data.api.VkAudioRepository
import com.vkmusic.data.api.vk.VkApiAudioRepository
import com.vkmusic.data.api.vk.VkApiClient
import com.vkmusic.data.auth.SessionStore
import com.vkmusic.data.auth.WebSessionManager
import com.vkmusic.data.web.PersistentCookieJar
import com.vkmusic.data.web.VkQrAuth
import com.vkmusic.data.web.VkWebAudioRepository
import com.vkmusic.data.web.VkWebClient
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
    abstract fun bindVkAudioRepository(impl: VkWebAudioRepository): VkAudioRepository

    companion object {
        @Provides
        @Singleton
        fun provideCookieJar(sessionStore: SessionStore, json: Json) = PersistentCookieJar(sessionStore, json)

        @Provides
        @Singleton
        fun provideVkWebClient(okHttpClient: OkHttpClient, cookieJar: PersistentCookieJar, json: Json) =
            VkWebClient(okHttpClient, cookieJar, json)

        // device_name показывается в приложении VK при подтверждении входа по QR.
        @Provides
        @Singleton
        fun provideVkQrAuth(web: VkWebClient) = VkQrAuth(web, deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim())

        // Официальный API получает web-токен (login.vk.ru/?act=web_token), как vk-io в Meridius.
        @Provides
        @Singleton
        fun provideVkApiClient(okHttpClient: OkHttpClient, json: Json, webSession: WebSessionManager) =
            VkApiClient(okHttpClient, json, tokenProvider = { webSession.accessToken() })

        @Provides
        @Singleton
        fun provideVkApiAudioRepository(client: VkApiClient, json: Json, sessionStore: SessionStore) =
            VkApiAudioRepository(client, json) {
                sessionStore.current()?.userId ?: error("Нет активной сессии VK")
            }

        @Provides
        @Singleton
        fun provideVkWebAudioRepository(web: VkWebClient, official: VkApiAudioRepository, json: Json, sessionStore: SessionStore) =
            VkWebAudioRepository(web, official, json) {
                sessionStore.current()?.userId ?: error("Нет активной сессии VK")
            }
    }
}

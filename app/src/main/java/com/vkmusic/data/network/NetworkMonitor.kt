package com.vkmusic.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.vkmusic.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Есть ли сейчас сеть. Связь пропадает в лифте и метро: плеер в это время ждёт, а не сыплет ошибками,
 * и продолжает, а экраны перезагружают упавшие списки, как только сеть вернётся.
 *
 * Сеть без проверки доступности интернета (VALIDATED) тоже считается: в некоторых сетях проверка Google
 * не проходит, а VK открывается.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    val isOnline: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasInternet())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        // Колбэк по сети по умолчанию: при смене Wi-Fi на мобильную приходит новая сеть, а не onLost последней.
        connectivity.registerDefaultNetworkCallback(callback)
        trySend(currentlyOnline())
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, currentlyOnline())

    private fun currentlyOnline(): Boolean =
        connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)?.hasInternet() == true

    private fun NetworkCapabilities.hasInternet(): Boolean = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

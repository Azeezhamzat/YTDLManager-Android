package io.github.azeezhamzat.ytdlmanager.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NetworkStatus(val connected: Boolean, val unmetered: Boolean)

/** Current connection, so the UI can say "waiting for Wi-Fi". */
class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _status = MutableStateFlow(read(cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }))
    val status: StateFlow<NetworkStatus> = _status.asStateFlow()

    init {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _status.value = read(caps)
            }

            override fun onLost(network: Network) {
                _status.value = NetworkStatus(connected = false, unmetered = false)
            }
        })
    }

    private fun read(caps: NetworkCapabilities?): NetworkStatus =
        if (caps == null) {
            NetworkStatus(connected = false, unmetered = false)
        } else {
            NetworkStatus(
                connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            )
        }
}

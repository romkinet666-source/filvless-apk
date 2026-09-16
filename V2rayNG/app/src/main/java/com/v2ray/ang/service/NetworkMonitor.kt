package com.v2ray.ang.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.v2ray.ang.AppConfig
import com.v2ray.ang.extension.delay
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Watches the network that carries the tunnel and reports topology changes.
 *
 * Cellular -> Wi-Fi is a make-before-break handover: the new network is announced while the old one
 * is still connected, so the socket to the server is never reset and the core keeps using a dead
 * connection. Deciding that a handover happened is what this class is for, acting on it is not.
 *
 * Uses the physical INTERNET network and ignores the VPN interface.
 * [onHandover] is invoked on a background thread after the debounce window and may block.
 */
class NetworkMonitor(
    private val connectivity: ConnectivityManager,
    private val onUnderlyingNetworksChanged: (Array<Network>?) -> Unit,
    private val onHandover: () -> Boolean,
    private val onRecoveryFailed: () -> Unit,
    private val onNetworkLost: () -> Unit,
    private val onInitialNetwork: () -> Unit,
) {
    private companion object {
        const val HANDOVER_DEBOUNCE_MS = 1000L
    }

    private val upstream = UpstreamTracker<Network>()
    val hasNetwork: Boolean get() = upstream.current != null
    private var handoverJob: Job? = null
    @Volatile private var registered = false

    /**
     * Unfortunately registerDefaultNetworkCallback is going to return our VPN interface:
     * https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    private val request by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!registered) return
            val firstAvailable = upstream.current == null
            val recover = upstream.available(network)
            onUnderlyingNetworksChanged(arrayOf(network))
            if (recover) scheduleHandover(network)
            else if (firstAvailable) onInitialNetwork()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (registered && upstream.current == network) onUnderlyingNetworksChanged(arrayOf(network))
        }

        override fun onLost(network: Network) {
            if (!registered || !upstream.lost(network)) return
            handoverJob?.cancel()
            onUnderlyingNetworksChanged(emptyArray())
            onNetworkLost()
        }
    }

    /**
     * Starts watching. Safe to call more than once, only the first call registers.
     */
    fun register() {
        if (registered) return
        try {
            registered = true
            connectivity.requestNetwork(request, callback)
        } catch (e: Exception) {
            registered = false
            LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to request network", e)
        }
    }

    /**
     * Stops watching and drops the tracked state. Safe to call more than once.
     */
    fun unregister() {
        val wasRegistered = registered
        registered = false
        handoverJob?.cancel()
        handoverJob = null
        upstream.reset()
        if (!wasRegistered) return
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "NetworkMonitor: Failed to unregister callback", e)
        }
    }

    private fun scheduleHandover(network: Network) {
        LogUtil.i(AppConfig.TAG, "NetworkMonitor: Upstream is now $network")
        handoverJob?.cancel()
        handoverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                repeat(3) { attempt ->
                    delay(HANDOVER_DEBOUNCE_MS * (1L shl attempt))
                    if (!registered || upstream.current != network) return@launch
                    if (onHandover()) return@launch
                }
                if (registered && upstream.current == network) onRecoveryFailed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to handle upstream change", e)
            }
        }
    }
}

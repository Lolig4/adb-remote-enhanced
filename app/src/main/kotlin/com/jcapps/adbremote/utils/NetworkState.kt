package com.jcapps.adbremote.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.textview.MaterialTextView
import com.jcapps.adbremote.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Wi-Fi / VPN reachability (constant names original). RECON-UNSURE: class/file names. */
// obf: EnumC0778
enum class NetworkState {
    WIFI_DISCONNECTED,
    WIFI_AND_VPN_CONNECTED,
    WIFI_CONNECTED,
}

// obf: C3025.m5425
fun networkStateOf(isWifiConnected: Boolean, isVpnConnected: Boolean): NetworkState {
    if (isWifiConnected && isVpnConnected) {
        return NetworkState.WIFI_AND_VPN_CONNECTED
    }
    return if (isWifiConnected) NetworkState.WIFI_CONNECTED else NetworkState.WIFI_DISCONNECTED
}

/** Emits the Wi-Fi/VPN state from two network callbacks. RECON-UNSURE name. */
// obf: (inlined, leftover "<this>") callbackFlow body C2545, callbacks C3883, awaitClose C2127 case 1
fun Context.networkStateFlow(): Flow<NetworkState> = callbackFlow {
    val connectivityManager = ContextCompat.getSystemService(this@networkStateFlow, ConnectivityManager::class.java)
    if (connectivityManager == null) {
        close(IllegalStateException("connectivity manager is null"))
    } else {
        var isWifiConnected = false
        var isVpnConnected = false
        val vpnCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isVpnConnected = true
                trySend(networkStateOf(isWifiConnected, isVpnConnected))
            }

            override fun onLost(network: Network) {
                isVpnConnected = false
                trySend(networkStateOf(isWifiConnected, isVpnConnected))
            }
        }
        val wifiCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isWifiConnected = true
                trySend(networkStateOf(isWifiConnected, isVpnConnected))
            }

            override fun onLost(network: Network) {
                isWifiConnected = false
                trySend(networkStateOf(isWifiConnected, isVpnConnected))
            }
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            vpnCallback,
        )
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            wifiCallback,
        )
        awaitClose {
            connectivityManager.unregisterNetworkCallback(vpnCallback)
            connectivityManager.unregisterNetworkCallback(wifiCallback)
        }
    }
}

/** Shows the Wi-Fi warning text for [state]. (inlined; leftover "tv"/"state") RECON-UNSURE name. */
fun showNetworkWarning(tv: MaterialTextView, state: NetworkState) {
    when (state) {
        NetworkState.WIFI_DISCONNECTED -> {
            tv.visibility = View.VISIBLE
            tv.setText(R.string.connect_both_devices_same_wifi)
        }
        NetworkState.WIFI_AND_VPN_CONNECTED -> {
            tv.visibility = View.VISIBLE
            tv.setText(R.string.adb_may_not_work_vpn)
        }
        NetworkState.WIFI_CONNECTED -> tv.visibility = View.GONE
    }
}

/** True when the device currently has an active Wi-Fi network. (inlined into DialogConnect) */
fun Context.isWifiConnected(): Boolean {
    val connectivityManager = ContextCompat.getSystemService(this, ConnectivityManager::class.java)
        ?: return false
    val activeNetwork = connectivityManager.activeNetwork
    val capabilities = if (activeNetwork != null) connectivityManager.getNetworkCapabilities(activeNetwork) else null
    return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
}

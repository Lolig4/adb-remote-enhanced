package com.jcapps.adbremote.model

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Rebuild addition: app-wide connection state to the TV. MainViewModel drives it (connect /
 * disconnect), every other screen only reads it instead of probing the TV itself.
 */
object ConnectionStatus {
    val state: MutableStateFlow<ConnectionState> = MutableStateFlow(ConnectionState.DISCONNECTED)

    val isConnected: Boolean
        get() = state.value == ConnectionState.CONNECTED
}

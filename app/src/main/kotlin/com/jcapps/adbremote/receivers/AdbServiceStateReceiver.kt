package com.jcapps.adbremote.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Listens for the ADB service state broadcast (action "adb_service") and dispatches the "state"
 * extra: "connected" -> [onConnected], "disconnected" -> [onDisconnected].
 */
// obf: C1303
class AdbServiceStateReceiver(
    // obf: f6221 (C1425 lambda group -> Function0)
    private val onConnected: (() -> Unit)?,
    // obf: f6220
    private val onDisconnected: (() -> Unit)?,
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        // obf: AbstractC2860.f10440 / C0656.m2453("Broadcast onReceive") — app debug tree logger
        when (intent?.getStringExtra("state")) {
            "connected" -> onConnected?.invoke()
            "disconnected" -> onDisconnected?.invoke()
        }
    }
}

package com.jcapps.adbremote.utils

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.jcapps.adbremote.R
import com.jcapps.adbremote.receivers.AdbServiceStateReceiver
import com.jcapps.adbremote.services.AdbService
import java.lang.ref.WeakReference

/**
 * Starts [AdbService] from an activity and (un)registers an [AdbServiceStateReceiver] for the
 * "adb_service" LocalBroadcast, forwarding connected/disconnected callbacks.
 */
// obf: C3097
class AdbServiceLauncher(
    activity: Activity,
    // obf: f11127 ctor args — C1425 (onConnected) / Function0 (onDisconnected)
    onConnected: (() -> Unit)? = null,
    onDisconnected: (() -> Unit)? = null,
) {
    // obf: f11126
    private val activityRef: WeakReference<Activity> = WeakReference(activity)

    // obf: f11125 (C2517 == LocalBroadcastManager at this site)
    private val localBroadcastManager: LocalBroadcastManager = LocalBroadcastManager.getInstance(activity)

    // obf: f11127
    private val receiver: AdbServiceStateReceiver = AdbServiceStateReceiver(onConnected, onDisconnected)

    // obf: m5526
    fun startService() {
        // Logger.log("Starting ADB service ...")  (obf AbstractC1309.m3254)
        val activity = activityRef.get() ?: return
        val intent = Intent(activity, AdbService::class.java)
        intent.putExtra("msg", activity.getString(R.string.connected_device))
        intent.putExtra("btn", activity.getString(R.string.stop))
        ContextCompat.startForegroundService(activity, intent)
    }

    // obf: m5527
    fun registerReceiver() {
        localBroadcastManager.registerReceiver(receiver, IntentFilter("adb_service"))
    }

    // obf: m5528
    fun unregisterReceiver() {
        localBroadcastManager.unregisterReceiver(receiver)
    }
}

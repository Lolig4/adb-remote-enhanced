package com.jcapps.adbremote.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.api.adb.AdbBinary
import com.jcapps.adbremote.prefs.DevicesStore
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.utils.hasConnected

/**
 * Foreground service that owns the live [AdbBinary]/on-device server while a device is connected.
 * Shows an ongoing notification (channel [NotificationChannels.ADB_NOTIFICATION_CHANNEL]) with an
 * action that routes to [ServerService]. [isRunning] exposes liveness to the rest of the app.
 */
// obf: C1527
class AdbService : Service() {

    // obf: f1647
    private var adb: AdbBinary? = null

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAdbChannel(this)
    }

    override fun onDestroy() {
        Logger.log("ADB service destroyed.")
        adb?.stop()
        isRunning = false
        ServiceCompat.stopForeground(this, if (Build.VERSION.SDK_INT >= 30) 16 else 0)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val msg = intent?.getStringExtra("msg") ?: ""
        val btn = intent?.getStringExtra("btn") ?: ""
        val notification = NotificationChannels.createAdbNotification(this, msg, btn)
        ServiceCompat.startForeground(
            this,
            1,
            notification,
            if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        Logger.log("ADB service started.")
        val defaultDevice = DevicesStore(PreferenceManager.getDefaultSharedPreferences(this)).getDefault()
        if (defaultDevice == null) {
            stopSelf()
        } else {
            Logger.log("Starting ADB server ...")
            val serverAppClassNameWithEncryptedToken = serverAppClassNameWithEncryptedToken(this) ?: ""
            adb?.stop()
            val adb = AdbBinary(this)
            this.adb = adb
            adb.startServer(
                defaultDevice.serial,
                serverAppClassNameWithEncryptedToken,
                // obf: C0728 case 4
                { message -> Logger.log(message) },
                // obf: C2247 case 6
                {
                    isRunning = true
                    hasConnected = true
                    val stateIntent = Intent(ACTION_ADB_SERVICE)
                    stateIntent.putExtra(EXTRA_STATE, STATE_CONNECTED)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(stateIntent)
                },
            )
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        stopSelf()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        stopSelf()
    }

    companion object {
        // obf: f1646 (C3025 tag 23 = this companion)

        // obf: <clinit> literals
        const val ACTION_ADB_SERVICE = "adb_service"
        const val EXTRA_STATE = "state"
        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTED = "disconnected"

        // obf: f1645 — liveness flag read across the app
        @Volatile
        var isRunning: Boolean = false

        // obf: C0104.m798(context) — native routine recovered, see NativeConfig
        private fun serverAppClassNameWithEncryptedToken(context: Context): String? =
            NativeConfig.serverAppClassNameWithEncryptedToken(context)
    }
}

package com.jcapps.adbremote.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.jcapps.adbremote.model.ServerState
import com.jcapps.adbremote.utils.killAdbServer
import com.jcapps.adbremote.utils.updateServerState

/**
 * Transient service kicked from the ADB notification's action button: it stops [AdbService],
 * kills the local adb server and broadcasts "disconnected". Returns START_NOT_STICKY.
 */
// obf: C1393
class ServerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopService(Intent(this, AdbService::class.java))
        killAdbServer {
            // obf: C2247 case 4
            updateServerState(ServerState.NOT_STARTED)
            val stateIntent = Intent(AdbService.ACTION_ADB_SERVICE)
            stateIntent.putExtra(AdbService.EXTRA_STATE, AdbService.STATE_DISCONNECTED)
            LocalBroadcastManager.getInstance(this).sendBroadcast(stateIntent)
        }
        return START_NOT_STICKY
    }

    companion object {
        // obf: f1588 (leftover `static final int = 0`)
        private const val UNUSED = 0
    }
}

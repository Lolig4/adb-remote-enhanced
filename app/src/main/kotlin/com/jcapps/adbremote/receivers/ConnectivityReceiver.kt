package com.jcapps.adbremote.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver for CONNECTIVITY_CHANGE.
 *
 * RECON-UNSURE: in the R8 artifact the mapped alias `C1251` is a synthetic receiver merged from
 * library code only — case 0 = appcompat (`AbstractC1072`), case 1 = WorkManager
 * `BroadcastReceiverConstraintTracker`, default = WorkManager `NetworkStateTracker`
 * (the only CONNECTIVITY_CHANGE handling present). No app-specific onReceive body was recoverable
 * from the artifact, so this is the closest faithful skeleton for the registry's
 * `ConnectivityReceiver`. See the core-adb report.
 */
// obf: C1251
class ConnectivityReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "android.net.conn.CONNECTIVITY_CHANGE") {
            // RECON-UNSURE: no app body exists in the artifact (C1251 is library-merged, and the
            //  receiver is not declared in AndroidManifest.xml) -> intentionally empty.
        }
    }
}

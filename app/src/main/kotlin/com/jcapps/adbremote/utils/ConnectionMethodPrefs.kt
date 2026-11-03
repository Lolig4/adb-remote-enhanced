package com.jcapps.adbremote.utils

import android.content.SharedPreferences
import com.jcapps.adbremote.api.adb.ConnectionMethod

/**
 * Selected button control mode (pref `button_control_mode`, default socket).
 * RECON-UNSURE: name/file; it only exists inlined (checkNotNullParameter "<this>" at each site).
 */
@Suppress("NOTHING_TO_INLINE")
inline fun SharedPreferences.getConnectionMethod(): ConnectionMethod {
    val value = getString("button_control_mode", ConnectionMethod.SOCKET.value)
    return ConnectionMethod.entries.firstOrNull { it.value == value } ?: ConnectionMethod.SOCKET
}

package com.jcapps.adbremote.model

/**
 * Low-level ADB socket connection parameters.
 *
 * The original class is `com.jcapps.adbremote.model.AdbConnection`; it is renamed here to
 * [AdbConnectionConfig] to avoid clashing with `api.adb.AdbConnection`. [toString] is overridden to
 * keep the original runtime text `AdbConnection(...)`.
 */
// obf: C0797
data class AdbConnectionConfig(
    // obf: f4822
    val ipAddress: String,
    // obf: f4821
    val port: Int,
    // obf: f4824 — default was zzbar.zzq.zzf (R8-merged constant = 1000)
    val connectionTimeout: Int = 1000,
    // obf: f4823
    val socketTimeout: Int = 1000,
) {
    override fun toString(): String =
        "AdbConnection(ipAddress=$ipAddress, port=$port, connectionTimeout=$connectionTimeout, socketTimeout=$socketTimeout)"
}

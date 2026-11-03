package com.jcapps.adbremote.utils

import com.jcapps.adbremote.api.remote.ApiService
import com.jcapps.adbremote.model.FTPConfig
import com.jcapps.adbremote.model.FTPSettings
import com.jcapps.adbremote.model.ServerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * deviceIp / ipAddress helper functions (originally R8-merged app statics in `AbstractC0923`).
 *
 * RECON-UNSURE / ownership: these are FTP-server + remote-API helpers (build an [FTPConfig] from
 * [FTPSettings] + a device IP, create an [ApiService] for an IP, and drive a global server run-state
 * flow of obf `EnumC2105` = NOT_STARTED/STARTING/RUNNING/STOPPING). The registry assigns the alias
 * to core-adb (`DeviceIpExt`), but the content is FTP/remote; verify whether it belongs to `files`.
 * The global state flow + its update (obf f5205/f5206/m2832) use [ServerState] (EnumC2105,
 * claimed by main-a).
 */

// obf: AbstractC0923.m2830 — build an FTP config for the device
fun FTPSettings.toFtpConfig(deviceIp: String): FTPConfig {
    return FTPConfig(
        deviceIp,
        port,
        defaultDir,
        fallbackDir,
        readOnly,
        passiveMode,
        if (anonymousLogin) null else username,
        if (anonymousLogin) null else password,
    )
}

// obf: AbstractC0923.m2831 — create the remote ApiService bound to the device IP
//   (C0416.m2147(ApiClient.INSTANCE, ipAddress))
fun apiServiceFor(ipAddress: String): ApiService {
    return com.jcapps.adbremote.api.remote.ApiClient.create(ipAddress)
}

// obf: AbstractC0923.f5206 — global server run-state (RECON-UNSURE name; added by main-a)
private val _serverState = MutableStateFlow(ServerState.NOT_STARTED)

// obf: AbstractC0923.f5205 (C1876 = ReadonlyStateFlow)
val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

// obf: AbstractC0923.m2832 — update the global server run-state flow
fun updateServerState(newState: ServerState) {
    _serverState.value = newState
}

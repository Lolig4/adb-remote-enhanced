package com.jcapps.adbremote.api.adb

import com.jcapps.adbremote.api.adb.commands.AdbBinaryCommand
import com.jcapps.adbremote.model.CommandResult
import com.jcapps.adbremote.model.CommandResultType
import com.jcapps.adbremote.model.CommandStatus
import com.jcapps.adbremote.model.DeviceStatus
import com.jcapps.adbremote.model.DeviceState
import kotlinx.coroutines.Job

/*
 * App top-level [AdbBinary] extensions for the `adb` CLI sub-commands used by the connect flow.
 * Claimed by group main-b. Only [connect] survived as a static (helper class `b`, m979); the
 * others were R8-inlined into DialogConnect (param names `<this>`/`serial`/`ipAddress`/`callback`
 * survived there). RECON-UNSURE: function names.
 *
 * NOTE for core-adb: `AdbBinary.runCommands` takes `List<AdbBinaryCommand>`; the raw result
 * callback is adapted to the typed callbacks below by lambda group C2192 cases 0..3 (jadx failed
 * on that method — reconstruct from smali in phase 2).
 */

/** `adb connect <ip>:<port>`. */
// obf: b.m979 (result adapter = C2192 case 2)
fun AdbBinary.connect(
    ipAddress: String,
    port: Int,
    timeout: Long,
    callback: (CommandResultType, CommandResult) -> Unit,
): Job = runCommands(listOf(AdbBinaryCommand.Command("adb connect $ipAddress:$port")), timeout) {
    // obf: C2192 case 2
    if (it.output?.contains("connected to") == true) {
        callback(CommandResultType.SUCCESS, it)
    } else if (it.type == CommandStatus.TIMEOUT) {
        callback(CommandResultType.RETRY, it)
    } else {
        callback(CommandResultType.FAILURE, it)
    }
}

/** `adb pair <ip>:<port>`, answering the code prompt with [pairingCode]; 20 s timeout. */
// obf: inlined in C0434 (result adapter = C2192 case 3)
fun AdbBinary.pair(
    ipAddress: String,
    port: Int,
    pairingCode: Int,
    callback: (Boolean, CommandResult) -> Unit,
): Job = runCommands(
    listOf(
        AdbBinaryCommand.Command("adb pair $ipAddress:$port"),
        AdbBinaryCommand.Expect("Enter pairing code"),
        AdbBinaryCommand.Command(pairingCode.toString()),
    ),
    20000L,
) {
    // obf: C2192 case 3
    if (it.output?.contains("Successfully paired to") == true) {
        callback(true, it)
    } else {
        callback(false, it)
    }
}

/** `adb -s <serial> tcpip <port>`; 2 s timeout. */
// obf: inlined in C0434.m2156 (result adapter = C2192 case 1)
fun AdbBinary.tcpip(
    serial: String,
    port: Int,
    callback: (CommandResultType, CommandResult) -> Unit,
): Job = runCommands(listOf(AdbBinaryCommand.Command("adb -s $serial tcpip $port")), 2000L) {
    // obf: C2192 case 1
    if (it.output?.contains("restarting in TCP mode") == true) {
        callback(CommandResultType.SUCCESS, it)
    } else if (it.error == "error: device offline") {
        callback(CommandResultType.SUCCESS, it)
    } else if (it.type == CommandStatus.TIMEOUT) {
        callback(CommandResultType.RETRY, it)
    } else {
        callback(CommandResultType.FAILURE, it)
    }
}

/** `adb devices -l`, parsed to [DeviceState]s; 1 s timeout. */
// obf: inlined in C0434.m2157 (result adapter = C2192 case 0)
fun AdbBinary.devices(
    callback: (CommandResultType, List<DeviceState>?, CommandResult) -> Unit,
): Job = runCommands(listOf(AdbBinaryCommand.Command("adb devices -l")), 1000L) {
    // obf: C2192 case 0
    val output = it.output
    if (output != null && output.contains("List of devices attached")) {
        val devices = output.trimIndent()
            .substringAfter("\n")
            .split("\n")
            .filter { line -> line.isNotBlank() }
            .map { line ->
                val parts = line.trim().split(Regex("\\s+"))
                val serial = parts[0].trim()
                val state = when (parts[1]) {
                    "device" -> DeviceStatus.CONNECTED
                    "unauthorized" -> DeviceStatus.UNAUTHORIZED
                    "authorizing" -> DeviceStatus.AUTHORIZING
                    "offline" -> DeviceStatus.OFFLINE
                    else -> DeviceStatus.OFFLINE
                }
                val model = parts.firstOrNull { part -> part.startsWith("model:") }
                    ?.substringAfter("model:")
                    ?.trim()
                DeviceState(serial, state, model)
            }
        callback(CommandResultType.SUCCESS, devices, it)
    } else if (it.type == CommandStatus.TIMEOUT) {
        callback(CommandResultType.RETRY, null, it)
    } else {
        callback(CommandResultType.FAILURE, null, it)
    }
}

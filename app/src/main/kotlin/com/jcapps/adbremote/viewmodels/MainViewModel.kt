package com.jcapps.adbremote.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jcapps.adbremote.api.adb.AdbBinary
import com.jcapps.adbremote.api.adb.AdbClient
import com.jcapps.adbremote.api.adb.AdbConnection
import com.jcapps.adbremote.api.adb.AdbDeviceHelper
import com.jcapps.adbremote.api.adb.ServerInstaller
import com.jcapps.adbremote.api.adb.commands.AdbBinaryCommand
import com.jcapps.adbremote.api.adb.connect
import com.jcapps.adbremote.model.AdbConnectionConfig
import com.jcapps.adbremote.model.AdbDevice
import com.jcapps.adbremote.model.AuthResult
import com.jcapps.adbremote.model.CommandResultType
import com.jcapps.adbremote.model.ConnectionState
import com.jcapps.adbremote.prefs.DevicesStore
import com.jcapps.adbremote.utils.Logger
import com.jcapps.adbremote.utils.killAdbServer
import com.jcapps.adbremote.utils.parsePrivateKey
import com.jcapps.adbremote.utils.readPrivateKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.RSAPublicKeySpec

/**
 * Owns the ADB connection lifecycle: selecting/saving the active [AdbDevice], starting the
 * on-device server, opening the ADB socket (generating/loading the RSA adbkey), and tearing it all
 * down. Exposes [device] and [connectionState] as state flows observed by the UI. (string "mainViewModel")
 */
// obf: C0205
class MainViewModel(
    // obf: f3175 ("adb")
    private val adb: AdbBinary,
    adbPrefs: DevicesStore,
) : ViewModel() {

    // obf: f3173 — set once the socket authenticated successfully (disables the give-up path)
    private var connectingFlag: Boolean = false

    // obf: f3176 — socket connect attempt counter
    private var attempt: Int = 0

    // obf: f3177 — server start/connect coroutine
    private var connectJob: Job? = null

    // obf: f3178 — socket processing-loop coroutine
    private var socketJob: Job? = null

    // obf: f3179 — socket requires authorization (set by MainActivity: C1425 case 19)
    var onAuthRequired: (() -> Unit)? = null

    // obf: f3182 — socket connection gave up (set by MainActivity: C1425 case 18)
    var onConnectionFailed: (() -> Unit)? = null

    // obf: f3185 — on-device server prepared / connected (set by MainActivity: C1425 case 17)
    var onConnected: (() -> Unit)? = null

    // obf: f3183 — server pusher (set by MainActivity)
    var serverInstaller: ServerInstaller? = null

    // obf: f3184 — live ADB client
    var adbClient: AdbClient? = null
        private set

    // obf: f3174 — adb-binary shell helper (used by MainActivity in BINARY control mode)
    val deviceHelper: AdbDeviceHelper

    // obf: f3181 — IO scope for the socket loop
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    // obf: f3180 — currently selected/connected device (initialised from the default saved device)
    val device: MutableStateFlow<AdbDevice?> = MutableStateFlow(adbPrefs.getDefault())

    // obf: f3186 — connection state (rebuild: shared app-wide via ConnectionStatus)
    val connectionState: MutableStateFlow<ConnectionState> = com.jcapps.adbremote.model.ConnectionStatus.state

    init {
        // obf: C2247 case 13
        deviceHelper = AdbDeviceHelper(adb) { device.value?.serial }
    }

    // obf: m1891 — select & connect the given device (or the current one); updates device/state flows
    fun connect(targetDevice: AdbDevice?) {
        val adbDevice = targetDevice ?: device.value
        if (adbDevice == null) {
            device.value = null
            connectionState.value = ConnectionState.DISCONNECTED
            return
        }
        Logger.log("Connecting to " + adbDevice.name)
        device.value = adbDevice
        connectionState.value = ConnectionState.CONNECTING
        startServer(adbDevice)
    }

    // obf: m1892 — (re)open the ADB socket; loads adbKeyFile or generates a 2048-bit RSA key pair
    //   (KeyFactory 'RSA','BC'), builds AdbClient(AdbConnection(keyPair)), enqueues connect, and
    //   launches the socket processing loop (C1801).
    fun connectSocket(reset: Boolean) {
        if (reset) {
            attempt = 0
        }
        attempt++
        Logger.log("Connecting to ADB socket, attempt $attempt ...")
        socketJob?.cancel()
        adbClient?.let {
            it.close()
            it.queue.clear()
        }
        val privateKeyPem = readPrivateKey(adb.adbKeyFile)
        val keyPair = if (privateKeyPem == null) {
            KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        } else {
            try {
                val privateKey = parsePrivateKey(privateKeyPem)!!
                val keyFactory = KeyFactory.getInstance("RSA", "BC")
                val privateKeySpec = keyFactory.getKeySpec(privateKey, RSAPrivateCrtKeySpec::class.java)
                val publicKey = keyFactory.generatePublic(
                    RSAPublicKeySpec(privateKeySpec.modulus, privateKeySpec.publicExponent),
                )
                KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            }
        }
        adbClient = AdbClient(AdbConnection(keyPair))
        enqueueConnect()
        socketJob = ioScope.launch {
            // obf: C1801
            var done = false
            while (isActive && !done) {
                try {
                    val result = adbClient?.processNext()
                    if (result !is AuthResult) continue
                    when (result) {
                        AuthResult.SUCCESS -> {
                            connectingFlag = true
                            Logger.log("Connected to ADB socket.")
                        }
                        AuthResult.FAILURE -> {
                            if (!connectingFlag && attempt >= 4) {
                                withContext(Dispatchers.Main) {
                                    // obf: C1218
                                    onConnectionFailed?.invoke()
                                }
                                done = true
                            } else {
                                Logger.log("ADB socket connection failed.")
                                connectSocket(false)
                            }
                            cancel()
                        }
                        AuthResult.REQUIRES_AUTH -> {
                            Logger.log("Waiting user authentication ...")
                            withContext(Dispatchers.Main) {
                                // obf: C3155
                                onAuthRequired?.invoke()
                            }
                            done = true
                            cancel()
                        }
                    }
                } catch (e: Exception) {
                    Logger.log("ADB socket connection interrupted. Reconnecting ...")
                    adbClient?.queue?.clear()
                    enqueueConnect()
                    delay(1000L)
                }
            }
        }
    }

    // obf: m1893 — start the on-device server for the device (b.m979(adb, ip, port, 2000L, cb))
    fun startServer(targetDevice: AdbDevice) {
        connectJob = adb.connect(targetDevice.ipAddress, targetDevice.port, 2000L) { type, result ->
            // obf: C2097 case 3
            val log = StringBuilder()
            val typeName = when (type) {
                CommandResultType.SUCCESS -> "OK"
                CommandResultType.RETRY -> "RETRY"
                CommandResultType.FAILURE -> "FAILURE"
            }
            log.append("type: ".plus(typeName))
            val output = result.output
            if (output != null && !output.isBlank()) {
                log.append(", output: ".plus(output))
            }
            val error = result.error
            if (error != null && !error.isBlank()) {
                log.append(", error: ".plus(error))
            }
            Logger.log(log.toString())
            when (type) {
                CommandResultType.SUCCESS -> {
                    Logger.log("Connected! Preparing ADB server ...")
                    serverInstaller?.install(targetDevice.serial) { success ->
                        // obf: C3629 case 8
                        if (success) {
                            Logger.log("ADB server prepared.")
                            connectionState.value = ConnectionState.CONNECTED
                            onConnected?.invoke()
                        } else {
                            connectJob = viewModelScope.launch(Dispatchers.IO) {
                                // obf: C0495
                                delay(1000L)
                                withContext(Dispatchers.Main) {
                                    // obf: C2576
                                    Logger.log("Reconnecting to " + targetDevice.name)
                                    startServer(targetDevice)
                                }
                            }
                        }
                    }
                }
                CommandResultType.RETRY, CommandResultType.FAILURE -> startServer(targetDevice)
            }
        }
    }

    // obf: m1894 — set state DISCONNECTED and tear down
    fun disconnect() {
        connectionState.value = ConnectionState.DISCONNECTED
        teardown()
    }

    // obf: m1895 — cancel the socket job and close the ADB client
    fun closeClient() {
        runCatching {
            socketJob?.cancel()
            adbClient?.close()
            adbClient = null
        }
    }

    override fun onCleared() {
        teardown()
    }

    // obf: m1896 — full teardown: adb.stop(), cancel connect job + serverInstaller job, closeClient()
    fun teardown() {
        adb.stop()
        connectJob?.cancel()
        serverInstaller?.job?.cancel()
        closeClient()
    }

    // obf: m1897 — disconnect all devices, optionally clearing the selection, then run onDone on Main
    fun disconnectAll(clearDevice: Boolean, onDone: (() -> Unit)?) {
        Logger.log("Disconnecting all devices ...")
        if (clearDevice) {
            device.value = null
        }
        disconnect()
        // obf: C2247 case 14
        connectJob = killAdbServer { onDone?.invoke() }
    }

    // obf: m1898 — enqueue an AdbConnectionConfig for the current device (default port 1)
    fun enqueueConnect() {
        val adbDevice = device.value
        enqueue(AdbConnectionConfig(adbDevice?.ipAddress.toString(), adbDevice?.port ?: 1))
    }

    /** `adb -s <serial> shell input keyevent <keyCode>` via the adb binary. (inlined into MainActivity C1433 case 12) */
    fun sendKeyEvent(keyCode: Int) {
        val command = StringBuilder()
        command.append("adb -s ")
        command.append(device.value?.serial)
        command.append(" shell input keyevent ")
        command.append(keyCode)
        connectJob = adb.runCommands(listOf(AdbBinaryCommand.Command(command.toString())), 1000L) {
            // obf: C0728 default case — result ignored
        }
    }

    // obf: m1899 — offer an item to the AdbClient command queue (null -> new Object())
    fun enqueue(item: Any?) {
        adbClient?.queue?.offer(item ?: Any())
    }
}

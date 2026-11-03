package com.jcapps.adbremote.api.adb

import android.view.MotionEvent
import com.jcapps.adbremote.NativeConfig
import com.jcapps.adbremote.api.adb.hid.HidKeyboard
import com.jcapps.adbremote.api.adb.hid.HidMouse
import com.jcapps.adbremote.api.adb.stream.AdbStream
import com.jcapps.adbremote.api.adb.stream.createMessage
import com.jcapps.adbremote.model.AdbConnectionConfig
import com.jcapps.adbremote.model.AuthResult
import com.jcapps.adbremote.model.PhysicalKey
import com.jcapps.adbremote.model.VirtualKey
import kotlin.random.Random
import java.net.SocketException
import java.util.concurrent.ArrayBlockingQueue

/**
 * High-level ADB client driving one [AdbConnection]. Runs a single `hid -` shell stream and a
 * command queue ([queue]); [processNext] pulls one queued item and performs the matching action:
 *
 *  - [com.jcapps.adbremote.model.AdbConnectionConfig] -> TCP connect + CNXN/AUTH handshake, open the
 *    `shell,v2,TERM=xterm-256color,raw:hid -` stream, then register the 3 virtual-HID devices
 *    (keyboard [NativeConfig.hidRegisterKeyboard], mouse [NativeConfig.hidRegisterMouse],
 *    consumer [NativeConfig.hidRegisterConsumer]).
 *  - VirtualKey / List<VirtualKey> (main-a) -> [keyboard]
 *  - [MotionEvent] -> [mouse]
 *  - PhysicalKey (main-a) -> consumer-control report ([consumerReportTemplate])
 *  - String -> raw stream write
 */
// obf: C0180
class AdbClient(
    // obf: f3062 ("adbSocket")
    val adbSocket: AdbConnection,
) : AutoCloseable {

    // obf: f3064
    private val stream: AdbStream = AdbStream(adbSocket)

    // obf: f3063
    private val keyboard: HidKeyboard = HidKeyboard(stream)

    // obf: f3065
    val mouse: HidMouse = HidMouse(stream)

    // obf: f3066 — producer/consumer command queue (capacity 1024)
    val queue: ArrayBlockingQueue<Any> = ArrayBlockingQueue(1024)

    // obf: f3067 — consumer-control report template; was C0450.m2180(C0104.m788()).string(UTF_8)
    private val consumerReportTemplate: String? = NativeConfig.hidReportConsumer

    override fun close() {
        try {
            mouse.close()
            keyboard.close()
            stream.close()
            adbSocket.close()
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * Takes one item off [queue] (blocking) and dispatches it. Returns an [AuthResult] for a
     * connection request, or null for input events.
     */
    // obf: m1876
    @Throws(SocketException::class, InterruptedException::class)
    fun processNext(): AuthResult? {
        val item = queue.take()
        if (item is AdbConnectionConfig) {
            val result = adbSocket.connect(item.ipAddress, item.port, item.connectionTimeout, item.socketTimeout)
            when (result.type) {
                AuthResult.SUCCESS -> {}
                AuthResult.FAILURE -> {
                    // obf: AbstractC2860.f10440 / C0656.m2452("ADB connection failed.") — debug tree logger (no-op)
                    return AuthResult.FAILURE
                }
                AuthResult.REQUIRES_AUTH -> {
                    // obf: C0656.m2450("ADB connection failed. requires auth") — debug tree logger (no-op)
                    return AuthResult.REQUIRES_AUTH
                }
            }
            // obf: C0656.m2450("ADB connection successful.") — debug tree logger (no-op)
            val localId = Random.nextInt()
            stream.localId = localId
            adbSocket.writeMessage(
                createMessage("OPEN", "shell,v2,TERM=xterm-256color,raw:hid -\u0000".toByteArray(Charsets.UTF_8), localId, 0),
            )
            val response = adbSocket.readMessage()
            if (response.commandString() != "OKAY") {
                return AuthResult.FAILURE
            }
            stream.remoteId = response.messageHeader.arg0
            stream.write(NativeConfig.hidRegisterKeyboard)
            stream.write(NativeConfig.hidRegisterMouse)
            stream.write(NativeConfig.hidRegisterConsumer)
            return AuthResult.SUCCESS
        }
        when (item) {
            is VirtualKey -> keyboard.sendKey(item.keyCode, item.modifierKey)
            is List<*> -> for (key in item) {
                if (key is VirtualKey) keyboard.sendKey(key.keyCode, key.modifierKey)
            }
            is MotionEvent -> mouse.onMotionEvent(item)
            is PhysicalKey -> {
                val keyCode = item.keyCode
                require(keyCode in 0..255) { "Key code must be between 0..255" }
                val template = consumerReportTemplate
                if (template == null) {
                    stream.write(String())
                } else {
                    stream.write(String.format(template, keyCode.toString()) + String.format(template, "0"))
                }
            }
            // RECON-UNSURE: R8 left `areEqual(item, C0450.f3926)` + String cast here (merged
            //  constant); the source most likely was `is String`.
            is String -> stream.write(item)
            else -> stream.write("")
        }
        return null
    }
}

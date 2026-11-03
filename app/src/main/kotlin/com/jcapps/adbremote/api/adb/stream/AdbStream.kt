package com.jcapps.adbremote.api.adb.stream

import com.jcapps.adbremote.api.adb.AdbConnection
import java.nio.ByteBuffer

/**
 * A single ADB stream opened over an [AdbConnection] (the shell `hid -` stream used for input
 * injection). Writes WRTE frames and tears the stream down with CLSE.
 *
 * Framing of each WRTE payload: a leading NUL byte, the string length as a little-endian int,
 * then the UTF-8 bytes.
 */
// obf: C0765 (claimed core-adb)
class AdbStream(
    // obf: f4771
    private val adbConnection: AdbConnection,
) : AutoCloseable {

    // obf: f4772
    var remoteId: Int = 0

    // obf: f4773
    var localId: Int = 0

    override fun close() {
        val socket = adbConnection.socket
        try {
            if (socket != null && !socket.isClosed) {
                adbConnection.writeMessage(
                    createMessage("CLSE", "".toByteArray(Charsets.UTF_8), localId, remoteId),
                )
                adbConnection.readMessage().commandString() == "CLSE"
                // obf: AbstractC2860.f10440 / C0656.m2453("Closed: ") — app debug tree logger (unregistered)
                adbConnection.close()
            }
        } catch (t: Throwable) {
            // swallow: best-effort close (original: Result.createFailure)
        }
    }

    // obf: m2560
    fun write(str: String) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(bytes.size + 5)
        buffer.put(0.toByte())
        val length = bytes.size
        val bigEndian = byteArrayOf(
            (length shr 24).toByte(),
            (length shr 16).toByte(),
            (length shr 8).toByte(),
            length.toByte(),
        )
        val littleEndian = ByteArray(4) { bigEndian[3 - it] }
        buffer.put(littleEndian)
        buffer.put(bytes)
        adbConnection.writeMessage(createMessage("WRTE", buffer.array(), localId, remoteId))
        val response = adbConnection.readMessage()
        if (response.commandString() != "OKAY") {
            throw IllegalStateException("Expected OKAY command, received ${response.commandString()}")
        }
    }
}

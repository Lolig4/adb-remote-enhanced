package com.jcapps.adbremote.api.adb.stream

/**
 * Top-level ADB wire helpers (originally R8-outlined app statics in `AbstractC2564`/`z8`).
 *
 * Other little-endian helpers map to the stdlib: `z8.m1600` -> `ByteArray.reversedArray()`,
 * `z8.m1614` -> `ByteArray.plus`.
 */

/** Builds a [Message] for [command] (must be 4 bytes) with the given payload and args. */
// obf: AbstractC2564.m4879
fun createMessage(command: String, data: ByteArray, arg0: Int, arg1: Int): Message {
    val commandBytes = command.toByteArray(Charsets.UTF_8)
    require(commandBytes.size == 4) { "command: $command byte length is not 4." }
    val commandInt = bytesToInt(commandBytes)
    var checksum = 0
    for (b in data) checksum += b.toInt() and 0xFF
    return Message(
        MessageHeader(commandInt, arg0, arg1, data.size, checksum, commandInt.inv()),
        data,
    )
}

/** Reads the first 4 bytes of [bytes] as a big-endian int. */
// obf: z8.m1628
fun bytesToInt(bytes: ByteArray): Int {
    require(bytes.size >= 4) { "array too small: %${bytes.size} < 4" }
    return (bytes[0].toInt() shl 24) or
        ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or
        (bytes[3].toInt() and 0xFF)
}

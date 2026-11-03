package com.jcapps.adbremote.api.adb.stream

/**
 * ADB protocol message header (24 bytes on the wire).
 *
 * Wire layout written/read by `AdbConnection`: command/arg0/arg1/magic are big-endian ints, while
 * dataLength and dataCrc32 are little-endian.
 */
// obf: C2872
data class MessageHeader(
    // obf: f10465
    val command: Int,
    // obf: f10464
    val arg0: Int,
    // obf: f10467
    val arg1: Int,
    // obf: f10466
    val dataLength: Int,
    // obf: f10469
    val dataCrc32: Int,
    // obf: f10468
    val magic: Int,
)

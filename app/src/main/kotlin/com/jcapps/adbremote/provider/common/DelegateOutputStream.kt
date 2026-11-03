package com.jcapps.adbremote.provider.common

import java.io.OutputStream

/** An OutputStream forwarding every call to [outputStream] (MaterialFiles DelegateOutputStream). */
abstract class DelegateOutputStream(
    // obf: f9837
    private val outputStream: OutputStream,
) : OutputStream() {
    // obf: AbstractC2616

    override fun write(b: Int) {
        outputStream.write(b)
    }

    override fun write(b: ByteArray) {
        outputStream.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        outputStream.write(b, off, len)
    }

    override fun flush() {
        outputStream.flush()
    }

    override fun close() {
        outputStream.close()
    }
}

package com.jcapps.adbremote.provider.common

/**
 * Growable byte buffer producing [ByteString]s (MaterialFiles ByteStringBuilder). In the artifact
 * R8 merged it into BouncyCastle's SSHBuffer (the variant with discriminator 0).
 */
class ByteStringBuilder(capacity: Int = 16) {
    // obf: SSHBuffer (merged, pos == 0)

    // obf: buffer
    private var buffer = ByteArray(capacity)

    // obf: f7424
    var length: Int = 0
        private set

    // obf: SSHBuffer(C3091)
    constructor(byteString: ByteString) : this(byteString.bytes.size + 16) {
        append(byteString)
    }

    // obf: m3970
    fun append(byte: Byte): ByteStringBuilder {
        ensureCapacity(length + 1)
        buffer[length] = byte
        length += 1
        return this
    }

    // obf: m3969
    fun append(byteString: ByteString): ByteStringBuilder {
        val bytes = byteString.bytes
        val newLength = length + bytes.size
        ensureCapacity(newLength)
        bytes.copyInto(buffer, length, 0, bytes.size)
        length = newLength
        return this
    }

    // obf: m3972
    private fun ensureCapacity(minimumCapacity: Int) {
        val capacity = buffer.size
        if (minimumCapacity > capacity) {
            var newCapacity = (capacity shl 1) + 2
            if (newCapacity < minimumCapacity) {
                newCapacity = minimumCapacity
            }
            buffer = buffer.copyOf(newCapacity)
        }
    }

    // obf: m3971
    fun toByteString(): ByteString = ByteString(buffer.copyOfRange(0, length))

    // obf: toString (case 0)
    override fun toString(): String = String(buffer, 0, length, Charsets.UTF_8)
}

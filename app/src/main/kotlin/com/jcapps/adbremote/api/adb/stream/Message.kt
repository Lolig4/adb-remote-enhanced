package com.jcapps.adbremote.api.adb.stream

/**
 * A full ADB protocol message: a [MessageHeader] plus its payload.
 *
 * Not a `data class`: the payload is compared/printed with array-structural semantics
 * (`contentEquals`/`contentHashCode`/`contentToString`), matching the original hand-written members.
 */
// obf: C0514
class Message(
    // obf: f4096
    val messageHeader: MessageHeader,
    // obf: f4095
    val data: ByteArray,
) {
    /** The 4 command bytes decoded as text, e.g. "CNXN"/"AUTH"/"OPEN"/"OKAY"/"CLSE"/"WRTE". */
    // obf: m2279
    fun commandString(): String {
        val i = messageHeader.command
        return String(
            byteArrayOf((i shr 24).toByte(), (i shr 16).toByte(), (i shr 8).toByte(), i.toByte()),
            Charsets.UTF_8,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return messageHeader == other.messageHeader && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = messageHeader.hashCode() * 31 + data.contentHashCode()

    override fun toString(): String =
        "Message(messageHeader=$messageHeader, data=${data.contentToString()})"
}

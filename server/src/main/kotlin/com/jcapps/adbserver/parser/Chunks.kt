package com.jcapps.adbserver.parser

import java.nio.ByteBuffer
import java.nio.charset.Charset

/*
 * Minimal binary-resource parser vendored into the server (used only by the /app-icon endpoint to
 * pull the launcher icon out of an APK). It is a trimmed Kotlin derivative of dongliu's apk-parser:
 * the dex keeps the original Kotlin cast messages, e.g.
 * "null cannot be cast to non-null type parser.struct.resource.ResourceTableHeader", so the original
 * sources lived in a top-level package `parser` (`parser.struct`, `parser.struct.resource`, ...).
 * RECON-UNSURE: placed in `com.jcapps.adbserver.parser` here; class names follow apk-parser.
 * R8 stripped all header fields that are never read, so many values are read and discarded here
 * exactly like in the dex.
 */

/** obf: C0103 (R8 merged this with Gson's exception base; here a plain RuntimeException). */
// obf: C0103
open class ParserException(message: String) : RuntimeException(message)

/** ByteBuffer helpers (Kotlin top-level functions in the original, R8-merged into AbstractC0353). */
internal object Buffers {

    // obf: AbstractC0353.m377
    fun readUByte(buffer: ByteBuffer): Short = (buffer.get().toInt() and 0xff).toShort()

    // obf: AbstractC0353.m380
    fun readUShort(buffer: ByteBuffer?): Int {
        if (buffer == null) return 0
        return buffer.short.toInt() and 0xffff
    }

    // obf: AbstractC0353.m359
    fun readUInt(buffer: ByteBuffer?): Long {
        if (buffer == null) return 0L
        return buffer.int.toLong() and 0xffffffffL
    }

    /** Reads [length] UTF-16 chars (buffer byte order). */
    // obf: AbstractC0353.m351
    fun readString(buffer: ByteBuffer, length: Int): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            sb.append(buffer.char)
        }
        return sb.toString()
    }

    // obf: AbstractC0353.m361
    fun position(buffer: ByteBuffer, position: Int) {
        buffer.position(position)
    }

    // obf: AbstractC0353.m382
    fun position(buffer: ByteBuffer?, position: Long) {
        if (buffer == null) return
        position(buffer, checkedUInt(position))
    }

    /** Kotlin `UInt`/`toIntExact`-style guard that R8 left inline at every call site. */
    fun checkedUInt(value: Long): Int {
        if (value < 0 || value > Int.MAX_VALUE) {
            throw ArithmeticException("unsigned integer overflow")
        }
        return value.toInt()
    }
}

/** obf: AbstractC0091 */
// obf: AbstractC0091 (f189 = chunkType, f188 = headerSize, f190 = chunkSize)
abstract class ChunkHeader(chunkType: Int, headerSize: Int, chunkSize: Long) {
    val chunkType: Short = chunkType.toShort()
    val headerSize: Short = headerSize.toShort()
    val chunkSize: Int = Buffers.checkedUInt(chunkSize)

    // obf: m124
    val bodySize: Int get() = chunkSize - headerSize
}

/**
 * Header without extra fields. R8 merged apk-parser's NullHeader, XmlHeader, XmlNodeHeader and
 * LibraryHeader into this one class.
 */
// obf: C0039
class PlainChunkHeader(chunkType: Int, headerSize: Int, chunkSize: Long) :
    ChunkHeader(chunkType, headerSize, chunkSize)

// obf: C0127
class XmlResourceMapHeader(chunkType: Int, headerSize: Int, chunkSize: Long) :
    ChunkHeader(chunkType, headerSize, chunkSize)

// obf: C0058
class StringPoolHeader(chunkType: Int, headerSize: Int, chunkSize: Long) :
    ChunkHeader(chunkType, headerSize, chunkSize) {
    var stringCount: Int = 0   // obf: f130
    var stringsStart: Long = 0 // obf: f131
    var flags: Long = 0        // obf: f132
}

// obf: C0003
class ResourceTableHeader(chunkType: Int, headerSize: Int, chunkSize: Long) :
    ChunkHeader(chunkType, headerSize, chunkSize) {
    var packageCount: Int = 0 // obf: f3
}

// obf: C0122
class PackageHeader(chunkType: Int, headerSize: Int, chunkSize: Long) :
    ChunkHeader(chunkType, headerSize, chunkSize) {
    var id: Int = 0          // obf: f250
    var keyStrings: Int = 0  // obf: f251
    var typeStrings: Int = 0 // obf: f252
}

// obf: C0267
class TypeHeader(chunkType: Int, headerSize: Int, chunkSize: Long) :
    ChunkHeader(chunkType, headerSize, chunkSize) {
    var id: Byte = 0           // obf: f562
    var entriesStart: Int = 0  // obf: f563
    var entryCount: Int = 0    // obf: f564
}

// obf: C0161
class TypeSpecHeader(chunkType: Int, headerSize: Int, chunkSize: Long) :
    ChunkHeader(chunkType, headerSize, chunkSize) {
    var id: Byte = 0         // obf: f334
    var entryCount: Int = 0  // obf: f335
}

/** obf: C0283 mode 12 (R8-merged holder class; f604 = String[]) */
// obf: C0283 (mode 12)
class StringPool(size: Int) {
    val pool: Array<String?> = arrayOfNulls(size)

    // obf: m289
    fun get(index: Int): String {
        if (index >= pool.size) return ""
        return pool[index]!!
    }
}

// obf: C0059
data class StringPoolEntry(val idx: Int, val offset: Long)

/** obf: AbstractC0094 */
// obf: AbstractC0094
internal object ParseUtils {

    // obf: f194
    val charsetUTF8: Charset = Charsets.UTF_8

    /**
     * Reads a string pool. With [skipLongStrings] (only used for resources.arsc) UTF-8 strings of
     * 55 bytes or more are skipped and stored as "".
     */
    // obf: m125
    fun readStringPool(buffer: ByteBuffer, stringPoolHeader: StringPoolHeader, skipLongStrings: Boolean): StringPool {
        val beginPos = buffer.position().toLong()
        val count = stringPoolHeader.stringCount
        val offsets = IntArray(count)
        if (count > 0) {
            for (idx in 0 until count) {
                offsets[idx] = Buffers.readUInt(buffer).toInt()
            }
        }
        val utf8 = (stringPoolHeader.flags and 256L) != 0L
        val stringPos = stringPoolHeader.stringsStart + beginPos - stringPoolHeader.headerSize
        Buffers.position(buffer, stringPos)
        val entries = arrayOfNulls<StringPoolEntry>(count)
        for (i in 0 until count) {
            entries[i] = StringPoolEntry(i, (offsets[i].toLong() and 0xffffffffL) + stringPos)
        }
        val stringPool = StringPool(stringPoolHeader.stringCount)
        var lastStr: String? = null
        var lastOffset = -1L
        for (entry in entries) {
            if (entry == null) continue
            if (entry.offset == lastOffset) {
                stringPool.pool[entry.idx] = lastStr
                continue
            }
            Buffers.position(buffer, entry.offset)
            val str: String
            if (utf8) {
                if ((Buffers.readUByte(buffer).toInt() and 128) != 0) {
                    Buffers.readUByte(buffer)
                }
                var len = Buffers.readUByte(buffer).toInt()
                if ((len and 128) != 0) {
                    len = ((len and 127) shl 8) + Buffers.readUByte(buffer)
                }
                if (len < 55 || !skipLongStrings) {
                    val bytes = ByteArray(len)
                    buffer.get(bytes)
                    str = String(bytes, charsetUTF8)
                } else {
                    str = ""
                    Buffers.position(buffer, buffer.position() + len)
                }
                Buffers.readUByte(buffer)
            } else {
                var len = Buffers.readUShort(buffer)
                if ((len and 0x8000) != 0) {
                    len = ((len and 0x7fff) shl 16) + Buffers.readUShort(buffer)
                }
                str = Buffers.readString(buffer, len)
                Buffers.readUShort(buffer)
            }
            stringPool.pool[entry.idx] = str
            lastStr = str
            lastOffset = entry.offset
        }
        Buffers.position(buffer, beginPos + stringPoolHeader.bodySize)
        return stringPool
    }

    // obf: m126
    fun readResValue(buffer: ByteBuffer?, stringPool: StringPool?): ResourceValue? {
        if (buffer == null || stringPool == null) return null
        Buffers.readUShort(buffer) // size
        Buffers.readUByte(buffer)  // res0
        val dataType = Buffers.readUByte(buffer).toInt()
        return when (dataType) {
            16 -> DecimalResourceValue(buffer.int)          // TYPE_INT_DEC
            17 -> HexResourceValue(buffer.int)              // TYPE_INT_HEX
            3 -> {                                          // TYPE_STRING
                val strRef = buffer.int
                if (strRef < 0) null else StringResourceValue(strRef, stringPool)
            }
            1 -> ReferenceResourceValue(buffer.int)         // TYPE_REFERENCE
            18 -> BooleanResourceValue(buffer.int)          // TYPE_INT_BOOLEAN
            // BUG(original, inherited from apk-parser): TYPE_NULL does not consume the 4 data
            // bytes, so the rest of the chunk is read misaligned.
            0 -> EmptyResourceValue.INSTANCE                // TYPE_NULL
            29, 31 -> RGBResourceValue(buffer.int, 6)       // TYPE_INT_COLOR_RGB8 / RGB4
            28, 30 -> RGBResourceValue(buffer.int, 8)       // TYPE_INT_COLOR_ARGB8 / ARGB4
            4 -> FloatResourceValue(buffer.int)             // TYPE_FLOAT
            5 -> DimensionResourceValue(buffer.int)         // TYPE_DIMENSION
            6 -> FractionResourceValue(buffer.int)          // TYPE_FRACTION
            else -> RawResourceValue(buffer.int, dataType.toShort())
        }
    }
}

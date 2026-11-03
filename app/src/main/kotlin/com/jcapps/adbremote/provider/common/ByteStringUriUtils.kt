package com.jcapps.adbremote.provider.common

import java.net.URI
import java.net.URISyntaxException

/**
 * Percent-encoding helpers between [ByteString] and URI strings, plus building a hierarchical
 * URI from (scheme, authority, path, query) (MaterialFiles ByteStringUriUtils / URI.create).
 */
object ByteStringUriUtils {
    // obf: n0

    private const val ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val DIGIT = "0123456789"
    private const val UNRESERVED = "$ALPHA$DIGIT-._~"
    private const val SUB_DELIMS = "!\$&'()*+,;="
    private const val PCHAR = "$UNRESERVED$SUB_DELIMS:@"
    private const val PATH_ALLOWED = "$PCHAR/"
    private const val QUERY_ALLOWED = "$PCHAR/?"

    private val SLASH = "/".toByteString()

    // obf: m1339 — percent-decode a raw URI component into bytes
    fun decode(encoded: String): ByteString {
        val builder = ByteStringBuilder()
        val length = encoded.length
        var index = 0
        while (index < length) {
            val byte = getAsciiByte(encoded, index)
            if (byte == '%'.code.toByte()) {
                val nextIndex = index + 3
                require(nextIndex <= length) { "Incomplete percent-encoding in URI" }
                val highHalfByte = decodeHexCharacter(getAsciiByte(encoded, index + 1))
                val lowHalfByte = decodeHexCharacter(getAsciiByte(encoded, index + 2))
                builder.append(((highHalfByte.toInt() shl 4).toByte().toInt() or lowHalfByte.toInt()).toByte())
                index = nextIndex
            } else {
                builder.append(byte)
                index++
            }
        }
        return builder.toByteString()
    }

    // obf: m1342
    private fun getAsciiByte(string: String, index: Int): Byte {
        val char = string[index]
        require(char.code == char.code and 0x7F) { "Non-ASCII character $char in URI" }
        return char.code.toByte()
    }

    // obf: m1338
    private fun decodeHexCharacter(byte: Byte): Byte {
        val value = when (byte.toInt()) {
            in '0'.code..'9'.code -> byte.toInt().toChar() - '0'
            in 'A'.code..'F'.code -> byte.toInt().toChar() - '7'
            in 'a'.code..'f'.code -> byte.toInt().toChar() - 'W'
            else -> throw IllegalArgumentException(
                "Non-hex-character ${byte.toInt().toChar()} for percent-encoding in URI"
            )
        }
        return value.toByte()
    }

    // obf: m1341 — percent-encode every byte not in [allowedCharacters]
    fun encode(decoded: ByteString, allowedCharacters: String): String {
        val builder = StringBuilder()
        for (byte in decoded.bytes) {
            val char = byte.toInt().toChar()
            if (allowedCharacters.indexOf(char) != -1) {
                builder.append(char)
            } else {
                builder.append('%')
                builder.append(encodeHalfByte(((byte.toInt() ushr 4).toByte().toInt() and 0x0F).toByte()))
                builder.append(encodeHalfByte((byte.toInt() and 0x0F).toByte()))
            }
        }
        return builder.toString()
    }

    // obf: m1340
    private fun encodeHalfByte(halfByte: Byte): Char {
        val value = when (halfByte.toInt()) {
            in 0 until 10 -> halfByte + '0'.code
            in 10 until 16 -> halfByte + ('A'.code - 10)
            else -> throw IllegalArgumentException(
                "Non-half-byte ${halfByte.toInt()} for percent-encoding in URI"
            )
        }
        return value.toChar()
    }

    /**
     * Build `scheme://authority/path[?query]` with percent-encoded path and query (inlined into
     * ByteStringListPath.toUri in the artifact).
     */
    fun createUri(
        scheme: String,
        userInfo: String?,
        host: String,
        port: Int?,
        path: ByteString,
        query: ByteString?,
    ): URI {
        val builder = StringBuilder()
        builder.append(scheme)
        builder.append(':')
        builder.append("//")
        try {
            val rawAuthority = URI(null, userInfo, host, port ?: -1, "/", null, null).rawAuthority
            builder.append(rawAuthority ?: "")
            val pathBytes = path.bytes
            if (pathBytes.isNotEmpty()) {
                val prefix = SLASH.bytes
                val startsWithSlash = pathBytes.size >= prefix.size &&
                    prefix.indices.all { pathBytes[it] == prefix[it] }
                require(startsWithSlash) {
                    "Path $path must either be empty or begin with a slash character"
                }
            }
            builder.append(encode(path, PATH_ALLOWED))
            if (query != null) {
                builder.append('?')
                builder.append(encode(query, QUERY_ALLOWED))
            }
            return URI.create(builder.toString())
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException(e)
        }
    }
}

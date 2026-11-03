package com.jcapps.adbremote.provider.common

import android.os.Parcel
import android.os.Parcelable
import java.util.Arrays

/**
 * An immutable byte sequence used to model filesystem path components (MaterialFiles style).
 * Equality/ordering are byte-wise; [toString] decodes lazily (UTF-8) and caches the result.
 */
class ByteString(val bytes: ByteArray) : Comparable<ByteString>, Parcelable {
    // obf: C3091

    // obf: f11118 — cached decoded string
    private var string: String? = null

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ByteString
        return Arrays.equals(bytes, other.bytes)
    }

    override fun hashCode(): Int = Arrays.hashCode(bytes)

    override fun toString(): String {
        string?.let { return it }
        return String(bytes, Charsets.UTF_8).also { string = it }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByteArray(bytes)
    }

    override fun compareTo(other: ByteString): Int {
        val bytes = bytes
        val otherBytes = other.bytes
        val size = bytes.size
        val otherSize = otherBytes.size
        val minSize = minOf(size, otherSize)
        for (i in 0 until minSize) {
            val result = bytes[i] - otherBytes[i]
            if (result != 0) return result
        }
        return size - otherSize
    }

    companion object {
        // obf: f11116
        @JvmField
        val EMPTY = ByteString(ByteArray(0))

        // obf: inlined into AbstractC3179.m5693 — builds a ByteString with its string pre-cached
        fun fromString(string: String): ByteString =
            ByteString(string.toByteArray(Charsets.UTF_8)).apply { this.string = string }

        // obf: C0213 case 10
        @JvmField
        val CREATOR = object : Parcelable.Creator<ByteString> {
            override fun createFromParcel(source: Parcel): ByteString =
                ByteString(source.createByteArray()!!)

            override fun newArray(size: Int): Array<ByteString?> = arrayOfNulls(size)
        }
    }
}

// obf: AbstractC3179.m5693
fun String.toByteString(): ByteString = ByteString.fromString(this)

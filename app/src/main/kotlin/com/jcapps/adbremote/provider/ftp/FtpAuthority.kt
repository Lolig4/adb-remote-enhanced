package com.jcapps.adbremote.provider.ftp

import android.os.Parcel
import android.os.Parcelable
import java.nio.charset.StandardCharsets

/**
 * The full FTP authority (protocol, host, port, username, mode, encoding) — the key under which
 * [FtpFileSystem]s and pooled clients are cached. Parcelable.
 */
class FtpAuthority(
    // obf: f5986
    val protocol: FtpProtocol,
    // obf: f5990
    val host: String,
    // obf: f5988
    val port: Int,
    // obf: f5987
    val username: String,
    // obf: f5989
    val mode: FtpMode,
    // obf: f5991
    val encoding: String,
) : Parcelable {
    // obf: C1222

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FtpAuthority) return false
        return protocol == other.protocol && host == other.host && port == other.port &&
            username == other.username && mode == other.mode && encoding == other.encoding
    }

    override fun hashCode(): Int {
        var result = protocol.hashCode()
        result = result * 31 + host.hashCode()
        result = result * 31 + port
        result = result * 31 + username.hashCode()
        result = result * 31 + mode.hashCode()
        return result * 31 + encoding.hashCode()
    }

    override fun toString(): String = toAuthorityString().toString()

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(protocol.name)
        dest.writeString(host)
        dest.writeInt(port)
        dest.writeString(username)
        dest.writeString(mode.name)
        dest.writeString(encoding)
    }

    // obf: m3204
    fun toAuthorityString(): FtpAuthorityString {
        val user = username.ifEmpty { null }
        return FtpAuthorityString(user, host, if (port != protocol.defaultPort) port else null)
    }

    companion object {
        // static init (companion C3025)
        const val ANONYMOUS_USERNAME = "anonymous"
        const val ANONYMOUS_PASSWORD = "guest"

        // obf: f5984 — default mode
        @JvmField val DEFAULT_MODE: FtpMode = FtpMode.PASSIVE

        // obf: f5985 — default encoding (UTF-8)
        @JvmField val DEFAULT_ENCODING: String = StandardCharsets.UTF_8.name()

        @JvmField
        val CREATOR = object : Parcelable.Creator<FtpAuthority> {
            override fun createFromParcel(source: Parcel): FtpAuthority =
                FtpAuthority(
                    FtpProtocol.valueOf(source.readString()!!),
                    source.readString()!!,
                    source.readInt(),
                    source.readString()!!,
                    FtpMode.valueOf(source.readString()!!),
                    source.readString()!!,
                )
            override fun newArray(size: Int): Array<FtpAuthority?> = arrayOfNulls(size)
        }
    }
}

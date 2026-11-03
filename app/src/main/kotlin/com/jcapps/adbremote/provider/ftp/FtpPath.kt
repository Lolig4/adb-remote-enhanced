package com.jcapps.adbremote.provider.ftp

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.jcapps.adbremote.compat.readParcelable
import com.jcapps.adbremote.provider.common.ByteString
import com.jcapps.adbremote.provider.common.ByteStringListPath
import com.jcapps.adbremote.provider.common.toByteString
import java.io.File
import java.util.Locale
import java8.nio.file.LinkOption
import java8.nio.file.Path

/** A concrete FTP [Path], a [ByteStringListPath] bound to its [FtpFileSystem]. */
class FtpPath : ByteStringListPath<FtpPath> {
    // obf: C2089

    // obf: f8417
    private val fileSystem: FtpFileSystem

    // obf: C2089(C3591, C3091)
    constructor(fileSystem: FtpFileSystem, path: ByteString) : super(FtpFileSystem.SEPARATOR, path) {
        this.fileSystem = fileSystem
    }

    // obf: C2089(C3591, boolean, List)
    constructor(fileSystem: FtpFileSystem, absolute: Boolean, segments: List<ByteString>) :
        super(FtpFileSystem.SEPARATOR, absolute, segments) {
        this.fileSystem = fileSystem
    }

    // obf: C2089(Parcel)
    constructor(source: Parcel) : super(source) {
        fileSystem = source.readParcelable<FtpFileSystem>()!!
    }

    override fun isPathAbsolute(path: ByteString): Boolean {
        val bytes = path.bytes
        return bytes.isNotEmpty() && bytes[0] == FtpFileSystem.SEPARATOR
    }

    override fun createPath(path: ByteString): FtpPath = FtpPath(fileSystem, path)

    override fun createPath(absolute: Boolean, segments: List<ByteString>): FtpPath =
        FtpPath(fileSystem, absolute, segments)

    override val defaultDirectory: FtpPath
        get() = fileSystem.defaultDirectory

    override val uriScheme: String
        get() = fileSystem.authority.protocol.scheme

    override val uriUserInfo: String?
        get() = fileSystem.authority.toAuthorityString().username

    override val uriHost: String
        get() = fileSystem.authority.toAuthorityString().host

    override val uriPort: Int?
        get() = fileSystem.authority.toAuthorityString().port

    override val uriQuery: ByteString?
        get() {
            val authority = fileSystem.authority
            val uriBuilder = Uri.Builder()
            if (authority.mode != FtpAuthority.DEFAULT_MODE) {
                uriBuilder.appendQueryParameter(
                    QUERY_PARAMETER_MODE, authority.mode.name.lowercase(Locale.ROOT)
                )
            }
            if (authority.encoding != FtpAuthority.DEFAULT_ENCODING) {
                uriBuilder.appendQueryParameter(QUERY_PARAMETER_ENCODING, authority.encoding)
            }
            return uriBuilder.build().query?.toByteString()
        }

    override fun getFileSystem(): FtpFileSystem = fileSystem

    override fun getRoot(): FtpPath? = if (isAbsolute()) fileSystem.rootDirectory else null

    // obf: m4270 — resolve a child name
    override fun resolve(other: String): Path = resolve(fileSystem.getPath(other))

    // RECON-UNSURE: stripped by R8 (MaterialFiles FtpPath.toRealPath)
    override fun toRealPath(vararg options: LinkOption): FtpPath {
        throw UnsupportedOperationException()
    }

    // inlined into FileProvider.query (the "_data" column always hits this)
    override fun toFile(): File {
        throw UnsupportedOperationException()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)
        dest.writeParcelable(fileSystem, flags)
    }

    companion object {
        // static init
        const val QUERY_PARAMETER_MODE = "mode"
        const val QUERY_PARAMETER_ENCODING = "encoding"

        // obf: C0213 case 23
        @JvmField
        val CREATOR = object : Parcelable.Creator<FtpPath> {
            override fun createFromParcel(source: Parcel): FtpPath = FtpPath(source)
            override fun newArray(size: Int): Array<FtpPath?> = arrayOfNulls(size)
        }
    }
}

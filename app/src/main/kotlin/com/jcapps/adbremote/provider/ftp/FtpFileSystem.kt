package com.jcapps.adbremote.provider.ftp

import android.os.Parcel
import android.os.Parcelable
import com.jcapps.adbremote.compat.readParcelable
import com.jcapps.adbremote.provider.common.ByteString
import com.jcapps.adbremote.provider.common.ByteStringBuilder
import com.jcapps.adbremote.provider.common.toByteString
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.Path
import java8.nio.file.PathMatcher
import java8.nio.file.WatchService
import java8.nio.file.attribute.UserPrincipalLookupService
import java8.nio.file.spi.FileSystemProvider

/**
 * A java.nio FileSystem for one [FtpAuthority] (MaterialFiles FtpFileSystem).
 *
 * RECON-UNSURE: isReadOnly/getSeparator/getRootDirectories/getFileStores/
 * supportedFileAttributeViews/getPathMatcher/getUserPrincipalLookupService/newWatchService were
 * stripped by R8 (never called); bodies after MaterialFiles.
 */
class FtpFileSystem(
    // obf: f12805
    private val provider: FtpFileSystemProvider,
    // obf: f12809
    val authority: FtpAuthority,
) : FileSystem(), Parcelable {
    // obf: C3591

    // obf: f12807 — root directory ("/")
    val rootDirectory = FtpPath(this, SEPARATOR_BYTE_STRING)

    init {
        if (!rootDirectory.isAbsolute()) {
            throw AssertionError("Root directory must be absolute")
        }
        if (rootDirectory.segments.size != 0) {
            throw AssertionError("Root directory must contain no names")
        }
    }

    // obf: f12806 — close lock
    private val lock = Any()

    // obf: f12808 — open flag
    private var isOpen = true

    val defaultDirectory: FtpPath
        get() = rootDirectory

    override fun provider(): FileSystemProvider = provider

    override fun close() {
        synchronized(lock) {
            if (!isOpen) {
                return
            }
            provider.removeFileSystem(this)
            isOpen = false
        }
    }

    override fun isOpen(): Boolean = synchronized(lock) { isOpen }

    override fun isReadOnly(): Boolean = false

    override fun getSeparator(): String = SEPARATOR_STRING

    override fun getRootDirectories(): Iterable<Path> = listOf(rootDirectory)

    override fun getFileStores(): Iterable<FileStore> {
        throw UnsupportedOperationException()
    }

    override fun supportedFileAttributeViews(): Set<String> = setOf("basic")

    override fun getPath(first: String, vararg more: String): FtpPath {
        val path = ByteStringBuilder(first.toByteString())
            .apply { more.forEach { append(SEPARATOR).append(it.toByteString()) } }
            .toByteString()
        return FtpPath(this, path)
    }

    fun getPath(first: ByteString, vararg more: ByteString): FtpPath {
        val path = ByteStringBuilder(first)
            .apply { more.forEach { append(SEPARATOR).append(it) } }
            .toByteString()
        return FtpPath(this, path)
    }

    override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
        throw UnsupportedOperationException()
    }

    override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
        throw UnsupportedOperationException()
    }

    override fun newWatchService(): WatchService {
        throw UnsupportedOperationException()
    }

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FtpFileSystem
        return authority == other.authority
    }

    override fun hashCode(): Int = authority.hashCode()

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(authority, flags)
    }

    companion object {
        const val SEPARATOR = '/'.code.toByte()

        // obf: f12804 — the separator ByteString "/"
        @JvmField
        val SEPARATOR_BYTE_STRING = ByteString(byteArrayOf(SEPARATOR))

        private const val SEPARATOR_STRING = "/"

        // obf: C0213 case 22
        @JvmField
        val CREATOR = object : Parcelable.Creator<FtpFileSystem> {
            override fun createFromParcel(source: Parcel): FtpFileSystem {
                val authority = source.readParcelable<FtpAuthority>()!!
                return FtpFileSystemProvider.getOrNewFileSystem(authority)
            }

            override fun newArray(size: Int): Array<FtpFileSystem?> = arrayOfNulls(size)
        }
    }
}

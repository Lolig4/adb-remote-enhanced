package com.jcapps.adbremote.provider.common

import android.os.Parcelable
import com.jcapps.adbremote.provider.ftp.FtpFileType
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileTime

/**
 * Abstract Parcelable [BasicFileAttributes] base (MaterialFiles AbstractBasicFileAttributes);
 * only [com.jcapps.adbremote.provider.ftp.FtpFileAttributes] extends it. R8 devirtualized and
 * removed all of its members in the artifact.
 *
 * RECON-UNSURE: exact original name and member list (reconstructed after MaterialFiles).
 */
abstract class AbstractFileAttributes : BasicFileAttributes, Parcelable {
    // obf: AbstractC1344

    protected abstract val lastModifiedTime: FileTime
    protected abstract val lastAccessTime: FileTime
    protected abstract val creationTime: FileTime
    protected abstract val type: FtpFileType
    protected abstract val size: Long
    protected abstract val fileKey: Parcelable

    override fun lastModifiedTime(): FileTime = lastModifiedTime

    override fun lastAccessTime(): FileTime = lastAccessTime

    override fun creationTime(): FileTime = creationTime

    override fun isRegularFile(): Boolean = type == FtpFileType.REGULAR_FILE

    override fun isDirectory(): Boolean = type == FtpFileType.DIRECTORY

    override fun isSymbolicLink(): Boolean = type == FtpFileType.SYMBOLIC_LINK

    override fun isOther(): Boolean = type == FtpFileType.OTHER

    override fun size(): Long = size

    override fun fileKey(): Any = fileKey
}

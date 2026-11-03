package com.jcapps.adbremote.provider.ftp

import android.os.Parcel
import android.os.Parcelable
import com.jcapps.adbremote.provider.common.AbstractFileAttributes
import java8.nio.file.attribute.FileTime
import org.threeten.bp.Instant

/** java.nio BasicFileAttributes for an FTP file (MaterialFiles style), Parcelable. */
class FtpFileAttributes(
    // obf: f10389
    override val lastModifiedTime: FileTime,
    // obf: f10393
    override val lastAccessTime: FileTime,
    // obf: f10391
    override val creationTime: FileTime,
    // obf: f10390
    override val type: FtpFileType,
    // obf: f10392
    override val size: Long,
    // obf: f10394
    override val fileKey: Parcelable,
) : AbstractFileAttributes() {
    // obf: C2834

    override fun describeContents(): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FtpFileAttributes) return false
        return lastModifiedTime == other.lastModifiedTime && lastAccessTime == other.lastAccessTime &&
            creationTime == other.creationTime && type == other.type && size == other.size &&
            fileKey == other.fileKey
    }

    override fun hashCode(): Int {
        var result = lastModifiedTime.hashCode()
        result = result * 31 + lastAccessTime.hashCode()
        result = result * 31 + creationTime.hashCode()
        result = result * 31 + type.hashCode()
        result = result * 31 + size.hashCode()
        return result * 31 + fileKey.hashCode()
    }

    override fun toString(): String =
        "FtpFileAttributes(lastModifiedTime=$lastModifiedTime, lastAccessTime=$lastAccessTime, " +
            "creationTime=$creationTime, type=$type, size=$size, fileKey=$fileKey)"

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(lastModifiedTime.toInstant())
        dest.writeSerializable(lastAccessTime.toInstant())
        dest.writeSerializable(creationTime.toInstant())
        dest.writeString(type.name)
        dest.writeLong(size)
        dest.writeParcelable(fileKey, flags)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<FtpFileAttributes> {
            // obf: C0213 case 21
            override fun createFromParcel(source: Parcel): FtpFileAttributes =
                FtpFileAttributes(
                    source.readFileTime()!!,
                    source.readFileTime()!!,
                    source.readFileTime()!!,
                    FtpFileType.valueOf(source.readString()!!),
                    source.readLong(),
                    source.readParcelable(FtpFileAttributes::class.java.classLoader)!!,
                )
            override fun newArray(size: Int): Array<FtpFileAttributes?> = arrayOfNulls(size)
        }
    }
}

// obf: AbstractC1557.m3556 — RECON-UNSURE: the original retrofile build used java.time (desugared)
// Instants; retrofile 1.1.1 on the classpath uses org.threeten.bp.Instant.
private fun Parcel.readFileTime(): FileTime? =
    (readSerializable() as Instant?)?.let { FileTime.from(it) }

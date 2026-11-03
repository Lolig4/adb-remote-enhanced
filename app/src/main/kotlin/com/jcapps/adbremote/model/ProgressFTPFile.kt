package com.jcapps.adbremote.model

import java8.nio.file.Path

/** A download/copy/cut item: a file already on the FTP server. */
class ProgressFTPFile(
    // obf: f7406 / mo3829
    override val id: Int,
    // obf: f7405 / mo3830
    override val name: String,
    // obf: f7410
    val fileName: String,
    // obf: f7408 / mo3827
    override val size: Long,
    // obf: f7412
    val isDirectory: Boolean,
    // obf: f7411
    val source: Path,
    // obf: f7407
    val mimeType: String,
    // obf: f7409 — RECON-UNSURE: element type (relative sub-directory paths for folder transfers)
    val subDirs: List<String>,
) : TransferFile(name, fileName) {
    // obf: C1733

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProgressFTPFile) return false
        return id == other.id && name == other.name && fileName == other.fileName &&
            size == other.size && isDirectory == other.isDirectory && source == other.source &&
            mimeType == other.mimeType && subDirs == other.subDirs
    }

    override fun hashCode(): Int {
        var result = id * 31 + name.hashCode()
        result = result * 31 + fileName.hashCode()
        result = result * 31 + size.hashCode()
        result = result * 31 + if (isDirectory) 1231 else 1237
        result = result * 31 + source.hashCode()
        result = result * 31 + mimeType.hashCode()
        return result * 31 + subDirs.hashCode()
    }

    override fun toString(): String =
        "ProgressFTPFile(id=$id, name=$name, fileName=$fileName, size=$size, " +
            "isDirectory=$isDirectory, source=$source, mimeType=$mimeType, subDirs=$subDirs)"
}

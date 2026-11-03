package com.jcapps.adbremote.model

import android.net.Uri
import java8.nio.file.Path

/** An upload item: a local SAF-picked file/folder to be sent to the FTP server. */
class PickedFile(
    // obf: f13775 / mo3829
    override val id: Int,
    // obf: f13774 / mo3830
    override val name: String,
    // obf: f13778
    val fileName: String,
    // obf: f13777 / mo3827
    override val size: Long,
    // obf: f13780
    val isDirectory: Boolean,
    // obf: f13779
    val source: Uri,
    // obf: f13776
    val destFolder: Path,
) : TransferFile(name, fileName) {
    // obf: C3925

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedFile) return false
        return id == other.id && name == other.name && fileName == other.fileName &&
            size == other.size && isDirectory == other.isDirectory && source == other.source &&
            destFolder == other.destFolder
    }

    override fun hashCode(): Int {
        var result = id * 31 + name.hashCode()
        result = result * 31 + fileName.hashCode()
        result = result * 31 + size.hashCode()
        result = result * 31 + if (isDirectory) 1231 else 1237
        result = result * 31 + source.hashCode()
        return result * 31 + destFolder.hashCode()
    }

    override fun toString(): String =
        "PickedFile(id=$id, name=$name, fileName=$fileName, size=$size, " +
            "isDirectory=$isDirectory, source=$source, destFolder=$destFolder)"
}

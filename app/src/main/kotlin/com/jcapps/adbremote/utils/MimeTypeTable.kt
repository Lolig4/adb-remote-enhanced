package com.jcapps.adbremote.utils

import android.webkit.MimeTypeMap
import com.jcapps.adbremote.provider.common.MimeType
import com.jcapps.adbremote.provider.common.PosixFileType
import com.jcapps.adbremote.provider.common.asMimeType
import com.jcapps.adbremote.provider.common.asMimeTypeOrNull
import java.util.Locale

/**
 * Extension -> MIME type overrides plus [getMimeTypeFromPath] (MaterialFiles MimeType.guessFrom*).
 * Supplements [ExtensionMimeTypes] and the platform MimeTypeMap.
 */
object MimeTypeTable {
    // obf: AbstractC3456

    // obf: f12338 — extension (lowercase) -> MimeType
    val mimeTypeByExtension: Map<String, MimeType> = mapOf(
        "csv" to "text/csv",
        "sh" to "application/x-sh",
        "bz" to "application/x-bzip",
        "bz2" to "application/x-bzip2",
        "z" to "application/x-compress",
        "lzma" to "application/x-lzma",
        "p7b" to "application/x-pkcs7-certificates",
        "spc" to "application/x-pkcs7-certificates",
        "ts" to "application/typescript",
        "py3" to "text/x-python",
        "py3x" to "text/x-python",
        "pyx" to "text/x-python",
        "wsgi" to "text/x-python",
        "yml" to "application/yaml",
        "asm" to "text/x-asm",
        "s" to "text/x-asm",
        "cs" to "text/x-csharp",
        "azw" to "application/vnd.amazon.ebook",
        "ibooks" to "application/x-ibooks+zip",
        "msg" to "application/vnd.ms-outlook",
        "mkd" to "text/markdown",
        "conf" to "text/plain",
        "ini" to "text/plain",
        "list" to "text/plain",
        "log" to "text/plain",
        "prop" to "text/plain",
        "properties" to "text/plain",
        "rc" to "text/plain",
    ).mapValues { it.value.asMimeType() }

    // static init — built but unused in this build
    @Suppress("unused")
    private val posixFileTypeToMimeTypeMap: Map<PosixFileType, MimeType> = mapOf(
        PosixFileType.CHARACTER_DEVICE to "inode/chardevice",
        PosixFileType.BLOCK_DEVICE to "inode/blockdevice",
        PosixFileType.FIFO to "inode/fifo",
        PosixFileType.SYMBOLIC_LINK to "inode/symlink",
        PosixFileType.SOCKET to "inode/socket",
    ).mapValues { it.value.asMimeType() }

    // static init — built but unused in this build
    @Suppress("unused")
    private val intrinsicMimeTypes: Map<MimeType, MimeType> = listOf(
        "application/ecmascript" to "text/ecmascript",
        "application/javascript" to "text/javascript",
        "application/json" to "text/json",
        "application/typescript" to "text/typescript",
        "application/yaml" to "text/x-yaml",
        "application/x-sh" to "text/x-shellscript",
        "application/x-shellscript" to "text/x-shellscript",
        MimeType.GENERIC.value to MimeType.ANY.value,
    ).associate { it.first.asMimeType() to it.second.asMimeType() }

    /**
     * Resolve a MIME type from a path's file name: app table -> bundled table -> platform
     * MimeTypeMap -> octet-stream fallback; an empty file name maps to the directory type.
     * obf: m6002
     */
    fun getMimeTypeFromPath(path: String): String {
        require(path.isNotEmpty() && '\u0000' !in path)
        val fileName = path.substringAfterLast('/', path).ifEmpty { null }
            ?: return MimeType.DIRECTORY.value
        require(fileName.isNotEmpty() && '\u0000' !in fileName && '/' !in fileName)
        val extension = fileName.substringAfterLast('.', "")
        return getMimeTypeFromExtension(extension)
    }

    private fun getMimeTypeFromExtension(extension: String): String {
        val lowercaseExtension = extension.lowercase(Locale.ROOT)
        mimeTypeByExtension[lowercaseExtension]?.let { return it.value }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtensionCompat(lowercaseExtension)
            ?.asMimeTypeOrNull()?.value
            ?: MimeType.GENERIC.value
    }
}

package com.jcapps.adbremote.utils

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.jcapps.adbremote.model.PickedFile
import com.jcapps.adbremote.model.ProgressFTPFile
import com.jcapps.adbremote.provider.common.ByteStringBuilder
import com.jcapps.adbremote.provider.common.toByteString
import com.jcapps.adbremote.provider.ftp.FtpAuthority
import com.jcapps.adbremote.provider.ftp.FtpClientHelper
import com.jcapps.adbremote.provider.ftp.FtpFileSystemProvider
import com.jcapps.adbremote.provider.ftp.FtpPath
import java.io.FileInputStream
import java8.nio.file.Path

/*
 * App top-level helpers of the file-transfer feature (R8-merged into AbstractC0913, AbstractC0395,
 * AbstractC0999, z8 and AbstractC2727). RECON-UNSURE: file and function names.
 */

/**
 * First free name of the form "name (n).ext" / "name (n)" (n = 1, 2, ...) for which
 * [fileNotExists] returns true.
 */
// obf: AbstractC0913.m2789
fun generateUniqueName(originalName: String, fileNotExists: (String) -> Boolean): String {
    val dotIndex = originalName.lastIndexOf('.')
    val (baseName, extension) = if (dotIndex != -1) {
        Pair(originalName.substring(0, dotIndex), originalName.substring(dotIndex + 1))
    } else {
        Pair(originalName, null)
    }
    var i = 1
    while (true) {
        val name = if (extension != null) "$baseName ($i).$extension" else "$baseName ($i)"
        if (fileNotExists(name)) {
            return name
        }
        i++
    }
}

/** The [FtpPath] for an absolute [path] on this authority. */
// obf: AbstractC0395.m2117
fun FtpAuthority.getPath(path: String): FtpPath =
    FtpPath(FtpFileSystemProvider.getOrNewFileSystem(this), ByteStringBuilder(path.toByteString()).toByteString())

/**
 * Transfer items for [path]: the file itself, or every file/folder below a folder (files first,
 * each level sorted by name). Empty when missing or on error.
 */
// obf: AbstractC0999.m2982
fun listFilesRecursively(path: Path): List<ProgressFTPFile> =
    runCatching {
        val ftpFile = FtpClientHelper.getFileOrNull(path)
        if (ftpFile == null) {
            emptyList()
        } else if (ftpFile.isFile) {
            listOf(
                ProgressFTPFile(
                    0, ftpFile.name, ftpFile.name, ftpFile.size, false,
                    (path as FtpPath).resolve(ftpFile.name), ftpFile.mimeType, emptyList(),
                ),
            )
        } else if (ftpFile.isDirectory) {
            val files = ArrayList<ProgressFTPFile>()
            collectFiles(path, files, emptyList())
            files
        } else {
            emptyList()
        }
    }.getOrElse { emptyList() }

// obf: AbstractC0999.m2937
private fun collectFiles(path: Path, files: ArrayList<ProgressFTPFile>, subDirs: List<String>) {
    val children = FtpClientHelper.listDirectory(path).sortedBy { it.isFile }
    for (ftpFile in children.filter { it.isFile }.sortedBy { it.name }) {
        val prefix = if (subDirs.isEmpty()) "" else subDirs.joinToString("/") + "/"
        files.add(
            ProgressFTPFile(
                files.size, prefix + ftpFile.name, ftpFile.name, ftpFile.size, false,
                (path as FtpPath).resolve(ftpFile.name), ftpFile.mimeType, subDirs,
            ),
        )
    }
    for (ftpFile in children.filter { it.isDirectory }.sortedBy { it.name }) {
        val prefix = if (subDirs.isEmpty()) "" else subDirs.joinToString("/") + "/"
        val ftpPath = path as FtpPath
        files.add(
            ProgressFTPFile(
                files.size, prefix + ftpFile.name, ftpFile.name, 0L, true,
                ftpPath.resolve(ftpFile.name), "vnd.android.document/directory", subDirs + ftpFile.name,
            ),
        )
        collectFiles(ftpPath.resolve(ftpFile.name), files, subDirs + ftpFile.name)
    }
}

/** Size of the document (`_size` column, falling back to the file descriptor's channel size). */
// obf: z8.m1599 (inlined Uri size helper)
fun DocumentFile.getFileSize(contentResolver: ContentResolver): Long? {
    var size: Long? = runCatching {
        contentResolver.query(uri, arrayOf("_size"), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
        }
    }.getOrNull()
    if (size == null) {
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                size = FileInputStream(pfd.fileDescriptor).channel.size()
            }
        } catch (e: Exception) {
            return null
        }
    }
    return size
}

/** Tree [DocumentFile] of this OpenDocumentTree uri, or null. */
// obf: z8.m1613
fun Uri.toTreeDocumentFile(context: Context): DocumentFile? =
    runCatching { DocumentFile.fromTreeUri(context, this) }.getOrNull()

/**
 * Recursively adds the files (then the sub folders, each followed by their content) of [folder]
 * to [pickedFiles]; names are relative to the upload root ([sourceFolderPath] prefix).
 */
// obf: AbstractC2727.m5135
fun collectPickedFiles(
    contentResolver: ContentResolver,
    pickedFiles: MutableList<PickedFile>,
    folder: DocumentFile?,
    sourceFolderPath: String,
    destFolder: Path,
) {
    if (folder == null || !folder.isDirectory) {
        return
    }
    val children = folder.listFiles().sortedBy { it.isFile }
    for (file in children.filter { it.isFile }.sortedBy { it.name }) {
        val pickedFile = file.toPickedFile(contentResolver, pickedFiles.size, destFolder, sourceFolderPath)
        if (pickedFile != null) {
            pickedFiles.add(pickedFile)
        }
    }
    for (directory in children.filter { it.isDirectory }.sortedBy { it.name }) {
        val name = directory.name
        if (name != null) {
            val ftpPath = destFolder as FtpPath
            pickedFiles.add(
                PickedFile(
                    pickedFiles.size, sourceFolderPath + name, name, 0L, true, directory.uri,
                    ftpPath.resolve(name),
                ),
            )
            collectPickedFiles(contentResolver, pickedFiles, directory, "$sourceFolderPath$name/", ftpPath.resolve(name))
        }
    }
}

/** Upload item for this document, or null when its name/size cannot be read. */
// obf: AbstractC2727.m5140
fun DocumentFile.toPickedFile(
    contentResolver: ContentResolver,
    id: Int,
    destFolder: Path,
    sourceFolderPath: String,
): PickedFile? =
    runCatching {
        val name = name ?: throw IllegalAccessException()
        val size = getFileSize(contentResolver) ?: throw IllegalAccessException()
        PickedFile(id, sourceFolderPath + name, name, size, false, uri, destFolder)
    }.getOrNull()

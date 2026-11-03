package com.jcapps.adbremote.transfer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.jcapps.adbremote.model.PickedFile
import com.jcapps.adbremote.provider.ftp.FtpClientHelper
import com.jcapps.adbremote.provider.ftp.FtpPath
import com.jcapps.adbremote.utils.collectPickedFiles
import com.jcapps.adbremote.utils.generateUniqueName
import com.jcapps.adbremote.utils.toPickedFile
import com.jcapps.adbremote.utils.toTreeDocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java8.nio.file.Path
import kotlin.coroutines.resume

/** Uploads SAF-picked files/folders ([PickedFile]) to the FTP server, one after another. */
class FileUploader(
    // obf: f12487
    private val contentResolver: ContentResolver,
    files: List<PickedFile>,
    listener: TransferListener,
) : FileTransfer(listener, files.sumOf { it.size }, ArrayList(List(files.size) { 0L })) {
    // obf: C3498

    // obf: f12485
    private val files: MutableList<PickedFile> = files.toMutableList()

    /** Conflict choice the user applied to all following items. */
    // obf: f12486
    private var conflictAction: ConflictAction? = null

    /** Uploads one item (creates folders; resumes partially uploaded files via APPE). */
    // obf: m6072
    private suspend fun upload(file: PickedFile) {
        if (file.isDirectory) {
            val destFolder = file.destFolder
            if (FtpClientHelper.getFileOrNull(destFolder) == null) {
                FtpClientHelper.createDirectory(destFolder)
            }
            onFileCompleted(file.id, file.size)
            return
        }
        val (fileName, offset) = getTarget(file)
        processedBytes = offset
        val inputStream = try {
            contentResolver.openInputStream(file.source)
                ?: throw TransferException("Failed to open input stream.")
        } catch (e: Exception) {
            throw TransferException(e)
        }
        val destFolder = file.destFolder
        val folder = FtpClientHelper.getFileOrNull(destFolder)
        if (folder == null) {
            FtpClientHelper.createDirectory(destFolder)
        } else if (!folder.isDirectory) {
            throw TransferException("Failed to create directory: " + (destFolder as FtpPath).toString())
        }
        val outputStream = FtpClientHelper.newOutputStream((destFolder as FtpPath).resolve(fileName))
        inputStream.use { input ->
            outputStream.use { output ->
                input.skip(processedBytes)
                val buffer = ByteArray(8192)
                var lastUpdate = System.currentTimeMillis()
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    processedBytes += bytesRead
                    val now = System.currentTimeMillis()
                    if (now >= progressUpdateInterval + lastUpdate) {
                        updateFileProgress(file.id, file.size)
                        lastUpdate = now
                    }
                }
            }
        }
        processedBytes = file.size
        onFileCompleted(file.id, file.size)
    }

    /** Applies [action] to the existing [path]; returns the file name to upload to. */
    // obf: m6073
    private fun resolveConflict(file: PickedFile, path: Path, action: ConflictAction): String =
        when (action) {
            ConflictAction.REPLACE -> {
                FtpClientHelper.deleteFile(path)
                file.fileName
            }
            ConflictAction.SKIP -> throw TransferException("File skipped.")
            ConflictAction.KEEP_BOTH -> {
                val newName = generateUniqueName(file.fileName) { name ->
                    // obf: C0953 case 2
                    FtpClientHelper.getFileOrNull((file.destFolder as FtpPath).resolve(name)) == null
                }
                val current = files[currentIndex]
                files[currentIndex] = PickedFile(
                    current.id, current.name, newName, current.size, current.isDirectory,
                    current.source, current.destFolder,
                )
                newName
            }
        }

    /** Target file name and the offset to resume from (asks on name conflicts). */
    // obf: m6074
    private suspend fun getTarget(file: PickedFile): Pair<String, Long> {
        val path = (file.destFolder as FtpPath).resolve(file.fileName)
        val ftpFile = FtpClientHelper.getFileOrNull(path) ?: return Pair(file.fileName, 0L)
        if (!ftpFile.isFile) {
            throw TransferException("Folder already exists with the same name.")
        }
        if (processedBytes > 0) {
            return Pair(file.fileName, ftpFile.size)
        }
        conflictAction?.let { return Pair(resolveConflict(file, path, it), 0L) }
        // obf: C1519
        val (action, applyToAll) = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                // obf: C2209 case 6
                listener.showConflictDialog(file.name) { action, applyToAll ->
                    continuation.resume(Pair(action, applyToAll))
                }
            }
        }
        if (applyToAll) {
            conflictAction = action
        }
        return Pair(resolveConflict(file, path, action), 0L)
    }

    /** Starts (or resumes) the upload unless it is already running. */
    // obf: m6075
    fun start() {
        val job = job
        if (job == null || !job.isActive) {
            // obf: C3061
            this.job = scope.launch {
                while (currentIndex < files.size) {
                    val file = files[currentIndex]
                    try {
                        upload(file)
                    } catch (e: TransferException) {
                        onFileError(false, file.id, e)
                    } catch (e: Exception) {
                        onFileError(true, file.id, e)
                        return@launch
                    }
                    currentIndex++
                }
                updateTotalProgress(true)
            }
        }
    }

    companion object {
        // obf: f12484 (C2352)

        /** Upload items for the picked [uris] into [uploadFolder] (unreadable ones are dropped). */
        // obf: inlined into C0273
        fun getPickedFiles(context: Context, uploadFolder: Path, uris: List<Uri>): List<PickedFile> =
            runCatching {
                val pickedFiles = ArrayList<PickedFile>()
                uris.forEachIndexed { index, uri ->
                    val pickedFile = runCatching {
                        DocumentFile.fromSingleUri(context, uri)!!
                            .toPickedFile(context.contentResolver, index, uploadFolder, "")
                    }.getOrNull()
                    if (pickedFile != null) {
                        pickedFiles.add(pickedFile)
                    }
                }
                pickedFiles
            }.getOrElse { emptyList() }

        /**
         * Upload items for the picked folder tree [uri]: creates the folder on the server (asking
         * the [listener] when a file with its name exists) and lists its content.
         */
        // obf: C2352.m4586
        suspend fun getFolderFiles(
            context: Context,
            destFolder: FtpPath,
            uri: Uri,
            listener: TransferListener,
        ): List<PickedFile> =
            runCatching {
                val folder = uri.toTreeDocumentFile(context)
                val pickedFiles = ArrayList<PickedFile>()
                if (folder != null && folder.canRead()) {
                    var folderName = folder.name ?: throw IllegalAccessException("Folder name is null.")
                    var dest = destFolder.resolve(folderName)
                    val ftpFile = FtpClientHelper.getFileOrNull(dest)
                    if (ftpFile == null) {
                        FtpClientHelper.createDirectory(dest)
                    } else if (!ftpFile.isDirectory) {
                        val name = folderName
                        // obf: C1809
                        val (action, _) = withContext(Dispatchers.Main) {
                            suspendCancellableCoroutine { continuation ->
                                // obf: C2209 case 5
                                listener.showConflictDialog(name) { action, applyToAll ->
                                    continuation.resume(Pair(action, applyToAll))
                                }
                            }
                        }
                        when (action) {
                            ConflictAction.REPLACE -> FtpClientHelper.deleteFile(dest)
                            ConflictAction.SKIP -> throw TransferException("Folder skipped.")
                            ConflictAction.KEEP_BOTH -> folderName = generateUniqueName(folderName) {
                                // obf: C0953 case 3
                                FtpClientHelper.getFileOrNull(destFolder.resolve(it)) == null
                            }
                        }
                        dest = destFolder.resolve(folderName)
                        FtpClientHelper.createDirectory(dest)
                    }
                    collectPickedFiles(context.contentResolver, pickedFiles, folder, "", dest)
                }
                pickedFiles
            }.getOrElse { emptyList() }
    }
}

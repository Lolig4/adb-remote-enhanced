package com.jcapps.adbremote.transfer

import com.jcapps.adbremote.model.MoveResult
import com.jcapps.adbremote.model.ProgressFTPFile
import com.jcapps.adbremote.provider.ftp.FtpClientHelper
import com.jcapps.adbremote.provider.ftp.FtpPath
import com.jcapps.adbremote.utils.generateUniqueName
import com.jcapps.adbremote.utils.listFilesRecursively
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java8.nio.file.Path
import kotlin.coroutines.resume

/**
 * Copies or moves FTP files ([ProgressFTPFile]) from [sourcePath] into [destPath] on the same
 * server, one after another. RECON-UNSURE: class name.
 */
class FtpFileCopier(
    // obf: f11981
    private val type: Type,
    // obf: f11977
    private val sourcePath: Path,
    // obf: f11979
    private val destPath: Path,
    // obf: f11980
    private val files: List<ProgressFTPFile>,
    listener: TransferListener,
) : FileTransfer(listener, files.sumOf { it.size }, ArrayList(List(files.size) { 0L })) {
    // obf: C3362

    // obf: EnumC3622
    enum class Type {
        COPY,
        MOVE,
    }

    /** Conflict choice the user applied to all following items. */
    // obf: f11978
    private var conflictAction: ConflictAction? = null

    /** False once an item failed (then the moved source folder is kept). */
    // obf: f11982
    private var isSuccessful: Boolean = true

    /** Moves one item with RNFR/RNTO. */
    // obf: m5922
    private suspend fun move(file: ProgressFTPFile) {
        val id = file.id
        val size = file.size
        val name = file.name
        if (file.isDirectory) {
            val path = (destPath as FtpPath).resolve(name)
            if (FtpClientHelper.getFileOrNull(path) == null) {
                FtpClientHelper.createDirectory(path)
            }
            onFileCompleted(id, size)
            return
        }
        val target = (destPath as FtpPath).resolve(name)
        val existing = FtpClientHelper.getFileOrNull(target)
        val source = file.source
        if (existing == null) {
            val moved = runCatching {
                FtpClientHelper.rename(source, target)
                true
            }.getOrElse { false }
            if (!moved) {
                throw TransferException("Failed to move this file.")
            }
            onFileCompleted(id, size)
            return
        }
        if (!existing.isFile) {
            throw TransferException("Folder already exists with the same name.")
        }
        val action = conflictAction
        if (action != null) {
            FtpClientHelper.rename(source, (getDestFolder(file) as FtpPath).resolve(resolveConflict(file, action)))
            onFileCompleted(id, size)
            return
        }
        // obf: C3497
        val (newAction, applyToAll) = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                // obf: C2209 case 3
                listener.showConflictDialog(file.fileName) { action, applyToAll ->
                    continuation.resume(Pair(action, applyToAll))
                }
            }
        }
        if (applyToAll) {
            conflictAction = newAction
        }
        val newTarget = (getDestFolder(file) as FtpPath).resolve(resolveConflict(file, newAction))
        FtpClientHelper.rename(file.source, newTarget)
        onFileCompleted(file.id, file.size)
    }

    /** Copies one item by streaming RETR into STOR/APPE (partial files are resumed). */
    // obf: m5923
    private suspend fun copy(file: ProgressFTPFile) {
        if (file.isDirectory) {
            val path = (destPath as FtpPath).resolve(file.name)
            if (FtpClientHelper.getFileOrNull(path) == null) {
                FtpClientHelper.createDirectory(path)
            }
            onFileCompleted(file.id, file.size)
            return
        }
        val (fileName, offset) = getTarget(file)
        processedBytes = offset
        val source = file.source
        val sourceFile = FtpClientHelper.getFileOrNull(source)
        if (sourceFile == null || !sourceFile.isFile) {
            throw TransferException("File not found: " + source.toString())
        }
        val inputStream = FtpClientHelper.newInputStream(source, offset)
        val destFolder = getDestFolder(file)
        val folder = FtpClientHelper.getFileOrNull(destFolder)
        if (folder == null) {
            FtpClientHelper.createDirectory(destFolder)
        } else if (!folder.isDirectory) {
            throw TransferException("Failed to create directory: " + destFolder.toString())
        }
        val outputStream = FtpClientHelper.newOutputStream((destFolder as FtpPath).resolve(fileName))
        inputStream.use { input ->
            outputStream.use { output ->
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

    /** Starts (or resumes) the copy/move unless it is already running. */
    // obf: m5924
    fun start() {
        val job = job
        if (job == null || !job.isActive) {
            // obf: C0441
            this.job = scope.launch {
                while (currentIndex < files.size) {
                    val file = files[currentIndex]
                    try {
                        when (type) {
                            Type.COPY -> copy(file)
                            Type.MOVE -> move(file)
                        }
                    } catch (e: TransferException) {
                        isSuccessful = false
                        onFileError(false, file.id, e)
                    } catch (e: Exception) {
                        onFileError(true, file.id, e)
                        return@launch
                    }
                    currentIndex++
                }
                if (type == Type.MOVE && isSuccessful) {
                    runCatching { FtpClientHelper.removeDirectory(sourcePath) }
                }
                updateTotalProgress(true)
            }
        }
    }

    /** Applies [action] to the existing target of [file]; returns the file name to write. */
    // obf: m5925
    private fun resolveConflict(file: ProgressFTPFile, action: ConflictAction): String {
        val parentDestPath = getDestFolder(file) as FtpPath
        val fileName = file.fileName
        return when (action) {
            ConflictAction.REPLACE -> {
                FtpClientHelper.deleteFile(parentDestPath.resolve(fileName))
                fileName
            }
            ConflictAction.SKIP -> throw TransferException("File skipped.")
            ConflictAction.KEEP_BOTH -> generateUniqueName(fileName) {
                // obf: C0953 case 0
                FtpClientHelper.getFileOrNull(parentDestPath.resolve(it)) == null
            }
        }
    }

    /** Destination folder of [file] (its sub-directory chain below [destPath]). */
    // obf: m5926
    private fun getDestFolder(file: ProgressFTPFile): Path =
        if (file.subDirs.isEmpty()) destPath else (destPath as FtpPath).resolve(file.subDirs.joinToString("/"))

    /** Target file name and the offset to resume from (asks on name conflicts). */
    // obf: m5927
    private suspend fun getTarget(file: ProgressFTPFile): Pair<String, Long> {
        val target = (destPath as FtpPath).resolve(file.name)
        val ftpFile = FtpClientHelper.getFileOrNull(target)
        val fileName = file.fileName
        if (ftpFile == null) {
            return Pair(fileName, 0L)
        }
        if (!ftpFile.isFile) {
            throw TransferException("Folder already exists with the same name.")
        }
        if (processedBytes > 0) {
            return Pair(fileName, ftpFile.size)
        }
        conflictAction?.let { return Pair(resolveConflict(file, it), 0L) }
        // obf: C2072
        val (action, applyToAll) = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                // obf: C2209 case 2
                listener.showConflictDialog(file.name) { action, applyToAll ->
                    continuation.resume(Pair(action, applyToAll))
                }
            }
        }
        if (applyToAll) {
            conflictAction = action
        }
        return Pair(resolveConflict(file, action), 0L)
    }

    companion object {
        // obf: f11976 (C3573 tag 16)

        /**
         * Moves the folder [sourcePath] into [destParent] as [name] with one rename. When a folder
         * with that name exists, returns its content ([MoveResult.files]) to be moved file by file.
         */
        // obf: C3573.m6177
        suspend fun moveFolder(
            sourcePath: FtpPath,
            destParent: FtpPath,
            name: String,
            listener: TransferListener,
        ): MoveResult =
            runCatching {
                if (FtpClientHelper.getFileOrNull(sourcePath) == null) {
                    return@runCatching MoveResult("Source folder not found.", false)
                }
                val dest = destParent.resolve(name)
                val existing = FtpClientHelper.getFileOrNull(dest)
                if (existing == null) {
                    val moved = runCatching {
                        FtpClientHelper.rename(sourcePath, dest)
                        true
                    }.getOrElse { false }
                    if (moved) {
                        return@runCatching MoveResult(null, true)
                    }
                    throw TransferException("Failed to move folder.")
                }
                if (existing.isDirectory) {
                    return@runCatching MoveResult(false, null, listFilesRecursively(sourcePath))
                }
                var newName = existing.name
                val conflictName = newName
                // obf: C2266
                val (action, _) = withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        // obf: C2209 case 1
                        listener.showConflictDialog(conflictName!!) { action, applyToAll ->
                            continuation.resume(Pair(action, applyToAll))
                        }
                    }
                }
                when (action) {
                    ConflictAction.REPLACE -> FtpClientHelper.deleteFile(dest)
                    ConflictAction.SKIP -> throw TransferException("Folder skipped.")
                    ConflictAction.KEEP_BOTH -> newName = generateUniqueName(newName!!) {
                        // obf: C0953 case 1
                        FtpClientHelper.getFileOrNull(destParent.resolve(it)) == null
                    }
                }
                FtpClientHelper.rename(sourcePath, destParent.resolve(newName!!))
                MoveResult(null, true)
            }.getOrElse { MoveResult(it.message, false) }
    }
}

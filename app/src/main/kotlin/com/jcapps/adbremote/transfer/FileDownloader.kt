package com.jcapps.adbremote.transfer

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import com.jcapps.adbremote.model.ProgressFTPFile
import com.jcapps.adbremote.provider.common.DelegateOutputStream
import com.jcapps.adbremote.provider.ftp.FtpClientHelper
import com.jcapps.adbremote.utils.generateUniqueName
import com.jcapps.adbremote.utils.getFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Downloads FTP files/folders ([ProgressFTPFile]) into a SAF [folder], one after another. */
class FileDownloader(
    // obf: f9357
    private val contentResolver: ContentResolver,
    // obf: f9354
    private val folder: DocumentFile,
    files: List<ProgressFTPFile>,
    listener: TransferListener,
) : FileTransfer(listener, files.sumOf { it.size }, ArrayList(List(files.size) { 0L })) {
    // obf: C2433

    // obf: f9355
    private val files: MutableList<ProgressFTPFile> = files.toMutableList()

    /** Already resolved/created target folders by sub-directory chain. */
    // obf: f9356
    private val folders: ConcurrentHashMap<List<String>, DocumentFile> = ConcurrentHashMap()

    /** Conflict choice the user applied to all following items. */
    // obf: f9358
    private var conflictAction: ConflictAction? = null

    /** Downloads one item (folders are only created; partial files are resumed). */
    // obf: m4673
    private suspend fun download(file: ProgressFTPFile) {
        if (file.isDirectory) {
            getTargetFolder(file.subDirs)
            onFileCompleted(file.id, file.size)
            return
        }
        val document = createTargetFile(getTargetFolder(file.subDirs), file.mimeType, file.fileName)
        val offset = document.getFileSize(contentResolver) ?: 0L
        processedBytes = offset
        val outputStream: OutputStream = runCatching {
            val pfd = contentResolver.openFileDescriptor(document.uri, "rw")
                ?: throw TransferException("Failed to open ParcelFileDescriptor for " + document.uri)
            val fileOutputStream = FileOutputStream(pfd.fileDescriptor)
            if (offset > 0) {
                fileOutputStream.channel.position(offset)
            }
            // obf: C2449
            object : DelegateOutputStream(fileOutputStream) {
                override fun close() {
                    super.close()
                    pfd.close()
                }
            }
        }.getOrElse { throw TransferException(it) }
        val source = file.source
        val ftpFile = FtpClientHelper.getFileOrNull(source)
        if (ftpFile == null || !ftpFile.isFile) {
            throw TransferException("File not found: " + source.toString())
        }
        FtpClientHelper.newInputStream(source, offset).use { input ->
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

    /** The (created) folder for the [subDirs] chain below the download folder. */
    // obf: m4674
    private fun getTargetFolder(subDirs: List<String>): DocumentFile {
        if (folders.containsKey(subDirs)) {
            return folders[subDirs]!!
        }
        var target = folder
        for (name in subDirs) {
            target = target.findFile(name)
                ?: target.createDirectory(name)
                ?: throw TransferException(target.name + " is not a directory.")
            folders[subDirs] = target
        }
        if (target.isDirectory) {
            return target
        }
        throw TransferException(target.name + " is not a directory.")
    }

    /** Creates [fileName] in [folder] (asking on conflicts) or returns it to resume. */
    // obf: m4675
    private suspend fun createTargetFile(folder: DocumentFile, mimeType: String, fileName: String): DocumentFile {
        val existing = folder.findFile(fileName)
            ?: return folder.createFile(mimeType, fileName)
                ?: throw TransferException("Failed to create file: $fileName")
        if (!existing.isFile) {
            throw TransferException("Failed to create file: $fileName")
        }
        if (processedBytes > 0) {
            return existing
        }
        conflictAction?.let { return folder.createFileWithNewName(existing, it, mimeType, fileName) }
        // obf: C2392
        val (action, applyToAll) = withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                // obf: C2209 case 4
                listener.showConflictDialog(fileName) { action, applyToAll ->
                    continuation.resume(Pair(action, applyToAll))
                }
            }
        }
        if (applyToAll) {
            conflictAction = action
        }
        return folder.createFileWithNewName(existing, action, mimeType, fileName)
    }

    // obf: m4676
    private fun DocumentFile.createFileWithNewName(
        existing: DocumentFile,
        action: ConflictAction,
        mimeType: String,
        fileName: String,
    ): DocumentFile =
        when (action) {
            ConflictAction.REPLACE -> {
                if (!existing.delete()) {
                    throw TransferException("Failed to replace file: $fileName")
                }
                createFile(mimeType, fileName) ?: throw TransferException("Failed to replace file: $fileName")
            }
            ConflictAction.SKIP -> throw TransferException("File skipped.")
            ConflictAction.KEEP_BOTH -> {
                // obf: C2042 case 1
                val newName = generateUniqueName(fileName) { name -> findFile(name) == null }
                val created = createFile(mimeType, newName)
                    ?: throw TransferException("Failed to create file: $newName")
                val current = files[currentIndex]
                files[currentIndex] = ProgressFTPFile(
                    current.id, current.name, newName, current.size, current.isDirectory,
                    current.source, current.mimeType, current.subDirs,
                )
                created
            }
        }

    /** Starts (or resumes) the download unless it is already running. */
    // obf: m4677
    fun start() {
        val job = job
        if (job == null || !job.isActive) {
            // obf: C1672
            this.job = scope.launch {
                while (currentIndex < files.size) {
                    val file = files[currentIndex]
                    try {
                        download(file)
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
}

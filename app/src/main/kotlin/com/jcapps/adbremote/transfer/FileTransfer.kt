package com.jcapps.adbremote.transfer

import com.jcapps.adbremote.utils.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * Base of the sequential file transfers (upload / download / FTP copy-move): keeps the processed
 * bytes per item, runs on its own IO scope and reports progress to the [listener].
 * RECON-UNSURE: class name.
 */
abstract class FileTransfer(
    // obf: f14177
    val listener: TransferListener,
    // obf: f14176
    val totalSize: Long,
    // obf: f14180
    val processedBytesList: ArrayList<Long>,
) {
    // obf: AbstractC4056

    /** Index of the item being transferred. */
    // obf: f14178
    var currentIndex: Int = 0

    /** Minimum interval between two progress updates (ms). */
    // obf: f14179
    val progressUpdateInterval: Int = 100

    // obf: f14181
    var job: Job? = null

    // obf: f14182
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Processed bytes of the current item. */
    // obf: m6632 / m6634
    var processedBytes: Long
        get() = processedBytesList[currentIndex]
        set(value) {
            processedBytesList[currentIndex] = value
        }

    /** Stops (pauses) the transfer. */
    // obf: m6633
    fun cancel() {
        job?.cancel()
        job = null
    }

    // obf: m6635
    fun onFileCompleted(id: Int, size: Long) {
        listener.onFileProgress(id, 100, formatFileSize(size))
    }

    /** Marks item [id] as paused/failed; [pause] also offers to resume the whole transfer. */
    // obf: m6636
    suspend fun onFileError(pause: Boolean, id: Int, e: Exception) {
        listener.onFileError(id, e)
        if (pause) {
            // obf: C0561
            withContext(Dispatchers.Main) { listener.onTransferPaused() }
        }
    }

    // obf: m6637
    suspend fun updateTotalProgress(completed: Boolean) {
        val percent = if (completed) 100 else ((processedBytesList.sum() * 100.0) / totalSize).toInt()
        // obf: C1482
        withContext(Dispatchers.Main) { listener.onTotalProgress(percent, completed) }
    }

    // obf: m6638
    suspend fun updateFileProgress(id: Int, size: Long) {
        val processedSize = formatFileSize(processedBytes)
        val percent = if (size == 0L) 100 else ((processedBytes * 100.0) / size).toInt()
        listener.onFileProgress(id, percent, processedSize)
        updateTotalProgress(false)
    }
}

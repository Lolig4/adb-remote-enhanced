package com.jcapps.adbremote.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.database.MatrixCursor
import android.os.Binder
import android.os.Build
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.jcapps.adbremote.provider.common.ChannelProxyFileDescriptorCallback
import com.jcapps.adbremote.utils.MimeTypeTable
import java.io.FileNotFoundException
import java.io.IOException
import java.net.URI
import java8.nio.file.Files
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.StandardOpenOption
import java8.nio.file.attribute.BasicFileAttributes

/**
 * A non-exported, uri-permission-granting ContentProvider that serves remote FTP files to other
 * apps (the "open remotely with" flow) via a proxy ParcelFileDescriptor backed by the FTP
 * provider stack. Original name kept. Authority: com.jcapps.adbremote.providers.file_provider.
 */
class FileProvider : ContentProvider() {
    // obf: FileProvider

    // obf: f14195
    private lateinit var callbackThread: HandlerThread
    // obf: f14196
    private lateinit var callbackHandler: Handler

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)
        if (info.exported) throw SecurityException("Provider must not be exported")
        if (!info.grantUriPermissions) throw SecurityException("Provider must grant uri permissions")
    }

    override fun onCreate(): Boolean {
        callbackThread = HandlerThread("FileProvider.CallbackThread").apply { start() }
        callbackHandler = Handler(callbackThread.looper)
        return true
    }

    // obf: query — returns a MatrixCursor over the requested columns (name/size/data/mime/modified)
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                Binder.getCallingUid() == Process.SYSTEM_UID
            ) {
                COLUMNS_RESTRICTED
            } else {
                COLUMNS
            }
        val path = uri.fileProviderPath
        val resultColumns = ArrayList<String>()
        val values = ArrayList<Any?>()
        for (column in columns) {
            when (column) {
                OpenableColumns.DISPLAY_NAME -> {
                    resultColumns += column
                    values += path.fileName.toString()
                }
                OpenableColumns.SIZE -> {
                    val size = try {
                        Files.size(path)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        null
                    }
                    resultColumns += column
                    values += size
                }
                MediaStore.MediaColumns.DATA -> {
                    val file = try {
                        path.toFile()
                    } catch (e: UnsupportedOperationException) {
                        continue
                    }
                    resultColumns += column
                    values += file.absolutePath
                }
                MediaStore.MediaColumns.MIME_TYPE -> {
                    resultColumns += column
                    values += MimeTypeTable.getMimeTypeFromPath(path.toString())
                }
                DocumentsContract.Document.COLUMN_LAST_MODIFIED -> {
                    val lastModified = try {
                        Files.readAttributes(path, BasicFileAttributes::class.java)
                            .lastModifiedTime().toMillis()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        null
                    }
                    resultColumns += column
                    values += lastModified
                }
            }
        }
        return MatrixCursor(resultColumns.toTypedArray(), 1).apply { addRow(values) }
    }

    // obf: getType — MimeTypeTable.getMimeTypeFromPath of the uri's path
    override fun getType(uri: Uri): String {
        val path = uri.fileProviderPath
        return MimeTypeTable.getMimeTypeFromPath(path.toString())
    }

    // obf: openFile — maps the PFD mode to OpenOptions and opens a proxy fd over the FTP channel
    // RECON-UNSURE: StorageManager.openProxyFileDescriptor is API 26; the artifact calls it without
    // an SDK check although minSdk is 23.
    @RequiresApi(Build.VERSION_CODES.O)
    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val path = uri.fileProviderPath
        val modeBits = ParcelFileDescriptor.parseMode(mode)
        val options = modeBits.toOpenOptions()
        val channel = try {
            withNetworkPermitted {
                try {
                    Files.newByteChannel(path, options)
                } catch (e: UnsupportedOperationException) {
                    throw IOException(e)
                }
            }
        } catch (e: IOException) {
            throw e.toFileNotFoundException()
        }
        try {
            val context = context ?: throw IllegalStateException("Context is null.")
            val storageManager = ContextCompat.getSystemService(context, StorageManager::class.java)!!
            return storageManager.openProxyFileDescriptor(
                modeBits,
                ChannelProxyFileDescriptorCallback(channel).toProxyFileDescriptorCallback(),
                callbackHandler,
            )
        } catch (e: IOException) {
            throw e.toFileNotFoundException()
        }
    }

    private fun Int.toOpenOptions(): Set<OpenOption> =
        LinkedHashSet<OpenOption>().apply {
            if (hasMode(this@toOpenOptions, ParcelFileDescriptor.MODE_APPEND)) {
                throw IllegalArgumentException("mode ${this@toOpenOptions}")
            }
            if (hasMode(this@toOpenOptions, ParcelFileDescriptor.MODE_READ_ONLY) ||
                hasMode(this@toOpenOptions, ParcelFileDescriptor.MODE_READ_WRITE)
            ) {
                this += StandardOpenOption.READ
            }
            if (hasMode(this@toOpenOptions, ParcelFileDescriptor.MODE_WRITE_ONLY) ||
                hasMode(this@toOpenOptions, ParcelFileDescriptor.MODE_READ_WRITE)
            ) {
                this += StandardOpenOption.WRITE
            }
            if (hasMode(this@toOpenOptions, ParcelFileDescriptor.MODE_CREATE)) {
                this += StandardOpenOption.CREATE
            }
            if (hasMode(this@toOpenOptions, ParcelFileDescriptor.MODE_TRUNCATE)) {
                this += StandardOpenOption.TRUNCATE_EXISTING
            }
        }

    private inline fun <T> withNetworkPermitted(block: () -> T): T {
        val oldThreadPolicy = StrictMode.getThreadPolicy()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder(oldThreadPolicy).permitNetwork().build()
        )
        try {
            return block()
        } finally {
            StrictMode.setThreadPolicy(oldThreadPolicy)
        }
    }

    private fun IOException.toFileNotFoundException(): FileNotFoundException =
        this as? FileNotFoundException
            ?: FileNotFoundException(message).apply { initCause(this@toFileNotFoundException) }

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("No external inserts")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("No external deletes")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("No external updates")

    override fun shutdown() {
        callbackThread.quitSafely()
    }

    companion object {
        // obf: f14194
        private val COLUMNS = arrayOf("_display_name", "_size", "_data", "mime_type", "last_modified")
        // obf: f14193
        private val COLUMNS_RESTRICTED = arrayOf("_display_name")

        // obf: m6657
        private fun hasMode(mode: Int, mask: Int): Boolean = (mode and mask) == mask
    }
}

// obf: eb.m1174 — the Path encoded in a content://…file_provider/<encoded URI> Uri
private val Uri.fileProviderPath: Path
    get() = Paths.get(URI.create(Uri.decode(path).substring(1)))


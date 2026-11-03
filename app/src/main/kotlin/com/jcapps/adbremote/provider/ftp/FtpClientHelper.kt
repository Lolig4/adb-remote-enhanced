package com.jcapps.adbremote.provider.ftp

import com.jcapps.adbremote.provider.ftp.FtpMlsdParser.mlsdFiles
import com.jcapps.adbremote.provider.ftp.FtpMlsdParser.mlstFile
import java.io.IOException
import java.time.ZoneOffset
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java8.nio.file.Path
import kotlinx.coroutines.withTimeout
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient

/**
 * Connection pool + command helpers over commons-net for the FTP provider (MaterialFiles
 * provider.ftp.client.Client). Pools idle [FTPClient]s per [FtpAuthority], caches directory
 * listings, and exposes the file operations the provider and the Files screen use.
 */
object FtpClientHelper {
    // obf: C3262 (singleton f11657)

    // obf: static init — MFMT timestamp formatter (built but unused in this build)
    @Suppress("unused")
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT)
        .withChronology(IsoChronology.INSTANCE)
        .withZone(ZoneOffset.UTC)

    // obf: f11656 — password source, set by FilesActivity
    @Volatile
    lateinit var authenticator: FtpAuthenticator

    // obf: f11659 — per-authority idle client pool
    private val clientPool = LinkedHashMap<FtpAuthority, MutableList<FTPClient>>()

    // obf: f11658 — directoryFilesCache
    private val directoryFilesCache: MutableMap<Path, FTPFile> =
        Collections.synchronizedMap(WeakHashMap())

    private val Path.ftpAuthority: FtpAuthority
        get() = (this as FtpPath).getFileSystem().authority

    // obf: m5809 — list a directory (caches each child), returns FTPFile list minus "." / ".."
    @Throws(IOException::class)
    fun listDirectory(path: Path): ArrayList<FTPFile> {
        val authority = path.ftpAuthority
        val client = acquireClient(authority)
        try {
            val files = client.mlsdFiles(path.toString()) ?: client.throwNegativeReplyCodeException()
            return files.mapNotNullTo(ArrayList()) { file ->
                if (file.name == "." || file.name == "..") {
                    null
                } else {
                    directoryFilesCache[(path as FtpPath).resolve(file.name)] = file
                    file
                }
            }
        } finally {
            releaseClient(authority, client)
        }
    }

    // obf: m5810 — open a seekable byte channel (requires REST feature)
    @Throws(IOException::class)
    fun newByteChannel(path: Path, append: Boolean): FtpFileByteChannel {
        val authority = path.ftpAuthority
        val client = acquireClient(authority)
        if (!client.hasFeature("REST")) {
            throw IOException("Missing feature " + "REST")
        }
        return FtpFileByteChannel(client, { releaseClient(authority, client) }, path.toString(), append)
    }

    // obf: m5811 — open an append output stream (APPE)
    @Throws(IOException::class)
    fun newOutputStream(path: Path): FtpOutputStream {
        val authority = path.ftpAuthority
        val client = acquireClient(authority)
        try {
            val outputStream = client.appendFileStream(path.toString())
                ?: client.throwNegativeReplyCodeException()
            return FtpOutputStream(outputStream, authority, client)
        } catch (t: Throwable) {
            releaseClient(authority, client)
            throw t
        }
    }

    // obf: m5812 — stat a file, resolving one symlink level unless noFollowLinks
    @Throws(IOException::class)
    fun getFile(path: Path, noFollowLinks: Boolean): FTPFile {
        val file = getFileFromCache(path, noFollowLinks)
        if (!file.isSymbolicLink || noFollowLinks) {
            return file
        }
        val link = file.link ?: throw IOException("FTPFile.getLink() returned null: $file")
        return getFileFromCache((path as FtpPath).resolve(link), false)
    }

    // obf: m5813 — stat from cache or fetch (MLST)
    @Throws(IOException::class)
    fun getFileFromCache(path: Path, noFollowLinks: Boolean): FTPFile {
        synchronized(directoryFilesCache) {
            val cachedFile = directoryFilesCache[path]
            if (cachedFile != null) {
                if (!cachedFile.isSymbolicLink || !noFollowLinks) {
                    directoryFilesCache.remove(path)
                }
                return cachedFile
            }
            val authority = path.ftpAuthority
            val client = acquireClient(authority)
            try {
                return client.mlstFile(path.toString()) ?: client.throwNegativeReplyCodeException()
            } finally {
                releaseClient(authority, client)
            }
        }
    }

    // obf: m5814 — acquire a live pooled client or create a new one (NOOP keepalive check)
    @Throws(IOException::class)
    fun acquireClient(authority: FtpAuthority): FTPClient {
        while (true) {
            val client = synchronized(clientPool) {
                val clients = clientPool[authority]
                clients?.removeLastOrNull().also {
                    if (clients != null && clients.isEmpty()) {
                        clientPool.remove(authority)
                    }
                }
            } ?: return createClient(authority)
            if (client.isConnected) {
                val isAlive = try {
                    client.sendNoOp()
                } catch (e: IOException) {
                    e.printStackTrace()
                    false
                }
                if (isAlive) {
                    return client
                }
                try {
                    client.logout()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                client.disconnect()
            } else {
                client.disconnect()
            }
        }
    }

    // obf: m5815 — return a client to the pool (or disconnect if dead)
    fun releaseClient(authority: FtpAuthority, client: FTPClient) {
        if (!client.isConnected) {
            client.disconnect()
            return
        }
        synchronized(clientPool) {
            clientPool.getOrPut(authority) { ArrayList() }.add(client)
        }
    }

    // obf: m5816 — delete file (DELE)
    @Throws(IOException::class)
    fun deleteFile(path: Path) {
        val authority = path.ftpAuthority
        val client = acquireClient(authority)
        try {
            if (!FTPReply.isPositiveCompletion(client.dele(path.toString()))) {
                client.throwNegativeReplyCodeException()
            }
        } finally {
            releaseClient(authority, client)
        }
        directoryFilesCache.remove(path)
    }

    // obf: m5817 — create directory (MKD)
    @Throws(IOException::class)
    fun createDirectory(path: Path) {
        val authority = path.ftpAuthority
        val client = acquireClient(authority)
        try {
            if (!FTPReply.isPositiveCompletion(client.mkd(path.toString()))) {
                client.throwNegativeReplyCodeException()
            }
        } finally {
            releaseClient(authority, client)
        }
    }

    // obf: m5818 — rename/move (RNFR + RNTO), same authority only
    @Throws(IOException::class)
    fun rename(source: Path, target: Path) {
        val authority = source.ftpAuthority
        if (authority != target.ftpAuthority) {
            throw IOException("Paths aren't on the same authority")
        }
        val client = acquireClient(authority)
        try {
            if (!client.rename(source.toString(), target.toString())) {
                client.throwNegativeReplyCodeException()
            }
        } finally {
            releaseClient(authority, client)
        }
        directoryFilesCache.remove(source)
        directoryFilesCache.remove(target)
    }

    // obf: m5819 — connect + AUTH + USER/PASS + PBSZ/PROT + TYPE I, honoring passive mode
    @Throws(IOException::class)
    fun createClient(authority: FtpAuthority): FTPClient {
        val password = authenticator.getPassword(authority) ?: ""
        val client = authority.protocol.clientFactory()
        // This is necessary for setting file type to binary.
        client.configure(FTPClientConfig(""))
        client.controlEncoding = authority.encoding
        client.setListHiddenFiles(true)
        client.connect(authority.host, authority.port)
        try {
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                client.throwNegativeReplyCodeException()
            }
            if (!client.login(authority.username, password)) {
                client.throwNegativeReplyCodeException()
            }
            if (authority.mode == FtpMode.PASSIVE) {
                client.enterLocalPassiveMode()
            }
            try {
                if (client is FTPSClient) {
                    client.execPBSZ(0)
                    client.execPROT("P")
                }
                if (!client.setFileType(FTP.BINARY_FILE_TYPE)) {
                    client.throwNegativeReplyCodeException()
                }
            } catch (t: Throwable) {
                try {
                    client.logout()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                client.disconnect()
                throw t
            }
        } catch (t: Throwable) {
            client.disconnect()
            throw t
        }
        return client
    }

    // obf: m5820 — getFile(noFollowLinks = true) returning null on a negative reply
    @Throws(IOException::class)
    fun getFileOrNull(path: Path): FTPFile? =
        try {
            getFile(path, true)
        } catch (e: NegativeReplyCodeException) {
            null
        }

    // obf: m5821 — remove directory (RMD)
    @Throws(IOException::class)
    fun removeDirectory(path: Path) {
        val authority = path.ftpAuthority
        val client = acquireClient(authority)
        try {
            if (!FTPReply.isPositiveCompletion(client.rmd(path.toString()))) {
                client.throwNegativeReplyCodeException()
            }
        } finally {
            releaseClient(authority, client)
        }
        directoryFilesCache.remove(path)
    }

    // obf: m5822 — open an input stream (RETR, optional REST offset)
    @Throws(IOException::class)
    fun newInputStream(path: Path, offset: Long?): FtpInputStream {
        val authority = path.ftpAuthority
        val client = acquireClient(authority)
        try {
            // RECON-UNSURE: the artifact unboxes the offset unconditionally (no null default).
            client.setRestartOffset(offset!!)
            val inputStream = client.retrieveFileStream(path.toString())
                ?: client.throwNegativeReplyCodeException()
            return FtpInputStream(inputStream, authority, client)
        } catch (t: Throwable) {
            releaseClient(authority, client)
            throw t
        }
    }

    // obf: m5823 — create an empty file (STOR of nothing)
    @Throws(IOException::class)
    fun createFile(path: Path) {
        val authority = path.ftpAuthority
        val client = acquireClient(authority)
        try {
            val outputStream = client.storeFileStream(path.toString())
                ?: client.throwNegativeReplyCodeException()
            FtpOutputStream(outputStream, authority, client).close()
        } catch (t: Throwable) {
            releaseClient(authority, client)
            throw t
        }
    }

    // obf: m5824 — suspend connectivity check (3 s NOOP timeout) used by FilesActivity
    suspend fun isReachable(authority: FtpAuthority): Boolean {
        val client = runCatching { createClient(authority) }.getOrNull() ?: return false
        return try {
            withTimeout(3000L) {
                client.noop()
                true
            }
        } catch (e: Exception) {
            false
        } finally {
            releaseClient(authority, client)
        }
    }
}

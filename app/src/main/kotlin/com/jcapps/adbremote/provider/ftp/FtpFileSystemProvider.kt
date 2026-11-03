package com.jcapps.adbremote.provider.ftp

import android.net.Uri
import com.jcapps.adbremote.provider.common.ByteStringUriUtils
import com.jcapps.adbremote.provider.common.OpenOptions
import com.jcapps.adbremote.provider.common.toByteString
import com.jcapps.adbremote.provider.common.toOpenOptions
import java.io.IOException
import java.net.URI
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessMode
import java8.nio.file.CopyOption
import java8.nio.file.DirectoryStream
import java8.nio.file.FileAlreadyExistsException
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.FileSystemAlreadyExistsException
import java8.nio.file.FileSystemException
import java8.nio.file.FileSystemNotFoundException
import java8.nio.file.LinkOption
import java8.nio.file.NoSuchFileException
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import java8.nio.file.StandardOpenOption
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.attribute.FileTime
import java8.nio.file.spi.FileSystemProvider
import org.threeten.bp.DateTimeUtils
import org.threeten.bp.Instant

/**
 * The FTP java.nio FileSystemProvider (MaterialFiles FtpFileSystemProvider). In the artifact the
 * object itself is C1081 (filesystem cache, getScheme/getPath/getFileSystem) while R8 moved
 * newByteChannel/readAttributes as static methods into the merged bag AbstractC4058 (whose
 * checkNotNull message names "provider.ftp.FtpFileSystemProvider.readAttributes").
 *
 * RECON-UNSURE: newFileSystem, newDirectoryStream, createDirectory, delete, copy, move,
 * isSameFile, isHidden, getFileStore, checkAccess, getFileAttributeView, readAttributes(String),
 * setAttribute were stripped by R8 (never called); bodies after MaterialFiles or unsupported.
 */
object FtpFileSystemProvider : FileSystemProvider() {
    // obf: C1081 (instance f5606) + AbstractC4058.m6639/m6641

    // static init
    private val HIDDEN_FILE_NAME_PREFIX = ".".toByteString()

    // obf: f5605
    private val fileSystems = LinkedHashMap<FtpAuthority, FtpFileSystem>()

    // obf: f5607
    private val lock = Any()

    override fun getScheme(): String = FtpProtocol.FTP.scheme

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem {
        uri.requireSameScheme()
        val authority = uri.ftpAuthority
        synchronized(lock) {
            if (fileSystems[authority] != null) {
                throw FileSystemAlreadyExistsException(authority.toString())
            }
            return newFileSystemLocked(authority)
        }
    }

    // obf: m3066
    fun getOrNewFileSystem(authority: FtpAuthority): FtpFileSystem =
        synchronized(lock) { fileSystems[authority] ?: newFileSystemLocked(authority) }

    private fun newFileSystemLocked(authority: FtpAuthority): FtpFileSystem {
        val fileSystem = FtpFileSystem(this, authority)
        fileSystems[authority] = fileSystem
        return fileSystem
    }

    override fun getFileSystem(uri: URI): FileSystem {
        uri.requireSameScheme()
        val authority = uri.ftpAuthority
        return synchronized(lock) { fileSystems[authority] }
            ?: throw FileSystemNotFoundException(authority.toString())
    }

    // obf: m3064 — remove a closed filesystem from the cache (called by FtpFileSystem.close)
    fun removeFileSystem(fileSystem: FtpFileSystem) {
        val authority = fileSystem.authority
        synchronized(lock) { fileSystems.remove(authority) }
    }

    override fun getPath(uri: URI): Path {
        uri.requireSameScheme()
        val authority = uri.ftpAuthority
        val path = uri.rawPath?.let { ByteStringUriUtils.decode(it) }
            ?: throw IllegalArgumentException("URI must have a path")
        return getOrNewFileSystem(authority).getPath(path)
    }

    // obf: m3065
    private fun URI.requireSameScheme() {
        val scheme = scheme
        require(scheme in FtpProtocol.schemes) {
            "URI scheme $scheme must be in ${FtpProtocol.schemes}"
        }
    }

    // obf: m3067
    private val URI.ftpAuthority: FtpAuthority
        get() {
            val protocol = FtpProtocol.fromScheme(scheme)
            val port = if (port != -1) port else protocol.defaultPort
            val username = userInfo.orEmpty()
            val queryUri = rawQuery?.let { ByteStringUriUtils.decode(it) }
                ?.let { Uri.parse(it.toString()) }
            val mode = queryUri?.getQueryParameter(FtpPath.QUERY_PARAMETER_MODE)
                ?.let { mode -> FtpMode.entries.first { it.name.equals(mode, true) } }
                ?: FtpAuthority.DEFAULT_MODE
            val encoding = queryUri?.getQueryParameter(FtpPath.QUERY_PARAMETER_ENCODING)
                ?: FtpAuthority.DEFAULT_ENCODING
            return FtpAuthority(protocol, host, port, username, mode, encoding)
        }

    /**
     * newByteChannel(path, options, attrs): validates the open options, rejects
     * SYNC/DSYNC/DELETE_ON_CLOSE, handles CREATE/CREATE_NEW via [FtpClientHelper], then opens the
     * channel. obf: AbstractC4058.m6639
     */
    @Throws(IOException::class)
    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>,
    ): SeekableByteChannel {
        file as? FtpPath ?: throw ProviderMismatchException(file.toString())
        val openOptions = options.toOpenOptions()
        openOptions.checkForFtp()
        if (openOptions.write || openOptions.create || openOptions.createNew ||
            openOptions.noFollowLinks
        ) {
            val fileFile = try {
                FtpClientHelper.getFileOrNull(file)
            } catch (e: IOException) {
                throw e.toFileSystemExceptionForFtp(file.toString())
            }
            if (openOptions.createNew && fileFile != null) {
                throw FileAlreadyExistsException(file.toString())
            }
            if (openOptions.noFollowLinks && fileFile != null && fileFile.isSymbolicLink) {
                throw FileSystemException(
                    file.toString(), null, "File is a symbolic link: $fileFile"
                )
            }
            if (fileFile == null) {
                if (!(openOptions.create || openOptions.createNew)) {
                    throw NoSuchFileException(file.toString())
                }
                try {
                    FtpClientHelper.createFile(file)
                } catch (e: IOException) {
                    throw e.toFileSystemExceptionForFtp(file.toString())
                }
            }
        }
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        try {
            return FtpClientHelper.newByteChannel(file, openOptions.append)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(file.toString())
        }
    }

    private fun OpenOptions.checkForFtp() {
        if (deleteOnClose) {
            throw UnsupportedOperationException(StandardOpenOption.DELETE_ON_CLOSE.toString())
        }
        if (sync) {
            throw UnsupportedOperationException(StandardOpenOption.SYNC.toString())
        }
        if (dsync) {
            throw UnsupportedOperationException(StandardOpenOption.DSYNC.toString())
        }
        if (write && !truncateExisting) {
            throw UnsupportedOperationException("Missing ${StandardOpenOption.TRUNCATE_EXISTING}")
        }
    }

    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>,
    ): DirectoryStream<Path> {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        directory as? FtpPath ?: throw ProviderMismatchException(directory.toString())
        if (attributes.isNotEmpty()) {
            throw UnsupportedOperationException(attributes.contentToString())
        }
        try {
            FtpClientHelper.createDirectory(directory)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(directory.toString())
        }
    }

    @Throws(IOException::class)
    override fun delete(path: Path) {
        path as? FtpPath ?: throw ProviderMismatchException(path.toString())
        try {
            val file = FtpClientHelper.getFile(path, true)
            if (file.isDirectory) {
                FtpClientHelper.removeDirectory(path)
            } else {
                FtpClientHelper.deleteFile(path)
            }
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(path.toString())
        }
    }

    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        throw UnsupportedOperationException()
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        throw UnsupportedOperationException()
    }

    override fun isSameFile(path: Path, path2: Path): Boolean {
        path as? FtpPath ?: throw ProviderMismatchException(path.toString())
        return path == path2
    }

    override fun isHidden(path: Path): Boolean {
        path as? FtpPath ?: throw ProviderMismatchException(path.toString())
        val fileName = path.getFileName() ?: return false
        val bytes = fileName.toByteString().bytes
        val prefix = HIDDEN_FILE_NAME_PREFIX.bytes
        return bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it] }
    }

    override fun getFileStore(path: Path): FileStore {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        path as? FtpPath ?: throw ProviderMismatchException(path.toString())
        try {
            FtpClientHelper.getFile(path, false)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(path.toString())
        }
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption,
    ): V? = null

    /**
     * readAttributes(path, type, options): fetches the FTPFile via [FtpClientHelper] and maps it to
     * [FtpFileAttributes]. obf: AbstractC4058.m6641
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(IOException::class)
    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption,
    ): A {
        if (!type.isAssignableFrom(BasicFileAttributes::class.java)) {
            throw UnsupportedOperationException(type.toString())
        }
        path as? FtpPath ?: throw ProviderMismatchException(path.toString())
        var noFollowLinks = false
        for (option in options) {
            when (option) {
                LinkOption.NOFOLLOW_LINKS -> noFollowLinks = true
                else -> throw UnsupportedOperationException(option.toString())
            }
        }
        val file = try {
            FtpClientHelper.getFile(path, noFollowLinks)
        } catch (e: IOException) {
            throw e.toFileSystemExceptionForFtp(path.toString())
        }
        // RECON-UNSURE: the artifact used desugared java.time (Calendar.toInstant()); retrofile
        // 1.1.1 FileTime takes an org.threeten.bp.Instant.
        val lastModifiedTime = FileTime.from(
            file.timestamp?.let { DateTimeUtils.toInstant(it) } ?: Instant.EPOCH
        )
        val fileType = when {
            file.isDirectory -> FtpFileType.DIRECTORY
            file.isFile -> FtpFileType.REGULAR_FILE
            file.isSymbolicLink -> FtpFileType.SYMBOLIC_LINK
            else -> FtpFileType.OTHER
        }
        val size = file.size.let { if (it != -1L) it else 0 }
        return FtpFileAttributes(
            lastModifiedTime, lastModifiedTime, lastModifiedTime, fileType, size, path
        ) as A
    }

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption,
    ): Map<String, Any> {
        throw UnsupportedOperationException()
    }

    override fun setAttribute(
        path: Path,
        attribute: String,
        value: Any,
        vararg options: LinkOption,
    ) {
        throw UnsupportedOperationException()
    }
}

package com.jcapps.adbremote.provider.common

import java.net.URI
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessMode
import java8.nio.file.CopyOption
import java8.nio.file.DirectoryStream
import java8.nio.file.FileStore
import java8.nio.file.FileSystem
import java8.nio.file.LinkOption
import java8.nio.file.OpenOption
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import java8.nio.file.attribute.FileAttribute
import java8.nio.file.attribute.FileAttributeView
import java8.nio.file.spi.FileSystemProvider

/**
 * A [FileSystemProvider] that reports its own [scheme] and forwards everything else to
 * [fileSystemProvider] (MaterialFiles DelegateSchemeFileSystemProvider). Used for ftps / ftpes.
 * Only getScheme/getPath/getFileSystem survived R8; the other forwards are the obvious delegation.
 */
open class DelegateSchemeFileSystemProvider(
    // obf: f6958
    private val scheme: String,
    // obf: f6957
    private val fileSystemProvider: FileSystemProvider,
) : FileSystemProvider() {
    // obf: C1577

    override fun getScheme(): String = scheme

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem =
        fileSystemProvider.newFileSystem(uri, env)

    override fun getFileSystem(uri: URI): FileSystem = fileSystemProvider.getFileSystem(uri)

    override fun getPath(uri: URI): Path = fileSystemProvider.getPath(uri)

    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>,
    ): SeekableByteChannel = fileSystemProvider.newByteChannel(file, options, *attributes)

    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>,
    ): DirectoryStream<Path> = fileSystemProvider.newDirectoryStream(directory, filter)

    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>) {
        fileSystemProvider.createDirectory(directory, *attributes)
    }

    override fun delete(path: Path) {
        fileSystemProvider.delete(path)
    }

    override fun copy(source: Path, target: Path, vararg options: CopyOption) {
        fileSystemProvider.copy(source, target, *options)
    }

    override fun move(source: Path, target: Path, vararg options: CopyOption) {
        fileSystemProvider.move(source, target, *options)
    }

    override fun isSameFile(path: Path, path2: Path): Boolean = fileSystemProvider.isSameFile(path, path2)

    override fun isHidden(path: Path): Boolean = fileSystemProvider.isHidden(path)

    override fun getFileStore(path: Path): FileStore = fileSystemProvider.getFileStore(path)

    override fun checkAccess(path: Path, vararg modes: AccessMode) {
        fileSystemProvider.checkAccess(path, *modes)
    }

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption,
    ): V? = fileSystemProvider.getFileAttributeView(path, type, *options)

    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption,
    ): A = fileSystemProvider.readAttributes(path, type, *options)

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption,
    ): Map<String, Any> = fileSystemProvider.readAttributes(path, attributes, *options)

    override fun setAttribute(path: Path, attribute: String, value: Any, vararg options: LinkOption) {
        fileSystemProvider.setAttribute(path, attribute, value, *options)
    }
}

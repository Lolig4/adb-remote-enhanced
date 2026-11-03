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
 * Placeholder default provider for the mandatory "file" scheme: the app never uses local paths
 * through java8.nio, every operation is `TODO()` (C1070 = kotlin.NotImplementedError).
 * RECON-UNSURE: class name; only getScheme/getPath/getFileSystem survived R8.
 */
object StubDefaultFileSystemProvider : FileSystemProvider() {
    // obf: C0828 (instance f4916)

    override fun getScheme(): String = "file"

    override fun newFileSystem(uri: URI, env: Map<String, *>): FileSystem = TODO()

    override fun getFileSystem(uri: URI): FileSystem = TODO()

    override fun getPath(uri: URI): Path = TODO()

    override fun newByteChannel(
        file: Path,
        options: Set<OpenOption>,
        vararg attributes: FileAttribute<*>,
    ): SeekableByteChannel = TODO()

    override fun newDirectoryStream(
        directory: Path,
        filter: DirectoryStream.Filter<in Path>,
    ): DirectoryStream<Path> = TODO()

    override fun createDirectory(directory: Path, vararg attributes: FileAttribute<*>): Unit = TODO()

    override fun delete(path: Path): Unit = TODO()

    override fun copy(source: Path, target: Path, vararg options: CopyOption): Unit = TODO()

    override fun move(source: Path, target: Path, vararg options: CopyOption): Unit = TODO()

    override fun isSameFile(path: Path, path2: Path): Boolean = TODO()

    override fun isHidden(path: Path): Boolean = TODO()

    override fun getFileStore(path: Path): FileStore = TODO()

    override fun checkAccess(path: Path, vararg modes: AccessMode): Unit = TODO()

    override fun <V : FileAttributeView> getFileAttributeView(
        path: Path,
        type: Class<V>,
        vararg options: LinkOption,
    ): V? = TODO()

    override fun <A : BasicFileAttributes> readAttributes(
        path: Path,
        type: Class<A>,
        vararg options: LinkOption,
    ): A = TODO()

    override fun readAttributes(
        path: Path,
        attributes: String,
        vararg options: LinkOption,
    ): Map<String, Any> = TODO()

    override fun setAttribute(path: Path, attribute: String, value: Any, vararg options: LinkOption): Unit = TODO()
}

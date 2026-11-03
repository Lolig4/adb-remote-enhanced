package com.jcapps.adbremote.provider.common

import java.io.File
import java8.nio.file.Path
import java8.nio.file.WatchEvent
import java8.nio.file.WatchKey
import java8.nio.file.WatchService

/**
 * Abstract java.nio Path base (MaterialFiles provider.common.AbstractPath). The artifact kept only
 * [iterator] (which throws); the remaining defaults below were stripped by R8 as unused and are
 * reconstructed after the MaterialFiles reference.
 *
 * RECON-UNSURE: every member except [iterator] (stripped by R8).
 */
abstract class AbstractPath<T : AbstractPath<T>> : Path {
    // obf: AbstractC1581

    // obf: iterator
    final override fun iterator(): MutableIterator<Path> {
        throw UnsupportedOperationException()
    }

    override fun startsWith(other: String): Boolean = startsWith(getFileSystem().getPath(other))

    override fun endsWith(other: String): Boolean = endsWith(getFileSystem().getPath(other))

    override fun resolveSibling(other: Path): Path {
        val parent = getParent() ?: return other
        return parent.resolve(other)
    }

    override fun resolveSibling(other: String): Path = resolveSibling(getFileSystem().getPath(other))

    override fun toFile(): File {
        throw UnsupportedOperationException()
    }

    override fun register(
        watcher: WatchService,
        events: Array<out WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier,
    ): WatchKey {
        throw UnsupportedOperationException()
    }

    override fun register(watcher: WatchService, vararg events: WatchEvent.Kind<*>): WatchKey =
        register(watcher, events)
}

package com.jcapps.adbremote.provider.common

import android.os.Parcel
import android.os.Parcelable
import com.jcapps.adbremote.compat.readBooleanCompat
import com.jcapps.adbremote.compat.readParcelableListCompat
import com.jcapps.adbremote.compat.writeBooleanCompat
import com.jcapps.adbremote.compat.writeParcelableListCompat
import java.net.URI
import java8.nio.file.InvalidPathException
import java8.nio.file.Path
import java8.nio.file.ProviderMismatchException
import java8.nio.file.spi.FileSystemProvider

/**
 * A java.nio Path implemented as a list of [ByteString] segments (MaterialFiles style). Parcelable;
 * [com.jcapps.adbremote.provider.ftp.FtpPath] is the only concrete subtype.
 *
 * Members with an `// obf:` marker are ported from the artifact. The other [Path] operations were
 * stripped by R8 (never called) and are reconstructed after the MaterialFiles reference.
 * RECON-UNSURE: getFileName/getParent/getName/subpath/startsWith/endsWith/normalize/relativize/
 * toAbsolutePath bodies (stripped by R8).
 */
abstract class ByteStringListPath<T : ByteStringListPath<T>> : AbstractPath<T>, Parcelable {
    // obf: AbstractC2640

    // obf: f9904 — path separator byte ('/')
    val separator: Byte

    // obf: f9907 — absolute flag
    private val isAbsolute: Boolean

    // obf: f9906 — segments (List<ByteString>)
    val segments: List<ByteString>

    // obf: f9905 — cached toByteString result
    @Volatile
    private var byteStringCache: ByteString? = null

    // obf: AbstractC2640(C3091) — the separator was constant-folded to '/' by R8
    constructor(separator: Byte, path: ByteString) {
        require(separator != '\u0000'.code.toByte()) { "Separator cannot be the nul character" }
        this.separator = separator
        val bytes = path.bytes
        if (bytes.indexOf('\u0000'.code.toByte()) != -1) {
            throw InvalidPathException(path.toString(), "Path cannot contain nul characters")
        }
        isAbsolute = isPathAbsolute(path)
        val segments = ArrayList<ByteString>()
        if (bytes.isEmpty()) {
            segments += ByteString.EMPTY
        } else {
            var fromIndex = 0
            val length = bytes.size
            while (true) {
                while (fromIndex < length && bytes[fromIndex] == separator) {
                    ++fromIndex
                }
                if (fromIndex == length) {
                    break
                }
                var toIndex = fromIndex + 1
                while (toIndex < length && bytes[toIndex] != separator) {
                    ++toIndex
                }
                if (fromIndex < 0 || toIndex > bytes.size || fromIndex > toIndex) {
                    throw IndexOutOfBoundsException()
                }
                segments += if (fromIndex == 0 && toIndex == bytes.size) {
                    path
                } else {
                    ByteString(bytes.copyOfRange(fromIndex, toIndex))
                }
                fromIndex = toIndex
            }
        }
        this.segments = segments
        requireSegmentsNotEmpty()
    }

    // obf: AbstractC2640(boolean, List)
    constructor(separator: Byte, isAbsolute: Boolean, segments: List<ByteString>) {
        this.separator = separator
        this.isAbsolute = isAbsolute
        this.segments = segments
        requireSegmentsNotEmpty()
    }

    // obf: AbstractC2640(Parcel)
    constructor(source: Parcel) {
        separator = source.readByte()
        isAbsolute = source.readBooleanCompat()
        segments = source.readParcelableListCompat(ArrayList<ByteString>())
    }

    private fun requireSegmentsNotEmpty() {
        check(isAbsolute || segments.isNotEmpty()) { "Non-absolute path must not be empty" }
    }

    /** Whether [path] is absolute for this path type (inlined as `path[0] == '/'`). */
    protected abstract fun isPathAbsolute(path: ByteString): Boolean

    /** Create a path of the same type from segments (inlined as `FtpPath(fileSystem, …)`). */
    protected abstract fun createPath(absolute: Boolean, segments: List<ByteString>): T

    /** Create a path of the same type from a byte string. */
    protected abstract fun createPath(path: ByteString): T

    /** The directory relative paths are resolved against (FtpFileSystem.rootDirectory). */
    protected abstract val defaultDirectory: T

    /** The URI scheme used by [toUri]. */
    protected abstract val uriScheme: String

    /** userInfo / host / port of the URI authority used by [toUri]. */
    protected abstract val uriUserInfo: String?
    protected abstract val uriHost: String
    protected abstract val uriPort: Int?

    /** Optional URI query used by [toUri]. */
    protected open val uriQuery: ByteString?
        get() = null

    override fun isAbsolute(): Boolean = isAbsolute

    // obf: isEmpty
    fun isEmpty(): Boolean {
        if (isAbsolute) return false
        if (segments.size != 1) return false
        return segments[0] == ByteString.EMPTY
    }

    // RECON-UNSURE: stripped by R8; the inlined copy in FileProvider.query always builds a new path
    override fun getFileName(): T? {
        if (segments.isEmpty()) return null
        return createPath(false, listOf(segments.last()))
    }

    @Suppress("UNCHECKED_CAST")
    override fun getParent(): T? =
        when (segments.size) {
            0 -> null
            1 -> if (isAbsolute) getRoot() as T? else null
            else -> createPath(isAbsolute, segments.subList(0, segments.size - 1))
        }

    override fun getNameCount(): Int = segments.size

    override fun getName(index: Int): T {
        require(index >= 0 && index < segments.size)
        return createPath(false, listOf(segments[index]))
    }

    override fun subpath(beginIndex: Int, endIndex: Int): T {
        require(beginIndex >= 0 && beginIndex < endIndex && endIndex <= segments.size)
        return createPath(false, segments.subList(beginIndex, endIndex))
    }

    override fun startsWith(other: Path): Boolean {
        val otherPath = other.toByteStringListPath()
        if (otherPath === this) return true
        if (otherPath.isAbsolute() != isAbsolute) return false
        val otherSegments = otherPath.segments
        if (otherSegments.size > segments.size) return false
        return otherSegments.indices.all { segments[it] == otherSegments[it] }
    }

    override fun endsWith(other: Path): Boolean {
        val otherPath = other.toByteStringListPath()
        if (otherPath === this) return true
        if (otherPath.isAbsolute() && !isAbsolute) return false
        val otherSegments = otherPath.segments
        val offset = segments.size - otherSegments.size
        if (offset < 0) return false
        if (otherPath.isAbsolute() && offset != 0) return false
        return otherSegments.indices.all { segments[offset + it] == otherSegments[it] }
    }

    override fun normalize(): T {
        val normalizedSegments = ArrayList<ByteString>()
        for (segment in segments) {
            when (segment) {
                BYTE_STRING_DOT -> {}
                BYTE_STRING_DOT_DOT ->
                    if (normalizedSegments.isEmpty()) {
                        if (!isAbsolute) {
                            normalizedSegments += segment
                        }
                    } else {
                        if (normalizedSegments.last() == BYTE_STRING_DOT_DOT) {
                            normalizedSegments += segment
                        } else {
                            normalizedSegments.removeAt(normalizedSegments.lastIndex)
                        }
                    }
                else -> normalizedSegments += segment
            }
        }
        if (!isAbsolute && normalizedSegments.isEmpty()) {
            return createPath(ByteString.EMPTY)
        }
        return createPath(isAbsolute, normalizedSegments)
    }

    // obf: m5046 — resolve(other)
    @Suppress("UNCHECKED_CAST")
    override fun resolve(other: Path): T {
        val otherPath = other.toByteStringListPath()
        require(otherPath.getFileSystem() == getFileSystem()) {
            "The other path must have the same file system as this path"
        }
        if (otherPath.isAbsolute()) return otherPath
        if (otherPath.isEmpty()) return this as T
        if (isEmpty()) return otherPath
        return createPath(isAbsolute, segments + otherPath.segments)
    }

    override fun relativize(other: Path): T {
        val otherPath = other.toByteStringListPath()
        require(otherPath.isAbsolute() == isAbsolute) {
            "The other path must be as absolute as this path"
        }
        if (isEmpty()) return otherPath
        if (this == otherPath) return createPath(ByteString.EMPTY)
        val otherSegments = otherPath.segments
        val smallerSize = minOf(segments.size, otherSegments.size)
        var commonSize = 0
        while (commonSize < smallerSize && segments[commonSize] == otherSegments[commonSize]) {
            ++commonSize
        }
        val relativeSegments = ArrayList<ByteString>()
        for (i in commonSize until segments.size) {
            relativeSegments += BYTE_STRING_DOT_DOT
        }
        for (i in commonSize until otherSegments.size) {
            relativeSegments += otherSegments[i]
        }
        return createPath(false, relativeSegments)
    }

    // inlined into toUri in the artifact
    @Suppress("UNCHECKED_CAST")
    override fun toAbsolutePath(): T {
        if (isAbsolute) return this as T
        return defaultDirectory.resolve(this)
    }

    // obf: m5047 — toByteString()
    fun toByteString(): ByteString {
        byteStringCache?.let { return it }
        val builder = ByteStringBuilder()
        if (isAbsolute && getRoot() != null) {
            builder.append(separator)
        }
        var first = true
        for (segment in segments) {
            if (first) {
                first = false
            } else {
                builder.append(separator)
            }
            builder.append(segment)
        }
        return builder.toByteString().also { byteStringCache = it }
    }

    // obf: m5048 — toUri()
    override fun toUri(): URI {
        val path = toAbsolutePath().toByteString()
        return ByteStringUriUtils.createUri(uriScheme, uriUserInfo, uriHost, uriPort, path, uriQuery)
    }

    override fun toString(): String = toByteString().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ByteStringListPath<*>
        return separator == other.separator && segments == other.segments &&
            isAbsolute == other.isAbsolute() && getFileSystem() == other.getFileSystem()
    }

    override fun hashCode(): Int =
        arrayOf<Any>(separator, segments, isAbsolute, getFileSystem()).contentDeepHashCode()

    override fun compareTo(other: Path): Int {
        val otherPath = javaClass.cast(other) as ByteStringListPath<*>
        if (getFileSystem().provider() != otherPath.getFileSystem().provider()) {
            throw ClassCastException(otherPath.toString())
        }
        return toByteString().compareTo(otherPath.toByteString())
    }

    @Suppress("UNCHECKED_CAST")
    private fun Path.toByteStringListPath(): T {
        if (this@ByteStringListPath.javaClass != javaClass ||
            this@ByteStringListPath.provider != provider
        ) {
            throw ProviderMismatchException(toString())
        }
        return this as T
    }

    private val Path.provider: FileSystemProvider
        get() = getFileSystem().provider()

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeByte(separator)
        dest.writeBooleanCompat(isAbsolute)
        dest.writeParcelableListCompat(segments, flags)
    }

    companion object {
        // static init interned "." and ".."
        private val BYTE_STRING_DOT = ".".toByteString()
        private val BYTE_STRING_DOT_DOT = "..".toByteString()
    }
}

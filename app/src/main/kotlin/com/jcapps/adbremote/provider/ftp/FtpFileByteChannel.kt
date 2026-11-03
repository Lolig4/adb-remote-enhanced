package com.jcapps.adbremote.provider.ftp

import com.jcapps.adbremote.provider.common.ForceableChannel
import com.jcapps.adbremote.utils.asFuture
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonReadableChannelException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java8.nio.channels.SeekableByteChannel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.apache.commons.net.ftp.FTPClient

/**
 * A seekable byte channel over an FTP file using REST/RETR/STOR/APPE/SIZE (MaterialFiles
 * FileByteChannel), used by [com.jcapps.adbremote.provider.FileProvider] via the provider.
 * Reads are served from a 3 MiB read-ahead buffer filled asynchronously on Dispatchers.IO.
 *
 * RECON-UNSURE: [position] (getter) and [truncate] were stripped by R8; bodies after MaterialFiles.
 */
class FtpFileByteChannel(
    // obf: f8571
    private val client: FTPClient,
    // obf: f8574 — returns the client to the pool (C3629 case 4)
    private val releaseClient: (FTPClient) -> Unit,
    // obf: f8575
    private val path: String,
    // obf: f8568
    private val isAppend: Boolean,
) : ForceableChannel, SeekableByteChannel {
    // obf: C2132

    // obf: f8573
    private var position = 0L

    // obf: f8570
    private val readBuffer = ReadBuffer()

    // obf: f8569
    private val ioLock = Any()

    // obf: f8577
    private val clientLock = Any()

    // obf: f8572
    private var isOpen = true

    // obf: f8576
    private val closeLock = Any()

    @Throws(IOException::class)
    override fun read(destination: ByteBuffer): Int {
        ensureOpen()
        if (isAppend) {
            throw NonReadableChannelException()
        }
        if (destination.remaining() == 0) {
            return 0
        }
        return synchronized(ioLock) {
            readBuffer.read(destination).also {
                if (it != -1) {
                    position += it
                }
            }
        }
    }

    @Throws(IOException::class)
    override fun write(source: ByteBuffer): Int {
        ensureOpen()
        val remaining = source.remaining()
        if (remaining == 0) {
            return 0
        }
        synchronized(ioLock) {
            if (isAppend) {
                appendFile(source)
                position = getSize()
            } else {
                storeFile(source, position)
                position += remaining - source.remaining()
            }
        }
        return remaining
    }

    @Throws(IOException::class)
    override fun position(): Long {
        ensureOpen()
        synchronized(ioLock) {
            return position
        }
    }

    // obf: m4312
    @Throws(IOException::class)
    override fun position(newPosition: Long): SeekableByteChannel {
        ensureOpen()
        if (isAppend) {
            // Ignored.
            return this
        }
        synchronized(ioLock) {
            readBuffer.reposition(position, newPosition)
            position = newPosition
        }
        return this
    }

    @Throws(IOException::class)
    override fun size(): Long {
        ensureOpen()
        return getSize()
    }

    @Throws(IOException::class)
    override fun truncate(size: Long): SeekableByteChannel {
        ensureOpen()
        require(size >= 0)
        synchronized(ioLock) {
            val currentSize = getSize()
            if (size >= currentSize) {
                return this
            }
            storeFile(ByteBuffer.allocate(0), size)
            position = position.coerceAtMost(size)
        }
        return this
    }

    @Throws(IOException::class)
    override fun force(metaData: Boolean) {
        ensureOpen()
        synchronized(ioLock) {
            // Unsupported.
        }
    }

    // obf: m4309 — STOR at an offset (REST)
    @Throws(IOException::class)
    private fun storeFile(source: ByteBuffer, offset: Long) {
        synchronized(clientLock) {
            client.setRestartOffset(offset)
            ByteBufferInputStream(source).use {
                if (!client.storeFile(path, it)) {
                    client.throwNegativeReplyCodeException()
                }
            }
        }
    }

    // obf: m4310 — APPE
    @Throws(IOException::class)
    private fun appendFile(source: ByteBuffer) {
        synchronized(clientLock) {
            ByteBufferInputStream(source).use {
                if (!client.appendFile(path, it)) {
                    client.throwNegativeReplyCodeException()
                }
            }
        }
    }

    // obf: m4311 — SIZE
    @Throws(IOException::class)
    private fun getSize(): Long {
        val sizeString = synchronized(clientLock) {
            client.getSize(path) ?: client.throwNegativeReplyCodeException()
        }
        return sizeString.toLongOrNull() ?: throw IOException("Invalid size $sizeString")
    }

    // obf: m4308
    @Throws(ClosedChannelException::class)
    private fun ensureOpen() {
        synchronized(closeLock) {
            if (!isOpen) {
                throw ClosedChannelException()
            }
        }
    }

    override fun isOpen(): Boolean = synchronized(closeLock) { isOpen }

    @Throws(IOException::class)
    override fun close() {
        synchronized(closeLock) {
            if (!isOpen) {
                return
            }
            isOpen = false
            synchronized(ioLock) {
                try {
                    readBuffer.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                // obf: m4313
                synchronized(clientLock) { releaseClient(client) }
            }
        }
    }

    /** Read-ahead buffer (obf C3922). */
    private inner class ReadBuffer : Closeable {
        // obf: f13768
        private val buffer = ByteBuffer.allocate(BUFFER_SIZE).apply { limit(0) }

        // obf: f13772
        private var bufferedPosition = 0L

        // obf: f13770
        private var pendingRead: Future<ByteBuffer>? = null

        // obf: f13769
        private val pendingReadLock = Any()

        @Throws(IOException::class)
        fun read(destination: ByteBuffer): Int {
            if (!buffer.hasRemaining()) {
                val future = synchronized(pendingReadLock) {
                    pendingRead?.also { pendingRead = null }
                } ?: readIntoBufferAsync()
                try {
                    val newBuffer = future.get()
                    buffer.clear()
                    buffer.put(newBuffer)
                    buffer.flip()
                    if (buffer.hasRemaining()) {
                        bufferedPosition += buffer.remaining()
                        synchronized(pendingReadLock) {
                            pendingRead = readIntoBufferAsync()
                        }
                    }
                    if (!buffer.hasRemaining()) {
                        return -1
                    }
                } catch (e: InterruptedException) {
                    throw InterruptedIOException().apply { initCause(e) }
                } catch (e: CancellationException) {
                    throw InterruptedIOException().apply { initCause(e) }
                } catch (e: ExecutionException) {
                    val exception = e.cause ?: e
                    throw exception as? IOException ?: IOException(exception)
                }
            }
            val length = destination.remaining().coerceAtMost(buffer.remaining())
            val bufferLimit = buffer.limit()
            buffer.limit(buffer.position() + length)
            destination.put(buffer)
            buffer.limit(bufferLimit)
            return length
        }

        // obf: m6532
        @OptIn(DelicateCoroutinesApi::class)
        private fun readIntoBufferAsync(): Future<ByteBuffer> {
            val position = bufferedPosition
            return GlobalScope.async(Dispatchers.IO) {
                withTimeout(READ_TIMEOUT_MILLIS) {
                    runInterruptible { retrieveFile(position) }
                }
            }.asFuture()
        }

        fun reposition(oldPosition: Long, newPosition: Long) {
            if (newPosition == oldPosition) {
                return
            }
            val newBufferPosition = buffer.position() + (newPosition - oldPosition)
            if (newBufferPosition !in 0..buffer.limit()) {
                cancelPendingRead()
                buffer.limit(0)
                bufferedPosition = newPosition
            } else {
                buffer.position(newBufferPosition.toInt())
            }
        }

        // obf: m6531
        private fun cancelPendingRead() {
            synchronized(pendingReadLock) {
                pendingRead?.let {
                    it.cancel(true)
                    try {
                        it.get()
                    } catch (e: Exception) {
                        // Ignored.
                    }
                    pendingRead = null
                }
            }
        }

        override fun close() {
            cancelPendingRead()
        }
    }

    // obf: lambda in C3395 — RETR at an offset into a fresh buffer
    @Throws(IOException::class)
    private fun retrieveFile(offset: Long): ByteBuffer {
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        synchronized(clientLock) {
            client.setRestartOffset(offset)
            val inputStream = client.retrieveFileStream(path)
            try {
                if (inputStream == null) {
                    client.throwNegativeReplyCodeException()
                }
                inputStream.use {
                    val array = buffer.array()
                    val arrayOffset = buffer.arrayOffset()
                    var length = 0
                    while (length < BUFFER_SIZE) {
                        val readSize = it.read(array, arrayOffset + length, BUFFER_SIZE - length)
                        if (readSize == -1) {
                            break
                        }
                        length += readSize
                    }
                    buffer.limit(length)
                }
            } finally {
                client.completePendingCommand()
            }
        }
        return buffer
    }

    companion object {
        private const val BUFFER_SIZE = 3 * 1024 * 1024
        private const val READ_TIMEOUT_MILLIS = 15_000L
    }
}

/** InputStream over a ByteBuffer's remaining bytes (MaterialFiles ByteBufferInputStream; obf C0638 case 1). */
private class ByteBufferInputStream(
    // obf: f4412
    private var buffer: ByteBuffer?,
) : InputStream() {
    // obf: m2435
    @Throws(IOException::class)
    private fun ensureOpen(): ByteBuffer = buffer ?: throw IOException("Stream closed")

    override fun read(): Int {
        val buffer = ensureOpen()
        return if (buffer.hasRemaining()) buffer.get().toInt() and 0xFF else -1
    }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
        val buffer = ensureOpen()
        if (length == 0) {
            return 0
        }
        val remaining = buffer.remaining()
        if (remaining == 0) {
            return -1
        }
        val readLength = length.coerceAtMost(remaining)
        buffer.get(bytes, offset, readLength)
        return readLength
    }

    override fun skip(n: Long): Long {
        val buffer = ensureOpen()
        if (n <= 0) {
            return 0
        }
        val skipLength = n.toInt().coerceAtMost(buffer.remaining())
        buffer.position(buffer.position() + skipLength)
        return skipLength.toLong()
    }

    override fun available(): Int = ensureOpen().remaining()

    override fun markSupported(): Boolean = true

    override fun mark(readlimit: Int) {
        ensureOpen().mark()
    }

    override fun reset() {
        ensureOpen().reset()
    }

    override fun close() {
        buffer = null
    }
}

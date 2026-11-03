package com.jcapps.adbremote.provider.common

import android.os.ProxyFileDescriptorCallback
import android.system.ErrnoException
import android.system.OsConstants
import androidx.annotation.RequiresApi
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.AccessDeniedException
import java8.nio.file.NoSuchFileException

/**
 * Serves a [SeekableByteChannel] through a proxy file descriptor (MaterialFiles
 * ChannelProxyFileDescriptorCallback); used by [com.jcapps.adbremote.provider.FileProvider].
 */
class ChannelProxyFileDescriptorCallback(
    // obf: f4334
    private val channel: SeekableByteChannel,
) {
    // obf: C0616

    // obf: f4333
    private var offset = 0L

    // obf: f4335
    private var isReleased = false

    @Throws(ErrnoException::class)
    fun onGetSize(): Long {
        ensureNotReleased()
        return try {
            channel.size()
        } catch (e: IOException) {
            throw e.toErrnoException()
        }
    }

    @Throws(ErrnoException::class)
    fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        ensureNotReleased()
        if (this.offset != offset) {
            try {
                channel.position(offset)
                this.offset = offset
            } catch (e: IOException) {
                throw e.toErrnoException()
            }
        }
        val buffer = ByteBuffer.wrap(data, 0, size)
        while (buffer.hasRemaining()) {
            val readSize = try {
                channel.read(buffer)
            } catch (e: IOException) {
                throw e.toErrnoException()
            }
            if (readSize == -1) {
                break
            }
            this.offset += readSize
        }
        return (this.offset - offset).toInt()
    }

    @Throws(ErrnoException::class)
    fun onWrite(offset: Long, size: Int, data: ByteArray): Int {
        ensureNotReleased()
        if (this.offset != offset) {
            try {
                channel.position(offset)
                this.offset = offset
            } catch (e: IOException) {
                throw e.toErrnoException()
            }
        }
        val buffer = ByteBuffer.wrap(data, 0, size)
        val writeSize = try {
            channel.write(buffer)
        } catch (e: IOException) {
            throw e.toErrnoException()
        }
        this.offset += writeSize
        return writeSize
    }

    @Throws(ErrnoException::class)
    fun onFsync() {
        ensureNotReleased()
        if (channel is ForceableChannel) {
            try {
                channel.force(true)
            } catch (e: IOException) {
                throw e.toErrnoException()
            }
        }
    }

    fun onRelease() {
        if (isReleased) {
            return
        }
        try {
            channel.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        isReleased = true
    }

    // obf: m2377
    @Throws(ErrnoException::class)
    private fun ensureNotReleased() {
        if (isReleased) {
            throw ErrnoException(null, OsConstants.EBADF)
        }
    }

    // obf: ProxyFileDescriptorCallbackC0372 — anonymous platform adapter
    @RequiresApi(26)
    fun toProxyFileDescriptorCallback(): ProxyFileDescriptorCallback =
        object : ProxyFileDescriptorCallback() {
            override fun onGetSize(): Long = this@ChannelProxyFileDescriptorCallback.onGetSize()

            override fun onRead(offset: Long, size: Int, data: ByteArray): Int =
                this@ChannelProxyFileDescriptorCallback.onRead(offset, size, data)

            override fun onWrite(offset: Long, size: Int, data: ByteArray): Int =
                this@ChannelProxyFileDescriptorCallback.onWrite(offset, size, data)

            override fun onFsync() {
                this@ChannelProxyFileDescriptorCallback.onFsync()
            }

            override fun onRelease() {
                this@ChannelProxyFileDescriptorCallback.onRelease()
            }
        }

    companion object {
        // obf: m2376
        private fun IOException.toErrnoException(): ErrnoException {
            val errno = when (this) {
                is AccessDeniedException -> OsConstants.EPERM
                is InvalidFileNameException -> OsConstants.EINVAL
                is NoSuchFileException -> OsConstants.ENOENT
                is ClosedByInterruptException, is InterruptedIOException -> OsConstants.EINTR
                else -> OsConstants.EIO
            }
            return ErrnoException(message, errno, this)
        }
    }
}

package com.jcapps.adbremote.provider.ftp

import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream

/**
 * Wraps the commons-net data-connection input stream and returns the [FTPClient] to the pool
 * on close (completing the pending command).
 */
class FtpInputStream(
    // obf: f10077 — the underlying commons-net stream (C4072)
    private val inputStream: InputStream,
    // obf: f10079
    private val authority: FtpAuthority,
    // obf: f10078
    private val client: FTPClient,
) : InputStream() {
    // obf: C2686

    override fun available(): Int = inputStream.available()

    override fun close() {
        try {
            // obf: m5101
            inputStream.close()
            if (!client.completePendingCommand()) {
                client.createNegativeReplyCodeException().printStackTrace()
            }
        } finally {
            FtpClientHelper.releaseClient(authority, client)
        }
    }

    override fun mark(readlimit: Int) = inputStream.mark(readlimit)

    override fun markSupported(): Boolean = inputStream.markSupported()

    override fun read(): Int = inputStream.read()

    override fun reset() = inputStream.reset()

    override fun skip(n: Long): Long = inputStream.skip(n)

    override fun read(b: ByteArray): Int = inputStream.read(b)

    override fun read(b: ByteArray, off: Int, len: Int): Int = inputStream.read(b, off, len)
}

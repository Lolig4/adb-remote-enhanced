package com.jcapps.adbremote.provider.ftp

import com.jcapps.adbremote.provider.common.DelegateOutputStream
import org.apache.commons.net.ftp.FTPClient
import java.io.OutputStream

/**
 * Wraps the commons-net STOR/APPE output stream and returns the [FTPClient] to the pool on close
 * (completing the pending command).
 */
class FtpOutputStream(
    // RECON-UNSURE: typed SocketOutputStream in the artifact (R8 type strengthening)
    outputStream: OutputStream,
    // obf: f6309
    private val authority: FtpAuthority,
    // obf: f6308
    private val client: FTPClient,
) : DelegateOutputStream(outputStream) {
    // obf: C1331

    override fun close() {
        try {
            super.close()
            if (!client.completePendingCommand()) {
                client.throwNegativeReplyCodeException()
            }
        } finally {
            FtpClientHelper.releaseClient(authority, client)
        }
    }
}

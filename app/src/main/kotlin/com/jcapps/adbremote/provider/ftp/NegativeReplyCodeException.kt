package com.jcapps.adbremote.provider.ftp

import java.io.IOException

/** An FTP command got a negative reply (MaterialFiles provider.ftp.client). */
class NegativeReplyCodeException(
    // obf: f4815
    val replyCode: Int,
    replyString: String,
) : IOException(replyString) {
    // obf: C0792
}

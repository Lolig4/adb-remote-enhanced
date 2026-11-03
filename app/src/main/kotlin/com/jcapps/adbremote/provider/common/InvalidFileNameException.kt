package com.jcapps.adbremote.provider.common

import java8.nio.file.FileSystemException

/** Thrown for FTP reply 553 (file name not allowed) (MaterialFiles provider.common). */
class InvalidFileNameException(file: String?, other: String?, reason: String?) :
    FileSystemException(file, other, reason) {
    // obf: C2844
}

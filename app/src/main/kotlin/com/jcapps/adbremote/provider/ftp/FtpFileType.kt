package com.jcapps.adbremote.provider.ftp

/**
 * File type stored in [FtpFileAttributes], mapped from the commons-net FTPFile by
 * [FtpFileSystemProvider.readAttributes].
 *
 * RECON-UNSURE: exact original name was obfuscated (EnumC0685); constant names and order are faithful.
 */
enum class FtpFileType {
    // obf: EnumC0685
    REGULAR_FILE,
    DIRECTORY,
    SYMBOLIC_LINK,
    OTHER,
}

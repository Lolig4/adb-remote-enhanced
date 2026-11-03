package com.jcapps.adbremote.provider.common

import android.system.OsConstants

/** POSIX file type with its OsConstants.S_IF* mode (MaterialFiles provider.common). */
enum class PosixFileType(val mode: Int) {
    // obf: EnumC2111
    UNKNOWN(0),
    DIRECTORY(OsConstants.S_IFDIR),
    CHARACTER_DEVICE(OsConstants.S_IFCHR),
    BLOCK_DEVICE(OsConstants.S_IFBLK),
    REGULAR_FILE(OsConstants.S_IFREG),
    FIFO(OsConstants.S_IFIFO),
    SYMBOLIC_LINK(OsConstants.S_IFLNK),
    SOCKET(OsConstants.S_IFSOCK),
}

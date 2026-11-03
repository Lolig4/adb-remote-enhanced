package com.jcapps.adbremote.provider.ftp

import com.jcapps.adbremote.provider.common.InvalidFileNameException
import java.io.IOException
import java8.nio.file.AccessDeniedException
import java8.nio.file.FileSystemException
import java8.nio.file.NoSuchFileException
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply

// FTPClient / exception helpers of the FTP provider (MaterialFiles provider.ftp.client).

fun FTPClient.createNegativeReplyCodeException(): NegativeReplyCodeException =
    NegativeReplyCodeException(replyCode, replyString)

// obf: AbstractC2564.m4929
@Throws(NegativeReplyCodeException::class)
fun FTPClient.throwNegativeReplyCodeException(): Nothing {
    throw createNegativeReplyCodeException()
}

// obf: AbstractC0999.m2949
fun IOException.toFileSystemExceptionForFtp(file: String?, other: String? = null): FileSystemException =
    when (this) {
        is NegativeReplyCodeException ->
            when (replyCode) {
                FTPReply.NOT_LOGGED_IN, FTPReply.NEED_ACCOUNT_FOR_STORING_FILES ->
                    AccessDeniedException(file, other, message)
                FTPReply.FILE_UNAVAILABLE -> NoSuchFileException(file, other, message)
                FTPReply.FILE_NAME_NOT_ALLOWED -> InvalidFileNameException(file, other, message)
                else -> FileSystemException(file, other, message)
            }
        else -> FileSystemException(file, other, message)
    }.apply { initCause(this@toFileSystemExceptionForFtp) }

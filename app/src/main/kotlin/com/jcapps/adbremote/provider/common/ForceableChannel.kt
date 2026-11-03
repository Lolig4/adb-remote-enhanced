package com.jcapps.adbremote.provider.common

import java.io.IOException
import java.nio.channels.Channel
import java.nio.channels.FileChannel

/** A channel supporting `force(metaData)` like FileChannel (MaterialFiles provider.common). */
interface ForceableChannel : Channel {
    @Throws(IOException::class)
    fun force(metaData: Boolean)
}

// obf: eb.m1172
@Throws(IOException::class)
fun Channel.force(metaData: Boolean) {
    when (this) {
        is FileChannel -> force(metaData)
        is ForceableChannel -> force(metaData)
        else -> throw UnsupportedOperationException()
    }
}

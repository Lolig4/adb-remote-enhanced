package com.jcapps.adbremote.model

/** State of a single file transfer in the progress sheet. */
enum class ProgressState {
    // obf: r
    PENDING,
    PAUSE,
    COMPLETED,
    ERROR,
}

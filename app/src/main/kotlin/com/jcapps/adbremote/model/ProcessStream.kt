package com.jcapps.adbremote.model

import java.io.DataOutputStream
import java.io.InputStream

/** Original class name `Stream`; [toString] keeps the runtime text `Stream(...)`. */
// obf: C3434
data class ProcessStream(
    // obf: f12285
    val process: Process,
    // obf: f12284
    val stdIn: DataOutputStream,
    // obf: f12287
    val stdOut: InputStream,
    // obf: f12286
    val stdError: InputStream,
) {
    override fun toString(): String =
        "Stream(process=$process, stdIn=$stdIn, stdOut=$stdOut, stdError=$stdError)"
}

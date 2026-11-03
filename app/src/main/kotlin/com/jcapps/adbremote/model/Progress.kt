package com.jcapps.adbremote.model

/** A transfer progress snapshot (processed bytes label, percent, state). */
data class Progress(
    // obf: f8092
    val processedSize: String,
    // obf: f8091
    val percent: Int,
    // obf: f8093
    val state: ProgressState,
) {
    // obf: C1980
}

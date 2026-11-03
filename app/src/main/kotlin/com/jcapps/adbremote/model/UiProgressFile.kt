package com.jcapps.adbremote.model

/** UI model wrapping a [TransferFile] with its live progress; the progress list diffs on these. */
data class UiProgressFile(
    // obf: f2226
    val file: TransferFile,
    // obf: f2225
    val processedSize: String = "0 B",
    // obf: f2228
    val percent: Int = 0,
    // obf: f2227
    val state: ProgressState = ProgressState.PENDING,
    // obf: f2229
    val error: String? = null,
) {
    // obf: s  — static m1441 is the generated copy(); secondary ctor s(file) = the defaults above.
}

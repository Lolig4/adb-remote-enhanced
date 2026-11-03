package com.jcapps.adbremote.model

/** Result of a move/paste operation. */
data class MoveResult(
    // obf: f7642
    val isMoved: Boolean,
    // obf: f7641
    val message: String? = null,
    // obf: f7643 — the folder's content when it has to be moved file by file (target exists)
    val files: List<ProgressFTPFile>? = null,
) {
    // obf: C1828
    // Secondary convenience ctor in the original: MoveResult(message, isMoved) with files = null.
    constructor(message: String?, isMoved: Boolean) : this(isMoved, message, null)
}

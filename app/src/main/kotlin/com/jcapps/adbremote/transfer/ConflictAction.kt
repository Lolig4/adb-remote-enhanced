package com.jcapps.adbremote.transfer

/** User choice when a transfer target already exists. RECON-UNSURE: class name. */
enum class ConflictAction {
    // obf: EnumC2363
    REPLACE,
    SKIP,
    KEEP_BOTH,
}

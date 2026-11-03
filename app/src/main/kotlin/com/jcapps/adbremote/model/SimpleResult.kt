package com.jcapps.adbremote.model

/** Original class name `Result`; [toString] keeps the runtime text `Result(...)`. */
// obf: C0120
data class SimpleResult(
    // obf: f2905
    val status: Boolean,
    // obf: f2904
    val message: String?,
) {
    override fun toString(): String = "Result(status=$status, message=$message)"
}

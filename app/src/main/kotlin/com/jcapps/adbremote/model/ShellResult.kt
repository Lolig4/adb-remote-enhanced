package com.jcapps.adbremote.model

/** Original class name `Result`; [toString] keeps the runtime text `Result(...)`. */
// obf: C1635
data class ShellResult(
    // obf: f7118
    val exitCode: Int,
    // obf: f7117
    val output: String? = null,
    // obf: f7120
    val error: String? = null,
    // obf: f7119
    val e: Throwable? = null,
) {
    override fun toString(): String = "Result(exitCode=$exitCode, output=$output, error=$error, e=$e)"
}

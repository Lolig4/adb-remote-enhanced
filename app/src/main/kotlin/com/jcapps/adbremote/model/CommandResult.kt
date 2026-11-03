package com.jcapps.adbremote.model

/** Original class name `Result`; [toString] keeps the runtime text `Result(...)`. */
// obf: C1645
data class CommandResult(
    // obf: f7149
    val type: CommandStatus,
    // obf: f7148
    val exitCode: Int = Int.MIN_VALUE,
    // obf: f7151
    val output: String? = null,
    // obf: f7150
    val error: String? = null,
) {
    override fun toString(): String = "Result(type=$type, exitCode=$exitCode, output=$output, error=$error)"
}

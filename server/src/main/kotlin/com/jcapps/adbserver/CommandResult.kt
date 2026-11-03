package com.jcapps.adbserver

import com.google.gson.annotations.SerializedName

/** Result of [Shell.run]; serialized as `{"output", "exit_code", "error"}`. */
// obf: C0090 (f186 = exitCode / m121, f185 = output / m123, f187 = error / m122)
data class CommandResult(
    @field:SerializedName("exit_code") val exitCode: Int,
    @field:SerializedName("output") val output: String,
    @field:SerializedName("error") val error: String,
)

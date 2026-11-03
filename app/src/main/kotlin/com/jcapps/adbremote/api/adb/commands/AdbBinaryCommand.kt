package com.jcapps.adbremote.api.adb.commands

/**
 * One step of a scripted session run by `AdbBinary.runCommands`: either a shell line to write or
 * an output text to wait for. Claimed by group main-b; RECON-UNSURE: class/subclass names
 * (no string evidence; only the ctor parameter names `command` / `output` survived).
 */
sealed class AdbBinaryCommand {
    // obf: AbstractC3040

    /** Shell line written to the adb process. */
    class Command(
        // obf: f5207
        val command: String,
    ) : AdbBinaryCommand() {
        // obf: C0927
    }

    /** Wait until the process prints [output] (e.g. the `adb pair` code prompt). */
    class Expect(
        // obf: f3572
        val output: String,
    ) : AdbBinaryCommand() {
        // obf: C0331
    }
}
